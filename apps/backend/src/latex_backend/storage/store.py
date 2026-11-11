from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import tempfile
import threading
import uuid
from datetime import date, datetime, timezone
from pathlib import Path, PurePosixPath

from latex_backend.core.config import REPO, RESOURCES
TEXT_SUFFIXES = {".tex", ".bib", ".txt", ".md", ".csv", ".sty", ".cls"}
HIDDEN = {"build", ".git", ".overleaf-history", ".history"}


class Problem(Exception):
    def __init__(self, message: str, status: int = 400):
        self.message, self.status = message, status


def atomic_write(path: Path, data: bytes) -> None:
    descriptor, temporary = tempfile.mkstemp(prefix=".write-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def escape_tex(text: str) -> str:
    mapping = {
        "\\": r"\textbackslash{}",
        "{": r"\{",
        "}": r"\}",
        "&": r"\&",
        "%": r"\%",
        "$": r"\$",
        "#": r"\#",
        "_": r"\_",
        "~": r"\textasciitilde{}",
        "^": r"\textasciicircum{}",
    }
    return "".join(mapping.get(character, character) for character in text)


class Templates:
    def __init__(self, directory: Path | None = None):
        self.root = directory or RESOURCES / "templates"
        catalog = json.loads((self.root / "catalog.json").read_text(encoding="utf-8"))
        self.categories = catalog["categories"]
        self.items = {}
        for folder in catalog["templates"]:
            metadata = json.loads(
                (self.root / folder / "template.json").read_text(encoding="utf-8")
            )
            self.items[metadata["id"]] = {**metadata, "resource": folder}

    def public(self) -> dict:
        return {
            "categories": self.categories,
            "templates": [
                item for item in self.items.values() if item["category"] != "internal"
            ],
        }

    def write(
        self,
        directory: Path,
        template: str,
        title: str,
        language: str,
        author="",
        affiliation="",
    ) -> dict:
        item = self.items.get(template)
        if not item:
            raise Problem("Không tìm thấy mẫu dự án.", 404)
        values = {
            "TITLE": escape_tex(title),
            "AUTHOR": escape_tex(author)
            if author
            else "Example Author"
            if language == "en"
            else "Tác giả Mẫu",
            "AFFILIATION": escape_tex(affiliation)
            if affiliation
            else "Independent project"
            if language == "en"
            else "Dự án cá nhân",
            "DATE": date.today().isoformat(),
        }
        names = list(item["files"])
        for name in names:
            target = directory / name
            target.parent.mkdir(parents=True, exist_ok=True)
            data = (self.root / item["resource"] / language / name).read_bytes()
            if target.suffix in TEXT_SUFFIXES:
                text = data.decode("utf-8")
                data = re.sub(
                    r"\{\{(TITLE|AUTHOR|AFFILIATION|DATE)\}\}",
                    lambda match: values[match[1]],
                    text,
                ).encode("utf-8")
            target.write_bytes(data)
        if item.get("sharedPreamble"):
            text = (self.root / "common" / language / "preamble.tex").read_text(
                encoding="utf-8"
            )
            text = re.sub(
                r"\{\{(TITLE|AUTHOR|AFFILIATION|DATE)\}\}",
                lambda match: values[match[1]],
                text,
            )
            (directory / "preamble.tex").write_text(text, encoding="utf-8")
        return item


class Store:
    def __init__(self, home: Path, templates: Templates | None = None, *, managed: bool = False, state=None, locations=None):
        self.managed = managed
        self.home = home.expanduser().resolve()
        self.projects = self.home / "projects"
        self.projects.mkdir(parents=True, exist_ok=True)
        self.templates = templates or Templates()
        self.lock = threading.RLock()
        from latex_backend.storage.state import State
        from latex_backend.storage.history import History

        self.state = State(self.home) if state is None else state
        self.state.managed = managed
        self.history = History(self)
        self.locations_file = self.home / ".app/project-locations.json"
        self.locations = locations if locations is not None else (
            json.loads(self.locations_file.read_text(encoding="utf-8"))
            if self.locations_file.exists()
            else {}
        )

    def register(self, project, root):
        if self.managed and Path(root).resolve() != (self.projects / project).resolve():
            raise Problem("Web projects must stay in server-managed storage.", 403)
        with self.lock:
            self.locations[project] = str(root.absolute())
            atomic_write(self.locations_file, json.dumps(self.locations).encode())

    def root(self, project: str) -> Path:
        if self.managed and self.projects.is_symlink():
            raise Problem("Server projects directory cannot be a symbolic link.", 403)
        if not re.fullmatch(r"[0-9a-f]{32}", project):
            raise Problem("Mã dự án không hợp lệ.", 404)
        root = Path(self.locations.get(project, str(self.projects / project)))
        if self.managed and root.resolve() != (self.projects / project).resolve():
            raise Problem("External desktop project: upload its ZIP to import it into server storage.", 409)
        if root.is_symlink() or not root.is_dir():
            raise Problem("Không tìm thấy dự án.", 404)
        return root

    def info(self, project: str) -> dict:
        info = json.loads(
            (self.root(project) / ".project.json").read_text(encoding="utf-8")
        )
        return {**info, "folder": "" if self.managed else str(self.root(project)), "storage": "server" if self.managed else "local"}

    def write_info(self, project, info):
        atomic_write(self.root(project) / '.project.json', json.dumps(info, ensure_ascii=False).encode('utf-8'))

    def destination(self, parent: str = "") -> Path:
        """Only operators choose physical storage paths in web mode."""
        if self.managed:
            if parent:
                raise Problem("The server manages project storage. Do not supply a filesystem path.", 403)
            if self.projects.is_symlink():
                raise Problem("Server projects directory cannot be a symbolic link.", 403)
            return self.projects
        return Path(parent or self.state.settings()["projectsDirectory"] or self.projects).expanduser().absolute()

    def list(self) -> list[dict]:
        result = []
        identifiers = set(self.locations) | {
            folder.name for folder in self.projects.iterdir()
        }
        for identifier in identifiers:
            if not re.fullmatch(r"[0-9a-f]{32}", identifier):
                continue
            try:
                result.append(self.info(identifier))
            except (OSError, ValueError, Problem):
                continue
        return sorted(result, key=lambda item: item["updated"], reverse=True)

    def create(
        self,
        title: str,
        template: str = "blank",
        language: str = "vi",
        parent: str = "",
        icon="file",
        color="green",
    ) -> dict:
        title = title.strip()
        if not 1 <= len(title) <= 120 or language not in {"vi", "en"}:
            raise Problem("Tên dự án cần 1–120 ký tự; ngôn ngữ vi hoặc en.")
        identifier = uuid.uuid4().hex
        destination = self.destination(parent)
        destination.mkdir(parents=True, exist_ok=True)
        staging = destination / (".new-" + identifier)
        staging.mkdir()
        try:
            item = self.templates.write(
                staging,
                template,
                title,
                language,
                (self.state.session or {}).get("name", ""),
                self.state.settings()["affiliation"],
            )
            info = {
                "id": identifier,
                "title": title,
                "engine": self.state.settings()["compiler"]
                if template == "blank"
                else item["engine"],
                "main": item["mainFile"],
                "template": template,
                "language": language,
                "updated": datetime.now(timezone.utc).isoformat(),
                "icon": icon,
                "color": color,
            }
            (staging / ".project.json").write_text(
                json.dumps(info, ensure_ascii=False), encoding="utf-8"
            )
            staging.rename(destination / identifier)
            self.register(identifier, destination / identifier)
            return self.info(identifier)
        finally:
            if staging.exists():
                shutil.rmtree(staging)

    def path(self, project: str, name: str) -> Path:
        relative = PurePosixPath(name)
        if (
            not name
            or "\\" in name
            or ":" in name
            or relative.is_absolute()
            or any(
                part in {"..", "."} or part.startswith(".") or part in HIDDEN
                for part in relative.parts
            )
        ):
            raise Problem("Đường dẫn tệp không hợp lệ.")
        root = self.root(project)
        target = root.joinpath(*relative.parts)
        if not target.resolve().is_relative_to(root.resolve()):
            raise Problem("Không được truy cập ngoài thư mục dự án.", 403)
        for parent in [target, *target.parents]:
            if parent == root:
                break
            if parent.is_symlink():
                raise Problem("Không hỗ trợ liên kết symbolic trong dự án.", 403)
        return target

    def tree(self, project: str) -> list[dict]:
        root = self.root(project)
        result = []
        for directory, folders, files in os.walk(root, followlinks=False):
            folders[:] = sorted(
                name
                for name in folders
                if name not in HIDDEN
                and not name.startswith(".")
                and not (Path(directory) / name).is_symlink()
            )
            for name in folders + sorted(files):
                path = Path(directory) / name
                if name.startswith(".") or path.is_symlink():
                    continue
                result.append(
                    {
                        "path": path.relative_to(root).as_posix(),
                        "directory": path.is_dir(),
                        "editable": path.suffix in TEXT_SUFFIXES,
                    }
                )
                if len(result) > 10_000:
                    raise Problem("Dự án vượt 10.000 tệp/thư mục.", 413)
        return result

    def read(self, project: str, name: str) -> str:
        path = self.path(project, name)
        if path.suffix not in TEXT_SUFFIXES or not path.is_file():
            raise Problem("Chỉ mở tệp văn bản được hỗ trợ.", 404)
        if path.stat().st_size > 2 * 1024 * 1024:
            raise Problem("Tệp văn bản vượt 2 MiB.", 413)
        return path.read_text(encoding="utf-8")

    def save(self, project: str, name: str, content: str) -> None:
        path = self.path(project, name)
        data = content.encode("utf-8")
        if path.suffix not in TEXT_SUFFIXES or len(data) > 2 * 1024 * 1024:
            raise Problem("Tệp không hỗ trợ hoặc vượt 2 MiB.", 413)
        if not path.is_file():
            raise Problem("Không tìm thấy tệp.", 404)
        with self.lock:
            old = path.read_text(encoding="utf-8")
            if old == content:
                return
            try:
                self.history.capture(
                    project,
                    name,
                    old,
                    "Before edit",
                    (self.state.session or {}).get("name", "Local"),
                )
            except (OSError, Problem):
                pass
            atomic_write(path, data)
            try:
                self.history.capture(
                    project,
                    name,
                    content,
                    "Edited",
                    (self.state.session or {}).get("name", "Local"),
                )
            except (OSError, Problem):
                pass
            self.state.activity("edit")
            info = self.info(project)
            info["updated"] = datetime.now(timezone.utc).isoformat()
            self.write_info(project, info)

    def add(self, project: str, name: str, directory: bool) -> None:
        if not directory and not PurePosixPath(name).suffix:
            name += ".tex"
        path = self.path(project, name)
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            if directory:
                path.mkdir()
            else:
                if path.suffix not in TEXT_SUFFIXES:
                    raise Problem("Định dạng tệp văn bản không hỗ trợ.")
                with path.open("x", encoding="utf-8"):
                    pass
        except FileExistsError:
            raise Problem("Tệp hoặc thư mục đã tồn tại.", 409) from None
        except FileNotFoundError:
            raise Problem("Thư mục cha chưa tồn tại.", 404) from None

    def rename_file(self, project, name, target):
        if name == self.info(project)["main"]:
            raise Problem("Không đổi tên tệp biên dịch gốc.")
        source, destination = self.path(project, name), self.path(project, target)
        if not source.exists():
            raise Problem("Không tìm thấy tệp.", 404)
        if destination.exists():
            raise Problem("Tên đích đã tồn tại.", 409)
        destination.parent.mkdir(parents=True, exist_ok=True)
        source.rename(destination)

    def delete_file(self, project, name):
        if name == self.info(project)["main"]:
            raise Problem("Không xóa tệp biên dịch gốc.")
        source = self.path(project, name)
        if not source.exists():
            raise Problem("Không tìm thấy tệp.", 404)
        trash = self.root(project) / ".file-trash"
        trash.mkdir(exist_ok=True)
        source.rename(trash / (uuid.uuid4().hex + "-" + source.name))

    def update_project(self, project, title=None, icon=None, color=None):
        info = self.info(project)
        if title is not None:
            if not 1 <= len(title.strip()) <= 120:
                raise Problem("Tên dự án không hợp lệ.")
            info["title"] = title.strip()
        if icon is not None:
            info["icon"] = str(icon)[:40]
        if color is not None:
            info["color"] = str(color)[:40]
        self.write_info(project, info)
        return self.info(project)

    def delete_project(self, project):
        root = self.root(project)
        trash = self.home / ".trash"
        trash.mkdir(exist_ok=True)
        shutil.move(str(root), str(trash / f"{project}-{uuid.uuid4().hex}"))
        self.locations.pop(project, None)
        atomic_write(self.locations_file, json.dumps(self.locations).encode())

    def move_project(self, project, parent):
        if self.managed:
            raise Problem("Only the server operator can move project storage.", 403)
        root = self.root(project)
        parent = Path(parent).expanduser().absolute()
        if parent.resolve().is_relative_to(root.resolve()):
            raise Problem("Không thể chuyển dự án vào chính nó.")
        parent.mkdir(parents=True, exist_ok=True)
        destination = parent / project
        if destination.exists():
            raise Problem("Thư mục đích đã tồn tại.", 409)
        staging = parent / (".move-" + uuid.uuid4().hex)
        try:
            shutil.copytree(root, staging, symlinks=True)
            for source in root.rglob("*"):
                if source.is_symlink():
                    raise Problem("Không chuyển symbolic link.")
                if (
                    source.is_file()
                    and hashlib.sha256(source.read_bytes()).digest()
                    != hashlib.sha256(
                        (staging / source.relative_to(root)).read_bytes()
                    ).digest()
                ):
                    raise Problem("Kiểm tra bản sao không khớp.")
            backup = root.parent / ".overleaf-moved-backups"
            backup.mkdir(exist_ok=True)
            staging.rename(destination)
            root.rename(backup / (root.name + "-" + uuid.uuid4().hex))
            self.register(project, destination)
        finally:
            if staging.exists():
                shutil.rmtree(staging)
        return self.info(project)

    def fingerprint(self, project: str) -> str:
        digest = hashlib.sha256()
        with self.lock:
            for entry in sorted(self.tree(project), key=lambda entry: entry["path"]):
                if not entry["directory"]:
                    digest.update(entry["path"].encode("utf-8") + b"\0")
                    with self.path(project, entry["path"]).open("rb") as source:
                        while chunk := source.read(65536):
                            digest.update(chunk)
        return digest.hexdigest()

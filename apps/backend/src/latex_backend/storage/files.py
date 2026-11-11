"""Transactional uploads, imports, URL downloads and project migration."""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
import signal
import time
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

from latex_backend.storage.store import REPO, Problem, atomic_write
from latex_backend.storage.shared_files import shared_write

MAX_BYTES = 500 * 1024 * 1024
MAX_FILE = 100 * 1024 * 1024


def relative(name):
    if not name or "\\" in name or ":" in name or name.startswith("/"):
        raise Problem("Đường dẫn nguồn không hợp lệ.")
    for part in name.rstrip("/").split("/"):
        if (
            part in {"", ".", ".."}
            or part.endswith((" ", "."))
            or re.fullmatch(r"(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\..*)?", part)
        ):
            raise Problem("Tên tệp không hợp lệ hoặc không portable.")
    return PurePosixPath(name)


class ProjectIO:
    def __init__(self, store):
        self.store = store
        self.cancelled = threading.Event()
        self.uploads = {}

    def check(self):
        if self.cancelled.is_set():
            raise Problem("Đã hủy thao tác.", 409)

    def copy(self, source, destination, prune=False):
        total, count = 0, 0
        if source.is_symlink():
            raise Problem("Không nhập symbolic link.")

        def walk():
            for folder, dirs, files in os.walk(source, followlinks=False):
                if prune:
                    dirs[:] = [
                        name
                        for name in dirs
                        if name not in {".git", "build", "node_modules", "target"}
                    ]
                for name in [*dirs, *files]:
                    yield Path(folder) / name

        items = [source] if source.is_file() else walk()
        if source.is_dir():
            destination.mkdir(parents=True, exist_ok=True)
        for item in items:
            self.check()
            count += 1
            if count > 10_000 or item.is_symlink():
                raise Problem("Nguồn vượt giới hạn hoặc chứa symbolic link.", 413)
            target = (
                destination
                if source.is_file()
                else destination / item.relative_to(source)
            )
            if item.is_dir():
                target.mkdir(parents=True, exist_ok=True)
            elif item.is_file():
                total += item.stat().st_size
                if total > MAX_BYTES:
                    raise Problem("Nguồn vượt 500 MiB.", 413)
                target.parent.mkdir(parents=True, exist_ok=True)
                with item.open("rb") as incoming, target.open("xb") as output:
                    while chunk := incoming.read(65536):
                        self.check()
                        output.write(chunk)

    def run_process(self, command, cwd=None, env=None, timeout=90):
        """Own the entire converter/clone process tree, including cancellation."""
        with tempfile.TemporaryFile() as log:
            process = subprocess.Popen(
                command,
                cwd=cwd,
                env=env,
                stdout=log,
                stderr=log,
                **(
                    {
                        "creationflags": subprocess.CREATE_NEW_PROCESS_GROUP
                        | subprocess.CREATE_NO_WINDOW
                    }
                    if os.name == "nt"
                    else {"start_new_session": True}
                ),
            )
            started = time.monotonic()
            try:
                while process.poll() is None:
                    self.check()
                    if time.monotonic() - started > timeout:
                        raise Problem("Công cụ nhập dự án đã quá thời gian.")
                    self.cancelled.wait(0.05)
                if process.returncode:
                    log.seek(0, 2)
                    log.seek(max(0, log.tell() - 6000))
                    raise Problem(
                        "Công cụ nhập thất bại: "
                        + log.read().decode("utf-8", errors="replace")
                    )
            finally:
                if process.poll() is None:
                    if os.name == "nt":
                        subprocess.run(
                            ["taskkill", "/PID", str(process.pid), "/T", "/F"],
                            capture_output=True,
                            creationflags=subprocess.CREATE_NO_WINDOW,
                        )
                    else:
                        try:
                            os.killpg(process.pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                    process.wait()

    def publish(self, source, destination):
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_name(".upload-" + uuid.uuid4().hex)
        try:
            self.copy(source, temporary)
            self.check()
            os.link(
                temporary, destination
            )  # exclusive publish, never replace existing content
        finally:
            temporary.unlink(missing_ok=True)

    def begin_upload(self, project, manifest):
        self.cancelled.clear()
        if not manifest or len(manifest) > 10_000:
            raise Problem("Upload cần 1–10.000 mục.")
        total, seen = 0, set()
        for entry in manifest:
            relative(entry["path"])
            target = self.store.path(project, entry["path"])
            key = entry["path"].casefold()
            size = int(entry.get("size", 0))
            if key in seen or target.exists():
                raise Problem("Tệp đích trùng hoặc đã tồn tại: " + entry["path"], 409)
            if size < 0 or size > MAX_FILE:
                raise Problem("Một tệp vượt 100 MiB.", 413)
            seen.add(key)
            total += size
        if total > MAX_BYTES:
            raise Problem("Tổng upload vượt 500 MiB.", 413)
        identifier = uuid.uuid4().hex
        scratch = Path(tempfile.mkdtemp(prefix="overleaf-upload-"))
        self.uploads[identifier] = {
            "project": project,
            "folder": scratch,
            "manifest": manifest,
            "received": set(),
        }
        return identifier

    def batch(self, project, identifier):
        value = self.uploads.get(identifier)
        if not value or value["project"] != project:
            raise Problem("Không tìm thấy phiên upload.", 404)
        return value

    async def upload_part(self, project, identifier, name, stream):
        batch = self.batch(project, identifier)
        expected = next(
            (
                item
                for item in batch["manifest"]
                if item["path"] == name and not item.get("directory")
            ),
            None,
        )
        if not expected:
            raise Problem("Mục upload chưa khai báo.")
        target = batch["folder"].joinpath(*relative(name).parts)
        target.parent.mkdir(parents=True, exist_ok=True)
        size = 0
        with target.open("wb") as output:
            async for chunk in stream:
                self.check()
                size += len(chunk)
                if size > expected["size"] or size > MAX_FILE:
                    raise Problem("Kích thước upload vượt khai báo.", 413)
                output.write(chunk)
        if size != expected["size"]:
            raise Problem("Upload bị thiếu bytes.")
        batch["received"].add(name)

    @shared_write
    def commit_upload(self, project, identifier):
        batch = self.batch(project, identifier)
        created, folders = [], []
        with self.store.lock:
            try:
                for entry in batch["manifest"]:
                    self.check()
                    target = self.store.path(project, entry["path"])
                    if target.exists():
                        raise Problem("Đích xuất hiện trong lúc upload.", 409)
                    if (
                        not entry.get("directory")
                        and entry["path"] not in batch["received"]
                    ):
                        raise Problem("Chưa nhận đủ tệp.")
                for entry in batch["manifest"]:
                    target = self.store.path(project, entry["path"])
                    parents = []
                    parent = target if entry.get("directory") else target.parent
                    while not parent.exists():
                        parents.append(parent)
                        parent = parent.parent
                    for parent in reversed(parents):
                        parent.mkdir()
                        folders.append(parent)
                    if not entry.get("directory"):
                        self.check()
                        with (
                            (batch["folder"] / entry["path"]).open("rb") as incoming,
                            target.open("xb") as output,
                        ):
                            created.append(target)
                            shutil.copyfileobj(incoming, output)
                return {"uploaded": len(created)}
            except BaseException:
                for target in reversed(created):
                    target.unlink(missing_ok=True)
                for folder in reversed(folders):
                    if folder.exists() and not any(folder.iterdir()):
                        folder.rmdir()
                raise
            finally:
                self.abort_upload(project, identifier)

    def abort_upload(self, project, identifier):
        batch = self.batch(project, identifier)
        shutil.rmtree(batch["folder"])
        self.uploads.pop(identifier, None)

    def upload_paths(self, project, sources, destination=""):
        manifest, files = [], {}
        for source in map(Path, sources):
            if source.is_symlink():
                raise Problem("Không tải symbolic link.")
            entries = [source, *source.rglob("*")] if source.is_dir() else [source]
            for entry in entries:
                if entry.is_symlink():
                    raise Problem("Không tải symbolic link.")
                name = (
                    (
                        PurePosixPath(destination)
                        / source.name
                        / entry.relative_to(source).as_posix()
                    ).as_posix()
                    if source.is_dir()
                    else (PurePosixPath(destination) / source.name).as_posix()
                )
                manifest.append(
                    {
                        "path": name,
                        "size": 0 if entry.is_dir() else entry.stat().st_size,
                        "directory": entry.is_dir(),
                    }
                )
                if entry.is_file():
                    files[name] = entry
        identifier = self.begin_upload(project, manifest)
        batch = self.batch(project, identifier)
        try:
            for name, source in files.items():
                self.copy(source, batch["folder"] / name)
                batch["received"].add(name)
            return self.commit_upload(project, identifier)
        except BaseException:
            if identifier in self.uploads:
                self.abort_upload(project, identifier)
            raise

    @shared_write
    def copy_project_file(self, project, other, source, target, output=False):
        if output:
            root = self.store.root(other) / "build"
            if root.is_symlink():
                raise Problem("Build không được là symbolic link.")
            value = root.joinpath(*relative(source).parts)
            if not value.resolve().is_relative_to(root.resolve()) or value.is_symlink():
                raise Problem("Đường dẫn đầu ra không hợp lệ.")
        else:
            value = self.store.path(other, source)
        destination = self.store.path(project, target)
        if destination.exists():
            raise Problem("Tệp đích đã tồn tại.", 409)
        if not value.is_file() or value.stat().st_size > MAX_FILE:
            raise Problem("Chọn một tệp tối đa 100 MiB.")
        destination.parent.mkdir(parents=True, exist_ok=True)
        self.cancelled.clear()
        self.publish(value, destination)

    @shared_write
    def download(self, project, url, name):
        self.cancelled.clear()

        def validate(value):
            parsed = urlsplit(value)
            if (
                parsed.scheme != "https"
                or not parsed.hostname
                or parsed.username
                or parsed.password
            ):
                raise Problem("URL cần HTTPS, không chứa tài khoản/mật khẩu.")

        validate(url)

        class Redirects(HTTPRedirectHandler):
            count = 0

            def redirect_request(self, request, fp, code, msg, headers, newurl):
                validate(newurl)
                self.count += 1
                if self.count > 5:
                    raise Problem("Vượt 5 redirect HTTPS.")
                return super().redirect_request(request, fp, code, msg, headers, newurl)

        destination = self.store.path(project, name)
        if destination.exists():
            raise Problem("Đích đã tồn tại.", 409)
        with tempfile.TemporaryDirectory(prefix="overleaf-download-") as scratch:
            temp = Path(scratch) / "download"
            with (
                build_opener(Redirects()).open(
                    Request(url, headers={"User-Agent": "OverleafDesktop/1.0"}),
                    timeout=15,
                ) as response,
                temp.open("wb") as output,
            ):
                if response.status != 200:
                    raise Problem("Máy chủ không trả HTTP 200.")
                size = 0
                while chunk := response.read(65536):
                    self.check()
                    size += len(chunk)
                    if size > MAX_FILE:
                        raise Problem("Tải xuống vượt 100 MiB.", 413)
                    output.write(chunk)
            destination.parent.mkdir(parents=True, exist_ok=True)
            self.publish(temp, destination)

    def unzip(self, archive, target):
        seen, size = set(), 0
        with zipfile.ZipFile(archive) as zipped:
            if len(zipped.infolist()) > 10_000:
                raise Problem("ZIP vượt 10.000 mục.", 413)
            for entry in zipped.infolist():
                self.check()
                name = entry.filename.replace("\\", "/")
                path = relative(name)
                if path.parts[0] == "__MACOSX":
                    continue
                if (entry.external_attr >> 16) & 0o170000 == 0o120000:
                    raise Problem("ZIP chứa symbolic link.")
                if name.casefold() in seen:
                    raise Problem("ZIP chứa tên trùng không phân biệt hoa/thường.")
                seen.add(name.casefold())
                size += entry.file_size
                if size > MAX_BYTES:
                    raise Problem("ZIP giải nén vượt 500 MiB.", 413)
                output = target.joinpath(*path.parts)
                if entry.is_dir():
                    output.mkdir(parents=True, exist_ok=True)
                else:
                    output.parent.mkdir(parents=True, exist_ok=True)
                    with (
                        zipped.open(entry) as incoming,
                        output.open("xb") as destination,
                    ):
                        actual = 0
                        while chunk := incoming.read(65536):
                            self.check()
                            actual += len(chunk)
                            if actual > entry.file_size:
                                raise Problem("ZIP size không khớp.")
                            destination.write(chunk)

    def import_project(
        self, kind, source, title, parent="", language="vi", reset_cancel=True
    ):
        if reset_cancel:
            self.cancelled.clear()
        destination = self.store.destination(parent)
        destination.mkdir(parents=True, exist_ok=True)
        identifier = uuid.uuid4().hex
        staging = destination / (".import-" + identifier)
        staging.mkdir()
        try:
            if kind == "zip":
                self.unzip(Path(source), staging)
            elif kind == "folder":
                self.copy(Path(source), staging, prune=True)
                git = staging / ".git"
                if git.exists():
                    shutil.rmtree(git)
            elif kind == "github":
                if not re.fullmatch(
                    r"https://github\.com/[\w.-]+/[\w.-]+(?:\.git)?/?", source
                ):
                    raise Problem(
                        "Chỉ nhận kho GitHub công khai bằng URL HTTPS owner/repo."
                    )
                env = {**os.environ, "GIT_TERMINAL_PROMPT": "0"}
                self.run_process(
                    [
                        "git",
                        "-c",
                        "credential.helper=",
                        "clone",
                        "--depth=1",
                        "--",
                        source,
                        str(staging),
                    ],
                    env=env,
                )
                if (staging / ".git").exists():
                    shutil.rmtree(staging / ".git")
            elif kind in {"word", "markdown"}:
                file = Path(source)
                if file.stat().st_size > 50 * 1024 * 1024 or (
                    kind == "word" and file.suffix.lower() != ".docx"
                ):
                    raise Problem("Chọn DOCX/Markdown tối đa 50 MiB.")
                original = staging / "source" / file.name
                original.parent.mkdir()
                self.copy(file, original)
                pandoc = (
                    os.getenv("OVERLEAF_PANDOC")
                    or shutil.which("pandoc")
                    or (
                        str(REPO / "target/pandoc/pandoc.exe")
                        if os.name == "nt"
                        else "pandoc"
                    )
                )
                self.run_process(
                    [
                        pandoc,
                        "--standalone",
                        "--from=" + ("docx" if kind == "word" else "markdown"),
                        "--to=latex",
                        "--extract-media=media",
                        "--variable=fontenc:T5",
                        "--variable=lang:" + ("vi-VN" if language == "vi" else "en-US"),
                        "--output=main.tex",
                        str(file.absolute()),
                    ],
                    cwd=staging,
                )
            else:
                raise Problem("Kiểu nhập không hợp lệ.")
            children = [
                entry for entry in staging.iterdir() if entry.name != "__MACOSX"
            ]
            if len(children) == 1 and children[0].is_dir():
                wrapper = children[0]
                for entry in list(wrapper.iterdir()):
                    entry.rename(staging / entry.name)
                wrapper.rmdir()
            if not (staging / "main.tex").is_file():
                candidate = next(
                    (
                        path
                        for path in sorted(staging.glob("*.tex"))
                        if path.stat().st_size <= 8 * 1024 * 1024
                        and "\\documentclass" in path.read_text(encoding="utf-8")
                    ),
                    None,
                )
                if not candidate:
                    raise Problem("Không có main.tex hoặc tệp gốc có documentclass.")
                shutil.copy2(candidate, staging / "main.tex")
            # Reject symlinks and pathological repository trees before publishing.
            total, count = 0, 0
            for entry in staging.rglob("*"):
                self.check()
                count += 1
                if entry.is_symlink():
                    raise Problem("Nguồn chứa symbolic link.")
                if entry.is_file():
                    total += entry.stat().st_size
                if count > 10_000 or total > MAX_BYTES:
                    raise Problem("Dự án vượt giới hạn nhập.", 413)
            old = self.store.templates.items["blank"]
            engine_match = re.search(
                r"%\s*!TeX\s+program\s*=\s*(pdflatex|xelatex|lualatex)",
                (staging / "main.tex").read_text(encoding="utf-8"),
                re.I,
            )
            info = {
                "id": identifier,
                "title": title.strip() or Path(source).stem,
                "engine": engine_match[1].lower() if engine_match else old["engine"],
                "main": "main.tex",
                "language": language,
                "template": kind,
                "updated": datetime.now(timezone.utc).isoformat(),
                "icon": "file",
                "color": "green",
            }
            from latex_backend.storage.state import properties

            look = properties(staging / ".overleaf-project")
            icons = {
                "file-text": "file",
                "book-open": "book",
                "graduation-cap": "cap",
                "code-2": "code",
                "lightbulb": "bulb",
                "pen-line": "pen",
                "calculator": "calc",
            }
            if look.get("icon"):
                info["icon"] = icons.get(look["icon"], look["icon"])
            if re.fullmatch(r"#[0-9a-fA-F]{6}", look.get("color", "")):
                info["color"] = look["color"]
            (staging / ".project.json").write_text(
                json.dumps(info, ensure_ascii=False), encoding="utf-8"
            )
            self.check()
            staging.rename(destination / identifier)
            self.store.register(identifier, destination / identifier)
            return self.store.info(identifier)
        finally:
            if staging.exists():
                shutil.rmtree(staging)

    def import_java(self, folder):
        self.cancelled.clear()
        from latex_backend.storage.state import properties

        folder = Path(folder)
        candidates = (
            set(path for path in (folder / "projects").iterdir() if path.is_dir())
            if (folder / "projects").is_dir()
            else set()
        )
        candidates.update(
            Path(value)
            for value in properties(folder / ".app/projects.properties")
            if Path(value).is_dir()
        )
        old_settings = properties(folder / ".app/settings.properties")
        selected = (
            Path(old_settings["projectsDirectory"])
            if old_settings.get("projectsDirectory")
            else None
        )
        if selected and selected.is_dir():
            candidates.update(
                path
                for path in selected.iterdir()
                if path.is_dir() and not path.name.startswith(".")
            )
        imported = []
        ledger_path = self.store.home / ".app/java-imports.json"
        ledger = json.loads(ledger_path.read_text()) if ledger_path.exists() else {}
        for source in sorted(candidates):
            self.check()
            key = str(source.resolve())
            if key in ledger:
                continue
            if not (source / "main.tex").is_file():
                continue
            item = self.import_project(
                "folder", str(source), source.name, reset_cancel=False
            )
            imported.append(item)
            ledger[key] = item["id"]
            atomic_write(ledger_path, json.dumps(ledger).encode())
        accounts = self.store.state.import_java_accounts(folder)
        activity = properties(folder / ".app/activity.properties")
        for key, value in activity.items():
            try:
                if key.startswith("edits."):
                    self.store.state.data["activity"].setdefault(key[6:], int(value))
                elif key.startswith("compiles."):
                    self.store.state.data["compiles"].setdefault(key[9:], int(value))
            except ValueError:
                continue
        self.store.state.write()
        return {"projects": imported, "accounts": accounts}

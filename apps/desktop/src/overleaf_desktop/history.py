"""Project-local revisions with preimages, deduplication, checksum, labels and Java reads."""

import hashlib
import json
import re
import shutil
import uuid
from datetime import datetime, timezone
from .store import Problem, atomic_write


class History:
    def __init__(self, store):
        self.store = store

    def directory(self, project):
        folder = self.store.root(project) / ".overleaf-history"
        if folder.is_symlink():
            raise Problem("History không được là symbolic link.")
        return folder

    def metadata(self, project, revision):
        if not re.fullmatch(r"[a-f0-9-]{32,36}", revision):
            raise Problem("Mã revision không hợp lệ.")
        folder = self.directory(project) / revision
        if not folder.is_dir() or folder.is_symlink():
            raise Problem("Không tìm thấy revision.", 404)
        metadata = folder / "metadata.json"
        if metadata.is_file() and not metadata.is_symlink():
            if metadata.stat().st_size > 16 * 1024:
                raise Problem("Metadata history vượt giới hạn.")
            data = json.loads(metadata.read_text(encoding="utf-8"))
        else:
            from .state import properties

            data = properties(folder / "metadata.properties", "iso-8859-1")
        if not isinstance(data, dict) or not all(
            isinstance(data.get(key), str) for key in ("file", "time", "sha256")
        ):
            raise Problem("Metadata history không hợp lệ.")
        if not re.fullmatch(r"[0-9a-f]{64}", data["sha256"]):
            raise Problem("Checksum history không hợp lệ.")
        self.store.path(project, data["file"])
        datetime.fromisoformat(data["time"])
        return {**data, "id": revision}

    def list(self, project):
        folder = self.directory(project)
        result = []
        if folder.is_dir():
            for entry in folder.iterdir():
                try:
                    data = self.metadata(project, entry.name)
                    data["size"] = (entry / "content.txt").stat().st_size
                    result.append(data)
                except (OSError, ValueError, KeyError, Problem):
                    continue
        return sorted(result, key=lambda item: item["time"], reverse=True)

    def read(self, project, revision):
        metadata = self.metadata(project, revision)
        path = self.directory(project) / revision / "content.txt"
        if path.is_symlink() or path.stat().st_size > 2 * 1024 * 1024:
            raise Problem("Nội dung history không hợp lệ.")
        data = path.read_bytes()
        if hashlib.sha256(data).hexdigest() != metadata["sha256"]:
            raise Problem("Checksum history không khớp.")
        return {**metadata, "content": data.decode("utf-8")}

    def capture(self, project, name, text, reason="Edited", author="Local"):
        self.store.path(project, name)
        data = text.encode("utf-8")
        if len(data) > 2 * 1024 * 1024:
            raise Problem("History hỗ trợ tối đa 2 MiB/revision.")
        digest = hashlib.sha256(data).hexdigest()
        with self.store.lock:
            revisions = self.list(project)
            previous = next((item for item in revisions if item["file"] == name), None)
            if previous and previous["sha256"] == digest:
                return previous
            if reason == "Before edit" and any(
                item["file"] == name and item["sha256"] == digest for item in revisions
            ):
                return previous
            if (
                len(revisions) >= 1024
                or sum(item["size"] for item in revisions) + len(data)
                > 128 * 1024 * 1024
            ):
                raise Problem("History đã đạt 1.024 revision hoặc 128 MiB.", 413)
            folder = self.directory(project)
            folder.mkdir(exist_ok=True)
            identifier = str(uuid.uuid4())
            staging = folder / ("pending-" + identifier)
            staging.mkdir()
            metadata = {
                "id": identifier,
                "file": name,
                "time": datetime.now(timezone.utc).isoformat(),
                "author": author,
                "reason": reason,
                "label": "",
                "sha256": digest,
                "size": len(data),
            }
            try:
                (staging / "content.txt").write_bytes(data)
                (staging / "metadata.json").write_text(
                    json.dumps(metadata, ensure_ascii=False), encoding="utf-8"
                )
                staging.rename(folder / identifier)
            finally:
                if staging.exists():
                    shutil.rmtree(staging)
            return metadata

    def label(self, project, revision, value):
        if len(value) > 120:
            raise Problem("Nhãn vượt 120 ký tự.")
        metadata = self.read(project, revision)
        metadata.pop("content")
        metadata["label"] = value.strip()
        atomic_write(
            self.directory(project) / revision / "metadata.json",
            json.dumps(metadata, ensure_ascii=False).encode(),
        )
        return metadata

    def restore(self, project, revision):
        data = self.read(project, revision)
        if self.store.path(project, data["file"]).exists():
            current = self.store.read(project, data["file"])
            self.capture(
                project,
                data["file"],
                current,
                "Before edit",
                (self.store.state.session or {}).get("name", "Local"),
            )
        else:
            self.store.add(project, data["file"], False)
        self.store.save(project, data["file"], data["content"])
        return {"file": data["file"], "content": data["content"]}

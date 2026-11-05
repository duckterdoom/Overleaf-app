"""HTTP surface for the Java feature-parity services; paths always pass Store validation."""

import asyncio
import hashlib
import shutil
from pathlib import Path
from fastapi import Request
from fastapi.responses import Response
from .store import Problem
from .files import ProjectIO
from .synctex import SyncTex
from .toolchain import diagnostics, preflight, install_packages, explain


def register(app, store, compiler):
    io = ProjectIO(store)
    store.io = io
    sync = SyncTex(store, compiler)
    app.state.install_task = None
    watch_cache = {}
    from .review import Review

    review = Review(store)
    from .word import Word, MIME

    word = Word(store)

    @app.get("/api/projects/{project}/word")
    def get_word(project: str):
        return word.read(project)

    @app.post("/api/projects/{project}/word")
    def save_word(project: str, data: dict):
        return word.save(project, data)

    @app.post("/api/projects/{project}/word/generate")
    def generate_word(project: str, data: dict):
        return word.generate(project, data.get("revision"))

    @app.get("/api/projects/{project}/pdf/{snapshot}/docx")
    def export_word(project: str, snapshot: str):
        from .docx_workspace import download

        return Response(
            download(compiler, project, snapshot)[0],
            media_type=MIME,
            headers={"Content-Disposition": 'attachment; filename="main.docx"'},
        )

    from .docx_workspace import DocxWorkspace
    docx_workspace = DocxWorkspace(compiler)

    @app.post('/api/projects/{project}/docx-preview')
    def open_docx(project: str, data: dict):
        return docx_workspace.open(project, data['snapshot'], data.get('reset', False), data.get('revision'))

    @app.get('/api/projects/{project}/docx-preview/{revision}')
    def read_docx(project: str, revision: str):
        return Response(docx_workspace.read(project, revision), media_type=MIME)

    @app.put('/api/projects/{project}/docx-preview/{revision}')
    async def save_docx(project: str, revision: str, request: Request):
        data = await request.body()
        return docx_workspace.save(project, revision, data)

    @app.get("/api/projects/{project}/review")
    def get_review(project: str):
        return review.read(project)

    @app.post("/api/projects/{project}/review")
    async def update_review(project: str, request: Request):
        return review.update(project, await request.json())

    from .ielts import IeltsManager

    ielts = IeltsManager(store)

    @app.get("/api/projects/{project}/ielts")
    def get_ielts(project: str):
        return ielts.read(project)

    @app.post("/api/projects/{project}/ielts")
    async def save_ielts(project: str, request: Request):
        return ielts.save(project, await request.json())

    @app.post("/api/projects/{project}/ielts/report")
    async def generate_ielts_report(project: str, request: Request):
        data = await request.json()
        filename = ielts.generate_report_tex(project, data)
        return {"success": True, "file": filename}

    from .network import NetworkSession

    store.network = NetworkSession(store)

    @app.get("/api/network")
    def network_status():
        return store.network.status()

    @app.get("/api/session")
    def session():
        return {
            "session": store.state.session,
            "lastEmail": store.state.data.get("lastEmail", ""),
            "settings": store.state.settings(),
            "activity": store.state.activity(),
        }

    @app.get("/api/avatar")
    def avatar():
        data = store.state.avatar()
        return Response(data, media_type="image/png", status_code=200 if data else 204)

    @app.post("/api/avatar")
    async def set_avatar(request: Request):
        return Response(
            store.state.avatar(await request.body()), media_type="image/png"
        )

    @app.get("/api/projects/{project}/watch")
    def watch(project: str):
        entries = store.tree(project)
        signature = hashlib.sha256()
        for entry in entries:
            path = store.path(project, entry["path"])
            stat = path.stat()
            signature.update(
                f"{entry['path']}:{stat.st_size}:{stat.st_mtime_ns}\n".encode()
            )
        current = compiler.snapshots.get(project)
        key = (signature.hexdigest(), current.id if current else None)
        if project not in watch_cache or watch_cache[project][0] != key:
            watch_cache[project] = (
                key,
                bool(current and current.fingerprint == store.fingerprint(project)),
            )
        return {
            "signature": signature.hexdigest(),
            "files": entries,
            "snapshot": current.id if current else None,
            "dirty": project in compiler.dirty,
            "current": watch_cache[project][1] and project not in compiler.dirty,
        }

    @app.get("/api/projects/{project}/history")
    def history(project: str):
        return store.history.list(project)

    @app.get("/api/projects/{project}/history/{revision}")
    def revision(project: str, revision: str):
        return store.history.read(project, revision)

    @app.get("/api/projects/{project}/outputs")
    def outputs(project: str):
        root = store.root(project) / "build"
        if root.is_symlink():
            raise Problem("Không đọc symbolic link.")
        return (
            [
                {"path": path.relative_to(root).as_posix(), "directory": False}
                for path in root.rglob("*")
                if path.is_file() and not path.is_symlink()
            ]
            if root.is_dir()
            else []
        )

    @app.get("/api/projects/{project}/preview/{snapshot}")
    def preview(project: str, snapshot: str):
        store.root(project)
        result = compiler.snapshots.get(project)
        if not result or result.id != snapshot:
            raise Problem("PDF không còn trong phiên.", 404)
        return Response(result.data, media_type="application/pdf")

    @app.get("/api/projects/{project}/geometry/{snapshot}")
    def geometry(project: str, snapshot: str):
        result = compiler.snapshots.get(project)
        if not result or result.id != snapshot:
            raise Problem("Không tìm thấy PDF.", 404)
        return result.geometry

    @app.post("/api/projects/{project}/upload/start")
    async def upload_start(project: str, request: Request):
        data = await request.json()
        return {"id": io.begin_upload(project, data["manifest"])}

    @app.put("/api/projects/{project}/upload/{batch}/part")
    async def upload_part(project: str, batch: str, path: str, request: Request):
        await io.upload_part(project, batch, path, request.stream())
        return {"received": True}

    @app.post("/api/projects/{project}/upload/{batch}/commit")
    def upload_commit(project: str, batch: str):
        return io.commit_upload(project, batch)

    @app.delete("/api/projects/{project}/upload/{batch}")
    def upload_abort(project: str, batch: str):
        io.abort_upload(project, batch)
        return {"cancelled": True}

    @app.post("/api/projects/import-file")
    async def import_file(request: Request):
        import uuid

        kind = request.query_params.get("kind", "zip")
        title = request.query_params.get("title", "")
        parent = request.query_params.get("parent", "")
        language = request.query_params.get("language", "vi")
        filename = request.query_params.get("filename", "")
        if kind not in {"zip", "word", "markdown"}:
            raise Problem("Kiểu nhập không hợp lệ.")
        staging_dir = store.home / ".import-tmp"
        staging_dir.mkdir(parents=True, exist_ok=True)
        temp_name = f"{uuid.uuid4().hex}_{filename or 'import'}"
        temp_path = staging_dir / temp_name
        total_size = 0
        try:
            with open(temp_path, "wb") as f:
                async for chunk in request.stream():
                    total_size += len(chunk)
                    if total_size > 50 * 1024 * 1024:
                        raise Problem("Tệp tải lên vượt giới hạn 50 MiB.", 413)
                    f.write(chunk)
            return io.import_project(
                kind,
                str(temp_path),
                title or Path(filename).stem,
                parent,
                language,
            )
        finally:
            if temp_path.exists():
                try:
                    temp_path.unlink()
                except Exception:
                    pass

    @app.post("/api/install/cancel")
    async def cancel_install():
        if app.state.install_task:
            app.state.install_task.cancel()
        return {"cancelled": True}

    @app.post("/api/install")
    async def install(request: Request):
        data = await request.json()
        if app.state.install_task and not app.state.install_task.done():
            raise Problem("Đang cài gói.", 409)
        item = store.templates.items.get(data.get("template"))
        if not item:
            raise Problem("Không tìm thấy mẫu.")
        app.state.install_task = asyncio.create_task(
            install_packages(compiler, item, data.get("confirmed"))
        )
        try:
            return await app.state.install_task
        except asyncio.CancelledError:
            return {
                **(await asyncio.to_thread(preflight, compiler.executable, item)),
                "cancelled": True,
            }

    @app.post("/api/actions/{name}")
    def action(name: str, data: dict):
        project = data.get("project", "")
        if name == "network-connect":
            return store.network.connect(**data)
        if name == "network-reconnect":
            return store.network.reconnect()
        if name == "network-offline":
            return store.network.offline()
        if name == "network-leave":
            return store.network.leave()
        if name == "network-edit":
            return store.network.edit(data["text"], data.get("base"))
        if name == "network-cursor":
            store.network.cursor(data.get("line", 0), data.get("column", 0))
            return {"sent": True}
        if name == "register" or name == "login" or name == "offline":
            return store.state.authenticate(
                name,
                data.get("name", ""),
                data.get("email", ""),
                data.get("password", ""),
                data.get("remember", False),
            )
        if name == "logout":
            store.state.logout()
            return {"loggedOut": True}
        if name == "profile":
            return store.state.update_profile(
                data.get("name"),
                data.get("email"),
                data.get("currentPassword", ""),
                data.get("password"),
            )
        if name == "remove-avatar":
            store.state.avatar(remove=True)
            return {"removed": True}
        if name in {"settings", "settings-reset"}:
            result = (
                store.state.reset_preferences()
                if name == "settings-reset"
                else store.state.preferences(data)
            )
            from .compiler import find_latexmk

            compiler.executable = find_latexmk(result["latexmkPath"])
            compiler.timeout = result["compileTimeoutSeconds"]
            return result
        if name == "activity":
            return store.state.activity()
        if name == "symbols":
            import re

            labels, citations, images = set(), set(), []
            for entry in store.tree(project):
                if entry["directory"]:
                    continue
                path = entry["path"]
                if Path(path).suffix.lower() in {".png", ".jpg", ".jpeg", ".pdf"}:
                    images.append(path)
                if path.endswith((".tex", ".bib")):
                    try:
                        content = (
                            data.get("content", "")
                            if path == data.get("file")
                            else store.read(project, path)
                        )
                    except (Problem, UnicodeError, OSError):
                        continue
                    if path.endswith(".tex"):
                        labels.update(re.findall(r"\\label\s*\{([^}]+)\}", content))
                    else:
                        citations.update(
                            re.findall(
                                r"(?im)@(?!(?:comment|string|preamble)\b)[a-z]+\s*[\{(]\s*([^,\s]+)\s*,",
                                content,
                            )
                        )
            return {
                "labels": sorted(labels),
                "citations": sorted(citations),
                "images": sorted(images),
            }
        if name in {
            "project-delete",
            "project-move",
            "file-rename",
            "file-delete",
            "history-restore",
        }:
            if compiler.active and compiler.active.project == project:
                raise Problem("Dừng compile trước khi thay đổi cấu trúc dự án.", 409)
            if store.network.joined and store.network.project == project:
                raise Problem("Rời phòng trước khi thay đổi cấu trúc dự án.", 409)
        if name == "project-update":
            return store.update_project(
                project, data.get("title"), data.get("icon"), data.get("color")
            )
        if name == "project-delete":
            store.delete_project(project)
            return {"deleted": True}
        if name == "project-move":
            return store.move_project(project, data["parent"])
        if name == "file-rename":
            store.rename_file(project, data["path"], data["target"])
            return {"renamed": True}
        if name == "file-delete":
            store.delete_file(project, data["path"])
            return {"deleted": True}
        if name == "editing":
            store.root(project)
            if data.get("dirty"):
                compiler.dirty.add(project)
            else:
                compiler.dirty.discard(project)
            return {"dirty": project in compiler.dirty}
        if name == "checkpoint":
            return store.history.capture(
                project,
                data["path"],
                data["content"],
                data.get("reason", "Checkpoint"),
                (store.state.session or {}).get("name", "Local"),
            )
        if name == "history-label":
            return store.history.label(project, data["revision"], data["label"])
        if name == "history-restore":
            if getattr(store, "network", None) and store.network.joined:
                raise Problem("Rời phòng trước khi khôi phục history.")
            compiler.dirty.add(project)
            return store.history.restore(project, data["revision"])
        if name == "copy-file":
            io.copy_project_file(
                project,
                data["sourceProject"],
                data["source"],
                data["target"],
                data.get("output", False),
            )
            return {"copied": True}
        if name == "download":
            io.download(project, data["url"], data["path"])
            return {"downloaded": True}
        if name == "io-cancel":
            io.cancelled.set()
            return {"cancelled": True}
        if name == "github":
            return io.import_project(
                "github",
                data["url"],
                data["title"],
                data.get("parent", ""),
                data.get("language", "vi"),
            )
        if name == "import-path":
            kind = data.get("kind")
            source = data.get("path")
            if not kind or not source:
                raise Problem("Thiếu thông tin tệp hoặc đường dẫn.")
            return io.import_project(
                kind,
                source,
                data.get("title", ""),
                data.get("parent", ""),
                data.get("language", "vi"),
            )
        if name == "diagnostics":
            return diagnostics(compiler.executable)
        if name == "preflight":
            template = store.templates.items.get(data["template"])
            if not template:
                raise Problem("Mẫu không hợp lệ.")
            return preflight(compiler.executable, template)
        if name == "explain":
            return explain(data["log"], store.state.settings()["language"])
        if name == "synctex":
            return sync.query(
                project,
                data["snapshot"],
                data["mode"],
                data.get("file", "main.tex"),
                data.get("line", 1),
                data.get("column", 0),
                data.get("page", 0),
                data.get("x", 0),
                data.get("y", 0),
            )
        if name == "clean":
            root = store.root(project) / "build"
            if compiler.active and compiler.active.project == project:
                raise Problem("Dừng compile trước khi dọn cache.")
            if root.is_symlink():
                raise Problem("Build không được là symbolic link.")
            if root.is_dir():
                for path in root.iterdir():
                    if path.is_symlink():
                        path.unlink()
                    elif path.is_dir():
                        shutil.rmtree(path)
                    elif path.suffix.lower() != ".pdf":
                        path.unlink()
            return {"cleaned": True}
        raise Problem("Thao tác không tồn tại.", 404)

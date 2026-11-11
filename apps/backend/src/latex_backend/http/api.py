from __future__ import annotations

import json
import secrets
import asyncio
from contextlib import asynccontextmanager
from pathlib import Path
from urllib.parse import urlsplit, quote

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response, StreamingResponse, FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from latex_backend.compilation.compiler import Compiler
from latex_backend.storage.store import Problem, Store


class ProjectBody(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    template: str = "blank"
    language: str = "vi"
    parent: str = ""
    icon: str = "file"
    color: str = "green"


class FileBody(BaseModel):
    path: str = Field(min_length=1, max_length=512)
    content: str = Field(default="", max_length=2 * 1024 * 1024)
    directory: bool = False
    expected: str | None = Field(default=None, max_length=2 * 1024 * 1024)


class CompileBody(BaseModel):
    engine: str = "pdflatex"
    main: str = Field(default="", max_length=512)
    force: bool = False


def create_api(
    store: Store, compiler: Compiler, token: str, frontend: Path | None = None, *, web: bool = False
) -> FastAPI:
    if web:
        store.managed = True
        store.state.managed = True
    @asynccontextmanager
    async def lifespan(app):
        app.state.loop = asyncio.get_running_loop()
        try:
            yield
        finally:
            if hasattr(compiler, "pdf_word_exporter"):
                compiler.pdf_word_exporter.cancel()
            install = getattr(app.state, "install_task", None)
            if install and not install.done():
                install.cancel()
                try:
                    await install
                except asyncio.CancelledError:
                    pass
            if compiler.active:
                await compiler.cancel(compiler.active.project)
            if getattr(store, "network", None):
                store.network.leave()
            if getattr(store, "io", None):
                store.io.cancelled.set()
                for identifier, batch in list(store.io.uploads.items()):
                    store.io.abort_upload(batch["project"], identifier)

    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)

    @app.exception_handler(Problem)
    async def problem_handler(request: Request, error: Problem):
        return JSONResponse({"message": error.message}, status_code=error.status)

    if getattr(store, 'db', None) is not None:
        from pymongo.errors import PyMongoError
        @app.exception_handler(PyMongoError)
        async def database_problem(request, error):
            return JSONResponse({'message': 'MongoDB tạm thời không khả dụng. Vui lòng thử lại.'}, 503)

    @app.middleware("http")
    async def local_session(request: Request, call_next):
        if request.url.hostname not in {"127.0.0.1", "localhost"}:
            return JSONResponse({"message": "Invalid local host"}, status_code=403)
        if request.url.path.startswith("/api/"):
            origin = request.headers.get("origin")
            if origin and urlsplit(origin).netloc != request.url.netloc:
                return JSONResponse({"message": "Invalid origin"}, status_code=403)
            if request.headers.get("sec-fetch-site") == "cross-site":
                return JSONResponse({"message": "Invalid request site"}, status_code=403)
            bootstrap = web and request.url.path == "/api/web/session" and request.method == "POST"
            expected = "Bearer " + token
            bearer = secrets.compare_digest(
                request.headers.get("authorization", "").encode("utf-8"),
                expected.encode("ascii"),
            )
            cookie = web and secrets.compare_digest(request.cookies.get("overleaf_local_session", "").encode(), token.encode())
            if not (bootstrap or bearer or cookie):
                return JSONResponse(
                    {"message": "Phiên truy cập không hợp lệ. Tải lại trang hoặc mở lại ứng dụng."},
                    status_code=401,
                )
            # Bound incoming JSON before the framework parses it (also covers chunked requests).
            if (
                "/upload/" in request.url.path and request.url.path.endswith("/part")
            ) or request.url.path.startswith("/api/projects/import-file"):
                return await call_next(request)
            size = 0
            body = bytearray()
            limit = 32 * 1024 * 1024 if '/docx-preview/' in request.url.path and request.method == 'PUT' else 12 * 1024 * 1024
            async for chunk in request.stream():
                size += len(chunk)
                if size > limit:
                    return JSONResponse(
                        {"message": "Request too large"}, status_code=413
                    )
                body.extend(chunk)
            request._body = bytes(body)
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        if request.url.path.startswith("/api/"):
            response.headers["Cache-Control"] = "no-store"
        return response

    @app.get("/api/health")
    def health():
        return {
            "version": "0.1.0",
            "stack": "python-nextjs-web" if web else "python-nextjs-desktop",
            "mode": "local-web" if web else "desktop",
            "database": "mongodb" if getattr(store, 'db', None) is not None else "filesystem",
            "latexmk": compiler.executable,
            "capabilities": {
                "storage": "server" if store.managed else "local",
                "chooseProjectDirectory": not store.managed,
                "importServerPath": not store.managed,
            },
        }

    if web:
        @app.post("/api/web/session")
        def browser_session(request: Request):
            response = JSONResponse({"mode": "local-web"})
            response.set_cookie("overleaf_local_session", token, httponly=True, samesite="strict", secure=request.url.scheme == "https", path="/api")
            return response

    @app.get("/api/templates")
    def templates():
        return store.templates.public()

    @app.get("/api/projects")
    def projects():
        return store.list()

    @app.post("/api/projects", status_code=201)
    def create_project(body: ProjectBody):
        return store.create(
            body.title, body.template, body.language, body.parent, body.icon, body.color
        )

    @app.get("/api/projects/{project}/files")
    def files(project: str):
        return store.tree(project)

    @app.get("/api/projects/{project}/binary")
    def binary(project: str, path: str):
        import mimetypes
        source = store.path(project, path)
        if source.suffix.lower() not in {'.pdf', '.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'} or not source.is_file():
            raise Problem("Chọn ảnh hoặc PDF có trong dự án.", 404)
        if source.stat().st_size > 100 * 1024 * 1024:
            raise Problem("Tệp vượt giới hạn 100 MiB.", 413)
        return FileResponse(source, media_type=mimetypes.guess_type(source.name)[0] or 'application/octet-stream', filename=source.name, content_disposition_type='attachment' if source.suffix.lower() == '.svg' else 'inline', headers={'Content-Security-Policy': "sandbox; default-src 'none'"})

    @app.get("/api/projects/{project}/file")
    def read_file(project: str, path: str):
        if (
            getattr(store, "network", None)
            and store.network.joined
            and store.network.project == project
            and path != "main.tex"
        ):
            raise Problem(
                "Phòng cộng tác chỉ đồng bộ main.tex. Rời phòng để mở tệp khác."
            )
        content = store.read(project, path)
        try:
            store.history.capture(
                project,
                path,
                content,
                "Opened",
                (store.state.session or {}).get("name", "Local"),
            )
        except (Problem, OSError):
            pass
        return {"content": content}

    @app.put("/api/projects/{project}/file")
    def save_file(project: str, body: FileBody):
        with store.lock:
            if body.expected is not None:
                current = store.read(project, body.path)
                if current != body.expected and current != body.content:
                    raise Problem('Tệp đã được người khác thay đổi. Nội dung chưa lưu của bạn được giữ lại; tải bản mới và đối chiếu trước khi lưu.', 409)
            store.save(project, body.path, body.content)
        compiler.dirty.discard(project)
        return {"saved": True}

    @app.post("/api/projects/{project}/file", status_code=201)
    def add_file(project: str, body: FileBody):
        store.add(project, body.path, body.directory)
        return {"created": True}

    @app.post("/api/projects/{project}/compile")
    async def compile_project(project: str, body: CompileBody):
        job = compiler.start(project, body.engine, body.main, force=body.force)

        async def events():
            try:
                while True:
                    event = await job.queue.get()
                    yield json.dumps(event, ensure_ascii=False) + "\n"
                    if event["type"] == "end":
                        break
            finally:
                if compiler.active is job:
                    await compiler.cancel(project)

        return StreamingResponse(events(), media_type="application/x-ndjson")

    @app.post("/api/projects/{project}/cancel")
    async def cancel(project: str):
        await compiler.cancel(project)
        return {"cancelled": True}

    @app.get("/api/projects/{project}/pdf/{snapshot}")
    def pdf(project: str, snapshot: str):
        result = compiler.verified(project, snapshot)
        return Response(
            result.data,
            media_type="application/pdf",
            headers={"Content-Disposition": "inline; filename*=UTF-8''" + quote(Path(result.main or "main.tex").stem + '.pdf')},
        )

    from latex_backend.http.parity_api import register

    register(app, store, compiler)
    if frontend and frontend.is_dir():
        app.mount("/", StaticFiles(directory=frontend, html=True), name="frontend")
    return app

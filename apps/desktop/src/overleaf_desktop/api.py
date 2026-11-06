from __future__ import annotations

import json
import secrets
import asyncio
from contextlib import asynccontextmanager
from pathlib import Path
from urllib.parse import urlsplit, quote

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, Response, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from .compiler import Compiler
from .store import Problem, Store


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


class CompileBody(BaseModel):
    engine: str = "pdflatex"
    main: str = Field(default="", max_length=512)


def create_api(
    store: Store, compiler: Compiler, token: str, frontend: Path | None = None
) -> FastAPI:
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

    @app.middleware("http")
    async def local_session(request: Request, call_next):
        if request.url.hostname not in {"127.0.0.1", "localhost"}:
            return JSONResponse({"message": "Invalid local host"}, status_code=403)
        if request.url.path.startswith("/api/"):
            expected = "Bearer " + token
            if not secrets.compare_digest(
                request.headers.get("authorization", "").encode("utf-8"),
                expected.encode("ascii"),
            ):
                return JSONResponse(
                    {"message": "Phiên desktop không hợp lệ. Mở lại ứng dụng."},
                    status_code=401,
                )
            origin = request.headers.get("origin")
            if origin and urlsplit(origin).netloc != request.url.netloc:
                return JSONResponse({"message": "Invalid origin"}, status_code=403)
            # Bound incoming JSON before the framework parses it (also covers chunked requests).
            if (
                "/upload/" in request.url.path and request.url.path.endswith("/part")
            ) or request.url.path.startswith("/api/projects/import-file"):
                return await call_next(request)
            size = 0
            body = bytearray()
            async for chunk in request.stream():
                size += len(chunk)
                if size > 12 * 1024 * 1024:
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
            "stack": "python-nextjs-desktop",
            "latexmk": compiler.executable,
        }

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
        store.save(project, body.path, body.content)
        compiler.dirty.discard(project)
        return {"saved": True}

    @app.post("/api/projects/{project}/file", status_code=201)
    def add_file(project: str, body: FileBody):
        store.add(project, body.path, body.directory)
        return {"created": True}

    @app.post("/api/projects/{project}/compile")
    async def compile_project(project: str, body: CompileBody):
        job = compiler.start(project, body.engine, body.main)

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

    from .parity_api import register

    register(app, store, compiler)
    if frontend and frontend.is_dir():
        app.mount("/", StaticFiles(directory=frontend, html=True), name="frontend")
    return app

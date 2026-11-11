from __future__ import annotations

import argparse
import asyncio
import os
import secrets
import socket
import threading
import time
import webbrowser
from pathlib import Path

import uvicorn

from latex_backend.http.api import create_api
from latex_backend.compilation.compiler import Compiler
from latex_backend.storage.store import REPO, Store


class DesktopBridge:
    """Only a verified PDF can reach the native save dialog; no generic filesystem bridge."""

    def __init__(self, compiler: Compiler):
        self._compiler = compiler
        self._window = None
        self._address = ""
        self._children = []
        self._primary_window = None
        self._pending = None
        self._pending_sequence = -1
        self._pending_lock = threading.Lock()

    def stage_source(
        self, project: str, file: str, content: str, dirty: bool, sequence: int
    ):
        # Closing callbacks run on the native UI thread: never evaluate JavaScript
        # from there, which can deadlock WebView2. Keep the latest buffer in Python.
        self._compiler.store.path(project, file)
        if len(content.encode("utf-8")) > 2 * 1024 * 1024:
            return {"error": "Source exceeds 2 MiB"}
        with self._pending_lock:
            if sequence >= self._pending_sequence:
                self._pending_sequence = sequence
                self._pending = {
                    "project": project,
                    "file": file,
                    "content": content,
                    "dirty": bool(dirty),
                }
        return {"staged": True}

    def _flush_source(self):
        try:
            if hasattr(self._compiler, "pdf_word_exporter"):
                self._compiler.pdf_word_exporter.cancel()
            with self._pending_lock:
                pending = self._pending
            if pending and pending.get("dirty"):
                self._compiler.store.save(
                    pending["project"], pending["file"], pending["content"]
                )
                self._compiler.dirty.discard(pending["project"])
        except Exception:
            return False

    def pick_directory(self):
        import webview

        selected = self._window.create_file_dialog(webview.FileDialog.FOLDER)
        return str(selected[0]) if selected else None

    def upload_files(self, project: str, destination: str = "", folder: bool = False):
        import webview

        selected = self._window.create_file_dialog(
            webview.FileDialog.FOLDER if folder else webview.FileDialog.OPEN,
            allow_multiple=not folder,
        )
        if not selected:
            return {"cancelled": True}
        return self._compiler.store.io.upload_paths(project, selected, destination)

    def import_project(
        self, kind: str, title: str = "", parent: str = "", language: str = "vi"
    ):
        import webview

        if kind not in {"zip", "word", "markdown", "folder"}:
            return {"error": "Kiểu nhập không hợp lệ."}
        types = {
            "zip": ("ZIP (*.zip)",),
            "word": ("Word (*.docx)",),
            "markdown": ("Markdown (*.md;*.markdown)",),
        }
        selected = self._window.create_file_dialog(
            webview.FileDialog.FOLDER if kind == "folder" else webview.FileDialog.OPEN,
            file_types=types.get(kind, ()),
        )
        if not selected:
            return {"cancelled": True}
        try:
            return self._compiler.store.io.import_project(
                kind, str(selected[0]), title, parent, language
            )
        except Exception as error:
            return {"error": str(error)}

    def import_path(
        self,
        kind: str,
        source: str,
        title: str = "",
        parent: str = "",
        language: str = "vi",
    ):
        if kind not in {"zip", "word", "markdown", "folder"}:
            return {"error": "Kiểu nhập không hợp lệ."}
        try:
            return self._compiler.store.io.import_project(
                kind, str(source), title, parent, language
            )
        except Exception as error:
            return {"error": str(error)}

    def migrate_java(self):
        selected = self.pick_directory()
        if not selected:
            return {"cancelled": True}
        try:
            return self._compiler.store.io.import_java(selected)
        except Exception as error:
            return {"error": str(error)}

    def profile_picture(self):
        import webview

        selected = self._window.create_file_dialog(
            webview.FileDialog.OPEN, file_types=("Images (*.png;*.jpg;*.jpeg;*.gif)",)
        )
        if not selected:
            return {"cancelled": True}
        try:
            if Path(selected[0]).stat().st_size > 5 * 1024 * 1024:
                return {"error": "Ảnh đại diện vượt 5 MiB."}
            self._compiler.store.state.avatar(Path(selected[0]).read_bytes())
            return {"saved": True}
        except Exception as error:
            return {"error": str(error)}

    def open_binary(self, project: str, name: str):
        import subprocess
        import sys

        target = self._compiler.store.path(project, name)
        if (
            target.suffix.lower()
            not in {".pdf", ".png", ".jpg", ".jpeg", ".gif", ".svg"}
            or not target.is_file()
        ):
            return {"error": "Chỉ mở ảnh/PDF bằng ứng dụng ngoài."}
        if os.name == "nt":
            os.startfile(target)
        else:
            subprocess.Popen(
                ["open" if sys.platform == "darwin" else "xdg-open", str(target)]
            )
        return {"opened": True}

    def detach_pdf(self, project: str, main: str = ""):
        import webview
        from urllib.parse import quote

        if main:
            self._compiler.store.path(project, main)

        if not self._address:
            return {"error": "Chưa có cửa sổ desktop."}
        child_bridge = DesktopBridge(self._compiler)
        child_bridge._address = self._address
        child_bridge._primary_window = self._primary_window or self._window
        window = webview.create_window(
            "PDF — Latex",
            self._address + "&project=" + project + "&pdf=true&main=" + quote(main, safe=''),
            js_api=child_bridge,
            width=850,
            height=950,
        )
        child_bridge._window = window
        self._children.append(window)

        def reattach():
            if window in self._children:
                self._children.remove(window)
            try:
                self._window.evaluate_js(
                    "window.dispatchEvent(new Event('pdf-reattach'))"
                )
            except Exception:
                pass

        window.events.closed += reattach
        return {"opened": True}

    def close_pdf_windows(self):
        for window in list(self._children):
            window.destroy()
        self._children.clear()
        return {"closed": True}

    def focus_source(self, project: str, file: str, line: int, column: int):
        import json

        self._compiler.store.path(project, file)
        primary = self._primary_window or self._window
        detail = json.dumps(
            {
                "project": project,
                "file": file,
                "line": max(1, int(line)),
                "column": max(0, int(column)),
            }
        )
        primary.evaluate_js(
            "window.dispatchEvent(new CustomEvent('synctex-source',{detail:"
            + detail
            + "}))"
        )
        primary.show()
        return {"focused": True}

    def export_pdf(self, project: str, snapshot: str) -> dict:
        import webview

        try:
            pdf = self._compiler.verified(project, snapshot)
            selected = self._window.create_file_dialog(
                webview.FileDialog.SAVE,
                save_filename=Path(pdf.main or "main.tex").stem + ".pdf",
                file_types=("PDF files (*.pdf)",),
            )
            if not selected:
                return {"cancelled": True}
            target = Path(
                selected[0] if isinstance(selected, (list, tuple)) else selected
            )
            if target.suffix.lower() != ".pdf":
                return {"error": "Tên tệp cần đuôi .pdf."}
            # Verify again after the user spends time in the save dialog.
            self._compiler.verified(project, snapshot)
            from latex_backend.storage.store import atomic_write

            atomic_write(target, pdf.data)
            return {"saved": True}
        except Exception as error:
            return {"error": str(error)}

    def export_docx(self, project: str, snapshot: str) -> dict:
        import webview
        from latex_backend.documents.docx_workspace import download, DocxWorkspace
        from latex_backend.storage.store import atomic_write

        try:
            data, revision = download(self._compiler, project, snapshot)
            pdf = self._compiler.verified(project, snapshot)
            selected = self._window.create_file_dialog(
                webview.FileDialog.SAVE,
                save_filename=Path(pdf.main or "main.tex").stem + ".docx",
                file_types=("Word document (*.docx)",),
            )
            if not selected:
                return {"cancelled": True}
            target = Path(
                selected[0] if isinstance(selected, (list, tuple)) else selected
            )
            if target.suffix.lower() != ".docx":
                return {"error": "Tên tệp cần đuôi .docx."}
            self._compiler.verified(project, snapshot)
            if revision and DocxWorkspace(self._compiler, pdf.main).status(project)['revision'] != revision:
                return {'error': 'Bản Word vừa đổi trong lúc chọn nơi lưu. Vui lòng xuất lại.'}
            atomic_write(target, data)
            return {"saved": True}
        except Exception as error:
            return {"error": str(error)}


def main() -> None:
    parser = argparse.ArgumentParser(description="Latex · Python + Next.js")
    parser.add_argument(
        "--home",
        type=Path,
        default=Path(os.getenv("OVERLEAF_PYTHON_HOME", "~/OverleafDesktopPython")),
    )
    parser.add_argument("--frontend", type=Path, default=REPO / "apps/frontend/out")
    parser.add_argument("--latexmk", default="")
    parser.add_argument(
        "--browser",
        action="store_true",
        help="Diagnostic browser mode; default opens a native desktop window",
    )
    parser.add_argument(
        "--smoke",
        action="store_true",
        help="Open a hidden native window, verify UI, then close",
    )
    args = parser.parse_args()
    if not (args.frontend / "index.html").is_file():
        parser.error(
            "Build giao diện trước: cd apps/frontend && npm ci && npm run build"
        )
    store = Store(args.home)
    compiler = Compiler(store, args.latexmk)
    token = secrets.token_urlsafe(32)
    app = create_api(store, compiler, token, args.frontend)
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    server = uvicorn.Server(uvicorn.Config(app, log_level="warning", access_log=False))
    thread = threading.Thread(target=lambda: server.run(sockets=[sock]), daemon=True)
    thread.start()
    deadline = time.monotonic() + 15
    while not server.started:
        if not thread.is_alive() or time.monotonic() > deadline:
            raise RuntimeError("Không khởi động được backend local.")
        time.sleep(0.05)
    address = f"http://127.0.0.1:{port}/#session={token}"
    try:
        if args.browser:
            webbrowser.open(address)
            print("Diagnostic browser mode. Ctrl+C to close the local backend.")
            thread.join()
        else:
            import webview

            bridge = DesktopBridge(compiler)
            window = webview.create_window(
                "Latex",
                address,
                js_api=bridge,
                width=1440,
                height=940,
                min_size=(1000, 680),
                background_color="#f7f8fa",
                hidden=args.smoke,
                confirm_close=not args.smoke,
            )
            bridge._window = window
            bridge._address = address

            def flush_source():
                return bridge._flush_source()

            window.events.closing += flush_source
            smoke_result = []

            def check_window():
                try:
                    for _ in range(200):
                        connected = window.evaluate_js(
                            "document.querySelector('[data-desktop-ready]')?.getAttribute('data-desktop-ready')"
                        )
                        if connected == "true":
                            smoke_result.append(True)
                            print("PYTHON_DESKTOP_WINDOW_SMOKE_OK", flush=True)
                            break
                        time.sleep(0.1)
                finally:
                    window.destroy()

            webview.start(
                check_window if args.smoke else None,
                gui="edgechromium" if os.name == "nt" else None,
                private_mode=True,
            )
            if args.smoke and not smoke_result:
                raise RuntimeError(
                    "Native window loaded but UI smoke did not complete."
                )
    except KeyboardInterrupt:
        pass
    finally:
        if getattr(store, "network", None):
            store.network.leave()
        if getattr(store, "io", None):
            store.io.cancelled.set()
        if compiler.active and getattr(app.state, "loop", None):
            try:
                future = asyncio.run_coroutine_threadsafe(
                    compiler.cancel(compiler.active.project), app.state.loop
                )
                future.result(timeout=5)
            except (RuntimeError, TimeoutError):
                pass
        server.should_exit = True
        thread.join(timeout=5)
        sock.close()


if __name__ == "__main__":
    main()

from __future__ import annotations

import asyncio
import os
import shutil
import signal
import subprocess
import tempfile
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path

from .store import REPO, Problem, Store, atomic_write
from .explainer import explain_exit


def find_latexmk(configured: str = "") -> str:
    if configured or os.getenv("OVERLEAF_LATEXMK"):
        return configured or os.environ["OVERLEAF_LATEXMK"]
    found = shutil.which("latexmk")
    if found:
        return found
    candidates = (
        [REPO / "target/TinyTeX/bin/windows/latexmk.exe"]
        if os.name == "nt"
        else [Path("/Library/TeX/texbin/latexmk")]
    )
    return str(next((path for path in candidates if path.is_file()), Path("latexmk")))


@dataclass
class Snapshot:
    id: str
    fingerprint: str
    data: bytes
    index: bytes = b""
    engine: str = "pdflatex"
    geometry: list = field(default_factory=list)


@dataclass
class Job:
    project: str
    engine: str
    id: str = field(default_factory=lambda: uuid.uuid4().hex)
    queue: asyncio.Queue = field(default_factory=asyncio.Queue)
    task: asyncio.Task | None = None
    executable: str = ""
    timeout: float = 120


class Compiler:
    def _error(self, message, kind="failure"):
        return {
            "type": "error",
            "message": message,
            "issues": [
                explain_exit(kind, message, self.store.state.settings()["language"])
            ],
        }

    def __init__(self, store: Store, executable: str = "", timeout: int = 120):
        self.store, self.executable, self.timeout = (
            store,
            find_latexmk(executable),
            timeout,
        )
        self.active: Job | None = None
        self.snapshots: dict[str, Snapshot] = {}
        self.dirty = set()
        self.executable = find_latexmk(
            store.state.settings()["latexmkPath"] or executable
        )
        self.timeout = store.state.data["settings"].get(
            "compileTimeoutSeconds", timeout
        )

    def start(self, project: str, engine: str) -> Job:
        self.store.info(project)
        if self.active:
            raise Problem(
                "Đang có một lượt biên dịch. Dừng hoặc đợi lượt hiện tại.", 409
            )
        if engine not in {"pdflatex", "xelatex", "lualatex"}:
            raise Problem("Engine không hợp lệ.")
        job = Job(project, engine, executable=self.executable, timeout=self.timeout)
        self.active = job
        job.task = asyncio.create_task(self._run(job))
        return job

    async def cancel(self, project: str) -> None:
        job = self.active
        if job and job.project == project and job.task:
            job.task.cancel()
            try:
                await job.task
            except asyncio.CancelledError:
                pass

    def verified(self, project: str, snapshot: str) -> Snapshot:
        current = self.snapshots.get(project)
        if (
            not current
            or project in self.dirty
            or current.id != snapshot
            or current.fingerprint != self.store.fingerprint(project)
        ):
            raise Problem("PDF chưa khớp nội dung hiện tại. Cần biên dịch lại.", 409)
        return current

    @staticmethod
    async def _stop(process: asyncio.subprocess.Process | None) -> None:
        if not process or process.returncode is not None:
            return
        if os.name == "nt":
            killer = await asyncio.create_subprocess_exec(
                "taskkill",
                "/PID",
                str(process.pid),
                "/T",
                "/F",
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.DEVNULL,
            )
            await killer.wait()
        else:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        await process.wait()

    async def _run(self, job: Job) -> None:
        process = None
        staging = None
        start = time.monotonic()
        try:
            # The last successful PDF may still be displayed by the UI but is no longer exportable.
            self.snapshots.clear()
            fingerprint = await asyncio.to_thread(self.store.fingerprint, job.project)
            root = self.store.root(job.project)
            output = root / "build"
            if output.is_symlink():
                raise Problem("Thư mục build không được là symbolic link.")
            output.mkdir(exist_ok=True)
            working = root
            if os.name == "nt" and not str(root).isascii():
                staging = tempfile.TemporaryDirectory(prefix="overleaf-python-tex-")
                working = Path(staging.name)
                if not str(working).isascii():
                    raise Problem("Cần thư mục tạm ASCII cho TeX trên Windows.")

                def ignored(directory, names):
                    return [
                        name
                        for name in names
                        if name == "build"
                        or name.startswith(".")
                        or (Path(directory) / name).is_symlink()
                    ]

                await asyncio.to_thread(
                    shutil.copytree, root, working, dirs_exist_ok=True, ignore=ignored
                )
            # TeX needs matching nested auxiliary directories for include commands.
            for entry in self.store.tree(job.project):
                if entry["directory"]:
                    (working / "build" / entry["path"]).mkdir(
                        parents=True, exist_ok=True
                    )
            (working / "build").mkdir(exist_ok=True)
            main = self.store.info(job.project)["main"]
            self.store.path(job.project, main)
            # latexmk remembers failed runs. Installing a missing package without
            # editing the source otherwise just repeats the old cached failure.
            name = Path(main).stem
            failed = output / ".overleaf-build-failed"
            previous_log = output / (name + ".log")
            fatal = False
            if previous_log.is_file():
                with previous_log.open("rb") as stream:
                    stream.seek(max(0, previous_log.stat().st_size - 16384))
                    tail = stream.read(16384)
                fatal = b"Fatal error occurred" in tail or b"Emergency stop" in tail
            force = failed.exists() or fatal or not (output / (name + ".pdf")).is_file()
            atomic_write(
                failed, b"Build started; remove only after successful PDF validation.\n"
            )
            environment = os.environ.copy()
            tool = shutil.which(job.executable) or job.executable
            environment["PATH"] = (
                str(Path(tool).absolute().parent)
                + os.pathsep
                + environment.get("PATH", "")
            )
            flags = {"pdflatex": "-pdf", "xelatex": "-xelatex", "lualatex": "-lualatex"}
            process = await asyncio.create_subprocess_exec(
                tool,
                *(["-g"] if force else []),
                flags[job.engine],
                "-interaction=nonstopmode",
                "-file-line-error",
                "-halt-on-error",
                "-synctex=1",
                "-outdir=build",
                main,
                cwd=working,
                env=environment,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.STDOUT,
                **(
                    {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP}
                    if os.name == "nt"
                    else {"start_new_session": True}
                ),
            )
            job.queue.put_nowait(
                {"type": "started", "job": job.id, "engine": job.engine}
            )

            async def pump():
                size = count = 0
                assert process and process.stdout
                while chunk := await process.stdout.read(4096):
                    if size < 512 * 1024 and count < 4096:
                        count += 1
                        remaining = 512 * 1024 - size
                        job.queue.put_nowait(
                            {
                                "type": "output",
                                "text": chunk[:remaining].decode(
                                    "utf-8", errors="replace"
                                ),
                            }
                        )
                        size += len(chunk[:remaining])
                return await process.wait()

            code = (
                await pump()
                if job.timeout == 0
                else await asyncio.wait_for(pump(), timeout=job.timeout)
            )
            if code != 0:
                from .toolchain import explain

                log_file = working / "build" / (Path(main).stem + ".log")
                diagnostics = (
                    log_file.read_text(encoding="utf-8", errors="replace")[
                        -4 * 1024 * 1024 :
                    ]
                    if log_file.is_file()
                    else ""
                )
                issues = explain(diagnostics, self.store.state.settings()["language"])
                job.queue.put_nowait({"type": "issues", "issues": issues})
                if issues:
                    job.queue.put_nowait(
                        {
                            "type": "output",
                            "text": "\n[TeX diagnostics]\n"
                            + "\n".join(
                                f"{item['file']}:{item['line']}: {item['message']}\n[EXPLAIN] {item['title']}\n{item.get('why', '')}\n{item['fix']}"
                                for item in issues
                            ),
                        }
                    )
                job.queue.put_nowait(
                    {
                        "type": "error",
                        "message": f"LaTeX kết thúc với mã {code}. Đọc lỗi đầu tiên trong Build output.",
                        "issues": issues,
                    }
                )
                return
            name = Path(main).stem
            log = working / "build" / f"{name}.log"
            if log.is_file() and "Missing character:" in log.read_text(
                encoding="utf-8", errors="replace"
            ):
                raise Problem(
                    "PDF thiếu ký tự. Kiểm tra font và engine trước khi xuất."
                )
            pdf = working / "build" / f"{name}.pdf"
            with pdf.open("rb") as source:
                data = source.read(128 * 1024 * 1024 + 1)
            if not data.startswith(b"%PDF-") or len(data) > 128 * 1024 * 1024:
                raise Problem("PDF không hợp lệ hoặc vượt 128 MiB.")
            if working != root:
                atomic_write(output / pdf.name, data)
            current = fingerprint == await asyncio.to_thread(
                self.store.fingerprint, job.project
            )
            from .synctex import index_bytes

            index = await asyncio.to_thread(index_bytes, working, main)
            if index:
                atomic_write(output / (name + ".synctex.gz"), index)
            from io import BytesIO
            from pypdf import PdfReader

            def page_geometry():
                document = PdfReader(BytesIO(data))
                return [
                    {
                        "media": [float(value) for value in page.mediabox],
                        "crop": [float(value) for value in page.cropbox],
                        "rotation": page.rotation,
                    }
                    for page in document.pages
                ]

            geometry = await asyncio.to_thread(page_geometry)
            self.snapshots[job.project] = Snapshot(
                job.id, fingerprint, data, index, job.engine, geometry
            )
            failed.unlink(missing_ok=True)
            self.store.state.activity("compile")
            job.queue.put_nowait(
                {
                    "type": "done",
                    "snapshot": job.id,
                    "current": current,
                    "duration_ms": round((time.monotonic() - start) * 1000),
                    "geometry": geometry,
                }
            )
        except asyncio.CancelledError:
            job.queue.put_nowait(self._error("Đã dừng biên dịch.", "cancel"))
        except TimeoutError:
            job.queue.put_nowait(
                self._error(f"Đã hết {job.timeout} giây biên dịch.", "timeout")
            )
        except FileNotFoundError:
            job.queue.put_nowait(
                self._error(
                    "Không tìm thấy công cụ hoặc PDF đầu ra. Cài/chọn latexmk và kiểm Build output.",
                    "start",
                )
            )
        except Exception as error:
            job.queue.put_nowait(
                self._error(
                    str(error), "glyph" if "thiếu ký tự" in str(error) else "failure"
                )
            )
        finally:
            await self._stop(process)
            if staging:
                await asyncio.to_thread(staging.cleanup)
            if self.active is job:
                self.active = None
            job.queue.put_nowait({"type": "end"})

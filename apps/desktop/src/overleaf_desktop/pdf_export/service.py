"""Convert verified immutable PDF bytes; recheck before delivering the DOCX."""

import hashlib
import os
import subprocess
import sys
import tempfile
import threading
from pathlib import Path
from ..store import Problem


class PDFWordExporter:
    def __init__(self, compiler):
        self.compiler = compiler
        self.lock = threading.Lock()
        self.process = None
        self.cache = None

    def cancel(self):
        process = self.process
        if process and process.poll() is None:
            process.kill()

    def export(self, project, snapshot):
        pdf = self.compiler.verified(project, snapshot)
        key = (
            project,
            snapshot,
            hashlib.sha256(pdf.data).hexdigest(),
            "original-bundle-v2",
        )
        if not self.lock.acquire(blocking=False):
            raise Problem("Một lượt chuyển PDF sang Word đang chạy. Vui lòng chờ.", 409)
        try:
            if self.cache and self.cache[0] == key:
                self.compiler.verified(project, snapshot)
                return self.cache[1]
            if len(pdf.data) > 100 * 1024 * 1024:
                raise Problem("PDF vượt giới hạn chuyển Word 100 MiB.")
            with tempfile.TemporaryDirectory(prefix="overleaf-pdf-word-") as directory:
                root = Path(directory)
                source, output = root / "snapshot.pdf", root / "converted.docx"
                source.write_bytes(pdf.data)
                self.process = subprocess.Popen(
                    [
                        sys.executable,
                        "-X",
                        "utf8",
                        "-m",
                        "overleaf_desktop.pdf_export.worker",
                        str(source),
                        str(output),
                    ],
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
                )
                try:
                    logs, _ = self.process.communicate(timeout=180)
                except subprocess.TimeoutExpired as error:
                    self.cancel()
                    self.process.communicate()
                    raise Problem(
                        "Chuyển PDF sang Word quá thời gian 180 giây."
                    ) from error
                if self.process.returncode:
                    message = logs.decode("utf-8", errors="replace")[-2500:]
                    raise Problem("Không chuyển được PDF sang Word. " + message)
                result = output.read_bytes()
            self.compiler.verified(project, snapshot)
            self.cache = (key, result)
            return result
        except OSError as error:
            raise Problem(
                "Không khởi chạy/đọc được bộ chuyển PDF sang Word: " + str(error)
            ) from error
        finally:
            self.process = None
            self.lock.release()


def exporter(compiler):
    with compiler.store.lock:
        if not hasattr(compiler, "pdf_word_exporter"):
            compiler.pdf_word_exporter = PDFWordExporter(compiler)
        return compiler.pdf_word_exporter

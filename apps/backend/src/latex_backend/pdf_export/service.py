"""Convert verified immutable PDF bytes; recheck before delivering the DOCX."""

import hashlib
import os
import subprocess
import sys
import tempfile
import threading
import json
import zipfile
import io
import logging
from functools import lru_cache
from importlib.metadata import version
from pathlib import Path
from latex_backend.storage.store import Problem, atomic_write


@lru_cache(maxsize=1)
def converter_signature():
    # Deployment/restart invalidates cache when the original bundle or installed
    # engines change. Do not modify the user's converter to change its layout.
    root = Path(__file__).parent
    digest = hashlib.sha256(b'original-pdf-word-cache-v1')
    paths = [root / 'worker.py', *sorted((root / 'original').rglob('*'))]
    for path in paths:
        if path.is_file() and path.suffix in {'.py', '.json', '.png'}:
            digest.update(str(path.relative_to(root)).encode())
            digest.update(path.read_bytes())
    for package in ['pdf2docx', 'PyMuPDF', 'python-docx']:
        digest.update((package + ':' + version(package)).encode())
    return digest.hexdigest()


class PDFWordExporter:
    def __init__(self, compiler):
        self.compiler = compiler
        self.lock = threading.Lock()
        self.process = None
        self.cache = None
        self.closed = threading.Event()

    def cancel(self):
        self.closed.set()
        self._terminate()

    def _terminate(self):
        process = self.process
        if process and process.poll() is None:
            process.kill()

    def _folder(self, project):
        folder = self.compiler.store.root(project) / '.overleaf-pdf-word-cache'
        if folder.is_symlink():
            raise Problem('Word cache cannot be a symbolic link.')
        folder.mkdir(exist_ok=True)
        return folder

    def _read_cache(self, project, key):
        try:
            folder = self._folder(project)
            data_file, meta_file = folder / (key + '.docx'), folder / (key + '.json')
            if data_file.is_symlink() or meta_file.is_symlink() or meta_file.stat().st_size > 2048 or data_file.stat().st_size > 128 * 1024 * 1024:
                return None
            metadata = json.loads(meta_file.read_text(encoding='utf-8'))
            data = data_file.read_bytes()
            if metadata['sha256'] != hashlib.sha256(data).hexdigest():
                return None
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                if 'word/document.xml' not in archive.namelist():
                    return None
            return data
        except (OSError, ValueError, TypeError, KeyError, zipfile.BadZipFile):
            return None

    def _write_cache(self, project, key, data):
        folder = self._folder(project)
        atomic_write(folder / (key + '.docx'), data)
        atomic_write(folder / (key + '.json'), json.dumps({'sha256': hashlib.sha256(data).hexdigest()}).encode())
        # Bounded per-project disk cache; operate only on our exact SHA names.
        files = sorted((p for p in folder.glob('*.docx') if len(p.stem) == 64 and all(c in '0123456789abcdef' for c in p.stem) and not p.is_symlink()), key=lambda p:p.stat().st_mtime, reverse=True)
        size = 0
        for index, path in enumerate(files):
            size += path.stat().st_size
            if index >= 4 or size > 256 * 1024 * 1024:
                path.unlink(missing_ok=True)
                metadata = path.with_suffix('.json')
                if not metadata.is_symlink():
                    metadata.unlink(missing_ok=True)

    def export(self, project, snapshot):
        pdf = self.compiler.verified(project, snapshot)
        key = hashlib.sha256(pdf.data + converter_signature().encode()).hexdigest()
        memory_key = (project, key)
        if self.closed.is_set():
            raise Problem('Word converter is stopping.', 503)
        # One conversion for duplicate Word preview/download requests; wait for
        # the first result instead of launching another worker or returning 409.
        if not self.lock.acquire(timeout=185):
            raise Problem('Word conversion queue is busy. Try again shortly.', 503)
        try:
            if self.closed.is_set():
                raise Problem('Word converter is stopping.', 503)
            if self.cache and self.cache[0] == memory_key:
                self.compiler.verified(project, snapshot)
                return self.cache[1]
            cached = self._read_cache(project, key)
            if cached is not None:
                self.compiler.verified(project, snapshot)
                self.cache = (memory_key, cached)
                return cached
            if len(pdf.data) > 100 * 1024 * 1024:
                raise Problem("PDF vượt giới hạn chuyển Word 100 MiB.")
            from latex_backend.core.work_limits import work_limits
            with work_limits().converting(), tempfile.TemporaryDirectory(prefix="overleaf-pdf-word-") as directory:
                self.compiler.verified(project, snapshot)
                if self.closed.is_set():
                    raise Problem('Word converter is stopping.', 503)
                root = Path(directory)
                source, output = root / "snapshot.pdf", root / "converted.docx"
                source.write_bytes(pdf.data)
                self.process = subprocess.Popen(
                    [
                        sys.executable,
                        "-X",
                        "utf8",
                        "-m",
                        "latex_backend.pdf_export.worker",
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
                    self._terminate()
                    self.process.communicate()
                    raise Problem(
                        "Chuyển PDF sang Word quá thời gian 180 giây."
                    ) from error
                if self.process.returncode:
                    message = logs.decode("utf-8", errors="replace")[-2500:]
                    raise Problem("Không chuyển được PDF sang Word. " + message)
                result = output.read_bytes()
            self.compiler.verified(project, snapshot)
            try:
                self._write_cache(project, key, result)
            except OSError:
                logging.getLogger(__name__).warning('Could not persist Word cache; the verified conversion remains available.')
            self.cache = (memory_key, result)
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

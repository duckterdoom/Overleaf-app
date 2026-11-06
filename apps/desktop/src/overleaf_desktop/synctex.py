"""SyncTeX CLI integration with immutable per-PDF indexes and safe relative inputs."""

import gzip
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path
from .store import Problem


def index_bytes(working, main, output="build"):
    source = working / output / (Path(main).stem + ".synctex.gz")
    if not source.is_file():
        return b""
    with gzip.open(source, "rb") as stream:
        data = stream.read(128 * 1024 * 1024 + 1)
    if len(data) > 128 * 1024 * 1024:
        raise Problem("SyncTeX index vượt giới hạn.", 413)
    lines = []
    for line in data.decode("utf-8", errors="replace").splitlines():
        match = re.match(r"(Input:\d+:)(.*)", line)
        if match:
            value = Path(match[2].strip('"'))
            value = (working / value).absolute() if not value.is_absolute() else value
            value = Path(os.path.normpath(value))
            if value.is_relative_to(working.absolute()):
                line = match[1] + "./" + value.relative_to(working).as_posix()
        lines.append(line)
    return gzip.compress(("\n".join(lines) + "\n").encode())


class SyncTex:
    def __init__(self, store, compiler):
        self.store, self.compiler = store, compiler

    def query(
        self,
        project,
        snapshot,
        mode,
        file="main.tex",
        line=1,
        column=0,
        page=0,
        x=0,
        y=0,
    ):
        pdf = self.compiler.verified(project, snapshot)
        if not pdf.index:
            raise Problem("Biên dịch lại để tạo dữ liệu SyncTeX.")
        executable = (
            Path(shutil.which(self.compiler.executable) or self.compiler.executable)
            .absolute()
            .with_name("synctex.exe" if os.name == "nt" else "synctex")
        )
        if not executable.is_file():
            raise Problem("Thiếu synctex cạnh latexmk trong bộ TeX đã chọn.")
        with tempfile.TemporaryDirectory(prefix="overleaf-synctex-") as scratch:
            folder = Path(scratch)
            if os.name == "nt" and not str(folder).isascii():
                raise Problem("Cần thư mục tạm ASCII cho SyncTeX.")
            (folder / "main.pdf").write_bytes(b"%PDF-1.4\n")
            (folder / "main.synctex.gz").write_bytes(pdf.index)
            if mode == "forward":
                source = self.store.path(project, file)
                if source.suffix.lower() != ".tex" or not source.is_file():
                    raise Problem("Chọn tệp nguồn .tex trong dự án.")
                args = [
                    "view",
                    "-i",
                    f"{max(1, int(line))}:{max(0, int(column) + 1)}:./{file}",
                    "-o",
                    str(folder / "main.pdf"),
                ]
            elif mode == "inverse":
                import math

                if int(page) < 0 or not all(
                    math.isfinite(float(value)) for value in (x, y)
                ):
                    raise Problem("Tọa độ không hợp lệ.")
                args = [
                    "edit",
                    "-o",
                    f"{int(page) + 1}:{float(x):.5f}:{float(y):.5f}:{folder / 'main.pdf'}",
                ]
            else:
                raise Problem("Hướng SyncTeX không hợp lệ.")
            env = {
                key: value
                for key, value in os.environ.items()
                if key.upper() not in {"SYNCTEX_VIEWER", "SYNCTEX_EDITOR"}
            }
            with (folder / "result.txt").open("wb") as output:
                try:
                    result = subprocess.run(
                        [str(executable), *args],
                        cwd=folder,
                        env=env,
                        stdout=output,
                        stderr=subprocess.STDOUT,
                        timeout=10,
                        creationflags=subprocess.CREATE_NO_WINDOW
                        if os.name == "nt"
                        else 0,
                    )
                except subprocess.TimeoutExpired as error:
                    raise Problem("SyncTeX hết thời gian chờ.") from error
            fields, active = {}, False
            with (folder / "result.txt").open("rb") as stream:
                text = stream.read(65536).decode("utf-8", errors="replace")
            for row in text.splitlines():
                if row == "SyncTeX result begin":
                    active = True
                elif row == "SyncTeX result end":
                    break
                elif active and ":" in row:
                    key, value = row.split(":", 1)
                    fields.setdefault(key, value.strip())
            if result.returncode or not fields:
                raise Problem(
                    "Không tìm thấy vị trí SyncTeX; thử chọn đoạn văn gần đó."
                )
            try:
                if mode == "forward":
                    return {
                        "page": int(fields["Page"]) - 1,
                        "x": float(fields["x"]),
                        "y": float(fields["y"]),
                    }
                source = Path(fields["Input"])
                if source.is_absolute():
                    source = source.relative_to(self.store.root(project))
                file = source.as_posix().removeprefix("./")
                self.store.path(project, file)
                if not file.endswith(".tex"):
                    raise ValueError("Not a project source")
                return {
                    "file": file,
                    "line": max(1, int(fields["Line"])),
                    "column": max(0, int(fields.get("Column", "0")) - 1),
                }
            except (KeyError, ValueError) as error:
                raise Problem("Kết quả SyncTeX không hợp lệ.") from error

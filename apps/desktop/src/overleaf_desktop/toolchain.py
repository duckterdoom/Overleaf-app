"""Tool discovery/preflight and local rule-based error explanations (no AI service)."""

import os
import re
import shutil
import subprocess
from pathlib import Path
from .store import REPO, Problem
from .explainer import explain as explain

ALIASES = {
    "lmodern": ("t1lmr.fd", "lm"),
    "vntex": ("t5enc.def", "vntex"),
    "babel-vietnamese": ("vietnamese.ldf", "babel-vietnamese"),
    "amssymb": ("amssymb.sty", "amsfonts"),
    "amsthm": ("amsthm.sty", "amscls"),
    "graphicx": ("graphicx.sty", "graphics"),
    "tikz": ("tikz.sty", "pgf"),
    "beamer": ("beamer.cls", "beamer"),
    "fontenc": ("fontenc.sty", "latex"),
    "inputenc": ("inputenc.sty", "latex"),
}


def run(command, timeout=15):
    env = os.environ.copy()
    env["PATH"] = str(Path(command[0]).parent) + os.pathsep + env.get("PATH", "")
    try:
        return subprocess.run(
            [str(value) for value in command],
            capture_output=True,
            timeout=timeout,
            env=env,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None


def diagnostics(executable):
    root = Path(shutil.which(executable) or executable).absolute().parent
    result = []
    for name in [
        "latexmk",
        "pdflatex",
        "xelatex",
        "lualatex",
        "biber",
        "synctex",
        "kpsewhich",
    ]:
        path = root / (name + (".exe" if os.name == "nt" else ""))
        command = [
            path,
            "-v" if name == "latexmk" else "help" if name == "synctex" else "--version",
        ]
        tested = run(command)
        detail = (
            (tested.stdout + tested.stderr)
            .decode("utf-8", errors="replace")
            .splitlines()
            if tested
            else []
        )
        result.append(
            {
                "name": name,
                "path": str(path),
                "available": bool(tested and tested.returncode == 0),
                "detail": detail[0] if detail else "Not found",
            }
        )
    return result


def bundled(root):
    root = root.resolve()
    return any(
        (base / "texmf-dist").is_dir() and root.is_relative_to(base.resolve())
        for base in [REPO / "target/TinyTeX", REPO / "TinyTeX", REPO / "tools/TinyTeX"]
    )


def preflight(executable, template):
    root = Path(shutil.which(executable) or executable).absolute().parent
    suffix = ".exe" if os.name == "nt" else ""
    missing = []
    for package in template.get("requiredPackages", []):
        probe, install = ALIASES.get(package, (package + ".sty", package))
        result = run([root / ("kpsewhich" + suffix), probe])
        if not result or result.returncode or not result.stdout.strip():
            missing.append({"name": package, "package": install, "probe": probe})
    for name, package in [
        (
            template.get("engine", "pdflatex"),
            {"pdflatex": "latex", "xelatex": "xetex", "lualatex": "luatex"}.get(
                template.get("engine"), "latex"
            ),
        ),
        ("latexmk", "latexmk"),
        *(([("biber", "biber")]) if template.get("needsBiber") else []),
    ]:
        if not (root / (name + suffix)).is_file():
            missing.append({"name": name, "package": package, "probe": name})
    return {"missing": missing, "bundled": bundled(root), "bin": str(root)}


async def install_packages(compiler, template, confirmed):
    import asyncio

    if confirmed is not True:
        raise Problem("Cần xác nhận cài gói trước khi chạy tlmgr.")
    report = await asyncio.to_thread(preflight, compiler.executable, template)
    if not report["bundled"]:
        raise Problem("Không tự sửa TeX hệ thống. Cài gói bằng bộ quản lý TeX của bạn.")
    bin_path = Path(report["bin"])
    packages = sorted({item["package"] for item in report["missing"]})
    if not all(re.fullmatch(r"[a-zA-Z0-9_-]+", package) for package in packages):
        raise Problem("Tên gói không hợp lệ.")
    if not packages:
        return report
    distribution = next(
        base
        for base in [REPO / "target/TinyTeX", REPO / "TinyTeX", REPO / "tools/TinyTeX"]
        if bin_path.resolve().is_relative_to(base.resolve())
    )
    command = (
        [
            str(distribution / "tlpkg/tlperl/bin/perl.exe"),
            str(distribution / "texmf-dist/scripts/texlive/tlmgr.pl"),
        ]
        if os.name == "nt"
        else [str(bin_path / "tlmgr")]
    )
    env = os.environ.copy()
    env["PATH"] = str(bin_path) + os.pathsep + env.get("PATH", "")
    env["PERL5LIB"] = str(distribution / "tlpkg/tlperl/lib")
    process = await asyncio.create_subprocess_exec(
        *command,
        "install",
        *packages,
        env=env,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.STDOUT,
        **(
            {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP}
            if os.name == "nt"
            else {"start_new_session": True}
        ),
    )
    try:
        output, _ = await asyncio.wait_for(process.communicate(), 600)
        if process.returncode:
            raise Problem("tlmgr: " + output.decode("utf-8", errors="replace")[-6000:])
    finally:
        await compiler._stop(process)
    return await asyncio.to_thread(preflight, compiler.executable, template)

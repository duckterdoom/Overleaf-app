"""Native template matrix, SyncTeX and large document checks. Uses a fresh isolated home."""

import asyncio, json, sys
from pathlib import Path
from overleaf_desktop.store import Store, Problem
from overleaf_desktop.compiler import Compiler
from overleaf_desktop.synctex import SyncTex


async def build(compiler, project, engine):
    job = compiler.start(project, engine)
    events = []
    while True:
        event = await job.queue.get()
        events.append(event)
        if event["type"] == "end":
            break
    root = compiler.store.root(project) / "build"
    root.mkdir(exist_ok=True)
    (root / (engine + "-events.json")).write_text(
        json.dumps(events, ensure_ascii=False), encoding="utf8"
    )
    done = next((event for event in events if event["type"] == "done"), None)
    if not done:
        raise AssertionError(events[-5:])
    return compiler.verified(project, done["snapshot"]), done["duration_ms"]


async def main():
    home = Path(sys.argv[1]).absolute()
    if home.exists():
        raise RuntimeError("Choose a fresh home")
    matrix = "--matrix" in sys.argv
    store = Store(home)
    compiler = Compiler(store)
    results = []
    failures = []
    if matrix:
        for template in store.templates.items:
            for language in ["vi", "en"]:
                project = store.create("Parity A&B", template, language)["id"]
                for engine in ["pdflatex", "xelatex", "lualatex"]:
                    try:
                        snapshot, elapsed = await build(compiler, project, engine)
                        results.append(
                            {
                                "template": template,
                                "language": language,
                                "engine": engine,
                                "ms": elapsed,
                                "pages": len(snapshot.geometry),
                            }
                        )
                        print(
                            "MATRIX_OK", template, language, engine, elapsed, flush=True
                        )
                    except Exception as error:
                        failures.append(
                            {
                                "template": template,
                                "language": language,
                                "engine": engine,
                                "error": str(error),
                            }
                        )
                        print(
                            "MATRIX_FAIL",
                            template,
                            language,
                            engine,
                            str(error)[-600:],
                            flush=True,
                        )
        (home / "matrix.json").write_text(
            json.dumps({"passed": results, "failures": failures}, ensure_ascii=False),
            encoding="utf8",
        )
    for folder in ["ascii", "Tiếng Việt"]:
        local = Store(home / folder)
        compile_local = Compiler(local)
        for engine in ["pdflatex", "xelatex", "lualatex"]:
            project = local.create("SyncTeX", "blank", "en")["id"]
            local.add(project, "chapters/body.tex", False)
            local.save(
                project,
                "chapters/body.tex",
                "\\section{Included chapter}\nIncluded source text for native synchronization.\nAnother paragraph to locate.\n",
            )
            local.save(
                project,
                "main.tex",
                "\\documentclass{article}\n\\usepackage{hyperref}\n\\begin{document}\n\\tableofcontents\n\\newpage\n\\input{chapters/body.tex}\n\\end{document}\n",
            )
            snapshot, elapsed = await build(compile_local, project, engine)
            sync = SyncTex(local, compile_local)
            point = sync.query(
                project, snapshot.id, "forward", file="chapters/body.tex", line=2
            )
            result = sync.query(
                project,
                snapshot.id,
                "inverse",
                page=point["page"],
                x=point["x"],
                y=point["y"],
            )
            assert result["file"] == "chapters/body.tex" and 1 <= result["line"] <= 3, (
                point,
                result,
            )
            compile_local.dirty.add(project)
            try:
                sync.query(project, snapshot.id, "forward")
                raise AssertionError("Stale SyncTeX allowed")
            except Problem:
                pass
            print("SYNCTEX_NATIVE_OK", folder, engine, point, result, flush=True)
    project = store.create("248-page benchmark", "blank", "en")["id"]
    body = "".join(
        "\\section{Chapter "
        + str(i)
        + "}\nA local PDF performance benchmark. "
        + ("Text for scrolling and rendering. " * 50)
        + "\n"
        + ("\\newpage\n" if i < 248 else "")
        for i in range(1, 249)
    )
    store.save(
        project,
        "main.tex",
        "\\documentclass{article}\n\\usepackage{hyperref}\n\\begin{document}\n"
        + body
        + "\\end{document}\n",
    )
    snapshot, cold = await build(compiler, project, "pdflatex")
    snapshot, warm = await build(compiler, project, "pdflatex")
    assert len(snapshot.geometry) == 248, len(snapshot.geometry)
    (home / "benchmark.json").write_text(
        json.dumps(
            {
                "project": project,
                "pages": 248,
                "bytes": len(snapshot.data),
                "cold_ms": cold,
                "warm_ms": warm,
            }
        ),
        encoding="utf8",
    )
    print("LARGE_PDF_OK", 248, len(snapshot.data), cold, warm, flush=True)
    assert not failures, failures


asyncio.run(main())

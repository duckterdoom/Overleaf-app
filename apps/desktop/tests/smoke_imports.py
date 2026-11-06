"""Native Pandoc (Markdown/DOCX) and HTTPS import; fresh home required."""

import asyncio, os, sys
from pathlib import Path
from overleaf_desktop.store import Store, REPO
from overleaf_desktop.files import ProjectIO
from overleaf_desktop.compiler import Compiler


async def main():
    home = Path(sys.argv[1]).absolute()
    if home.exists():
        raise RuntimeError("Fresh home required")
    store = Store(home)
    io = ProjectIO(store)
    source = home / "test.md"
    source.write_text(
        "# Imported document\n\nA document with **bold**, math $x^2$ and a [link](https://example.org).\n",
        encoding="utf8",
    )
    pandoc = os.getenv("OVERLEAF_PANDOC") or str(REPO / "target/pandoc/pandoc.exe")
    docx = home / "test.docx"
    io.run_process([pandoc, str(source), "-o", str(docx)])
    for kind, path in [("markdown", source), ("word", docx)]:
        project = io.import_project(kind, str(path), kind, language="en")["id"]
        compiler = Compiler(store)
        job = compiler.start(project, "pdflatex")
        done = False
        while True:
            event = await job.queue.get()
            if event["type"] == "done":
                done = True
            if event["type"] == "error":
                raise AssertionError(event)
            if event["type"] == "end":
                break
        assert done
        print("PANDOC_IMPORT_NATIVE_OK", kind, flush=True)
    project = store.create("HTTPS")["id"]
    io.download(
        project,
        "https://raw.githubusercontent.com/latex3/latex2e/main/README.md",
        "references/README.md",
    )
    assert (store.root(project) / "references/README.md").stat().st_size > 100
    print("HTTPS_IMPORT_NATIVE_OK", flush=True)


asyncio.run(main())

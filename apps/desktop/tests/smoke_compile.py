"""Explicit native compiler smoke: python tests/smoke_compile.py <fresh-home>."""

import asyncio
import json
import sys
from pathlib import Path
from overleaf_desktop.compiler import Compiler
from overleaf_desktop.store import Store, Problem


async def main():
    root = Path(sys.argv[1]).absolute()
    if root.exists():
        raise RuntimeError("Use a new output directory")
    for folder in ["ascii", "Tiếng Việt"]:
        store = Store(root / folder)
        compiler = Compiler(store)
        for engine in ["pdflatex", "xelatex", "lualatex"]:
            project = store.create("Kiểm thử A&B", "research-article", "vi")
            job = compiler.start(project["id"], engine)
            events = []
            while True:
                event = await job.queue.get()
                events.append(event)
                if event["type"] == "end":
                    break
            (store.root(project["id"]) / "build/events.json").write_text(
                json.dumps(events, ensure_ascii=False), encoding="utf-8"
            )
            done = [event for event in events if event["type"] == "done"]
            assert done and done[0]["current"], events[-3:]
            snapshot = compiler.verified(project["id"], done[0]["snapshot"])
            assert snapshot.data.startswith(b"%PDF-")
            store.save(
                project["id"],
                project["main"],
                store.read(project["id"], project["main"]) + "\n% edit\n",
            )
            try:
                compiler.verified(project["id"], snapshot.id)
                raise AssertionError("Stale PDF exported")
            except Problem:
                pass
            print(f"PYTHON_NATIVE_PDF_OK {folder} {engine}", flush=True)
    print("PYTHON_NATIVE_COMPILE_SMOKE_OK builds=6", flush=True)


asyncio.run(main())

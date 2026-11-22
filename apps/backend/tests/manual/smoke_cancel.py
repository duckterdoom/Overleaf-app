"""Explicit native cancellation/timeout/recovery check, using a fresh isolated home."""

import asyncio
import sys
from pathlib import Path
from latex_backend.compilation.compiler import Compiler
from latex_backend.storage.store import Store


async def collect(job):
    events = []
    while True:
        event = await asyncio.wait_for(job.queue.get(), 20)
        events.append(event)
        if event["type"] == "end":
            return events


async def main():
    home = Path(sys.argv[1])
    if home.exists():
        raise RuntimeError("Use a new output directory")
    store = Store(home)
    project = store.create("Cancellation")
    identifier = project["id"]
    original = store.read(identifier, "main.tex")
    store.save(
        identifier,
        "main.tex",
        "\\documentclass{article}\n\\begin{document}\n\\loop\\iftrue\\repeat\n\\end{document}\n",
    )
    compiler = Compiler(store)
    job = compiler.start(identifier, "pdflatex")
    assert (await asyncio.wait_for(job.queue.get(), 10))["type"] == "started"
    await asyncio.sleep(0.2)
    await asyncio.wait_for(compiler.cancel(identifier), 15)
    events = await collect(job)
    assert any(event.get("message") == "Đã dừng biên dịch." for event in events)
    assert compiler.active is None
    compiler.timeout = 1
    events = await collect(compiler.start(identifier, "pdflatex"))
    assert any("hết 1 giây" in event.get("message", "") for event in events), events[
        -3:
    ]
    assert compiler.active is None
    store.save(identifier, "main.tex", original)
    compiler.timeout = 120
    events = await collect(compiler.start(identifier, "pdflatex"))
    done = next(event for event in events if event["type"] == "done")
    assert compiler.verified(identifier, done["snapshot"]).data.startswith(b"%PDF-")
    print("PYTHON_CANCEL_TIMEOUT_RECOVERY_SMOKE_OK", flush=True)


asyncio.run(main())

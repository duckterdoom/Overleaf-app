"""A package/font repair must rerun latexmk even when source files are unchanged."""

import asyncio
from pypdf import PdfWriter
from overleaf_desktop.compiler import Compiler
from overleaf_desktop.store import Store
from overleaf_desktop.explainer import explain_message


def test_failed_build_retries_across_restart_but_success_keeps_warm_cache(
    tmp_path, monkeypatch
):
    store = Store(tmp_path / "home")
    project = store.create("Recovery")["id"]
    root = store.root(project)
    calls = []

    class Process:
        def __init__(self, code):
            self.returncode = code
            self.stdout = asyncio.StreamReader()
            self.stdout.feed_eof()

        async def wait(self):
            return self.returncode

    async def launch(*args, **kwargs):
        calls.append(args)
        output = root / "build"
        if len(calls) == 1:
            (output / "main.log").write_text(
                "! LaTeX Error: File `footmisc.sty' not found.\nEmergency stop.\nFatal error occurred",
                encoding="utf8",
            )
            return Process(12)
        writer = PdfWriter()
        writer.add_blank_page(600, 800)
        writer.write(output / "main.pdf")
        (output / "main.log").write_text("Output written on main.pdf", encoding="utf8")
        return Process(0)

    monkeypatch.setattr(asyncio, "create_subprocess_exec", launch)

    async def compile():
        compiler = Compiler(store, executable="fake-latexmk")
        job = compiler.start(project, "pdflatex")
        events = []
        while True:
            event = await job.queue.get()
            events.append(event)
            if event["type"] == "end":
                return events

    first = asyncio.run(compile())
    assert any(e["type"] == "error" for e in first)
    assert (root / "build/.overleaf-build-failed").exists()
    second = asyncio.run(compile())
    assert any(e["type"] == "done" for e in second) and "-g" in calls[1]
    assert not (root / "build/.overleaf-build-failed").exists()
    asyncio.run(compile())
    assert "-g" not in calls[2]
    # Older app versions left no marker, only latexmk's fatal log and stale PDF.
    (root / "build/main.log").write_text(
        "Emergency stop.\nFatal error occurred", encoding="utf8"
    )
    asyncio.run(compile())
    assert "-g" in calls[3]


def test_palatino_metric_error_explains_missing_font_package_not_engine_switch():
    issue = explain_message(
        "Font T1/pplx/m/n/12=pplr9e at 12.0pt not loadable: Metric (TFM) file not found."
    )
    assert (
        issue["known"] and "pplr9e.tfm" in issue["why"] and "palatino" in issue["fix"]
    )
    assert "fontspec" not in issue["fix"]
    math = explain_message(
        "Font OML/zplm/m/it/14.4=zplmr7m at 14.4pt not loadable: Metric (TFM) file not found."
    )
    assert math["known"] and "mathpazo" in math["fix"] and "fontspec" not in math["fix"]

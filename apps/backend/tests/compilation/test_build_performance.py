import asyncio
import io
import threading
import time
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from pypdf import PdfWriter
from latex_backend.compilation.compiler import Compiler
from latex_backend.storage.store import Store
from latex_backend.pdf_export.service import PDFWordExporter
from test_pdf_word_export import make_snapshot


def test_compile_reuse_invalidates_on_source_dependency_force_and_clean(tmp_path, monkeypatch):
    store = Store(tmp_path / 'home')
    project = store.create('Cached compile')['id']
    root = store.root(project)
    tool = tmp_path / 'latexmk'
    tool.write_text('fake tool')
    package = tmp_path / 'package.sty'
    package.write_text('version 1')
    calls = []
    async def launch(*args, **kwargs):
        calls.append(args)
        writer = PdfWriter(); writer.add_blank_page(600,800)
        writer.write(root / 'build/main.pdf')
        (root / 'build/main.fls').write_text(f'INPUT {package}\nINPUT {root / "main.tex"}\n')
        process = type('Process', (), {})()
        process.returncode = 0
        process.stdout = asyncio.StreamReader()
        process.stdout.feed_data(b'original compile warning\n'); process.stdout.feed_eof()
        async def wait(): return 0
        process.wait = wait
        return process
    monkeypatch.setattr(asyncio, 'create_subprocess_exec', launch)
    compiler = Compiler(store, str(tool))
    async def build(force=False):
        job = compiler.start(project, 'pdflatex', force=force)
        events=[]
        while True:
            event = await job.queue.get(); events.append(event)
            if event['type'] == 'end': break
        return next(e for e in events if e['type']=='done'), events
    async def run():
        first, _ = await build()
        cached, events = await build()
        assert cached['cached'] and cached['snapshot'] == first['snapshot'] and len(calls)==1
        assert any('original compile warning' in e.get('text','') for e in events)
        store.save(project, 'main.tex', 'modified source')
        await build(); assert len(calls)==2
        package.write_text('new package version')
        await build(); assert len(calls)==3
        await build(force=True); assert len(calls)==4 and '-g' in calls[-1]
        package.unlink()
        await build(); assert len(calls)==5
        (root / 'build/main.fls').unlink()
        await build(); assert len(calls)==6
    asyncio.run(run())


def test_word_duplicate_requests_and_persistent_content_cache(tmp_path, monkeypatch):
    from latex_backend.pdf_export import service as module
    store = Store(tmp_path)
    project = store.create('Cached Word')['id']
    compiler = make_snapshot(store, project)
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as archive:
        archive.writestr('word/document.xml','<document>Cached original converter result</document>')
    expected=stream.getvalue()
    calls=[]
    class Process:
        returncode=0
        def __init__(self,args,**kwargs):
            calls.append(args)
            Path(args[6]).write_bytes(expected)
        def communicate(self,timeout=None):
            time.sleep(.1)
            return b'',None
    monkeypatch.setattr(module.subprocess,'Popen',Process)
    exporter=PDFWordExporter(compiler)
    with ThreadPoolExecutor(2) as pool:
        futures=[pool.submit(exporter.export,project,'snapshot-pdf-word') for _ in range(2)]
        assert [f.result() for f in futures] == [expected,expected]
    assert len(calls)==1
    # Snapshot IDs change on another compile, but identical verified PDF bytes
    # and converter version should reuse the per-project disk cache.
    compiler.snapshots[project].id='new-snapshot'
    assert PDFWordExporter(compiler).export(project,'new-snapshot')==expected
    assert len(calls)==1
    cached=next((store.root(project)/'.overleaf-pdf-word-cache').glob('*.docx'))
    cached.write_bytes(b'corrupted')
    assert PDFWordExporter(compiler).export(project,'new-snapshot')==expected
    assert len(calls)==2
    cached.with_suffix('.json').write_text('[]')
    assert PDFWordExporter(compiler).export(project,'new-snapshot')==expected
    assert len(calls)==3


def test_compile_queue_cancellation_does_not_leak_slot():
    from latex_backend.core.work_limits import WorkLimits
    async def run():
        limits=WorkLimits()
        while limits.compile.acquire(blocking=False): pass
        async def queued():
            async with limits.compiling(): pass
        task=asyncio.create_task(queued())
        await asyncio.sleep(.01)
        task.cancel()
        try: await task
        except asyncio.CancelledError: pass
        limits.compile.release()
        async with limits.compiling(): pass
    asyncio.run(run())

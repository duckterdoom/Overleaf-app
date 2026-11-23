"""Document roots must retain independent previews, build files and Word edits."""
import asyncio
import io
import zipfile
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from pypdf import PdfWriter
from latex_backend.http.api import create_api
from latex_backend.compilation.compiler import Compiler, Snapshot
from latex_backend.documents.docx_workspace import DocxWorkspace, download
from latex_backend.pdf_export.service import PDFWordExporter
from latex_backend.storage.store import Store, Problem


def test_compile_roots_keep_previews_and_isolate_failures_and_aux(tmp_path, monkeypatch):
    store = Store(tmp_path)
    project = store.create('Multiple documents')['id']
    store.add(project, 'reports', True)
    store.add(project, 'reports/main.tex', False)
    store.save(project, 'reports/main.tex', r'\documentclass{article}\begin{document}Report\end{document}')
    compiler = Compiler(store, executable='test-latexmk')
    fail = False
    outputs = []

    class Process:
        def __init__(self, code):
            self.returncode = code
            self.stdout = asyncio.StreamReader()
            self.stdout.feed_eof()

        async def wait(self):
            return self.returncode

    async def launch(*args, **kwargs):
        output = Path(kwargs['cwd']) / next(arg.split('=', 1)[1] for arg in args if arg.startswith('-outdir='))
        outputs.append(output)
        if fail:
            (output / 'main.log').write_text('! Undefined control sequence.\nEmergency stop.')
            return Process(12)
        writer = PdfWriter()
        writer.add_blank_page(600, 800)
        writer.add_metadata({'/Title': args[-1]})
        writer.write(output / 'main.pdf')
        return Process(0)

    monkeypatch.setattr(asyncio, 'create_subprocess_exec', launch)

    async def run(main):
        job = compiler.start(project, 'pdflatex', main)
        await job.task
        events = []
        while not job.queue.empty():
            events.append(job.queue.get_nowait())
        return events

    first = next(e for e in asyncio.run(run('main.tex')) if e['type'] == 'done')
    second = next(e for e in asyncio.run(run('reports/main.tex')) if e['type'] == 'done')
    assert outputs[0] != outputs[1]
    assert compiler.verified(project, first['snapshot']).main == 'main.tex'
    assert compiler.verified(project, second['snapshot']).main == 'reports/main.tex'
    with TestClient(create_api(store, compiler, 'test'), base_url='http://127.0.0.1', headers={'Authorization': 'Bearer test'}) as client:
        for event in [first, second]:
            assert client.get(f"/api/projects/{project}/preview/{event['snapshot']}").content.startswith(b'%PDF')
            assert client.get(f"/api/projects/{project}/watch", params={'main': event['main']}).json()['snapshot'] == event['snapshot']
    fail = True
    assert any(e['type'] == 'error' for e in asyncio.run(run('reports/main.tex')))
    assert compiler.verified(project, first['snapshot']).data.startswith(b'%PDF')
    with pytest.raises(Problem):
        compiler.verified(project, second['snapshot'])
    for path in ['../escape.tex', 'missing.tex', 'reports', 'main.pdf']:
        with pytest.raises(Problem):
            compiler.start(project, 'pdflatex', path)


def test_word_edits_are_scoped_to_the_compiled_root(tmp_path, monkeypatch):
    store = Store(tmp_path)
    project = store.create('Word roots')['id']
    store.add(project, 'ielts_report.tex', False)
    compiler = Compiler(store)
    fingerprint = store.fingerprint(project)
    compiler.documents[(project, 'main.tex')] = Snapshot('main-pdf', fingerprint, b'%PDF-main', main='main.tex')
    compiler.documents[(project, 'ielts_report.tex')] = Snapshot('report-pdf', fingerprint, b'%PDF-report', main='ielts_report.tex')

    def docx(value):
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w') as archive:
            archive.writestr(zipfile.ZipInfo('word/document.xml', (2020, 1, 1, 0, 0, 0)), '<document>' + value + '</document>')
        return data.getvalue()

    monkeypatch.setattr(PDFWordExporter, 'export', lambda self, project, snapshot: docx(snapshot))
    with TestClient(create_api(store, compiler, 'test'), base_url='http://127.0.0.1', headers={'Authorization': 'Bearer test'}) as client:
        route = f'/api/projects/{project}/docx-preview'
        main = client.post(route, json={'snapshot': 'main-pdf'}).json()
        report = client.post(route, json={'snapshot': 'report-pdf'}).json()
        edited = docx('EDITED REPORT')
        response = client.put(route + '/' + report['revision'], params={'main': 'ielts_report.tex'}, content=edited)
        assert response.status_code == 200
        assert client.get(route + '/' + main['revision'], params={'main': 'ielts_report.tex'}).status_code == 409
        assert download(compiler, project, 'main-pdf')[0] == docx('main-pdf')
        assert download(compiler, project, 'report-pdf')[0] == edited
        assert DocxWorkspace(compiler).read(project, main['revision']) == docx('main-pdf')
        store.save(project, 'main.tex', 'Changed source')
        assert client.post(route, json={'snapshot': 'report-pdf'}).json()['stale']
        assert client.get(f'/api/projects/{project}/pdf/report-pdf/docx').status_code == 409

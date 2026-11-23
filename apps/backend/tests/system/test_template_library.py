import asyncio
from types import SimpleNamespace

import pymupdf
from fastapi.testclient import TestClient

from latex_backend.core.application import create_web_app
from latex_backend.compilation.compiler import Compiler, Snapshot
from latex_backend.documents.template_preview import TemplatePreviews
from latex_backend.storage.store import Templates, Problem
from test_web_sharing import URL, account


def fake_compiler(calls, fail=False):
    def start(self, project, engine, main='', **kwargs):
        calls.append((project, engine, main))
        queue = asyncio.Queue()
        if fail:
            queue.put_nowait({'type': 'error', 'message': 'Missing package example.sty'})
        else:
            with pymupdf.open() as pdf:
                pdf.new_page().insert_text((50, 60), 'Real PDF fixture for a bundled template')
                data = pdf.tobytes()
            snapshot = Snapshot('template-test', self.store.fingerprint(project), data, main=main)
            self.snapshots[project] = snapshot
            queue.put_nowait({'type': 'done', 'snapshot': snapshot.id, 'current': True})
        queue.put_nowait({'type': 'end'})
        return SimpleNamespace(queue=queue, task=None)
    return start


def test_template_source_preview_cache_and_project_isolation(tmp_path, monkeypatch):
    calls = []
    monkeypatch.setattr(Compiler, 'start', fake_compiler(calls))
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        route = '/api/templates/research-article'
        assert client.get(route + '/source').status_code == 401
        account(client, 'templates@example.com', 'Template reader')
        source = client.get(route + '/source?language=en')
        source.raise_for_status()
        detail = source.json()
        assert detail['template']['id'] == 'research-article'
        files = {file['path']: file['content'] for file in detail['files']}
        assert '\\documentclass' in files['main.tex']
        assert '{{TITLE}}' not in files['main.tex']
        assert 'preamble.tex' in files
        assert any(content is None for content in files.values())
        pdf = client.get(route + '/pdf?language=en')
        pdf.raise_for_status()
        assert pdf.headers['content-type'] == 'application/pdf'
        assert pdf.content.startswith(b'%PDF-')
        thumb = client.get(route + '/thumbnail?language=en')
        thumb.raise_for_status()
        assert thumb.content.startswith(b'\x89PNG')
        assert client.get(route + '/pdf?language=en').content == pdf.content
        assert len(calls) == 1  # Thumbnail/PDF share one verified compile.
        assert client.get('/api/projects').json() == []
        assert client.get(route + '/source?language=invalid').status_code == 422
        for key in ['unknown-template', 'blank', 'example']:
            assert client.get('/api/templates/' + key + '/source').status_code == 404
        project = client.post('/api/projects', json={'title': 'My article', 'template': 'research-article', 'language': 'en'})
        project.raise_for_status()
        assert len(client.get('/api/projects').json()) == 1
        assert client.get(route + '/source?language=en').json() == detail


def test_failed_template_compile_never_returns_a_fake_pdf(tmp_path, monkeypatch):
    monkeypatch.setattr(Compiler, 'start', fake_compiler([], fail=True))
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        account(client, 'template-error@example.com', 'Reader')
        result = client.get('/api/templates/research-article/pdf')
        assert result.status_code == 422
        assert 'example.sty' in result.json()['message']
        assert client.get('/api/templates/research-article/source').status_code == 200
        assert client.get('/api/projects').json() == []


def test_preview_requests_are_deduplicated_bounded_and_cancelled(monkeypatch):
    async def exercise():
        previews = TemplatePreviews(Templates(), '')
        gate = asyncio.Event()
        async def build(self, key):
            await gate.wait()
            return {'pdf': b'fixture', 'thumbnail': b'fixture'}
        monkeypatch.setattr(TemplatePreviews, '_build', build)
        keys = [key for key, item in previews.templates.items.items() if item['category'] not in {'internal', 'ielts'}][:9]
        waiting = [asyncio.create_task(previews.preview(key, 'en')) for key in keys[:8]]
        await asyncio.sleep(0)
        duplicate = asyncio.create_task(previews.preview(keys[0], 'en'))
        await asyncio.sleep(0)
        assert len(previews.tasks) == 8
        try:
            await previews.preview(keys[8], 'en')
            raise AssertionError('Unbounded preview queue')
        except Problem as error:
            assert error.status == 429
        finally:
            await previews.close()
            await asyncio.gather(*waiting, duplicate, return_exceptions=True)
        assert not previews.tasks
    asyncio.run(exercise())

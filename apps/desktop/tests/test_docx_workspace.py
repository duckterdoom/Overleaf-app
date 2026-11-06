import io
import zipfile
import pytest
from overleaf_desktop.store import Store, Problem
from overleaf_desktop.compiler import Compiler, Snapshot
from overleaf_desktop.docx_workspace import DocxWorkspace, download
from overleaf_desktop.pdf_export.service import PDFWordExporter


def docx(text):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as archive:
        archive.writestr('[Content_Types].xml', '<Types/>')
        archive.writestr('word/document.xml', '<document>' + text + '</document>')
    return output.getvalue()


def test_docx_preview_keeps_original_bytes_and_exports_edits(tmp_path, monkeypatch):
    store = Store(tmp_path)
    project = store.create('DOCX')['id']
    compiler = Compiler(store)
    compiler.snapshots[project] = Snapshot('pdf', store.fingerprint(project), b'%PDF')
    original, changed = docx('original'), docx('edited')
    monkeypatch.setattr(PDFWordExporter, 'export', lambda *args: original)
    workspace = DocxWorkspace(compiler)
    first = workspace.open(project, 'pdf')
    assert workspace.read(project, first['revision']) == original
    assert download(compiler, project, 'pdf')[0] == original
    before = store.fingerprint(project)
    saved = workspace.save(project, first['revision'], changed)
    assert store.fingerprint(project) == before
    assert DocxWorkspace(compiler).read(project, saved['revision']) == changed
    assert download(compiler, project, 'pdf') == (changed, saved['revision'])
    with pytest.raises(Problem):
        workspace.save(project, first['revision'], changed)
    store.save(project, 'main.tex', 'Changed source')
    compiler.snapshots[project] = Snapshot('new-pdf', store.fingerprint(project), b'%PDF-new')
    assert workspace.open(project, 'new-pdf')['stale']
    with pytest.raises(Problem):
        download(compiler, project, 'new-pdf')
    fresh = workspace.open(project, 'new-pdf', True, saved['revision'])
    assert not fresh['edited'] and not fresh['stale']
    assert workspace.read(project, fresh['revision']) == original


def test_docx_preview_rejects_non_docx_payload(tmp_path):
    store = Store(tmp_path)
    project = store.create('Reject')['id']
    with pytest.raises(Problem):
        DocxWorkspace(Compiler(store)).commit(project, b'<html>not Word</html>', {})


def test_docx_preview_api_auth_revision_and_download(tmp_path, monkeypatch):
    from fastapi.testclient import TestClient
    from overleaf_desktop.api import create_api

    store = Store(tmp_path)
    project = store.create('API DOCX')['id']
    compiler = Compiler(store)
    compiler.snapshots[project] = Snapshot('pdf', store.fingerprint(project), b'%PDF')
    original, edited = docx('original'), docx('API edit')
    monkeypatch.setattr(PDFWordExporter, 'export', lambda *args: original)
    with TestClient(create_api(store, compiler, 'token'), base_url='http://127.0.0.1') as client:
        path = f'/api/projects/{project}/docx-preview'
        assert client.post(path, json={'snapshot': 'pdf'}).status_code == 401
        client.headers['Authorization'] = 'Bearer token'
        revision = client.post(path, json={'snapshot': 'pdf'}).json()['revision']
        assert client.get(path + '/' + revision).content == original
        response = client.put(path + '/' + revision, content=edited)
        assert response.status_code == 200 and response.json()['edited']
        assert client.put(path + '/' + revision, content=edited).status_code == 409
        assert client.get(f'/api/projects/{project}/pdf/pdf/docx').content == edited

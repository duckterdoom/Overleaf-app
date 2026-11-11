import asyncio
import io
import json
import zipfile
from html import escape
from types import SimpleNamespace

import pytest

from latex_backend.documents.docx_sync import plan
from latex_backend.documents.docx_workspace import DocxWorkspace
from latex_backend.compilation.compiler import Compiler, Snapshot
from latex_backend.pdf_export.service import PDFWordExporter
from latex_backend.storage.store import Store, Problem
from latex_backend.collaboration.live_documents import LiveDocuments, apply_changes


def docx(text):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w') as archive:
        archive.writestr('word/document.xml', '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>' + escape(text) + '</w:t></w:r></w:p></w:body></w:document>')
    return stream.getvalue()


def setup(tmp_path, text):
    store = Store(tmp_path)
    project = store.create('Word sync')['id']
    store.save(project, 'main.tex', text)
    return store, project


def test_native_word_text_edits_preserve_tex_and_escape_insertions(tmp_path):
    source = r'\documentclass{article}\begin{document}Hello beautiful world. \textbf{Second paragraph.} $x^2$\includegraphics{photo.png}\end{document}'
    store, project = setup(tmp_path, source)
    updates = plan(store, project, 'main.tex', docx('Hello beautiful world. Second paragraph.'), docx('Hello wonderful & bright world. Second paragraph.'))
    assert updates['main.tex'] == source.replace('beautiful', r'wonderful \& bright')
    # Pure deletion, then a repeated save against the new baseline.
    store.save(project, 'main.tex', updates['main.tex'])
    changed = plan(store, project, 'main.tex', docx('Hello wonderful & bright world. Second paragraph.'), docx('Hello wonderful & bright world. Second paragraph!'))
    assert changed['main.tex'].endswith(r'\textbf{Second paragraph!} $x^2$\includegraphics{photo.png}\end{document}')


def test_sync_secondary_root_and_include_without_touching_default(tmp_path):
    store, project = setup(tmp_path, 'Default document stays intact.')
    store.add(project, 'report.tex', False)
    store.add(project, 'body.tex', False)
    store.save(project, 'report.tex', r'\documentclass{article}\begin{document}\input{body}\end{document}')
    store.save(project, 'body.tex', 'Unique article text.')
    assert plan(store, project, 'report.tex', docx('Unique article text.'), docx('Unique revised article text.')) == {'body.tex': 'Unique revised article text.'}
    assert store.read(project, 'main.tex') == 'Default document stays intact.'


def test_ambiguous_text_or_crossing_command_is_rejected(tmp_path):
    store, project = setup(tmp_path, 'Repeated text.\nRepeated text.')
    with pytest.raises(Problem):
        plan(store, project, 'main.tex', docx('Repeated text.'), docx('Changed text.'))
    store.save(project, 'main.tex', r'Hello \textbf{beautiful} world.')
    with pytest.raises(Problem):
        plan(store, project, 'main.tex', docx('Hello beautiful world.'), docx('Goodbye.'))
    store.save(project, 'main.tex', r'\begin{equation}formula\end{equation}')
    with pytest.raises(Problem):
        plan(store, project, 'main.tex', docx('formula'), docx('replaced'))


def test_invalid_docx_and_format_only_do_not_change_source(tmp_path):
    store, project = setup(tmp_path, 'Article text.')
    assert plan(store, project, 'main.tex', docx('Article text.'), docx('Article text.')) == {}
    with pytest.raises(Problem):
        plan(store, project, 'main.tex', docx('Article text.'), b'invalid')
    assert store.read(project, 'main.tex') == 'Article text.'


def test_workspace_sync_stale_draft_and_recompile_fingerprint(tmp_path, monkeypatch):
    store, project = setup(tmp_path, r'\begin{document}Hello original world.\end{document}')
    compiler = Compiler(store)
    fingerprint = store.fingerprint(project)
    compiler.snapshots[project] = Snapshot('pdf', fingerprint, b'%PDF')
    monkeypatch.setattr(PDFWordExporter, 'export', lambda *args: docx('Hello original world.'))
    workspace = DocxWorkspace(compiler)
    first = workspace.open(project, 'pdf')
    saved = workspace.save(project, first['revision'], docx('Hello changed world.'))
    assert saved['synced_files'] == ['main.tex'] and saved['sync_state'] == 'synced'
    assert 'changed' in store.read(project, 'main.tex')
    assert not saved['stale'] and saved['fingerprint'] != fingerprint
    with pytest.raises(Problem):
        compiler.verified(project, 'pdf')
    store.save(project, 'main.tex', store.read(project, 'main.tex') + '% peer edit')
    draft = docx('Hello new word edit.')
    with pytest.raises(Problem):
        workspace.save(project, saved['revision'], draft)
    assert 'peer edit' in store.read(project, 'main.tex')
    assert workspace.read(project, saved['revision']) != draft
    assert (workspace.folder(project) / 'conflict.docx').read_bytes() == draft


def test_old_independent_word_draft_cannot_claim_complete_sync(tmp_path, monkeypatch):
    store, project = setup(tmp_path, 'Hello original world.')
    compiler = Compiler(store)
    compiler.snapshots[project] = Snapshot('pdf', store.fingerprint(project), b'%PDF')
    monkeypatch.setattr(PDFWordExporter, 'export', lambda *args: docx('Hello original world.'))
    workspace = DocxWorkspace(compiler)
    current = workspace.open(project, 'pdf')
    metadata_path = workspace.folder(project) / 'current.json'
    metadata = json.loads(metadata_path.read_text())
    metadata.pop('text_baseline')
    metadata['edited'] = True  # Simulate an edited draft saved by the old app.
    metadata_path.write_text(json.dumps(metadata))
    with pytest.raises(Problem, match='Word cũ'):
        workspace.save(project, current['revision'], docx('Hello revised world.'))
    assert store.read(project, 'main.tex') == 'Hello original world.'


def test_word_commit_broadcasts_ot_without_epoch_reset(tmp_path, monkeypatch):
    async def run():
        store, project = setup(tmp_path, 'Hello original world. 😀')
        compiler = Compiler(store)
        compiler.snapshots[project] = Snapshot('pdf', store.fingerprint(project), b'%PDF')
        monkeypatch.setattr(PDFWordExporter, 'export', lambda *args: docx('Hello original world. 😀'))
        workspace = DocxWorkspace(compiler)
        first = workspace.open(project, 'pdf')
        peers = {key: {'live': ('main.tex', key + ':browser'), 'queue': asyncio.Queue(32)} for key in ['owner', 'editor']}
        authority = LiveDocuments(SimpleNamespace(rooms={project: peers}))
        before = store.read(project, 'main.tex')
        authority.documents[(project, 'main.tex')] = {'text': before, 'epoch': 'existing-epoch', 'version': 4, 'updates': [], 'bytes': 0}
        result = await authority.external_edit(project, store, compiler, lambda: workspace.save(project, first['revision'], docx('Hello changed world. 😀')))
        assert result['sync_state'] == 'synced'
        for peer in peers.values():
            event = peer['queue'].get_nowait()
            assert event['epoch'] == 'existing-epoch' and event['from'] == 4 and event['version'] == 5
            assert apply_changes(before, event['updates'][0]['changes']) == 'Hello changed world. 😀'
        await authority.close()
    asyncio.run(run())


def test_shared_editor_word_api_updates_owner_and_both_sockets(tmp_path, monkeypatch):
    from fastapi.testclient import TestClient
    from latex_backend.core.application import create_web_app
    from test_web_sharing import account, use, URL
    from test_presence import connect
    from test_live_documents import event

    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as client:
        owner = account(client, 'word-owner@example.com', 'Owner')
        project = client.post('/api/projects', json={'title': 'Shared Word'}).json()['id']
        source = r'\begin{document}Shared original text.\end{document}'
        client.put(f'/api/projects/{project}/file', json={'path': 'main.tex', 'content': source}).raise_for_status()
        owner_id = app.accounts.resolve(owner)['id']
        compiler = app.tenants[owner_id][0].state.compiler
        compiler.snapshots[project] = Snapshot('pdf', compiler.store.fingerprint(project), b'%PDF')
        monkeypatch.setattr(PDFWordExporter, 'export', lambda *args: docx('Shared original text.'))
        route = f'/api/projects/{project}/docx-preview'
        revision = client.post(route, json={'snapshot': 'pdf'}).json()['revision']
        token = client.patch(f'/api/projects/{project}/sharing', json={'mode': 'link', 'permission': 'editor'}).json()['linkToken']
        editor = account(client, 'word-editor@example.com', 'Editor')
        client.post('/api/shares/accept', json={'token': token}).raise_for_status()
        with connect(client, project, owner) as a, connect(client, project, editor) as b:
            for ws, identity in [(a, owner), (b, editor)]:
                ws.send_json({'type': 'sync-open', 'file': 'main.tex', 'clientID': app.accounts.resolve(identity)['id'] + ':browser', 'epoch': '', 'version': 0})
                assert event(ws, 'sync-snapshot')['text'] == source
            response = client.put(route + '/' + revision, content=docx('Shared revised text.'))
            assert response.status_code == 200, response.text
            assert response.json()['synced_files'] == ['main.tex']
            for ws in [a, b]:
                update = event(ws, 'sync-updates')
                assert apply_changes(source, update['updates'][0]['changes']) == source.replace('original', 'revised')
        use(client, owner)
        assert 'revised' in client.get(f'/api/projects/{project}/file?path=main.tex').json()['content']

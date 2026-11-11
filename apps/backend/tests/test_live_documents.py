import pytest
import asyncio
import threading
from types import SimpleNamespace
from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from latex_backend.collaboration.live_documents import apply_changes, LiveDocuments
from latex_backend.storage.store import Problem
from test_presence import connect
from test_web_sharing import account, use, URL


def event(ws, kind):
    while True:
        result = ws.receive_json()
        if result['type'] == kind:
            return result


def test_changeset_utf16_multiline_and_invalid_lengths():
    assert apply_changes('a😀n\nb', [3, [1], 2]) == 'a😀\nb'
    assert apply_changes('ab', [1, [0, 'Tiếng Việt 😀', 'line 2'], 1]) == 'aTiếng Việt 😀\nline 2b'
    for change in [[1], [-1, 3], [[4]], ['bad'], [[1, 'has\nnewline'], 1]]:
        with pytest.raises(Problem):
            apply_changes('ab', change)


def test_live_delete_persist_broadcast_stale_retry_and_reload(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as c:
        owner = account(c, 'owner@example.com', 'Owner')
        project = c.post('/api/projects', json={'title':'Live'}).json()['id']
        c.put(f'/api/projects/{project}/file', json={'path':'main.tex','content':'anb'}).raise_for_status()
        link = c.patch(f'/api/projects/{project}/sharing', json={'mode':'link','permission':'editor'}).json()['linkToken']
        editor = account(c, 'editor@example.com', 'Editor')
        c.post('/api/shares/accept', json={'token':link}).raise_for_status()
        ids = [app.accounts.resolve(token)['id'] + ':browser' for token in [owner,editor]]
        with connect(c, project, owner) as a, connect(c, project, editor) as b:
            for ws, client in zip([a,b],ids):
                ws.send_json({'type':'sync-open','file':'main.tex','clientID':client,'epoch':'','version':0})
            first, second = event(a,'sync-snapshot'), event(b,'sync-snapshot')
            assert first['text'] == second['text'] == 'anb'
            epoch = first['epoch']
            assert epoch == second['epoch']
            b.send_json({'type':'sync-push','file':'main.tex','epoch':epoch,'version':0,'updates':[[1,[1],1]]})
            assert event(a,'sync-updates')['updates'][0]['clientID'] == ids[1]
            assert event(b,'sync-updates')['version'] == 1
            assert c.get(f'/api/projects/{project}/file?path=main.tex').json()['content'] == 'ab'
            # A stale push must not overwrite/deform the accepted deletion.
            a.send_json({'type':'sync-push','file':'main.tex','epoch':epoch,'version':0,'updates':[[1,[0,'X'],2]]})
            assert event(a,'sync-updates')['from'] == 0
            assert c.get(f'/api/projects/{project}/file?path=main.tex').json()['content'] == 'ab'
            # CodeMirror rebases locally and retries against version 1.
            a.send_json({'type':'sync-push','file':'main.tex','epoch':epoch,'version':1,'updates':[[1,[0,'X'],1]]})
            assert event(a,'sync-updates')['version'] == 2
            assert event(b,'sync-updates')['version'] == 2
        assert app.presence.live.documents == {}
        use(c, owner)
        assert c.get(f'/api/projects/{project}/file?path=main.tex').json()['content'] == 'aXb'
        history = c.get(f'/api/projects/{project}/history').json()
        assert any(h['author'] == 'Editor' for h in history)
        with connect(c, project, owner) as a:
            a.send_json({'type':'sync-open','file':'main.tex','clientID':ids[0],'epoch':'','version':0})
            assert event(a,'sync-snapshot')['text'] == 'aXb'


def test_disconnect_during_persist_finishes_version_and_broadcast():
    async def run():
        started, release = threading.Event(), threading.Event()
        class Store:
            owner = 'owner'
            lock = threading.RLock()
            text = 'anb'
            history = SimpleNamespace(capture=lambda *args: None)
            def read(self, *args):
                return self.text
            def save_live(self, project, path, text):
                started.set()
                assert release.wait(3)
                self.text = text
        store = Store()
        writer = {'live': ('main.tex', 'user:browser'), 'queue': asyncio.Queue(128)}
        reader = {'live': ('main.tex', 'other:browser'), 'queue': asyncio.Queue(128)}
        hub = LiveDocuments(SimpleNamespace(rooms={'project': {'writer':writer, 'reader':reader}},
            gateway=SimpleNamespace(accounts=SimpleNamespace(public=lambda user: user))))
        hub.documents[('project','main.tex')] = {'epoch':'epoch','text':'anb','version':0,'updates':[],'bytes':0}
        task = asyncio.create_task(hub.handle('project','writer',{'id':'user','name':'Editor'},store,
            SimpleNamespace(dirty=set()),{'type':'sync-push','file':'main.tex','epoch':'epoch','version':0,'updates':[[1,[1],1]]}))
        assert await asyncio.to_thread(started.wait, 3)
        task.cancel()
        release.set()
        with pytest.raises(asyncio.CancelledError):
            await task
        assert store.text == 'ab'
        assert hub.documents[('project','main.tex')]['version'] == 1
        assert (await reader['queue'].get())['version'] == 1
        await hub.close()
    asyncio.run(run())

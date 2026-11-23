import pytest
import asyncio
from types import SimpleNamespace
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
from latex_backend.core.application import create_web_app
from test_web_sharing import account, use, URL
from latex_backend.collaboration.presence import Presence


def connect(client, project, token, origin=URL):
    return client.websocket_connect(f'ws://127.0.0.1:8000/api/projects/{project}/presence',
                                    headers={'origin': origin, 'cookie': f'latex_account={token}'})


def test_named_presence_selection_file_scoping_and_disconnect(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as c:
        owner = account(c, 'owner@example.com', 'Real Owner')
        project = c.post('/api/projects', json={'title': 'Presence'}).json()['id']
        link = c.patch(f'/api/projects/{project}/sharing', json={'mode': 'link', 'permission': 'editor'}).json()['linkToken']
        editor = account(c, 'editor@example.com', 'Actual Editor')
        c.post('/api/shares/accept', json={'token': link}).raise_for_status()
        with connect(c, project, owner) as first:
            assert first.receive_json()['members'][0]['name'] == 'Real Owner'
            with connect(c, project, editor) as second:
                assert len(first.receive_json()['members']) == 2
                assert len(second.receive_json()['members']) == 2
                second.send_json({'file': 'main.tex', 'anchor': 1, 'head': 9, 'digest': 'a' * 64,
                                  'active': True, 'name': 'FAKE', 'userId': 'FAKE', 'color': 'FAKE'})
                state = first.receive_json()
                person = next(p for p in state['members'] if p['name'] == 'Actual Editor')
                assert person['anchor'] == person['head'] == 9
                assert person['file'] == 'main.tex' and person['active']
                assert person['color'].startswith('#') and person['userId'] != 'FAKE'
                second.receive_json()
            assert len(first.receive_json()['members']) == 1
        assert not app.presence.rooms


def test_presence_rejects_viewers_unsigned_cross_origin_and_bad_paths(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as c:
        owner = account(c, 'owner@example.com', 'Owner')
        project = c.post('/api/projects', json={'title': 'Private'}).json()['id']
        for token, origin in [('', URL), (owner, 'https://evil.example')]:
            with pytest.raises(WebSocketDisconnect):
                with connect(c, project, token, origin):
                    pass
        link = c.patch(f'/api/projects/{project}/sharing', json={'mode': 'link', 'permission': 'viewer'}).json()['linkToken']
        viewer = account(c, 'viewer@example.com', 'Viewer')
        c.post('/api/shares/accept', json={'token': link}).raise_for_status()
        with pytest.raises(WebSocketDisconnect):
            with connect(c, project, viewer):
                pass
        with connect(c, project, owner) as ws:
            ws.receive_json()
            ws.send_json({'file': '../outside.tex', 'anchor': 0, 'head': 0, 'digest': 'a' * 64})
            with pytest.raises(WebSocketDisconnect):
                ws.receive_json()
        assert not app.presence.rooms


def test_presence_revoked_live_session_disconnects(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as c:
        owner = account(c, 'owner@example.com', 'Owner')
        project = c.post('/api/projects', json={'title': 'Session'}).json()['id']
        with connect(c, project, owner) as ws:
            ws.receive_json()
            app.accounts.revoke(owner)
            with pytest.raises(WebSocketDisconnect):
                ws.receive_json()
        assert not app.presence.rooms


def test_presence_rooms_isolated_and_live_permission_downgrade(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as c:
        owner = account(c, 'owner@example.com', 'Owner')
        first = c.post('/api/projects', json={'title': 'First'}).json()['id']
        second = c.post('/api/projects', json={'title': 'Second'}).json()['id']
        route = f'/api/projects/{first}/sharing'
        link = c.patch(route, json={'mode': 'link', 'permission': 'editor'}).json()['linkToken']
        editor = account(c, 'editor@example.com', 'Editor')
        c.post('/api/shares/accept', json={'token': link}).raise_for_status()
        with connect(c, first, owner) as a, connect(c, second, owner) as b:
            assert [p['name'] for p in a.receive_json()['members']] == ['Owner']
            assert [p['name'] for p in b.receive_json()['members']] == ['Owner']
            with connect(c, first, editor) as e:
                assert len(e.receive_json()['members']) == 2
                a.receive_json()
                use(c, owner)
                c.patch(route, json={'mode': 'link', 'permission': 'viewer'}).raise_for_status()
                with pytest.raises(WebSocketDisconnect):
                    while True:
                        e.receive_json()
            assert all(p['member']['userId'] != app.accounts.resolve(editor)['id']
                       for p in app.presence.rooms[first].values())


def test_join_during_last_disconnect_does_not_create_orphan_room():
    """A real ASGI accept yields while the previous connection leaves the room."""
    async def run():
        accepted = asyncio.Event()
        snapshot = asyncio.Event()
        release = asyncio.Event()
        disconnect = asyncio.Event()
        user = {'id': 'user', 'name': 'Real User', 'role': 'tutor'}
        async def workspace(project, principal):
            return SimpleNamespace(state=SimpleNamespace(compiler=None)), None, 'editor'
        hub = Presence(SimpleNamespace(accounts=SimpleNamespace(resolve=lambda token: user),
                                       sharing=SimpleNamespace(workspace_role=lambda project, identifier: 'tutor'),
                                       project_workspace=workspace))
        hub.rooms['project'] = {'previous': {}}
        initial = True
        async def receive():
            nonlocal initial
            if initial:
                initial = False
                return {'type': 'websocket.connect'}
            await disconnect.wait()
            return {'type': 'websocket.disconnect', 'code': 1000}
        async def send(message):
            if message['type'] == 'websocket.accept':
                accepted.set()
                await release.wait()
            if message['type'] == 'websocket.send':
                snapshot.set()
        scope = {'type': 'websocket', 'path': '/api/projects/project/presence', 'scheme': 'ws',
                 'query_string': b'', 'server': ('127.0.0.1', 8000),
                 'headers': [(b'host', b'127.0.0.1:8000'), (b'origin', URL.encode()),
                             (b'cookie', b'latex_account=test')]}
        task = asyncio.create_task(hub.serve(scope, receive, send, 'project'))
        try:
            await asyncio.wait_for(accepted.wait(), 2)
            hub.rooms.pop('project')  # Last peer leaves during this accept.
            release.set()
            await asyncio.wait_for(snapshot.wait(), 2)
            assert len(hub.rooms['project']) == 1
        finally:
            disconnect.set()
            await asyncio.wait_for(task, 2)
        assert hub.rooms == {}
    asyncio.run(run())

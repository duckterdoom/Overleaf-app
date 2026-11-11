from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from latex_backend.http.web_accounts import COOKIE

URL = 'http://127.0.0.1:8000'


def account(client, email, name):
    client.cookies.clear()
    assert client.post('/api/actions/register', json={'name': name, 'email': email, 'password': 'test-password'}).status_code == 200
    assert client.post('/api/auth/onboarding', json={'name': name, 'role': 'student'}).status_code == 200
    return client.cookies.get(COOKIE)


def use(client, token):
    client.cookies.clear()
    client.cookies.set(COOKIE, token, path='/api')


def test_private_invitation_accept_permissions_and_revoke(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as c:
        owner = account(c, 'owner@example.com', 'Owner')
        project = c.post('/api/projects', json={'title': 'Private manuscript'}).json()['id']
        original = c.get(f'/api/projects/{project}/file?path=main.tex').json()['content']
        route = f'/api/projects/{project}/sharing'
        assert c.get(route).json()['mode'] == 'private'
        result = c.post(route + '/invitations', json={'emails': ['reader@example.com'], 'permission': 'viewer'})
        assert result.status_code == 200
        invitation = result.json()['members'][0]
        wrong = account(c, 'wrong@example.com', 'Wrong')
        assert c.post('/api/shares/accept', json={'token': invitation['inviteToken']}).status_code == 403
        reader = account(c, 'reader@example.com', 'Reader')
        assert c.get(f'/api/projects/{project}/files').status_code == 404
        accepted = c.post('/api/shares/accept', json={'token': invitation['inviteToken']})
        assert accepted.json()['permission'] == 'viewer' and accepted.json()['shared']
        assert c.get('/api/projects').json()[0]['id'] == project
        assert c.get(f'/api/projects/{project}/file?path=main.tex').json()['content'] == original
        assert c.put(f'/api/projects/{project}/file', json={'path': 'main.tex', 'content': 'bad'}).status_code == 403
        assert c.post(f'/api/projects/{project}/file', json={'path': 'bad.tex'}).status_code == 403
        for action in ['project-delete', 'project-update', 'history-restore', 'checkpoint', 'copy-file', 'clean']:
            assert c.post('/api/actions/' + action, json={'project': project}).status_code == 403
        assert c.patch(route, json={'mode': 'link'}).status_code == 403
        restricted = c.get(route).json()
        assert restricted['linkToken'] is None and restricted['members'] == []
        use(c, owner)
        assert c.patch(route + '/members/' + invitation['id'], json={'permission': 'editor'}).status_code == 200
        use(c, reader)
        assert c.put(f'/api/projects/{project}/file', json={'path': 'main.tex', 'content': 'New shared source', 'expected': original}).status_code == 200
        # Shared editors cannot delete/rename the owner's project or copy other private projects.
        assert c.post('/api/actions/project-delete', json={'project': project}).status_code == 403
        use(c, owner)
        assert c.get(f'/api/projects/{project}/file?path=main.tex').json()['content'] == 'New shared source'
        assert c.put(f'/api/projects/{project}/file', json={'path': 'main.tex', 'content': 'Stale owner edit', 'expected': original}).status_code == 409
        assert c.delete(route + '/members/' + invitation['id']).status_code == 200
        use(c, reader)
        assert c.get(f'/api/projects/{project}/files').status_code == 404
        assert c.get('/api/projects').json() == []
        assert c.post('/api/shares/accept', json={'token': invitation['inviteToken']}).status_code == 404


def test_public_link_rotation_downgrade_private_and_persistence(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as c:
        owner = account(c, 'owner@example.com', 'Owner')
        project = c.post('/api/projects', json={'title': 'Link manuscript'}).json()['id']
        route = f'/api/projects/{project}/sharing'
        token = c.patch(route, json={'mode': 'link', 'permission': 'editor'}).json()['linkToken']
        editor = account(c, 'editor@example.com', 'Editor')
        assert c.post('/api/shares/accept', json={'token': token}).json()['permission'] == 'editor'
        assert c.post('/api/actions/checkpoint', json={'project': project, 'path': 'main.tex', 'content': 'Checkpoint by editor'}).status_code == 200
        history = c.get(f'/api/projects/{project}/history').json()
        assert any(item['author'] == 'Editor' for item in history)
        use(c, owner)
        assert c.patch(route, json={'mode': 'link', 'permission': 'viewer'}).status_code == 200
        use(c, editor)
        assert c.get('/api/projects').json()[0]['permission'] == 'viewer'
        assert c.post('/api/actions/checkpoint', json={'project': project}).status_code == 403
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        use(c, editor)
        assert c.get('/api/projects').json()[0]['permission'] == 'viewer'
        use(c, owner)
        new_token = c.patch(route, json={'mode': 'link', 'rotate': True}).json()['linkToken']
        assert new_token != token
        use(c, editor)
        assert c.get(f'/api/projects/{project}/files').status_code == 404
        assert c.post('/api/shares/accept', json={'token': token}).status_code == 404
        assert c.post('/api/shares/accept', json={'token': new_token}).status_code == 200
        use(c, owner)
        assert c.patch(route, json={'mode': 'private'}).json()['linkToken'] is None
        use(c, editor)
        assert c.get(f'/api/projects/{project}/files').status_code == 404
        c.cookies.clear()
        assert c.post('/api/shares/accept', json={'token': new_token}).status_code == 401


def test_no_cross_project_escalation_or_web_tcp_room(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        owner = account(c, 'owner@example.com', 'Owner')
        first = c.post('/api/projects', json={'title': 'Shared'}).json()['id']
        second = c.post('/api/projects', json={'title': 'Secret'}).json()['id']
        c.get('/api/projects')
        token = c.patch(f'/api/projects/{first}/sharing', json={'mode': 'link', 'permission': 'editor'}).json()['linkToken']
        editor = account(c, 'editor@example.com', 'Editor')
        c.post('/api/shares/accept', json={'token': token})
        assert c.get(f'/api/projects/{second}/files').status_code == 404
        assert c.get(f'/api/projects/{second}/sharing').status_code == 404
        assert c.post('/api/actions/copy-file', json={'project': first, 'sourceProject': second, 'source': 'main.tex', 'target': 'stolen.tex'}).status_code == 403
        assert c.post('/api/actions/network-connect', json={'host': '127.0.0.1'}).status_code == 403
        assert c.post(f'/api/projects/{first}/sharing/invitations', json={'emails': ['other@example.com']}).status_code == 403
        assert c.get('/api/session').json()['session']['email'] == 'editor@example.com'

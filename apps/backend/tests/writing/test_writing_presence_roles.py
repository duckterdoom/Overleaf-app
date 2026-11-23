from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import signup
from test_writing_sharing import share
from test_presence import connect


def test_activity_ielts_uses_project_room_and_server_role_with_editor(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as c:
        owner = signup(c, 'owner@example.com', 'tutor')
        project = c.post('/api/projects', json={'title': 'Same workspace'}).json()['id']
        invitation = share(c, owner, project, 'member@example.com', 'student')
        member = signup(c, 'member@example.com', 'tutor')
        c.post('/api/shares/accept', json={'token': invitation['inviteToken']}).raise_for_status()
        with connect(c, project, owner) as editor, connect(c, project, member) as writing:
            writing.receive_json()
            def until(socket, predicate):
                while True:
                    event = socket.receive_json()
                    if predicate(event): return event
            writing.send_json({'type': 'activity', 'context': 'writing', 'location': 'IELTS Writing · Task 1',
                'active': True, 'workspaceRole': 'tutor', 'name': 'SPOOFED'})
            event = until(editor, lambda e: e['type'] == 'presence' and any(m.get('context') == 'writing' for m in e['members']))
            actual = next(m for m in event['members'] if m['context'] == 'writing')
            assert actual['workspaceRole'] == 'student' and actual['name'] != 'SPOOFED'
            assert actual['file'] == actual['digest'] == '' and actual['location'].endswith('Task 1')
            assert len(event['members']) == 2
            use(c, owner)
            c.patch(f'/api/projects/{project}/sharing/members/' + invitation['id'], json={'workspaceRole': 'tutor'}).raise_for_status()
            until(writing, lambda e: e['type'] == 'access-changed')
            assert c.get('/api/writing/overview?projectId=' + project).json()['role'] == 'tutor'
        assert not app.presence.rooms


def test_owner_project_role_and_overlapping_grants_display_effective_access(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        owner = signup(c, 'owner@example.com', 'student')
        project = c.post('/api/projects', json={'title': 'Roles'}).json()['id']
        route = f'/api/projects/{project}/sharing'
        configured = c.patch(route, json={'mode': 'link', 'permission': 'editor', 'workspaceRole': 'tutor', 'ownerRole': 'tutor'}).json()
        assert configured['workspaceRole'] == configured['owner']['workspaceRole'] == 'tutor'
        assert c.get('/api/session').json()['session']['role'] == 'student'
        link = configured['linkToken']
        member = signup(c, 'member@example.com', 'student')
        c.post('/api/shares/accept', json={'token': link}).raise_for_status()
        invitation = share(c, owner, project, 'member@example.com', 'student', 'viewer')
        assert invitation['status'] == 'accepted'  # One active person, not duplicate pending/link rows.
        assert invitation['permission'] == 'viewer'
        use(c, member)
        accepted = c.post('/api/shares/accept', json={'token': invitation['inviteToken']}).json()
        assert accepted['permission'] == 'viewer' and accepted['workspaceRole'] == 'student'
        use(c, owner)
        info = c.get(route).json()
        assert len(info['members']) == 1
        person = info['members'][0]
        c.patch(route + '/members/' + person['id'], json={'permission': 'editor', 'workspaceRole': 'tutor'}).raise_for_status()
        use(c, member)
        access = c.get(route).json()
        assert access['permission'] == 'editor' and access['workspaceRole'] == 'tutor'
        assert c.patch(route, json={'mode': 'link', 'ownerRole': 'student'}).status_code == 403
        use(c, owner)
        c.patch(route + '/members/' + person['id'], json={'permission': 'viewer', 'workspaceRole': 'student'}).raise_for_status()
        use(c, member)
        access = c.get(route).json()
        assert access['permission'] == 'viewer' and access['workspaceRole'] == 'student'

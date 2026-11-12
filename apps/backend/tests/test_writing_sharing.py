from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import signup, grade


def share(c, owner, project, email, role='student', permission='editor'):
    use(c, owner)
    response = c.post(f'/api/projects/{project}/sharing/invitations', json={
        'emails': [email], 'permission': permission, 'workspaceRole': role})
    response.raise_for_status()
    return next(row for row in response.json()['members'] if row['email'] == email)


def test_shared_project_roles_ielts_acl_drafts_and_editor_use_same_grants(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        owner = signup(c, 'owner@example.com', 'tutor')
        project = c.post('/api/projects', json={'title': 'Shared IELTS'}).json()['id']
        query = '?projectId=' + project
        invitation = share(c, owner, project, 'learner@example.com', 'student')
        # A global Tutor acts as Student only in this shared project.
        learner = signup(c, 'learner@example.com', 'tutor')
        assert c.post('/api/shares/accept', json={'token': invitation['inviteToken']}).json()['workspaceRole'] == 'student'
        assert c.get('/api/session').json()['session']['role'] == 'tutor'
        use(c, owner)
        classroom = c.post('/api/writing/classes' + query, json={'title': 'Shared class'}).json()
        assignment = c.post('/api/writing/assignments' + query, json={'title': 'Shared essay', 'classId': classroom['id'],
            'tasks': [2], 'prompts': {'2': 'Discuss both views.'}, 'profiles': {'2': 'discussion'}, 'published': True}).json()
        route = '/api/writing/assignments/' + assignment['id']
        use(c, learner)
        overview = c.get('/api/writing/overview' + query).json()
        assert overview['role'] == 'student' and overview['assignments'][0]['id'] == assignment['id']
        assert len(overview['classes']) == 1  # Sharing joins project classes without a second invitation.
        assert c.post('/api/writing/classes' + query, json={'title': 'Escalation'}).status_code == 403
        assert c.get(route + '/draft').status_code in (403, 404)  # Cannot bypass scope by dropping query.
        c.get(route + '/draft' + query).raise_for_status()
        c.put(route + '/draft' + query, json={'revision': 0, 'answers': {'2': 'A private submitted essay.'}}).raise_for_status()
        submitted = c.post(route + '/submit' + query, json={'revision': 1, 'requestId': 'request-shared-12345'}).json()
        review = '/api/writing/submissions/' + submitted['id']
        use(c, owner)
        c.put(review + '/review' + query, json=grade([2])).raise_for_status()
        use(c, learner)
        assert c.get(review + query).json()['review'] is None
        # A Student account invited as Tutor can grade the project without changing their account.
        invitation2 = share(c, owner, project, 'cotutor@example.com', 'tutor')
        cotutor = signup(c, 'cotutor@example.com', 'student')
        c.post('/api/shares/accept', json={'token': invitation2['inviteToken']}).raise_for_status()
        assert c.get('/api/writing/overview' + query).json()['role'] == 'tutor'
        assert c.get(review + query).json()['review']['revision'] == 1
        c.post(review + '/publish' + query, json={'revision': 1}).raise_for_status()
        # Downgrading permission blocks both editor writes and IELTS writes immediately.
        use(c, owner)
        member_route = f'/api/projects/{project}/sharing/members/' + invitation2['id']
        c.patch(member_route, json={'permission': 'viewer'}).raise_for_status()
        use(c, cotutor)
        assert c.post('/api/writing/classes' + query, json={'title': 'Blocked'}).status_code == 403
        assert c.put(f'/api/projects/{project}/file', json={'path': 'main.tex', 'content': 'Blocked'}).status_code == 403
        assert c.get(review + query).status_code == 200
        use(c, owner)
        c.patch(member_route, json={'workspaceRole': 'student'}).raise_for_status()
        use(c, cotutor)
        assert c.get('/api/writing/overview' + query).json()['role'] == 'student'
        assert c.get(review + query).status_code == 404
        assert c.get('/api/session').json()['session']['role'] == 'student'
        use(c, owner)
        c.delete(member_route).raise_for_status()
        use(c, cotutor)
        assert c.get('/api/writing/overview' + query).status_code == 404


def test_link_members_role_changes_cannot_grant_access_to_other_projects(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        owner = signup(c, 'owner@example.com', 'tutor')
        project = c.post('/api/projects', json={'title': 'Link IELTS'}).json()['id']
        other = c.post('/api/projects', json={'title': 'Secret'}).json()['id']
        route = f'/api/projects/{project}/sharing'
        token = c.patch(route, json={'mode': 'link', 'permission': 'editor', 'workspaceRole': 'student'}).json()['linkToken']
        member = signup(c, 'member@example.com', 'tutor')
        c.post('/api/shares/accept', json={'token': token}).raise_for_status()
        assert c.get('/api/writing/overview?projectId=' + project).json()['role'] == 'student'
        assert c.get('/api/writing/overview?projectId=' + other).status_code == 404
        assert c.patch(route, json={'mode': 'link', 'workspaceRole': 'tutor'}).status_code == 403
        use(c, owner)
        visitor = c.get(route).json()['members'][0]
        assert visitor['id'].startswith('link-') and visitor['inviteToken'] is None
        c.patch(route + '/members/' + visitor['id'], json={'workspaceRole': 'tutor'}).raise_for_status()
        use(c, member)
        assert c.get('/api/writing/overview?projectId=' + project).json()['role'] == 'tutor'
        use(c, owner)
        c.patch(route + '/members/' + visitor['id'], json={'permission': 'editor'}).raise_for_status()
        new_token = c.patch(route, json={'mode': 'link', 'permission': 'viewer', 'workspaceRole': 'student', 'rotate': True}).json()['linkToken']
        use(c, member)
        assert c.get('/api/writing/overview?projectId=' + project).status_code == 404
        accepted = c.post('/api/shares/accept', json={'token': new_token}).json()
        assert accepted['permission'] == 'viewer' and accepted['workspaceRole'] == 'student'
        use(c, owner)
        c.patch(route, json={'mode': 'private'}).raise_for_status()
        use(c, member)
        assert c.get('/api/writing/overview?projectId=' + project).status_code == 404


def test_legacy_import_keeps_submitted_snapshot_and_cannot_move_to_second_project(tmp_path):
    from test_writing_workflow import setup, submit
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        owner, student, classroom, assignment = setup(c, tasks=[2])
        submission, _ = submit(c, assignment)
        use(c, owner)
        project = c.post('/api/projects', json={'title': 'Import destination'}).json()['id']
        c.get('/api/projects').raise_for_status()
        query = '?projectId=' + project
        preview = c.get('/api/writing/overview' + query).json()
        assert preview['assignments'] == [] and preview['legacy'] == {'classes': 1, 'assignments': 1}
        c.post('/api/writing/import-legacy' + query).raise_for_status()
        assert c.get('/api/writing/overview' + query).json()['assignments'][0]['id'] == assignment['id']
        imported = c.get('/api/writing/submissions/' + submission['id'] + query).json()['submission']
        assert imported['answers'] == submission['answers'] and imported['projectId'] == project
        assert c.get('/api/writing/overview').json()['assignments'] == []
        second = c.post('/api/projects', json={'title': 'Wrong destination'}).json()['id']
        c.get('/api/projects').raise_for_status()
        assert c.post('/api/writing/import-legacy?projectId=' + second).json() == {'classes': 0, 'assignments': 0}

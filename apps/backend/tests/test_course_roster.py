from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import signup


def test_course_local_student_edit_permissions_revision_and_removal(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        tutor = signup(client, 'roster-tutor@example.com', 'tutor')
        course = client.post('/api/writing/courses', json={'title': 'Course A'}).json()
        other_course = client.post('/api/writing/courses', json={'title': 'Course B'}).json()
        student = signup(client, 'roster-student@example.com', 'student')
        account = client.get('/api/session').json()['session']
        use(client, tutor)
        for row in [course, other_course]:
            client.post(f"/api/writing/courses/{row['id']}/members", json={'email': 'roster-student@example.com'}).raise_for_status()
        detail = client.get(f"/api/writing/courses/{course['id']}")
        assert detail.status_code == 200
        member = detail.json()['students'][0]
        route = f"/api/writing/courses/{course['id']}/members/{member['id']}"
        change = {'displayName': 'Jamie A', 'studentCode': 'ST-01', 'note': 'Introduction practice', 'revision': 0}
        updated = client.patch(route, json=change)
        assert updated.status_code == 200
        assert updated.json()['revision'] == 1
        assert client.patch(route, json=change).status_code == 409
        details = client.get(f"/api/writing/courses/{course['id']}").json()
        assert details['students'][0]['displayName'] == 'Jamie A'
        assert details['students'][0]['studentCode'] == 'ST-01'
        assert details['students'][0]['note'] == 'Introduction practice'
        assert client.get(f"/api/writing/courses/{other_course['id']}").json()['students'][0]['displayName'] != 'Jamie A'
        stranger = signup(client, 'roster-other@example.com', 'tutor')
        assert client.patch(route, json={**change, 'revision': 1}).status_code in [403, 404]
        use(client, student)
        assert client.patch(route, json={**change, 'revision': 1}).status_code == 403
        student_view = client.get(f"/api/writing/courses/{course['id']}")
        assert student_view.status_code == 200
        assert 'students' not in student_view.json()
        assert client.get('/api/session').json()['session'] == account
        use(client, tutor)
        client.delete(route).raise_for_status()
        assert client.get(f"/api/writing/courses/{course['id']}").json()['studentCount'] == 0
        assert client.patch(route, json={**change, 'revision': 1}).status_code == 404


def test_legacy_group_input_is_saved_as_course(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        signup(client, 'course-only@example.com', 'tutor')
        response = client.post('/api/writing/courses', json={'title': 'Unified course', 'kind': 'group'})
        assert response.status_code == 200
        course = response.json()
        assert course['kind'] == 'course'
        edited = client.put('/api/writing/courses/' + course['id'], json={
            'title': 'Renamed course', 'kind': 'group', 'revision': course['revision']})
        assert edited.status_code == 200
        assert edited.json()['kind'] == 'course'

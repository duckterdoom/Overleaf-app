from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import setup, submit


def test_assignment_read_update_delete_and_preserve_submitted_snapshot(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        tutor, student, _, assignment = setup(c)
        route = '/api/writing/assignments/' + assignment['id']
        assert c.delete(route, params={'revision': assignment['revision']}).status_code == 403
        use(c, tutor)
        assert c.get(route).json()['title'] == assignment['title']
        from latex_backend.writing.api import Assignment
        body = {key: value for key, value in assignment.items() if key in Assignment.model_fields}
        body.update(title='Updated assignment', practice=False)
        updated = c.put(route, json=body)
        updated.raise_for_status()
        assignment = updated.json()
        use(c, student)
        submission, _ = submit(c, assignment)
        use(c, tutor)
        assert c.put(route, json={**body, 'revision':assignment['revision'], 'title':'Cannot change running question'}).status_code == 409
        assert c.delete(route, params={'revision':0}).status_code == 409
        c.delete(route, params={'revision':assignment['revision']}).raise_for_status()
        assert c.get(route).status_code == 404
        assert c.get('/api/writing/overview').json()['assignments'] == []
        assert c.get('/api/writing/submissions/'+submission['id']).json()['submission']['assignment']['title'] == 'Updated assignment'

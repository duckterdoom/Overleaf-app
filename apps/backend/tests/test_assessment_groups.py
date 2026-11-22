from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL
from test_writing_workflow import signup
from test_writing_points import body
from test_web_sharing import use


def test_research_library_includes_report_subtype_and_keeps_snapshots(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        signup(client, 'types@example.com', 'tutor')
        for category in ['ielts', 'report', 'research']:
            content = body(None, category=category)
            content['title'] = category + ' question'
            client.post('/api/writing/questions', json={'content': content}).raise_for_status()
        result = client.get('/api/writing/questions?category=research&scope=mine').json()
        assert {row['content']['category'] for row in result['items']} == {'report', 'research'}
        report = client.get('/api/writing/questions?category=research&documentType=report&scope=mine').json()
        assert report['total'] == 1
        assert report['items'][0]['content']['category'] == 'report'
        research = client.get('/api/writing/questions?category=research&documentType=research&scope=mine').json()
        assert research['total'] == 1
        assert research['items'][0]['content']['category'] == 'research'
        assert client.get('/api/writing/questions?category=ielts&scope=mine').json()['total'] == 1


def test_research_guides_include_report_and_keep_course_visibility(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        tutor = signup(client, 'guide-group-tutor@example.com', 'tutor')
        course = client.post('/api/writing/classes', json={'title': 'Research course'}).json()
        ids = []
        for category in ['report', 'research']:
            guide = client.post('/api/writing/guides', json={
                'title': category + ' guide', 'category': category, 'task': 1,
                'profiles': [category], 'content': '# Evidence', 'classIds': [course['id']]})
            guide.raise_for_status()
            ids.append(guide.json()['id'])
        assert {row['id'] for row in client.get('/api/writing/guides?category=research').json()['items']} == set(ids)
        # The new public category also accepts an existing report rubric.
        client.post('/api/writing/guides', json={
            'title': 'Report subtype', 'category': 'research', 'task': 1,
            'profiles': ['report'], 'content': '# Report', 'classIds': []}).raise_for_status()
        student = signup(client, 'guide-group-student@example.com', 'student')
        assert client.get('/api/writing/guides?category=research').json()['items'] == []
        client.post('/api/writing/classes/join', json={'code': course['joinCode']}).raise_for_status()
        visible = client.get('/api/writing/guides?category=research').json()['items']
        assert {row['id'] for row in visible} == set(ids)
        assert all('classIds' not in row for row in visible)
        use(client, tutor)
        detail = client.get('/api/writing/guides/' + ids[0]).json()
        assert detail['category'] == 'report'  # Stored legacy subtype is preserved.

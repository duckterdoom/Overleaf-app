import io
import uuid
from PIL import Image
from fastapi.testclient import TestClient

from latex_backend.core.application import create_web_app
from latex_backend.http.web_accounts import COOKIE
from latex_backend.writing.service import CATALOG, word_count
from test_web_sharing import URL, use


def signup(client, email, role):
    client.cookies.clear()
    client.post('/api/actions/register', json={'email': email, 'name': role.title(), 'password': 'test-password'}).raise_for_status()
    client.post('/api/auth/onboarding', json={'role': role, 'name': role.title()}).raise_for_status()
    return client.cookies.get(COOKIE)


def setup(client, tasks=(1, 2), variant='academic'):
    tutor = signup(client, 'tutor@example.com', 'tutor')
    classroom = client.post('/api/writing/classes', json={'title': 'Writing cohort'}).json()
    assignment = client.post('/api/writing/assignments', json={'title': 'Both tasks', 'classId': classroom['id'],
        'variant': variant, 'tasks': list(tasks), 'prompts': {'1': 'Describe the chart.' if variant == 'academic' else 'Write a letter.', '2': 'Discuss both views.'},
        'profiles': {'1': 'bar' if variant == 'academic' else 'formal', '2': 'discussion'}, 'published': True}).json()
    student = signup(client, 'student@example.com', 'student')
    client.post('/api/writing/classes/join', json={'code': classroom['joinCode']}).raise_for_status()
    return tutor, student, classroom, assignment


def submit(client, assignment):
    route = '/api/writing/assignments/' + assignment['id']
    draft = client.get(route + '/draft').json()
    answers = {'1': 'The chart compares transport.\n\nOverall, car use fell.\n\nBus use rose.',
               '2': 'Both views deserve consideration.\n\nRoads help local businesses.\n\nPublic transport carries more passengers.\n\nIn conclusion, both measures matter.'}
    saved = client.put(route + '/draft', json={'revision': draft['revision'], 'answers': answers, 'latexSources': {'1': 'student source'}})
    saved.raise_for_status()
    body = {'revision': saved.json()['revision'], 'requestId': uuid.uuid4().hex}
    response = client.post(route + '/submit', json=body)
    response.raise_for_status()
    assert client.post(route + '/submit', json=body).json()['id'] == response.json()['id']
    return response.json(), saved.json()


def grade(tasks):
    return {'revision': 0, 'tasks': {str(task): {'profile': 'bar' if task == 1 else 'discussion',
        'scores': {code: value for code, value in zip(['TA' if task == 1 else 'TR', 'CC', 'LR', 'GRA'], [6, 6.5, 6, 6])},
        'notes': {}, 'sections': []} for task in tasks}, 'summary': 'Useful development.', 'recommendations': 'Check comparisons.'}


def test_role_acl_join_and_no_role_switch_or_fake_default_scores(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        assert c.get('/api/writing/overview').status_code == 401
        tutor, student, classroom, assignment = setup(c)
        assert c.post('/api/writing/classes', json={'title': 'Forbidden'}).status_code == 403
        overview = c.get('/api/writing/overview').json()
        assert len(overview['assignments']) == 1 and overview['submissions'] == []
        assert 'joinCode' not in overview['classes'][0] and 'students' not in overview['classes'][0]
        outsider = signup(c, 'outsider@example.com', 'student')
        assert c.get('/api/writing/overview').json()['assignments'] == []
        assert c.get('/api/writing/assignments/' + assignment['id'] + '/draft').status_code == 404
        use(c, tutor)
        assert c.get('/api/writing/overview').json()['classes'][0]['studentCount'] == 1


def test_submission_snapshot_grading_draft_private_publish_revision_and_weight(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        tutor, student, _, assignment = setup(c)
        submission, saved = submit(c, assignment)
        route = '/api/writing/submissions/' + submission['id']
        assert c.get(route).json()['review'] is None
        draft = c.get('/api/writing/assignments/' + assignment['id'] + '/draft').json()
        c.put('/api/writing/assignments/' + assignment['id'] + '/draft', json={'revision': draft['revision'], 'answers': {'1': 'Changed after submission', '2': 'Changed'}, 'latexSources': {}}).raise_for_status()
        assert c.get(route).json()['submission']['answers'] == saved['answers']
        assert c.put(route + '/review', json=grade([1, 2])).status_code == 403
        use(c, tutor)
        body = grade([1, 2])
        check = CATALOG['bar']['checks']['intro'][0]
        body['tasks']['1']['sections'] = [{'paragraph': 0, 'kind': 'intro', 'title': 'Introduction', 'checks': {check['id']: 'met'}, 'note': 'Good paraphrase', 'quote': 'The chart compares transport.'}]
        first = c.put(route + '/review', json=body).json()
        assert first['revision'] == 1
        assert c.put(route + '/review', json=body).status_code == 409
        use(c, student)
        assert c.get(route).json()['review'] is None  # No draft feedback leak.
        use(c, tutor)
        result = c.post(route + '/publish', json={'revision': 1}).json()
        assert result['writingRaw'] == (6.125 + 2 * 6.125) / 3
        assert result['writingBand'] == 6.0
        report = c.post('/api/writing/latex', json={'submissionId': submission['id'], 'report': True})
        report.raise_for_status()
        assert 'Met: ' in report.json()['source'] and 'Good paraphrase' in report.json()['source']
        body['revision'] = 1
        body['summary'] = 'Unpublished next draft'
        c.put(route + '/review', json=body).raise_for_status()
        use(c, student)
        assert c.get(route).json()['review']['summary'] == 'Useful development.'
        use(c, tutor)
        assert c.post(route + '/publish', json={'revision': 2}).status_code == 400
        c.post(route + '/publish', json={'revision': 2, 'reason': 'Clarify summary'}).raise_for_status()
        use(c, student)
        assert c.get(route).json()['review']['summary'] == 'Unpublished next draft'


def test_incomplete_grades_invalid_evidence_and_other_tutor_blocked(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        tutor, student, _, assignment = setup(c, tasks=[2])
        submission, _ = submit(c, assignment)
        route = '/api/writing/submissions/' + submission['id']
        stranger = signup(c, 'other-tutor@example.com', 'tutor')
        assert c.get(route).status_code == 404
        assert c.put(route + '/review', json=grade([2])).status_code == 404
        use(c, tutor)
        data = grade([2])
        data['tasks']['2']['scores']['TR'] = None
        c.put(route + '/review', json=data).raise_for_status()
        assert c.post(route + '/publish', json={'revision': 1}).status_code == 400
        data['revision'] = 1
        data['tasks']['2']['sections'] = [{'paragraph': 0, 'kind': 'intro', 'title': 'Intro', 'quote': 'Fabricated evidence'}]
        assert c.put(route + '/review', json=data).status_code == 400


def test_general_task1_latex_image_and_export_to_real_project(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        tutor, student, _, assignment = setup(c, tasks=[1], variant='general')
        submission, _ = submit(c, assignment)
        data = c.post('/api/writing/latex', json={'submissionId': submission['id'], 'createProject': True})
        data.raise_for_status()
        project = data.json()['project']
        content = c.get(f"/api/projects/{project['id']}/file?path=main.tex").json()['content']
        assert 'The chart compares transport.' in content and '\\begin{document}' in content
        assert 'student source' not in content  # Never execute a student's source as Tutor.
        use(c, tutor)
        image = io.BytesIO()
        Image.new('RGB', (40, 30), 'green').save(image, 'PNG')
        asset = c.post('/api/writing/assets', content=image.getvalue(), headers={'Content-Type': 'image/png'}).json()['id']
        use(c, student)
        assert c.get('/api/writing/assets/' + asset).status_code == 404


def test_practice_draft_cas_and_empty_submit_no_fake_class(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        signup(c, 'practice@example.com', 'student')
        assignment = c.post('/api/writing/assignments', json={'title': 'Practice', 'practice': True, 'tasks': [1], 'prompts': {'1': 'Describe a chart.'}, 'profiles': {'1': 'bar'}, 'durationMinutes': 20}).json()
        route = '/api/writing/assignments/' + assignment['id']
        first = c.get(route + '/draft').json()
        assert first['deadline'] == c.get(route + '/draft').json()['deadline']
        assert c.post(route + '/submit', json={'revision': 0, 'requestId': uuid.uuid4().hex}).status_code == 400
        data = {'revision': 0, 'answers': {'1': 'A short answer.'}, 'latexSources': {}}
        c.put(route + '/draft', json=data).raise_for_status()
        assert c.put(route + '/draft', json=data).status_code == 409
        assert c.get('/api/writing/overview').json()['classes'] == []
    assert word_count("It's a well-known change in 2026.") == 6

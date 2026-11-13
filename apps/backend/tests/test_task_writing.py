import copy
import uuid

from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from latex_backend.writing.service import CATALOG
from test_web_sharing import URL, use
from test_writing_workflow import signup
from test_writing_sharing import share
from test_writing_prompt_files import pdf
from latex_backend.storage.store import atomic_write


def content(course=None):
    return {'title': 'Introduction practice', 'classId': course, 'category': 'ielts', 'tasks': [2],
            'profiles': {'2': 'discussion'}, 'prompts': {'2': 'Introduce the two views on public transport.'},
            'writingScope': 'intro', 'purpose': 'practice', 'published': True,
            'rubricCatalog': {'discussion': copy.deepcopy(CATALOG['discussion'])}}


def test_question_visibility_ownership_revisions_and_assignment_snapshot(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        owner = signup(client, 'library-owner@example.com', 'tutor')
        row = client.post('/api/writing/questions', json={'content': content()})
        row.raise_for_status()
        private = row.json()
        other = signup(client, 'library-other@example.com', 'tutor')
        assert client.get('/api/writing/questions').json()['items'] == []
        assert client.put('/api/writing/questions/' + private['id'], json={'content': content(), 'revision': 1}).status_code == 404
        use(client, owner)
        shared = client.put('/api/writing/questions/' + private['id'], json={'content': content(), 'visibility': 'public', 'revision': 1})
        shared.raise_for_status()
        assert client.put('/api/writing/questions/' + private['id'], json={'content': content(), 'revision': 1}).status_code == 409
        use(client, other)
        question = client.get('/api/writing/questions').json()['items'][0]
        assert question['visibility'] == 'public'
        assert client.delete(f"/api/writing/questions/{question['id']}?revision=2").status_code == 403
        course = client.post('/api/writing/courses', json={'title': 'Writing 101', 'kind': 'course'}).json()
        body = content(course['id'])
        body['questionId'] = question['id']
        body['rubricCatalog']['discussion']['checks']['intro'][0]['text'] = 'My exercise-specific criterion'
        assignment = client.post('/api/writing/assignments', json=body)
        assignment.raise_for_status()
        rubric = assignment.json()['rubricCatalog']['discussion']
        assert set(rubric['checks']) == {'intro'}
        assert rubric['checks']['intro'][0]['text'] == 'My exercise-specific criterion'
        use(client, owner)
        assert client.delete(f"/api/writing/questions/{question['id']}?revision=2").status_code == 200
        use(client, other)
        saved = client.get('/api/writing/assignments/' + assignment.json()['id']).json()
        assert saved['rubricCatalog']['discussion'] == rubric
        student = signup(client, 'library-student@example.com', 'student')
        assert client.get('/api/writing/questions').status_code == 403
        assert client.get('/api/writing/assignments/' + saved['id']).status_code == 404


def test_course_enrollment_section_assessment_and_no_new_document_project(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        tutor = signup(client, 'course-tutor@example.com', 'tutor')
        course = client.post('/api/writing/courses', json={'title': 'Introductions', 'kind': 'group'}).json()
        student = signup(client, 'course-student@example.com', 'student')
        outsider = signup(client, 'course-outsider@example.com', 'student')
        use(client, tutor)
        client.post(f"/api/writing/courses/{course['id']}/members", json={'email': 'course-student@example.com'}).raise_for_status()
        assignment = client.post('/api/writing/assignments', json=content(course['id'])).json()
        key = assignment['id']
        use(client, outsider)
        assert client.get('/api/writing/overview').json()['assignments'] == []
        assert client.get(f'/api/writing/assignments/{key}/draft').status_code == 404
        use(client, student)
        assert len(client.get('/api/writing/overview').json()['assignments']) == 1
        draft = client.get(f'/api/writing/assignments/{key}/draft').json()
        saved = client.put(f'/api/writing/assignments/{key}/draft', json={'revision': draft['revision'], 'answers': {'2': 'Some support public transport. Others prefer roads.'}}).json()
        submission = client.post(f'/api/writing/assignments/{key}/submit', json={'revision': saved['revision'], 'requestId': uuid.uuid4().hex}).json()
        use(client, tutor)
        profile = assignment['rubricCatalog']['discussion']
        checks = {check['id']: 'met' for check in profile['checks']['intro']}
        review = {'revision': 0, 'tasks': {'2': {'profile': 'discussion', 'scores': {}, 'sections': [
            {'paragraph': 0, 'kind': 'intro', 'title': 'Introduction', 'checks': checks}]}}}
        route = '/api/writing/submissions/' + submission['id']
        invalid = copy.deepcopy(review)
        invalid['tasks']['2']['sections'][0]['checks']['invented'] = 'met'
        assert client.put(route + '/review', json=invalid).status_code == 400
        client.put(route + '/review', json=review).raise_for_status()
        result = client.post(route + '/publish', json={'revision': 1})
        result.raise_for_status()
        assert result.json()['scorePercent'] == 100 and result.json()['taskBands'] == {}
        assert result.json()['writingBand'] is None
        assert client.get('/api/projects').json() == []
        student_id = client.get('/api/writing/overview').json()['classes'][0]['students'][0]['id']
        client.delete(f"/api/writing/courses/{course['id']}/members/" + student_id).raise_for_status()
        use(client, student)
        assert client.get(f'/api/writing/assignments/{key}').status_code == 404


def test_paper_sections_roundtrip_to_latex_and_frozen_submission(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        signup(client, 'paper-student@example.com', 'student')
        body = content()
        body.update({'practice': True, 'category': 'research', 'tasks': [1], 'writingScope': 'whole',
                     'profiles': {'1': 'research'}, 'prompts': {'1': 'Report your research.'}, 'rubricCatalog': None})
        assignment = client.post('/api/writing/assignments', json=body)
        assignment.raise_for_status()
        route = '/api/writing/assignments/' + assignment.json()['id']
        draft = client.get(route + '/draft').json()
        blocks = [{'heading': 'Introduction', 'text': 'A question about transport.'},
                  {'heading': 'Methods', 'text': 'We surveyed forty commuters.'},
                  {'heading': 'Results', 'text': 'Most used the bus.'}]
        text = '\n\n'.join(block['text'] for block in blocks)
        invalid = {'revision': draft['revision'], 'answers': {'1': 'Different text'}, 'blocks': {'1': blocks}}
        assert client.put(route + '/draft', json=invalid).status_code == 400
        saved = client.put(route + '/draft', json={**invalid, 'answers': {'1': text}})
        saved.raise_for_status()
        submission = client.post(route + '/submit', json={'revision': saved.json()['revision'], 'requestId': uuid.uuid4().hex}).json()
        source = client.post('/api/writing/latex', json={'submissionId': submission['id']}).json()['source']
        assert '\\subsubsection*{Methods}' in source and 'We surveyed forty commuters.' in source
        assert client.get('/api/writing/submissions/' + submission['id']).json()['submission']['blocks']['1'] == blocks
        assert client.get('/api/projects').json() == []


def test_explicit_project_course_requires_membership_even_with_editor_access(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        tutor = signup(client, 'project-course-tutor@example.com', 'tutor')
        project = client.post('/api/projects', json={'title': 'Writing sources'}).json()['id']
        client.patch(f'/api/projects/{project}/sharing', json={'mode': 'private', 'ownerRole': 'tutor'}).raise_for_status()
        query = '?projectId=' + project
        course = client.post('/api/writing/courses' + query, json={'title': 'Explicit course'}).json()
        assignment = client.post('/api/writing/assignments' + query, json=content(course['id'])).json()
        invitation = share(client, tutor, project, 'project-course-student@example.com', 'student')
        student = signup(client, 'project-course-student@example.com', 'student')
        client.post('/api/shares/accept', json={'token': invitation['inviteToken']}).raise_for_status()
        assert client.get('/api/writing/overview' + query).json()['assignments'] == []
        assert client.get('/api/writing/assignments/' + assignment['id'] + query).status_code == 404
        use(client, tutor)
        client.post('/api/writing/courses/' + course['id'] + '/members' + query,
                    json={'email': 'project-course-student@example.com'}).raise_for_status()
        use(client, student)
        assert len(client.get('/api/writing/overview' + query).json()['assignments']) == 1


def test_independent_writing_imports_editor_source_and_public_reuse_copies_assets(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as client:
        owner = signup(client, 'source-owner@example.com', 'tutor')
        uid = client.get('/api/session').json()['session']['id']
        project = client.post('/api/projects', json={'title': 'Question source'}).json()['id']
        client.get('/api/projects').raise_for_status()
        _, store = app.tenants[uid]
        with store.shared_files.edit(project):
            atomic_write(store.path(project, 'question.pdf'), pdf())
        imported = client.post('/api/writing/prompt-import', json={'sourceProjectId': project, 'path': 'question.pdf', 'firstPage': 1, 'lastPage': 1})
        imported.raise_for_status()
        asset = imported.json()
        original_pdf = client.get('/api/writing/prompt-files/' + asset['id']).content
        assert asset['projectId'] is None and asset['sourceProjectId'] == project
        body = content()
        body['promptFiles'] = {'2': asset['id']}
        question = client.post('/api/writing/questions', json={'visibility': 'public', 'content': body}).json()
        other = signup(client, 'source-other@example.com', 'tutor')
        assert client.post('/api/writing/prompt-import', json={'sourceProjectId': project, 'path': 'question.pdf'}).status_code == 404
        # Public attachment is visible through its question, not through source project access.
        client.get('/api/writing/prompt-files/' + asset['id']).raise_for_status()
        course = client.post('/api/writing/courses', json={'title': 'Public reuse'}).json()
        assignment = client.post('/api/writing/assignments', json={**content(course['id']), 'questionId': question['id']})
        assignment.raise_for_status()
        copy_id = assignment.json()['promptFiles']['2']['id']
        assert copy_id != asset['id']
        use(client, owner)
        client.delete('/api/writing/questions/' + question['id'] + '?revision=1').raise_for_status()
        use(client, other)
        assert client.get('/api/writing/prompt-files/' + asset['id']).status_code == 404
        copied = client.get('/api/writing/prompt-files/' + copy_id)
        copied.raise_for_status()
        assert copied.content == original_pdf

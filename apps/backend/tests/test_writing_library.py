import copy

from fastapi.testclient import TestClient

from latex_backend.core.application import create_web_app
from test_task_writing import content
from test_web_sharing import URL, use
from test_writing_workflow import signup


def test_library_views_search_and_independent_private_copy(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        owner = signup(c, 'library-author@example.com', 'tutor')
        body = {**content(), 'title': 'Transport [2026]'}
        original = c.post('/api/writing/questions', json={'content': body, 'visibility': 'public'}).json()
        private = c.post('/api/writing/questions', json={'content': content()}).json()
        other = signup(c, 'library-reader@example.com', 'tutor')
        assert c.get('/api/writing/questions?scope=mine').json()['total'] == 0
        found = c.get('/api/writing/questions', params={'scope': 'public', 'search': 'SPORT [2026]'}).json()
        assert found['total'] == 1 and found['items'][0]['savedToLibrary'] is False
        assert c.get('/api/writing/questions', params={'scope': 'public', 'search': '.*'}).json()['total'] == 0
        assert c.get('/api/writing/questions?scope=public&category=report').json()['total'] == 0
        assert c.post('/api/writing/questions/' + private['id'] + '/copy').status_code == 404
        response = c.post('/api/writing/questions/' + original['id'] + '/copy')
        response.raise_for_status()
        saved = response.json()
        assert saved['visibility'] == 'private' and saved['id'] != original['id']
        assert saved['sourceQuestionId'] == original['id']
        assert c.post('/api/writing/questions/' + original['id'] + '/copy').json()['id'] == saved['id']
        assert c.get('/api/writing/questions?scope=mine').json()['total'] == 1
        assert c.get('/api/writing/questions?scope=public').json()['items'][0]['savedToLibrary'] is True
        edited = c.put('/api/writing/questions/' + saved['id'], json={
            'content': {**body, 'title': 'My edited transport'}, 'revision': saved['revision']})
        edited.raise_for_status()
        assert edited.json()['sourceQuestionId'] == original['id']
        c.delete('/api/writing/questions/' + saved['id'], params={'revision': edited.json()['revision']}).raise_for_status()
        restored = c.post('/api/writing/questions/' + original['id'] + '/copy')
        restored.raise_for_status()
        assert restored.json()['visibility'] == 'private'
        assert restored.json()['title'] == 'My edited transport'
        use(c, owner)
        assert c.get('/api/writing/questions?scope=public').json()['total'] == 0
        assert c.get('/api/writing/questions?scope=mine').json()['total'] == 2
        c.delete('/api/writing/questions/' + original['id'], params={'revision': 1}).raise_for_status()
        use(c, other)
        mine = c.get('/api/writing/questions?scope=mine').json()['items'][0]
        assert mine['title'] == 'My edited transport'
        course = c.post('/api/writing/classes', json={'title': 'My group'}).json()
        c.post('/api/writing/assignments', json={**content(course['id']), 'questionId': mine['id']}).raise_for_status()
        signup(c, 'library-student@example.com', 'student')
        assert c.post('/api/writing/questions/' + mine['id'] + '/copy').status_code == 403


def test_assignment_saves_reusable_question_and_keeps_drafts_private(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        tutor = signup(c, 'exercise-library@example.com', 'tutor')
        course = c.post('/api/writing/classes', json={'title': 'Writing cohort'}).json()
        body = {**content(course['id']), 'libraryVisibility': 'public', 'published': False}
        response = c.post('/api/writing/assignments', json=body)
        response.raise_for_status()
        exercise = response.json()
        route = '/api/writing/assignments/' + exercise['id']
        assert c.get('/api/writing/questions?scope=mine').json()['total'] == 0
        body.update(published=True, revision=exercise['revision'])
        response = c.put(route, json=body)
        response.raise_for_status()
        exercise = response.json()
        question = c.get('/api/writing/questions?scope=mine').json()['items'][0]
        assert question['sourceAssignmentId'] == exercise['id']
        assert question['visibility'] == 'public'
        assert question['content']['rubricCatalog'] == exercise['rubricCatalog']
        assert not {'classId', 'examSecret', 'examPassword', 'dueAt', 'opensAt', 'closesAt'} & question['content'].keys()
        other = signup(c, 'exercise-other@example.com', 'tutor')
        assert c.get('/api/writing/questions?scope=public').json()['total'] == 1
        assert c.get(route).status_code == 404
        use(c, tutor)
        assert c.put(route, json={**body, 'libraryVisibility': 'private'}).status_code == 409
        body.update(libraryVisibility='private', revision=exercise['revision'])
        response = c.put(route, json=body)
        response.raise_for_status()
        assert c.get('/api/writing/questions?scope=mine').json()['total'] == 1
        use(c, other)
        assert c.get('/api/writing/questions?scope=public').json()['total'] == 0
        signup(c, 'exercise-student@example.com', 'student')
        c.post('/api/writing/classes/join', json={'code': course['joinCode']}).raise_for_status()
        assert c.get(route).status_code == 200
        assert c.post('/api/writing/assignments', json={
            **content(), 'practice': True, 'libraryVisibility': 'public'}).status_code == 403


def test_question_copy_preserves_attachment_after_source_is_private(tmp_path, mongo_database):
    from bson import Binary
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        author = signup(c, 'asset-author@example.com', 'tutor')
        uid = c.get('/api/session').json()['session']['id']
        asset = {'_id': 'source-asset', 'id': 'source-asset', 'ownerId': uid, 'projectId': None,
                 'kind': 'text', 'mime': 'text/plain', 'sourcePath': 'question.txt', 'digest': 'test',
                 'pages': None, 'firstPage': 1, 'lastPage': None, 'text': 'Transport prompt',
                 'data': Binary(b'Transport prompt')}
        mongo_database.writing_prompt_files.insert_one(asset)
        body = {**content(), 'promptFiles': {'2': asset['id']}}
        question = c.post('/api/writing/questions', json={'content': body, 'visibility': 'public'}).json()
        reader = signup(c, 'asset-reader@example.com', 'tutor')
        saved = c.post('/api/writing/questions/' + question['id'] + '/copy').json()
        copied_id = saved['content']['promptFiles']['2']['id']
        assert copied_id != asset['id']
        use(c, author)
        c.put('/api/writing/questions/' + question['id'], json={'content': body, 'revision': 1}).raise_for_status()
        use(c, reader)
        assert c.get('/api/writing/prompt-files/' + asset['id']).status_code == 404
        assert c.get('/api/writing/prompt-files/' + copied_id).content == b'Transport prompt'
        editable = copy.deepcopy(saved['content'])
        editable['promptFiles'] = {'2': copied_id}
        c.put('/api/writing/questions/' + saved['id'], json={
            'content': editable, 'revision': saved['revision']}).raise_for_status()

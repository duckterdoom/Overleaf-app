import io
import os
import uuid
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import signup


def picture():
    output = io.BytesIO()
    Image.new('RGB', (320, 180), 'white').save(output, 'PNG')
    return output.getvalue()


@pytest.mark.parametrize('purpose', ['practice', 'homework', 'exam'])
def test_direct_questions_description_images_and_long_answers(tmp_path, purpose):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        tutor = signup(c, 'direct-tutor@example.com', 'tutor')
        course = c.post('/api/writing/classes', json={'title': 'Direct questions'}).json()
        uploaded = c.post('/api/writing/prompt-images?filename=chart.png', content=picture())
        uploaded.raise_for_status()
        image = uploaded.json()
        assert image['kind'] == 'image' and image['origin'] == 'upload'
        body = {'title': 'Direct writing', 'description': 'Read the chart, then explain both views.',
                'classId': course['id'], 'tasks': [1, 2], 'variant': 'academic',
                'profiles': {'1': 'bar', '2': 'discussion'}, 'purpose': purpose,
                'prompts': {'1': 'Compare the chart.', '2': 'Discuss both views.'},
                'promptFiles': {'1': image['id']}, 'published': True}
        if purpose == 'exam':
            clock = datetime.now(timezone.utc)
            body.update(durationMinutes=60, opensAt=(clock-timedelta(minutes=1)).isoformat(),
                        closesAt=(clock+timedelta(hours=2)).isoformat(), examPassword='exam-secret')
        assignment = c.post('/api/writing/assignments', json=body)
        assignment.raise_for_status()
        task = assignment.json()
        # Explicit library publishing keeps the description and immutable image copy.
        question = c.post('/api/writing/questions', json={'content': body})
        question.raise_for_status()
        assert question.json()['content']['description'] == body['description']
        student = signup(c, 'direct-student@example.com', 'student')
        assert c.get('/api/writing/prompt-files/'+image['id']).status_code == 404
        c.post('/api/writing/classes/join', json={'code': course['joinCode']}).raise_for_status()
        route = '/api/writing/assignments/'+task['id']
        if purpose == 'exam':
            assert c.get('/api/writing/prompt-files/'+image['id']).status_code == 404
            c.post(route+'/enter', json={'password': 'exam-secret'}).raise_for_status()
        assert c.get(route).json()['description'] == body['description']
        received = c.get('/api/writing/prompt-files/'+image['id'])
        with Image.open(io.BytesIO(received.content)) as decoded:
            assert decoded.size == (320, 180)
        assert c.get('/api/writing/prompt-files/'+image['id']).headers['content-type'] == 'image/png'
        draft = c.get(route+'/draft').json()
        answer = 'Long answer. ' * 9000
        saved = c.put(route+'/draft', json={'revision': draft['revision'],
            'answers': {'1': answer, '2': 'A short answer is also accepted.'},
            'blocks': {'1': [{'heading': 'Body', 'text': answer}]}})
        saved.raise_for_status()
        assert saved.json()['answers']['1'] == answer
        submitted = c.post(route+'/submit', json={'revision': saved.json()['revision'], 'requestId': uuid.uuid4().hex})
        submitted.raise_for_status()
        assert submitted.json()['assignment']['description'] == body['description']
        assert submitted.json()['answers']['1'] == answer
        use(c, tutor)
        assert c.get('/api/writing/prompt-files/'+image['id']).status_code == 200
        use(c, student)
        assert c.post('/api/writing/prompt-images?filename=chart.png', content=picture()).status_code == 403


def test_image_validation_and_private_asset_ownership(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        assert c.post('/api/writing/prompt-images', content=picture()).status_code == 401
        signup(c, 'image-owner@example.com', 'tutor')
        assert c.post('/api/writing/prompt-images', content=b'<svg/>').status_code == 400
        assert c.post('/api/writing/prompt-images', content=b'x'*(12*1024*1024+1)).status_code == 413
        image = c.post('/api/writing/prompt-images?filename=chart.png', content=picture()).json()
        signup(c, 'image-other@example.com', 'tutor')
        assert c.get('/api/writing/prompt-files/'+image['id']).status_code == 404
        course = c.post('/api/writing/classes', json={'title': 'Other'}).json()
        result = c.post('/api/writing/assignments', json={'title': 'Stolen image', 'classId': course['id'],
            'tasks': [1], 'profiles': {'1': 'bar'}, 'prompts': {'1': 'Chart'},
            'promptFiles': {'1': image['id']}, 'published': True})
        assert result.status_code in {403, 404}


def test_question_image_accepts_large_valid_upload_and_normalizes_alpha(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        signup(c, 'large-image@example.com', 'tutor')
        output = io.BytesIO()
        Image.frombytes('RGB', (1400, 1100), os.urandom(1400*1100*3)).save(output, 'PNG')
        assert len(output.getvalue()) > 4*1024*1024
        upload = c.post('/api/writing/prompt-images?filename=../large.png', content=output.getvalue())
        upload.raise_for_status()
        assert upload.json()['sourcePath'] == 'large.png'
        alpha = io.BytesIO()
        Image.new('RGBA', (16, 16), (255, 0, 0, 128)).save(alpha, 'PNG')
        uploaded = c.post('/api/writing/prompt-images?filename=alpha.png', content=alpha.getvalue()).json()
        with Image.open(io.BytesIO(c.get('/api/writing/prompt-files/'+uploaded['id']).content)) as image:
            assert image.mode == 'RGBA' and image.getpixel((0, 0))[3] == 128


def test_reusing_question_preserves_changed_or_removed_visual(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        signup(c, 'reuse-image@example.com', 'tutor')
        course = c.post('/api/writing/classes', json={'title': 'Reuse'}).json()
        first = c.post('/api/writing/prompt-images?filename=first.png', content=picture()).json()
        second = c.post('/api/writing/prompt-images?filename=second.png', content=picture()).json()
        body = {'title': 'Reusable chart', 'description': 'Focus on the trends.', 'tasks': [1],
                'profiles': {'1': 'bar'}, 'prompts': {'1': 'Compare the chart.'},
                'promptFiles': {'1': first['id']}, 'classId': course['id'], 'published': True,
                'libraryVisibility': 'private'}
        question = c.post('/api/writing/questions', json={'content': body}).json()
        for files, expected in [({'1': first['id']}, 'first.png'), ({'1': second['id']}, 'second.png'), ({}, None)]:
            result = c.post('/api/writing/assignments', json={**body, 'questionId': question['id'], 'promptFiles': files})
            result.raise_for_status()
            assignment = result.json()
            assert assignment['description'] == body['description']
            saved_question = next(q for q in c.get('/api/writing/questions').json()['items'] if q['id'] == 'assignment-' + assignment['id'])
            assert saved_question['content']['description'] == body['description']
            if expected:
                assert assignment['promptFiles']['1']['sourcePath'] == expected
            else:
                assert assignment['promptFiles'] == {}

from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import signup
from test_writing_sharing import share
import pytest


@pytest.mark.parametrize('category',['report','research'])
def test_other_guide_categories_start_empty_and_share_with_enrolled_students(tmp_path,category):
    with TestClient(create_web_app(tmp_path),base_url=URL) as c:
        tutor=signup(c,'other-guides-tutor@example.com','tutor')
        assert c.get('/api/writing/guides?category='+category).json()['total']==0
        assert c.get('/api/writing/guides?category=ielts').json()['total']==13
        course=c.post('/api/writing/classes',json={'title':'Guide enrollment'}).json()
        body={'title':'Structure guide','category':category,'task':1,'profiles':[category],
              'content':'# Structure\n\n## Evidence\n\nExplain the evidence.','classIds':[course['id']]}
        created=c.post('/api/writing/guides',json=body);created.raise_for_status()
        assert created.json()['category']==category and not created.json()['builtin']
        invalid=c.post('/api/writing/guides',json={**body,'profiles':['bar']})
        assert invalid.status_code==400
        signup(c,'other-guides-student@example.com','student')
        assert c.get('/api/writing/guides?category='+category).json()['total']==0
        c.post('/api/writing/classes/join',json={'code':course['joinCode']}).raise_for_status()
        assert c.get('/api/writing/guides?category='+category).json()['total']==1
        assert c.get('/api/writing/guides?category=ielts').json()['total']==0


def test_guides_defaults_crud_class_visibility_and_revisions(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        assert c.get('/api/writing/guides').status_code == 401
        owner = signup(c, 'guides-owner@example.com', 'tutor')
        defaults = c.get('/api/writing/guides').json()['items']
        assert len(defaults) == 13
        assert sum(g['task'] == 1 for g in defaults) == 7
        line = next(g for g in defaults if g['profiles'] == ['line'])
        full = c.get('/api/writing/guides/' + line['id']).json()
        assert 'Blueprint' in full['content'] and len(full['content']) > 3000
        assert 'Model answer' in full['sampleContent'] and full['hasSample']
        assert '/api/writing/guide-assets/' in full['content']
        course = c.post('/api/writing/courses', json={'title': 'Guide class', 'kind': 'course'}).json()
        student = signup(c, 'guides-student@example.com', 'student')
        assert c.get('/api/writing/guides').json()['items'] == []
        assert c.get('/api/writing/guides/' + line['id']).status_code == 404
        assert c.post('/api/writing/guides', json={}).status_code in {403, 422}
        use(c, owner)
        body = {k: full[k] for k in ('title', 'task', 'profiles', 'content', 'summary', 'revision')}
        body['classIds'] = [course['id']]
        body['content'] += '\n\nTeacher-specific guidance.'
        saved = c.put('/api/writing/guides/' + line['id'], json=body)
        saved.raise_for_status()
        assert saved.json()['revision'] == 1
        assert c.put('/api/writing/guides/' + line['id'], json=body).status_code == 409
        use(c, student)
        assert c.get('/api/writing/guides').json()['items'] == []
        use(c, owner)
        c.post('/api/writing/courses/' + course['id'] + '/members', json={'email': 'guides-student@example.com'}).raise_for_status()
        use(c, student)
        visible = c.get('/api/writing/guides?profile=line&task=1&classId=' + course['id']).json()['items']
        assert [g['id'] for g in visible] == [line['id']]
        assert 'content' not in visible[0] and 'classIds' not in visible[0]
        detail = c.get('/api/writing/guides/' + line['id']).json()
        assert detail['content'].endswith('Teacher-specific guidance.') and 'classIds' not in detail
        assert c.put('/api/writing/guides/' + line['id'], json={**body, 'revision': 1}).status_code == 403
        assert c.delete('/api/writing/guides/' + line['id'] + '?revision=1').status_code == 403
        assert c.get('/api/writing/guides?task=2').json()['items'] == []
        assert c.get('/api/writing/guides?search=Line').json()['total'] == 1
        assert c.get('/api/writing/guides?page=1').json()['items'] == []
        # Unsharing revokes direct detail access immediately, even for enrolled students.
        use(c, owner)
        body.update(revision=1, classIds=[])
        c.put('/api/writing/guides/' + line['id'], json=body).raise_for_status()
        use(c, student)
        assert c.get('/api/writing/guides/' + line['id']).status_code == 404
        use(c, owner)
        body.update(revision=2, classIds=[course['id']])
        c.put('/api/writing/guides/' + line['id'], json=body).raise_for_status()
        other = signup(c, 'guides-other@example.com', 'tutor')
        assert c.get('/api/writing/guides/' + line['id']).status_code == 404
        other_line = next(g for g in c.get('/api/writing/guides').json()['items'] if g['profiles'] == ['line'])
        assert 'Teacher-specific guidance.' not in c.get('/api/writing/guides/' + other_line['id']).json()['content']
        other_course = c.post('/api/writing/courses', json={'title': 'Other class', 'kind': 'course'}).json()
        use(c, owner)
        assert c.post('/api/writing/guides', json={**body, 'revision': 0, 'classIds': [other_course['id']]}).status_code == 404
        # Membership removal revokes direct access, not just discovery.
        use(c, student)
        student_id = c.get('/api/session').json()['session']['id']
        use(c, owner)
        c.delete(f"/api/writing/courses/{course['id']}/members/{student_id}").raise_for_status()
        use(c, student)
        assert c.get('/api/writing/guides/' + line['id']).status_code == 404
        use(c, owner)
        c.delete('/api/writing/guides/' + line['id'] + '?revision=3').raise_for_status()
        assert line['id'] not in [g['id'] for g in c.get('/api/writing/guides').json()['items']]
        assert c.get('/api/writing/guides/' + line['id']).status_code == 404
        assert c.delete('/api/writing/guides/' + line['id'] + '?revision=1').status_code == 404
        created = c.post('/api/writing/guides', json={**body, 'revision': 0, 'classIds': []})
        created.raise_for_status()
        custom = created.json()
        assert custom['sourceUrl'] is None and custom['revision'] == 1
        assert c.put('/api/writing/guides/' + custom['id'], json={**body, 'revision': 1, 'classIds': []}).json()['revision'] == 2
        assert c.delete('/api/writing/guides/' + custom['id'] + '?revision=1').status_code == 409


def test_guide_validation_and_default_delete_are_scoped(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        signup(c, 'guides-validation@example.com', 'tutor')
        row = c.get('/api/writing/guides').json()['items'][0]
        body = {'title': 'New guide', 'summary': 'Guide summary', 'task': 1, 'profiles': ['line'], 'content': '# Teaching reference', 'classIds': [], 'revision': 0}
        for patch in ({'title': ' '}, {'content': ' '}, {'task': 2}, {'profiles': ['nonexistent']}, {'sourceUrl': 'javascript:alert(1)'}):
            assert c.post('/api/writing/guides', json={**body, **patch}).status_code in {400, 422}
        c.delete('/api/writing/guides/' + row['id'] + '?revision=0').raise_for_status()
        assert len(c.get('/api/writing/guides').json()['items']) == 12
        signup(c, 'guides-unaffected@example.com', 'tutor')
        assert len(c.get('/api/writing/guides').json()['items']) == 13


def test_project_guides_use_project_roles_and_explicit_class_membership(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        owner = signup(c, 'guide-project-owner@example.com', 'tutor')
        project = c.post('/api/projects', json={'title': 'Guide source'}).json()['id']
        second = c.post('/api/projects', json={'title': 'Other source'}).json()['id']
        c.get('/api/projects').raise_for_status()
        query = '?projectId=' + project
        course = c.post('/api/writing/courses' + query, json={'title': 'Shared guide class', 'kind': 'course'}).json()
        guide = c.get('/api/writing/guides' + query).json()['items'][0]
        full = c.get('/api/writing/guides/' + guide['id'] + query).json()
        body = {k: full[k] for k in ('title', 'task', 'profiles', 'summary', 'content', 'revision')}
        body['classIds'] = [course['id']]
        c.put('/api/writing/guides/' + guide['id'] + query, json=body).raise_for_status()
        assert c.get('/api/writing/guides/' + guide['id'] + '?projectId=' + second).status_code == 404
        invitation = share(c, owner, project, 'guide-project-learner@example.com', role='student')
        learner = signup(c, 'guide-project-learner@example.com', 'tutor')
        c.post('/api/shares/accept', json={'token': invitation['inviteToken']}).raise_for_status()
        # Project collaboration alone does not expose class resources.
        assert c.get('/api/writing/guides' + query).json()['items'] == []
        use(c, owner)
        c.post('/api/writing/courses/' + course['id'] + '/members' + query, json={'email': 'guide-project-learner@example.com'}).raise_for_status()
        use(c, learner)
        assert c.get('/api/writing/guides/' + guide['id'] + query).status_code == 200
        assert c.put('/api/writing/guides/' + guide['id'] + query, json={**body, 'revision': 1}).status_code == 403
        assert c.get('/api/writing/guides/' + guide['id']).status_code == 404
        invitation = share(c, owner, project, 'guide-project-reader@example.com', role='tutor', permission='viewer')
        signup(c, 'guide-project-reader@example.com', 'student')
        c.post('/api/shares/accept', json={'token': invitation['inviteToken']}).raise_for_status()
        assert len(c.get('/api/writing/guides' + query).json()['items']) == 13
        assert c.put('/api/writing/guides/' + guide['id'] + query, json={**body, 'revision': 1}).status_code == 403
        assert c.delete('/api/writing/guides/' + guide['id'] + query + '&revision=1').status_code == 403


def test_paired_samples_images_and_class_visibility(tmp_path):
    import re
    from latex_backend.writing.guides import RESOURCE
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        owner=signup(c,'sample-tutor@example.com','tutor')
        items=c.get('/api/writing/guides').json()['items']
        paired=[item for item in items if item['hasSample']]
        assert len(paired)==10 and all('sampleContent' not in item for item in items)
        for item in paired:
            full=c.get('/api/writing/guides/'+item['id']).json()
            guide_image=re.search(r'/api/writing/guide-assets/([a-f0-9]{64}\.(?:png|jpg))',full['content'])[1]
            assert guide_image in full['sampleContent'] and (RESOURCE/'images'/guide_image).is_file()
            image=c.get('/api/writing/guide-assets/'+guide_image)
            assert image.status_code==200 and image.headers['content-type'].startswith('image/')
        line=next(item for item in paired if item['profiles']==['line'])
        full=c.get('/api/writing/guides/'+line['id']).json()
        course=c.post('/api/writing/classes',json={'title':'Samples course'}).json()
        body={key:full[key] for key in ('title','task','profiles','summary','content','revision')}
        body.update(sampleContent='# Custom sample\n\nA tutor model answer.',classIds=[course['id']])
        saved=c.put('/api/writing/guides/'+line['id'],json=body);saved.raise_for_status()
        assert saved.json()['sampleContent']==body['sampleContent']
        signup(c,'sample-student@example.com','student')
        assert c.get('/api/writing/guides/'+line['id']).status_code==404
        c.post('/api/writing/classes/join',json={'code':course['joinCode']}).raise_for_status()
        assert c.get('/api/writing/guides/'+line['id']).json()['sampleContent']==body['sampleContent']
        assert c.put('/api/writing/guides/'+line['id'],json={**body,'revision':1}).status_code==403
        use(c,owner)
        body.update(revision=1,sampleContent='')
        cleared=c.put('/api/writing/guides/'+line['id'],json=body);cleared.raise_for_status()
        assert not cleared.json()['hasSample']
        assert not next(item for item in c.get('/api/writing/guides').json()['items'] if item['id']==line['id'])['hasSample']
        assert c.get('/api/writing/guide-assets/not-a-resource.jpg').status_code==404


def test_saved_unchanged_defaults_refresh_but_custom_text_is_preserved():
    import hashlib
    from latex_backend.writing.guides import builtin_content, RESOURCE
    old='# Old bar chart guide'
    row={'id':'scope-bar','builtin':True,'content':old,'sourceSha256':hashlib.sha256(old.encode()).hexdigest()}
    assert builtin_content(row)==(RESOURCE/'bar.md').read_text(encoding='utf-8')
    row['content'] += '\nTutor-specific changes'
    assert builtin_content(row) is None
    row['baseContentSha256']=hashlib.sha256(row['content'].encode()).hexdigest()
    assert 'Recycling Collections' in builtin_content(row)
    row['builtin']=False
    assert builtin_content(row) is None

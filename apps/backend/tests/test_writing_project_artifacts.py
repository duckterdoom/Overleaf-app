from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import signup, submit
from test_writing_sharing import share


def test_shared_project_opens_after_owner_logout_and_backend_restart(tmp_path, mongo_database):
    app=create_web_app(tmp_path)
    with TestClient(app,base_url=URL) as c:
        owner=signup(c,'offline-owner@example.com','tutor')
        project=c.post('/api/projects',json={'title':'Persistent shared IELTS'}).json()['id']
        c.get('/api/projects').raise_for_status()
        query='?projectId='+project
        classroom=c.post('/api/writing/classes'+query,json={'title':'Class'}).json()
        assignment=c.post('/api/writing/assignments'+query,json={'title':'Project answer','classId':classroom['id'],
            'tasks':[2],'prompts':{'2':'Discuss community transport.'},'profiles':{'2':'discussion'},'published':True}).json()
        invitation=share(c,owner,project,'offline-member@example.com','student')
        member=signup(c,'offline-member@example.com','student')
        c.post('/api/shares/accept',json={'token':invitation['inviteToken']}).raise_for_status()
        use(c,owner);c.post('/api/actions/logout').raise_for_status()
    # New gateway has no owner workspace/session in memory.
    with TestClient(create_web_app(tmp_path),base_url=URL) as c:
        use(c,member)
        assert any(p['id']==project for p in c.get('/api/projects').json())
        c.get(f'/api/projects/{project}/file?path=main.tex').raise_for_status()
        c.get('/api/writing/assignments/'+assignment['id']+query).raise_for_status()
        draft=c.get('/api/writing/assignments/'+assignment['id']+'/draft'+query).json()
        saved=c.put('/api/writing/assignments/'+assignment['id']+'/draft'+query,json={'revision':draft['revision'],'answers':{'2':'A shared project keeps this answer.'}}).json()
        c.post('/api/writing/assignments/'+assignment['id']+'/submit'+query,json={'revision':saved['revision'],'requestId':'a'*32}).raise_for_status()
        assert mongo_database.writing_submissions.find_one({'assignmentId':assignment['id']})['projectId']==project

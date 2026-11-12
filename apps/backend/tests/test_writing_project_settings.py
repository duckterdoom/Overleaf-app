from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import signup
from test_writing_sharing import share


def test_project_checklists_survive_role_changes_and_remain_isolated(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as c:
        owner = signup(c, 'rubric-owner@example.com', 'tutor')
        project = c.post('/api/projects', json={'title':'Project settings'}).json()['id']
        c.get('/api/projects').raise_for_status()
        query = '?projectId=' + project
        route = '/api/writing/settings' + query
        response = c.get(route)
        assert response.status_code == 200, response.text
        initial = response.json()
        checks = {key: profile['checks'] for key, profile in initial['catalog'].items()}
        checks['bar']['intro'].append({'id':'project_evidence','text':'Custom evidence requirement','codes':['TA']})
        checks['bar']['body'].pop()
        saved = c.put(route, json={'revision':0,'checks':checks})
        saved.raise_for_status()
        assert c.put(route, json={'revision':0,'checks':checks}).status_code == 409
        classroom = c.post('/api/writing/classes'+query,json={'title':'Rubric class'}).json()
        assignment = c.post('/api/writing/assignments'+query,json={'title':'Snapshot rubric','classId':classroom['id'],
            'tasks':[1],'prompts':{'1':'Describe chart'},'profiles':{'1':'bar'},'published':True}).json()
        assert assignment['rubricCatalog']['bar']['checks']['intro'][-1]['id']=='project_evidence'
        invitation = share(c, owner, project, 'rubric-other@example.com', 'tutor')
        other = signup(c, 'rubric-other@example.com', 'student')
        c.post('/api/shares/accept',json={'token':invitation['inviteToken']}).raise_for_status()
        assert c.get(route).json()==saved.json()
        use(c,owner)
        sharing = f'/api/projects/{project}/sharing'
        c.patch(sharing,json={'mode':'private','ownerRole':'student'}).raise_for_status()
        assert c.get(route).json()==saved.json()
        assert c.put(route,json={'revision':1,'checks':checks}).status_code==403
        use(c,other)
        checks['bar']['intro'][-1]['text']='Changed by another Tutor'
        c.put(route,json={'revision':1,'checks':checks}).raise_for_status()
        assert c.get('/api/writing/assignments/'+assignment['id']+query).json()['rubricCatalog']['bar']['checks']['intro'][-1]['text']=='Custom evidence requirement'
        second=c.post('/api/projects',json={'title':'Separate project'}).json()['id']
        c.get('/api/projects').raise_for_status()
        assert c.get('/api/writing/settings?projectId='+second).json()['revision']==0

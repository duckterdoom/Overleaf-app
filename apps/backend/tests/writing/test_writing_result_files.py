from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL,use
from test_writing_prompt_files import setup
from test_writing_sharing import share
from test_writing_workflow import signup,grade


def test_student_practice_from_project_and_published_results_in_project(tmp_path):
    app=create_web_app(tmp_path)
    with TestClient(app,base_url=URL) as c:
        owner,project,query,classroom,_,store,_=setup(c,app)
        attachment=c.post('/api/writing/prompt-import'+query,json={'path':'questions.pdf','firstPage':1,'lastPage':1}).json()
        assignment=c.post('/api/writing/assignments'+query,json={'title':'Shared question','classId':classroom['id'],
            'tasks':[1],'profiles':{'1':'bar'},'promptFiles':{'1':attachment['id']},'published':True}).json()
        invite=share(c,owner,project,'result-student@example.com','student')
        student=signup(c,'result-student@example.com','student')
        c.post('/api/shares/accept',json={'token':invite['inviteToken']}).raise_for_status()
        own=c.post('/api/writing/prompt-import'+query,json={'path':'questions.pdf','firstPage':2,'lastPage':2}).json()
        assert c.get('/api/writing/prompt-files/'+own['id']+query).status_code==200
        practice=c.post('/api/writing/assignments'+query,json={'title':'Personal file practice','tasks':[2],
            'profiles':{'2':'discussion'},'promptFiles':{'2':own['id']},'practice':True})
        practice.raise_for_status()
        assert practice.json()['projectId']==project
        assert c.post('/api/writing/assignments'+query,json={'title':'Cannot use private upload','tasks':[2],
            'profiles':{'2':'discussion'},'promptFiles':{'2':attachment['id']},'practice':True}).status_code==403
        route='/api/writing/assignments/'+assignment['id']
        draft=c.get(route+'/draft'+query).json()
        saved=c.put(route+'/draft'+query,json={'revision':draft['revision'],'answers':{'1':'The graph compares original values.'}}).json()
        submission=c.post(route+'/submit'+query,json={'revision':saved['revision'],'requestId':'b'*32}).json()
        use(c,owner)
        review='/api/writing/submissions/'+submission['id']
        data=grade([1]);data['summary']='Good comparisons. Improve your overview.'
        c.put(review+'/review'+query,json=data).raise_for_status()
        published=c.post(review+'/publish'+query,json={'revision':1})
        published.raise_for_status()
        files=published.json()['resultFiles'];assert len(files)==1
        name=files[0];assert name.startswith('result/') and '-task1-bar-' in name
        content=store.read(project,name)
        assert content.index('Question')<content.index('The graph compares original values.')<content.index('Tutor feedback')
        assert 'Good comparisons.' in content
        assert 'result/assets/' in content
        assert c.post(review+'/publish'+query,json={'revision':1}).json()['resultFiles']==files
        use(c,student)
        assert any(f['path']==name for f in c.get(f'/api/projects/{project}/files').json())

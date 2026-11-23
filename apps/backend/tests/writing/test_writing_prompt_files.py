import pymupdf
from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from latex_backend.compilation.compiler import Snapshot
from latex_backend.storage.store import atomic_write
from test_web_sharing import URL, use
from test_writing_workflow import signup
from test_writing_sharing import share


def pdf():
    with pymupdf.open() as doc:
        for title in ('Task 1: Compare the chart.', 'Task 2: Discuss both views.'):
            page=doc.new_page();page.insert_text((40,50),title);page.draw_rect((40,80,140,180),color=(0,0.5,0))
        return doc.tobytes()


def setup(c,app):
    owner=signup(c,'owner@example.com','tutor')
    project=c.post('/api/projects',json={'title':'Editor questions'}).json()['id']
    c.get('/api/projects').raise_for_status()
    uid=c.get('/api/session').json()['session']['id']
    child,store=app.tenants[uid]
    content=pdf()
    with store.shared_files.edit(project):
        atomic_write(store.path(project,'questions.pdf'),content)
    query='?projectId='+project
    classroom=c.post('/api/writing/classes'+query,json={'title':'Writing'}).json()
    return owner,project,query,classroom,child,store,content


def test_editor_pdf_questions_are_shared_frozen_and_keep_time_settings(tmp_path):
    app=create_web_app(tmp_path)
    with TestClient(app,base_url=URL) as c:
        owner,project,query,classroom,child,store,content=setup(c,app)
        first=c.post('/api/writing/prompt-import'+query,json={'path':'questions.pdf','firstPage':1,'lastPage':1}).json()
        second=c.post('/api/writing/prompt-import'+query,json={'path':'questions.pdf','firstPage':2,'lastPage':2}).json()
        body={'title':'Questions from editor','classId':classroom['id'],'tasks':[1,2],'prompts':{'1':'','2':''},
            'profiles':{'1':'bar','2':'discussion'},'promptFiles':{'1':first['id'],'2':second['id']},
            'durationMinutes':60,'dueAt':'2030-01-01T10:00:00+07:00','published':False}
        assignment=c.post('/api/writing/assignments'+query,json=body).json()
        invitation=share(c,owner,project,'student@example.com','student')
        student=signup(c,'student@example.com','student')
        c.post('/api/shares/accept',json={'token':invitation['inviteToken']}).raise_for_status()
        assert c.get('/api/writing/prompt-files/'+first['id']+query).status_code==404
        assert c.post('/api/writing/prompt-import'+query,json={'path':'questions.pdf'}).status_code==200
        use(c,owner);body.update(revision=assignment['revision'],published=True)
        assignment=c.put('/api/writing/assignments/'+assignment['id']+query,json=body).json()
        assert assignment['durationMinutes']==60 and assignment['dueAt']=='2030-01-01T03:00:00+00:00'
        use(c,student)
        binary=c.get('/api/writing/prompt-files/'+first['id']+query);binary.raise_for_status()
        with pymupdf.open(stream=binary.content,filetype='pdf') as doc:
            assert len(doc)==1 and 'Task 1' in doc[0].get_text()
        with store.shared_files.edit(project):
            atomic_write(store.path(project,'questions.pdf'),b'changed source after assignment')
        assert c.get('/api/writing/prompt-files/'+first['id']+query).content==binary.content
        draft=c.get('/api/writing/assignments/'+assignment['id']+'/draft'+query).json()
        assert draft['deadline'] is not None
        assert c.get('/api/writing/prompt-files/'+first['id']).status_code==404


def test_tex_import_requires_matching_current_compiled_pdf_and_path_acl(tmp_path):
    app=create_web_app(tmp_path)
    with TestClient(app,base_url=URL) as c:
        owner,project,query,classroom,child,store,content=setup(c,app)
        store.add(project,'question.tex',False)
        store.save(project,'question.tex',r'\documentclass{article}\begin{document}Question\end{document}')
        compiler=child.state.compiler
        snapshot=Snapshot('compiled-question',store.fingerprint(project),content,main='question.tex')
        compiler.snapshots[project]=snapshot
        imported=c.post('/api/writing/prompt-import'+query,json={'path':'question.tex','snapshot':snapshot.id})
        imported.raise_for_status()
        asset=imported.json()
        assert c.get('/api/writing/prompt-files/'+asset['id']+query).content==content
        assert c.post('/api/writing/prompt-import'+query,json={'path':'../questions.pdf'}).status_code==400
        store.save(project,'question.tex','Changed source')
        assert c.post('/api/writing/prompt-import'+query,json={'path':'question.tex','snapshot':snapshot.id}).status_code==409


def test_questions_cannot_reference_another_project(tmp_path):
    app=create_web_app(tmp_path)
    with TestClient(app,base_url=URL) as c:
        owner,project,query,classroom,child,store,content=setup(c,app)
        asset=c.post('/api/writing/prompt-import'+query,json={'path':'questions.pdf'}).json()
        other=c.post('/api/projects',json={'title':'Other'}).json()['id'];c.get('/api/projects')
        other_query='?projectId='+other
        other_class=c.post('/api/writing/classes'+other_query,json={'title':'Other class'}).json()
        assert c.get('/api/writing/prompt-files/'+asset['id']+other_query).status_code==404
        body={'title':'Wrong question','classId':other_class['id'],'tasks':[2],'prompts':{'2':''},'profiles':{'2':'discussion'},
            'promptFiles':{'2':asset['id']},'published':True}
        assert c.post('/api/writing/assignments'+other_query,json=body).status_code==404

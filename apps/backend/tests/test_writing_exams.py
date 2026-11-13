from datetime import datetime,timedelta,timezone
from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL,use
from test_writing_prompt_files import setup
from test_writing_workflow import signup
from test_writing_sharing import share


def test_exam_password_schedule_admission_deadline_and_submission(tmp_path,mongo_database,monkeypatch):
    from latex_backend.writing import exams
    clock=[datetime.now(timezone.utc)]
    monkeypatch.setattr(exams,'utc_now',lambda:clock[0])
    app=create_web_app(tmp_path)
    with TestClient(app,base_url=URL) as c:
        owner,project,query,_,_,_,_=setup(c,app)
        asset=c.post('/api/writing/prompt-import'+query,json={'path':'questions.pdf'}).json()
        body={'title':'Exam test','kind':'exam','tasks':[1],'profiles':{'1':'bar'},'promptFiles':{'1':asset['id']},
            'durationMinutes':60,'opensAt':(clock[0]+timedelta(minutes=1)).isoformat(),
            'closesAt':(clock[0]+timedelta(minutes=20)).isoformat(),'examPassword':'exam-first-password','published':True}
        for field in ['durationMinutes','opensAt','closesAt','examPassword']:
            bad={**body,field:None}
            assert c.post('/api/writing/assignments'+query,json=bad).status_code==400,field
        assert c.post('/api/writing/assignments'+query,json={**body,'closesAt':body['opensAt']}).status_code==400
        created=c.post('/api/writing/assignments'+query,json=body);created.raise_for_status();exam=created.json()
        assert 'examSecret' not in exam and 'examPassword' not in exam
        assert mongo_database.writing_assignments.find_one({'_id':exam['id']})['examSecret']['hash']!='exam-first-password'
        invite=share(c,owner,project,'exam-student@example.com','student')
        student=signup(c,'exam-student@example.com','student')
        c.post('/api/shares/accept',json={'token':invite['inviteToken']}).raise_for_status()
        route='/api/writing/assignments/'+exam['id']
        overview=c.get('/api/writing/overview'+query).json()
        item=next(a for a in overview['assignments'] if a['id']==exam['id'])
        assert item['kind']=='exam' and 'promptFiles' not in item and 'prompts' not in item
        assert 'promptFiles' not in c.get(route+query).json()
        assert c.get('/api/writing/prompt-files/'+asset['id']+query).status_code==404
        assert c.get(route+'/draft'+query).status_code==403
        assert c.post(route+'/enter'+query,json={'password':'exam-first-password'}).status_code==403
        assert c.patch(route+'/exam-password'+query,json={'password':'forbidden','revision':exam['revision']}).status_code==403
        clock[0]+=timedelta(minutes=2)
        assert c.post(route+'/enter'+query,json={'password':'wrong'}).status_code==403
        entry=c.post(route+'/enter'+query,json={'password':'exam-first-password'});entry.raise_for_status()
        draft=entry.json()['draft'];assert draft['deadline']==body['closesAt']
        assert c.get('/api/writing/prompt-files/'+asset['id']+query).status_code==200
        assert c.get(route+'/draft'+query).json()['deadline']==draft['deadline']
        use(c,owner)
        changed=c.patch(route+'/exam-password'+query,json={'password':'exam-new-password','revision':exam['revision']})
        changed.raise_for_status();assert 'examSecret' not in changed.json()
        assert c.patch(route+'/exam-password'+query,json={'password':'another-password','revision':exam['revision']}).status_code==409
        invite2=share(c,owner,project,'exam-student2@example.com','student')
        signup(c,'exam-student2@example.com','student')
        c.post('/api/shares/accept',json={'token':invite2['inviteToken']}).raise_for_status()
        assert c.post(route+'/enter'+query,json={'password':'exam-first-password'}).status_code==403
        c.post(route+'/enter'+query,json={'password':'exam-new-password'}).raise_for_status()
        use(c,student)
        assert c.get(route+'/draft'+query).json()['deadline']==draft['deadline']
        saved=c.put(route+'/draft'+query,json={'revision':draft['revision'],'answers':{'1':'The chart shows a trend.'}}).json()
        submission_body={'revision':saved['revision'],'requestId':'e'*32}
        posted=c.post(route+'/submit'+query,json=submission_body);posted.raise_for_status()
        assert posted.json()['assignment']['kind']=='exam'
        assert c.get(route+'/draft'+query).json()['readOnly'] is True
        assert c.put(route+'/draft'+query,json={'revision':saved['revision'],'answers':{'1':'change'}}).status_code==403
        clock[0]+=timedelta(minutes=30)
        assert c.post(route+'/submit'+query,json=submission_body).json()['id']==posted.json()['id']
        assert c.post(route+'/enter'+query,json={'password':'exam-new-password'}).status_code==403


def test_exam_duration_expires_before_closing_and_blocks_writes(tmp_path,monkeypatch):
    from latex_backend.writing import exams
    clock=[datetime.now(timezone.utc)]
    monkeypatch.setattr(exams,'utc_now',lambda:clock[0])
    app=create_web_app(tmp_path)
    with TestClient(app,base_url=URL) as c:
        owner,project,query,_,_,_,_=setup(c,app)
        exam=c.post('/api/writing/assignments'+query,json={'title':'Short exam','kind':'exam','tasks':[2],
            'profiles':{'2':'discussion'},'prompts':{'2':'Discuss it'},'durationMinutes':1,
            'opensAt':(clock[0]-timedelta(minutes=1)).isoformat(),'closesAt':(clock[0]+timedelta(hours=1)).isoformat(),
            'examPassword':'password','published':True}).json()
        invite=share(c,owner,project,'short-student@example.com','student')
        signup(c,'short-student@example.com','student');c.post('/api/shares/accept',json={'token':invite['inviteToken']}).raise_for_status()
        route='/api/writing/assignments/'+exam['id']
        draft=c.post(route+'/enter'+query,json={'password':'password'}).json()['draft']
        c.put(route+'/draft'+query,json={'revision':0,'answers':{'2':'Saved answer'}}).raise_for_status()
        clock[0]+=timedelta(minutes=2)
        assert c.get(route+'/draft'+query).json()['readOnly'] is True
        assert c.put(route+'/draft'+query,json={'revision':1,'answers':{'2':'Late change'}}).status_code==403
        assert c.post(route+'/submit'+query,json={'revision':1,'requestId':'x'*32}).status_code==403
        assert c.get(route+'/draft'+query).json()['answers']['2']=='Saved answer'

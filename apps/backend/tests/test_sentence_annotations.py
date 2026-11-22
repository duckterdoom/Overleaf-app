import copy
import uuid
from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from latex_backend.writing.points import point_allocations
from test_web_sharing import URL,use
from test_writing_workflow import signup
from test_writing_points import body


def test_sentence_feedback_anchors_unicode_privacy_publication_and_export(tmp_path):
    with TestClient(create_web_app(tmp_path),base_url=URL) as c:
        tutor=signup(c,'sentence-tutor@example.com','tutor')
        name=c.get('/api/session').json()['session']['name']
        course=c.post('/api/writing/classes',json={'title':'Sentence feedback'}).json()
        assignment=c.post('/api/writing/assignments',json=body(course['id'])).json()
        student=signup(c,'sentence-student@example.com','student')
        c.post('/api/writing/classes/join',json={'code':course['joinCode']}).raise_for_status()
        route='/api/writing/assignments/'+assignment['id']
        text='\U0001f600 A weak sentence.\n\nAnother sentence.'
        draft=c.get(route+'/draft').json()
        saved=c.put(route+'/draft',json={'revision':draft['revision'],'answers':{'1':text}}).json()
        submitted=c.post(route+'/submit',json={'revision':saved['revision'],'requestId':uuid.uuid4().hex}).json()
        reviewroute='/api/writing/submissions/'+submitted['id']
        note={'id':'sentence_1','start':3,'end':19,'quote':'A weak sentence.','kind':'replace','category':'intro',
              'replacement':'A strong sentence.','comment':'Use a precise subject.','authorName':'Forged identity','createdAt':'fake'}
        grading={'revision':0,'tasks':{'1':{'profile':'rubric1','scores':{},'criterionScores':point_allocations(assignment)['1'],'annotations':[note]}}}
        assert c.put(reviewroute+'/review',json=grading).status_code==403
        use(c,tutor)
        response=c.put(reviewroute+'/review',json=grading);response.raise_for_status()
        actual=response.json()['tasks']['1']['annotations'][0]
        assert actual['authorName']==name and actual['createdAt']!='fake'
        assert c.put(reviewroute+'/review',json=grading).status_code==409
        use(c,student)
        assert c.get(reviewroute).json()['review'] is None
        use(c,tutor)
        invalids=[{'quote':'Wrong quote'},{'end':1000},{'start':0,'end':1,'quote':'\U0001f600'},
                  {'category':'unassigned'},{'kind':'comment','comment':''},{'replacement':''}]
        for invalid in invalids:
            attempt=copy.deepcopy(grading);attempt['revision']=1;attempt['tasks']['1']['annotations']=[{**note,**invalid}]
            assert c.put(reviewroute+'/review',json=attempt).status_code==400
        attempt=copy.deepcopy(grading);attempt['revision']=1;attempt['tasks']['1']['annotations']=[note,note]
        assert c.put(reviewroute+'/review',json=attempt).status_code==400
        grading['revision']=1
        grading['tasks']['1']['annotations'].append({'id':'sentence_2','start':5,'end':9,'quote':'weak',
            'kind':'underline','category':'body','comment':'Choose a more specific adjective.'})
        updated=c.put(reviewroute+'/review',json=grading);updated.raise_for_status()
        assert updated.json()['tasks']['1']['annotations'][0]['createdAt']==actual['createdAt']
        published=c.post(reviewroute+'/publish',json={'revision':2});published.raise_for_status()
        assert published.json()['totalScore']==10
        latex=c.post('/api/writing/latex',json={'submissionId':submitted['id'],'report':True,'createProject':False}).json()['source']
        assert 'Sentence feedback' in latex and 'Suggested correction: A strong sentence.' in latex
        use(c,student)
        result=c.get(reviewroute).json()
        assert result['submission']['answers']['1']==text
        assert len(result['published']['tasks']['1']['annotations'])==2
        assert result['published']['tasks']['1']['annotations'][0]['authorName']==name
        assert c.put(reviewroute+'/review',json=grading).status_code==403

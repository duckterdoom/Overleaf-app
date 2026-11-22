import copy
import uuid
import pytest
from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from latex_backend.writing.points import point_allocations
from test_web_sharing import URL, use
from test_writing_workflow import signup


def body(course, category='ielts', tasks=None, total=10):
    tasks = tasks or [1]
    catalog = {}
    for task in tasks:
        catalog['rubric'+str(task)] = {'category': category, 'task': task,
            'family': 'academic' if category=='ielts' and task==1 else 'essay' if category=='ielts' else category,
            'label': 'Criteria', 'focus': [], 'sectionTitles': {'intro': 'Introduction', 'body': 'Evidence'},
            'checks': {'intro': [{'id': 'opening', 'text': 'Opening', 'weight': 2}, {'id': 'purpose', 'text': 'Purpose', 'weight': 1}],
                       'body': [{'id': 'evidence', 'text': 'Evidence', 'weight': 3}]}}
    return {'title':'Points exercise', 'classId':course, 'tasks':tasks, 'category':category,
            'profiles':{str(t):'rubric'+str(t) for t in tasks}, 'prompts':{str(t):'Write a response.' for t in tasks},
            'rubricCatalog':catalog, 'published':True, 'scoring':{'mode':'points','total':total},
            'assessmentCategories':{str(t):['intro','body'] for t in tasks}}


@pytest.mark.parametrize('category,tasks,total', [('ielts',[1,2],5),('ielts',[2],10),('report',[1],20),('research',[1],100)])
def test_points_snapshot_scoring_publish_and_report(tmp_path, category, tasks, total):
    with TestClient(create_web_app(tmp_path),base_url=URL) as c:
        teacher=signup(c,'point-teacher@example.com','tutor')
        course=c.post('/api/writing/classes',json={'title':'Point course'}).json()
        request=body(course['id'],category,tasks,total)
        if 1 in tasks: request['imageId']='sample-commuting'
        created=c.post('/api/writing/assignments',json=request);created.raise_for_status();assignment=created.json()
        limits=point_allocations(assignment)
        assert round(sum(v for values in limits.values() for v in values.values()),2)==total
        question=c.post('/api/writing/questions',json={'content':request}).json()
        assert question['content']['scoring']['total']==total
        student=signup(c,'point-student@example.com','student')
        c.post('/api/writing/classes/join',json={'code':course['joinCode']}).raise_for_status()
        route='/api/writing/assignments/'+assignment['id']
        draft=c.get(route+'/draft').json()
        saved=c.put(route+'/draft',json={'revision':draft['revision'],'answers':{str(t):'Opening.\n\nEvidence.' for t in tasks}}).json()
        submitted=c.post(route+'/submit',json={'revision':saved['revision'],'requestId':uuid.uuid4().hex}).json()
        reviewroute='/api/writing/submissions/'+submitted['id']
        grading={'revision':0,'tasks':{str(t):{'profile':'rubric'+str(t),'scores':{},'criterionScores':limits[str(t)]} for t in tasks}}
        assert c.put(reviewroute+'/review',json=grading).status_code==403
        use(c,teacher)
        # Edits to the exercise after submission must not change the marking scale.
        edited={**request,'scoring':{'mode':'points','total':total+10},'revision':assignment['revision']}
        assert c.put(route,json=edited).status_code==409
        c.put(reviewroute+'/review',json=grading).raise_for_status()
        published=c.post(reviewroute+'/publish',json={'revision':1});published.raise_for_status();result=published.json()
        assert result['totalScore']==total and result['maxScore']==total
        assert result['taskBands']=={} and result['writingBand'] is None
        latex=c.post('/api/writing/latex',json={'submissionId':submitted['id'],'report':True,'createProject':False}).json()['source']
        assert 'Total score:' in latex and 'Estimated task band' not in latex
        exported=c.post('/api/writing/latex',json={'submissionId':submitted['id'],'report':True,'createProject':True,'createDocument':True})
        assert exported.status_code==200, exported.text
        project=exported.json()['project']['id']
        files={name:c.get(f'/api/projects/{project}/file',params={'path':name}).json()['content'] for name in ('main.tex','question.tex','answer.tex','feedback.tex')}
        assert all(r'\input{'+name+'}' in files['main.tex'] for name in ('question.tex','answer.tex','feedback.tex'))
        assert 'Write a response.' in files['question.tex'] and 'Opening.' not in files['question.tex']
        assert 'Opening.' in files['answer.tex'] and 'Write a response.' not in files['answer.tex']
        assert 'Total score:' in files['feedback.tex'] and 'Opening.' not in files['feedback.tex']
        assert exported.json()['project']['main']=='main.tex'
        if 1 in tasks:
            assert any(item['path']=='prompt-image.png' for item in c.get(f'/api/projects/{project}/files').json())
        use(c,student)
        assert c.get(f'/api/projects/{project}/file?path=feedback.tex').status_code==404
        assert c.get(reviewroute).json()['published']['totalScore']==total


def test_points_validation_categories_bounds_and_incomplete_publish(tmp_path):
    with TestClient(create_web_app(tmp_path),base_url=URL) as c:
        teacher=signup(c,'point-validation@example.com','tutor')
        course=c.post('/api/writing/classes',json={'title':'Validation'}).json()
        request=body(course['id'])
        for selected in [[],['unknown'],['intro','intro']]:
            assert c.post('/api/writing/assignments',json={**request,'assessmentCategories':{'1':selected}}).status_code==400
        for total in [0,-1,1.111]:
            assert c.post('/api/writing/assignments',json={**request,'scoring':{'mode':'points','total':total}}).status_code==422
        request['assessmentCategories']={'1':['intro']}
        assignment=c.post('/api/writing/assignments',json=request).json()
        assert set(assignment['rubricCatalog']['rubric1']['checks'])=={'intro'}
        limits=point_allocations(assignment)['1']
        assert sum(limits.values())==10
        student=signup(c,'point-validation-student@example.com','student')
        c.post('/api/writing/classes/join',json={'code':course['joinCode']}).raise_for_status()
        route='/api/writing/assignments/'+assignment['id']
        draft=c.get(route+'/draft').json()
        saved=c.put(route+'/draft',json={'revision':draft['revision'],'answers':{'1':'Introduction only.'}}).json()
        sub=c.post(route+'/submit',json={'revision':saved['revision'],'requestId':uuid.uuid4().hex}).json()
        reviewroute='/api/writing/submissions/'+sub['id']
        use(c,teacher)
        grading={'revision':0,'tasks':{'1':{'profile':'rubric1','scores':{},'criterionScores':{}}}}
        for scores in [{'unknown':1},{'intro:opening':-1},{'intro:opening':100},{'intro:opening':.001}]:
            invalid=copy.deepcopy(grading);invalid['tasks']['1']['criterionScores']=scores
            assert c.put(reviewroute+'/review',json=invalid).status_code==400
        c.put(reviewroute+'/review',json=grading).raise_for_status()
        assert c.post(reviewroute+'/publish',json={'revision':1}).status_code==400
        grading['revision']=1;grading['tasks']['1']['criterionScores']={key:0 for key in limits}
        c.put(reviewroute+'/review',json=grading).raise_for_status()
        assert c.post(reviewroute+'/publish',json={'revision':2}).json()['totalScore']==0
        grading['revision']=2;grading['awardedTotal']=7.25
        saved_review=c.put(reviewroute+'/review',json=grading)
        assert saved_review.status_code==200 and saved_review.json()['awardedTotal']==7.25
        result=c.post(reviewroute+'/publish',json={'revision':3,'reason':'Tutor final adjustment'}).json()
        assert result['totalScore']==7.25 and result['calculatedScore']==0 and result['maxScore']==10
        assert c.get(route).json()['scoring']['total']==10


def test_whole_paper_exact_rounding_and_default_weights(tmp_path):
    with TestClient(create_web_app(tmp_path),base_url=URL) as c:
        signup(c,'whole-points@example.com','tutor')
        course=c.post('/api/writing/classes',json={'title':'Whole paper'}).json()
        request=body(course['id'],total=.03)
        request.pop('assessmentCategories')
        for checks in request['rubricCatalog']['rubric1']['checks'].values():
            for check in checks:check.pop('weight')
        response=c.post('/api/writing/assignments',json=request);response.raise_for_status()
        assignment=response.json()
        assert set(assignment['rubricCatalog']['rubric1']['checks'])=={'intro','body'}
        assert all(check['weight']==2 for checks in assignment['rubricCatalog']['rubric1']['checks'].values() for check in checks)
        assert list(point_allocations(assignment)['1'].values())==[.01,.01,.01]
        request['scoring']['total']=.02
        assert c.post('/api/writing/assignments',json=request).status_code==400


def test_awarded_total_is_separate_from_scale_and_requires_complete_criteria():
    from latex_backend.writing.points import point_result, validate_awarded_total
    from latex_backend.storage.store import Problem
    assignment=body('course')
    limits=point_allocations(assignment)
    review={'tasks': {'1': {'profile':'rubric1','criterionScores':limits['1']}}, 'awardedTotal':7.25}
    result=point_result(assignment,review)
    assert result['totalScore']==7.25 and result['calculatedScore']==10 and result['maxScore']==10
    assert assignment['scoring']['total']==10
    for invalid in (-1,11,.001,float('nan'),float('inf')):
        with pytest.raises(Problem):validate_awarded_total(assignment,{**review,'awardedTotal':invalid})
    review['tasks']['1']['criterionScores']={}
    with pytest.raises(Problem,match='criterion'):point_result(assignment,review)

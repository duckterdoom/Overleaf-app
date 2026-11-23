from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import signup
from test_writing_points import body


def test_library_taxonomy_filters_snapshots_before_pagination_and_respects_visibility(tmp_path):
    with TestClient(create_web_app(tmp_path),base_url=URL) as c:
        owner=signup(c,'taxonomy-owner@example.com','tutor')
        intro=body(None);intro['assessmentCategories']={'1':['intro']}
        intro['title']='Introduction chart'
        created=c.post('/api/writing/questions',json={'content':intro,'visibility':'public'});created.raise_for_status()
        for number in range(51):
            report=body(None,'report');report['title']='Report '+str(number)
            c.post('/api/writing/questions',json={'content':report}).raise_for_status()
        result=c.get('/api/writing/questions?scope=mine&category=ielts&task=1&profile=rubric1&section=intro').json()
        assert result['total']==1 and result['items'][0]['title']=='Introduction chart'
        assert any(p['category']=='report' for p in result['taxonomy'])
        assert c.get('/api/writing/questions?scope=mine&category=ielts&profile=rubric1&section=body').json()['total']==0
        assert c.get('/api/writing/questions?scope=mine&category=report&task=1&profile=rubric1&section=body').json()['total']==51
        assert len(c.get('/api/writing/questions?scope=mine&category=report&page=1').json()['items'])==1
        assert c.get('/api/writing/questions?category=ielts&section=intro').json()['total']==1
        assert c.get('/api/writing/questions?category=report&sections=intro&sections=body').json()['total']==51
        assert c.get('/api/writing/questions?category=ielts&sections=intro&sections=body').json()['total']==0
        assert c.get('/api/writing/questions?profile=$where').status_code==422
        assert c.get('/api/writing/questions?profile=rubric1&section=a.b').status_code==422
        other=signup(c,'taxonomy-other@example.com','tutor')
        public=c.get('/api/writing/questions?scope=public').json()
        assert public['total']==1
        assert not any(p['category']=='report' and p['key']=='rubric1' for p in public['taxonomy'])
        assert {'line','bar','report','research'} <= {p['key'] for p in public['taxonomy']}
        assert next(p for p in public['taxonomy'] if p['key']=='rubric1')['sections']==[{'key':'intro','label':'Introduction'}]
        assert not any(p['key']=='rubric1' for p in c.get('/api/writing/questions?scope=mine').json()['taxonomy'])
        use(c,owner)
        assert not any(p['key']=='rubric1' for p in c.get('/api/writing/questions?scope=public').json()['taxonomy'])


def test_combined_categories_must_exist_in_the_same_task_snapshot(tmp_path):
    with TestClient(create_web_app(tmp_path),base_url=URL) as c:
        signup(c,'combined-categories@example.com','tutor')
        intro=body(None);intro['title']='Only introduction';intro['assessmentCategories']={'1':['intro']}
        together={**body(None),'title':'Introduction and body'}
        split=body(None,tasks=[1,2]);split['title']='Split across tasks';split['assessmentCategories']={'1':['intro'],'2':['body']}
        for content in [intro,together,split]:
            c.post('/api/writing/questions',json={'content':content}).raise_for_status()
        result=c.get('/api/writing/questions?scope=mine&category=ielts&sections=intro&sections=body').json()
        assert result['total']==1 and result['items'][0]['title']=='Introduction and body'

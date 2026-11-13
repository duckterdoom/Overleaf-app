import copy
from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL,use
from test_writing_workflow import signup
from test_writing_sharing import share


def test_report_research_no_classes_no_bands_project_rubric_crud(tmp_path):
    app=create_web_app(tmp_path)
    with TestClient(app,base_url=URL) as c:
        owner=signup(c,'unified-owner@example.com','tutor')
        project=c.post('/api/projects',json={'title':'Shared assessment'}).json()['id']
        c.get('/api/projects').raise_for_status()
        query='?projectId='+project
        settings=c.get('/api/writing/settings'+query).json()
        original=copy.deepcopy(settings['catalog'])
        catalog=copy.deepcopy(original)
        catalog['custom_research']=copy.deepcopy(catalog['research'])
        catalog['custom_research']['label']='Custom research rubric'
        catalog['custom_research']['checks']['methodology']=[{'id':'method','text':'Reproducible methodology','codes':['CONTENT']}]
        catalog['custom_research']['sectionTitles']['methodology']='Methodology'
        del catalog['research']
        del catalog['line']
        saved=c.put('/api/writing/settings'+query,json={'revision':0,'catalog':catalog})
        saved.raise_for_status()
        assert 'line' not in saved.json()['catalog'] and 'research' not in saved.json()['catalog']
        assert c.put('/api/writing/settings'+query,json={'revision':0,'catalog':catalog}).status_code==409
        invitation=share(c,owner,project,'unified-student@example.com','student')
        student=signup(c,'unified-student@example.com','student')
        c.post('/api/shares/accept',json={'token':invitation['inviteToken']}).raise_for_status()
        for category,profile in [('report','report'),('research','custom_research')]:
            use(c,owner)
            assignment=c.post('/api/writing/assignments'+query,json={'title':category,'category':category,
                'tasks':[1],'profiles':{'1':profile},'prompts':{'1':'Investigate the given problem.'},'published':True})
            assignment.raise_for_status();assignment=assignment.json()
            assert assignment['classId'] is None
            use(c,student)
            overview=c.get('/api/writing/overview'+query).json()
            assert any(a['id']==assignment['id'] for a in overview['assignments'])
            route='/api/writing/assignments/'+assignment['id']
            draft=c.get(route+'/draft'+query).json()
            draft=c.put(route+'/draft'+query,json={'revision':draft['revision'],'answers':{'1':'Introduction.\n\nEvidence with citations.\n\nConclusion.'}}).json()
            submission=c.post(route+'/submit'+query,json={'revision':draft['revision'],'requestId':'z'*32}).json()
            use(c,owner)
            review='/api/writing/submissions/'+submission['id']
            data={'revision':0,'tasks':{'1':{'profile':profile,'scores':{},'notes':{},'overall':'Good analysis','sections':[
                {'paragraph':1,'kind':'methodology' if category=='research' else 'body','title':'Evidence','checks':{},'note':'Explain the source.'}]}},
                'summary':'Clear reasoning','recommendations':'Add references'}
            c.put(review+'/review'+query,json=data).raise_for_status()
            published=c.post(review+'/publish'+query,json={'revision':1})
            published.raise_for_status();published=published.json()
            assert published['taskBands']=={} and published['writingBand'] is None
            assert len(published['resultFiles'])==1
            source=c.get(f"/api/projects/{project}/file",params={'path':published['resultFiles'][0]}).json()['content']
            assert 'Good analysis' in source and 'Estimated task band' not in source and 'Writing band' not in source
            bad=copy.deepcopy(data);bad['revision']=1;bad['tasks']['1']['scores']={'TA':9,'CC':9,'LR':9,'GRA':9}
            assert c.put(review+'/review'+query,json=bad).status_code==400
        assert c.get('/api/writing/overview'+query).json()['classes']==[]
        assert c.get('/api/writing/overview'+query).json()['students'][0]['name']=='Student'
        # Delete an active profile only from future configuration; snapshots remain readable.
        catalog.pop('custom_research')
        c.put('/api/writing/settings'+query,json={'revision':1,'catalog':catalog}).raise_for_status()
        assert c.get('/api/writing/assignments/'+assignment['id']+query).json()['rubricCatalog']['custom_research']['label']=='Custom research rubric'

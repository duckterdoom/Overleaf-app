import io
import zipfile
from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import URL, use
from test_writing_workflow import signup


def test_project_tags_and_source_archive_follow_owner_and_sharing_acl(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        owner = signup(client, 'library-owner@example.com', 'tutor')
        first = client.post('/api/projects', json={'title':'First project'}).json()['id']
        second = client.post('/api/projects', json={'title':'Second project'}).json()['id']
        tags = {'project':first, 'tags':['Research', 'Draft', 'Research']}
        response = client.post('/api/actions/project-update', json=tags)
        assert response.status_code == 200, response.text
        assert response.json()['tags'] == ['Research','Draft']
        archived = client.post('/api/actions/project-update', json={'project': first, 'archived': True})
        assert archived.status_code == 200 and archived.json()['archived'] is True
        assert next(p for p in client.get('/api/projects').json() if p['id'] == first)['archived'] is True
        assert client.post('/api/actions/project-update', json={'project': first, 'archived': 'yes'}).status_code == 400
        restored = client.post('/api/actions/project-update', json={'project': first, 'archived': False})
        assert restored.json()['archived'] is False
        renamed=client.post('/api/actions/project-update',json={'project':first,'title':'Renamed first'})
        assert renamed.json()['tags']==['Research','Draft']
        invalid = client.post('/api/actions/project-update', json={'project':first,'tags':['x']*21})
        assert invalid.status_code == 400
        assert next(p for p in client.get('/api/projects').json() if p['id']==first)['tags'] == ['Research','Draft']
        added=client.post('/api/actions/project-update',json={'project':first,'tags':['Peer'],'tagMode':'add'})
        assert added.json()['tags']==['Research','Draft','Peer']
        removed=client.post('/api/actions/project-update',json={'project':first,'tags':['Draft'],'tagMode':'remove'})
        assert removed.json()['tags']==['Research','Peer']
        exported = client.post('/api/projects/archive', json={'projects':[first,second,first]})
        assert exported.status_code == 200, exported.text[:100] if exported.headers.get('content-type')!='application/zip' else ''
        with zipfile.ZipFile(io.BytesIO(exported.content)) as archive:
            names = archive.namelist()
            assert sum(name.endswith('/main.tex') for name in names)==2
            assert not any('/.' in name or '/build/' in name for name in names)
        token = client.patch(f'/api/projects/{first}/sharing', json={'mode':'link','permission':'viewer'}).json()['linkToken']
        viewer = signup(client, 'library-viewer@example.com', 'student')
        client.post('/api/shares/accept', json={'token':token}).raise_for_status()
        assert client.post('/api/projects/archive', json={'projects':[first]}).status_code==200
        assert client.post('/api/actions/project-update', json=tags).status_code==403
        assert client.post('/api/actions/project-delete', json={'project':first}).status_code==403
        assert client.post('/api/actions/project-update', json={'project':first,'archived':True}).status_code==403
        assert client.post('/api/projects/archive', json={'projects':[first,second]}).status_code==404
        assert client.post('/api/projects/archive', json={'projects':[]}).status_code==422
        assert client.post('/api/projects/archive', json={'projects':['../private']}).status_code==404
        use(client,owner)
        assert len(client.get('/api/projects').json())==2


def test_source_archive_rejects_oversized_input_before_reading():
    from types import SimpleNamespace
    from threading import RLock
    import pytest
    from latex_backend.documents.project_archive import build_archive
    from latex_backend.storage.store import Problem
    store = SimpleNamespace(lock=RLock(), info=lambda _: {'title':'Large'},
        tree=lambda _: [{'path':'large.bin','directory':False}],
        path=lambda *_: SimpleNamespace(stat=lambda: SimpleNamespace(st_size=129*1024*1024)))
    with pytest.raises(Problem) as raised:
        build_archive([(store,'a'*32)])
    assert raised.value.status == 413

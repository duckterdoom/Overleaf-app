from fastapi.testclient import TestClient
from latex_backend.core.application import create_web_app
from test_web_sharing import account, use, URL
from test_presence import connect
from test_live_documents import event


def test_shared_review_authorship_notifications_and_viewer_permissions(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as client:
        owner = account(client, 'owner@example.com', 'Real Owner')
        project = client.post('/api/projects', json={'title':'Shared review'}).json()['id']
        link = client.patch(f'/api/projects/{project}/sharing', json={'mode':'link','permission':'editor'}).json()['linkToken']
        editor = account(client, 'editor@example.com', 'Actual Editor')
        client.post('/api/shares/accept', json={'token':link}).raise_for_status()
        route = f'/api/projects/{project}/review'
        with connect(client, project, owner) as a, connect(client, project, editor) as b:
            created = client.post(route,json={'operation':'create','file':'main.tex','from':2,'line':1,'quote':'TeX','text':'Shared comment'})
            created.raise_for_status()
            thread = created.json()[0]
            assert thread['messages'][0]['author'] == 'Actual Editor'
            for ws in [a,b]:
                assert event(ws,'review-changed') == {'type':'review-changed','project':project}
            use(client,owner)
            assert client.get(route).json()[0]['id'] == thread['id']
            client.post(route,json={'operation':'reply','id':thread['id'],'text':'Owner reply'}).raise_for_status()
            assert event(b,'review-changed')['project'] == project
            use(client,editor)
            assert client.get(route).json()[0]['messages'][-1]['author'] == 'Real Owner'
        use(client,owner)
        viewer_link = client.patch(f'/api/projects/{project}/sharing',json={'mode':'link','permission':'viewer'}).json()['linkToken']
        viewer = account(client,'viewer@example.com','Viewer')
        client.post('/api/shares/accept',json={'token':viewer_link}).raise_for_status()
        assert client.get(route).json()[0]['messages'][-1]['text'] == 'Owner reply'
        assert client.post(route,json={'operation':'delete','id':thread['id']}).status_code == 403
        assert client.get(route).json()[0]['id'] == thread['id']

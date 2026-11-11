from io import BytesIO
import sys
import pytest
from fastapi.testclient import TestClient
from PIL import Image
from latex_backend.http.api import create_api
from latex_backend.compilation.compiler import Compiler
from latex_backend.storage.store import Store


def test_local_web_cookie_bootstrap_and_origin_guards(tmp_path):
    store = Store(tmp_path)
    with TestClient(create_api(store, Compiler(store), 'secret', web=True), base_url='http://127.0.0.1:8000') as client:
        assert client.get('/api/projects').status_code == 401
        assert client.post('/api/web/session', headers={'Origin':'https://evil.example'}).status_code == 403
        response = client.post('/api/web/session', headers={'Origin':'http://127.0.0.1:8000'})
        assert response.status_code == 200 and response.json() == {'mode':'local-web'}
        cookie = response.headers['set-cookie'].lower()
        assert 'httponly' in cookie and 'samesite=strict' in cookie and 'path=/api' in cookie
        assert client.get('/api/health').json()['mode'] == 'local-web'
        project = client.post('/api/projects', json={'title':'Browser project'}).json()['id']
        assert client.put(f'/api/projects/{project}/file', json={'path':'main.tex','content':'saved from browser'}).status_code == 200
        assert store.read(project, 'main.tex') == 'saved from browser'
        assert client.post('/api/actions/offline', headers={'Origin':'http://127.0.0.1:9999'}, json={}).status_code == 403
        assert client.get('/api/projects', headers={'Sec-Fetch-Site':'cross-site'}).status_code == 403
        assert client.get('/api/projects', headers={'Host':'evil.example'}).status_code == 403
        client.cookies.clear()
        assert client.get('/api/projects').status_code == 401


def test_cookie_does_not_enable_desktop_api(tmp_path):
    store = Store(tmp_path)
    with TestClient(create_api(store, Compiler(store), 'secret'), base_url='http://127.0.0.1') as client:
        client.cookies.set('overleaf_local_session','secret')
        assert client.get('/api/projects').status_code == 401
        assert client.post('/api/web/session').status_code == 401
        assert client.get('/api/projects', headers={'Authorization':'Bearer secret'}).status_code == 200


def test_browser_binary_preview_is_bounded_and_svg_downloads(tmp_path):
    store = Store(tmp_path)
    project = store.create('Browser files')['id']
    root = store.root(project)
    data = BytesIO()
    Image.new('RGB', (20,20),'green').save(data, format='PNG')
    (root/'picture.png').write_bytes(data.getvalue())
    (root/'picture.svg').write_text('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>')
    with TestClient(create_api(store, Compiler(store), 'secret', web=True), base_url='http://127.0.0.1') as client:
        client.post('/api/web/session')
        route = f'/api/projects/{project}/binary'
        result = client.get(route, params={'path':'picture.png'})
        assert result.content == data.getvalue() and result.headers['content-type'] == 'image/png'
        svg = client.get(route, params={'path':'picture.svg'})
        assert 'attachment' in svg.headers['content-disposition'] and 'sandbox' in svg.headers['content-security-policy']
        assert client.get(route, params={'path':'../secret.png'}).status_code in {400,403}
        assert client.get(route, params={'path':'main.tex'}).status_code == 404


def test_web_launcher_rejects_public_binding(monkeypatch):
    from latex_backend.entrypoints.web import main
    monkeypatch.setattr(sys, 'argv', ['overleaf-web','--host','0.0.0.0'])
    with pytest.raises(SystemExit) as error:
        main()
    assert error.value.code == 2

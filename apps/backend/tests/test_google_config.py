import json
import os
from urllib.parse import parse_qs, urlsplit

import pytest
from fastapi.testclient import TestClient

from latex_backend.auth import google_config as config
from latex_backend.core.application import create_web_app

URL = 'http://127.0.0.1:8000'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value), encoding='utf-8-sig')


def credentials(**extra):
    return {'client_id': 'test-client', 'client_secret': 'never-print-this-secret', **extra}


def test_no_config_keeps_email_login_and_disables_google(tmp_path):
    assert not config.settings().enabled
    with TestClient(create_web_app(tmp_path / 'data'), base_url=URL) as client:
        assert client.get('/api/auth/config').json()['googleEnabled'] is False
        response = client.get('/api/auth/google/start', follow_redirects=False)
        assert 'google_not_configured' in response.headers['location']


def test_project_file_enables_google_without_env_and_uses_current_port(tmp_path):
    write(config.project_config_path(), credentials())
    with TestClient(create_web_app(tmp_path / 'data'), base_url='http://127.0.0.1:8001') as client:
        response = client.get('/api/auth/config')
        assert response.json()['googleEnabled'] is True
        assert 'test-client' not in response.text and 'never-print' not in response.text
        start = client.get('/api/auth/google/start', follow_redirects=False)
        query = parse_qs(urlsplit(start.headers['location']).query)
        assert query['client_id'] == ['test-client']
        assert query['redirect_uri'] == ['http://127.0.0.1:8001/api/auth/google/callback']
        assert 'never-print' not in start.headers['location']


def test_user_file_survives_another_clone_and_google_download_schema(tmp_path, monkeypatch):
    write(config.user_config_path(), {'web': credentials(redirect_uris=[URL + '/api/auth/google/callback'])})
    assert config.settings().source == 'user-file'
    monkeypatch.setattr(config, 'REPO', tmp_path / 'second-clone')
    assert config.settings().client_id == 'test-client'
    with TestClient(create_web_app(tmp_path / 'data'), base_url=URL) as client:
        assert client.get('/api/auth/config').json()['googleEnabled'] is True


def test_env_pair_overrides_project_and_user_without_mixing(monkeypatch):
    write(config.user_config_path(), credentials(client_id='user-id'))
    write(config.project_config_path(), credentials(client_id='project-id', public_url=URL))
    assert config.settings().client_id == 'project-id'
    monkeypatch.setenv('LATEX_GOOGLE_CLIENT_ID', 'env-id')
    with pytest.raises(config.GoogleSetupError, match='both client_id'):
        config.settings()
    monkeypatch.setenv('LATEX_GOOGLE_CLIENT_SECRET', 'env-secret')
    monkeypatch.setenv('LATEX_PUBLIC_URL', 'http://localhost:8001/')
    assert config.settings().client_id == 'env-id'
    assert config.settings().public_url == 'http://localhost:8001'
    assert config.settings().source == 'environment'


def test_empty_project_template_falls_back_to_user_file():
    write(config.project_config_path(), {'client_id': '', 'client_secret': '', 'public_url': ''})
    write(config.user_config_path(), credentials())
    assert config.settings().source == 'user-file'


def test_explicit_config_and_invalid_missing_file(monkeypatch, tmp_path):
    path = tmp_path / 'team.json'
    write(path, credentials())
    monkeypatch.setenv('LATEX_GOOGLE_CONFIG', str(path))
    assert config.settings().source == 'explicit-file'
    monkeypatch.setenv('LATEX_GOOGLE_CONFIG', str(tmp_path / 'missing.json'))
    with pytest.raises(config.GoogleSetupError, match='Cannot read'):
        config.settings()


@pytest.mark.parametrize('value', [[], {'client_secret': 'sensitive'}, {'installed': credentials()}, {'web': []}])
def test_invalid_private_file_does_not_echo_credentials(value, tmp_path):
    write(config.project_config_path(), value)
    with TestClient(create_web_app(tmp_path / 'data'), base_url=URL) as client:
        response = client.get('/api/auth/config')
        assert response.status_code == 503
        assert 'sensitive' not in response.text and 'never-print' not in response.text


def test_invalid_json_and_oversize_are_sanitized():
    path = config.project_config_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('{"client_secret": "sensitive-unclosed', encoding='utf-8')
    with pytest.raises(config.GoogleSetupError) as error:
        config.settings()
    assert 'sensitive' not in str(error.value)
    path.write_text('x' * 65537)
    with pytest.raises(config.GoogleSetupError, match='64 KiB'):
        config.settings()


def test_import_download_once_and_check_cli_never_prints_secret(tmp_path, monkeypatch, capsys):
    download = tmp_path / 'downloaded-private.json'
    write(download, {'web': credentials()})
    monkeypatch.setattr('sys.argv', ['google_config', str(download)])
    assert config.main() == 0
    assert config.user_config_path().is_file()
    if os.name != 'nt':
        assert config.user_config_path().stat().st_mode & 0o777 == 0o600
    monkeypatch.setattr('sys.argv', ['google_config', '--check'])
    assert config.main() == 0
    output = capsys.readouterr()
    assert 'configured (user-file)' in output.out
    assert 'never-print' not in output.out + output.err
    with pytest.raises(config.GoogleSetupError, match='already exists'):
        config.import_file(download)
    config.import_file(download, replace=True)


@pytest.mark.parametrize('url', ['http://example.com', 'http://localhost:invalid', 'https://secret@example.com', 'http://localhost?secret=x'])
def test_invalid_public_url_not_echoed(url):
    with pytest.raises(config.GoogleSetupError) as error:
        config.validate('id', 'secret', url)
    assert url not in str(error.value)


def test_file_oauth_callback_completes_account_creation_without_env(tmp_path, monkeypatch):
    from latex_backend.http.web_accounts import FLOW_COOKIE
    write(config.project_config_path(), credentials())
    app = create_web_app(tmp_path / 'data')

    async def exchange(code, flow):
        assert code == 'code'
        assert flow['redirect'] == URL + '/api/auth/google/callback'
        return {'sub': 'file-oauth-user', 'email': 'file-oauth@example.com', 'email_verified': True, 'name': 'File OAuth'}

    monkeypatch.setattr(app, 'exchange_google', exchange)
    with TestClient(app, base_url=URL) as client:
        start = client.get('/api/auth/google/start', follow_redirects=False)
        state = parse_qs(urlsplit(start.headers['location']).query)['state'][0]
        assert client.cookies.get(FLOW_COOKIE) == state
        callback = client.get('/api/auth/google/callback?code=code&state=' + state, follow_redirects=False)
        assert callback.status_code == 303 and callback.headers['location'] == '/'
        user = client.get('/api/session').json()['session']
        assert user['provider'] == 'google' and not user['role']


def test_existing_terminal_config_can_be_saved_for_team_without_printing_secret(monkeypatch, capsys):
    monkeypatch.setenv('LATEX_GOOGLE_CLIENT_ID', 'team-client')
    monkeypatch.setenv('LATEX_GOOGLE_CLIENT_SECRET', 'private-team-secret')
    monkeypatch.setattr('sys.argv', ['google_config', '--from-env', '--project'])
    assert config.main() == 0
    monkeypatch.delenv('LATEX_GOOGLE_CLIENT_ID')
    monkeypatch.delenv('LATEX_GOOGLE_CLIENT_SECRET')
    assert config.settings().enabled and config.settings().source == 'project-file'
    output = capsys.readouterr()
    assert 'private-team-secret' not in output.out + output.err

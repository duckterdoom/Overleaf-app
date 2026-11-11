import json
import time
from urllib.parse import parse_qs, urlsplit

import pytest
from fastapi.testclient import TestClient

from latex_backend.core.application import create_web_app
from latex_backend.http.web_accounts import COOKIE, FLOW_COOKIE
from latex_backend.storage.state import State

URL = 'http://127.0.0.1:8000'


def register(client, email='student@example.com', name='Student'):
    result = client.post('/api/actions/register', json={'name': name, 'email': email, 'password': 'test-password', 'remember': True})
    assert result.status_code == 200, result.text
    return result


def choose(client, role='student', name='Student'):
    result = client.post('/api/auth/onboarding', json={'name': name, 'role': role})
    assert result.status_code == 200, result.text
    return result.json()


def test_onboarding_resume_roles_and_persistent_login(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        assert client.get('/api/session').json()['session'] is None
        assert client.post('/api/projects', json={'title': 'no identity'}).status_code == 401
        result = register(client)
        assert result.json()['needsRole'] and result.json()['role'] is None
        assert 'httponly' in result.headers['set-cookie'].lower()
        pending_cookie = client.cookies.get(COOKIE)
        assert client.post('/api/projects', json={'title': 'pending'}).status_code == 403
        assert client.get('/api/session').json()['session']['needsRole']
        assert client.post('/api/auth/onboarding', json={'role': 'admin', 'name': 'No'}).status_code == 400
        assert choose(client)['role'] == 'student'
        assert client.post('/api/auth/onboarding', json={'role': 'tutor', 'name': 'No'}).status_code == 409
        assert client.post('/api/projects', json={'title': 'My project'}).status_code == 201
        assert client.post('/api/actions/logout', json={}).status_code == 200
        assert client.get('/api/session').json()['session'] is None
        client.cookies.set(COOKIE, pending_cookie, path='/api')
        assert client.get('/api/session').json()['session'] is None  # revoked token
        client.cookies.clear()
        assert client.post('/api/actions/login', json={'email': 'student@example.com', 'password': 'wrong'}).status_code == 401
        result = client.post('/api/actions/login', json={'email': 'student@example.com', 'password': 'test-password', 'remember': True})
        assert result.json()['role'] == 'student' and not result.json()['needsRole']
        cookie = client.cookies.get(COOKIE)
    # Persisted cookie and role survive server restart; bootstrap is not identity.
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        client.cookies.set(COOKIE, cookie, path='/api')
        assert client.get('/api/session').json()['session']['role'] == 'student'
        assert len(client.get('/api/projects').json()) == 1


def test_two_users_have_isolated_files_sessions_and_settings(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        register(client)
        choose(client)
        one_cookie = client.cookies.get(COOKIE)
        project = client.post('/api/projects', json={'title': 'Private'}).json()['id']
        assert client.post('/api/actions/settings', json={'language': 'en'}).status_code == 200
        client.cookies.clear()
        register(client, 'tutor@example.com', 'Tutor')
        assert choose(client, 'tutor', 'Tutor')['role'] == 'tutor'
        assert client.get('/api/projects').json() == []
        assert client.get(f'/api/projects/{project}/files').status_code == 404
        assert client.get('/api/session').json()['settings']['language'] == 'vi'
        two_cookie = client.cookies.get(COOKIE)
        client.cookies.clear()
        client.cookies.set(COOKIE, one_cookie, path='/api')
        assert client.get('/api/session').json()['session']['email'] == 'student@example.com'
        assert len(client.get('/api/projects').json()) == 1
        client.post('/api/actions/logout', json={})
        client.cookies.clear()
        client.cookies.set(COOKIE, two_cookie, path='/api')
        assert client.get('/api/session').json()['session']['role'] == 'tutor'


def test_origin_limits_expiration_and_no_global_session_leak(tmp_path):
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        assert client.post('/api/actions/register', json={}, headers={'Origin': 'https://evil.example'}).status_code == 403
        assert client.get('/api/session', headers={'Host': 'evil.example'}).status_code == 403
        assert client.post('/api/actions/login', content='x' * 20000).status_code == 413
        assert client.post('/api/actions/offline', json={}).status_code == 403
        register(client)
        choose(client)
        from datetime import datetime, timedelta, timezone
        client.app.accounts.db.sessions.update_many({}, {'$set': {'expires_at': datetime.now(timezone.utc) - timedelta(seconds=1)}})
        assert client.get('/api/session').json()['session'] is None
        client.post('/api/web/session')
        assert client.post('/api/projects', json={'title': 'No bypass'}).status_code == 401


def test_google_flow_state_replay_nonce_and_identity_collision(tmp_path, monkeypatch):
    monkeypatch.setenv('LATEX_GOOGLE_CLIENT_ID', 'client-id')
    monkeypatch.setenv('LATEX_GOOGLE_CLIENT_SECRET', 'secret')
    app = create_web_app(tmp_path)
    calls = []
    async def exchange(code, flow):
        calls.append((code, flow))
        return {'sub': 'google-123', 'email': 'google@example.com', 'email_verified': True, 'name': 'Google User'}
    monkeypatch.setattr(app, 'exchange_google', exchange)
    with TestClient(app, base_url=URL) as client:
        start = client.get('/api/auth/google/start', follow_redirects=False)
        query = parse_qs(urlsplit(start.headers['location']).query)
        assert query['scope'] == ['openid email profile'] and query['code_challenge_method'] == ['S256']
        state = query['state'][0]
        bad = client.get('/api/auth/google/callback?code=x&state=wrong', follow_redirects=False)
        assert 'google_failed' in bad.headers['location'] and not calls
        # Invalid callback clears flow cookie: restore original browser binding for legitimate callback.
        client.cookies.set(FLOW_COOKIE, state, path='/api/auth/google')
        response = client.get(f'/api/auth/google/callback?code=valid&state={state}', follow_redirects=False, headers={'Sec-Fetch-Site': 'cross-site'})
        assert response.status_code == 303 and response.headers['location'] == '/'
        assert client.get('/api/session').json()['session']['needsRole']
        assert choose(client, 'tutor', 'Google Tutor')['role'] == 'tutor'
        assert client.get('/api/session').json()['session']['provider'] == 'google'
        client.cookies.set(FLOW_COOKIE, state, path='/api/auth/google')
        response = client.get(f'/api/auth/google/callback?code=valid&state={state}', follow_redirects=False)
        assert 'google_failed' in response.headers['location'] and len(calls) == 1
        assert app.accounts.google({'sub': 'google-123', 'email': 'changed@example.com', 'email_verified': True})['role'] == 'tutor'
        from latex_backend.storage.store import Problem
        with pytest.raises(Problem):
            app.accounts.google({'sub': 'different-sub', 'email': 'google@example.com', 'email_verified': True})
        with pytest.raises(Problem):
            app.accounts.google({'sub': 'unverified', 'email': 'x@example.com', 'email_verified': False})


def test_existing_credentials_imported_but_shared_projects_not_assigned(tmp_path, mongo_database):
    state = State(tmp_path)
    state.authenticate('register', 'Legacy', 'old@example.com', 'legacy-password', True)
    from latex_backend.storage.migration import migrate
    migrate(tmp_path, mongo_database, apply=True)
    with TestClient(create_web_app(tmp_path), base_url=URL) as client:
        assert client.get('/api/session').json()['session'] is None
        result = client.post('/api/actions/login', json={'email': 'old@example.com', 'password': 'legacy-password'})
        assert result.status_code == 200 and result.json()['needsRole']


def test_password_change_revokes_other_sessions(tmp_path):
    app = create_web_app(tmp_path)
    with TestClient(app, base_url=URL) as client:
        register(client); choose(client)
        original = client.cookies.get(COOKIE)
        result = client.post('/api/actions/profile', json={'currentPassword': 'test-password', 'password': 'new-password'})
        assert result.status_code == 200
        assert app.accounts.resolve(original) is None
        assert client.get('/api/session').json()['session']['role'] == 'student'


def test_google_exchange_passes_pkce_and_verifies_nonce_and_audience(tmp_path, monkeypatch):
    import asyncio
    import httpx
    from google.oauth2 import id_token
    from latex_backend.storage.store import Problem

    monkeypatch.setenv('LATEX_GOOGLE_CLIENT_ID', 'my-client')
    monkeypatch.setenv('LATEX_GOOGLE_CLIENT_SECRET', 'server-secret')
    app = create_web_app(tmp_path)
    calls = []

    class Exchange:
        async def __aenter__(self): return self
        async def __aexit__(self, *args): pass
        async def post(self, url, data):
            calls.append(data)
            return httpx.Response(200, json={'id_token': 'signed-token'}, request=httpx.Request('POST', url))

    monkeypatch.setattr(httpx, 'AsyncClient', lambda **kwargs: Exchange())
    def verify(token, transport, audience):
        assert token == 'signed-token' and audience == 'my-client'
        return {'nonce': 'expected-nonce', 'sub': 'google-sub'}
    monkeypatch.setattr(id_token, 'verify_oauth2_token', verify)
    flow = {'nonce': 'expected-nonce', 'verifier': 'pkce-secret', 'redirect': URL + '/api/auth/google/callback'}
    try:
        assert asyncio.run(app.exchange_google('auth-code', flow))['sub'] == 'google-sub'
        assert calls[0]['code_verifier'] == 'pkce-secret'
        assert calls[0]['client_secret'] == 'server-secret'
        with pytest.raises(Problem):
            asyncio.run(app.exchange_google('auth-code', {**flow, 'nonce': 'wrong'}))
    finally:
        app.accounts.close()

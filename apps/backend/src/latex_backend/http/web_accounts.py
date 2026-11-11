"""Browser account gateway and isolated user workspaces for the local web server."""
from __future__ import annotations

import asyncio
import base64
import hashlib
import json
import re
import secrets
import time
from contextlib import AsyncExitStack, asynccontextmanager
from urllib.parse import urlencode, urlsplit

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, RedirectResponse
from pydantic import BaseModel, Field

from latex_backend.auth.accounts import Accounts
from latex_backend.compilation.compiler import Compiler
from latex_backend.http.api import create_api
from latex_backend.storage.state import DEFAULTS
from latex_backend.storage.store import Problem, Store
from latex_backend.storage.mongo_store import MongoStore
from pymongo.errors import PyMongoError
from latex_backend.collaboration.sharing import Sharing
from latex_backend.auth.context import request_user, authorized_project
from latex_backend.auth.google_config import settings as google_settings, GoogleSetupError

COOKIE = 'latex_account'
FLOW_COOKIE = 'latex_google_flow'


class Credentials(BaseModel):
    name: str = Field(default='', max_length=40)
    email: str = Field(default='', max_length=254)
    password: str = Field(default='', max_length=1024)
    remember: bool = False


class RoleChoice(BaseModel):
    name: str = Field(min_length=1, max_length=40)
    role: str


class WebAccounts:
    def __init__(self, shell, home, frontend, latexmk, token, database, client=None):
        self.shell, self.home, self.frontend = shell, home, frontend
        self.latexmk, self.token = latexmk, token
        self.database, self.mongo_client = database, client
        self.accounts = Accounts(home, database)
        self.sharing = Sharing(self.accounts)
        self.tenants = {}
        self.flows = {}
        self.lock = asyncio.Lock()
        self.state = shell.state
        self.state.accounts = self.accounts
        self.state.web_accounts = self
        self.auth = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
        self.stack = AsyncExitStack()
        original_lifespan = shell.router.lifespan_context

        @asynccontextmanager
        async def lifespan(app):
            async with original_lifespan(app), self.stack:
                try:
                    yield
                finally:
                    await self.presence.live.close()
                    # Child app cleanup runs before closing the identity database.
                    await self.stack.aclose()
                    self.accounts.close()
                    if self.mongo_client is not None:
                        self.mongo_client.close()

        shell.router.lifespan_context = lifespan
        self.routes()
        from latex_backend.http.sharing_api import register_sharing
        register_sharing(self)
        from latex_backend.collaboration.presence import Presence
        self.presence = Presence(self)
        self.presence.routes()

    async def project_workspace(self, project, user):
        if not isinstance(project, str) or not re.fullmatch(r'[0-9a-f]{32}', project):
            raise Problem('Không tìm thấy dự án.', 404)
        own_app, own_store = await self.workspace(user)
        if (own_store.projects / project).is_dir():
            await asyncio.to_thread(own_store.root, project)
            await asyncio.to_thread(self.sharing.register, project, user['id'])
            return own_app, own_store, 'owner'
        owner_id, permission = await asyncio.gather(
            asyncio.to_thread(self.sharing.owner, project),
            asyncio.to_thread(self.sharing.access, project, user['id']))
        if not owner_id or not permission:
            raise Problem('Không tìm thấy dự án hoặc bạn không có quyền truy cập.', 404)
        owner = await asyncio.to_thread(self.accounts.user, owner_id)
        app, store = await self.workspace(owner)
        await asyncio.to_thread(store.root, project)
        return app, store, permission

    async def project_info(self, project, user):
        _, store, permission = await self.project_workspace(project, user)
        owner = self.accounts.user(self.sharing.owner(project))
        return {**store.info(project), 'permission': permission, 'shared': permission != 'owner', 'ownerName': owner['name']}

    def principal(self, request):
        user = self.accounts.resolve(request.cookies.get(COOKIE, ''))
        if user is None:
            raise Problem('Vui lòng đăng nhập.', 401)
        return user

    def signed_in(self, request, user, remember=False, redirect=False):
        # Rotate the current browser session after successful authentication.
        self.accounts.revoke(request.cookies.get(COOKIE, ''))
        token, lifetime = self.accounts.issue(user['id'], remember)
        response = RedirectResponse('/', 303) if redirect else JSONResponse(self.accounts.public(user))
        response.set_cookie(COOKIE, token, httponly=True, secure=request.url.scheme == 'https',
                            samesite='lax', path='/api', max_age=lifetime if remember else None)
        return response

    async def workspace(self, user):
        async with self.lock:
            if user['id'] not in self.tenants:
                store = MongoStore(self.home / 'users' / user['id'], self.database, user['id'])
                compiler = Compiler(store, self.latexmk)
                app = create_api(store, compiler, self.token, web=True)
                app.state.review_changed = self.presence.review_changed
                app.state.live_documents = self.presence.live
                app.state.compiler = compiler
                await self.stack.enter_async_context(app.router.lifespan_context(app))
                self.tenants[user['id']] = (app, store)
            app, store = self.tenants[user['id']]
            store.state.session = self.accounts.public(user)
            return app, store

    def google_config(self):
        try:
            value = google_settings()
        except GoogleSetupError as error:
            raise Problem(str(error), 503) from None
        return value.client_id, value.client_secret

    def callback_url(self, request):
        # Operator controls the public base URL; no arbitrary return URL is accepted.
        try:
            base = google_settings().public_url or str(request.base_url).rstrip('/')
        except GoogleSetupError as error:
            raise Problem(str(error), 503) from None
        parsed = urlsplit(base)
        if parsed.scheme != 'https' and not (parsed.scheme == 'http' and parsed.hostname in {'localhost', '127.0.0.1'}):
            raise Problem('LATEX_PUBLIC_URL cần HTTPS hoặc localhost.', 503)
        return base + '/api/auth/google/callback'

    async def exchange_google(self, code, flow):
        import httpx
        from google.auth.transport.requests import Request as GoogleRequest
        from google.oauth2.id_token import verify_oauth2_token
        client_id, client_secret = self.google_config()
        async with httpx.AsyncClient(timeout=20) as client:
            response = await client.post('https://oauth2.googleapis.com/token', data={
                'code': code, 'client_id': client_id, 'client_secret': client_secret,
                'redirect_uri': flow['redirect'], 'grant_type': 'authorization_code',
                'code_verifier': flow['verifier'],
            })
            response.raise_for_status()
            encoded = response.json()['id_token']

        def verify():
            # Bound network requests made by Google's certificate verifier too.
            import requests
            class TimedRequest(GoogleRequest):
                def __call__(self, *args, **kwargs):
                    kwargs['timeout'] = 20
                    return super().__call__(*args, **kwargs)
            with requests.Session() as session:
                return verify_oauth2_token(encoded, TimedRequest(session=session), client_id)

        claims = await asyncio.to_thread(verify)
        if not secrets.compare_digest(str(claims.get('nonce', '')), flow['nonce']):
            raise Problem('Google nonce không hợp lệ.', 401)
        return claims

    def routes(self):
        auth = self.auth

        @auth.exception_handler(Problem)
        async def problem(request, error):
            return JSONResponse({'message': error.message}, error.status)

        @auth.exception_handler(PyMongoError)
        async def database_problem(request, error):
            return JSONResponse({'message': 'MongoDB tạm thời không khả dụng. Vui lòng thử lại; không có fallback SQLite.'}, 503)

        @auth.get('/api/auth/config')
        def config():
            return {'googleEnabled': all(self.google_config()), 'roles': ['student', 'tutor']}

        @auth.get('/api/session')
        async def session(request: Request):
            user = self.accounts.resolve(request.cookies.get(COOKIE, ''))
            if user:
                _, store = await self.workspace(user)
                return {'session': self.accounts.public(user), 'lastEmail': user['email'],
                        'settings': store.state.settings(), 'activity': store.state.activity()}
            return {'session': None, 'lastEmail': '', 'settings': {**DEFAULTS, 'projectsDirectory': '', 'latexmkPath': ''},
                    'activity': {'edits': {}, 'compiles': 0}}

        @auth.post('/api/actions/register')
        def register(data: Credentials, request: Request):
            self.accounts.throttle(('register', request.client.host if request.client else 'local'))
            user = self.accounts.signup(data.name, data.email, data.password)
            return self.signed_in(request, user, data.remember)

        @auth.post('/api/actions/login')
        def login(data: Credentials, request: Request):
            self.accounts.throttle(('login', request.client.host if request.client else 'local'))
            user = self.accounts.login(data.email, data.password)
            return self.signed_in(request, user, data.remember)

        @auth.post('/api/actions/offline')
        def offline():
            raise Problem('Đăng nhập để sử dụng workspace riêng trên web.', 403)

        @auth.post('/api/actions/logout')
        def logout(request: Request):
            self.accounts.revoke(request.cookies.get(COOKIE, ''))
            response = JSONResponse({'loggedOut': True})
            response.delete_cookie(COOKIE, path='/api')
            return response

        @auth.post('/api/auth/onboarding')
        def onboarding(data: RoleChoice, request: Request):
            user = self.principal(request)
            return self.accounts.public(self.accounts.choose_role(user['id'], data.role, data.name))

        @auth.post('/api/actions/profile')
        def profile(data: dict, request: Request):
            user = self.principal(request)
            updated = self.accounts.profile(user['id'], data)
            if data.get('password') is not None:
                return self.signed_in(request, updated)
            return self.accounts.public(updated)

        @auth.get('/api/auth/google/start')
        def google_start(request: Request):
            client_id, client_secret = self.google_config()
            if not client_id or not client_secret:
                return RedirectResponse('/#auth_error=google_not_configured', 303)
            now = time.time()
            self.flows = {key: value for key, value in self.flows.items() if value['expires'] > now}
            if len(self.flows) >= 1000:
                raise Problem('Vui lòng thử lại sau.', 429)
            state, verifier, nonce = (secrets.token_urlsafe(32) for _ in range(3))
            redirect = self.callback_url(request)
            self.flows[state] = {'verifier': verifier, 'nonce': nonce, 'redirect': redirect, 'expires': now + 600}
            challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b'=').decode()
            response = RedirectResponse('https://accounts.google.com/o/oauth2/v2/auth?' + urlencode({
                'client_id': client_id, 'redirect_uri': redirect, 'response_type': 'code',
                'scope': 'openid email profile', 'state': state, 'nonce': nonce,
                'code_challenge': challenge, 'code_challenge_method': 'S256', 'prompt': 'select_account',
            }), 303)
            response.set_cookie(FLOW_COOKIE, state, max_age=600, httponly=True,
                                secure=request.url.scheme == 'https', samesite='lax', path='/api/auth/google')
            return response

        @auth.get('/api/auth/google/callback')
        async def google_callback(request: Request):
            state = request.query_params.get('state', '')
            browser_state = request.cookies.get(FLOW_COOKIE, '')
            flow = None
            if state and secrets.compare_digest(state, browser_state):
                flow = self.flows.pop(state, None)
            error = 'google_failed'
            if flow and flow['expires'] > time.time():
                if request.query_params.get('error'):
                    error = 'google_cancelled'
                elif request.query_params.get('code'):
                    try:
                        claims = await self.exchange_google(request.query_params['code'], flow)
                        user = self.accounts.google(claims)
                        response = self.signed_in(request, user, True, redirect=True)
                        response.delete_cookie(FLOW_COOKIE, path='/api/auth/google')
                        return response
                    except Problem as problem:
                        error = 'account_exists' if problem.status == 409 else 'google_failed'
                    except Exception:
                        # Never leak tokens, authorization codes, or client secrets to the UI.
                        error = 'google_failed'
            response = RedirectResponse('/#auth_error=' + error, 303)
            response.delete_cookie(FLOW_COOKIE, path='/api/auth/google')
            return response

    async def __call__(self, scope, receive, send):
        started = False
        async def guarded_send(message):
            nonlocal started
            if message['type'] == 'http.response.start':
                started = True
            await send(message)
        try:
            return await self._dispatch(scope, receive, guarded_send)
        except PyMongoError:
            if started:
                raise
            return await JSONResponse({'message': 'MongoDB tạm thời không khả dụng. Vui lòng thử lại; không có fallback SQLite.'},
                                      503)(scope, receive, send)

    async def _dispatch(self, scope, receive, send):
        if scope['type'] == 'websocket':
            match = re.fullmatch(r'/api/projects/([0-9a-f]{32})/presence', scope['path'])
            if match:
                return await self.presence.serve(scope, receive, send, match[1])
        if scope['type'] != 'http' or not scope['path'].startswith('/api/'):
            return await self.shell(scope, receive, send)
        request = Request(scope, receive)
        path = request.url.path
        callback = path == '/api/auth/google/callback' and request.method == 'GET'
        origin = request.headers.get('origin')
        invalid = request.url.hostname not in {'localhost', '127.0.0.1'}
        invalid |= bool(origin and origin != str(request.base_url).rstrip('/'))
        invalid |= request.headers.get('sec-fetch-site') == 'cross-site' and not callback
        if invalid:
            return await JSONResponse({'message': 'Invalid request origin or host'}, 403)(scope, receive, send)
        # Bound auth payloads before JSON parsing. File upload routes retain their own limits.
        public_auth = path == '/api/session' or path.startswith(('/api/auth/', '/api/shares/')) or '/sharing' in path or (path == '/api/projects' and request.method == 'GET') or path in {
            '/api/actions/register', '/api/actions/login', '/api/actions/logout',
            '/api/actions/offline', '/api/actions/profile'}
        if public_auth:
            if request.method in {'POST', 'PUT', 'PATCH'}:
                body = bytearray()
                async for chunk in request.stream():
                    body.extend(chunk)
                    if len(body) > 16384:
                        return await JSONResponse({'message': 'Request too large'}, 413)(scope, receive, send)
                async def replay():
                    return {'type': 'http.request', 'body': bytes(body), 'more_body': False}
                receive = replay

            async def secure_send(message):
                if message['type'] == 'http.response.start':
                    message.setdefault('headers', []).extend([
                        (b'cache-control', b'no-store'), (b'referrer-policy', b'no-referrer'),
                        (b'x-content-type-options', b'nosniff')])
                await send(message)
            return await self.auth(scope, receive, secure_send)
        if path in {'/api/web/session', '/api/health', '/api/templates'}:
            return await self.shell(scope, receive, send)
        user = await asyncio.to_thread(self.accounts.resolve, request.cookies.get(COOKIE, ''))
        if user is None or user['role'] is None:
            # Boot requests the list before it decides which auth screen to render.
            if path == '/api/projects' and request.method == 'GET':
                return await JSONResponse([], headers={'Cache-Control': 'no-store'})(scope, receive, send)
            return await JSONResponse({'message': 'Vui lòng đăng nhập và chọn vai trò.'},
                                      401 if user is None else 403)(scope, receive, send)
        try:
            match = re.match(r'^/api/projects/([0-9a-f]{32})(?:/|$)', path)
            project = match[1] if match else None
            action_name, data = '', {}
            if path.startswith('/api/actions/'):
                action_name = path.rsplit('/', 1)[-1]
                if action_name in {'network-connect', 'network-reconnect', 'network-edit', 'network-cursor'}:
                    raise Problem('Web sử dụng chia sẻ dự án, không kết nối phòng TCP/TLS.', 403)
                body = bytearray()
                async for chunk in request.stream():
                    body.extend(chunk)
                    if len(body) > 12 * 1024 * 1024:
                        raise Problem('Request too large', 413)
                try:
                    data = json.loads(body or b'{}')
                    if not isinstance(data, dict):
                        raise ValueError()
                except (ValueError, UnicodeError):
                    raise Problem('JSON không hợp lệ.')
                async def replay_action():
                    return {'type': 'http.request', 'body': bytes(body), 'more_body': False}
                receive = replay_action
                project = data.get('project') or None
            if project:
                app, store, permission = await self.project_workspace(project, user)
                if permission != 'owner' and action_name:
                    readable = {'symbols', 'synctex', 'editing'}
                    editable = {'checkpoint', 'file-rename', 'file-delete', 'history-label', 'history-restore', 'download', 'clean'}
                    if action_name not in readable | (editable if permission == 'editor' else set()):
                        raise Problem('Bạn không có quyền thực hiện thao tác này.', 403)
                    if permission == 'viewer' and action_name == 'editing':
                        return await JSONResponse({'dirty': project in app.state.compiler.dirty}, headers={'Cache-Control': 'no-store'})(scope, receive, send)
                if permission == 'viewer' and not action_name and request.method not in {'GET', 'HEAD'}:
                    if path != f'/api/projects/{project}/compile':
                        raise Problem('Bạn chỉ có quyền xem tài liệu này.', 403)
            else:
                app, store = await self.workspace(user)
                permission = 'owner'
        except Problem as error:
            return await JSONResponse({'message': error.message}, error.status)(scope, receive, send)
        # Internal bearer is attached only after identity validation, never sent to the browser.
        child_scope = dict(scope)
        child_scope['headers'] = [(k, v) for k, v in scope['headers'] if k.lower() != b'authorization'] + [
            (b'authorization', ('Bearer ' + self.token).encode())]
        async def permission_send(message):
            if message['type'] == 'http.response.start':
                message.setdefault('headers', []).append((b'x-latex-permission', permission.encode()))
            await send(message)
        context = request_user.set(self.accounts.public(user))
        project_context = authorized_project.set((store.owner, project) if project else None)
        try:
            return await app(child_scope, receive, permission_send)
        finally:
            authorized_project.reset(project_context)
            request_user.reset(context)

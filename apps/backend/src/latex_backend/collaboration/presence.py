"""Authenticated presence and reliable versioned source updates (one worker).

Presence never accepts an identity, permission or avatar from a browser.
"""
import asyncio
import hashlib
import json
import re
import secrets
import time
import anyio
from urllib.parse import urlsplit

from fastapi import Request, WebSocket
from fastapi.responses import Response
from starlette.websockets import WebSocketDisconnect
from pymongo.errors import PyMongoError
from latex_backend.storage.store import Problem

COLORS = ['#2878c7', '#9c44ba', '#ce6630', '#208568', '#bd4280', '#78701e']


class Presence:
    def __init__(self, gateway):
        self.gateway = gateway
        self.rooms = {}
        from latex_backend.collaboration.live_documents import LiveDocuments
        self.live = LiveDocuments(self)

    def publish(self, project):
        room = self.rooms.get(project, {})
        event = {'type': 'presence', 'project': project,
                 'members': [dict(peer['member']) for peer in room.values()]}
        for peer in room.values():
            queue = peer['queue']
            if queue.full():
                continue  # Never drop reliable document updates to send presence.
            queue.put_nowait(event)

    def review_changed(self, project):
        # Send only an invalidation; review text is loaded through the normal
        # ACL-checked API. A missed notification is recovered by panel polling.
        for peer in self.rooms.get(project, {}).values():
            if not peer['queue'].full():
                peer['queue'].put_nowait({'type': 'review-changed', 'project': project})

    async def serve(self, scope, receive, send, project):
        ws = WebSocket(scope, receive, send)
        origin = urlsplit(ws.headers.get('origin', ''))
        # Browser cookies alone must never authorize cross-origin sockets.
        if (origin.scheme not in {'http', 'https'} or origin.netloc != ws.headers.get('host')
                or ws.url.hostname not in {'127.0.0.1', 'localhost'}):
            return await ws.close(1008)
        token = ws.cookies.get('latex_account', '')

        async def authorize():
            user = await asyncio.to_thread(self.gateway.accounts.resolve, token)
            if not user or not user.get('role'):
                raise Problem('Sign in required', 401)
            app, store, permission = await self.gateway.project_workspace(project, user)
            if permission not in {'owner', 'editor'}:
                raise Problem('Editor permission required', 403)
            return user, store, app.state.compiler

        async def validate_bound():
            # Recheck identity and ACL on every edit without rebuilding/routing
            # the owner's workspace (which is already bound to this socket).
            def check():
                current = self.gateway.accounts.resolve(token)
                if not current or not current.get('role'):
                    raise Problem('Sign in required', 401)
                if self.gateway.sharing.access(project, current['id']) not in {'owner', 'editor'}:
                    raise Problem('Editor permission required', 403)
                store.root(project)  # Also reject a deleted project.
                return current
            return await asyncio.to_thread(check)

        identifier, tasks = secrets.token_hex(12), []
        joined = False
        try:
            user, store, compiler = await authorize()
            # Join only AFTER accept: accept yields to other connections. The last
            # peer can leave and delete the old room while this handshake awaits.
            await ws.accept()
            room = self.rooms.setdefault(project, {})
            if len(room) >= 100 or sum(p['member']['userId'] == user['id'] for p in room.values()) >= 8:
                return await ws.close(1008)
            member = {'connectionId': identifier, 'userId': user['id'], 'name': user['name'],
                      'color': COLORS[int(hashlib.sha256(user['id'].encode()).hexdigest()[:8], 16) % len(COLORS)],
                      'file': '', 'anchor': 0, 'head': 0, 'digest': '', 'active': False}
            queue = asyncio.Queue(maxsize=128)
            room[identifier] = {'member': member, 'queue': queue}
            joined = True
            self.publish(project)

            async def outgoing():
                while True:
                    if room[identifier].get('overflow'):
                        raise Problem('Live queue overflow; reconnect', 409)
                    await asyncio.wait_for(ws.send_json(await queue.get()), timeout=10)

            async def watchdog():
                while True:
                    await asyncio.sleep(5)
                    current, _, _ = await authorize()
                    member['name'] = current['name']
                    self.publish(project)

            async def incoming():
                last = 0.0
                validated_path = None
                while True:
                    # Transport ping/pong detects dead sockets. Browser JS timers
                    # are throttled in background tabs; don't eject an idle editor
                    # merely because an application heartbeat was delayed.
                    raw = await ws.receive_text()
                    if len(raw) > 4 * 1024 * 1024:
                        raise Problem('Presence message too large', 413)
                    data = json.loads(raw)
                    if not isinstance(data, dict):
                        raise Problem('Invalid presence')
                    if data.get('type') == 'ping':
                        continue
                    if data.get('type') in {'sync-open', 'sync-push'}:
                        # Revalidate write permission/session on every live edit.
                        current_user = await validate_bound()
                        await self.live.handle(project, identifier, current_user, store, compiler, data)
                        continue
                    if len(raw) > 4096:
                        raise Problem('Presence message too large', 413)
                    now = time.monotonic()
                    if now - last < .04:
                        continue
                    last = now
                    path = data.get('file', '')
                    digest = data.get('digest', '')
                    anchor, head = data.get('anchor'), data.get('head')
                    if (not isinstance(path, str) or len(path) > 1024
                            or not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest)
                            or any(type(n) is not int or not 0 <= n <= 4_194_304 for n in [anchor, head])):
                        raise Problem('Invalid cursor')
                    # Validate project-relative paths; presence cannot probe another project.
                    if path != validated_path:
                        target = store.path(project, path)
                        if not target.is_file():
                            raise Problem('File not found', 404)
                        validated_path = path
                    # Selection ranges stay private to the selecting browser;
                    # share only the caret, including for older clients.
                    member.update(file=path, digest=digest, anchor=head, head=head,
                                  active=data.get('active') is True)
                    self.publish(project)

            tasks = [asyncio.create_task(fn()) for fn in [outgoing, watchdog, incoming]]
            done, _ = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            for task in done:
                task.result()
        except (Problem, PyMongoError, ValueError, WebSocketDisconnect, TimeoutError, RuntimeError):
            try:
                await ws.close(1008)
            except RuntimeError:
                pass
        finally:
            if joined:
                room = self.rooms.get(project, {})
                room.pop(identifier, None)
                self.publish(project)
                if not room:
                    self.rooms.pop(project, None)
                self.live.release(project)
            with anyio.CancelScope(shield=True):
                for task in tasks:
                    task.cancel()
                if tasks:
                    await asyncio.gather(*tasks, return_exceptions=True)
                if joined:
                    await self.live.flush_history(project)

    def routes(self):
        @self.gateway.auth.get('/api/projects/{project}/sharing/avatar/{user_id}')
        async def avatar(project: str, user_id: str, request: Request):
            viewer = self.gateway.principal(request)
            await self.gateway.project_workspace(project, viewer)
            if not any(p['member']['userId'] == user_id for p in self.rooms.get(project, {}).values()):
                raise Problem('User not present', 404)
            user = self.gateway.accounts.user(user_id)
            _, store = await self.gateway.workspace(user)
            key = hashlib.sha256(user['email'].encode()).hexdigest()
            path = store.state.folder / (key + '.png')
            if not path.is_file() or path.is_symlink():
                raise Problem('No avatar', 404)
            return Response(path.read_bytes(), media_type='image/png', headers={'Cache-Control': 'private, no-store'})

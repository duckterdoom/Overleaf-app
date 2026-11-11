"""Single-worker versioned authority for CodeMirror collaborative changes.

Stale pushes receive accepted changes, and CodeMirror rebases unconfirmed local
updates before retrying. Persist before acknowledging; never replace stale text.
"""
import secrets
import json
import asyncio
import logging
import anyio
from pymongo.errors import PyMongoError
from latex_backend.auth.context import request_user, authorized_project
from latex_backend.storage.store import Problem


def apply_changes(text, changes):
    if not isinstance(changes, list) or len(changes) > 10000:
        raise Problem('Invalid change set')
    source = text.encode('utf-16-le')
    offset, parts = 0, []
    for part in changes:
        if type(part) is int:
            length, insertion = part, None
        elif isinstance(part, list) and part and type(part[0]) is int and all(isinstance(s, str) and '\n' not in s and '\r' not in s for s in part[1:]):
            length, insertion = part[0], '\n'.join(part[1:])
        else:
            raise Problem('Invalid change set')
        if length < 0 or offset + length * 2 > len(source):
            raise Problem('Invalid change length')
        parts.append(source[offset:offset + length * 2] if insertion is None else insertion.encode('utf-16-le'))
        offset += length * 2
    if offset != len(source):
        raise Problem('Change length does not match document', 409)
    try:
        result = b''.join(parts).decode('utf-16-le')
    except UnicodeError:
        raise Problem('Invalid Unicode change') from None
    if len(result.encode('utf-8')) > 2 * 1024 * 1024:
        raise Problem('Document exceeds 2 MiB', 413)
    return result


class LiveDocuments:
    def __init__(self, presence):
        self.presence = presence
        self.documents = {}
        self.history_pending = {}
        self.history_task = None
        self.history_lock = asyncio.Lock()
        self.edit_lock = asyncio.Lock()
        self.closing = False

    def queue_history(self, project, path, store, text, user):
        # Keep the latest snapshot per author, so alternating editors retain
        # attribution while a burst of keystrokes doesn't create 100 revisions.
        key = (project, path, user['id'])
        self.history_pending.pop(key, None)
        self.history_pending[key] = (store, text, user['name'])
        if self.history_task is None or self.history_task.done():
            self.history_task = asyncio.create_task(self._history_worker())

    async def _history_worker(self):
        while self.history_pending and not self.closing:
            await asyncio.sleep(2)
            await self.flush_history()

    async def flush_history(self, project=None):
        async with self.history_lock:
            jobs = [(key, value) for key, value in self.history_pending.items() if project is None or key[0] == project]
            for key, _ in jobs:
                self.history_pending.pop(key, None)
            def capture():
                for (identifier, path, _), (store, text, author) in jobs:
                    context = authorized_project.set((store.owner, identifier))
                    try:
                        store.history.capture(identifier, path, text, 'Live edit', author)
                    except (Problem, OSError, PyMongoError):
                        # Source + metadata have already been persisted. Never
                        # leak Mongo connection strings through exception logs.
                        logging.getLogger(__name__).warning('Could not record a live history checkpoint; saved source is retained.')
                    finally:
                        authorized_project.reset(context)
            if jobs:
                await asyncio.to_thread(capture)

    async def close(self):
        self.closing = True
        if self.history_task is not None:
            await self.history_task
        await self.flush_history()

    @staticmethod
    def deliver(peer, event):
        if peer['queue'].full():
            raise Problem('Live connection queue full; reconnect', 409)
        peer['queue'].put_nowait(event)

    def snapshot(self, peer, path, doc):
        self.deliver(peer, {'type': 'sync-snapshot', 'file': path, 'epoch': doc['epoch'],
                            'version': doc['version'], 'text': doc['text']})

    def send_updates(self, peer, path, doc, version):
        start = doc['version'] - len(doc['updates'])
        if version < start or version > doc['version']:
            return self.snapshot(peer, path, doc)
        self.deliver(peer, {'type': 'sync-updates', 'file': path, 'epoch': doc['epoch'],
                            'from': version, 'version': doc['version'],
                            'updates': doc['updates'][version - start:]})

    async def handle(self, project, identifier, user, store, compiler, data):
        async with self.edit_lock:
            # The gateway just validated this project; avoid redundant Atlas root
            # lookups for each nested path operation within this one edit request.
            context = authorized_project.set((store.owner, project))
            try:
                return await self._handle(project, identifier, user, store, compiler, data)
            finally:
                authorized_project.reset(context)


    async def _handle(self, project, identifier, user, store, compiler, data):
        peer = self.presence.rooms[project][identifier]
        path = data.get('file')
        if not isinstance(path, str) or not path or len(path) > 1024:
            raise Problem('Invalid live file')
        key = (project, path)
        current = (await asyncio.to_thread(store.read, project, path)).replace('\r\n', '\n').replace('\r', '\n')
        doc = self.documents.get(key)
        if doc is None or doc['text'] != current:
            doc = {'epoch': secrets.token_hex(16), 'text': current, 'version': 0, 'updates': [], 'bytes': 0}
            self.documents[key] = doc
        version = data.get('version')
        if type(version) is not int or version < 0:
            raise Problem('Invalid live version')
        if data['type'] == 'sync-open':
            client_id = data.get('clientID', '')
            if not isinstance(client_id, str) or not client_id.startswith(user['id'] + ':') or len(client_id) > 120:
                raise Problem('Invalid client identity')
            peer['live'] = (path, client_id)
            self.release(project)
            if data.get('epoch') != doc['epoch']:
                self.snapshot(peer, path, doc)
            else:
                self.send_updates(peer, path, doc, version)
            return
        binding = peer.get('live')
        if not binding or binding[0] != path:
            raise Problem('Open live file first')
        if data.get('epoch') != doc['epoch']:
            return self.snapshot(peer, path, doc)
        if version != doc['version']:
            return self.send_updates(peer, path, doc, version)
        updates = data.get('updates')
        if not isinstance(updates, list) or not 1 <= len(updates) <= 100:
            raise Problem('Invalid live updates')
        content = current
        accepted = []
        for changes in updates:
            content = apply_changes(content, changes)
            accepted.append({'changes': changes, 'clientID': binding[1]})
        def persist():
            context = request_user.set(self.presence.gateway.accounts.public(user))
            try:
                with store.lock:
                    disk = store.read(project, path).replace('\r\n', '\n').replace('\r', '\n')
                    if disk != current:
                        raise Problem('Source changed outside the live authority; reconnect.', 409)
                    if hasattr(store, 'save_live'):
                        store.save_live(project, path, content)
                    else:
                        store.save(project, path, content)
            finally:
                request_user.reset(context)
        saving = asyncio.create_task(asyncio.to_thread(persist))
        cancelled = False
        try:
            await asyncio.shield(saving)
        except asyncio.CancelledError:
            # A disconnect cannot leave an already-written source without its
            # authority version/broadcast. Complete this commit before cleanup.
            with anyio.CancelScope(shield=True):
                await saving
            cancelled = True
        compiler.dirty.discard(project)
        if hasattr(store, 'save_live'):
            self.queue_history(project, path, store, content, user)
        doc['text'] = content
        doc['version'] += len(accepted)
        doc['updates'].extend(accepted)
        doc['bytes'] += sum(len(json.dumps(update).encode()) for update in accepted)
        while len(doc['updates']) > 4096 or doc['bytes'] > 8 * 1024 * 1024:
            doc['bytes'] -= len(json.dumps(doc['updates'].pop(0)).encode())
        event = {'type': 'sync-updates', 'file': path, 'epoch': doc['epoch'],
                 'from': version, 'version': doc['version'], 'updates': accepted}
        for other in list(self.presence.rooms.get(project, {}).values()):
            if other.get('live', (None,))[0] == path:
                if other['queue'].full():
                    other['overflow'] = True
                else:
                    self.deliver(other, event)
        if cancelled:
            raise asyncio.CancelledError

    def release(self, project):
        active = {p.get('live', (None,))[0] for p in self.presence.rooms.get(project, {}).values()}
        for key in list(self.documents):
            if key[0] == project and key[1] not in active:
                del self.documents[key]

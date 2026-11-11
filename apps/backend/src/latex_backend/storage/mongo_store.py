"""MongoDB metadata with filesystem document/artifact storage. No JSON fallback."""
import copy
import json
import re
import shutil
import threading
import uuid
from datetime import date, datetime, timezone

from latex_backend.storage.state import State
from latex_backend.storage.store import Store, Problem, atomic_write, TEXT_SUFFIXES
from latex_backend.storage.history import History
from latex_backend.auth.context import authorized_project
from latex_backend.storage.shared_files import SharedFiles, shared_write


class MongoState(State):
    def __init__(self, home, database, owner):
        self.folder = home / '.app'
        self.folder.mkdir(parents=True, exist_ok=True)
        self.file = self.folder / 'state.json'  # Legacy reference only, never read/written here.
        self.lock = threading.RLock()
        self.collection, self.owner = database.user_state, owner
        self._local_session = None
        self.collection.update_one({'_id': owner}, {'$setOnInsert': {'value': {
            'accounts': {}, 'settings': {}, 'activity': {}, 'compiles': {}}}}, upsert=True)
        self.reload()

    def reload(self):
        row = self.collection.find_one({'_id': self.owner})
        self.data = copy.deepcopy(row['value'])
        return self.data

    def write(self):
        keys = getattr(self, 'preference_fields', self.data['settings'].keys())
        values = {f'value.settings.{key}': self.data['settings'][key] for key in keys if key in self.data['settings']}
        if values:
            self.collection.update_one({'_id': self.owner}, {'$set': values})

    def settings(self):
        with self.lock:
            self.reload()
            return super().settings()

    def preferences(self, values):
        with self.lock:
            self.reload()
            self.preference_fields = set(values)
            try:
                return super().preferences(values)
            finally:
                del self.preference_fields

    def activity(self, kind=None):
        today = date.today()
        key = f'value.activity.{today.isoformat()}' if kind == 'edit' else f'value.compiles.{today.year}'
        if kind:
            self.collection.update_one({'_id': self.owner}, {'$inc': {key: 1}})
        with self.lock:
            self.reload()
            return {'edits': self.data['activity'], 'compiles': self.data['compiles'].get(str(today.year), 0)}


class MongoRecords:
    def __init__(self, store):
        self.store = store

    def read(self, project, kind, default):
        self.store.root(project)
        row = self.store.db.project_records.find_one({'_id': f'{self.store.owner}:{project}:{kind}'})
        return copy.deepcopy(row['value'] if row else default)

    def write(self, project, kind, value):
        self.store.root(project)
        self.store.db.project_records.replace_one({'_id': f'{self.store.owner}:{project}:{kind}'}, {
            '_id': f'{self.store.owner}:{project}:{kind}', 'owner_id': self.store.owner,
            'project': project, 'kind': kind, 'value': value}, upsert=True)


class MongoHistory(History):
    def metadata(self, project, revision):
        self.store.root(project)
        if not re.fullmatch(r'[a-f0-9-]{32,36}', revision):
            raise Problem('Mã revision không hợp lệ.')
        row = self.store.db.history.find_one({'_id': f'{self.store.owner}:{project}:{revision}'})
        if not row:
            raise Problem('Không tìm thấy revision.', 404)
        if row.get('blob'):
            folder = self.directory(project) / revision
            folder.mkdir(parents=True, exist_ok=True)
            target = folder / 'content.txt'
            if target.is_symlink() or folder.is_symlink():
                raise Problem('History không hỗ trợ symbolic link.', 403)
            if not target.is_file():
                from latex_backend.storage.store import atomic_write
                atomic_write(target, self.store.shared_files.download(row['blob']))
        return copy.deepcopy(row['value'])

    def list(self, project):
        self.store.root(project)
        rows = self.store.db.history.find({'owner_id': self.store.owner, 'project': project}).sort('time', -1)
        return [copy.deepcopy(row['value']) for row in rows]

    @shared_write
    def capture(self, project, name, text, reason='Edited', author='Local'):
        # Parent writes the content payload and an export-side metadata mirror.
        # Its list/dedup operations dispatch to MongoDB methods above.
        value = super().capture(project, name, text, reason, author)
        if value:
            self.persist(project, value)
        return value

    def persist(self, project, value):
        key = f"{self.store.owner}:{project}:{value['id']}"
        path = self.store.root(project) / '.overleaf-history' / value['id'] / 'content.txt'
        blob = {'sha256': value['sha256'], 'size': value['size']}
        self.store.shared_files.upload(path, blob)
        self.store.db.history.replace_one({'_id': key}, {'_id': key, 'owner_id': self.store.owner,
            'project': project, 'time': value['time'], 'value': value, 'blob': blob}, upsert=True)

    def label(self, project, revision, value):
        if len(value) > 120:
            raise Problem('Nhãn vượt 120 ký tự.')
        metadata = self.read(project, revision)
        metadata.pop('content')
        metadata['label'] = value.strip()
        self.persist(project, metadata)
        return metadata


class MongoStore(Store):
    @shared_write
    def save_live(self, project, name, content):
        """Persist acknowledged source without reloading history/settings per key.

        History snapshots are coalesced separately by LiveDocuments. File fsync,
        project metadata and the activity increment still precede the live ack.
        """
        path = self.path(project, name)
        data = content.encode('utf-8')
        if path.suffix not in TEXT_SUFFIXES or len(data) > 2 * 1024 * 1024:
            raise Problem('Unsupported live document or exceeds 2 MiB.', 413)
        with self.lock:
            if not path.is_file():
                raise Problem('File not found.', 404)
            if path.read_bytes() == data:
                return
            atomic_write(path, data)
            result = self.db.projects.update_one({'_id': project, 'owner_id': self.owner, 'deleted': False},
                {'$set': {'metadata.updated': datetime.now(timezone.utc).isoformat()}})
            if not result.matched_count:
                raise Problem('Project not found.', 404)
            today = date.today().isoformat()
            self.db.user_state.update_one({'_id': self.owner}, {'$inc': {f'value.activity.{today}': 1}})

    def __init__(self, home, database, owner, templates=None):
        self.db, self.owner = database, owner
        home = home.expanduser().resolve()
        super().__init__(home, templates, managed=True, state=MongoState(home, database, owner), locations={})
        self.history, self.records = MongoHistory(self), MongoRecords(self)
        self.shared_files = SharedFiles(self)

    def root(self, project):
        if authorized_project.get() != (self.owner, project) and not self.db.projects.find_one({'_id': project, 'owner_id': self.owner, 'deleted': False}):
            raise Problem('Không tìm thấy dự án.', 404)
        self.shared_files.sync(project)
        return super().root(project)

    @shared_write
    def register(self, project, root):
        root = root.resolve()
        if root != (self.projects / project).resolve() or root.is_symlink():
            raise Problem('Dự án phải nằm trong vùng lưu server.', 403)
        # Only creation/import reads its newly constructed staging manifest.
        info = json.loads((root / '.project.json').read_text(encoding='utf-8'))
        existing = self.db.projects.find_one({'_id': project})
        if existing and existing['owner_id'] != self.owner:
            raise Problem('Mã dự án đã thuộc tài khoản khác.', 409)
        self.db.projects.update_one({'_id': project}, {'$setOnInsert': {
            'owner_id': self.owner, 'metadata': self.clean(info), 'deleted': False}}, upsert=True)

    @staticmethod
    def clean(info):
        return {key: value for key, value in info.items() if key not in {'folder', 'storage', 'permission', 'shared', 'ownerName'}}

    def info(self, project):
        self.root(project)
        row = self.db.projects.find_one({'_id': project, 'owner_id': self.owner, 'deleted': False})
        return {**row['metadata'], 'folder': '', 'storage': 'server'}

    @shared_write
    def write_info(self, project, info):
        self.root(project)
        self.db.projects.update_one({'_id': project, 'owner_id': self.owner, 'deleted': False}, {'$set': {'metadata': self.clean(info)}})

    def list(self):
        result = []
        for row in self.db.projects.find({'owner_id': self.owner, 'deleted': False}):
            try:
                if not (self.projects / row['_id']).is_dir() and not self.db.project_files.find_one({'_id': row['_id'], 'owner_id': self.owner}, {'_id': 1}):
                    continue
                result.append({**row['metadata'], 'folder': '', 'storage': 'server'})
            except (OSError, ValueError, Problem):
                continue
        return sorted(result, key=lambda item: item['updated'], reverse=True)

    @shared_write
    def save(self, project, name, content):
        return super().save(project, name, content)

    @shared_write
    def add(self, project, name, directory):
        return super().add(project, name, directory)

    @shared_write
    def rename_file(self, project, name, target):
        return super().rename_file(project, name, target)

    @shared_write
    def delete_file(self, project, name):
        return super().delete_file(project, name)

    def refresh_shared(self, project):
        changes = self.shared_files.sync(project, force=True)
        return {'synced_files': list(set(changes) | self.shared_files.changed.pop(project, set()))}

    def delete_project(self, project):
        root = self.root(project)
        trash = self.home / '.trash'
        trash.mkdir(exist_ok=True)
        self.db.projects.update_one({'_id': project, 'owner_id': self.owner}, {'$set': {'deleted': True}})
        try:
            shutil.move(str(root), str(trash / f'{project}-{uuid.uuid4().hex}'))
        except Exception:
            self.db.projects.update_one({'_id': project, 'owner_id': self.owner}, {'$set': {'deleted': False}})
            raise

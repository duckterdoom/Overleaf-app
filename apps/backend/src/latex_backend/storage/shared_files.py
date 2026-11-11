"""Versioned project files in MongoDB; local folders are verified working copies.

Immutable 512 KiB chunks avoid MongoDB's document limit. A project manifest is
published with compare-and-swap, so stale backends cannot overwrite new files.
"""
from contextlib import contextmanager
from functools import wraps
import hashlib
import json
from pathlib import PurePosixPath, Path
import re
import time
import uuid

from bson import Binary
from pymongo.errors import DuplicateKeyError, BulkWriteError
from latex_backend.storage.store import Problem, atomic_write

CHUNK = 512 * 1024
MAX_FILE = 100 * 1024 * 1024
MAX_TOTAL = 500 * 1024 * 1024
SKIP = {'build', '.git', '.file-trash', '.overleaf-pdf-word-cache', '.overleaf-cloud-conflicts',
        '.env', '.venv', 'node_modules', 'google.local.json', 'mongodb.local.json'}
MARKER = '.overleaf-cloud.json'


class CloudConflict(Problem):
    def __init__(self):
        super().__init__('Dự án đã đổi trên máy khác. Bản sửa local được giữ trong .overleaf-cloud-conflicts; tải lại dự án để đối chiếu trước khi lưu. Không ghi đè bản trên database.', 409)


def shared_write(method):
    @wraps(method)
    def wrapped(self, project, *args, **kwargs):
        store = getattr(self, 'store', self)
        shared = getattr(store, 'shared_files', None)
        if shared is None:
            return method(self, project, *args, **kwargs)
        with shared.edit(project):
            return method(self, project, *args, **kwargs)
    return wrapped


class SharedFiles:
    def __init__(self, store):
        self.store, self.db = store, store.db
        self.base, self.checked, self.hashes = {}, {}, {}
        self.changed = {}
        self.depth = 0

    def root(self, project):
        # Caller has already checked project identity and owner/ACL.
        if not re.fullmatch(r'[a-f0-9]{32}', project):
            raise Problem('Mã dự án không hợp lệ.', 404)
        root = self.store.projects / project
        if root.is_symlink() or self.store.projects.is_symlink():
            raise Problem('Shared storage không hỗ trợ symbolic link.', 403)
        return root

    @staticmethod
    def allowed(name):
        if not isinstance(name, str) or not name or name == '.' or '\x00' in name:
            return False
        path = PurePosixPath(name)
        return (name != MARKER and name != '.project.json' and not path.is_absolute()
                and '\\' not in name and ':' not in name and '..' not in path.parts
                and not any(part in SKIP or part.startswith(('.write-', '.upload-', 'pending-', 'client_secret'))
                            for part in path.parts))

    def scan(self, root):
        entries, total = {}, 0
        if not root.is_dir():
            return entries
        # Prune excluded caches instead of recursively scanning a TeX build.
        import os
        for folder, dirs, files in os.walk(root, followlinks=False):
            dirs[:] = [name for name in dirs if name not in SKIP and not name.startswith(('pending-', '.write-', '.upload-'))]
            for name in dirs + files:
                path = Path(folder) / name
                relative = path.relative_to(root).as_posix()
                if not self.allowed(relative):
                    continue
                if path.is_symlink():
                    raise Problem('Không đẩy symbolic link lên shared storage.', 403)
                if len(entries) >= 20000:
                    raise Problem('Shared project vượt 20.000 mục.', 413)
                if path.is_dir():
                    entries[relative] = {'directory': True}
                    continue
                stat = path.stat()
                total += stat.st_size
                if stat.st_size > MAX_FILE or total > MAX_TOTAL:
                    raise Problem('Shared storage giới hạn 100 MiB/tệp, 500 MiB/dự án.', 413)
                key = (str(path), stat.st_mtime_ns, stat.st_size)
                digest = self.hashes.get(key)
                if digest is None:
                    digest = hashlib.sha256(path.read_bytes()).hexdigest()
                    self.hashes[key] = digest
                entries[relative] = {'sha256': digest, 'size': stat.st_size}
        if len(self.hashes) > 50000:
            self.hashes.clear()
        if len({name.casefold() for name in entries}) != len(entries):
            raise Problem('Tên tệp/thư mục trùng khi không phân biệt hoa thường; đổi tên trước khi chia sẻ giữa Windows/macOS/Linux.', 409)
        return dict(sorted(entries.items()))

    def upload(self, path, entry):
        digest = entry['sha256']
        if self.db.project_blobs.find_one({'_id': digest}):
            return
        batch, count, hasher, size = [], 0, hashlib.sha256(), 0
        with path.open('rb') as handle:
            while data := handle.read(CHUNK):
                hasher.update(data)
                size += len(data)
                batch.append({'_id': digest + ':' + str(count), 'data': Binary(data)})
                count += 1
                if len(batch) == 16:
                    self.insert_chunks(batch)
                    batch = []
        self.insert_chunks(batch)
        if hasher.hexdigest() != digest or size != entry['size']:
            raise CloudConflict()
        self.db.project_blobs.update_one({'_id': digest}, {'$setOnInsert': {'size': size, 'chunks': count}}, upsert=True)

    def insert_chunks(self, chunks):
        if not chunks:
            return
        try:
            self.db.project_chunks.insert_many(chunks, ordered=False)
        except BulkWriteError as error:
            if error.details.get('writeConcernErrors') or any(item['code'] != 11000 for item in error.details.get('writeErrors', [])):
                raise

    def download(self, entry):
        header = self.db.project_blobs.find_one({'_id': entry['sha256']})
        if not header or header['size'] != entry['size'] or not 0 <= header['size'] <= MAX_FILE or header['chunks'] != (header['size'] + CHUNK - 1) // CHUNK:
            raise Problem('Shared file thiếu metadata hoặc vượt giới hạn.', 503)
        data = bytearray()
        for number in range(header['chunks']):
            chunk = self.db.project_chunks.find_one({'_id': entry['sha256'] + ':' + str(number)})
            if not chunk or len(chunk['data']) > CHUNK:
                raise Problem('Shared file thiếu chunk; chưa thay bản local.', 503)
            data.extend(chunk['data'])
            if len(data) > entry['size']:
                raise Problem('Shared file sai kích thước.', 503)
        if len(data) != entry['size'] or hashlib.sha256(data).hexdigest() != entry['sha256']:
            raise Problem('Checksum shared file không khớp; chưa thay bản local.', 503)
        return bytes(data)

    def remember(self, project, value):
        self.base[project] = value
        root = self.root(project)
        root.mkdir(parents=True, exist_ok=True)
        atomic_write(root / MARKER, json.dumps(value).encode('utf-8'))
        self.checked[project] = time.monotonic()

    def baseline(self, project):
        if project not in self.base:
            marker = self.root(project) / MARKER
            if marker.is_file() and not marker.is_symlink() and marker.stat().st_size <= 12 * 1024 * 1024:
                try:
                    value = json.loads(marker.read_text(encoding='utf-8'))
                    if value.get('project') == project and value.get('owner') == self.store.owner and isinstance(value.get('files'), dict) and all(self.allowed(name) for name in value['files']):
                        self.base[project] = value
                except (ValueError, OSError):
                    pass
            self.base.setdefault(project, {'project': project, 'owner': self.store.owner, 'revision': None, 'files': {}})
        return self.base[project]

    @staticmethod
    def visible(entries):
        # Immutable history payloads can be added by the history coalescer while
        # another backend publishes source; they are not unsaved editor text.
        return {name: value for name, value in entries.items() if not name.startswith('.overleaf-history/') and name != '.overleaf-history'}

    def apply(self, project, old, new):
        root = self.root(project)
        if len(new) > 20000 or sum(entry.get('size', 0) for entry in new.values()) > MAX_TOTAL:
            raise Problem('Shared manifest vượt giới hạn dự án.', 413)
        root.mkdir(parents=True, exist_ok=True)
        # Validate every path and fetch/check every changed blob before writing.
        payloads = {}
        for name, entry in new.items():
            if not self.allowed(name) or len(name) > 1024:
                raise Problem('Shared manifest có đường dẫn không hợp lệ.', 503)
            target = root / name
            if not target.resolve().is_relative_to(root.resolve()) or any(p.is_symlink() for p in [target, *target.parents] if p != root.parent):
                raise Problem('Shared storage path không hợp lệ.', 403)
            if not entry.get('directory') and (old.get(name) != entry or not target.is_file()):
                payloads[name] = self.download(entry)
        removed = (set(old) - set(new)) | {name for name in set(old) & set(new) if bool(old[name].get('directory')) != bool(new[name].get('directory'))}
        for name in sorted(removed, key=lambda value: len(PurePosixPath(value).parts), reverse=True):
            target = root / name
            if not self.allowed(name) or not target.resolve().is_relative_to(root.resolve()) or target.is_symlink():
                raise Problem('Không xóa đường dẫn shared không hợp lệ.', 403)
            if target.is_file():
                target.unlink()
            elif target.is_dir() and not any(target.iterdir()):
                target.rmdir()
        for name, entry in new.items():
            target = root / name
            if entry.get('directory'):
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                if name in payloads:
                    atomic_write(target, payloads[name])

    def sync(self, project, force=False):
        with self.store.lock:
            if self.depth or (not force and time.monotonic() - self.checked.get(project, 0) < .75):
                return []
            self.depth += 1
            try:
                base = self.baseline(project)
                remote = self.db.project_files.find_one({'_id': project, 'owner_id': self.store.owner})
                if remote is None:
                    if self.root(project).is_dir():
                        # A restored database may retain project metadata but
                        # not the shared manifests. Re-publish the full copy,
                        # never only the next edited file from an old marker.
                        self.base[project] = {'project': project, 'owner': self.store.owner, 'revision': None, 'files': {}}
                        self.publish(project)
                    return []
                if remote['revision'] == base['revision']:
                    self.checked[project] = time.monotonic()
                    return []
                local = self.scan(self.root(project))
                if local and self.visible(local) != self.visible(base['files']) and self.visible(local) != self.visible(remote['files']):
                    self.backup(project, local, base['files'])
                    raise CloudConflict()
                changed = [name for name in set(local) | set(remote['files']) if local.get(name) != remote['files'].get(name)]
                self.apply(project, local, remote['files'])
                self.remember(project, {'project': project, 'owner': self.store.owner, 'revision': remote['revision'], 'files': remote['files']})
                self.changed.setdefault(project, set()).update(changed)
                return changed
            finally:
                self.depth -= 1

    def publish(self, project):
        base = self.baseline(project)
        root = self.root(project)
        entries = self.scan(root)
        if len(json.dumps(entries).encode('utf-8')) > 10 * 1024 * 1024:
            raise Problem('Shared manifest vượt 10 MiB.', 413)
        if entries == base['files'] and base['revision'] is not None:
            return
        for name, entry in entries.items():
            if not entry.get('directory') and entry != base['files'].get(name):
                self.upload(root / name, entry)
        delta = {name for name in set(base['files']) | set(entries) if base['files'].get(name) != entries.get(name)}
        for _ in range(4):
            remote = self.db.project_files.find_one({'_id': project})
            if remote and remote['owner_id'] != self.store.owner:
                raise CloudConflict()
            merged = dict(remote['files']) if remote else {}
            if remote and remote['revision'] != base['revision']:
                if any(merged.get(name) not in (base['files'].get(name), entries.get(name)) for name in delta):
                    raise CloudConflict()
            for name in delta:
                if name in entries:
                    merged[name] = entries[name]
                else:
                    merged.pop(name, None)
            revision = uuid.uuid4().hex
            value = {'_id': project, 'owner_id': self.store.owner, 'revision': revision, 'files': dict(sorted(merged.items()))}
            if remote is None:
                try:
                    self.db.project_files.insert_one(value)
                except DuplicateKeyError:
                    continue
            elif not self.db.project_files.replace_one({'_id': project, 'owner_id': self.store.owner, 'revision': remote['revision']}, value).matched_count:
                continue
            self.apply(project, entries, value['files'])
            self.changed.setdefault(project, set()).update(name for name in set(entries) | set(merged) if entries.get(name) != merged.get(name))
            self.remember(project, {'project': project, 'owner': self.store.owner, 'revision': revision, 'files': value['files']})
            return
        raise CloudConflict()

    def backup(self, project, local, base):
        root = self.root(project)
        identifier = hashlib.sha256(json.dumps(local, sort_keys=True).encode()).hexdigest()[:24]
        backup = root / '.overleaf-cloud-conflicts' / identifier
        if (root / '.overleaf-cloud-conflicts').is_symlink() or backup.is_symlink():
            raise Problem('Không ghi backup qua symbolic link.', 403)
        backup.mkdir(parents=True, exist_ok=True)
        for name, entry in local.items():
            if not entry.get('directory') and entry != base.get(name):
                target = backup / name
                if not target.resolve().is_relative_to(backup.resolve()) or any(p.is_symlink() for p in [target, *target.parents] if p.is_relative_to(backup)):
                    raise Problem('Backup path không hợp lệ.', 403)
                target.parent.mkdir(parents=True, exist_ok=True)
                atomic_write(target, (root / name).read_bytes())

    def accept_remote(self, project):
        """Explicit operator recovery: back up local differences, use DB files.

        Never invoked automatically during editor save or connection recovery.
        """
        with self.store.lock:
            remote = self.db.project_files.find_one({'_id': project, 'owner_id': self.store.owner})
            if not remote:
                raise Problem('Dự án chưa có source trên shared storage.', 409)
            local = self.scan(self.root(project))
            if local and local != remote['files']:
                self.backup(project, local, remote['files'])
            self.apply(project, local, remote['files'])
            self.remember(project, {'project': project, 'owner': self.store.owner, 'revision': remote['revision'], 'files': remote['files']})

    @contextmanager
    def edit(self, project):
        with self.store.lock:
            if self.depth:
                yield
                return
            # Do not refresh an already-read baseline just before writing:
            # doing so would silently turn stale editor text into a new version.
            if project not in self.base:
                self.sync(project, force=True)
            base = self.baseline(project)
            self.depth += 1
            try:
                yield
                self.publish(project)
            except CloudConflict:
                # Keep rejected text/binaries privately before restoring the
                # baseline, so a later refresh can safely fetch the winner.
                root = self.root(project)
                local = self.scan(root)
                self.backup(project, local, base['files'])
                self.apply(project, local, base['files'])
                self.checked[project] = 0
                raise
            finally:
                self.depth -= 1

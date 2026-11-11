"""Read-only SQLite/JSON source inspection and resumable MongoDB cutover."""
import hashlib
import json
import re
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path

from latex_backend.storage.mongo import MongoSetupError, indexes
from latex_backend.storage.state import properties

COLLECTIONS = ('users', 'sessions', 'shared_projects', 'invitations', 'link_visitors',
               'user_state', 'projects', 'history', 'project_records')


def rebind_home(home, db, *, apply=False):
    """Move the app server while retaining Atlas and the existing copied file volume."""
    home = home.expanduser().resolve()
    previous = db.app_config.find_one({'_id': 'storage_home'})
    if not previous:
        raise MongoSetupError('Database chưa được khởi tạo; không có storage home để chuyển.')
    checked = 0
    for row in db.projects.find({'deleted': False}):
        owner = row['owner_id']
        if owner != 'legacy' and not re.fullmatch(r'[0-9a-f]{32}', owner):
            raise MongoSetupError('Owner id không hợp lệ trong database.')
        folder = home if owner == 'legacy' else home / 'users' / owner
        root = folder / 'projects' / row['_id']
        main = root / row['metadata']['main']
        if not main.resolve().is_relative_to(root.resolve()) or not root.resolve().is_relative_to(home) or root.is_symlink() or not main.is_file():
            raise MongoSetupError('Thiếu source trong bản sao storage; chưa đổi storage home.')
        checked += 1
    for row in db.history.find({}):
        # Histories of deleted projects live in trash, not the active source volume.
        if not db.projects.find_one({'_id': row['project'], 'deleted': False}):
            continue
        folder = home if row['owner_id'] == 'legacy' else home / 'users' / row['owner_id']
        payload = folder / 'projects' / row['project'] / '.overleaf-history' / row['value']['id'] / 'content.txt'
        if not payload.resolve().is_relative_to(home) or payload.is_symlink() or not payload.is_file() or hashlib.sha256(payload.read_bytes()).hexdigest() != row['value']['sha256']:
            raise MongoSetupError('Bản sao history thiếu hoặc sai checksum; chưa đổi storage home.')
    if apply:
        db.app_config.update_one({'_id': 'storage_home', 'path': previous['path']},
                                {'$set': {'path': str(home), 'backend': 'mongodb'}})
    return {'state': 'rebound' if apply else 'rebind-dry-run', 'projectsChecked': checked, 'newHome': str(home)}


def inspect(home):
    home = home.expanduser().resolve()
    documents = {name: [] for name in COLLECTIONS}
    sources = []

    def safe(path):
        if not path.resolve().is_relative_to(home):
            raise MongoSetupError('Source nằm ngoài thư mục dữ liệu được chọn.')
        current = path
        while current != home:
            if current.is_symlink():
                raise MongoSetupError('Không migrate symbolic link: ' + str(current))
            current = current.parent

    def read(path):
        safe(path)
        if path.stat().st_size > 12 * 1024 * 1024:
            raise MongoSetupError('JSON source vượt giới hạn migration 12 MiB: ' + str(path))
        raw = path.read_bytes()
        sources.append((path, raw))
        return json.loads(raw.decode('utf-8-sig'))

    sql_path = home / '.auth/accounts.sqlite3'
    if sql_path.exists():
        safe(sql_path)
        with sqlite3.connect(sql_path.as_uri() + '?mode=ro', uri=True) as sql:
            sql.row_factory = sqlite3.Row
            tables = {row[0] for row in sql.execute("SELECT name FROM sqlite_master WHERE type='table'")}
            for row in sql.execute('SELECT * FROM users'):
                item = dict(row)
                item['_id'] = item['id']
                item['password'] = json.loads(item['password']) if item.get('password') else None
                item['auth_version'] = 0
                documents['users'].append(item)
            for row in sql.execute('SELECT * FROM sessions'):
                expires = datetime.fromtimestamp(row['expires'], timezone.utc)
                expires = expires.replace(microsecond=expires.microsecond // 1000 * 1000)
                documents['sessions'].append({'_id': row['digest'], 'user_id': row['user_id'], 'version': 0,
                    'expires_at': expires})
            for name in ('shared_projects', 'invitations', 'link_visitors'):
                if name not in tables:
                    continue
                for row in sql.execute('SELECT * FROM ' + name):
                    item = {key: value for key, value in dict(row).items() if value is not None}
                    item['_id'] = item.pop('id') if name == 'invitations' else (
                        item['project'] if name == 'shared_projects' else f"{item['project']}:{item['user_id']}")
                    documents[name].append(item)
    root_state = read(home / '.app/state.json') if (home / '.app/state.json').exists() else {}
    emails = {user['email'] for user in documents['users']}
    for email, value in root_state.get('accounts', {}).items():
        if email in emails:
            continue
        identifier = uuid.uuid5(uuid.NAMESPACE_URL, f'latex:{home}:{email}').hex
        documents['users'].append({'_id': identifier, 'id': identifier, 'email': email, 'name': value['name'],
            'password': {key: value[key] for key in ('salt', 'hash', 'iterations')}, 'google_sub': None,
            'role': None, 'auth_version': 0})

    workspaces = [('legacy', home)]
    if (home / 'users').is_dir():
        for folder in (home / 'users').iterdir():
            if re.fullmatch(r'[0-9a-f]{32}', folder.name) and folder.is_dir():
                if folder.is_symlink():
                    raise MongoSetupError('Workspace source không được là symbolic link.')
                workspaces.append((folder.name, folder))
    for owner, folder in workspaces:
        state_path = folder / '.app/state.json'
        state = root_state if owner == 'legacy' else (read(state_path) if state_path.exists() else {})
        documents['user_state'].append({'_id': owner, 'value': {'accounts': {},
            'settings': state.get('settings', {}), 'activity': state.get('activity', {}), 'compiles': state.get('compiles', {})}})
        projects = folder / 'projects'
        if not projects.is_dir():
            continue
        if projects.is_symlink():
            raise MongoSetupError('Projects source không được là symbolic link.')
        for project in projects.iterdir():
            if not re.fullmatch(r'[0-9a-f]{32}', project.name) or not project.is_dir():
                continue
            if project.is_symlink():
                raise MongoSetupError('Project source không được là symbolic link.')
            metadata = read(project / '.project.json')
            if metadata.get('id') != project.name:
                raise MongoSetupError('Project metadata id không khớp thư mục: ' + project.name)
            documents['projects'].append({'_id': project.name, 'owner_id': owner, 'metadata': {
                key: value for key, value in metadata.items() if key not in {'folder', 'storage', 'permission', 'shared', 'ownerName'}}, 'deleted': False})
            for filename, kind in (('.overleaf-review.json', 'review'), ('.overleaf-ielts.json', 'ielts')):
                if (project / filename).exists():
                    documents['project_records'].append({'_id': f'{owner}:{project.name}:{kind}', 'owner_id': owner,
                        'project': project.name, 'kind': kind, 'value': read(project / filename)})
            history = project / '.overleaf-history'
            if history.is_symlink():
                raise MongoSetupError('History source không được là symbolic link.')
            if not history.is_dir():
                continue
            for revision in history.iterdir():
                if not re.fullmatch(r'[a-f0-9-]{32,36}', revision.name) or not revision.is_dir():
                    continue
                if revision.is_symlink() or (revision / 'content.txt').is_symlink():
                    raise MongoSetupError('Revision source không được là symbolic link.')
                if (revision / 'metadata.json').exists():
                    value = read(revision / 'metadata.json')
                else:
                    safe(revision / 'metadata.properties')
                    value = properties(revision / 'metadata.properties', 'iso-8859-1')
                    sources.append((revision / 'metadata.properties', (revision / 'metadata.properties').read_bytes()))
                value['id'] = revision.name
                safe(revision / 'content.txt')
                if (revision / 'content.txt').stat().st_size > 2 * 1024 * 1024:
                    raise MongoSetupError('Nội dung history vượt 2 MiB: ' + revision.name)
                content = (revision / 'content.txt').read_bytes()
                if value['sha256'] != hashlib.sha256(content).hexdigest():
                    raise MongoSetupError('Checksum history không khớp: ' + revision.name)
                value['size'] = len(content)
                documents['history'].append({'_id': f'{owner}:{project.name}:{revision.name}', 'owner_id': owner,
                    'project': project.name, 'time': value['time'], 'value': value})
    for values in documents.values():
        values.sort(key=lambda value: value['_id'])
    canonical = json.dumps(documents, sort_keys=True, ensure_ascii=False, default=lambda value: value.isoformat()).encode()
    fingerprint = hashlib.sha256(canonical).hexdigest()
    return documents, sources, fingerprint


def migrate(home, db=None, *, apply=False):
    home = home.expanduser().resolve()
    if apply and db is not None:
        bound = db.app_config.find_one({'_id': 'storage_home'})
        if bound and bound['path'] != str(home):
            raise MongoSetupError('Destination database thuộc storage home khác.')
        complete = db.migrations.find_one({'_id': 'sqlite_json_v1', 'state': 'complete'})
        if complete:
            return {'state': 'already-complete', 'counts': complete['counts'], 'backup': complete.get('backup'), 'sourceFilesModified': False}
    documents, sources, fingerprint = inspect(home)
    report = {'state': 'dry-run', 'counts': {name: len(values) for name, values in documents.items()},
              'sourceFingerprint': fingerprint, 'sourceFilesModified': False}
    if not apply:
        return report
    if db is None:
        raise MongoSetupError('Cần kết nối MongoDB trước khi apply.')
    bound = db.app_config.find_one({'_id': 'storage_home'})
    if bound and bound['path'] != str(home):
        raise MongoSetupError('Destination database thuộc storage home khác.')
    marker = db.migrations.find_one({'_id': 'sqlite_json_v1'})
    if marker and marker.get('state') == 'complete':
        return {**report, 'state': 'already-complete', 'backup': marker.get('backup')}
    if marker and marker.get('fingerprint') != fingerprint:
        raise MongoSetupError('Source thay đổi sau lần migrate chưa hoàn tất. Không tự ghi đè destination.')
    if not marker and any(db[name].count_documents({}) for name in COLLECTIONS):
        raise MongoSetupError('Destination không rỗng. Dùng database mới để tránh ghi đè dữ liệu.')
    indexes(db)
    backup = Path(marker['backup']) if marker else home / '.migration-backups' / ('mongo-' + datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S') + '-' + uuid.uuid4().hex[:8])
    if not marker:
        backup.mkdir(parents=True, exist_ok=False)
        for source, raw in sources:
            target = backup / source.relative_to(home)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(raw)
        source_db = home / '.auth/accounts.sqlite3'
        if source_db.exists():
            target_db = backup / '.auth/accounts.sqlite3'
            target_db.parent.mkdir(parents=True, exist_ok=True)
            with sqlite3.connect(source_db.as_uri() + '?mode=ro', uri=True) as source, sqlite3.connect(target_db) as target:
                source.backup(target)
        (backup / 'manifest.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
        db.migrations.insert_one({'_id': 'sqlite_json_v1', 'state': 'in-progress', 'fingerprint': fingerprint,
                                  'backup': str(backup), 'started_at': datetime.now(timezone.utc)})
    db.app_config.update_one({'_id': 'storage_home'}, {'$setOnInsert': {'path': str(home)}}, upsert=True)
    for name, values in documents.items():
        for value in values:
            db[name].update_one({'_id': value['_id']}, {'$setOnInsert': value}, upsert=True)
    for name, values in documents.items():
        for value in values:
            actual = db[name].find_one({'_id': value['_id']})
            if actual != value:
                # TTL may already have removed a naturally expired imported session.
                if name == 'sessions' and value['expires_at'] <= datetime.now(timezone.utc):
                    continue
                raise MongoSetupError('Đối chiếu migration thất bại ở collection ' + name + '; chưa đánh dấu hoàn tất.')
    if inspect(home)[2] != fingerprint:
        raise MongoSetupError('Source thay đổi trong lúc migrate. Chưa đánh dấu hoàn tất; giữ web dừng và đối chiếu backup.')
    db.migrations.update_one({'_id': 'sqlite_json_v1'}, {'$set': {'state': 'complete', 'counts': report['counts'],
        'completed_at': datetime.now(timezone.utc)}})
    return {**report, 'state': 'complete', 'backup': str(backup)}

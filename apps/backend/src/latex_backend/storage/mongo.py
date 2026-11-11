"""MongoDB connection and indexes for the application, not the legacy prototype."""
import json
import os
from pathlib import Path

from pymongo import MongoClient
from pymongo.errors import PyMongoError
from latex_backend.core.config import REPO


class MongoSetupError(RuntimeError):
    pass


def settings():
    path = REPO / 'config/mongodb.local.json'
    try:
        local = json.loads(path.read_text(encoding='utf-8-sig')) if path.exists() else {}
    except (ValueError, OSError):
        raise MongoSetupError('config/mongodb.local.json không hợp lệ. Không ghi URI/mật khẩu vào log.') from None
    uri = os.getenv('LATEX_MONGO_URI') or local.get('uri') or os.getenv('OVERLEAF_MONGO_URI')
    name = os.getenv('LATEX_MONGO_DB') or local.get('database') or 'latex'
    if not uri:
        raise MongoSetupError('Web yêu cầu MongoDB. Điền config/mongodb.local.json hoặc LATEX_MONGO_URI; xem docs/MONGODB_PRIMARY.md. Không tự chuyển về SQLite.')
    if not isinstance(uri, str) or not uri.strip().startswith(('mongodb://', 'mongodb+srv://')) or '<' in uri or '>' in uri:
        raise MongoSetupError('MongoDB URI không hợp lệ hoặc vẫn chứa placeholder. Không in URI/mật khẩu ra log.')
    if not isinstance(name, str) or not name or any(char in name for char in '/\\.$ "'):
        raise MongoSetupError('Tên database MongoDB không hợp lệ.')
    return uri.strip(), name


def connect():
    uri, name = settings()
    client = None
    try:
        client = MongoClient(uri, serverSelectionTimeoutMS=8000, connectTimeoutMS=8000,
                             socketTimeoutMS=20000, tz_aware=True, appname='LatexWeb')
        client.admin.command('ping')
        return client, client[name]
    except (PyMongoError, ValueError):
        if client is not None:
            client.close()
        raise MongoSetupError('Không kết nối được MongoDB. Kiểm tra URI, database user, IP access list và mạng. URI/secret không được in ra.') from None


def indexes(db):
    def ensure(collection, keys, **options):
        keys = [(keys, 1)] if isinstance(keys, str) else keys
        name = '_'.join(f'{key}_{direction}' for key, direction in keys)
        existing = collection.index_information().get(name)
        if existing:
            if any(existing.get(key) != value for key, value in options.items()):
                raise MongoSetupError('MongoDB index không khớp cấu hình yêu cầu: ' + name)
            return
        collection.create_index(keys, **options)
    ensure(db.users, 'email', unique=True)
    ensure(db.users, 'google_sub', unique=True, partialFilterExpression={'google_sub': {'$type': 'string'}})
    ensure(db.sessions, 'expires_at', expireAfterSeconds=0)
    ensure(db.sessions, 'user_id')
    ensure(db.projects, [('owner_id', 1), ('deleted', 1)])
    ensure(db.shared_projects, 'project', unique=True)
    ensure(db.shared_projects, 'token', unique=True, partialFilterExpression={'token': {'$type': 'string'}})
    ensure(db.invitations, [('project', 1), ('email', 1)], unique=True)
    ensure(db.invitations, 'token', unique=True)
    ensure(db.invitations, 'user_id')
    ensure(db.link_visitors, [('project', 1), ('user_id', 1)], unique=True)
    ensure(db.history, [('owner_id', 1), ('project', 1), ('time', -1)])


def prepare(home: Path, db):
    home = home.expanduser().resolve()
    bound = db.app_config.find_one({'_id': 'storage_home'})
    if bound and bound['path'] != str(home):
        raise MongoSetupError('Database đang gắn với thư mục dữ liệu khác. Dùng database riêng hoặc làm quy trình chuyển storage home có kiểm chứng.')
    migration = db.migrations.find_one({'_id': 'sqlite_json_v1'})
    legacy = any((home / name).exists() for name in ('.auth/accounts.sqlite3', '.app/state.json', '.app/project-locations.json'))
    for name in ('projects', 'users'):
        folder = home / name
        if folder.is_dir() and any(folder.iterdir()):
            legacy = True
    initialized = bound and bound.get('backend') == 'mongodb'
    if (migration and migration.get('state') != 'complete') or (legacy and not migration and not initialized):
        raise MongoSetupError('Có dữ liệu SQLite/JSON cũ chưa chuyển. Dừng web rồi chạy script migrate-mongodb với --dry-run, sau đó --apply. Xem docs/MONGODB_PRIMARY.md.')
    indexes(db)
    db.app_config.update_one({'_id': 'storage_home'}, {'$set': {'path': str(home), 'backend': 'mongodb'}}, upsert=True)

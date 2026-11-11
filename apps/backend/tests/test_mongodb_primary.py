import hashlib
import json
import sqlite3
import time
from datetime import datetime, timezone

import pytest
from fastapi.testclient import TestClient
from pymongo.errors import AutoReconnect

from latex_backend.core.application import create_web_app
from latex_backend.storage.migration import migrate
from latex_backend.storage.mongo import MongoSetupError, prepare
from latex_backend.storage.state import State
from latex_backend.storage.store import Store
from latex_backend.storage.mongo_store import MongoStore
from latex_backend.collaboration.review import Review

URL = 'http://127.0.0.1:8000'


def test_sqlite_json_migration_preserves_identity_acl_settings_and_history(tmp_path, mongo_database):
    home = tmp_path / 'legacy'
    owner, member = 'a' * 32, 'b' * 32
    legacy = Store(home / 'users' / owner)
    legacy.state.preferences({'mode': 'dark', 'language': 'en'})
    project = legacy.create('Original manuscript')['id']
    legacy.save(project, 'main.tex', 'MIGRATED SOURCE')
    revision = legacy.history.list(project)[0]['id']
    legacy.history.label(project, revision, 'Important revision')
    Review(legacy).update(project, {'operation': 'create', 'file': 'main.tex', 'text': 'Keep this review'})
    auth = home / '.auth'
    auth.mkdir()
    sql_file = auth / 'accounts.sqlite3'
    with sqlite3.connect(sql_file) as sql:
        sql.executescript('''
            CREATE TABLE users(id TEXT PRIMARY KEY,email TEXT,name TEXT,password TEXT,google_sub TEXT,role TEXT);
            CREATE TABLE sessions(digest TEXT,user_id TEXT,expires REAL);
            CREATE TABLE shared_projects(project TEXT,owner TEXT,mode TEXT,permission TEXT,token TEXT);
            CREATE TABLE invitations(id TEXT,project TEXT,email TEXT,permission TEXT,token TEXT,user_id TEXT);
            CREATE TABLE link_visitors(project TEXT,user_id TEXT,token TEXT);
        ''')
        password = json.dumps(State.password('old-password'))
        sql.execute('INSERT INTO users VALUES(?,?,?,?,?,?)', (owner, 'owner@example.com', 'Owner', password, None, 'tutor'))
        sql.execute('INSERT INTO users VALUES(?,?,?,?,?,?)', (member, 'member@example.com', 'Member', None, 'google-sub-keep', 'student'))
        sql.execute('INSERT INTO sessions VALUES(?,?,?)', (hashlib.sha256(b'old-cookie').hexdigest(), owner, time.time() + 86400))
        sql.execute('INSERT INTO shared_projects VALUES(?,?,?,?,?)', (project, owner, 'link', 'viewer', 'preserved-public-token'))
        sql.execute('INSERT INTO invitations VALUES(?,?,?,?,?,?)', ('c' * 32, project, 'member@example.com', 'editor', 'preserved-invitation-token', member))
    original_sql = sql_file.read_bytes()
    original_json = (legacy.root(project) / '.project.json').read_bytes()
    report = migrate(home)
    assert report['state'] == 'dry-run' and report['counts']['users'] == 2
    assert mongo_database.users.count_documents({}) == 0
    with pytest.raises(MongoSetupError):
        prepare(home, mongo_database)
    completed = migrate(home, mongo_database, apply=True)
    assert completed['state'] == 'complete'
    assert sql_file.read_bytes() == original_sql
    assert (legacy.root(project) / '.project.json').read_bytes() == original_json
    assert __import__('pathlib').Path(completed['backup']).joinpath('.auth/accounts.sqlite3').is_file()
    app = create_web_app(home, database=mongo_database)
    with TestClient(app, base_url=URL) as client:
        assert app.accounts.resolve('old-cookie')['id'] == owner
        assert app.accounts.login('owner@example.com', 'old-password')['role'] == 'tutor'
        assert app.accounts.google({'sub': 'google-sub-keep', 'email': 'member@example.com', 'email_verified': True})['id'] == member
        assert app.sharing.access(project, member) == 'editor'
        assert app.sharing.info(project, owner)['linkToken'] == 'preserved-public-token'
        client.cookies.set('latex_account', 'old-cookie', path='/api')
        assert client.get('/api/session').json()['settings']['mode'] == 'dark'
        assert client.get('/api/projects').json()[0]['title'] == 'Original manuscript'
        assert any(row['label'] == 'Important revision' for row in client.get(f'/api/projects/{project}/history').json())
        assert client.get(f'/api/projects/{project}/review').json()[0]['messages'][0]['text'] == 'Keep this review'
    # After cutover these old JSON files can even be unreadable: runtime uses MongoDB.
    (legacy.root(project) / '.project.json').write_text('BROKEN JSON', encoding='utf-8')
    legacy.state.file.write_text('BROKEN JSON', encoding='utf-8')
    (legacy.history.directory(project) / revision / 'metadata.json').write_text('BROKEN JSON', encoding='utf-8')
    with TestClient(create_web_app(home, database=mongo_database), base_url=URL) as client:
        client.cookies.set('latex_account', 'old-cookie', path='/api')
        assert client.get('/api/projects').json()[0]['title'] == 'Original manuscript'
        assert client.get('/api/session').json()['settings']['language'] == 'en'
        assert any(row['label'] == 'Important revision' for row in client.get(f'/api/projects/{project}/history').json())


def test_migration_idempotent_and_nonempty_target_refused(tmp_path, mongo_database):
    state = State(tmp_path)
    state.authenticate('register', 'Old', 'old@example.com', 'old-password')
    first = migrate(tmp_path, mongo_database, apply=True)
    assert first['state'] == 'complete'
    second = migrate(tmp_path, mongo_database, apply=True)
    assert second['state'] == 'already-complete'
    assert mongo_database.users.count_documents({}) == 1
    another = mongo_database.client.another
    another.users.insert_one({'_id': 'existing', 'email': 'keep@example.com'})
    with pytest.raises(MongoSetupError):
        migrate(tmp_path, another, apply=True)
    assert another.users.find_one({'_id': 'existing'})['email'] == 'keep@example.com'


def test_mongo_is_required_and_errors_do_not_leak_or_fallback(tmp_path, mongo_database, monkeypatch):
    with TestClient(create_web_app(tmp_path, database=mongo_database), base_url=URL) as client:
        client.post('/api/web/session')
        assert client.get('/api/health').json()['database'] == 'mongodb'
        def unavailable(*args, **kwargs):
            raise AutoReconnect('do-not-expose-sensitive-details')
        monkeypatch.setattr(mongo_database.users, 'find_one', unavailable)
        # A valid-looking cookie reaches the identity query without a fallback account store.
        response = client.post('/api/actions/login', json={'email': 'a@example.com', 'password': 'password'})
        assert response.status_code == 503 and 'do-not-expose' not in response.text
    assert not (tmp_path / '.auth/accounts.sqlite3').exists()


def test_state_and_metadata_writes_live_in_mongodb(tmp_path, mongo_database):
    store = MongoStore(tmp_path, mongo_database, 'user')
    project = store.create('Mongo project')['id']
    store.state.preferences({'language': 'en'})
    store.save(project, 'main.tex', 'NEW CONTENT')
    store.update_project(project, 'New title')
    assert mongo_database.projects.find_one({'_id': project})['metadata']['title'] == 'New title'
    assert mongo_database.user_state.find_one({'_id': 'user'})['value']['activity']
    assert mongo_database.history.count_documents({'project': project}) >= 2
    store.state.activity('compile')
    assert store.state.activity()['compiles'] == 1
    assert not store.state.file.exists()
    store.delete_project(project)
    assert mongo_database.projects.find_one({'_id': project})['deleted']
    assert store.list() == []


def test_legacy_projects_without_state_still_require_migration(tmp_path, mongo_database):
    store = Store(tmp_path)
    store.create('Legacy without account')
    assert not store.state.file.exists()
    with pytest.raises(MongoSetupError):
        prepare(tmp_path, mongo_database)


def test_migration_resumes_after_database_failure(tmp_path, mongo_database, monkeypatch):
    store = Store(tmp_path)
    project = store.create('Resumable migration')['id']
    original = mongo_database.projects.update_one
    def unavailable(*args, **kwargs):
        raise AutoReconnect('temporary outage')
    monkeypatch.setattr(mongo_database.projects, 'update_one', unavailable)
    with pytest.raises(AutoReconnect):
        migrate(tmp_path, mongo_database, apply=True)
    assert mongo_database.migrations.find_one({'_id': 'sqlite_json_v1'})['state'] == 'in-progress'
    with pytest.raises(MongoSetupError):
        prepare(tmp_path, mongo_database)
    monkeypatch.setattr(mongo_database.projects, 'update_one', original)
    assert migrate(tmp_path, mongo_database, apply=True)['state'] == 'complete'
    assert mongo_database.projects.count_documents({'_id': project}) == 1


def test_rebind_home_verifies_the_copied_volume(tmp_path, mongo_database):
    import shutil
    from latex_backend.storage.migration import rebind_home
    original = tmp_path / 'old-volume'
    store = Store(original)
    project = store.create('Moved server')['id']
    store.save(project, 'main.tex', 'COPY ME')
    migrate(original, mongo_database, apply=True)
    new_home = tmp_path / 'new-volume'
    with pytest.raises(MongoSetupError):
        rebind_home(new_home, mongo_database, apply=True)
    shutil.copytree(original, new_home)
    assert rebind_home(new_home, mongo_database)['state'] == 'rebind-dry-run'
    assert mongo_database.app_config.find_one({'_id': 'storage_home'})['path'] == str(original)
    assert rebind_home(new_home, mongo_database, apply=True)['state'] == 'rebound'
    prepare(new_home, mongo_database)


def test_connection_configuration_is_required_and_secrets_are_not_logged(tmp_path, monkeypatch):
    from latex_backend.storage import mongo
    monkeypatch.setattr(mongo, 'REPO', tmp_path)
    monkeypatch.delenv('LATEX_MONGO_URI', raising=False)
    monkeypatch.delenv('OVERLEAF_MONGO_URI', raising=False)
    with pytest.raises(MongoSetupError, match='yêu cầu MongoDB'):
        mongo.settings()
    monkeypatch.setenv('LATEX_MONGO_URI', 'mongodb://private-user:secret-value@example.invalid')
    def failed(*args, **kwargs):
        raise AutoReconnect('private-user secret-value')
    monkeypatch.setattr(mongo, 'MongoClient', failed)
    with pytest.raises(MongoSetupError) as error:
        mongo.connect()
    assert 'secret-value' not in str(error.value)


@pytest.mark.parametrize('code, message, auth_failure', [
    (18, 'private-user secret-value', True),
    (8000, 'bad auth : authentication failed private-user secret-value', True),
    (8000, 'Atlas quota failure private-user secret-value', False),
])
def test_connection_auth_failure_is_specific_and_redacted(monkeypatch, code, message, auth_failure):
    from pymongo.errors import OperationFailure
    from latex_backend.storage import mongo
    monkeypatch.setattr(mongo, 'settings', lambda: ('mongodb://private-user:secret-value@example.invalid', 'test'))
    closed = []

    class Client:
        admin = None

        def __init__(self, *args, **kwargs):
            self.admin = self

        def command(self, *args):
            raise OperationFailure(message, code=code)

        def close(self):
            closed.append(True)

    monkeypatch.setattr(mongo, 'MongoClient', Client)
    with pytest.raises(MongoSetupError) as failure:
        mongo.connect()
    text = str(failure.value)
    assert ('xác thực database user thất bại' in text) == auth_failure
    assert 'secret-value' not in text and 'private-user' not in text
    assert closed == [True]

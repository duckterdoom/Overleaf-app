import asyncio
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from latex_backend.core.application import create_web_app
from latex_backend.storage.mongo import prepare
from latex_backend.storage.mongo_store import MongoStore
from latex_backend.storage.shared_files import CloudConflict
from latex_backend.storage.store import Problem
from test_web_sharing import account, URL


def pair(tmp_path, database):
    a = create_web_app(tmp_path / 'Admin', database=database)
    b = create_web_app(tmp_path / 'Minh Ngoc', database=database)
    return a, b


def test_two_backends_share_accounts_source_and_binary_without_rebinding(tmp_path, mongo_database):
    a, b = pair(tmp_path, mongo_database)
    with TestClient(a, base_url=URL) as ca, TestClient(b, base_url=URL) as cb:
        token = account(ca, 'shared@example.com', 'Shared User')
        owner = a.accounts.resolve(token)['id']
        project = ca.post('/api/projects', json={'title': 'Cloud project'}).json()['id']
        assert cb.post('/api/actions/login', json={'email': 'shared@example.com', 'password': 'test-password'}).status_code == 200
        assert cb.get('/api/projects').json()[0]['id'] == project
        expected = ca.get(f'/api/projects/{project}/file?path=main.tex').json()['content']
        assert cb.get(f'/api/projects/{project}/file?path=main.tex').json()['content'] == expected
        sa, sb = a.tenants[owner][1], b.tenants[owner][1]
        from latex_backend.storage.files import ProjectIO
        image = tmp_path / 'image.png'
        image.write_bytes(b'BINARY IMAGE' * 100000)
        ProjectIO(sa).upload_paths(project, [image])
        sb.refresh_shared(project)
        assert sb.path(project, 'image.png').read_bytes() == image.read_bytes()
        sb.save(project, 'main.tex', 'Written on machine B')
        sa.refresh_shared(project)
        assert sa.read(project, 'main.tex') == 'Written on machine B'
        assert mongo_database.app_config.find_one({'_id': 'storage_home'})['path'] == str((tmp_path / 'Admin').resolve())
        assert mongo_database.app_config.count_documents({'_id': {'$regex': '^storage_node:'}}) == 2
        prepare(tmp_path / 'Admin', mongo_database)
        prepare(tmp_path / 'Minh Ngoc', mongo_database)


def test_stale_backend_write_rejected_cloud_winner_and_local_draft_preserved(tmp_path, mongo_database):
    a, b = pair(tmp_path, mongo_database)
    with TestClient(a, base_url=URL) as ca, TestClient(b, base_url=URL) as cb:
        token = account(ca, 'conflict@example.com', 'Author')
        owner = a.accounts.resolve(token)['id']
        project = ca.post('/api/projects', json={'title': 'Conflicts'}).json()['id']
        cb.post('/api/actions/login', json={'email': 'conflict@example.com', 'password': 'test-password'}).raise_for_status()
        cb.get('/api/projects').raise_for_status()
        cb.get(f'/api/projects/{project}/file?path=main.tex').raise_for_status()
        sa, sb = a.tenants[owner][1], b.tenants[owner][1]
        sa.save_live(project, 'main.tex', 'Accepted on A')
        with pytest.raises(CloudConflict):
            sb.save_live(project, 'main.tex', 'Rejected draft from B')
        backups = list((sb.projects / project / '.overleaf-cloud-conflicts').rglob('main.tex'))
        assert len(backups) == 1 and backups[0].read_text() == 'Rejected draft from B'
        sb.refresh_shared(project)
        assert sb.read(project, 'main.tex') == sa.read(project, 'main.tex') == 'Accepted on A'


def test_renamed_deleted_files_and_history_available_on_other_machine(tmp_path, mongo_database):
    prepare(tmp_path / 'one', mongo_database)
    owner = 'a' * 32
    a = MongoStore(tmp_path / 'one/users' / owner, mongo_database, owner)
    b = MongoStore(tmp_path / 'two/users' / owner, mongo_database, owner)
    project = a.create('History')['id']
    a.add(project, 'nested', True)
    a.add(project, 'nested/chapter.tex', False)
    a.save(project, 'nested/chapter.tex', 'Chapter text')
    a.save(project, 'main.tex', 'New main')
    b.refresh_shared(project)
    assert b.read(project, 'nested/chapter.tex') == 'Chapter text'
    revisions = a.history.list(project)
    assert revisions
    for revision in revisions:
        assert b.history.read(project, revision['id'])['sha256'] == revision['sha256']
    a.rename_file(project, 'nested/chapter.tex', 'nested/renamed.tex')
    b.refresh_shared(project)
    assert not b.path(project, 'nested/chapter.tex').exists()
    assert b.read(project, 'nested/renamed.tex') == 'Chapter text'
    a.delete_file(project, 'nested/renamed.tex')
    b.refresh_shared(project)
    assert not b.path(project, 'nested/renamed.tex').exists()


def test_bad_checksum_or_manifest_path_cannot_overwrite_local_files(tmp_path, mongo_database):
    prepare(tmp_path / 'one', mongo_database)
    owner = 'a' * 32
    a = MongoStore(tmp_path / 'one', mongo_database, owner)
    b = MongoStore(tmp_path / 'two', mongo_database, owner)
    project = a.create('Integrity')['id']
    b.refresh_shared(project)
    before = b.read(project, 'main.tex')
    a.save_live(project, 'main.tex', 'New remote source')
    remote = mongo_database.project_files.find_one({'_id': project})
    digest = remote['files']['main.tex']['sha256']
    mongo_database.project_chunks.update_one({'_id': digest + ':0'}, {'$set': {'data': b'CORRUPT'}})
    with pytest.raises(Problem, match='Checksum'):
        b.refresh_shared(project)
    assert (b.projects / project / 'main.tex').read_text() == before
    remote['files']['../escape'] = {'directory': True}
    remote['revision'] = 'malicious'
    mongo_database.project_files.replace_one({'_id': project}, remote)
    with pytest.raises(Problem):
        b.refresh_shared(project)
    assert not (tmp_path / 'escape').exists()


def test_other_owner_cannot_hydrate_project(tmp_path, mongo_database):
    prepare(tmp_path / 'one', mongo_database)
    a = MongoStore(tmp_path / 'one', mongo_database, 'a' * 32)
    project = a.create('Private')['id']
    intruder = MongoStore(tmp_path / 'other', mongo_database, 'b' * 32)
    with pytest.raises(Problem):
        intruder.root(project)
    assert not (intruder.projects / project).exists()


def test_operator_download_keeps_old_local_draft_and_does_not_change_db(tmp_path, mongo_database):
    prepare(tmp_path / 'one', mongo_database)
    owner = 'a' * 32
    a = MongoStore(tmp_path / 'one', mongo_database, owner)
    b = MongoStore(tmp_path / 'two', mongo_database, owner)
    project = a.create('Recovery')['id']
    b.refresh_shared(project)
    local = b.projects / project / 'main.tex'
    local.write_text('Unpublished local draft', encoding='utf-8')
    a.save_live(project, 'main.tex', 'Cloud winner')
    before = mongo_database.project_files.find_one({'_id': project})
    with pytest.raises(CloudConflict):
        b.refresh_shared(project)
    b.shared_files.accept_remote(project)
    assert b.read(project, 'main.tex') == 'Cloud winner'
    assert mongo_database.project_files.find_one({'_id': project}) == before
    backups = list((b.projects / project / '.overleaf-cloud-conflicts').rglob('main.tex'))
    assert backups and all(path.read_text() == 'Unpublished local draft' for path in backups)


def test_stale_writes_on_different_files_merge_and_file_to_folder_refresh(tmp_path, mongo_database):
    prepare(tmp_path / 'one', mongo_database)
    owner = 'a' * 32
    a = MongoStore(tmp_path / 'one', mongo_database, owner)
    b = MongoStore(tmp_path / 'two', mongo_database, owner)
    project = a.create('Merge')['id']
    for name in ['one.tex', 'two.tex', 'switch.tex']:
        a.add(project, name, False)
    b.refresh_shared(project)
    a.save_live(project, 'one.tex', 'Machine A')
    b.save_live(project, 'two.tex', 'Machine B')
    a.refresh_shared(project)
    assert a.read(project, 'one.tex') == b.read(project, 'one.tex') == 'Machine A'
    assert a.read(project, 'two.tex') == b.read(project, 'two.tex') == 'Machine B'
    a.delete_file(project, 'switch.tex')
    a.add(project, 'switch.tex', True)
    a.add(project, 'switch.tex/child.tex', False)
    b.refresh_shared(project)
    assert b.path(project, 'switch.tex').is_dir()
    assert b.path(project, 'switch.tex/child.tex').is_file()


def test_two_live_authorities_rebase_shared_source_without_epoch_reset(tmp_path, mongo_database):
    from latex_backend.collaboration.live_documents import LiveDocuments, apply_changes
    async def run():
        prepare(tmp_path / 'one', mongo_database)
        owner = 'a' * 32
        a = MongoStore(tmp_path / 'one', mongo_database, owner)
        b = MongoStore(tmp_path / 'two', mongo_database, owner)
        project = a.create('Distributed source')['id']
        a.save(project, 'main.tex', 'ab')
        b.refresh_shared(project)
        hubs, peers, compilers = [], [], []
        user = {'id': owner, 'name': 'Author'}
        for store, name in [(a, 'A'), (b, 'B')]:
            peer = {'queue': asyncio.Queue(128)}
            presence = SimpleNamespace(rooms={project: {name: peer}}, gateway=SimpleNamespace(accounts=SimpleNamespace(public=lambda value: value)))
            hub = LiveDocuments(presence)
            compiler = SimpleNamespace(dirty=set())
            await hub.handle(project, name, user, store, compiler, {'type': 'sync-open', 'file': 'main.tex', 'clientID': owner + ':' + name, 'epoch': '', 'version': 0})
            snapshot = peer['queue'].get_nowait()
            assert snapshot['type'] == 'sync-snapshot' and snapshot['text'] == 'ab'
            hubs.append(hub)
            peers.append((peer, snapshot['epoch']))
            compilers.append(compiler)
        await hubs[0].handle(project, 'A', user, a, compilers[0], {'type': 'sync-push', 'file': 'main.tex', 'epoch': peers[0][1], 'version': 0, 'updates': [[1, [0, 'X'], 1]]})
        await hubs[1].handle(project, 'B', user, b, compilers[1], {'type': 'sync-push', 'file': 'main.tex', 'epoch': peers[1][1], 'version': 0, 'updates': [[1, [0, 'Y'], 1]]})
        events = []
        while not peers[1][0]['queue'].empty():
            events.append(peers[1][0]['queue'].get_nowait())
        assert events and all(event['type'] == 'sync-updates' and event['epoch'] == peers[1][1] for event in events)
        assert apply_changes('ab', events[0]['updates'][0]['changes']) == 'aXb'
        # Browser rebases its pending Y insertion on the accepted X, then retries.
        await hubs[1].handle(project, 'B', user, b, compilers[1], {'type': 'sync-push', 'file': 'main.tex', 'epoch': peers[1][1], 'version': 1, 'updates': [[1, [0, 'Y'], 2]]})
        await hubs[0].external_edit(project, a, compilers[0], lambda: a.refresh_shared(project))
        assert a.read(project, 'main.tex') == b.read(project, 'main.tex') == 'aYXb'
        assert hubs[0].documents[(project, 'main.tex')]['epoch'] == peers[0][1]
        for hub in hubs:
            await hub.close()
    asyncio.run(run())

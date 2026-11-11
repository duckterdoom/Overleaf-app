"""Publish existing authorized project volumes without changing accounts or ACL."""
import argparse
import json
import re
import threading
from pathlib import Path
from types import SimpleNamespace

from latex_backend.core.config import data_directory
from latex_backend.storage.mongo import connect, MongoSetupError
from latex_backend.storage.mongo_store import MongoStore
from latex_backend.storage.shared_files import SharedFiles
from latex_backend.storage.store import Problem
from pymongo.errors import PyMongoError


def publish(home, db, *, apply=False, download=False):
    home = home.expanduser().resolve()
    plan = []
    for row in db.projects.find({'deleted': False}):
        owner, project = row['owner_id'], row['_id']
        if not re.fullmatch('[a-f0-9]{32}', project) or (owner != 'legacy' and not re.fullmatch('[a-f0-9]{32}', owner)):
            raise MongoSetupError('Project/owner id không hợp lệ; chưa publish.')
        folder = home if owner == 'legacy' else home / 'users' / owner
        shared = SharedFiles(SimpleNamespace(projects=folder / 'projects', db=db, owner=owner, lock=threading.RLock()))
        root = shared.root(project)
        main = root / row['metadata']['main']
        if download:
            remote = db.project_files.find_one({'_id': project, 'owner_id': owner})
            if not remote:
                raise MongoSetupError('Một dự án chưa có source trên shared storage; publish từ máy có source trước.')
            plan.append((owner, project, folder, remote['files']))
            continue
        if not root.is_dir() or not main.resolve().is_relative_to(root.resolve()) or not main.is_file():
            # Another machine may have projects this node has never downloaded.
            if db.project_files.find_one({'_id': project, 'owner_id': owner}):
                continue
            raise MongoSetupError('Thiếu source local của một dự án chưa có trên shared storage. Chưa publish; copy đầy đủ volume hoặc chạy lệnh trên máy có source.')
        entries = shared.scan(root)
        plan.append((owner, project, folder, entries))
    report = {'state': ('downloaded' if download else 'published') if apply else 'dry-run', 'projects': len(plan),
              'files': sum(sum(not entry.get('directory') for entry in entries.values()) for _, _, _, entries in plan),
              'bytes': sum(sum(entry.get('size', 0) for entry in entries.values()) for _, _, _, entries in plan),
              'accountsOrSharingChanged': False}
    if apply:
        for owner, project, folder, _ in plan:
            store = MongoStore(folder, db, owner)
            if download:
                store.shared_files.accept_remote(project)
            else:
                store.refresh_shared(project)
            remote = db.project_files.find_one({'_id': project, 'owner_id': owner})
            if not remote:
                raise MongoSetupError('Shared manifest chưa được xác nhận.')
            # Check persisted chunk checksums, not merely the upload count.
            for entry in remote['files'].values():
                if not entry.get('directory'):
                    store.shared_files.download(entry)
        report['checksumsVerified'] = True
    return report


def main():
    parser = argparse.ArgumentParser(description='Publish existing project source/images/history/Word drafts to shared MongoDB; keep accounts/ACL unchanged.')
    parser.add_argument('--home', type=Path, default=data_directory())
    parser.add_argument('--apply', action='store_true', help='Publish after validating the local files; default is read-only dry-run')
    parser.add_argument('--download', action='store_true', help='Download the DB version instead; local differences are backed up, DB source is never changed')
    args = parser.parse_args()
    client = None
    try:
        client, db = connect()
        print(json.dumps(publish(args.home, db, apply=args.apply, download=args.download), ensure_ascii=False, indent=2))
    except (MongoSetupError, Problem, PyMongoError, OSError, ValueError) as error:
        message = str(error) if isinstance(error, MongoSetupError) else error.message if isinstance(error, Problem) else 'Publish chưa hoàn tất. Kiểm tra file/quyền MongoDB; không có credential được in ra.'
        parser.error(message)
    finally:
        if client is not None:
            client.close()


if __name__ == '__main__':
    main()

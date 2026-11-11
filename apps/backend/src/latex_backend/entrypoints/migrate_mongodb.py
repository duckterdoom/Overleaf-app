"""Inspect legacy data without a database, then explicitly apply and verify."""
import argparse
import json
from pathlib import Path
from latex_backend.core.config import data_directory
from latex_backend.storage.migration import migrate, rebind_home
from latex_backend.storage.mongo import connect, MongoSetupError
from pymongo.errors import PyMongoError


def main():
    parser = argparse.ArgumentParser(description='SQLite/JSON to MongoDB migration; originals are preserved.')
    parser.add_argument('--home', type=Path, default=data_directory())
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--dry-run', action='store_true')
    mode.add_argument('--apply', action='store_true')
    parser.add_argument('--check', action='store_true', help='Only test connection, without printing the URI.')
    parser.add_argument('--rebind-home', action='store_true', help='Verify a copied file volume before moving the app server; requires --apply to change the binding.')
    args = parser.parse_args()
    client = None
    try:
        if args.apply or args.check or args.rebind_home:
            client, db = connect()
        else:
            db = None
        if args.check:
            db.users.find_one({}, {'_id': 1})
            print(json.dumps({'connection': 'ok', 'database': db.name}))
        elif args.rebind_home:
            print(json.dumps(rebind_home(args.home, db, apply=args.apply), ensure_ascii=False, indent=2))
        else:
            print(json.dumps(migrate(args.home, db, apply=args.apply), ensure_ascii=False, indent=2))
    except (MongoSetupError, PyMongoError, OSError, ValueError, KeyError) as error:
        message = str(error) if isinstance(error, MongoSetupError) else 'Migration không hoàn tất. Kiểm tra source và quyền database; không có secret được in ra.'
        parser.error(message)
    finally:
        if client is not None:
            client.close()


if __name__ == '__main__':
    main()

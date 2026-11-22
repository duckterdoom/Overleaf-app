"""Explicit browser-test factory with real Atlas collections scoped by a guard."""
import os
from atlas_namespace import NamespaceDatabase
from latex_backend.core.application import create_web_app
from latex_backend.storage.mongo import connect
from latex_backend.entrypoints import web

client, real_database = connect()
database = NamespaceDatabase(real_database, os.environ['LATEX_TEST_COLLECTION_PREFIX'])
guard = database.app_config.find_one({'_id': 'test_guard'})
if not guard or guard.get('nonce') != os.environ['LATEX_TEST_COLLECTION_GUARD']:
    client.close()
    raise RuntimeError('Missing live-test collection guard')
web.create_web_app = lambda home, frontend, latexmk: create_web_app(home, frontend, latexmk, database=database)
try:
    web.main()
finally:
    client.close()

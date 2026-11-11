"""Composition root for the managed web workspace."""
import secrets
from pathlib import Path

from latex_backend.compilation.compiler import Compiler
from latex_backend.http.api import create_api
from latex_backend.storage.mongo_store import MongoStore
from latex_backend.storage.mongo import connect, prepare, MongoSetupError
from pymongo.errors import PyMongoError
from latex_backend.http.web_accounts import WebAccounts


def create_web_app(home: Path, frontend: Path | None = None, latexmk: str = "", *, database=None):
    client = None
    if database is None:
        client, database = connect()
    try:
        prepare(home, database)
        store = MongoStore(home, database, 'legacy')
    except Exception as error:
        if client is not None:
            client.close()
        if isinstance(error, PyMongoError):
            raise MongoSetupError('Không chuẩn bị được MongoDB. Kiểm tra quyền database và indexes; không có fallback SQLite.') from None
        raise
    compiler = Compiler(store, latexmk)
    app_token = secrets.token_urlsafe(32)
    app = create_api(store, compiler, app_token, frontend, web=True)
    app.state.store = store
    app.state.compiler = compiler
    return WebAccounts(app, store.home, frontend, latexmk, app_token, database, client)

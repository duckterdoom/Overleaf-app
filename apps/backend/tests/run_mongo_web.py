"""Explicit browser-test factory. Never imported by production launchers."""
import mongomock
from latex_backend.core.application import create_web_app
from latex_backend.entrypoints import web

client = mongomock.MongoClient(tz_aware=True)
database = client.latex_browser_test
web.create_web_app = lambda home, frontend, latexmk: create_web_app(home, frontend, latexmk, database=database)
web.main()

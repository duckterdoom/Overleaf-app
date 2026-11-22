"""Isolated OAuth browser fixture: no real Google or Atlas connections."""
import os
import sys
import mongomock
from latex_backend.core.application import create_web_app
from latex_backend.entrypoints import web

os.environ['LATEX_GOOGLE_CLIENT_ID'] = 'test-browser-client'
os.environ['LATEX_GOOGLE_CLIENT_SECRET'] = 'test-browser-secret'
database = mongomock.MongoClient(tz_aware=True).latex_auth_role_test


def factory(home, frontend, latexmk):
    app = create_web_app(home, frontend, latexmk, database=database)

    async def exchange(code, flow):
        if code not in {'student', 'tutor'}:
            raise RuntimeError('Unknown OAuth fixture code')
        return {'sub': 'test-google-' + code, 'email': 'google-' + code + '@example.com',
                'email_verified': True, 'name': 'Google ' + code.title()}

    app.exchange_google = exchange
    return app


web.create_web_app = factory
# This isolated OAuth/UI fixture does not exercise native TeX compilation.
sys.argv.append('--skip-tex-check')
web.main()

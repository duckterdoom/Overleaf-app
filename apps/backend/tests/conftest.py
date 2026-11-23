"""Unit tests explicitly inject MongoDB doubles; production never falls back."""
import sys
from pathlib import Path
import mongomock
import pytest

# Ensure all test domain subdirectories and support are in sys.path for cross-test imports
_tests_dir = Path(__file__).resolve().parent
for _subdir in _tests_dir.iterdir():
    if _subdir.is_dir() and not _subdir.name.startswith(('.', '__')):
        _subdir_str = str(_subdir)
        if _subdir_str not in sys.path:
            sys.path.insert(0, _subdir_str)


@pytest.fixture(autouse=True)
def private_google_config(tmp_path, monkeypatch):
    # Never read a developer's real OAuth file or environment during unit tests.
    from latex_backend.auth import google_config
    monkeypatch.setattr(google_config, 'REPO', tmp_path / 'repo')
    monkeypatch.setattr(google_config, 'user_config_path', lambda: tmp_path / 'profile/google.local.json')
    for name in ('LATEX_GOOGLE_CLIENT_ID', 'LATEX_GOOGLE_CLIENT_SECRET', 'LATEX_PUBLIC_URL', 'LATEX_GOOGLE_CONFIG'):
        monkeypatch.delenv(name, raising=False)


@pytest.fixture(autouse=True)
def mongo_database(monkeypatch):
    from latex_backend.core import application
    client = mongomock.MongoClient(tz_aware=True)
    database = client.latex_test
    monkeypatch.setattr(application, 'connect', lambda: (None, database))
    yield database
    client.close()

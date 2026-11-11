"""Unit tests explicitly inject MongoDB doubles; production never falls back."""
import mongomock
import pytest


@pytest.fixture(autouse=True)
def mongo_database(monkeypatch):
    from latex_backend.core import application
    client = mongomock.MongoClient(tz_aware=True)
    database = client.latex_test
    monkeypatch.setattr(application, 'connect', lambda: (None, database))
    yield database
    client.close()

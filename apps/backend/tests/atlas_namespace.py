"""Test-only collection namespace over real PyMongo; no database permissions bypass."""
import re


class NamespaceDatabase:
    def __init__(self, database, prefix):
        if not re.fullmatch(r'latex_test_[0-9]{8}_[a-z]+_[0-9a-f]{16}_', prefix):
            raise ValueError('Invalid live-test collection prefix')
        self.database, self.prefix = database, prefix
        self.name = database.name

    def __getitem__(self, name):
        if not re.fullmatch(r'[a-z_]+', name):
            raise ValueError('Invalid test collection name')
        return self.database[self.prefix + name]

    def __getattr__(self, name):
        return self[name]

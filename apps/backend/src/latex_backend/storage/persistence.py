"""Optional M5 repository prototype, equivalent to Java persistence (not UI-wired)."""

import hmac
import os
from datetime import datetime, timezone


def object_id(value):
    from bson import ObjectId
    from bson.errors import InvalidId

    try:
        return ObjectId(value)
    except (InvalidId, ValueError, TypeError):
        return None


class Repository:
    def __init__(self, collection):
        self.collection = collection

    def save(self, value):
        value = dict(value)
        value["updated_at"] = datetime.now(timezone.utc)
        if not value.get("_id"):
            value["_id"] = self.collection.insert_one(value).inserted_id
        else:
            self.collection.replace_one({"_id": value["_id"]}, value)
        return value

    def find_by_id(self, identifier):
        key = object_id(identifier)
        return self.collection.find_one({"_id": key}) if key else None

    def delete_by_id(self, identifier):
        key = object_id(identifier)
        return bool(key and self.collection.delete_one({"_id": key}).deleted_count)


class UserRepository(Repository):
    def find_by_email(self, email):
        return self.collection.find_one({"email": email})

    def find_by_username(self, username):
        return self.collection.find_one({"username": username})

    def create_user(self, username, email, password_hash):
        return str(
            self.save(
                {
                    "username": username,
                    "email": email,
                    "password_hash": password_hash,
                    "project_ids": [],
                }
            )["_id"]
        )

    def add_project_to_user(self, user, project):
        user, project = object_id(user), object_id(project)
        if user and project:
            self.collection.update_one(
                {"_id": user}, {"$addToSet": {"project_ids": project}}
            )

    def remove_project_from_user(self, user, project):
        user, project = object_id(user), object_id(project)
        if user and project:
            self.collection.update_one(
                {"_id": user}, {"$pull": {"project_ids": project}}
            )


class ProjectRepository(Repository):
    def find_by_collaborator_id(self, user):
        key = object_id(user)
        return list(self.collection.find({"collaborator_ids": key})) if key else []

    def add_collaborator(self, project, user):
        project, user = object_id(project), object_id(user)
        if project and user:
            self.collection.update_one(
                {"_id": project},
                {
                    "$addToSet": {"collaborator_ids": user},
                    "$set": {"updated_at": datetime.now(timezone.utc)},
                },
            )

    def find_by_owner_id(self, owner):
        key = object_id(owner)
        return list(self.collection.find({"owner_id": key})) if key else []


class DocumentRepository(Repository):
    def find_by_project_id(self, project):
        key = object_id(project)
        return list(self.collection.find({"project_id": key})) if key else []

    def find_by_project_id_and_path(self, project, path):
        key = object_id(project)
        return (
            self.collection.find_one({"project_id": key, "path": path}) if key else None
        )

    def update_content(self, identifier, content):
        key = object_id(identifier)
        return bool(
            key
            and self.collection.update_one(
                {"_id": key},
                {
                    "$set": {
                        "content": content,
                        "updated_at": datetime.now(timezone.utc),
                    }
                },
            ).modified_count
        )


class DatabaseService:
    def __init__(self, database=None):
        self.client = None
        if database is None:
            from pymongo import MongoClient

            uri = os.getenv("OVERLEAF_MONGO_URI")
            if not uri:
                raise ValueError(
                    "Set OVERLEAF_MONGO_URI explicitly to use the optional MongoDB prototype"
                )
            self.client = MongoClient(uri, serverSelectionTimeoutMS=5000)
            database = self.client[os.getenv("OVERLEAF_MONGO_DB", "overleaf")]
        self.users = UserRepository(database["users"])
        self.projects = ProjectRepository(database["projects"])
        self.documents = DocumentRepository(database["documents"])

    def authenticate(self, email, password_hash):
        user = self.users.find_by_email(email)
        return (
            user
            if user
            and hmac.compare_digest(
                str(user.get("password_hash", "")).encode(), password_hash.encode()
            )
            else None
        )

    def load_initial_document_content(self, project, path):
        document = self.documents.find_by_project_id_and_path(project, path)
        return document.get("content", "") if document else ""

    def save_document_content(self, document, content):
        return self.documents.update_content(document, content)

    def close(self):
        if self.client:
            self.client.close()

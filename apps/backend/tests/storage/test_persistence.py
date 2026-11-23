import mongomock
from latex_backend.storage.persistence import DatabaseService


def test_m5_repository_and_service_contract():
    db = DatabaseService(mongomock.MongoClient().overleaf)
    user = db.users.save(
        {"email": "a@example.org", "password_hash": "same-external-hash"}
    )
    project = db.projects.save({"owner_id": user["_id"], "name": "Project"})
    document = db.documents.save(
        {"project_id": project["_id"], "path": "main.tex", "content": "Initial"}
    )
    assert db.authenticate("a@example.org", "same-external-hash")["_id"] == user["_id"]
    assert db.authenticate("a@example.org", "wrong") is None
    assert db.projects.find_by_owner_id(str(user["_id"]))[0]["name"] == "Project"
    assert (
        db.load_initial_document_content(str(project["_id"]), "main.tex") == "Initial"
    )
    assert db.save_document_content(str(document["_id"]), "Edited")
    assert db.load_initial_document_content(str(project["_id"]), "main.tex") == "Edited"
    assert db.documents.find_by_project_id(str(project["_id"]))
    assert db.documents.find_by_id("not-an-id") is None
    assert db.documents.delete_by_id(str(document["_id"]))
    assert db.load_initial_document_content(str(project["_id"]), "main.tex") == ""

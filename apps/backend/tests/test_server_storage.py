"""Web must never interpret a browser-supplied destination as a server path."""
from io import BytesIO
import zipfile

from fastapi.testclient import TestClient

from latex_backend.http.api import create_api
from latex_backend.compilation.compiler import Compiler
from latex_backend.storage.store import Store


def browser(store):
    return TestClient(create_api(store, Compiler(store), "test-secret", web=True), base_url="http://127.0.0.1")


def test_web_storage_rejects_desktop_paths_and_survives_restart(tmp_path):
    home = tmp_path / "server-data"
    external = tmp_path / "browser-chosen-path"
    store = Store(home)
    # A setting inherited from the desktop must not redirect new web projects.
    store.state.preferences({"projectsDirectory": str(external), "latexmkPath": "old-desktop-path"})
    with browser(store) as client:
        client.post("/api/web/session")
        assert client.get("/api/health").json()["capabilities"]["storage"] == "server"
        assert client.post("/api/projects", json={"title": "Escape", "parent": str(external)}).status_code == 403
        project = client.post("/api/projects", json={"title": "Persistent web project"}).json()
        assert project["storage"] == "server" and project["folder"] == ""
        identifier = project["id"]
        assert store.root(identifier) == home / "projects" / identifier
        for name, data in (
            ("project-move", {"project": identifier, "parent": str(external)}),
            ("import-path", {"kind": "folder", "path": str(tmp_path), "title": "Leak"}),
            ("migrate-java", {"folder": str(tmp_path)}),
            ("settings", {"projectsDirectory": str(external)}),
            ("settings", {"latexmkPath": str(external / "program")}),
        ):
            assert client.post(f"/api/actions/{name}", json=data).status_code == 403
        preferences = client.get("/api/session").json()["settings"]
        assert preferences["projectsDirectory"] == preferences["latexmkPath"] == ""
        assert client.post("/api/actions/settings", json=preferences).status_code == 200
        assert client.post("/api/install", json={"template": "blank"}).status_code == 403
        assert client.put(f"/api/projects/{identifier}/file", json={"path": "main.tex", "content": "Server persisted text"}).status_code == 200
    assert not external.exists()
    with browser(Store(home)) as client:
        client.post("/api/web/session")
        assert client.get("/api/projects").json()[0]["id"] == identifier
        assert client.get(f"/api/projects/{identifier}/file", params={"path": "main.tex"}).json()["content"] == "Server persisted text"


def test_web_import_stream_uses_managed_storage_and_validates_names(tmp_path):
    store = Store(tmp_path / "data", managed=True)
    data = BytesIO()
    with zipfile.ZipFile(data, "w") as archive:
        archive.writestr("main.tex", r"\documentclass{article}\begin{document}Web import\end{document}")
        archive.writestr("chapters/one.tex", "Chapter")
    with browser(store) as client:
        client.post("/api/web/session")
        for filename in ("../../escape.zip", r"C:\escape.zip", "sub/file.zip"):
            response = client.post("/api/projects/import-file", params={"filename": filename}, content=data.getvalue())
            assert response.status_code == 400
        response = client.post("/api/projects/import-file", params={"filename": "project.zip", "parent": str(tmp_path / "escape")}, content=data.getvalue())
        assert response.status_code == 403
        response = client.post("/api/projects/import-file", params={"filename": "project.zip", "title": "Uploaded"}, content=data.getvalue())
        assert response.status_code == 200, response.text
        project = response.json()
        assert project["storage"] == "server" and not project["folder"]
        assert store.read(project["id"], "chapters/one.tex") == "Chapter"
        assert not list((store.home / ".import-tmp").iterdir())


def test_external_desktop_project_is_not_served_by_web(tmp_path):
    store = Store(tmp_path / "data")
    project = store.create("Desktop external", parent=str(tmp_path / "external"))
    with browser(store) as client:
        client.post("/api/web/session")
        assert client.get("/api/projects").json() == []
        assert client.get(f'/api/projects/{project["id"]}/file', params={"path": "main.tex"}).status_code == 409
    assert (tmp_path / "external" / project["id"] / "main.tex").is_file()


def test_web_factory_uses_operator_data_directory(tmp_path, monkeypatch):
    from latex_backend.core.config import data_directory
    from latex_backend.core.application import create_web_app
    monkeypatch.setenv("LATEX_DATA_DIR", str(tmp_path / "volume"))
    app = create_web_app(data_directory())
    assert app.state.store.managed
    assert app.state.store.home == tmp_path / "volume"
    assert app.state.compiler.store is app.state.store

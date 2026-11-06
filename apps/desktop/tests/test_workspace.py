import pytest
from fastapi.testclient import TestClient
from overleaf_desktop.api import create_api
from overleaf_desktop.compiler import Compiler, Snapshot
from overleaf_desktop.store import Problem, Store, Templates


@pytest.fixture
def store(tmp_path):
    return Store(tmp_path / "workspace")


@pytest.fixture
def api(store):
    compiler = Compiler(store)
    with TestClient(
        create_api(store, compiler, "test-session"), base_url="http://127.0.0.1"
    ) as client:
        yield client, compiler


def headers(**extra):
    return {"Authorization": "Bearer test-session", **extra}


@pytest.mark.parametrize(
    "template", [item["id"] for item in Templates().public()["templates"]]
)
@pytest.mark.parametrize("language", ["vi", "en"])
def test_every_template_materializes_all_assets_and_escaped_title(
    store, template, language
):
    project = store.create("A&B_2026", template, language)
    root = store.root(project["id"])
    metadata = store.templates.items[template]
    for path in metadata["files"]:
        assert (root / path).is_file()
        if path.endswith(".tex"):
            assert "{{TITLE}}" not in (root / path).read_text(encoding="utf-8")
        if path.endswith(".png"):
            assert (root / path).read_bytes().startswith(b"\x89PNG")
    assert project["engine"] == metadata["engine"]
    assert r"A\&B\_2026" in (root / project["main"]).read_text(encoding="utf-8")


def test_reopen_save_and_fingerprint_ignore_build(store):
    project = store.create("Luận văn")
    identifier = project["id"]
    first = store.fingerprint(identifier)
    store.add(identifier, "chapters", True)
    store.add(identifier, "chapters/new.tex", False)
    store.save(identifier, "chapters/new.tex", "Tiếng Việt: ă â đ ê ô ơ ư")
    assert first != store.fingerprint(identifier)
    second = store.fingerprint(identifier)
    (store.root(identifier) / "build").mkdir()
    (store.root(identifier) / "build/main.pdf").write_bytes(b"%PDF-test")
    assert second == store.fingerprint(identifier)
    reopened = Store(store.home)
    assert reopened.list()[0]["title"] == "Luận văn"
    assert reopened.read(identifier, "chapters/new.tex").startswith("Tiếng Việt")


@pytest.mark.parametrize(
    "name",
    [
        "../outside.tex",
        "/outside.tex",
        "C:/outside.tex",
        "a\\b.tex",
        ".project.json",
        "build/main.tex",
        "a/../../outside.tex",
    ],
)
def test_paths_cannot_escape_or_edit_internal_data(store, name):
    project = store.create("Paths")
    with pytest.raises(Problem):
        store.path(project["id"], name)


def test_auth_origin_and_host(api):
    client, _ = api
    assert client.get("/api/projects").status_code == 401
    assert (
        client.get(
            "/api/projects", headers=headers(Origin="https://evil.example")
        ).status_code
        == 403
    )
    assert (
        client.get("/api/projects", headers=headers(Host="evil.example")).status_code
        == 403
    )
    assert client.get("/api/projects", headers=headers()).status_code == 200


def test_api_create_save_and_stale_pdf_export(api, store):
    client, compiler = api
    created = client.post(
        "/api/projects", headers=headers(), json={"title": "API project"}
    )
    assert created.status_code == 201, created.text
    identifier = created.json()["id"]
    snapshot = Snapshot(
        "snapshot-one", store.fingerprint(identifier), b"%PDF-original-bytes"
    )
    compiler.snapshots[identifier] = snapshot
    path = f"/api/projects/{identifier}/pdf/{snapshot.id}"
    response = client.get(path, headers=headers())
    assert response.content == snapshot.data
    assert response.headers["cache-control"] == "no-store"
    saved = client.put(
        f"/api/projects/{identifier}/file",
        headers=headers(),
        json={"path": "main.tex", "content": "changed"},
    )
    assert saved.status_code == 200, saved.text
    assert client.get(path, headers=headers()).status_code == 409
    assert (
        client.post(
            f"/api/projects/{identifier}/file",
            headers=headers(),
            json={"path": "main.tex"},
        ).status_code
        == 409
    )


def test_compile_engine_cannot_inject_arguments(api, store):
    client, _ = api
    project = store.create("Invalid engine")
    response = client.post(
        f"/api/projects/{project['id']}/compile",
        headers=headers(),
        json={"engine": "pdflatex --shell-escape"},
    )
    assert response.status_code == 400


def test_native_bridge_exposes_only_export_and_writes_same_bytes(
    store, tmp_path, monkeypatch
):
    import sys
    from types import SimpleNamespace
    from overleaf_desktop.main import DesktopBridge

    monkeypatch.setitem(
        sys.modules, "webview", SimpleNamespace(FileDialog=SimpleNamespace(SAVE=1))
    )
    project = store.create("Native export")
    compiler = Compiler(store)
    snapshot = Snapshot(
        "native", store.fingerprint(project["id"]), b"%PDF-native-export"
    )
    compiler.snapshots[project["id"]] = snapshot
    bridge = DesktopBridge(compiler)
    target = tmp_path / "export.pdf"
    bridge._window = SimpleNamespace(
        create_file_dialog=lambda *args, **kwargs: [str(target)]
    )
    assert {name for name in dir(bridge) if not name.startswith("_")} == {
        "export_pdf",
        "export_docx",
        "pick_directory",
        "upload_files",
        "import_project",
        "import_path",
        "migrate_java",
        "profile_picture",
        "open_binary",
        "detach_pdf",
        "close_pdf_windows",
        "focus_source",
        "stage_source",
    }
    assert bridge.export_pdf(project["id"], "native") == {"saved": True}
    assert target.read_bytes() == snapshot.data


def test_native_export_rechecks_source_after_dialog(store, tmp_path, monkeypatch):
    import sys
    from types import SimpleNamespace
    from overleaf_desktop.main import DesktopBridge

    monkeypatch.setitem(
        sys.modules, "webview", SimpleNamespace(FileDialog=SimpleNamespace(SAVE=1))
    )
    project = store.create("Changed during dialog")
    compiler = Compiler(store)
    compiler.snapshots[project["id"]] = Snapshot(
        "native", store.fingerprint(project["id"]), b"%PDF-native-export"
    )
    bridge = DesktopBridge(compiler)
    target = tmp_path / "export.pdf"

    def dialog(*args, **kwargs):
        store.save(project["id"], "main.tex", "changed during dialog")
        return [str(target)]

    bridge._window = SimpleNamespace(create_file_dialog=dialog)
    assert "error" in bridge.export_pdf(project["id"], "native")
    assert not target.exists()

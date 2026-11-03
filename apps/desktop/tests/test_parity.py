"""Parity regressions for local data, transactions, wire protocol and actual TCP."""

import asyncio, base64, hashlib, json, socket, struct, threading, time, zipfile
from io import BytesIO
from pathlib import Path
import pytest
from PIL import Image
from fastapi.testclient import TestClient
from overleaf_desktop.store import Store, Problem
from overleaf_desktop.state import State
from overleaf_desktop.files import ProjectIO
from overleaf_desktop.compiler import Compiler, Snapshot
from overleaf_desktop.api import create_api
from overleaf_desktop.network import (
    Message,
    Transfer,
    frames,
    ulen,
    splice,
    apply,
    transform,
    CollaborationServer,
    NetworkSession,
    receive,
)
from overleaf_desktop.toolchain import explain, install_packages


@pytest.fixture
def store(tmp_path):
    return Store(tmp_path / "home")


def test_accounts_atomic_profile_remember_avatar_activity(tmp_path):
    state = State(tmp_path)
    state.authenticate("register", "Nguyễn An", "AN@example.org", "secret123", True)
    assert State(tmp_path).session["name"] == "Nguyễn An"
    original = json.loads(json.dumps(state.data))
    with pytest.raises(Problem):
        state.update_profile("Mutated", "new@example.org", "wrong", "different")
    assert state.session["name"] == "Nguyễn An" and state.data == original
    state.update_profile("An mới", "new@example.org", "secret123", "different")
    assert State(tmp_path).session["email"] == "new@example.org"
    output = BytesIO()
    Image.new("RGB", (600, 400), "blue").save(output, "JPEG")
    avatar = state.avatar(output.getvalue())
    assert Image.open(BytesIO(avatar)).size == (256, 256)
    assert state.avatar(remove=True) == b""
    with pytest.raises(Problem):
        state.avatar(b"Not an image")
    assert (
        state.activity("edit")["edits"] and state.activity("compile")["compiles"] == 1
    )
    state.logout()
    assert State(tmp_path).session is None
    with pytest.raises(Problem):
        state.authenticate("login", email="new@example.org", password="secret123")
    state.authenticate("login", email="new@example.org", password="different")
    state.authenticate("offline", "Local")
    with pytest.raises(Problem):
        state.update_profile("Bad", email="bad@example.org")
    assert state.session["name"] == "Local"


def test_java_accounts_settings_import(tmp_path):
    source = tmp_path / "java"
    (source / ".app").mkdir(parents=True)
    salt = b"1234567890123456"
    hashed = hashlib.pbkdf2_hmac("sha256", b"secret12", salt, 120000)
    (source / ".app/accounts.properties").write_text(
        "java@example.org=120000|"
        + base64.b64encode(salt).decode()
        + "|"
        + base64.b64encode(hashed).decode()
        + "|Nguyễn Java",
        encoding="utf8",
    )
    (source / ".app/settings.properties").write_text(
        "autoSave=true\neditorFontSize=18\naffiliation=Tr\\u01b0\\u1eddng\\ A\n",
        encoding="utf8",
    )
    state = State(tmp_path / "new")
    assert (
        state.import_java_accounts(source) == 1
        and state.import_java_accounts(source) == 0
    )
    assert (
        state.authenticate("login", email="java@example.org", password="secret12")[
            "name"
        ]
        == "Nguyễn Java"
    )
    assert (
        state.settings()["affiliation"] == "Trường A"
        and state.settings()["autoSave"] is True
    )


def test_custom_move_trash_and_history_restore(store, tmp_path):
    parent = tmp_path / "Nghiên cứu"
    store.state.preferences(
        {"projectsDirectory": str(parent), "affiliation": "Trường A"}
    )
    project = store.create("Dự án", "blank", "vi", icon="book", color="blue")
    identifier = project["id"]
    assert Path(project["folder"]).parent == parent
    original = store.read(identifier, "main.tex")
    store.save(identifier, "main.tex", "version 2")
    old = next(
        item
        for item in store.history.list(identifier)
        if item["sha256"] == hashlib.sha256(original.encode()).hexdigest()
    )
    store.history.label(identifier, old["id"], "Before edit")
    assert store.history.read(identifier, old["id"])["label"] == "Before edit"
    store.history.restore(identifier, old["id"])
    assert store.read(identifier, "main.tex") == original
    assert any(
        store.history.read(identifier, item["id"])["content"] == "version 2"
        for item in store.history.list(identifier)
    )
    moved = store.move_project(identifier, str(tmp_path / "Other"))
    assert store.read(identifier, "main.tex") == original
    assert Store(store.home).info(identifier)["folder"] == moved["folder"]
    store.update_project(identifier, "Tên mới", "pen", "purple")
    assert store.info(identifier)["title"] == "Tên mới"
    store.delete_project(identifier)
    assert store.list() == [] and list((store.home / ".trash").rglob("main.tex"))


async def stream(data):
    yield data[:2]
    yield data[2:]


def test_upload_empty_dirs_rollback_and_cancel(store):
    project = store.create("Upload")["id"]
    io = ProjectIO(store)
    manifest = [
        {"path": "figures", "directory": True, "size": 0},
        {"path": "figures/a.png", "size": 5},
        {"path": "empty", "directory": True, "size": 0},
    ]
    batch = io.begin_upload(project, manifest)
    asyncio.run(io.upload_part(project, batch, "figures/a.png", stream(b"12345")))
    assert not (store.root(project) / "figures").exists()
    assert (
        io.commit_upload(project, batch)["uploaded"] == 1
        and (store.root(project) / "empty").is_dir()
    )
    batch = io.begin_upload(project, [{"path": "new.txt", "size": 3}])
    asyncio.run(io.upload_part(project, batch, "new.txt", stream(b"new")))
    (store.root(project) / "new.txt").write_text("existing")
    with pytest.raises(Problem):
        io.commit_upload(project, batch)
    assert store.read(project, "new.txt") == "existing"
    batch = io.begin_upload(project, [{"path": "cancel.txt", "size": 3}])
    io.cancelled.set()
    with pytest.raises(Problem):
        asyncio.run(io.upload_part(project, batch, "cancel.txt", stream(b"new")))
    io.abort_upload(project, batch)
    assert not io.uploads


@pytest.mark.parametrize(
    "name",
    [
        "../escape",
        "/absolute",
        "C:/outside",
        "nested/../../bad",
        "CON.tex",
        "file.",
        "a//b",
        "a\\b",
        "NUL",
    ],
)
def test_unsafe_upload_paths(store, name):
    project = store.create("Unsafe")["id"]
    with pytest.raises(Problem):
        ProjectIO(store).begin_upload(project, [{"path": name, "size": 0}])


def test_zip_copy_and_java_migration(store, tmp_path):
    archive = tmp_path / "project.zip"
    with zipfile.ZipFile(archive, "w") as zipped:
        zipped.writestr(
            "wrapper/report.tex",
            r"\documentclass{article}\begin{document}Test\end{document}",
        )
        zipped.writestr("wrapper/chapters/one.tex", "Content")
    io = ProjectIO(store)
    project = io.import_project("zip", str(archive), "ZIP")["id"]
    assert "\\documentclass" in store.read(project, "main.tex")
    other = store.create("Other")["id"]
    io.copy_project_file(other, project, "chapters/one.tex", "sections/two.tex")
    assert store.read(other, "sections/two.tex") == "Content"
    store.rename_file(other, "sections/two.tex", "parts/test.tex")
    store.delete_file(other, "parts")
    assert list((store.root(other) / ".file-trash").rglob("test.tex"))
    with pytest.raises(Problem):
        store.delete_file(other, "main.tex")
    with pytest.raises(Problem):
        io.download(other, "http://example.com/file", "x.txt")
    old = Store(tmp_path / "JavaHome")
    previous = old.create("Original")["id"]
    old.save(previous, "main.tex", "Java content")
    migrated = io.import_java(old.home)["projects"][0]["id"]
    assert store.read(migrated, "main.tex") == "Java content" and store.history.list(
        migrated
    )
    store.save(migrated, "main.tex", "Python content")
    assert (
        old.read(previous, "main.tex") == "Java content"
        and io.import_java(old.home)["projects"] == []
    )


@pytest.mark.parametrize("name", ["../outside", "C:/bad", "/absolute", "con.tex"])
def test_zip_rejects_unsafe_without_publishing(store, tmp_path, name):
    archive = tmp_path / "bad.zip"
    with zipfile.ZipFile(archive, "w") as zipped:
        zipped.writestr(name, "Bad")
    with pytest.raises(Problem):
        ProjectIO(store).import_project("zip", str(archive), "Bad")
    assert not store.list() and not list(store.projects.glob(".import-*"))


@pytest.mark.parametrize(
    "kind",
    [
        "AUTH",
        "JOIN_ROOM",
        "TEXT_EDIT",
        "FULL_SYNC",
        "ACK",
        "ACK_BATCH",
        "RESYNC",
        "HEARTBEAT",
        "ERROR",
    ],
)
def test_wire_v1_v2_roundtrip(kind):
    message = Message(kind, "ROOM", "user", "Nguyễn", "Xin chào 🌿", 123, 456)
    raw = message.encode()
    assert (
        struct.unpack(">i", raw[:4])[0] == len(raw) - 4
        and Message.decode(raw[4:]) == message
    )
    decoded = Message.decode(struct.pack(">i", 1) + raw[8:-8])
    assert decoded.payload == message.payload and decoded.clock == 0


@pytest.mark.parametrize(
    "raw",
    [
        b"",
        b"123",
        struct.pack(">i", 9),
        struct.pack(">ii", 2, -1),
        struct.pack(">ii", 2, 100),
    ],
)
def test_invalid_frame(raw):
    with pytest.raises((ValueError, struct.error)):
        Message.decode(raw)


def test_chunked_resync_utf16_transform():
    original = Message(
        "RESYNC", "r", "u", "Name", "checkpoint\n" + ("Tiếng Việt 🌿" * 15000)
    )
    transfer = Transfer()
    for frame in frames(original):
        result = transfer.accept(Message.decode(frame[4:]))
    assert result.payload == original.payload
    with pytest.raises(ValueError):
        frames(Message("RESYNC", payload="x" * (4 * 1024 * 1024 + 1)))
    assert ulen("A🌿B") == 4 and splice("A🌿B", 1, 2, "X") == "AXB"
    one = {
        "id": "a",
        "type": "INSERT",
        "pos": 1,
        "content": "X",
        "user": "a",
        "lamport": 1,
        "ts": 1,
    }
    two = {**one, "id": "b", "user": "b", "content": "Y"}
    assert (
        apply(apply("ab", one), transform(two, one))
        == apply(apply("ab", two), transform(one, two, True))
        == "aXYb"
    )


class RunningServer:
    def __enter__(self):
        self.loop = asyncio.new_event_loop()
        self.server = CollaborationServer("test-token")
        self.thread = threading.Thread(target=self.loop.run_forever, daemon=True)
        self.thread.start()
        self.port = asyncio.run_coroutine_threadsafe(
            self.server.start("127.0.0.1", 0), self.loop
        ).result(5)
        return self

    def __exit__(self, *args):
        asyncio.run_coroutine_threadsafe(self.server.close(), self.loop).result(5)
        self.loop.call_soon_threadsafe(self.loop.stop)
        self.thread.join(5)
        self.loop.close()


def wait(predicate):
    deadline = time.monotonic() + 8
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.02)
    raise AssertionError("Timed out waiting for network state")


def test_real_tcp_presence_ack_offline_rebase_and_unknown_room(tmp_path):
    a, b = Store(tmp_path / "a"), Store(tmp_path / "b")
    pa, pb = a.create("A")["id"], b.create("B")["id"]
    a.save(pa, "main.tex", "Start")
    b.save(pb, "main.tex", "different local")
    one, two = NetworkSession(a), NetworkSession(b)
    with RunningServer() as server:
        try:
            one.connect(
                pa, port=server.port, room="test", name="An", token="test-token"
            )
            wait(lambda: not one.resyncing and not one.pending)
            two.connect(
                pb, port=server.port, room="test", name="Bình", token="test-token"
            )
            wait(lambda: not two.resyncing and two.document == "Start")
            wait(lambda: len(one.members) == 2)
            assert list(b.root(pb).glob("main.before-*.tex"))
            one.edit("Start A")
            wait(lambda: two.document == "Start A" and not one.pending)
            two.offline()
            two.edit("B Start A")
            one.edit("Start A C")
            wait(lambda: not one.pending)
            two.reconnect()
            wait(lambda: not two.resyncing and not two.pending)
            wait(lambda: one.document == two.document)
            assert "B " in two.document and " C" in two.document
            one.cursor(3, 5)
            wait(lambda: one.user in two.cursors)
            two.offline()
            one.leave()
            server.loop.call_soon_threadsafe(server.server.rooms.clear)
            time.sleep(0.1)
            two.edit(two.document + " offline")
            pending = list(two.pending)
            two.reconnect()
            wait(lambda: not two.online)
            assert list(two.pending) == pending and two.error == "ROOM_UNKNOWN"
        finally:
            one.leave()
            two.leave()


def test_tcp_bad_auth_and_invalid_frame():
    with RunningServer() as server:
        with socket.create_connection(("127.0.0.1", server.port), 2) as sock:
            sock.sendall(Message("AUTH", payload="wrong").encode())
            assert sock.recv(1) == b""
        with socket.create_connection(("127.0.0.1", server.port), 2) as sock:
            sock.sendall(Message("AUTH", payload="test-token").encode())
            assert receive(sock).payload == "AUTHENTICATED"
            sock.sendall(struct.pack(">i", 2 * 1024 * 1024))
            assert sock.recv(1) == b""


@pytest.mark.parametrize(
    "message,title",
    [
        ("Undefined control sequence", "Lệnh"),
        ("File `missing.sty' not found", "Không tìm thấy"),
        ("Missing $ inserted", "Ký hiệu"),
        ("Font fontspec not found", "Font"),
        ("Runaway argument", "ngoặc"),
        ("Environment unknown undefined", "Môi trường"),
    ],
)
def test_error_explainer_root_cause(message, title):
    issues = explain("main.tex:12: " + message + "\ncommand failed with exit code 12:")
    assert len(issues) == 1 and issues[0]["line"] == 12 and title in issues[0]["title"]
    assert explain("main.tex:12: " + message, "en")[0]["title"] != issues[0]["title"]


def test_install_consent_and_api_wiring(store):
    compiler = Compiler(store)
    with pytest.raises(Problem):
        asyncio.run(install_packages(compiler, store.templates.items["blank"], False))
    with TestClient(
        create_api(store, compiler, "test"),
        base_url="http://127.0.0.1",
        headers={"Authorization": "Bearer test"},
    ) as client:
        assert client.get("/api/session").json()["session"] is None
        assert (
            client.post("/api/actions/offline", json={"name": "Tester"}).status_code
            == 200
        )
        project = client.post("/api/projects", json={"title": "API"}).json()["id"]
        base = f"/api/projects/{project}"
        assert (
            client.get(base + "/file", params={"path": "main.tex"}).status_code == 200
        )
        assert client.put(
            base + "/file",
            json={"path": "main.tex", "content": "\\label{sec:a} Updated"},
        ).json()["saved"]
        assert len(client.get(base + "/history").json()) >= 2
        assert client.post("/api/actions/symbols", json={"project": project}).json()[
            "labels"
        ] == ["sec:a"]
        compiler.snapshots[project] = Snapshot(
            "pdf", store.fingerprint(project), b"%PDF-test"
        )
        assert (
            client.get(base + "/pdf/pdf").content
            == client.get(base + "/preview/pdf").content
        )
        client.post("/api/actions/editing", json={"project": project, "dirty": True})
        assert client.get(base + "/pdf/pdf").status_code == 409
        assert (
            client.get(base + "/preview/pdf").status_code == 200
            and client.get(base + "/watch").json()["current"] is False
        )
        assert client.get("/api/network").json()["joined"] is False


def test_native_close_stages_latest_buffer_without_javascript_deadlock(store):
    from overleaf_desktop.main import DesktopBridge

    project = store.create("Close")["id"]
    compiler = Compiler(store)
    bridge = DesktopBridge(compiler)
    bridge.stage_source(project, "main.tex", "Newest unsaved text", True, 10)
    bridge.stage_source(project, "main.tex", "Out of order stale buffer", True, 9)
    compiler.dirty.add(project)
    assert bridge._flush_source() is None
    assert (
        store.read(project, "main.tex") == "Newest unsaved text"
        and project not in compiler.dirty
    )


def test_java_explainer_context_and_launcher_summary():
    from overleaf_desktop.explainer import explain_message, rules

    assert len(rules()) == 28
    issue = explain(
        "! Undefined control sequence.\nl.42 Text \\unknownCommand\n", "en"
    )[0]
    assert issue["line"] == 42 and "\\unknownCommand" in issue["why"] and issue["known"]
    wrapper = explain_message("command failed with exit code 12:", "", "en")
    assert wrapper["known"] and "summary" in wrapper["why"]


def test_restore_refuses_to_overwrite_if_history_backup_quota_is_exhausted(
    store, monkeypatch
):
    project = store.create("Quota")["id"]
    old = store.history.capture(project, "main.tex", "Older saved revision")
    store.save(project, "main.tex", "Current content must survive")
    monkeypatch.setattr(
        store.history,
        "list",
        lambda project: [{"file": "another.tex", "sha256": "0" * 64, "size": 0}] * 1024,
    )
    with pytest.raises(Problem) as error:
        store.history.restore(project, old["id"])
    assert (
        error.value.status == 413
        and store.read(project, "main.tex") == "Current content must survive"
    )


def test_install_cancel_returns_valid_preflight_and_cleans_task(store, monkeypatch):
    import overleaf_desktop.parity_api as module

    entered, cancelled = threading.Event(), threading.Event()
    results = []

    async def fake_install(*args):
        entered.set()
        try:
            await asyncio.sleep(120)
        except asyncio.CancelledError:
            cancelled.set()
            raise

    report = {
        "missing": [{"name": "example", "package": "example"}],
        "bundled": True,
        "bin": "test",
    }
    monkeypatch.setattr(module, "install_packages", fake_install)
    monkeypatch.setattr(module, "preflight", lambda *args: report)
    app = create_api(store, Compiler(store), "test")
    with TestClient(
        app, base_url="http://127.0.0.1", headers={"Authorization": "Bearer test"}
    ) as client:
        worker = threading.Thread(
            target=lambda: results.append(
                client.post(
                    "/api/install", json={"template": "blank", "confirmed": True}
                ).json()
            )
        )
        worker.start()
        assert entered.wait(5)
        assert client.post("/api/install/cancel").json()["cancelled"]
        worker.join(5)
        assert not worker.is_alive() and cancelled.is_set()
        assert results[0]["missing"] == report["missing"] and results[0]["cancelled"]


def test_join_document_limit_preserves_local_before_network_or_snapshot(
    store, monkeypatch
):
    project = store.create("Large local source")["id"]
    source = "x" * 100001
    store.save(project, "main.tex", source)
    session = NetworkSession(store)
    monkeypatch.setattr(
        socket,
        "getaddrinfo",
        lambda *args, **kwargs: pytest.fail(
            "Oversized source must be rejected before connecting"
        ),
    )
    with pytest.raises(Problem) as error:
        session.connect(project)
    assert (
        error.value.status == 413
        and store.read(project, "main.tex") == source
        and not session.joined
    )


def test_actual_java_external_catalog_and_appearance_import(store, tmp_path):
    old = tmp_path / "old"
    (old / ".app").mkdir(parents=True)
    external = tmp_path / "External project"
    external.mkdir()
    (external / "main.tex").write_text(
        r"\documentclass{article}\begin{document}Test\end{document}", encoding="utf8"
    )
    (external / ".overleaf-project").write_text(
        "icon=book-open\ncolor=\\#123456\n", encoding="utf8"
    )
    path = str(external).replace("\\", "\\\\").replace(":", "\\:").replace(" ", "\\ ")
    (old / ".app/projects.properties").write_text(path + "=project\n", encoding="utf8")
    imported = ProjectIO(store).import_java(old)["projects"]
    assert len(imported) == 1
    assert (
        imported[0]["title"] == "External project"
        and imported[0]["icon"] == "book"
        and imported[0]["color"] == "#123456"
    )


def test_settings_validation_atomic_and_reset_retains_storage(tmp_path):
    state = State(tmp_path)
    state.preferences({"projectsDirectory": str(tmp_path / "Custom"), "theme": "mint"})
    with pytest.raises(Problem):
        state.preferences({"theme": "bee", "editorFontSize": 1000})
    assert state.settings()["theme"] == "mint"
    assert state.reset_preferences()["theme"] == "forest" and state.settings()[
        "projectsDirectory"
    ] == str(tmp_path / "Custom")

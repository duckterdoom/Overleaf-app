import pytest
from fastapi.testclient import TestClient
from latex_backend.http.api import create_api
from latex_backend.compilation.compiler import Compiler
from latex_backend.documents.ielts import IeltsManager, round_ielts
from latex_backend.storage.store import Store


@pytest.fixture
def store(tmp_path):
    return Store(tmp_path)


def test_round_ielts():
    assert round_ielts(6.0) == 6.0
    assert round_ielts(6.125) == 6.0
    assert round_ielts(6.25) == 6.5
    assert round_ielts(6.375) == 6.5
    assert round_ielts(6.625) == 6.5
    assert round_ielts(6.75) == 7.0
    assert round_ielts(6.875) == 7.0


def test_ielts_manager_persistence(store):
    mgr = IeltsManager(store)
    project = store.create("Test Candidate Project")
    data = mgr.read(project["id"])
    assert "task1" in data
    assert "task2" in data

    # Update data
    data["studentName"] = "Nguyen Van A"
    data["task1"]["scores"]["TA"] = 7.5
    saved = mgr.save(project["id"], data)
    assert saved["studentName"] == "Nguyen Van A"

    # Re-read
    loaded = mgr.read(project["id"])
    assert loaded["studentName"] == "Nguyen Van A"
    assert loaded["task1"]["scores"]["TA"] == 7.5

    # Generate Report
    report_file = mgr.generate_report_tex(project["id"], loaded)
    assert report_file == "ielts_report.tex"
    report_path = store.root(project["id"]) / report_file
    assert report_path.is_file()
    content = report_path.read_text(encoding="utf-8")
    assert "Nguyen Van A" in content
    assert "IELTS WRITING EVALUATION REPORT" in content


def test_ielts_api_endpoints(store):
    compiler = Compiler(store)
    project = store.create("IELTS Test Project")
    with TestClient(
        create_api(store, compiler, "test"),
        base_url="http://127.0.0.1",
        headers={"Authorization": "Bearer test"},
    ) as client:
        # GET
        res = client.get(f"/api/projects/{project['id']}/ielts")
        assert res.status_code == 200
        val = res.json()
        assert "task1" in val

        # POST
        val["studentName"] = "Tran Thi B"
        val["task2"]["scores"]["TR"] = 8.0
        post_res = client.post(f"/api/projects/{project['id']}/ielts", json=val)
        assert post_res.status_code == 200
        assert post_res.json()["studentName"] == "Tran Thi B"

        # REPORT
        rep_res = client.post(f"/api/projects/{project['id']}/ielts/report", json=val)
        assert rep_res.status_code == 200
        assert rep_res.json()["success"] is True
        assert rep_res.json()["file"] == "ielts_report.tex"

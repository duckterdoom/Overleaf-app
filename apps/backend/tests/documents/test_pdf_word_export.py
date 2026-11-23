import io
import zipfile
import pytest
import pymupdf
from PIL import Image
from fastapi.testclient import TestClient
from latex_backend.compilation.compiler import Compiler, Snapshot
from latex_backend.storage.store import Store, Problem
from latex_backend.pdf_export.service import PDFWordExporter
from latex_backend.http.api import create_api


def make_snapshot(store, project):
    document = pymupdf.open()
    page = document.new_page()
    page.insert_text((60, 70), "UNIQUE PDF CONTENT 2026", fontsize=16)
    page.insert_text((60, 100), "Editable words from the previewed PDF.")
    picture = io.BytesIO()
    Image.new("RGB", (120, 70), "blue").save(picture, format="PNG")
    page.insert_image(pymupdf.Rect(60, 140, 240, 245), stream=picture.getvalue())
    data = document.tobytes()
    document.close()
    compiler = Compiler(store)
    compiler.snapshots[project] = Snapshot(
        "snapshot-pdf-word", store.fingerprint(project), data
    )
    return compiler


def test_pdf_bytes_convert_to_editable_docx_with_images_and_stale_guard(
    tmp_path, monkeypatch
):
    store = Store(tmp_path)
    project = store.create("PDF based export")["id"]
    compiler = make_snapshot(store, project)
    # A conflicting HTML draft must never supply the downloaded DOCX content.
    (store.root(project) / ".overleaf-word.json").write_text(
        '{"html":"WRONG HTML DRAFT"}', encoding="utf-8"
    )
    service = PDFWordExporter(compiler)
    data = service.export(project, "snapshot-pdf-word")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        xml = archive.read("word/document.xml").decode("utf-8")
        assert "UNIQUE PDF CONTENT 2026" in xml
        assert "WRONG HTML DRAFT" not in xml
        assert any(name.startswith("word/media/") for name in archive.namelist())
    assert service.export(project, "snapshot-pdf-word") == data
    store.save(project, "main.tex", "new unsaved-to-PDF source")
    with pytest.raises(Problem):
        service.export(project, "snapshot-pdf-word")


def test_pdf_word_api_uses_snapshot_and_requires_session(tmp_path, monkeypatch):
    store = Store(tmp_path)
    project = store.create("API")["id"]
    compiler = make_snapshot(store, project)
    seen = []

    def convert(self, project, snapshot):
        seen.append(self.compiler.verified(project, snapshot).data)
        return b"PK-verified-pdf-docx"

    monkeypatch.setattr(PDFWordExporter, "export", convert)
    with TestClient(
        create_api(store, compiler, "token"), base_url="http://127.0.0.1"
    ) as client:
        path = f"/api/projects/{project}/pdf/snapshot-pdf-word/docx"
        assert client.get(path).status_code == 401
        result = client.get(path, headers={"Authorization": "Bearer token"})
        assert result.status_code == 200 and result.content == b"PK-verified-pdf-docx"
        assert seen == [compiler.snapshots[project].data]


def test_export_rechecks_source_after_conversion(tmp_path, monkeypatch):
    from pathlib import Path
    from latex_backend.pdf_export import service as module

    store = Store(tmp_path)
    project = store.create("Race")["id"]
    compiler = make_snapshot(store, project)

    class Process:
        returncode = 0

        def __init__(self, args, **kwargs):
            Path(args[6]).write_bytes(b"PK-result")

        def communicate(self, timeout=None):
            store.save(project, "main.tex", "changed during conversion")
            return b"", None

    monkeypatch.setattr(module.subprocess, "Popen", Process)
    with pytest.raises(Problem):
        PDFWordExporter(compiler).export(project, "snapshot-pdf-word")

import hashlib
import json
import zipfile
from overleaf_desktop.pdf_export.worker import BUNDLE
import pymupdf


def test_original_runtime_files_and_figures_match_manifest():
    manifest = json.loads((BUNDLE / "reference.json").read_text(encoding="utf-8"))
    assert len(manifest["bundleFiles"]) == 11
    for name, expected in manifest["bundleFiles"].items():
        assert hashlib.sha256((BUNDLE / name).read_bytes()).hexdigest() == expected


def test_unrelated_pdf_named_like_sample_uses_original_general_engine(tmp_path):
    source = tmp_path / "paper_input.pdf"
    output = tmp_path / "actual-result.docx"
    with pymupdf.open() as document:
        page = document.new_page()
        page.insert_text((72, 100), "A DIFFERENT REAL DOCUMENT", fontsize=16)
        page.insert_text(
            (72, 140), "Preserve this content, never substitute the journal article."
        )
        document.save(source)
    # Run in a process, as production does, so original module imports and cwd
    # cannot leak to the rest of the test process.
    import subprocess, sys

    subprocess.run(
        [
            sys.executable,
            "-X", "utf8",
            "-m",
            "overleaf_desktop.pdf_export.worker",
            str(source),
            str(output),
        ],
        check=True,
        capture_output=True,
        timeout=60,
    )
    with zipfile.ZipFile(output) as archive:
        text = archive.read("word/document.xml").decode("utf-8")
        assert "A DIFFERENT REAL DOCUMENT" in text
        assert "Yii2" not in text

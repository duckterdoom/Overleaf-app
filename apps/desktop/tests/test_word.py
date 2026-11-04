import io
import os
import shutil
import zipfile
import pytest
from overleaf_desktop.store import Store, Problem, REPO
from overleaf_desktop.word import Word, sanitize


def test_word_html_rejects_active_content_and_external_images():
    result = sanitize(
        '<p onclick="evil()">Hello<script>alert(1)</script><img src="file:///secret"><img src="https://example.com/x.png"><a href="javascript:evil()">x</a></p>'
    )
    assert "onclick" not in result and "alert" not in result
    assert (
        "file:" not in result
        and "https://example" not in result
        and "javascript:" not in result
    )
    assert "Hello" in result
    dimensions = sanitize(
        '<img style="width:76.5mm;background:url(file:///secret);height:20px" src="https://bad/image.png">'
    )
    assert "width:76.5mm" in dimensions and "height:20px" in dimensions
    assert (
        "url(" not in dimensions
        and "file:" not in dimensions
        and "https:" not in dimensions
    )


def test_word_round_trip_edit_and_export(tmp_path):
    if not (
        os.getenv("OVERLEAF_PANDOC")
        or shutil.which("pandoc")
        or (REPO / "target/pandoc/pandoc.exe").is_file()
    ):
        pytest.skip("Requires native Pandoc")
    store = Store(tmp_path)
    project = store.create("Word")["id"]
    root = store.root(project)
    from PIL import Image

    Image.new("RGB", (10, 10), "blue").save(root / "figure.png")
    (root / "main.tex").write_text(
        "\\documentclass{article}\n\\title{Tiếng Việt}\n\\begin{document}\n\\maketitle\n\\section{Introduction}\nHello \\textbf{world}. $x^2$\n\\input{body}\n\\end{document}",
        encoding="utf-8",
    )
    (root / "body.tex").write_text("Body with \\emph{italic}.", encoding="utf-8")
    (root / "body.tex").write_text(
        "Body with \\emph{italic}. \\includegraphics{figure.png}", encoding="utf-8"
    )
    before = store.fingerprint(project)
    service = Word(store)
    draft = service.generate(project, None)
    assert (
        "Hello" in draft["html"]
        and "Body" in draft["html"]
        and "<math" in draft["html"]
    )
    with pytest.raises(Problem):
        service.save(project, {"revision": "stale", "html": "bad"})
    saved = service.save(
        project,
        {
            "revision": draft["revision"],
            "html": draft["html"].replace(
                ">Hello</span>", ">Edited Word tiếng Việt</span>"
            ),
        },
    )
    assert store.fingerprint(project) != before
    assert "Edited Word tiếng Việt" in (root / "main.tex").read_text(encoding="utf-8")
    assert "\\documentclass{article}" in (root / "main.tex").read_text(encoding="utf-8")
    assert "\\input{body}" in (root / "main.tex").read_text(encoding="utf-8")
    assert Word(Store(tmp_path)).read(project)["revision"] == saved["revision"]
    data = service.export(project, saved["revision"])
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        xml = archive.read("word/document.xml").decode("utf-8")
        assert "Edited Word tiếng Việt" in xml and "Introduction" in xml
        assert "oMath" in xml
        assert any(name.startswith("word/media/") for name in archive.namelist())
    with pytest.raises(Problem):
        service.export(project, draft["revision"])
    with pytest.raises(Problem):
        service.save(
            project,
            {"revision": saved["revision"], "html": "<p>Replaced entire document</p>"},
        )
    (root / "body.tex").write_text("Changed source", encoding="utf-8")
    assert service.read(project)["stale"]
    (root / "main.tex").write_text("\\input{../secret}", encoding="utf-8")
    with pytest.raises(Problem):
        service.generate(project, saved["revision"])


def test_native_docx_export_rechecks_revision_after_save_dialog(tmp_path, monkeypatch):
    import sys
    from types import SimpleNamespace
    from overleaf_desktop.main import DesktopBridge
    from overleaf_desktop.compiler import Compiler

    store = Store(tmp_path / "home")
    project = store.create("Native Word")["id"]
    service = Word(store)
    draft = service.write(
        project,
        {
            "html": "<p>Word draft</p>",
            "fingerprint": store.fingerprint(project),
            "warnings": [],
        },
    )
    bridge = DesktopBridge(Compiler(store))
    monkeypatch.setitem(
        sys.modules, "webview", SimpleNamespace(FileDialog=SimpleNamespace(SAVE=1))
    )
    monkeypatch.setattr(Word, "export", lambda self, project, revision: b"PK-test-docx")
    target = tmp_path / "out.docx"
    bridge._window = SimpleNamespace(create_file_dialog=lambda *a, **kw: [str(target)])
    assert bridge.export_docx(project, draft["revision"]) == {"saved": True}
    assert target.read_bytes() == b"PK-test-docx"
    target.unlink()

    def changed(*args, **kwargs):
        service.save(
            project, {"revision": draft["revision"], "html": "<p>Newer edit</p>"}
        )
        return [str(target)]

    bridge._window = SimpleNamespace(create_file_dialog=changed)
    assert "error" in bridge.export_docx(project, draft["revision"])
    assert not target.exists()


def test_mapped_sync_preserves_custom_latex_and_conflicts(tmp_path):
    if not (shutil.which("pandoc") or (REPO / "target/pandoc/pandoc.exe").is_file()):
        pytest.skip("Requires Pandoc")
    import xml.etree.ElementTree as ET

    store = Store(tmp_path)
    project = store.create("Sync")["id"]
    root = store.root(project)
    preamble = "\\documentclass[twocolumn]{article}\n\\newcommand{\\custom}{Keep custom}\n\\title{Full width article title}\n"
    (root / "main.tex").write_text(
        preamble
        + "\\begin{document}\n\\maketitle\n\\section{Section title}\\label{sec:one}\n\\input{body}\n$x^2$\\end{document}",
        encoding="utf-8",
    )
    (root / "body.tex").write_text("Editable paragraph text here.", encoding="utf-8")
    service = Word(store)
    first = service.generate(project, None)
    assert first["layout"]["columns"] == 2
    changed = first["html"].replace(
        ">Editable paragraph text here.</span>", ">Changed 100% &amp; safe.</span>"
    )
    second = service.save(project, {"revision": first["revision"], "html": changed})
    assert (root / "body.tex").read_text(encoding="utf-8") == r"Changed 100\% \& safe."
    assert (root / "main.tex").read_text(encoding="utf-8").startswith(preamble)
    assert "\\label{sec:one}" in (root / "main.tex").read_text(encoding="utf-8")
    third = service.save(
        project,
        {
            "revision": second["revision"],
            "html": second["html"].replace(
                ">Changed 100% &amp; safe.</span>", ">Second edit.</span>"
            ),
        },
    )
    assert (root / "body.tex").read_text(encoding="utf-8") == "Second edit."
    with zipfile.ZipFile(
        io.BytesIO(service.export(project, third["revision"]))
    ) as archive:
        document = ET.fromstring(archive.read("word/document.xml"))
        namespace = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
        assert [
            item.get(namespace + "num")
            for item in document.findall(".//" + namespace + "cols")
        ] == ["1", "2"]
        assert "Full width article title" in "".join(document.itertext())
        assert "OVERLEAFWORDSECTION" not in "".join(document.itertext())
    (root / "main.tex").write_text(
        (root / "main.tex").read_text(encoding="utf-8") + "\n% external edit",
        encoding="utf-8",
    )
    with pytest.raises(Problem):
        service.save(
            project,
            {
                "revision": third["revision"],
                "html": third["html"].replace(
                    ">Second edit.</span>", ">Conflict edit.</span>"
                ),
            },
        )
    assert "Conflict edit." in (root / ".overleaf-word-conflict.html").read_text(
        encoding="utf-8"
    )
    assert (root / "body.tex").read_text(encoding="utf-8") == "Second edit."


def test_framed_figures_use_class_graphicspath_dimensions_and_fonts(tmp_path):
    if not (shutil.which("pandoc") or (REPO / "target/pandoc/pandoc.exe").is_file()):
        pytest.skip("Requires Pandoc")
    from PIL import Image
    import xml.etree.ElementTree as ET

    store = Store(tmp_path)
    project = store.create("Journal Figures")["id"]
    root = store.root(project)
    (root / "Figures").mkdir()
    Image.new("RGB", (120, 90), "red").save(root / "Figures/owasp.png")
    Image.new("RGB", (100, 50), "blue").save(root / "Figures/overview.png")
    (root / "DemoJournal.cls").write_text(
        r"\LoadClass[twocolumn]{article}\usepackage{mathpazo}\graphicspath{{Figures/}{./}}",
        encoding="utf-8",
    )
    (root / "main.tex").write_text(
        r"""\documentclass[12pt]{DemoJournal}
\begin{document}
\section{Analysis of Yii2}
\begin{figure}\centering\fbox{\includegraphics[width=0.45\textwidth]{"owasp.png"}}
\caption{OWASP mapping}\end{figure}
\begin{figure}\framebox{\includegraphics[width=\linewidth]{overview}}
\caption{System overview}\end{figure}
\end{document}""",
        encoding="utf-8",
    )
    before = store.fingerprint(project)
    service = Word(store)
    draft = service.generate(project, None)
    assert draft["imageCount"] == 2 and draft["html"].count("<img ") == 2
    assert "width:76.5mm" in draft["html"]
    assert "Figure 1:" in draft["html"] and "Figure 2:" in draft["html"]
    assert draft["layout"]["fontFamily"] == "Palatino Linotype"
    assert draft["layout"]["fontSize"] == 12
    assert not draft["needsRefresh"]
    with zipfile.ZipFile(
        io.BytesIO(service.export(project, draft["revision"]))
    ) as archive:
        media = [name for name in archive.namelist() if name.startswith("word/media/")]
        assert len(media) == 2
        document = archive.read("word/document.xml").decode("utf-8")
        ET.fromstring(document)
        assert document.count("<w:drawing>") == 2
        assert "data:image/" not in document
        assert "OWASP mapping" in document and "System overview" in document
        styles = archive.read("word/styles.xml").decode("utf-8")
        ET.fromstring(styles)
        assert "Palatino Linotype" in styles and 'w:sz w:val="24"' in styles
    assert store.fingerprint(project) == before

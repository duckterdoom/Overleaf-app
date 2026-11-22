"""Browser-only PDF fixture compiler; conversion, DOCX edits and OT use real services."""
import asyncio
import io
import os
import sys
from pathlib import Path
import re
import uuid
from types import SimpleNamespace
import mongomock
import pymupdf
from PIL import Image
from latex_backend.core.application import create_web_app
from latex_backend.compilation.compiler import Compiler, Snapshot
from latex_backend.entrypoints import web


def fixture_start(self, project, engine, main='', **kwargs):
    main = main or self.store.info(project)['main']
    source = self.store.read(project, main)
    match = re.search(r'Hello [^\n]+world\.', source)
    phrase = match[0] if match else self.store.info(project)['title']
    pdf = pymupdf.open()
    page = pdf.new_page()
    page.insert_text((65,80), phrase, fontsize=14)
    page.insert_text((65,120), 'Another unique paragraph remains intact.', fontsize=12)
    picture = io.BytesIO()
    Image.new('RGB', (100,50), 'blue').save(picture, format='PNG')
    page.insert_image(pymupdf.Rect(65,160,265,260), stream=picture.getvalue())
    page = pdf.new_page()
    page.insert_text((65,80), 'Final page keeps its own text.', fontsize=12)
    data = Path(os.environ['LATEX_TEST_PDF_SAMPLE']).read_bytes() if os.getenv('LATEX_TEST_PDF_SAMPLE') else pdf.tobytes()
    snapshot = Snapshot(uuid.uuid4().hex, self.store.fingerprint(project), data, main=main)
    pdf.close()
    self.snapshots[project] = snapshot
    self.dirty.discard(project)
    queue = asyncio.Queue()
    queue.put_nowait({'type':'done', 'snapshot':snapshot.id, 'current':True, 'duration_ms':1})
    queue.put_nowait({'type':'end'})
    return SimpleNamespace(queue=queue, task=None)

Compiler.start = fixture_start
client = mongomock.MongoClient(tz_aware=True)
web.create_web_app = lambda home, frontend, latexmk: create_web_app(home, frontend, latexmk, database=client.docx_browser_test)
# This fixture replaces compilation with generated PDFs; it must not depend on
# a developer's installed TeX distribution. Production startup keeps its checks.
sys.argv.append('--skip-tex-check')
web.main()

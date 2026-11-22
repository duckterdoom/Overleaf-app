"""Opt-in integration check requiring an installed TeX toolchain. No production data."""

import asyncio,io,zipfile
from pathlib import Path
import pymupdf
from latex_backend.storage.store import Store
from latex_backend.compilation.compiler import Compiler
from latex_backend.documents.docx_workspace import DocxWorkspace

async def build(compiler,project):
    job=compiler.start(project,'pdflatex')
    result=None
    while True:
        event=await job.queue.get()
        if event['type']=='error':raise RuntimeError(event.get('message'))
        if event['type']=='done':result=event['snapshot']
        if event['type']=='end':break
    if job.task:await job.task
    assert result
    return result

async def main():
    import argparse
    parser=argparse.ArgumentParser(description='Real LaTeX/PDF/DOCX/source round trip in a fresh test home')
    parser.add_argument('--home',required=True)
    parser.add_argument('--latexmk',default='')
    args=parser.parse_args()
    home=Path(args.home)
    if home.exists():raise RuntimeError('Fresh test home required')
    store=Store(home)
    project=store.create('Real Word roundtrip')['id']
    source=r'\documentclass{article}\begin{document}Hello original world. Another unique paragraph remains intact.\end{document}'
    store.save(project,'main.tex',source)
    compiler=Compiler(store,args.latexmk)
    snap=await build(compiler,project)
    workspace=DocxWorkspace(compiler)
    meta=workspace.open(project,snap)
    original=workspace.read(project,meta['revision'])
    out=io.BytesIO()
    edited=False
    with zipfile.ZipFile(io.BytesIO(original)) as zin,zipfile.ZipFile(out,'w') as zout:
        for entry in zin.infolist():
            data=zin.read(entry.filename)
            if entry.filename=='word/document.xml':
                assert b'original' in data
                data=data.replace(b'original',b'revised',1);edited=True
            zout.writestr(entry,data)
    assert edited
    saved=workspace.save(project,meta['revision'],out.getvalue())
    assert saved['sync_state']=='synced'
    assert store.read(project,'main.tex')==source.replace('original','revised')
    new=await build(compiler,project)
    with pymupdf.open(stream=compiler.preview(project,new).data,filetype='pdf') as pdf:
        text=''.join(page.get_text() for page in pdf)
        assert 'Hello revised world.' in text and 'Hello original world.' not in text
    print('REAL_LATEX_PDF_DOCX_SOURCE_PDF_ROUNDTRIP_OK')
asyncio.run(main())

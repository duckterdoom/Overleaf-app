"""Standalone real TeX/original-converter benchmark; use a NEW test directory."""
import argparse
import asyncio
import json
import statistics
import time
from pathlib import Path
from PIL import Image
from latex_backend.storage.store import Store, Problem
from latex_backend.compilation.compiler import Compiler
from latex_backend.pdf_export.service import PDFWordExporter


async def benchmark(home):
    if home.exists():
        raise RuntimeError('Use a fresh benchmark home; no real projects are modified.')
    store=Store(home)
    project=store.create('Compile and Word benchmark')['id']
    root=store.root(project)
    Image.new('RGB',(300,120),'#34775d').save(root/'figure.png')
    source=r'''\documentclass{article}
\usepackage{graphicx}
\begin{document}
\section{Compile benchmark}
Verified source content, mathematics $E=mc^2$ and a real image.
\includegraphics[width=8cm]{figure.png}
\begin{tabular}{ll}Item & Result \\ Source & Preserved \\ Image & Included\end{tabular}
\newpage
\section{Second page}
''' + ((r'A paragraph of actual editable source text with enough content to verify conversion.\par' + '\n') * 30) + r'''
\newpage
\section{Third page}
The original PDF-to-Word engine must preserve these words and the embedded image.
\end{document}
'''
    store.save(project,'main.tex',source)
    compiler=Compiler(store)
    async def build(force=False):
        start=time.perf_counter()
        job=compiler.start(project,'pdflatex',force=force)
        events=[]
        while True:
            event=await job.queue.get(); events.append(event)
            if event['type']=='end': break
        result=next((event for event in events if event['type']=='done'),None)
        if result is None:
            raise RuntimeError(json.dumps(events,ensure_ascii=False)[-6000:])
        return {**result,'wall_ms':round((time.perf_counter()-start)*1000)}
    cold=await build(True)
    repeated=[await build() for _ in range(3)]
    assert all(r.get('cached') and r['snapshot']==cold['snapshot'] for r in repeated)
    compiler.documents.clear()
    latexmk_noop=await build()  # Existing path: start latexmk even though unchanged.
    store.save(project,'main.tex',source.replace('Verified source content','Updated verified source content'))
    edited=await build()
    assert not edited.get('cached')
    service=PDFWordExporter(compiler)
    def timed(service):
        start=time.perf_counter(); data=service.export(project,edited['snapshot'])
        return round((time.perf_counter()-start)*1000),data
    first,data=await asyncio.to_thread(timed,service)
    ram,second=await asyncio.to_thread(timed,service)
    disk,third=await asyncio.to_thread(timed,PDFWordExporter(compiler))
    assert data==second==third
    store.save(project,'main.tex','Source changed after PDF generation')
    try:
        service.export(project,edited['snapshot'])
        raise AssertionError('Stale cached export was allowed')
    except Problem as error:
        assert error.status==409
    report={'fixture':'multi-page text + math + table + image; Windows local Store, real latexmk and original converter',
        'compile_cold_ms':cold['wall_ms'],'compile_unchanged_ms':[r['wall_ms'] for r in repeated],
        'compile_unchanged_median_ms':statistics.median(r['wall_ms'] for r in repeated),
        'compile_latexmk_noop_ms':latexmk_noop['wall_ms'],
        'compile_after_source_edit_ms':edited['wall_ms'],'pdf_pages':len(edited['geometry']),
        'word_first_conversion_ms':first,'word_ram_cache_ms':ram,'word_persistent_cache_ms':disk,
        'docx_bytes':len(data),'cached_docx_identical_bytes':True,'stale_export_blocked':True}
    (home/'benchmark.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report))


if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('home',type=Path)
    asyncio.run(benchmark(parser.parse_args().home.resolve()))

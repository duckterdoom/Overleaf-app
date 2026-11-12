"""Compile every IELTS template plus every assignable question in en/vi.

Uses a fresh output directory, real TeX and no user accounts/database.
"""
import concurrent.futures
import json
import os
from pathlib import Path
import re
import subprocess
import sys

import pymupdf
from latex_backend.compilation.compiler import find_latexmk
from latex_backend.compilation.tex_runtime import tex_environment
from latex_backend.storage.store import Templates


def main():
    home = Path(sys.argv[1]).resolve()
    home.mkdir(parents=True, exist_ok=False)
    templates = Templates()
    executable = find_latexmk()
    jobs = []
    for identifier, item in templates.items.items():
        if item['category'] != 'ielts':
            continue
        for lang in ('en', 'vi'):
            folder = home / identifier / lang
            folder.mkdir(parents=True)
            templates.write(folder, identifier, 'IELTS practice / Đề luyện', lang)
            jobs.append((folder, 'main.tex', 'pdflatex'))
            if identifier == 'ielts-complete-bank':
                bank = json.loads((folder / 'ielts-bank.json').read_text(encoding='utf-8'))
                jobs.extend((folder, p['questionFile'], 'pdflatex') for p in bank['profiles'])
                jobs.append((folder, 'answers.tex', 'pdflatex'))
            if identifier == 'ielts-academic-mock':
                jobs.extend((folder, 'main.tex', engine) for engine in ('xelatex', 'lualatex'))

    def build(job):
        folder, source, engine = job
        out = 'build-' + Path(source).stem + '-' + engine
        result = subprocess.run([executable, {'pdflatex':'-pdf','xelatex':'-xelatex','lualatex':'-lualatex'}[engine],
            '-interaction=nonstopmode', '-halt-on-error', '-no-shell-escape', '-outdir=' + out, source],
            cwd=folder, env=tex_environment(executable), capture_output=True, timeout=180,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        log = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        (folder / (out + '.txt')).write_text(log, encoding='utf-8')
        pdf = folder / out / (Path(source).stem + '.pdf')
        if result.returncode or not pdf.is_file():
            raise AssertionError(str(folder.relative_to(home)) + '/' + source + '\n' + log[-3500:])
        with pymupdf.open(pdf) as doc:
            text = '\n'.join(p.get_text() for p in doc)
            if source != 'answers.tex':
                assert 'WRITING TASK' in text, str(pdf)
            pages = len(doc)
            if folder.parent.name.startswith('ielts-task1-') and folder.name == 'en' and source == 'main.tex':
                doc[0].get_pixmap(matrix=pymupdf.Matrix(1.2,1.2)).save(folder / 'preview.png')
        warnings = sorted(set(re.findall(r'Overfull \\[hv]box.*', log)))
        print('PDF OK', folder.relative_to(home), source, engine, pages, flush=True)
        return {'folder':str(folder.relative_to(home)), 'source':source, 'engine':engine, 'pages':pages, 'overfull':warnings}

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(build, jobs))
    (home / 'report.json').write_text(json.dumps({'passed':True, 'compiles':len(results), 'results':results}, ensure_ascii=False, indent=2), encoding='utf-8')
    print('IELTS_TEMPLATE_MATRIX_OK', len(results), flush=True)


if __name__ == '__main__':
    main()

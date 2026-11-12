"""Operator/dev TeX provisioning. Never called by a browser request."""
import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tarfile
import tempfile
import time
import urllib.request
import uuid
import zipfile
from xml.sax.saxutils import escape

from latex_backend.core.config import REPO, RESOURCES
from latex_backend.compilation.toolchain import ALIASES, preflight, run

MANIFEST = RESOURCES / 'tex-runtime.json'
MANAGED = REPO / 'target/tex-runtime/TinyTeX'
TOOLS = ('latexmk', 'pdflatex', 'xelatex', 'lualatex', 'biber', 'synctex', 'kpsewhich')


def manifest():
    return json.loads(MANIFEST.read_text(encoding='utf-8'))


def bin_directory(root):
    suffix = '.exe' if os.name == 'nt' else ''
    return next((p.parent for p in sorted((root / 'bin').glob('*/latexmk' + suffix)) if p.is_file()), None)


def managed_latexmk():
    folder = bin_directory(MANAGED)
    return str(folder / ('latexmk.exe' if os.name == 'nt' else 'latexmk')) if folder else None


def requirements():
    items = [json.loads(p.read_text(encoding='utf-8')) for p in (RESOURCES / 'templates').rglob('template.json')]
    return {'requiredPackages': sorted({p for item in items for p in item.get('requiredPackages', [])}),
            'needsBiber': True, 'engine': 'pdflatex'}, len(items)


def signature():
    return hashlib.sha256(MANIFEST.read_bytes() + b'fontconfig-v2' + str(MANAGED.resolve()).encode()).hexdigest()


def check(executable, require_verified=True):
    template, count = requirements()
    report = preflight(executable, template)
    folder = Path(report['bin'])
    suffix = '.exe' if os.name == 'nt' else ''
    missing = report['missing']
    for tool in TOOLS:
        if not (folder / (tool + suffix)).is_file() and not any(m['name'] == tool for m in missing):
            missing.append({'name': tool, 'package': tool, 'probe': tool})
    if (folder / ('kpsewhich' + suffix)).is_file():
        # One kpsewhich process for optional package/font probes, not one per file.
        probes = manifest()['probes']
        result = run([folder / ('kpsewhich' + suffix), *probes])
        found = {Path(line.replace('\\', '/')).name for line in result.stdout.decode(errors='replace').splitlines()} if result else set()
        missing += [{'name': p, 'package': '', 'probe': p} for p in probes if p not in found]
    if require_verified and folder.parent.parent.resolve() == MANAGED.resolve():
        try:
            verified = json.loads((MANAGED / '.latex-ready.json').read_text())['signature'] == signature()
        except (OSError, ValueError, KeyError):
            verified = False
        if not verified:
            missing.append({'name': 'runtime-validation', 'package': '', 'probe': 'three-engine PDF smoke check'})
    return {**report, 'ready': not missing, 'templates': count, 'profile': 'catalog-and-common-documents'}


def archive_for(system=None, machine=None):
    system, machine = (system or platform.system()).lower(), (machine or platform.machine()).lower()
    arch = {'amd64': 'amd64' if system == 'windows' else 'x86_64', 'x86_64': 'amd64' if system == 'windows' else 'x86_64',
            'aarch64': 'arm64', 'arm64': 'arm64'}.get(machine, machine)
    key = 'darwin' if system == 'darwin' else system + '-' + arch
    entry = manifest()['archives'].get(key)
    if not entry:
        raise RuntimeError(f'No managed TinyTeX archive for {system}/{machine}. Install a native TeX distribution and use --latexmk.')
    return entry


def download(entry, destination):
    url = f"https://github.com/rstudio/tinytex-releases/releases/download/v{manifest()['version']}/{entry['name']}"
    print('Downloading official TinyTeX ' + entry['name'] + ' (first setup only)...', flush=True)
    digest = hashlib.sha256()
    with urllib.request.urlopen(url, timeout=60) as source, destination.open('wb') as output:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk); output.write(chunk)
    if digest.hexdigest() != entry['sha256']:
        destination.unlink(missing_ok=True)
        raise RuntimeError('TinyTeX SHA-256 verification failed; archive was not executed or installed.')


def extract(archive, destination):
    def validate(names, total):
        if total > 4 * 1024 ** 3 or len(names) > 150000:
            raise RuntimeError('TinyTeX archive exceeds extraction limits.')
        for name in names:
            relative = Path(name.replace('\\', '/'))
            if relative.is_absolute() or '..' in relative.parts or ':' in name:
                raise RuntimeError('Unsafe path in TinyTeX archive.')
    if zipfile.is_zipfile(archive):
        with zipfile.ZipFile(archive) as stream:
            validate(stream.namelist(), sum(i.file_size for i in stream.infolist()))
            stream.extractall(destination)
    else:
        with tarfile.open(archive) as stream:
            entries = stream.getmembers()
            validate([i.name for i in entries], sum(i.size for i in entries))
            stream.extractall(destination, filter='data')


@contextmanager
def setup_lock(parent):
    parent.mkdir(parents=True, exist_ok=True)
    path = parent / 'setup.lock'
    try:
        descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError:
        raise RuntimeError('Another TeX setup is running. Wait for it; if it crashed, remove target/tex-runtime/setup.lock after confirming no setup is running.') from None
    try:
        os.write(descriptor, str(os.getpid()).encode()); os.close(descriptor)
        yield
    finally:
        path.unlink(missing_ok=True)


def install_distribution(root):
    if root.exists():
        if not bin_directory(root):
            raise RuntimeError(f'Incomplete managed TeX at {root}. Keep it for inspection; rename it before retrying setup.')
        return
    entry = archive_for()
    stage = root.parent / ('.prepare-' + uuid.uuid4().hex)
    stage.mkdir()
    archive = stage / entry['name']
    download(entry, archive)
    extract(archive, stage / 'unpacked')
    candidates = [p for p in (stage / 'unpacked').iterdir() if p.is_dir() and bin_directory(p)]
    if len(candidates) != 1:
        raise RuntimeError(f'Unexpected TinyTeX archive layout. Staging retained at {stage}.')
    candidates[0].rename(root)
    archive.unlink()
    # Only discard the newly created staging directory, never an existing TeX install.
    if stage.resolve().parent != root.parent.resolve() or not stage.name.startswith('.prepare-'):
        raise RuntimeError('Unexpected staging path.')
    shutil.rmtree(stage)


def provision(root, full=False):
    folder = bin_directory(root)
    if folder is None:
        raise RuntimeError('Managed TinyTeX binaries are missing.')
    packages = set(manifest()['packages'])
    template, _ = requirements()
    packages.update(ALIASES.get(p, ('', p))[1] for p in template['requiredPackages'])
    if full:
        packages.add('scheme-full')
    if not all(re.fullmatch('[a-zA-Z0-9_-]+', p) for p in packages):
        raise RuntimeError('Invalid package name in runtime manifest.')
    env = os.environ.copy()
    env['PATH'] = str(folder) + os.pathsep + env.get('PATH', '')
    env['LC_ALL'] = 'C'
    if os.name == 'nt':
        env['PERL5LIB'] = str(root / 'tlpkg/tlperl/lib')
        # The official batch watchdog completes the infrastructure self-update
        # after Perl exits. Invoking tlmgr.pl directly leaves that update pending.
        manager = [str(folder / 'tlmgr.bat')]
    else:
        manager = [str(folder / 'tlmgr')]
    repository = os.getenv('LATEX_TEX_REPOSITORY', manifest()['repository'])
    if not re.fullmatch(r'https://[A-Za-z0-9_.:/-]+', repository):
        raise RuntimeError('TeX repository must be a plain HTTPS URL without credentials/query/shell characters.')
    print('Preparing managed TeX packages. This can take several minutes on a fresh machine.', flush=True)
    for arguments in (['update', '--self'], ['install', *sorted(packages)]):
        result = subprocess.run([*manager, '--repository', repository, *arguments], env=env, timeout=1800, capture_output=True,
                                creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        output = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        if output:
            print(output[-16000:], flush=True)
        if result.returncode or re.search(r'not present in repository|action install returned an error|An error has occurred', output):
            raise RuntimeError(f'TeX package preparation failed (exit {result.returncode}). See tlmgr output above. Check network and TeX Live repository year; do not mix yearly releases.')


def repair_managed_fonts(root):
    # Windows archives contain the build machine's absolute font directories.
    # Configure only this private distribution, without installing system fonts.
    directories = [root / 'texmf-dist/fonts/opentype', root / 'texmf-dist/fonts/truetype']
    directories += ([Path(os.environ.get('WINDIR', 'C:/Windows')) / 'Fonts'] if os.name == 'nt' else
                    [Path('/usr/share/fonts'), Path('/usr/local/share/fonts'), Path('/Library/Fonts'), Path('/System/Library/Fonts')])
    cache = root / 'texmf-var/fonts/cache'
    cache.mkdir(parents=True, exist_ok=True)
    config = root / 'texmf-var/fonts/conf/fonts.conf'
    config.parent.mkdir(parents=True, exist_ok=True)
    content = '<?xml version="1.0"?><fontconfig>' + ''.join('<dir>' + escape(p.as_posix()) + '</dir>' for p in directories)
    content += '<cachedir>' + escape(cache.as_posix()) + '</cachedir></fontconfig>'
    config.write_text(content, encoding='utf-8')
    (root / '.latex-fontconfig.conf').write_text(content, encoding='utf-8')


def tex_environment(executable):
    folder = Path(executable).absolute().parent
    env = os.environ.copy()
    env['PATH'] = str(folder) + os.pathsep + env.get('PATH', '')
    config = folder.parent.parent / '.latex-fontconfig.conf'
    if config.is_file():
        env['FONTCONFIG_FILE'] = str(config)
    return env


def smoke(executable):
    folder = Path(executable).parent
    suffix = '.exe' if os.name == 'nt' else ''
    text = r'''\documentclass{article}
\usepackage{iftex}
\ifPDFTeX\usepackage[utf8]{inputenc}\usepackage[T5]{fontenc}\usepackage{lmodern}
\else\usepackage{fontspec}\setmainfont{TeX Gyre Termes}\fi
\usepackage[vietnamese]{babel}\usepackage{amsmath,amssymb,graphicx,colortbl,booktabs,footmisc}
\begin{document}Tiếng Việt: kiểm tra biên dịch. $E=mc^2$.
\begin{tabular}{ll}\toprule A&B\\\bottomrule\end{tabular}\end{document}'''
    with tempfile.TemporaryDirectory(prefix='latex-runtime-check-') as temp:
        path = Path(temp)
        (path / 'main.tex').write_text(text, encoding='utf-8')
        for engine in ('pdflatex', 'xelatex', 'lualatex'):
            result = subprocess.run([str(folder / (engine + suffix)), '-interaction=nonstopmode', '-halt-on-error', '-no-shell-escape',
                                     '-output-directory=' + temp, str(path / 'main.tex')], capture_output=True,
                                    env=tex_environment(executable), timeout=120,
                                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
            if result.returncode or not (path / 'main.pdf').is_file():
                raise RuntimeError(engine + ' smoke compile failed:\n' + (result.stdout + result.stderr).decode(errors='replace')[-4000:])
            print(engine + ': Vietnamese PDF compile OK', flush=True)
            (path / 'main.pdf').unlink()


def smoke_catalog(executable):
    from latex_backend.storage.store import Templates
    templates = Templates()
    with tempfile.TemporaryDirectory(prefix='latex-catalog-check-') as temp:
        for key, item in templates.items.items():
            for language in ('en', 'vi'):
                folder = Path(temp) / key / language
                folder.mkdir(parents=True)
                templates.write(folder, key, 'Template check', language)
                # Match Compiler's preparation for \include{chapters/...} aux files.
                for relative in {p.parent.relative_to(folder) for p in folder.rglob('*.tex')}:
                    (folder / 'build' / relative).mkdir(parents=True, exist_ok=True)
                engine = {'pdflatex': '-pdf', 'xelatex': '-xelatex', 'lualatex': '-lualatex'}[item['engine']]
                process = subprocess.run([str(executable), engine, '-interaction=nonstopmode', '-halt-on-error', '-no-shell-escape',
                    '-outdir=build', item['mainFile']], cwd=folder, capture_output=True, timeout=180,
                    env={**tex_environment(executable), 'LC_ALL': 'C'},
                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
                pdf = folder / 'build' / (Path(item['mainFile']).stem + '.pdf')
                if process.returncode or not pdf.is_file():
                    raise RuntimeError(f'Catalog compile failed: {key}/{language}\n' + (process.stdout + process.stderr).decode(errors='replace')[-5000:])
                print(f'Catalog {key}/{language}: PDF OK', flush=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description='Prepare server-side TeX for Latex (operator/dev only).')
    parser.add_argument('--ensure', action='store_true', help='Install missing runtime/packages automatically in the managed folder.')
    parser.add_argument('--check', action='store_true', help='Read-only readiness check; exit 1 if not ready.')
    parser.add_argument('--managed', action='store_true', help='Prepare managed TeX even if a system distribution is ready.')
    parser.add_argument('--full', action='store_true', help='Install scheme-full; large download for server images.')
    parser.add_argument('--smoke', action='store_true', help='Compile Vietnamese documents with all three engines.')
    parser.add_argument('--smoke-catalog', action='store_true', help='Compile every built-in template in English and Vietnamese (CI/operator).')
    parser.add_argument('--latexmk', default='')
    args = parser.parse_args(argv)
    from latex_backend.compilation.compiler import find_latexmk
    executable = find_latexmk(args.latexmk)
    prepared = False
    try:
        report = check(executable)
        explicit = args.latexmk or os.getenv('OVERLEAF_LATEXMK')
        if not args.check and args.ensure and (not report['ready'] or args.managed or args.full):
            if explicit:
                raise RuntimeError('Configured TeX is incomplete. Repair that distribution or remove the explicit latexmk override to use managed TinyTeX.')
            with setup_lock(MANAGED.parent):
                install_distribution(MANAGED)
                executable = managed_latexmk()
                provision(MANAGED, args.full)
                repair_managed_fonts(MANAGED)
                report = check(executable, require_verified=False)
                if not report['ready']:
                    raise RuntimeError('Managed TeX still lacks required tools/packages: ' + ', '.join(m['name'] for m in report['missing']))
                smoke(executable)
                if args.smoke_catalog:
                    smoke_catalog(executable)
                (MANAGED / '.latex-ready.json').write_text(json.dumps({'signature': signature(), 'version': manifest()['version'],
                    'verifiedAt': time.time(), 'full': args.full, 'threeEnginePdf': True}), encoding='utf-8')
                prepared = True
        print(json.dumps(report, ensure_ascii=True), flush=True)
        if not report['ready']:
            print('TeX is not ready. Dev/operator: run scripts/setup-tex.bat (Windows) or sh scripts/setup-tex.sh. Web visitors do not install TeX.', flush=True)
            return 1
        if args.smoke and not prepared:
            smoke(executable)
        if args.smoke_catalog and not prepared:
            smoke_catalog(executable)
        return 0
    except (RuntimeError, OSError, subprocess.TimeoutExpired, tarfile.TarError, zipfile.BadZipFile) as error:
        print('TeX setup failed: ' + str(error), flush=True)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())

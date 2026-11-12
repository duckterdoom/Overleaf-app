import hashlib
import io
import json
from pathlib import Path
import tarfile
import zipfile
import pytest

from latex_backend.compilation import tex_runtime as runtime
from latex_backend.compilation.compiler import find_latexmk
from latex_backend.compilation.toolchain import preflight


def test_empty_clone_reports_tools_not_just_packages(tmp_path):
    report = preflight(str(tmp_path / 'missing/latexmk'), {'requiredPackages': ['iftex', 'inputenc']})
    assert not report['toolchainAvailable']
    assert {m['name'] for m in report['missing']} >= {'iftex', 'inputenc', 'latexmk', 'pdflatex'}
    assert 'setup-tex' in report['setupCommand']


def test_managed_tex_precedes_path_but_explicit_override_wins(tmp_path, monkeypatch):
    root = tmp_path / 'portable runtime with spaces'
    suffix = '.exe' if runtime.os.name == 'nt' else ''
    binary = root / 'bin/native' / ('latexmk' + suffix)
    binary.parent.mkdir(parents=True)
    binary.touch()
    monkeypatch.setattr(runtime, 'MANAGED', root)
    monkeypatch.delenv('OVERLEAF_LATEXMK', raising=False)
    assert find_latexmk() == str(binary)
    assert find_latexmk('explicit-tex') == 'explicit-tex'


@pytest.mark.parametrize('system,cpu,key', [('Windows', 'AMD64', 'windows-amd64'), ('Linux', 'x86_64', 'linux-x86_64'),
    ('Linux', 'aarch64', 'linux-arm64'), ('Darwin', 'arm64', 'darwin')])
def test_native_archives_have_pinned_checksums(system, cpu, key):
    entry = runtime.archive_for(system, cpu)
    assert entry == runtime.manifest()['archives'][key] and len(entry['sha256']) == 64


def test_unsupported_native_platform_is_not_silently_replaced():
    with pytest.raises(RuntimeError, match='No managed TinyTeX archive'):
        runtime.archive_for('Windows', 'arm64')


def test_corrupt_download_never_reaches_extraction(tmp_path, monkeypatch):
    monkeypatch.setattr(runtime.urllib.request, 'urlopen', lambda *a, **kw: io.BytesIO(b'corrupt download'))
    archive = tmp_path / 'download.zip'
    with pytest.raises(RuntimeError, match='SHA-256'):
        runtime.download(runtime.archive_for('Windows', 'AMD64'), archive)
    assert not archive.exists()


@pytest.mark.parametrize('kind', ['zip', 'tar'])
def test_archive_escape_is_rejected(tmp_path, kind):
    archive = tmp_path / 'untrusted'
    if kind == 'zip':
        with zipfile.ZipFile(archive, 'w') as stream:
            stream.writestr('../outside.txt', 'must not escape')
    else:
        with tarfile.open(archive, 'w') as stream:
            item = tarfile.TarInfo('../outside.txt'); item.size = 1
            stream.addfile(item, io.BytesIO(b'x'))
    with pytest.raises(RuntimeError, match='Unsafe path'):
        runtime.extract(archive, tmp_path / 'stage')
    assert not (tmp_path / 'outside.txt').exists()


def test_setup_lock_prevents_parallel_mutation_and_releases_after_failure(tmp_path):
    with pytest.raises(ValueError):
        with runtime.setup_lock(tmp_path):
            with pytest.raises(RuntimeError, match='Another TeX setup'):
                with runtime.setup_lock(tmp_path):
                    pass
            raise ValueError()
    assert not (tmp_path / 'setup.lock').exists()


def test_manifest_covers_all_templates_and_common_import_dependencies():
    template, count = runtime.requirements()
    assert count >= 31 and {'vntex', 'beamer', 'biblatex', 'tikz'} <= set(template['requiredPackages'])
    packages = runtime.manifest()['packages']
    assert {'footmisc', 'colortbl', 'cm-super', 'ec', 'biber'} <= set(packages)
    assert 'subcaption' not in packages  # subcaption.sty belongs to the caption package.


def test_explicit_broken_distribution_is_not_modified(monkeypatch, capsys):
    monkeypatch.setattr(runtime, 'check', lambda *a, **kw: {'ready': False, 'missing': ['example']})
    monkeypatch.setattr(runtime, 'install_distribution', lambda *a: pytest.fail('Must not change configured system TeX'))
    assert runtime.main(['--ensure', '--latexmk', 'operator-selected-tex']) == 1
    assert 'Configured TeX is incomplete' in capsys.readouterr().out


def test_incomplete_managed_install_is_retained(tmp_path):
    folder = tmp_path / 'TinyTeX'
    folder.mkdir(); important = folder / 'keep.txt'; important.write_text('keep')
    with pytest.raises(RuntimeError, match='Incomplete managed TeX'):
        runtime.install_distribution(folder)
    assert important.read_text() == 'keep'


def test_portable_font_configuration_uses_current_root_and_stays_local(tmp_path):
    from xml.etree import ElementTree
    root = tmp_path / 'private TeX & spaces'
    runtime.repair_managed_fonts(root)
    config = root / '.latex-fontconfig.conf'
    directories = [node.text for node in ElementTree.fromstring(config.read_text()).findall('dir')]
    assert (root / 'texmf-dist/fonts/opentype').as_posix() in directories
    assert not any('runneradmin' in directory for directory in directories)
    binary = root / 'bin/native/latexmk'
    assert runtime.tex_environment(binary)['FONTCONFIG_FILE'] == str(config)


def test_new_managed_runtime_must_pass_smoke_before_being_ready(tmp_path, monkeypatch):
    from types import SimpleNamespace
    root = tmp_path / 'TinyTeX'
    folder = root / 'bin/native'
    folder.mkdir(parents=True)
    for name in runtime.TOOLS:
        (folder / (name + ('.exe' if runtime.os.name == 'nt' else ''))).touch()
    monkeypatch.setattr(runtime, 'MANAGED', root)
    monkeypatch.setattr(runtime, 'preflight', lambda *a: {'bin': str(folder), 'missing': []})
    monkeypatch.setattr(runtime, 'run', lambda *a: SimpleNamespace(stdout='\n'.join(runtime.manifest()['probes']).encode()))
    executable = folder / 'latexmk'
    assert not runtime.check(executable)['ready']
    (root / '.latex-ready.json').write_text(json.dumps({'signature': runtime.signature()}))
    assert runtime.check(executable)['ready']

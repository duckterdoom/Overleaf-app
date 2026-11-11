"""Exercise real Windows PowerShell 5.1 startup in an isolated fresh clone.

No project/runtime is deleted, no interpreter is downloaded, and no database
configuration is read. PATH shims represent broken/missing launchers.
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys

import pytest

SCRIPTS = Path(__file__).resolve().parents[1]
POWERSHELL = shutil.which('powershell.exe')
pytestmark = pytest.mark.skipif(os.name != 'nt' or not POWERSHELL, reason='Windows PowerShell integration')


def workspace(tmp_path, commands):
    root = tmp_path / 'fresh clone with spaces'
    scripts = root / 'scripts'
    scripts.mkdir(parents=True)
    for name in ('ensure-python.ps1', 'python-runtime.ps1'):
        shutil.copyfile(SCRIPTS / name, scripts / name)
    shims = root / 'commands'
    shims.mkdir()
    env = dict(os.environ)
    env.pop('LATEX_PYTHON', None)
    env['PATH'] = str(shims)
    env['LATEX_TEST_BASE_PYTHON'] = str(Path(sys._base_executable))
    env['LATEX_TEST_PROBE_LOG'] = str(root / 'probes.txt')
    for name, works in commands.items():
        body = '@echo off\necho ' + name + '>>"%LATEX_TEST_PROBE_LOG%"\n'
        if works:
            body += ('"%LATEX_TEST_BASE_PYTHON%" -c "import sys; assert sys.version_info >= (3,12); print(sys.executable)"\n'
                     if name == 'py' else '"%LATEX_TEST_BASE_PYTHON%" %*\n')
            body += 'exit /b %errorlevel%\n'
        else:
            body += 'echo Runtime not found 1>&2\nexit /b 1\n'
        (shims / (name + '.cmd')).write_text(body, encoding='ascii')
    return root, env


def run(root, env, script='ensure-python.ps1'):
    return subprocess.run([POWERSHELL, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(root / 'scripts' / script)],
                          cwd=root, env=env, capture_output=True, text=True, timeout=90)


def test_fresh_clone_python_works_even_with_broken_py_and_uv(tmp_path):
    root, env = workspace(tmp_path, {'python': True, 'py': False, 'uv': False})
    result = run(root, env)
    assert result.returncode == 0, result.stdout + result.stderr
    assert (root / 'probes.txt').read_text().splitlines() == ['python']
    python = root / 'apps/backend/.venv/Scripts/python.exe'
    check = subprocess.run([str(python), '-c', 'import sys; assert sys.version_info >= (3,12)'], capture_output=True)
    assert check.returncode == 0, check.stderr
    # A valid environment is reused, not recreated or moved on every launch.
    (root / 'probes.txt').unlink()
    again = run(root, env)
    assert again.returncode == 0, again.stdout + again.stderr
    assert not (root / 'probes.txt').exists()
    assert not list((root / 'apps/backend').glob('.venv.backup-*'))


@pytest.mark.parametrize('commands, expected', [
    ({'python': False, 'python3': True, 'py': False}, ['python', 'python3']),
    ({'python': False, 'python3': False, 'py': True}, ['python', 'python3', 'py']),
])
def test_broken_candidate_falls_through_and_preserves_old_env(tmp_path, commands, expected):
    root, env = workspace(tmp_path, commands)
    old = root / 'apps/backend/.venv'
    old.mkdir(parents=True)
    (old / 'keep.txt').write_text('old environment')
    result = run(root, env)
    assert result.returncode == 0, result.stdout + result.stderr
    assert (root / 'probes.txt').read_text().splitlines() == expected
    backups = list(old.parent.glob('.venv.backup-*'))
    assert len(backups) == 1 and (backups[0] / 'keep.txt').read_text() == 'old environment'


def test_no_python_reports_actionable_error_instead_of_line_one(tmp_path):
    root, env = workspace(tmp_path, {'python': False, 'python3': False, 'py': False})
    result = run(root, env)
    assert result.returncode == 1
    assert 'No working Python 3.12+ found' in result.stdout
    assert 'Tried python, python3 and py -3' in result.stdout
    assert 'SyntaxError' not in result.stdout + result.stderr


def test_explicit_installed_python_path_overrides_path_launchers(tmp_path):
    root, env = workspace(tmp_path, {'python': False, 'py': False})
    env['LATEX_PYTHON'] = env['LATEX_TEST_BASE_PYTHON']
    result = run(root, env)
    assert result.returncode == 0, result.stdout + result.stderr
    assert not (root / 'probes.txt').exists()


def test_policy_block_stops_with_explicit_diagnosis(tmp_path):
    root, env = workspace(tmp_path, {'python': False, 'python3': True})
    (root / 'commands/python.cmd').write_text(
        '@echo off\necho python>>"%LATEX_TEST_PROBE_LOG%"\necho An Application Control policy has blocked this file 1>&2\nexit /b 1\n',
        encoding='ascii')
    result = run(root, env)
    assert result.returncode == 1
    assert 'Windows Application Control blocked Python' in result.stdout
    assert (root / 'probes.txt').read_text().splitlines() == ['python']
    assert not (root / 'apps/backend/.venv').exists()


def test_web_build_passes_validated_interpreter_to_uv(tmp_path):
    root, env = workspace(tmp_path, {'python': True})
    shutil.copyfile(SCRIPTS / 'run-python-web.bat', root / 'scripts/run-python-web.bat')
    (root / 'apps/frontend').mkdir(parents=True)
    env['PATH'] += os.pathsep + str(Path(POWERSHELL).parent) + os.pathsep + str(Path(os.environ['SystemRoot']) / 'System32')
    (root / 'commands/uv.cmd').write_text('@echo off\necho %*>"%LATEX_TEST_UV_LOG%"\nexit /b 0\n', encoding='ascii')
    (root / 'commands/npm.cmd').write_text('@echo off\necho %*>>"%LATEX_TEST_NPM_LOG%"\nexit /b 0\n', encoding='ascii')
    env['LATEX_TEST_UV_LOG'] = str(root / 'uv.txt')
    env['LATEX_TEST_NPM_LOG'] = str(root / 'npm.txt')
    result = subprocess.run([str(Path(os.environ['SystemRoot']) / 'System32/cmd.exe'), '/d', '/c',
                             'scripts\\run-python-web.bat', '--build'], cwd=root, env=env,
                            capture_output=True, text=True, timeout=90)
    assert result.returncode == 0, result.stdout + result.stderr
    arguments = (root / 'uv.txt').read_text()
    assert '--python "' + str(root / 'apps/backend/.venv/Scripts/python.exe') + '"' in arguments
    assert '--extra web-local' in arguments and '--frozen' in arguments
    assert (root / 'npm.txt').read_text().splitlines() == ['ci', 'run build']

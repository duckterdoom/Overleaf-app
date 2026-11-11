$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $repoRoot
$env:PYTHONUTF8 = '1'
$venvRoot = Join-Path $repoRoot 'apps/backend/.venv'
$venvPython = Join-Path $venvRoot 'Scripts/python.exe'
. (Join-Path $PSScriptRoot 'python-runtime.ps1')

try {
    if (Test-LatexPython $venvPython) { exit 0 }
    Write-Host 'Python environment is missing or broken. Preparing Python 3.12+...'
    $basePython = Find-LatexPython
    $uvPath = Join-Path $repoRoot 'target/uv-python-migration/uv.exe'
    if (-not (Test-Path -LiteralPath $uvPath)) {
        $uvCommand = Get-Command uv -CommandType Application -ErrorAction SilentlyContinue
        if ($uvCommand) { $uvPath = $uvCommand.Source }
    }
    if (-not $basePython -and (Test-Path -LiteralPath $uvPath)) {
        $env:UV_PYTHON_INSTALL_DIR = Join-Path $repoRoot 'target/python-runtimes'
        $env:UV_CACHE_DIR = Join-Path $repoRoot 'target/uv-cache'
        & $uvPath python install 3.12
        if ($LASTEXITCODE -ne 0) { throw 'Could not prepare Python with uv. Check the error above and your internet connection.' }
    } elseif (-not $basePython) {
        throw 'No working Python 3.12+ found. Install Python from python.org or uv, or set LATEX_PYTHON to an installed python.exe. Tried python, python3 and py -3. See docs/WINDOWS_PYTHON_STARTUP.md.'
    }
    if (Test-Path -LiteralPath $venvRoot) {
        $resolvedVenv = [IO.Path]::GetFullPath($venvRoot)
        if ($resolvedVenv -ne [IO.Path]::GetFullPath((Join-Path $repoRoot 'apps/backend/.venv'))) {
            throw 'Refusing to move an environment outside the expected project directory.'
        }
        $backupPath = "$venvRoot.backup-$([Guid]::NewGuid().ToString('N'))"
        Move-Item -LiteralPath $venvRoot -Destination $backupPath
        Write-Host "Previous environment preserved at $backupPath"
    }
    if ($basePython) {
        Write-Host "Using installed Python: $basePython"
        & $basePython -m venv $venvRoot
    } else {
        & $uvPath venv --python 3.12 --seed $venvRoot
    }
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the Python environment. Review the full error above.' }
    if (-not (Test-LatexPython $venvPython)) { throw 'Python environment validation failed. Review the runtime error above.' }
    Write-Host "Python environment ready: $venvPython"
    exit 0
} catch {
    Write-Host "Python setup failed: $($_.Exception.Message)"
    exit 1
}

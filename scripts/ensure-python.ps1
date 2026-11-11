$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $repoRoot
$env:PYTHONUTF8 = '1'
$venvRoot = Join-Path $repoRoot 'apps/backend/.venv'
$venvPython = Join-Path $venvRoot 'Scripts/python.exe'

function Test-DesktopPython([string] $candidate) {
    $ErrorActionPreference = 'Continue'
    if (-not (Test-Path -LiteralPath $candidate)) { return $false }
    try {
        & $candidate -c 'import sys; sys.exit(0 if sys.version_info >= (3,12) else 1)' *> $null
    } catch {
        $launchError = $_
        $cause = $launchError.Exception
        $policyBlocked = $false
        while ($null -ne $cause) {
            if (($cause -is [System.ComponentModel.Win32Exception] -and $cause.NativeErrorCode -in @(4551, 1260, 577)) -or
                $cause.Message -match 'Application Control policy|blocked by group policy') {
                $policyBlocked = $true
            }
            $cause = $cause.InnerException
        }
        if ($policyBlocked) {
            throw "Windows Application Control blocked Python: $candidate. Ask your administrator to review the CodeIntegrity/Operational block event and approve the required Python runtime according to your device policy. Setup has stopped; no security settings were changed. See docs/WINDOWS_PYTHON_STARTUP.md."
        }
        throw $launchError
    }
    return $LASTEXITCODE -eq 0
}

try {
    if (Test-DesktopPython $venvPython) { exit 0 }
    Write-Host 'Python environment is missing or broken. Preparing Python 3.12+...'
    $uvPath = Join-Path $repoRoot 'target/uv-python-migration/uv.exe'
    if (-not (Test-Path -LiteralPath $uvPath)) {
        $uvCommand = Get-Command uv -ErrorAction SilentlyContinue
        if ($uvCommand) { $uvPath = $uvCommand.Source }
    }
    if (Test-Path -LiteralPath $uvPath) {
        # Keep the managed interpreter at the same stable location as pyvenv.cfg.
        $env:UV_PYTHON_INSTALL_DIR = Join-Path $repoRoot 'target/python-runtimes'
        $env:UV_CACHE_DIR = Join-Path $repoRoot 'target/uv-cache'
        & $uvPath python install 3.12
        if ($LASTEXITCODE -ne 0) { throw 'Could not download/restore Python. Check your internet connection.' }
        if (Test-DesktopPython $venvPython) { exit 0 }
        if (Test-Path -LiteralPath $venvRoot) {
            $backupPath = "$venvRoot.backup-$([Guid]::NewGuid().ToString('N'))"
            Move-Item -LiteralPath $venvRoot -Destination $backupPath
            Write-Host "Previous environment preserved at $backupPath"
        }
        & $uvPath venv --python 3.12 --seed $venvRoot
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the Python environment.' }
    } else {
        $basePython = $null
        $pyCommand = Get-Command py -ErrorAction SilentlyContinue
        if ($pyCommand) {
            $probe = & $pyCommand.Source -3 -c 'import sys; print(sys.executable if sys.version_info >= (3,12) else "")' 2>$null
            if ($LASTEXITCODE -eq 0 -and $probe) { $basePython = [string]$probe }
        }
        if (-not $basePython) {
            $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
            if ($pythonCommand -and $pythonCommand.Source -notlike '*WindowsApps*') {
                if (Test-DesktopPython $pythonCommand.Source) { $basePython = $pythonCommand.Source }
            }
        }
        if (-not $basePython) { throw 'Install Python 3.12+ (python.org) or uv, then run the launcher again. The Windows Store alias is not a Python installation.' }
        if (Test-Path -LiteralPath $venvRoot) {
            Move-Item -LiteralPath $venvRoot -Destination "$venvRoot.backup-$([Guid]::NewGuid().ToString('N'))"
        }
        & $basePython -m venv $venvRoot
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the Python environment.' }
    }
    if (-not (Test-DesktopPython $venvPython)) { throw 'Python environment validation failed.' }
    exit 0
} catch {
    Write-Host "Python setup failed: $($_.Exception.Message)"
    exit 1
}

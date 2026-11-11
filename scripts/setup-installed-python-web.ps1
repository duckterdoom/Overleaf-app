$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path $PSScriptRoot -Parent)
$env:PYTHONUTF8 = '1'
$localPython = Join-Path (Get-Location) 'target/official-python-web-env/Scripts/python.exe'
try {
    if (-not (Test-Path -LiteralPath $localPython)) {
        # Use an existing system installation; never download another interpreter.
        & py -3 -c 'import sys; assert sys.version_info >= (3,12), "Python 3.12+ is required"'
        if ($LASTEXITCODE -ne 0) { throw 'No working installed Python 3.12+ was found.' }
        & py -3 -m venv target/official-python-web-env
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the local web environment.' }
    }
    & $localPython -m pip install -e 'apps/backend[web-local]'
    if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed. Review the error above.' }
    & $localPython -c 'import latex_backend.entrypoints.web, pdf2docx, pymupdf, docx, PIL, pymongo, websockets; print("Local web environment ready.")'
    if ($LASTEXITCODE -ne 0) { throw 'Runtime verification failed. Review any Windows application-control block before retrying.' }
    Write-Host 'Run scripts\run-python-web.bat to start Latex.'
} catch {
    Write-Error $_
    exit 1
}

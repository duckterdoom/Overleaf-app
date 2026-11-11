$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path $PSScriptRoot -Parent)
$env:PYTHONUTF8 = '1'
$localPython = Join-Path (Get-Location) 'target/official-python-web-env/Scripts/python.exe'
. (Join-Path $PSScriptRoot 'python-runtime.ps1')
try {
    if (-not (Test-LatexPython $localPython)) {
        # Use an existing system installation; never download another interpreter.
        $basePython = Find-LatexPython
        if (-not $basePython) { throw 'No working installed Python 3.12+ found. Tried python, python3 and py -3. Set LATEX_PYTHON if needed.' }
        $environmentRoot = Join-Path (Get-Location) 'target/official-python-web-env'
        if (Test-Path -LiteralPath $environmentRoot) {
            $expectedRoot = [IO.Path]::GetFullPath((Join-Path (Get-Location) 'target/official-python-web-env'))
            if ([IO.Path]::GetFullPath($environmentRoot) -ne $expectedRoot) { throw 'Invalid environment directory.' }
            Move-Item -LiteralPath $environmentRoot -Destination "$environmentRoot.backup-$([Guid]::NewGuid().ToString('N'))"
        }
        Write-Host "Using installed Python: $basePython"
        & $basePython -m venv $environmentRoot
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the local web environment.' }
    }
    & $localPython -m pip install -e 'apps/backend[web-local]'
    if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed. Review the error above.' }
    & $localPython -c 'import latex_backend.entrypoints.web, pdf2docx, pymupdf, docx, PIL, pymongo, websockets'
    if ($LASTEXITCODE -ne 0) { throw 'Runtime verification failed. Review any Windows application-control block before retrying.' }
    Write-Host 'Local web environment ready.'
    Write-Host 'Run scripts\run-python-web.bat to start Latex.'
} catch {
    Write-Error $_
    exit 1
}

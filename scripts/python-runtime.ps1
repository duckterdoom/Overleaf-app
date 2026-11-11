# Shared runtime discovery. Keep the native -c probe free of embedded quotes:
# Windows PowerShell 5.1 passes quotes differently from PowerShell 7.
function Test-LatexPython([string] $Candidate, [string[]] $LauncherArguments = @()) {
    if (-not $Candidate -or -not (Test-Path -LiteralPath $Candidate -PathType Leaf)) { return $null }
    $ErrorActionPreference = 'Stop'
    try {
        $probeCode = 'import sys; assert sys.version_info >= (3,12); print(sys.executable)'
        $output = & $Candidate @LauncherArguments -c $probeCode 2>&1
        $probeExit = $LASTEXITCODE
        if ($probeExit -eq 0) {
            $lines = @($output | ForEach-Object { [string]$_ } | Where-Object { $_.Trim() })
            if ($lines.Count) {
                $executable = $lines[-1]
                if (Test-Path -LiteralPath $executable -PathType Leaf) { return $executable }
            }
        }
        Write-Host "Skipping unusable Python candidate: $Candidate (exit $probeExit)."
    } catch {
        $failure = $_
        $cause = $failure.Exception
        while ($null -ne $cause) {
            if (($cause -is [System.ComponentModel.Win32Exception] -and $cause.NativeErrorCode -in @(4551, 1260, 577)) -or
                $cause.Message -match 'Application Control policy|blocked by group policy') {
                throw "Windows Application Control blocked Python: $Candidate. Ask your administrator to approve this runtime according to your device policy. No security settings were changed. See docs/WINDOWS_PYTHON_STARTUP.md."
            }
            $cause = $cause.InnerException
        }
        # A missing launcher runtime or stale trampoline must not hide another
        # installed interpreter. Application Control is handled separately above.
        Write-Host "Skipping unusable Python candidate: $Candidate ($($failure.Exception.Message))."
    }
    return $null
}

function Find-LatexPython {
    if ($env:LATEX_PYTHON) {
        $explicit = Test-LatexPython $env:LATEX_PYTHON
        if (-not $explicit) { throw 'LATEX_PYTHON must point to a working Python 3.12+ executable.' }
        return $explicit
    }
    foreach ($name in @('python', 'python3', 'py')) {
        foreach ($command in @(Get-Command $name -CommandType Application -All -ErrorAction SilentlyContinue)) {
            $launcherArguments = @()
            if ($name -eq 'py') { $launcherArguments = @('-3') }
            $runtime = Test-LatexPython $command.Source $launcherArguments
            if ($runtime) { return $runtime }
        }
    }
    return $null
}

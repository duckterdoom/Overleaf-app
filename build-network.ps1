param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$compiler = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { 'javac' }
$sourceRoots = @('overleaf-common', 'overleaf-server', 'overleaf-client')
$sources = foreach ($module in $sourceRoots) {
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "$module\src\main\java") -Filter '*.java' -Recurse
}
$output = Join-Path $PSScriptRoot 'bin'
New-Item -ItemType Directory -Path $output -Force | Out-Null
& $compiler --release 17 -encoding UTF-8 -d $output @($sources.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Networking build failed.' }
Write-Host 'Networking build complete: bin/'

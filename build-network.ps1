param([string]$JavaHome = $env:JAVA_HOME, [string]$OutputDirectory = (Join-Path $PSScriptRoot 'dist'))
$ErrorActionPreference = 'Stop'
$compiler = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { 'javac' }
$archiver = if ($JavaHome) { Join-Path $JavaHome 'bin\jar.exe' } else { 'jar' }
$output = Join-Path $PSScriptRoot 'target\network-classes'
$distribution = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $output, $distribution -Force | Out-Null
$sources = foreach ($relative in @('overleaf-common\src\main\java', 'overleaf-server\src\main\java', 'overleaf-client\src\main\java\com\overleaf\client\net')) {
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot $relative) -Recurse -Filter '*.java'
}
& $compiler --release 17 -encoding UTF-8 -d $output @($sources.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
& $archiver --create --file (Join-Path $distribution 'overleaf-server.jar') --main-class com.overleaf.server.ServerApp -C $output com/overleaf/common -C $output com/overleaf/server
if ($LASTEXITCODE -ne 0) { throw 'Server packaging failed' }
& $archiver --create --file (Join-Path $distribution 'overleaf-network-demo.jar') --main-class com.overleaf.client.net.NetworkDemo -C $output com/overleaf/common -C $output com/overleaf/client/net
if ($LASTEXITCODE -ne 0) { throw 'Demo packaging failed' }
Write-Host "Built $distribution\overleaf-server.jar and overleaf-network-demo.jar (Java 17+)"

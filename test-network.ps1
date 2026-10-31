param([string]$JavaHome = $env:JAVA_HOME, [string]$MavenRepository = (Join-Path $env:USERPROFILE '.m2\repository'))
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'build-network.ps1') -JavaHome $JavaHome
$compiler = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { 'javac' }
$runtime = if ($JavaHome) { Join-Path $JavaHome 'bin\java.exe' } else { 'java' }
$libraryRoot = Join-Path $PSScriptRoot 'target\network-test-libs'
$output = Join-Path $PSScriptRoot 'target\network-test-classes'
New-Item -ItemType Directory -Path $libraryRoot, $output -Force | Out-Null
# Cached JUnit 5.10.2; no fake assertions or substitute test engine.
$libraries = @(
    'org\junit\jupiter\junit-jupiter-api\5.10.2\junit-jupiter-api-5.10.2.jar',
    'org\junit\jupiter\junit-jupiter-engine\5.10.2\junit-jupiter-engine-5.10.2.jar',
    'org\junit\platform\junit-platform-commons\1.10.2\junit-platform-commons-1.10.2.jar',
    'org\junit\platform\junit-platform-engine\1.10.2\junit-platform-engine-1.10.2.jar',
    'org\junit\platform\junit-platform-launcher\1.10.2\junit-platform-launcher-1.10.2.jar',
    'org\opentest4j\opentest4j\1.3.0\opentest4j-1.3.0.jar',
    'org\apiguardian\apiguardian-api\1.1.2\apiguardian-api-1.1.2.jar'
)
foreach ($library in $libraries) {
    $source = Join-Path $MavenRepository $library
    if (!(Test-Path -LiteralPath $source)) { throw "Missing cached JUnit library: $source. Use Maven tests or supply -MavenRepository." }
    Copy-Item -LiteralPath $source -Destination $libraryRoot -Force
}
$classpath = (Join-Path $PSScriptRoot 'target\network-classes') + ';' + (Join-Path $libraryRoot '*')
$tests = foreach ($relative in @('overleaf-common\src\test\java', 'overleaf-server\src\test\java', 'overleaf-client\src\test\java\com\overleaf\client\net')) {
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot $relative) -Filter '*.java' -Recurse
}
& $compiler --release 17 -encoding UTF-8 -cp $classpath -d $output @($tests.FullName) (Join-Path $PSScriptRoot 'tools\NetworkingTestLauncher.java')
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& $runtime '-Dfile.encoding=UTF-8' -cp ($output + ';' + $classpath) NetworkingTestLauncher
if ($LASTEXITCODE -ne 0) { throw 'Networking tests failed' }

# Prepare the bundled TinyTeX for Optica's universal manuscript ZIP.
# Run from PowerShell: .\scripts\install-optica-tex.ps1
$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$distribution = @("target\TinyTeX", "TinyTeX", "tools\TinyTeX") |
    ForEach-Object { Join-Path $repo $_ } |
    Where-Object { Test-Path -LiteralPath (Join-Path $_ "bin\windows\kpsewhich.exe") } |
    Select-Object -First 1
if (-not $distribution) {
    throw "Bundled Windows TinyTeX not found. Install TinyTeX first or use your system TeX package manager."
}
$bin = Join-Path $distribution "bin\windows"
$perl = Join-Path $distribution "tlpkg\tlperl\bin\perl.exe"
$manager = Join-Path $distribution "texmf-dist\scripts\texlive\tlmgr.pl"
$packages = @(
    "geometry", "xcolor", "colortbl", "tools", "multirow", "graphics",
    "silence", "newtx", "tex-gyre", "psnfss", "courier", "helvetic", "microtype", "soul", "fancyhdr",
    "lastpage", "caption", "cite", "hyperref", "kvoptions", "iftex", "lineno",
    "xpatch", "xstring", "fontaxes", "carlisle", "mweights", "oberdiek",
    "xkeyval", "etoolbox", "amsmath", "amscls", "amsfonts"
)
$oldPath = $env:PATH
$oldPerl = $env:PERL5LIB
try {
    $env:PATH = "$bin;$oldPath"
    $env:PERL5LIB = Join-Path $distribution "tlpkg\tlperl\lib"
    & $perl $manager install @packages
    if ($LASTEXITCODE -ne 0) { throw "TinyTeX package installation failed. Read the tlmgr output above." }
    foreach ($probe in @("colortbl.sty", "silence.sty", "newtxtext.sty", "newtxmath.sty", "lastpage.sty", "cite.sty", "xpatch.sty", "xstring.sty", "fontaxes.sty", "scalefnt.sty", "mweights.sty", "centernot.sty", "pcrr8t.tfm", "phvr8t.tfm", "ts1-qtmri.tfm")) {
        & (Join-Path $bin "kpsewhich.exe") $probe
        if ($LASTEXITCODE -ne 0) { throw "Package installation did not provide $probe." }
    }
    Write-Host "Optica TeX packages are ready. Click Recompile in the app."
} finally {
    $env:PATH = $oldPath
    $env:PERL5LIB = $oldPerl
}

param([switch]$Apply)

# Dry run by default. Only generated caches and named UI test runs are eligible.
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..\..'))
$paths = @('.pytest_cache', '.ruff_cache', '.next')
$testPattern = '^(appearance-(audit|direct|final|mutation)-|assessment-(no-horizontal|ui)-|auth-role-selection-|checklist-|codebase-cleanup-|compact-(compile|question|word)-|dashboard-columns-|direct-questions-(browser|regression)-|document-(word|workspace)-|docx-(final|review)-|editor-(return-library-check|scroll)-|expanded-guides-|finish-score-check-|focus-exit-|full-question-content-|guide-samples-check-|hidden-question-scroll-|history-(diff-ui|editor-regression)-|home-portals-|library-(guides|taxonomy)-|list-filters-|module-navigation-|reorganized-)'
$targetRoot = Join-Path $repoRoot 'target'
if (Test-Path -LiteralPath $targetRoot) {
    $paths += Get-ChildItem -LiteralPath $targetRoot -Directory |
        Where-Object { $_.Name -match $testPattern } |
        ForEach-Object { Join-Path 'target' $_.Name }
}
$removed = 0
foreach ($relative in $paths) {
    $path = [IO.Path]::GetFullPath((Join-Path $repoRoot $relative))
    if (-not $path.StartsWith($repoRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Cleanup path must stay inside this repository.'
    }
    if (-not (Test-Path -LiteralPath $path)) { continue }
    $item = Get-Item -LiteralPath $path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Refusing linked cleanup target: $relative"
    }
    $links = Get-ChildItem -LiteralPath $path -Recurse -Force |
        Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint } |
        Select-Object -First 1
    if ($links) { throw "Refusing target containing links: $relative" }
    if ($Apply) {
        Remove-Item -LiteralPath $path -Recurse -Force
        Write-Output "Removed: $relative"
    } else {
        Write-Output "Would remove: $relative"
    }
    $removed++
}
Write-Output "Eligible folders: $removed"
if (-not $Apply) { Write-Output 'Dry run only. Add -Apply to delete the listed folders.' }
Write-Output 'Python, TeX, Pandoc, backups, project data, frontend out and startup scripts are retained.'

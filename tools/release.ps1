param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notes = "",
    [string]$Repo = "",
    [switch]$UseDebugSigning
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $env:USERPROFILE ".gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat"
$signingArgs = @()
if ($UseDebugSigning) { $signingArgs += "-PuseDebugSigning=true" }
& $gradle -p $root assembleRelease @signingArgs
if ($LASTEXITCODE -ne 0) { throw "Gradle release build failed" }

$apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
$tag = "v$Version"
$title = "落弦律 $Version"
if ([string]::IsNullOrWhiteSpace($Repo)) { $Repo = (gh repo view --json nameWithOwner -q .nameWithOwner) }
gh release create $tag $apk --repo $Repo --title $title --notes $Notes --target master
if ($LASTEXITCODE -ne 0) { throw "GitHub release failed" }
Write-Host "Published $tag to $Repo"

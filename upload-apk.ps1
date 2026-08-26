#!/usr/bin/env pwsh
# upload-apk.ps1 — Copy latest release APK to OneDrive (Shamanna) and open SharePoint to verify

$apkSource = "$PSScriptRoot\app\build\outputs\apk\release\app-release.apk"
$destination = "F:\snkr\WARVIS"
$sharePointUrl = "https://simcorp-my.sharepoint.com/my?id=%2Fpersonal%2Fsnkr%5Fsimcorp%5Fcom%2FDocuments%2FShamanna&viewid=0a3a4328-b9e6-4967-b983-368e0166a11d"

# ── 1. Verify APK exists ──────────────────────────────────────────────────────
if (!(Test-Path $apkSource)) {
    Write-Error "APK not found at: $apkSource`nRun 'gradle assembleRelease' first."
    exit 1
}

$apk = Get-Item $apkSource
$sizeMB = [math]::Round($apk.Length / 1MB, 1)
Write-Host "Found APK: $($apk.Name) ($sizeMB MB, built $($apk.LastWriteTime.ToString('yyyy-MM-dd HH:mm')))"

# ── 2. Copy to OneDrive ───────────────────────────────────────────────────────
if (!(Test-Path $destination)) {
    New-Item -ItemType Directory -Path $destination | Out-Null
    Write-Host "Created folder: $destination"
}

$destFile = Join-Path $destination $apk.Name
Copy-Item $apkSource $destFile -Force
Write-Host "✅ Copied to OneDrive: $destFile"
Write-Host "   OneDrive will sync this to SharePoint automatically."

# ── 3. Open SharePoint in browser ────────────────────────────────────────────
Write-Host "Opening SharePoint folder in browser..."
Start-Process $sharePointUrl
Write-Host "Done. Refresh the SharePoint page if the file isn't visible yet (sync takes ~30 seconds)."

<#
.SYNOPSIS
    Switches off (or back on) the emulator apps that MesOS replaces.

.DESCRIPTION
    For the MesOS-only experience on the Android Emulator. It disables, for the
    current user, the Google/Android apps that MesOS 0.2 replaces, so MesOS apps
    also become the ones other apps open photos, files and the camera with.

    Nothing is uninstalled and nothing else is touched (System UI, Play Store,
    Play services, WebView and the system file picker stay on). Undo it with
    -Restore. The emulator must be running.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\mesos-emulator-mode.ps1
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\mesos-emulator-mode.ps1 -Restore
#>
param([switch]$Restore)

$ErrorActionPreference = 'Stop'

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $onPath) { throw 'adb not found. Install Android SDK Platform-Tools (Android Studio > SDK Manager).' }
    $adb = $onPath.Source
}

$state = (& $adb get-state 2>$null)
if ("$state".Trim() -ne 'device') { throw 'No running emulator found. Start it from Android Studio > Device Manager.' }

# Package -> what MesOS uses instead.
$replaced = [ordered]@{
    'com.google.android.apps.photos'     = 'MesOS Photos'
    'com.google.android.apps.nbu.files'  = 'MesOS Files'
    'com.android.camera2'                = 'MesOS Camera'
    'com.google.android.GoogleCamera'    = 'MesOS Camera'
    'com.google.android.calculator'      = 'MesOS Calculator'
    'com.google.android.keep'            = 'MesOS Notes'
}

$installed = @(& $adb shell pm list packages | ForEach-Object { "$_".Trim() })

foreach ($package in $replaced.Keys) {
    if ($installed -notcontains "package:$package") {
        Write-Host "  not installed  $package"
        continue
    }
    if ($Restore) {
        & $adb shell pm enable --user 0 $package | Out-Null
        Write-Host "  enabled        $package"
    } else {
        & $adb shell pm disable-user --user 0 $package | Out-Null
        Write-Host "  disabled       $package  (replaced by $($replaced[$package]))"
    }
}

if ($Restore) {
    Write-Host 'Android apps are back on.' -ForegroundColor Green
} else {
    Write-Host 'MesOS-only mode is on. Undo with -Restore.' -ForegroundColor Green
}

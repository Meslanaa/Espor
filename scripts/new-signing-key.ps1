<#
.SYNOPSIS
    Creates the MesOS developer signing key used by the GitHub release workflow.

.DESCRIPTION
    Every MesOS release must be signed with the same key, otherwise Android refuses to
    update an installed MesOS. This script creates that key once, on your own computer:

      1. generates a PKCS12 keystore with a random password in %USERPROFILE%\.mesos\
      2. copies the keystore (Base64) to the clipboard
      3. prints the two GitHub Actions secrets to create

    Nothing is uploaded by this script. Keep the keystore and password backed up
    (e.g. in a password manager). Never commit them or paste them into a chat.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\new-signing-key.ps1
#>

$ErrorActionPreference = 'Stop'

$keyDir = Join-Path $env:USERPROFILE '.mesos'
$keystore = Join-Path $keyDir 'mesos-developer.jks'
$passwordFile = Join-Path $keyDir 'mesos-developer.password.txt'

if (Test-Path $keystore) {
    throw "A MesOS key already exists at $keystore. Reuse it. Creating a new key means installed MesOS copies can no longer be updated."
}

# keytool ships with Android Studio's bundled JDK.
$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\keytool.exe') }
$candidates += 'C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe'
$candidates += (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr\bin\keytool.exe')
$keytool = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $keytool) {
    $onPath = Get-Command keytool -ErrorAction SilentlyContinue
    if ($onPath) { $keytool = $onPath.Source }
}
if (-not $keytool) {
    throw 'keytool.exe not found. Install Android Studio or set JAVA_HOME to a JDK.'
}

New-Item -ItemType Directory -Force -Path $keyDir | Out-Null

$bytes = New-Object byte[] 24
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = [Convert]::ToBase64String($bytes) -replace '[+/=]', ''

& $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias mesos `
    -keyalg RSA -keysize 4096 -validity 10000 `
    -storepass $password -keypass $password `
    -dname 'CN=MesOS Developer, O=MesOS'
if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }

Set-Content -Path $passwordFile -Value $password -NoNewline
$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
Set-Clipboard -Value $base64

Write-Host ''
Write-Host 'MesOS signing key created:' -ForegroundColor Green
Write-Host "  Keystore : $keystore"
Write-Host "  Password : $passwordFile"
Write-Host ''
Write-Host 'Now open GitHub -> Meslanaa/Espor -> Settings -> Secrets and variables -> Actions'
Write-Host 'and create two repository secrets with "New repository secret":'
Write-Host ''
Write-Host '  1. Name : MESOS_KEYSTORE_BASE64'
Write-Host '     Value: paste with Ctrl+V (already copied to the clipboard)'
Write-Host ''
Write-Host '  2. Name : MESOS_KEYSTORE_PASSWORD'
Write-Host "     Value: $password"
Write-Host ''
Write-Host 'Back up both files above. Do not share them.' -ForegroundColor Yellow

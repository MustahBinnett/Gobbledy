# Rebuilds Gobbledy and copies the APK to this folder as Gobbledy.apk.
# Uses the build tools installed in C:\PocketDictate-tools (nothing system-wide).
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
$env:JAVA_HOME = 'C:\PocketDictate-tools\jdk'
$env:GRADLE_USER_HOME = 'C:\PocketDictate-tools\gradle-home'
git submodule update --init --recursive
.\gradlew.bat assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw "Build failed (exit code $LASTEXITCODE)" }
Copy-Item -Force 'app\build\outputs\apk\release\app-release.apk' 'Gobbledy.apk'
Write-Host "Done: $PSScriptRoot\Gobbledy.apk"

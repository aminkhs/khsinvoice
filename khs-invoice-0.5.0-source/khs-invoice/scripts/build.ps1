$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $taskRoot '.tools\jdk'
$env:GRADLE_USER_HOME = Join-Path $taskRoot '.tools\gradle-home'
Push-Location $taskRoot
try { & .\gradlew.bat :core:test :app:assembleDebug :app:testDebugUnitTest --console=plain; if($LASTEXITCODE -ne 0) { throw 'Gradle verification failed' } } finally { Pop-Location }

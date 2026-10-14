$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME=Join-Path $taskRoot '.tools\jdk'
$env:GRADLE_USER_HOME=Join-Path $taskRoot '.tools\gradle-home'
Push-Location $taskRoot
try {
    & .\gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
    if($LASTEXITCODE -ne 0) { throw 'Build, tests or lint failed' }
    $adb=Join-Path $taskRoot '.tools\sdk\platform-tools\adb.exe'
    & $adb install -r 'app\build\outputs\apk\debug\app-debug.apk'
    if($LASTEXITCODE -ne 0) { throw 'App installation failed' }
    & $adb install -r 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
    if($LASTEXITCODE -ne 0) { throw 'Test installation failed' }
    $testResult=& $adb shell am instrument -w -e notClass 'ir.hesab.app.SmsCaptureTest' 'ir.hesab.app.test/androidx.test.runner.AndroidJUnitRunner'
    $testResult | Set-Content -LiteralPath 'docs\device-test-results.txt' -Encoding utf8
    $testResult
    if(!($testResult -match 'OK \(\d+ tests\)')) { throw 'Device instrumentation failed' }
    New-Item -ItemType Directory -Force -Path 'docs\screenshots' | Out-Null
    & $adb pull '/sdcard/Android/data/ir.hesab.app/files/home-test.png' 'docs\screenshots\home-test.png'
    & $adb pull '/sdcard/Android/data/ir.hesab.app/files/home-dark-test.png' 'docs\screenshots\home-dark-test.png'
    & $adb pull '/sdcard/Android/data/ir.hesab.app/files/editor-test.png' 'docs\screenshots\editor-test.png'
} finally { Pop-Location }

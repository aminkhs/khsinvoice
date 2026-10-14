$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$toolsRoot=Join-Path $taskRoot '.tools'
$env:JAVA_HOME="$toolsRoot\jdk"
$env:GRADLE_USER_HOME="$toolsRoot\gradle-home"
$env:ANDROID_HOME="$toolsRoot\sdk"
$env:ANDROID_USER_HOME="$toolsRoot\android-user"
$env:ANDROID_AVD_HOME="$toolsRoot\avd"
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME | Out-Null
& "$toolsRoot\sdk\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$toolsRoot\sdk" 'emulator' 'system-images;android-35;google_apis;x86_64'
if($LASTEXITCODE -ne 0) { throw 'Emulator package installation failed' }
'no' | & "$toolsRoot\sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd --name HesabTest --package 'system-images;android-35;google_apis;x86_64' --force
$process=Start-Process -FilePath "$toolsRoot\sdk\emulator\emulator.exe" -ArgumentList '-avd','HesabTest','-no-window','-no-audio','-no-boot-anim','-gpu','swiftshader_indirect' -WindowStyle Hidden -PassThru -RedirectStandardOutput "$toolsRoot\emulator.log" -RedirectStandardError "$toolsRoot\emulator-error.log"
& "$toolsRoot\sdk\platform-tools\adb.exe" wait-for-device
for($i=0;$i -lt 120;$i++) { $boot=& "$toolsRoot\sdk\platform-tools\adb.exe" shell getprop sys.boot_completed; if($boot.Trim() -eq '1') { break }; Start-Sleep -Seconds 2 }
& "$taskRoot\gradlew.bat" :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notClass=ir.hesab.app.SmsCaptureTest' --console=plain
if($LASTEXITCODE -ne 0) { throw 'Device tests failed' }

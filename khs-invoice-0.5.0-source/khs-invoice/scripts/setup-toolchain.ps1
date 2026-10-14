$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $taskRoot '.tools'
New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
if (!(Test-Path "$toolsRoot\jdk")) {
    Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile "$toolsRoot\jdk.zip" -TimeoutSec 300
    Expand-Archive -LiteralPath "$toolsRoot\jdk.zip" -DestinationPath "$toolsRoot\java" -Force
    $jdkFolder = Get-ChildItem "$toolsRoot\java" -Directory | Select-Object -First 1
    Move-Item -LiteralPath $jdkFolder.FullName -Destination "$toolsRoot\jdk"
}
$env:JAVA_HOME = "$toolsRoot\jdk"
$env:GRADLE_USER_HOME = "$toolsRoot\gradle-home"
if (!(Test-Path "$toolsRoot\sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' -OutFile "$toolsRoot\sdk.zip" -TimeoutSec 300
    Expand-Archive -LiteralPath "$toolsRoot\sdk.zip" -DestinationPath "$toolsRoot\sdk-unpacked" -Force
    New-Item -ItemType Directory -Force -Path "$toolsRoot\sdk\cmdline-tools" | Out-Null
    Move-Item -LiteralPath "$toolsRoot\sdk-unpacked\cmdline-tools" -Destination "$toolsRoot\sdk\cmdline-tools\latest"
}
1..40 | ForEach-Object { 'y' } | & "$toolsRoot\sdk\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$toolsRoot\sdk" --licenses
& "$toolsRoot\sdk\cmdline-tools\latest\bin\sdkmanager.bat" "--sdk_root=$toolsRoot\sdk" 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'SDK installation failed' }
$sdkEscaped = "$toolsRoot\sdk".Replace('\','/')
Set-Content -LiteralPath "$taskRoot\local.properties" -Value "sdk.dir=$sdkEscaped" -Encoding utf8

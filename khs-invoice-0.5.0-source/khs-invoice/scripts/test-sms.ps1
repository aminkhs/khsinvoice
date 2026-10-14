$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$adb=Join-Path $taskRoot '.tools\sdk\platform-tools\adb.exe'
$outputFile=Join-Path $taskRoot 'docs\sms-test-results.txt'
$errorFile=Join-Path $taskRoot '.tools\sms-test-error.log'
$process=Start-Process -FilePath $adb -ArgumentList 'shell','am','instrument','-w','-e','class','ir.hesab.app.SmsCaptureTest','ir.hesab.app.test/androidx.test.runner.AndroidJUnitRunner' -WindowStyle Hidden -PassThru -RedirectStandardOutput $outputFile -RedirectStandardError $errorFile
Start-Sleep -Seconds 8
$syntheticBody=('توضیح مصنوعی آزمون ' * 15) + 'خرید: 123000 ریال حساب: ****8765'
& $adb emu sms send 5550100 $syntheticBody
$process.WaitForExit(100000) | Out-Null
Get-Content -LiteralPath $outputFile
if(!(Select-String -LiteralPath $outputFile -Pattern 'OK \(1 test\)' -Quiet)) { throw 'Real SMS end-to-end test failed' }

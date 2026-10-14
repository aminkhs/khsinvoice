$ErrorActionPreference='Stop'
# Builds dist\khs-invoice-<version>-source.zip (what belongs in the repository) and copies the debug APK next to it.
$taskRoot=Split-Path -Parent $PSScriptRoot
$distRoot=Join-Path $taskRoot 'dist'
New-Item -ItemType Directory -Force -Path $distRoot | Out-Null
$version=[regex]::Match((Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'app\build.gradle.kts')),'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if(!$version) { throw 'versionName was not found in app\build.gradle.kts' }

# The same rules as .gitignore, so the archive and a fresh "git add ." hold the same files.
$skippedFolders='.gradle','.kotlin','.tools','.idea','.git','dist','build'
$skippedFiles='^local\.properties$|^keystore\.properties$|\.(jks|keystore|iml)$|^(\.DS_Store|Thumbs\.db)$'
$files=New-Object System.Collections.Generic.List[System.IO.FileInfo]
function Add-Folder([string]$folder) {
    foreach($entry in Get-ChildItem -LiteralPath $folder -Force) {
        if($entry.PSIsContainer) { if($skippedFolders -notcontains $entry.Name) { Add-Folder $entry.FullName } }
        elseif($entry.Name -notmatch $skippedFiles) { $files.Add($entry) }
    }
}
Add-Folder $taskRoot

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath=Join-Path $distRoot "khs-invoice-$version-source.zip"
if(Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
$zip=[System.IO.Compression.ZipFile]::Open($zipPath,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach($file in $files | Sort-Object FullName) {
        # Forward slashes, so the archive opens the same way on every system.
        $name='khs-invoice/'+$file.FullName.Substring($taskRoot.Length+1).Replace('\','/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$name,[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $zip.Dispose() }

$outputs=@($zipPath)
$apk=Join-Path $taskRoot 'app\build\outputs\apk\debug\app-debug.apk'
if(Test-Path -LiteralPath $apk) {
    $apkPath=Join-Path $distRoot "khs-invoice-$version-debug.apk"
    Copy-Item -LiteralPath $apk -Destination $apkPath -Force
    $outputs+=$apkPath
} else { Write-Warning 'No debug APK found; run scripts\build.ps1 first to include it.' }
$outputs | ForEach-Object { "$((Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLower())  $([System.IO.Path]::GetFileName($_))" } | Set-Content -LiteralPath (Join-Path $distRoot 'SHA256SUMS.txt') -Encoding ascii
"$($files.Count) files -> $zipPath"

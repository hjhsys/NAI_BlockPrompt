$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$projectRoot = Split-Path -Parent $PSScriptRoot
$outputDirectory = Join-Path $projectRoot "diagnostics"
$outputFile = Join-Path $outputDirectory "emulator-network.log"

if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb.exe를 찾을 수 없습니다: $adb"
}

New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
Write-Host "NAI.Network logcat을 저장합니다. 중지하려면 Ctrl+C를 누르세요."
Write-Host "출력: $outputFile"
& $adb logcat -v threadtime -s "NAI.Network:V" | Tee-Object -FilePath $outputFile

@echo off
setlocal
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

rem Optional args:
rem   %1 = Mac ZIP filename, e.g. ShaleApp-1.0.9-mac.zip
rem   %2 = Mac SHA256
set MAC_ZIP_NAME=%~1
set MAC_SHA256=%~2

for /f %%i in ('powershell -NoProfile -Command "$m = [regex]::Match((Get-Content \"%ROOT%\pom.xml\" -Raw), '<version>([^<]+)</version>'); if ($m.Success) { $m.Groups[1].Value }"') do set VERSION=%%i
if "%VERSION%"=="" (
    echo Failed to resolve version from pom.xml
    exit /b 1
)

set DIST=%ROOT%\dist
set ZIP_FILE=%DIST%\ShaleApp-%VERSION%.zip
set MANIFEST=%ROOT%\build\assets\shale-stable.json

if not exist "%ZIP_FILE%" (
    echo Missing Windows zip for sha256: %ZIP_FILE%
    exit /b 1
)

for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -Path \"%ZIP_FILE%\" -Algorithm SHA256).Hash.ToLower()"') do set SHA256=%%i
if "%SHA256%"=="" (
    echo Failed to compute Windows SHA256
    exit /b 1
)

echo ====================================
echo Updating manifest to version %VERSION%
echo Windows SHA256: %SHA256%
if not "%MAC_ZIP_NAME%"=="" echo Mac ZIP: %MAC_ZIP_NAME%
if not "%MAC_SHA256%"=="" echo Mac SHA256: %MAC_SHA256%
echo ====================================

powershell -NoProfile -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$manifestPath='%MANIFEST%';" ^
  "$macZipName='%MAC_ZIP_NAME%';" ^
  "$macSha='%MAC_SHA256%';" ^
  "$existing=$null;" ^
  "if (Test-Path $manifestPath) {" ^
  "  $raw=Get-Content $manifestPath -Raw;" ^
  "  if ($raw -and $raw.Trim()) { $existing=$raw | ConvertFrom-Json }" ^
  "}" ^
  "$publishedAt=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ');" ^
  "$manifest=[ordered]@{" ^
  "  version='%VERSION%';" ^
  "  channel=if ($existing -and $existing.channel) { $existing.channel } else { 'stable' };" ^
  "  zipUrl='https://shalestorage.z13.web.core.windows.net/ShaleApp-%VERSION%.zip';" ^
  "  installerUrl='https://shalestorage.z13.web.core.windows.net/Shale-%VERSION%.exe';" ^
  "  notes='Release %VERSION%';" ^
  "  mandatory=if ($existing -and $null -ne $existing.mandatory) { [bool]$existing.mandatory } else { $false };" ^
  "  sha256='%SHA256%';" ^
  "  publishedAt=$publishedAt" ^
  "};" ^
  "if ($macZipName -and $macSha) {" ^
  "  $manifest['macZipUrl']='https://shalestorage.z13.web.core.windows.net/' + $macZipName;" ^
  "  $manifest['macSha256']=$macSha.ToLower();" ^
  "} else {" ^
  "  if ($existing -and $existing.PSObject.Properties['macZipUrl']) { $manifest['macZipUrl']=$existing.macZipUrl }" ^
  "  if ($existing -and $existing.PSObject.Properties['macSha256']) { $manifest['macSha256']=$existing.macSha256 }" ^
  "}" ^
  "$manifest | ConvertTo-Json -Depth 5 | Set-Content $manifestPath -Encoding UTF8" || exit /b 1

echo Manifest updated:
echo %MANIFEST%
exit /b 0
@echo off
setlocal
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

for /f %%i in ('powershell -NoProfile -Command "$m = [regex]::Match((Get-Content \"%ROOT%\pom.xml\" -Raw), '<version>([^<]+)</version>'); if ($m.Success) { $m.Groups[1].Value }"') do set VERSION=%%i
if "%VERSION%"=="" exit /b 1

set DIST=%ROOT%\dist
set ZIP_FILE=%DIST%\ShaleApp-%VERSION%.zip
set MANIFEST=%ROOT%\build\assets\shale-stable.json

if not exist "%ZIP_FILE%" (
    echo Missing zip for sha256: %ZIP_FILE%
    exit /b 1
)

for /f %%i in ('powershell -NoProfile -Command "(Get-FileHash -Path \"%ZIP_FILE%\" -Algorithm SHA256).Hash.ToLower()"') do set SHA256=%%i
if "%SHA256%"=="" (
    echo Failed to compute SHA256
    exit /b 1
)

echo ====================================
echo Updating manifest to version %VERSION%
echo SHA256: %SHA256%
echo ====================================

powershell -NoProfile -Command "$publishedAt=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'); $manifest=[ordered]@{version='%VERSION%'; channel='stable'; zipUrl='https://shalestorage.z13.web.core.windows.net/ShaleApp-%VERSION%.zip'; installerUrl='https://shalestorage.z13.web.core.windows.net/Shale-%VERSION%.exe'; notes='Release %VERSION%'; mandatory=$false; sha256='%SHA256%'; publishedAt=$publishedAt}; $manifest | ConvertTo-Json -Depth 3 | Set-Content '%MANIFEST%' -Encoding UTF8" || exit /b 1

echo Manifest updated:
echo %MANIFEST%
exit /b 0
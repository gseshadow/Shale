@echo off
setlocal
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

for /f %%i in ('powershell -NoProfile -Command "$m = [regex]::Match((Get-Content \"%ROOT%\pom.xml\" -Raw), '<version>([^<]+)</version>'); if ($m.Success) { $m.Groups[1].Value }"') do set VERSION=%%i
if "%VERSION%"=="" (
    echo Failed to resolve version from pom.xml
    
    exit /b 1
)

set MANIFEST=%ROOT%\build\assets\shale-stable.json

echo ====================================
echo Updating manifest to version %VERSION%
echo ====================================

powershell -NoProfile -Command "$publishedAt=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'); $manifest=[ordered]@{version='%VERSION%'; channel='stable'; zipUrl='https://shalestorage.z13.web.core.windows.net/ShaleApp-%VERSION%.zip'; installerUrl='https://shalestorage.z13.web.core.windows.net/Shale-%VERSION%.exe'; notes='Release %VERSION%'; mandatory=$false; sha256=''; publishedAt=$publishedAt}; $manifest | ConvertTo-Json -Depth 3 | Set-Content '%MANIFEST%' -Encoding UTF8" || goto :fail

echo Manifest updated:
echo %MANIFEST%
exit /b 0

:fail
echo update-manifest.bat failed.
exit /b 1
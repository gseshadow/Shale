@echo off
setlocal
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

if "%~1"=="" (
    echo Usage: release-and-publish.bat ^<version^>
    exit /b 1
)

set VERSION=%~1
set BASE_URL=https://shalestorage.z13.web.core.windows.net

echo ====================================
echo Starting Shale Release and Publish %VERSION%
echo ====================================
echo.

echo Step 1: Release build
call "%SCRIPT_DIR%\release.bat" %VERSION% || goto :fail

echo.
echo Step 2: Publish
call "%SCRIPT_DIR%\publish-update.bat" || goto :fail

echo.
echo ====================================
echo Release and publish complete
echo ====================================
echo Version: %VERSION%
echo.
echo Published URLs:
echo %BASE_URL%/Shale-%VERSION%.exe
echo %BASE_URL%/ShaleApp-%VERSION%.zip
echo %BASE_URL%/shale-stable.json
echo.
echo Local dist files:
echo %ROOT%\dist\Shale-%VERSION%.exe
echo %ROOT%\dist\ShaleApp-%VERSION%.zip
echo %ROOT%\dist\shale-stable.json

exit /b 0

:fail
echo.
echo ====================================
echo Release-and-publish failed
echo ====================================
exit /b 1
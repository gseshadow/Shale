@echo off
setlocal
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

if "%~1"=="" (
    echo ====================================
    echo Usage: release.bat ^<version^>
    echo Example: release.bat 1.0.2
    echo ====================================
    
    exit /b 1
)

set VERSION=%~1
set DIST=%ROOT%\dist
set MANIFEST_SRC=%ROOT%\build\assets\shale-stable.json
set MANIFEST_DIST=%DIST%\shale-stable.json

echo ====================================
echo Starting Shale Release %VERSION%
echo ====================================

echo.
echo Step 1: Updating Maven version
call "%SCRIPT_DIR%\bump-version.bat" %VERSION% || goto :fail

echo.
echo Step 2: Building release
call "%SCRIPT_DIR%\build-shale-release.bat" || goto :fail

echo.
echo Step 3: Updating update manifest
call "%SCRIPT_DIR%\update-manifest.bat" || goto :fail

echo.
echo Step 4: Copying manifest into dist
if not exist "%DIST%" mkdir "%DIST%"
copy /Y "%MANIFEST_SRC%" "%MANIFEST_DIST%" >nul || goto :fail

echo.
echo ====================================
echo Release %VERSION% complete
echo ====================================
echo.

echo Files ready to upload:
echo %DIST%\Shale-%VERSION%.exe
echo %DIST%\ShaleApp-%VERSION%.zip
echo %DIST%\shale-stable.json
echo.


exit /b 0

:fail
echo.
echo ====================================
echo RELEASE FAILED
echo ====================================
exit /b 1
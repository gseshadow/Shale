@echo off
setlocal
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

if "%~1"=="" (
    echo Usage: release-and-publish.bat ^<version^> [mandatory^|--mandatory]
    exit /b 1
)

set VERSION=%~1
set MANDATORY_UPDATE=false
shift

:parse_args
if "%~1"=="" goto :args_done
if /I "%~1"=="mandatory" set MANDATORY_UPDATE=true
if /I "%~1"=="--mandatory" set MANDATORY_UPDATE=true
if /I "%~1"=="true" set MANDATORY_UPDATE=true
shift
goto :parse_args

:args_done
set BASE_URL=https://shalestorage.z13.web.core.windows.net
set DIST=%ROOT%\dist
set MAC_ZIP=%DIST%\ShaleApp-%VERSION%-mac.zip

echo ====================================
echo Starting Shale Release and Publish %VERSION%
echo Mandatory update: %MANDATORY_UPDATE%
echo ====================================
echo.

echo Step 1: Release build
call "%SCRIPT_DIR%\release.bat" %VERSION% %MANDATORY_UPDATE% || goto :fail

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
if exist "%MAC_ZIP%" echo %BASE_URL%/ShaleApp-%VERSION%-mac.zip
echo %BASE_URL%/shale-stable.json
echo.
echo Local dist files:
echo %DIST%\Shale-%VERSION%.exe
echo %DIST%\ShaleApp-%VERSION%.zip
if exist "%MAC_ZIP%" echo %DIST%\ShaleApp-%VERSION%-mac.zip
echo %DIST%\shale-stable.json
echo.

exit /b 0

:fail
echo.
echo ====================================
echo Release-and-publish failed
echo ====================================
exit /b 1
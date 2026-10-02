@echo off
setlocal
cd /d "%~dp0"

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "ROOT=%%~fI"

if "%~1"=="" (
    echo Usage: release-and-publish.bat ^<version^> ^<true^|false^>
    exit /b 1
)
if "%~2"=="" (
    echo Missing mandatory update flag. Expected true or false.
    exit /b 1
)
if not "%~3"=="" (
    echo Unexpected release argument: "%~3"
    exit /b 1
)
set "VERSION=%~1"
set "MANDATORY_UPDATE=%~2"
if /I not "%MANDATORY_UPDATE%"=="true" if /I not "%MANDATORY_UPDATE%"=="false" (
    echo Invalid mandatory update flag: "%MANDATORY_UPDATE%". Expected true or false.
    exit /b 1
)
set BASE_URL=https://shalestorage.z13.web.core.windows.net
set DIST=%ROOT%\dist
set MAC_ZIP=%DIST%\ShaleApp-%VERSION%-mac.zip

if /I not "%SHALE_GIT_PREFLIGHT_DONE%"=="true" (
    echo Step 0: Git preflight
    python "%SCRIPT_DIR%\release_git_sync.py" preflight "%ROOT%" || goto :git_preflight_failed
)

echo ====================================
echo Starting Shale Release and Publish %VERSION%
echo Mandatory update: %MANDATORY_UPDATE%
echo ====================================
echo.

echo Step 1: Release build
call "%SCRIPT_DIR%\release.bat" "%VERSION%" "%MANDATORY_UPDATE%" || goto :fail

echo.
echo Step 2: Commit and push release metadata
python "%SCRIPT_DIR%\release_git_sync.py" sync "%ROOT%" "%VERSION%" || goto :git_sync_failed

echo.
echo Step 3: Publish
call "%SCRIPT_DIR%\publish-update.bat" || goto :fail

echo.
echo ====================================
echo Release and publish complete
echo ====================================
echo Version: %VERSION%
echo.
echo Published URLs:
echo %BASE_URL%/Shale-%VERSION%.msi
echo %BASE_URL%/ShaleApp-%VERSION%.zip
if exist "%MAC_ZIP%" echo %BASE_URL%/ShaleApp-%VERSION%-mac.zip
echo %BASE_URL%/shale-stable.json
echo.
echo Local dist files:
echo %DIST%\Shale-%VERSION%.msi
echo %DIST%\ShaleApp-%VERSION%.zip
if exist "%MAC_ZIP%" echo %DIST%\ShaleApp-%VERSION%-mac.zip
echo %DIST%\shale-stable.json
echo.

exit /b 0

:git_preflight_failed
echo Git preflight failed before release mutation or build. Nothing was published.
goto :fail

:git_sync_failed
echo Git synchronization failed. Generated release files and any created commit were preserved.
echo Publication did not begin. Follow the Git recovery guidance above, then retry.
goto :fail

:fail
echo.
echo ====================================
echo Release-and-publish failed
echo ====================================
echo If Git synchronization succeeded but publication failed, retry publication only with:
echo   build\scripts\publish-update.bat
exit /b 1

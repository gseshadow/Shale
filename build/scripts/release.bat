@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

if "%~1"=="" (
    echo ====================================
    echo Usage: release.bat ^<version^>
    echo Example: release.bat 1.0.10
    echo ====================================
    exit /b 1
)

set VERSION=%~1
set DIST=%ROOT%\dist
set MANIFEST_SRC=%ROOT%\build\assets\shale-stable.json
set MANIFEST_DIST=%DIST%\shale-stable.json

set MAC_HANDOFF_DIR=%ROOT%\build\mac-handoff
set MAC_META_FILE=%MAC_HANDOFF_DIR%\shale-mac-release.json
set MAC_ZIP_NAME=
set MAC_SHA256=
set MAC_META_VERSION=
set MAC_SOURCE_ZIP=
set MAC_DIST_ZIP=

echo ====================================
echo Starting Shale Release %VERSION%
echo ====================================
echo.

echo Step 1: Updating Maven version
call "%SCRIPT_DIR%\bump-version.bat" %VERSION% || goto :fail

echo.
echo Step 2: Building Windows release
call "%SCRIPT_DIR%\build-shale-release.bat" || goto :fail

echo.
echo Step 3: Checking Mac handoff
if exist "%MAC_META_FILE%" (
    echo Found Mac metadata: %MAC_META_FILE%

    for /f "usebackq delims=" %%L in (`powershell -NoProfile -Command ^
      "$ErrorActionPreference='Stop';" ^
      "$j = Get-Content '%MAC_META_FILE%' -Raw | ConvertFrom-Json;" ^
      "Write-Output ('VERSION=' + $j.version);" ^
      "Write-Output ('ZIP=' + $j.macZipName);" ^
      "Write-Output ('SHA=' + $j.macSha256)"`) do (
        set "LINE=%%L"
        if /I "!LINE:~0,8!"=="VERSION=" set "MAC_META_VERSION=!LINE:~8!"
        if /I "!LINE:~0,4!"=="ZIP=" set "MAC_ZIP_NAME=!LINE:~4!"
        if /I "!LINE:~0,4!"=="SHA=" set "MAC_SHA256=!LINE:~4!"
    )

    if "!MAC_META_VERSION!"=="" (
        echo Failed to read Mac metadata version.
        goto :fail
    )
    if /I not "!MAC_META_VERSION!"=="%VERSION%" (
        echo Mac metadata version mismatch.
        echo   Expected: %VERSION%
        echo   Found:    !MAC_META_VERSION!
        goto :fail
    )
    if "!MAC_ZIP_NAME!"=="" (
        echo Failed to read macZipName from Mac metadata.
        goto :fail
    )
    if "!MAC_SHA256!"=="" (
        echo Failed to read macSha256 from Mac metadata.
        goto :fail
    )

    set "MAC_SOURCE_ZIP=%MAC_HANDOFF_DIR%\!MAC_ZIP_NAME!"
    set "MAC_DIST_ZIP=%DIST%\!MAC_ZIP_NAME!"

    if not exist "!MAC_SOURCE_ZIP!" (
        echo Mac ZIP referenced by metadata was not found:
        echo   !MAC_SOURCE_ZIP!
        goto :fail
    )

    echo Copying Mac ZIP into dist...
    copy /Y "!MAC_SOURCE_ZIP!" "!MAC_DIST_ZIP!" >nul || goto :fail

    echo Mac ZIP:    !MAC_ZIP_NAME!
    echo Mac SHA256: !MAC_SHA256!
) else (
    echo No Mac handoff metadata found. Proceeding Windows-only.
)

echo.
echo Step 4: Updating manifest
if not "!MAC_ZIP_NAME!"=="" if not "!MAC_SHA256!"=="" (
    call "%SCRIPT_DIR%\update-manifest.bat" "!MAC_ZIP_NAME!" "!MAC_SHA256!" || goto :fail
) else (
    call "%SCRIPT_DIR%\update-manifest.bat" || goto :fail
)

echo.
echo Step 5: Copying manifest into dist
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
if not "!MAC_ZIP_NAME!"=="" echo %DIST%\!MAC_ZIP_NAME!
echo %DIST%\shale-stable.json
echo.

exit /b 0

:fail
echo.
echo ====================================
echo RELEASE FAILED
echo ====================================
exit /b 1
@echo off
setlocal

if "%~1"=="" (
    echo Usage: release-all.bat ^<version^>
    exit /b 1
)

set VERSION=%~1
set MAC_HOST=admin@192.168.1.56
set MAC_REPO=/Users/admin/Documents/Shale
set MAC_DIST=%MAC_REPO%/dist-macos

set ROOT=%~dp0..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

set HANDOFF=%ROOT%\build\mac-handoff

echo ====================================
echo Full cross-platform release %VERSION%
echo ====================================
echo.

echo Step 1: Run Mac build via SSH
ssh %MAC_HOST% "cd %MAC_REPO% && ./build/scripts/prepare-shale-mac-release.sh codex/latest %VERSION%" || goto :fail

echo.
echo Step 2: Fetch Mac artifacts

if not exist "%HANDOFF%" mkdir "%HANDOFF%"

scp "%MAC_HOST%:%MAC_DIST%/ShaleApp-%VERSION%-mac.zip" "%HANDOFF%"
scp "%MAC_HOST%:%MAC_DIST%/shale-mac-release.json" "%HANDOFF%"

echo.
echo Step 3: Run Windows release + publish
call "%ROOT%\build\scripts\release-and-publish.bat" %VERSION% || goto :fail

echo.
echo ====================================
echo Full release complete
echo ====================================
exit /b 0

:fail
echo.
echo ====================================
echo FULL RELEASE FAILED
echo ====================================
exit /b 1
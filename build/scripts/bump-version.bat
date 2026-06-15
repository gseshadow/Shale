@echo off
setlocal
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

if "%~1"=="" (
    echo Usage: bump-version.bat ^<newVersion^>
    echo Example: bump-version.bat 1.0.2
    
    exit /b 1
)

set NEW_VERSION=%~1
for /f "usebackq delims=" %%V in (`python "%SCRIPT_DIR%preflight-version.py" "%ROOT%" --print-root-version`) do set "PREVIOUS_VERSION=%%V"

echo ====================================
echo Bumping Shale version to %NEW_VERSION%
echo ====================================

call mvn -f "%ROOT%\pom.xml" versions:set -DnewVersion=%NEW_VERSION% || goto :fail
call mvn -f "%ROOT%\pom.xml" versions:commit || goto :fail
call python "%SCRIPT_DIR%preflight-version.py" "%ROOT%" "%PREVIOUS_VERSION%" || goto :fail

echo Version updated to %NEW_VERSION%
exit /b 0

:fail
echo Version update failed.
exit /b 1

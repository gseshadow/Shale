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

echo ====================================
echo Bumping Shale version to %NEW_VERSION%
echo ====================================

call mvn -f "%ROOT%\pom.xml" versions:set -DnewVersion=%NEW_VERSION% || goto :fail
call mvn -f "%ROOT%\pom.xml" versions:commit || goto :fail

echo Version updated to %NEW_VERSION%
exit /b 0

:fail
echo Version update failed.
exit /b 1
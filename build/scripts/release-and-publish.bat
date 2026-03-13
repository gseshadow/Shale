@echo off
setlocal
cd /d "%~dp0"

if "%~1"=="" (
    echo Usage: release-and-publish.bat ^<version^>
    exit /b 1
)

call "%~dp0release.bat" %~1 || exit /b 1
call "%~dp0publish-update.bat" || exit /b 1
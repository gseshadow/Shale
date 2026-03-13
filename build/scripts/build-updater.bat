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

echo ====================================
echo Building Shale Updater version %VERSION%
echo ====================================

set UPDATER_TARGET=%ROOT%\shale-updater\target
set DESKTOP_TARGET=%ROOT%\shale-desktop\target
set DIST_UPDATER=%ROOT%\dist-updater

if not exist "%DIST_UPDATER%" mkdir "%DIST_UPDATER%"

call mvn -f "%ROOT%\pom.xml" -pl shale-updater -am clean package || goto :fail

rmdir /s /q "%DIST_UPDATER%\ShaleUpdater" 2>nul

jpackage ^
  --type app-image ^
  --name ShaleUpdater ^
  --input "%UPDATER_TARGET%" ^
  --dest "%DIST_UPDATER%" ^
  --main-jar "shale-updater-%VERSION%.jar" ^
  --main-class "com.shale.updater.Main" ^
  --add-modules java.net.http,java.logging,jdk.crypto.ec ^
  --win-console || goto :fail

rmdir /s /q "%DESKTOP_TARGET%\updater" 2>nul
mkdir "%DESKTOP_TARGET%\updater"

echo Staging updater into desktop target...
xcopy "%DIST_UPDATER%\ShaleUpdater\*" "%DESKTOP_TARGET%\updater\" /E /I /Y || goto :fail

echo Updater staged successfully.
exit /b 0

:fail
echo build-updater.bat failed.
exit /b 1
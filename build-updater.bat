@echo off
setlocal
cd /d "%~dp0"

for /f %%i in ('powershell -NoProfile -Command "$m = [regex]::Match((Get-Content '%~dp0pom.xml' -Raw), '<version>([^<]+)</version>'); if ($m.Success) { $m.Groups[1].Value }"') do set VERSION=%%i
if "%VERSION%"=="" (
  echo Failed to resolve version from pom.xml
  pause
  exit /b 1
)

echo Building Shale Updater version %VERSION%

set ROOT=C:\Eclipse\Workspace\shale-parent
set UPDATER_TARGET=%ROOT%\shale-updater\target
set DESKTOP_TARGET=%ROOT%\shale-desktop\target
set DIST_UPDATER=%ROOT%\dist-updater

if not exist "%DIST_UPDATER%" mkdir "%DIST_UPDATER%"

call mvn -pl shale-updater -am clean package || exit /b 1

rmdir /s /q "%DIST_UPDATER%\ShaleUpdater" 2>nul

jpackage ^
  --type app-image ^
  --name ShaleUpdater ^
  --input "%UPDATER_TARGET%" ^
  --dest "%DIST_UPDATER%" ^
  --main-jar "shale-updater-%VERSION%.jar" ^
  --main-class "com.shale.updater.Main" ^
  --add-modules java.net.http,java.logging,jdk.crypto.ec ^
  --win-console || exit /b 1

rmdir /s /q "%DESKTOP_TARGET%\updater" 2>nul
mkdir "%DESKTOP_TARGET%\updater"

echo Staging updater into desktop target...
xcopy "%DIST_UPDATER%\ShaleUpdater\*" "%DESKTOP_TARGET%\updater\" /E /I /Y || exit /b 1

echo Updater staged successfully.
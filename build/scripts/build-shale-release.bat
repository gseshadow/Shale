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
echo Building Shale Release %VERSION%
echo ====================================

set DESKTOP_TARGET=%ROOT%\shale-desktop\target
set DIST=%ROOT%\dist
set DIST_APP=%ROOT%\dist-appimage
set ASSETS_DIR=%ROOT%\build\assets
set JMODS_DIR=%ASSETS_DIR%\javafx-jmods-21.0.10

if not exist "%DIST%" mkdir "%DIST%"
if not exist "%DIST_APP%" mkdir "%DIST_APP%"

echo Cleaning dist folders...
if exist "%DIST%\*" del /q "%DIST%\*" 2>nul
if exist "%DIST_APP%\Shale" rmdir /s /q "%DIST_APP%\Shale" 2>nul
if exist "%ROOT%\dist-updater\ShaleUpdater" rmdir /s /q "%ROOT%\dist-updater\ShaleUpdater" 2>nul
echo Dist cleanup complete.
echo.

call mvn -f "%ROOT%\pom.xml" -pl shale-desktop -am clean package || goto :fail
call "%ROOT%\build\scripts\build-updater.bat" || goto :fail

jpackage ^
  --type app-image ^
  --name Shale ^
  --input "%DESKTOP_TARGET%" ^
  --dest "%DIST_APP%" ^
  --main-jar "shale-desktop-%VERSION%.jar" ^
  --main-class "com.shale.desktop.MainApp" ^
  --module-path "%JMODS_DIR%" ^
  --add-modules javafx.controls,javafx.fxml,java.sql,java.naming,java.net.http,jdk.crypto.ec ^
  --icon "%ASSETS_DIR%\Shale.ico" || goto :fail

powershell -NoProfile -Command "Compress-Archive -Path '%DIST_APP%\Shale\*' -DestinationPath '%DIST%\ShaleApp-%VERSION%.zip' -Force" || goto :fail

jpackage ^
  --type exe ^
  --name Shale ^
  --input "%DESKTOP_TARGET%" ^
  --dest "%DIST%" ^
  --main-jar "shale-desktop-%VERSION%.jar" ^
  --main-class "com.shale.desktop.MainApp" ^
  --module-path "%JMODS_DIR%" ^
  --add-modules javafx.controls,javafx.fxml,java.sql,java.naming,java.net.http,jdk.crypto.ec ^
  --icon "%ASSETS_DIR%\Shale.ico" ^
  --app-version "%VERSION%" ^
  --vendor "Get Downing" ^
  --description "Shale Desktop" ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser ^
  --win-per-user-install ^
  --install-dir "Shale" || goto :fail

echo Release build complete.
exit /b 0

:fail
echo build-shale-release.bat failed.
exit /b 1
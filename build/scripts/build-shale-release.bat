@echo off
setlocal
cd /d "%~dp0"

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "ROOT=%%~fI"

for /f %%i in ('powershell -NoProfile -Command "$m = [regex]::Match((Get-Content \"%ROOT%\pom.xml\" -Raw), '<version>([^<]+)</version>'); if ($m.Success) { $m.Groups[1].Value }"') do set VERSION=%%i
if "%VERSION%"=="" (
    echo Failed to resolve version from pom.xml
    exit /b 1
)

echo ====================================
echo Building Shale Release %VERSION%
echo ====================================

set DESKTOP_TARGET=%ROOT%\shale-desktop\target
set UPDATER_TARGET=%ROOT%\shale-updater\target
set DESKTOP_JAR=%DESKTOP_TARGET%\shale-desktop-%VERSION%.jar
set UPDATER_JAR=%UPDATER_TARGET%\shale-updater-%VERSION%.jar
set DESKTOP_LIB=%DESKTOP_TARGET%\lib
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

pushd "%ROOT%" || goto :fail
call mvn -f "%ROOT%\pom.xml" -pl shale-desktop,shale-updater -am clean package
if errorlevel 1 (
    popd
    goto :fail
)
popd

if not exist "%DESKTOP_JAR%" goto :missing_desktop_jar
if not exist "%UPDATER_JAR%" goto :missing_updater_jar
if not exist "%DESKTOP_LIB%\" goto :missing_desktop_lib

call "%ROOT%\build\scripts\build-updater.bat" --package-only || goto :fail

echo Building desktop application image...
jpackage ^
  --type app-image ^
  --name Shale ^
  --input "%DESKTOP_TARGET%" ^
  --dest "%DIST_APP%" ^
  --main-jar "shale-desktop-%VERSION%.jar" ^
  --main-class "com.shale.desktop.ShaleLauncher" ^
  --module-path "%JMODS_DIR%" ^
  --add-modules javafx.controls,javafx.fxml,java.sql,java.naming,java.net.http,jdk.crypto.ec ^
  --icon "%ASSETS_DIR%\Shale.ico" || goto :fail

python "%ROOT%\build\scripts\windows_msi_payload.py" config "%DIST_APP%\Shale\app\Shale.cfg" || goto :fail

powershell -NoProfile -Command "Compress-Archive -Path '%DIST_APP%\Shale\*' -DestinationPath '%DIST%\ShaleApp-%VERSION%.zip' -Force" || goto :fail

echo Starting validated Windows MSI build...
call "%ROOT%\build\scripts\build-shale-windows-msi.bat" || goto :fail

echo Release build complete.
exit /b 0

:missing_desktop_jar
echo Combined Maven build completed but the exact-version desktop JAR is missing.
echo Missing artifact: desktop JAR
echo Expected path: "%DESKTOP_JAR%"
echo Resolved root-POM version: "%VERSION%"
goto :fail

:missing_updater_jar
echo Combined Maven build completed but the exact-version updater JAR is missing.
echo Missing artifact: updater JAR
echo Expected path: "%UPDATER_JAR%"
echo Resolved root-POM version: "%VERSION%"
goto :fail

:missing_desktop_lib
echo Combined Maven build completed but the desktop dependency directory is missing.
echo Missing artifact: desktop runtime dependency directory
echo Expected path: "%DESKTOP_LIB%"
echo Resolved root-POM version: "%VERSION%"
goto :fail

:fail
echo build-shale-release.bat failed.
exit /b 1

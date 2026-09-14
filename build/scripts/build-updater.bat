@echo off
setlocal
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

set PACKAGE_ONLY=false
if "%~1"=="" goto :arguments_valid
if /I not "%~1"=="--package-only" goto :usage
if not "%~2"=="" goto :usage
set PACKAGE_ONLY=true

:arguments_valid

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
set UPDATER_INPUT=%ROOT%\build\staging\updater-input
set UPDATER_JAR=%UPDATER_TARGET%\shale-updater-%VERSION%.jar

if not exist "%DIST_UPDATER%" mkdir "%DIST_UPDATER%"

if /I "%PACKAGE_ONLY%"=="true" goto :verify_updater_jar
call mvn -f "%ROOT%\pom.xml" -pl shale-updater -am clean package || goto :fail

:verify_updater_jar
if not exist "%UPDATER_JAR%" goto :missing_updater_jar

rmdir /s /q "%DIST_UPDATER%\ShaleUpdater" 2>nul
rmdir /s /q "%UPDATER_INPUT%" 2>nul
mkdir "%UPDATER_INPUT%" || goto :fail
copy /y "%UPDATER_TARGET%\shale-updater-%VERSION%.jar" "%UPDATER_INPUT%\" >nul || goto :fail

jpackage ^
  --type app-image ^
  --name ShaleUpdater ^
  --input "%UPDATER_INPUT%" ^
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

:usage
echo Usage: build-updater.bat [--package-only]
echo Unknown or extra arguments are not supported.
exit /b 2

:missing_updater_jar
echo Required exact-version updater JAR is missing.
echo Expected path: "%UPDATER_JAR%"
echo Resolved root-POM version: "%VERSION%"
if /I "%PACKAGE_ONLY%"=="true" echo Package-only mode requires this artifact from the immediately preceding authoritative Maven reactor.
goto :fail

:fail
echo build-updater.bat failed.
exit /b 1

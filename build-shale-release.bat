@echo off
setlocal
cd /d "%~dp0"

for /f %%i in ('powershell -NoProfile -Command "$m = [regex]::Match((Get-Content '%~dp0pom.xml' -Raw), '<version>([^<]+)</version>'); if ($m.Success) { $m.Groups[1].Value }"') do set VERSION=%%i
if "%VERSION%"=="" (
  echo Failed to resolve version from pom.xml
  pause
  exit /b 1
)

echo Building Shale version %VERSION%


set ROOT=C:\Eclipse\Workspace\shale-parent
set DESKTOP_TARGET=%ROOT%\shale-desktop\target
set DIST=%ROOT%\dist
set DIST_APP=%ROOT%\dist-appimage

if not exist "%DIST%" mkdir "%DIST%"
if not exist "%DIST_APP%" mkdir "%DIST_APP%"

call mvn -pl shale-desktop -am clean package || exit /b 1
call "%ROOT%\build-updater.bat" || exit /b 1

rmdir /s /q "%DIST_APP%\Shale" 2>nul

jpackage ^
  --type app-image ^
  --name Shale ^
  --input "%DESKTOP_TARGET%" ^
  --dest "%DIST_APP%" ^
  --main-jar "shale-desktop-%VERSION%.jar" ^
  --main-class "com.shale.desktop.MainApp" ^
  --module-path "C:\Shale\javafx-jmods-21.0.10" ^
  --add-modules javafx.controls,javafx.fxml,java.sql,java.naming,java.net.http,jdk.crypto.ec ^
  --icon "C:\Shale\Images\Shale.ico" || exit /b 1

del /q "%DIST%\ShaleApp-%VERSION%.zip" 2>nul
powershell -NoProfile -Command "Compress-Archive -Path '%DIST_APP%\Shale\*' -DestinationPath '%DIST%\ShaleApp-%VERSION%.zip' -Force" || exit /b 1

del /q "%DIST%\Shale-%VERSION%.exe" 2>nul
jpackage ^
  --type exe ^
  --name Shale ^
  --input "%DESKTOP_TARGET%" ^
  --dest "%DIST%" ^
  --main-jar "shale-desktop-%VERSION%.jar" ^
  --main-class "com.shale.desktop.MainApp" ^
  --module-path "C:\Shale\javafx-jmods-21.0.10" ^
  --add-modules javafx.controls,javafx.fxml,java.sql,java.naming,java.net.http,jdk.crypto.ec ^
  --icon "C:\Shale\Images\Shale.ico" ^
  --app-version "%VERSION%" ^
  --vendor "Get Downing" ^
  --description "Shale Desktop" ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser ^
  --win-per-user-install ^
  --install-dir "Shale" || exit /b 1

echo Release build complete.
pause
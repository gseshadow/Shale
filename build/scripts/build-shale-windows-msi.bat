@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
set ROOT=%~dp0..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI
for /f %%i in ('powershell -NoProfile -Command "$m = [regex]::Match((Get-Content '%ROOT%\pom.xml' -Raw), '<version>([^<]+)</version>'); if ($m.Success) { $m.Groups[1].Value }"') do set VERSION=%%i
if "%VERSION%"=="" exit /b 10
for /f "tokens=2" %%i in ('java -version 2^>^&1 ^| findstr /r "version \"21\."') do set JDK21=%%i
if "%JDK21%"=="" (echo JDK 21 is required.& exit /b 11)
where candle.exe >nul 2>nul || (echo WiX 3 candle.exe is required.& exit /b 12)
where light.exe >nul 2>nul || (echo WiX 3 light.exe is required.& exit /b 13)
where dark.exe >nul 2>nul || (echo WiX 3 dark.exe is required.& exit /b 14)

set STAGE=%ROOT%\build\staging\windows-msi
set PRELIM=%STAGE%\preliminary
set TEMP=%STAGE%\jpackage-temp
set FINAL=%STAGE%\final
set APPINPUT=%ROOT%\shale-desktop\target
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%PRELIM%" "%FINAL%" || exit /b 15

call "%ROOT%\build\native\windows-toast\build-native.bat" "%APPINPUT%\native" || exit /b 16
copy /y "%ROOT%\build\packaging\windows\shale-windows-toast.properties" "%APPINPUT%\shale-windows-toast.properties" >nul || exit /b 17

jpackage --type msi --name Shale --input "%APPINPUT%" --dest "%PRELIM%" --temp "%TEMP%" --verbose ^
 --main-jar "shale-desktop-%VERSION%.jar" --main-class com.shale.desktop.MainApp ^
 --icon "%ROOT%\build\assets\Shale.ico" --app-version "%VERSION%" --vendor "Get Downing" ^
 --description "Shale Desktop" --win-menu --win-shortcut --win-dir-chooser --win-per-user-install --install-dir Shale || exit /b 18

for /r "%TEMP%" %%F in (bundle.wxf) do set BUNDLE=%%F
if not defined BUNDLE (echo bundle.wxf not found.& exit /b 19)
python "%ROOT%\build\scripts\windows_msi_identity.py" mutate "%BUNDLE%" || exit /b 20
for %%I in ("%BUNDLE%") do set CONFIG=%%~dpI
for /r "%TEMP%" %%F in (bundle.wixobj) do set BUNDLEOBJ=%%F
for /r "%TEMP%" %%F in (main.wixobj) do set MAINOBJ=%%F
for /r "%TEMP%" %%F in (ui.wixobj) do set UIOBJ=%%F
if not defined BUNDLEOBJ exit /b 21
candle.exe -nologo "%BUNDLE%" -ext WixUtilExtension -arch x64 -out "%BUNDLEOBJ%" || exit /b 22
set LOC=
for %%F in ("%CONFIG%\*.wxl") do set LOC=!LOC! -loc "%%~fF"
set FINALMSI=%FINAL%\Shale-%VERSION%.msi
light.exe -nologo -spdb -ext WixUtilExtension -sice:ICE27 -sice:ICE91 !LOC! -cultures:en-us ^
 -out "%FINALMSI%" "%MAINOBJ%" "%BUNDLEOBJ%" "%UIOBJ%" || exit /b 23
mkdir "%STAGE%\dark" || exit /b 24
dark.exe -x "%STAGE%\dark\payload" -o "%STAGE%\dark\final.wxs" "%FINALMSI%" || exit /b 25
python "%ROOT%\build\scripts\windows_msi_identity.py" validate "%STAGE%\dark\final.wxs" || exit /b 26
dir /s /b "%STAGE%\dark\payload\shale-windows-toast.properties" >nul 2>nul || (echo Installed marker missing.& exit /b 27)
dir /s /b "%STAGE%\dark\payload\shale_windows_toast.dll" >nul 2>nul || (echo JNI DLL missing.& exit /b 28)
copy /y "%FINALMSI%" "%ROOT%\dist\Shale-%VERSION%.msi.new" >nul || exit /b 29
move /y "%ROOT%\dist\Shale-%VERSION%.msi.new" "%ROOT%\dist\Shale-%VERSION%.msi" >nul || exit /b 30
echo Validated MSI published: Shale-%VERSION%.msi
exit /b 0

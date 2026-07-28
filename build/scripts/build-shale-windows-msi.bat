@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
set ROOT=%~dp0..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI
for /f %%i in ('powershell -NoProfile -Command "$m = [regex]::Match((Get-Content '%ROOT%\pom.xml' -Raw), '<version>([^<]+)</version>'); if ($m.Success) { $m.Groups[1].Value }"') do set VERSION=%%i
if "%VERSION%"=="" exit /b 10

echo Validating Windows MSI toolchain...
set JDK_VERSION_LOG=%TEMP%\shale-jdk-version-%RANDOM%.txt
java -version >"%JDK_VERSION_LOG%" 2>&1
if errorlevel 1 goto :missing_jdk21
findstr /r /c:"^openjdk version .*21[.]" /c:"^java version .*21[.]" "%JDK_VERSION_LOG%" >nul
if errorlevel 1 goto :missing_jdk21
del /q "%JDK_VERSION_LOG%" >nul 2>nul
call :require_tool candle.exe 12
if errorlevel 1 exit /b %errorlevel%
call :require_tool light.exe 13
if errorlevel 1 exit /b %errorlevel%
call :require_tool dark.exe 14
if errorlevel 1 exit /b %errorlevel%
goto :toolchain_ready

:missing_jdk21
del /q "%JDK_VERSION_LOG%" >nul 2>nul
echo JDK 21 is required.
exit /b 11

:toolchain_ready

set STAGE=%ROOT%\build\staging\windows-msi
set PRELIM=%STAGE%\preliminary
set JPACKAGE_TEMP=%STAGE%\jpackage-temp
set GENERATED_CONFIG_DIR=%JPACKAGE_TEMP%\config
set BUNDLE_SOURCE=%GENERATED_CONFIG_DIR%\bundle.wxf
set WIXOBJ_DIR=%JPACKAGE_TEMP%\wixobj
set BUNDLE_WIXOBJ=%WIXOBJ_DIR%\bundle.wixobj
set MAIN_WIXOBJ=%WIXOBJ_DIR%\main.wixobj
set UI_WIXOBJ=%WIXOBJ_DIR%\ui.wixobj
set FINAL=%STAGE%\final
set PRELIMINARY_MSI=%PRELIM%\Shale-%VERSION%.msi
set FINAL_MSI=%FINAL%\Shale-%VERSION%.msi
set APPINPUT=%ROOT%\shale-desktop\target
if exist "%STAGE%" rmdir /s /q "%STAGE%"
mkdir "%PRELIM%" "%FINAL%" || exit /b 15

call "%ROOT%\build\native\windows-toast\build-native.bat" "%APPINPUT%\native" || exit /b 16
copy /y "%ROOT%\build\packaging\windows\shale-windows-toast.properties" "%APPINPUT%\shale-windows-toast.properties" >nul || exit /b 17

echo Starting jpackage and WiX MSI construction...
jpackage --type msi --name Shale --input "%APPINPUT%" --dest "%PRELIM%" --temp "%JPACKAGE_TEMP%" --verbose ^
 --main-jar "shale-desktop-%VERSION%.jar" --main-class com.shale.desktop.MainApp ^
 --icon "%ROOT%\build\assets\Shale.ico" --app-version "%VERSION%" --vendor "Get Downing" ^
 --description "Shale Desktop" --win-menu --win-shortcut --win-dir-chooser --win-per-user-install --install-dir Shale || exit /b 18
echo Preliminary jpackage MSI completed.
if not exist "%PRELIMINARY_MSI%" goto :missing_preliminary_msi
if not exist "%BUNDLE_SOURCE%" goto :missing_bundle
echo Generated bundle.wxf location verified: "%BUNDLE_SOURCE%"
python "%ROOT%\build\scripts\windows_msi_identity.py" inspect "%BUNDLE_SOURCE%" || exit /b 20
echo Generated WiX identity validation passed.
python "%ROOT%\build\scripts\windows_msi_identity.py" mutate "%BUNDLE_SOURCE%" || exit /b 20
echo bundle.wxf identity and shortcut mutation completed.

if not exist "%WIXOBJ_DIR%" goto :missing_wixobj_dir
if not exist "%MAIN_WIXOBJ%" goto :missing_main_wixobj
if not exist "%UI_WIXOBJ%" goto :missing_ui_wixobj
echo Starting final candle and light MSI reconstruction...
candle.exe -nologo "%BUNDLE_SOURCE%" -ext WixUtilExtension -arch x64 -out "%BUNDLE_WIXOBJ%" || exit /b 22
if not exist "%BUNDLE_WIXOBJ%" goto :missing_bundle_wixobj
set LOC=
for %%F in ("%GENERATED_CONFIG_DIR%\*.wxl") do set LOC=!LOC! -loc "%%~fF"
light.exe -nologo -spdb -ext WixUtilExtension -sice:ICE27 -sice:ICE91 !LOC! -cultures:en-us ^
 -out "%FINAL_MSI%" "%MAIN_WIXOBJ%" "%BUNDLE_WIXOBJ%" "%UI_WIXOBJ%" || exit /b 23
echo Starting compiled final MSI validation...
mkdir "%STAGE%\dark" || exit /b 24
dark.exe -x "%STAGE%\dark\payload" -o "%STAGE%\dark\final.wxs" "%FINAL_MSI%" || exit /b 25
python "%ROOT%\build\scripts\windows_msi_identity.py" validate "%STAGE%\dark\final.wxs" || exit /b 26
dir /s /b "%STAGE%\dark\payload\shale-windows-toast.properties" >nul 2>nul
if errorlevel 1 goto :missing_marker
dir /s /b "%STAGE%\dark\payload\shale_windows_toast.dll" >nul 2>nul
if errorlevel 1 goto :missing_dll
copy /y "%FINAL_MSI%" "%ROOT%\dist\Shale-%VERSION%.msi.new" >nul || exit /b 29
move /y "%ROOT%\dist\Shale-%VERSION%.msi.new" "%ROOT%\dist\Shale-%VERSION%.msi" >nul || exit /b 30
echo Final MSI publication completed: Shale-%VERSION%.msi
exit /b 0

:missing_bundle
echo Generated bundle.wxf was not found at expected path: "%BUNDLE_SOURCE%"
exit /b 19

:missing_preliminary_msi
echo Preliminary jpackage MSI was not found at expected path: "%PRELIMINARY_MSI%"
exit /b 31

:missing_wixobj_dir
echo Generated WiX object directory was not found: "%WIXOBJ_DIR%"
exit /b 32

:missing_main_wixobj
echo Generated main.wixobj was not found: "%MAIN_WIXOBJ%"
exit /b 33

:missing_ui_wixobj
echo Generated ui.wixobj was not found: "%UI_WIXOBJ%"
exit /b 34

:missing_bundle_wixobj
echo Recompiled bundle.wixobj was not found: "%BUNDLE_WIXOBJ%"
exit /b 35

:missing_marker
echo Installed marker missing.
exit /b 27

:missing_dll
echo JNI DLL missing.
exit /b 28

:require_tool
where "%~1" >nul 2>nul
if not errorlevel 1 exit /b 0
echo Required Windows MSI tool not found: %~1
exit /b %~2

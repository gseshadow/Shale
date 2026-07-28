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
set INSTALLDIR_DIALOG_WIXOBJ=%WIXOBJ_DIR%\InstallDirNotEmptyDlg.wixobj
set LOC_DE=%GENERATED_CONFIG_DIR%\MsiInstallerStrings_de.wxl
set LOC_EN=%GENERATED_CONFIG_DIR%\MsiInstallerStrings_en.wxl
set LOC_JA=%GENERATED_CONFIG_DIR%\MsiInstallerStrings_ja.wxl
set LOC_ZH_CN=%GENERATED_CONFIG_DIR%\MsiInstallerStrings_zh_CN.wxl
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
 --main-jar "shale-desktop-%VERSION%.jar" --main-class com.shale.desktop.ShaleLauncher ^
 --icon "%ROOT%\build\assets\Shale.ico" --app-version "%VERSION%" --vendor "Get Downing" ^
 --description "Shale Desktop" --win-menu --win-shortcut --win-dir-chooser --win-per-user-install --install-dir Shale || exit /b 18
echo Preliminary jpackage MSI completed.
if not exist "%PRELIMINARY_MSI%" goto :missing_preliminary_msi
if not exist "%BUNDLE_SOURCE%" goto :missing_bundle
echo Generated bundle.wxf location verified: "%BUNDLE_SOURCE%"
python "%ROOT%\build\scripts\windows_msi_payload.py" source "%BUNDLE_SOURCE%" || exit /b 20
python "%ROOT%\build\scripts\windows_msi_identity.py" inspect "%BUNDLE_SOURCE%" || exit /b 20
echo Generated WiX identity validation passed.
python "%ROOT%\build\scripts\windows_msi_identity.py" mutate "%BUNDLE_SOURCE%" || exit /b 20
echo bundle.wxf identity and shortcut mutation completed.

if not exist "%WIXOBJ_DIR%" goto :missing_wixobj_dir
if not exist "%MAIN_WIXOBJ%" goto :missing_main_wixobj
if not exist "%UI_WIXOBJ%" goto :missing_ui_wixobj
if not exist "%INSTALLDIR_DIALOG_WIXOBJ%" goto :missing_installdir_dialog_wixobj
if not exist "%GENERATED_CONFIG_DIR%" goto :missing_generated_config_dir
if not exist "%LOC_DE%" goto :missing_loc_de
if not exist "%LOC_EN%" goto :missing_loc_en
if not exist "%LOC_JA%" goto :missing_loc_ja
if not exist "%LOC_ZH_CN%" goto :missing_loc_zh_cn
echo Modified bundle.wxf recompilation started.
candle.exe -nologo "%BUNDLE_SOURCE%" -ext WixUtilExtension -arch x64 -out "%BUNDLE_WIXOBJ%"
if errorlevel 1 goto :candle_failed
if not exist "%BUNDLE_WIXOBJ%" goto :missing_bundle_wixobj
echo Recompiled bundle.wixobj verified: "%BUNDLE_WIXOBJ%"

set LINK_OBJECTS=
set LINK_OBJECT_COUNT=0
set BUNDLE_OBJECT_COUNT=0
for %%F in ("%WIXOBJ_DIR%\*.wixobj") do (
 if exist "%%~fF" (
  set LINK_OBJECTS=!LINK_OBJECTS! "%%~fF"
  set /a LINK_OBJECT_COUNT+=1 >nul
  if /i "%%~fF"=="%BUNDLE_WIXOBJ%" set /a BUNDLE_OBJECT_COUNT+=1 >nul
 )
)
if !LINK_OBJECT_COUNT! LSS 4 goto :missing_link_objects
if not !BUNDLE_OBJECT_COUNT! EQU 1 goto :duplicate_bundle_object
set LOC=-loc "%LOC_DE%" -loc "%LOC_EN%" -loc "%LOC_JA%" -loc "%LOC_ZH_CN%"
echo Final WiX link inputs verified: !LINK_OBJECT_COUNT! objects, 4 localization files, binding directory "%GENERATED_CONFIG_DIR%"
echo Final controlled light.exe arguments: -ext WixUtilExtension -ext WixUIExtension -b "%GENERATED_CONFIG_DIR%" -sice:ICE27 -sice:ICE91 -cultures:en-us -out "%FINAL_MSI%" [localizations] [all objects from "%WIXOBJ_DIR%"]
echo Final light.exe reconstruction started.
light.exe -nologo -spdb -ext WixUtilExtension -ext WixUIExtension -b "%GENERATED_CONFIG_DIR%" ^
 -sice:ICE27 -sice:ICE91 !LOC! -cultures:en-us -out "%FINAL_MSI%" !LINK_OBJECTS!
if errorlevel 1 goto :light_failed
echo Final light.exe reconstruction completed.
if not exist "%FINAL_MSI%" goto :missing_final_msi
echo Compiled final MSI validation started.
mkdir "%STAGE%\dark" || exit /b 24
dark.exe -x "%STAGE%\dark\payload" -o "%STAGE%\dark\final.wxs" "%FINAL_MSI%"
if errorlevel 1 goto :dark_failed
echo dark.exe inspection completed.
python "%ROOT%\build\scripts\windows_msi_identity.py" validate "%STAGE%\dark\final.wxs" || exit /b 26
python "%ROOT%\build\scripts\windows_msi_payload.py" compiled "%STAGE%\dark\final.wxs" || exit /b 27
echo Final identity and payload validation passed.
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

:missing_installdir_dialog_wixobj
echo Generated InstallDirNotEmptyDlg.wixobj was not found: "%INSTALLDIR_DIALOG_WIXOBJ%"
exit /b 36

:missing_generated_config_dir
echo Generated WiX binding and localization directory was not found: "%GENERATED_CONFIG_DIR%"
exit /b 37

:missing_bundle_wixobj
echo Recompiled bundle.wixobj was not found: "%BUNDLE_WIXOBJ%"
exit /b 35

:missing_link_objects
echo Final WiX object input set is incomplete under: "%WIXOBJ_DIR%"
exit /b 38

:missing_localization
echo Required generated WiX localization file was not found: "%MISSING_LOCALIZATION%"
exit /b 39

:missing_loc_de
set MISSING_LOCALIZATION=%LOC_DE%
goto :missing_localization

:missing_loc_en
set MISSING_LOCALIZATION=%LOC_EN%
goto :missing_localization

:missing_loc_ja
set MISSING_LOCALIZATION=%LOC_JA%
goto :missing_localization

:missing_loc_zh_cn
set MISSING_LOCALIZATION=%LOC_ZH_CN%
goto :missing_localization

:duplicate_bundle_object
echo Final WiX object input must contain the recompiled bundle.wixobj exactly once: "%BUNDLE_WIXOBJ%"
exit /b 41

:candle_failed
echo Modified bundle.wxf recompilation failed: "%BUNDLE_SOURCE%"
exit /b 22

:light_failed
echo Final light.exe reconstruction failed for output: "%FINAL_MSI%"
exit /b 23

:missing_final_msi
echo Reconstructed final MSI was not found: "%FINAL_MSI%"
exit /b 40

:dark_failed
echo dark.exe compiled-MSI inspection failed for: "%FINAL_MSI%"
exit /b 25

:require_tool
where "%~1" >nul 2>nul
if not errorlevel 1 exit /b 0
echo Required Windows MSI tool not found: %~1
exit /b %~2

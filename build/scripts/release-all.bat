@echo off
setlocal EnableExtensions

if "%~1"=="" goto :usage
if "%~2"=="" goto :usage
if not "%~3"=="" goto :usage
set "VERSION=%~1"
set "MANDATORY_UPDATE=%~2"
if /I not "%MANDATORY_UPDATE%"=="true" if /I not "%MANDATORY_UPDATE%"=="false" goto :invalid_mandatory

for %%I in ("%~dp0..\..") do set "ROOT=%%~fI"
set "SCRIPT_DIR=%ROOT%\build\scripts"
set "DOWNSTREAM_SCRIPT=%SCRIPT_DIR%\release-and-publish.bat"
set "MAC_HOST=admin@192.168.1.56"
set "MAC_REPO=/Users/admin/Documents/Shale"
set "MAC_DIST=%MAC_REPO%/dist-macos"
set "HANDOFF=%ROOT%\build\mac-handoff"
set "MAC_ZIP=%HANDOFF%\ShaleApp-%VERSION%-mac.zip"
set "MAC_METADATA=%HANDOFF%\shale-mac-release.json"

cd /d "%ROOT%" || goto :root_unavailable

python -B "%SCRIPT_DIR%\prepare_release_notes.py" "%ROOT%" "%VERSION%"
set "PREPARE_EXIT=%ERRORLEVEL%"
if "%PREPARE_EXIT%"=="10" goto :notes_prepared
if not "%PREPARE_EXIT%"=="0" goto :notes_preparation_failed

echo Step 0: Git preflight before remote Mac build
python "%SCRIPT_DIR%\release_git_sync.py" preflight "%ROOT%" || goto :git_preflight_failed
set "SHALE_GIT_PREFLIGHT_DONE=true"
for /f "delims=" %%C in ('git rev-parse HEAD') do set "SOURCE_REVISION=%%C"
if not defined SOURCE_REVISION goto :git_preflight_failed

echo ====================================
echo Full cross-platform release %VERSION%
echo Mandatory update: %MANDATORY_UPDATE%
echo ====================================
echo.

echo Step 1: Run Mac build via SSH
echo Mac artifacts will be built from source revision: %SOURCE_REVISION%
ssh %MAC_HOST% "cd %MAC_REPO% && ./build/scripts/prepare-shale-mac-release.sh codex/latest %VERSION% %SOURCE_REVISION%" || goto :fail

echo.
echo Step 2: Fetch Mac artifacts
if not exist "%HANDOFF%" mkdir "%HANDOFF%" || goto :fail
scp "%MAC_HOST%:%MAC_DIST%/ShaleApp-%VERSION%-mac.zip" "%HANDOFF%" || goto :fail
scp "%MAC_HOST%:%MAC_DIST%/shale-mac-release.json" "%HANDOFF%" || goto :fail
if not exist "%MAC_ZIP%" goto :missing_mac_zip
if not exist "%MAC_METADATA%" goto :missing_mac_metadata

echo.
echo Step 3: Run Windows release + publish
echo Windows stage repository root: "%ROOT%"
echo Windows stage downstream script: "%DOWNSTREAM_SCRIPT%"
echo Windows stage working directory: "%CD%"
echo Windows stage version: "%VERSION%"
echo Windows stage mandatory flag: "%MANDATORY_UPDATE%"
echo Windows stage Mac ZIP: "%MAC_ZIP%"
echo Windows stage Mac metadata: "%MAC_METADATA%"
if not exist "%DOWNSTREAM_SCRIPT%" goto :missing_downstream
call :report_tool candle.exe || goto :fail
call :report_tool light.exe || goto :fail
call :report_tool dark.exe || goto :fail
call "%DOWNSTREAM_SCRIPT%" "%VERSION%" "%MANDATORY_UPDATE%"
if errorlevel 1 goto :windows_stage_failed

echo.
echo ====================================
echo Full release complete
echo ====================================
exit /b 0

:report_tool
set "TOOL_PATH="
for /f "delims=" %%T in ('where "%~1" 2^>nul') do if not defined TOOL_PATH set "TOOL_PATH=%%~fT"
if not defined TOOL_PATH (
    echo Required Windows tool was not found on PATH: %~1
    exit /b 1
)
echo Windows stage tool %~1: "%TOOL_PATH%"
exit /b 0

:usage
echo Usage: release-all.bat ^<version^> ^<true^|false^>
exit /b 2

:invalid_mandatory
echo Invalid mandatory update flag: "%MANDATORY_UPDATE%". Expected true or false.
exit /b 2

:root_unavailable
echo Repository root is unavailable: "%ROOT%"
exit /b 3

:notes_prepared
echo.
echo Release-notes draft preparation is complete. No build, upload, catalog import, or publication was started.
echo Review and edit: "%ROOT%\release-notes\%VERSION%.json"
echo Then rerun the exact same command:
echo build\scripts\release-all.bat %VERSION% %MANDATORY_UPDATE%
exit /b 0

:notes_preparation_failed
echo Release-notes draft preparation failed before the Git preflight, build, upload, catalog import, or publication.
goto :fail

:git_preflight_failed
echo Git preflight failed before the Mac or Windows build. Nothing was published.
goto :fail

:missing_mac_zip
echo Required fetched Mac ZIP was not found: "%MAC_ZIP%"
goto :fail

:missing_mac_metadata
echo Required fetched Mac metadata was not found: "%MAC_METADATA%"
goto :fail

:missing_downstream
echo Windows downstream release script was not found: "%DOWNSTREAM_SCRIPT%"
goto :fail

:windows_stage_failed
echo Windows downstream release stage failed: "%DOWNSTREAM_SCRIPT%"

:fail
echo.
echo ====================================
echo FULL RELEASE FAILED
echo ====================================
exit /b 1

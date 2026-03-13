@echo off
setlocal
cd /d "%~dp0"

set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI

for /f %%i in ('powershell -NoProfile -Command "$m = [regex]::Match((Get-Content \"%ROOT%\pom.xml\" -Raw), '<version>([^<]+)</version>'); if ($m.Success) { $m.Groups[1].Value }"') do set VERSION=%%i
if "%VERSION%"=="" exit /b 1

set DIST=%ROOT%\dist
set STORAGE_ACCOUNT=shalestorage
set CONTAINER=$web

set EXE_FILE=%DIST%\Shale-%VERSION%.exe
set ZIP_FILE=%DIST%\ShaleApp-%VERSION%.zip
set JSON_FILE=%DIST%\shale-stable.json

if not exist "%EXE_FILE%" exit /b 1
if not exist "%ZIP_FILE%" exit /b 1
if not exist "%JSON_FILE%" exit /b 1

echo ====================================
echo Publishing Shale %VERSION%
echo ====================================
echo.

echo Azure account:
call az account show || exit /b 1
echo.

echo Uploading installer...
call az storage blob upload --account-name "%STORAGE_ACCOUNT%" --container-name "%CONTAINER%" --name "Shale-%VERSION%.exe" --file "%EXE_FILE%" --overwrite true --auth-mode login --no-progress --only-show-errors --output none || exit /b 1
echo Installer uploaded.
echo.

echo Uploading update zip...
call az storage blob upload --account-name "%STORAGE_ACCOUNT%" --container-name "%CONTAINER%" --name "ShaleApp-%VERSION%.zip" --file "%ZIP_FILE%" --overwrite true --auth-mode login --no-progress --only-show-errors --output none || exit /b 1
echo Zip uploaded.
echo.

echo Uploading manifest...
call az storage blob upload --account-name "%STORAGE_ACCOUNT%" --container-name "%CONTAINER%" --name "shale-stable.json" --file "%JSON_FILE%" --overwrite true --auth-mode login --no-progress --only-show-errors --output none || exit /b 1
echo Manifest uploaded.
echo.

echo Pruning old installers and zips (keeping newest 2)...
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $acct='%STORAGE_ACCOUNT%'; $container='%CONTAINER%'; $blobs = az storage blob list --account-name $acct --container-name $container --auth-mode login | ConvertFrom-Json; $installers = $blobs | Where-Object { $_.name -like 'Shale-*.exe' } | Sort-Object lastModified -Descending; $zips = $blobs | Where-Object { $_.name -like 'ShaleApp-*.zip' } | Sort-Object lastModified -Descending; $installers | Select-Object -Skip 2 | ForEach-Object { Write-Host ('Deleting ' + $_.name); az storage blob delete --account-name $acct --container-name $container --name $_.name --auth-mode login --only-show-errors --output none | Out-Null }; $zips | Select-Object -Skip 2 | ForEach-Object { Write-Host ('Deleting ' + $_.name); az storage blob delete --account-name $acct --container-name $container --name $_.name --auth-mode login --only-show-errors --output none | Out-Null }" || exit /b 1
echo Prune complete.
echo.

echo Verifying blobs:
call az storage blob list --account-name "%STORAGE_ACCOUNT%" --container-name "%CONTAINER%" --auth-mode login --output table || exit /b 1

echo.
echo Publish complete.
exit /b 0
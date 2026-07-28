@echo off
setlocal
set SCRIPT_DIR=%~dp0
set ROOT=%SCRIPT_DIR%..\..\..
for %%I in ("%ROOT%") do set ROOT=%%~fI
if /I not "%PROCESSOR_ARCHITECTURE%"=="AMD64" if /I not "%PROCESSOR_ARCHITEW6432%"=="AMD64" exit /b 2
if "%JAVA_HOME%"=="" exit /b 3
where cl.exe >nul 2>nul || exit /b 4
where dumpbin.exe >nul 2>nul || exit /b 8
if "%~1"=="" exit /b 5
set OUTPUT_DIR=%~f1
set BUILD_DIR=%ROOT%\build\staging\windows-toast-native
set DEPENDENCY_DIR=%ROOT%\build\staging\windows-toast-dependencies
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
if not exist "%OUTPUT_DIR%" goto :missing_output_dir
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if not exist "%BUILD_DIR%" goto :missing_build_dir
if not exist "%DEPENDENCY_DIR%" mkdir "%DEPENDENCY_DIR%"
if not exist "%DEPENDENCY_DIR%" goto :missing_dependency_dir
set DLL_PATH=%OUTPUT_DIR%\shale_windows_toast.dll
set DEPENDENCIES=%DEPENDENCY_DIR%\shale_windows_toast.dependencies.txt

echo Compiling native Windows toast DLL...
cl.exe /nologo /std:c++20 /EHsc /LD /O2 /MT /DWIN32_LEAN_AND_MEAN ^
  /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
  /Fo"%BUILD_DIR%\shale_windows_toast.obj" "%SCRIPT_DIR%shale_windows_toast.cpp" ^
  /link /OUT:"%DLL_PATH%" /IMPLIB:"%BUILD_DIR%\shale_windows_toast.lib" ^
  /PDB:"%BUILD_DIR%\shale_windows_toast.pdb" runtimeobject.lib windowsapp.lib
if errorlevel 1 goto :compile_failed
echo Native DLL compilation completed.
if not exist "%DLL_PATH%" goto :missing_dll
echo Native DLL location verified: "%DLL_PATH%"

echo Starting dumpbin dependency inspection...
dumpbin.exe /nologo /dependents "%DLL_PATH%" >"%DEPENDENCIES%"
if errorlevel 1 goto :dumpbin_failed
if not exist "%DEPENDENCIES%" goto :missing_dependency_report
python "%ROOT%\build\scripts\validate_windows_dll_dependencies.py" "%DEPENDENCIES%"
if errorlevel 1 goto :dependency_validation_failed
echo Dependency validation passed.
del /q "%DEPENDENCIES%" >nul 2>nul
exit /b 0

:missing_output_dir
echo Native DLL output directory could not be created: "%OUTPUT_DIR%"
exit /b 6

:missing_build_dir
echo Native intermediate directory could not be created: "%BUILD_DIR%"
exit /b 11

:missing_dependency_dir
echo Dependency report directory could not be created: "%DEPENDENCY_DIR%"
exit /b 12

:compile_failed
echo Native Windows toast DLL compilation failed.
exit /b 7

:missing_dll
echo Compiled native DLL was not found: "%DLL_PATH%"
exit /b 13

:dumpbin_failed
echo dumpbin dependency inspection failed for: "%DLL_PATH%"
exit /b 9

:missing_dependency_report
echo dumpbin dependency report was not created: "%DEPENDENCIES%"
exit /b 14

:dependency_validation_failed
echo Native DLL dependency validation failed for: "%DEPENDENCIES%"
exit /b 10

@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..\..") do set "ROOT=%%~fI"
set "NATIVE_STAGE=native-prerequisites"
echo Native stage started: %NATIVE_STAGE% script="%~f0"
if /I not "%PROCESSOR_ARCHITECTURE%"=="AMD64" if /I not "%PROCESSOR_ARCHITEW6432%"=="AMD64" goto :unsupported_architecture
if "%JAVA_HOME%"=="" goto :missing_java_home
if not exist "%JAVA_HOME%\include\jni.h" goto :missing_jni_headers
call :resolve_tool cl.exe CL_PATH
if errorlevel 1 goto :missing_cl
call :resolve_tool dumpbin.exe DUMPBIN_PATH
if errorlevel 1 goto :missing_dumpbin
if "%~1"=="" goto :missing_output_argument
set "OUTPUT_DIR=%~f1"
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
echo Native command tool=cl.exe resolved="!CL_PATH!" output="%DLL_PATH%"
"!CL_PATH!" /nologo /std:c++20 /EHsc /LD /O2 /MT /DWIN32_LEAN_AND_MEAN ^
  /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
  /Fo"%BUILD_DIR%\shale_windows_toast.obj" "%SCRIPT_DIR%shale_windows_toast.cpp" ^
  /link /OUT:"%DLL_PATH%" /IMPLIB:"%BUILD_DIR%\shale_windows_toast.lib" ^
  /PDB:"%BUILD_DIR%\shale_windows_toast.pdb" runtimeobject.lib windowsapp.lib
if errorlevel 1 goto :compile_failed
echo Native DLL compilation completed.
if not exist "%DLL_PATH%" goto :missing_dll
echo Native DLL location verified: "%DLL_PATH%"

echo Starting dumpbin dependency inspection...
echo Native command tool=dumpbin.exe resolved="!DUMPBIN_PATH!" report="%DEPENDENCIES%"
"!DUMPBIN_PATH!" /nologo /dependents "%DLL_PATH%" >"%DEPENDENCIES%"
if errorlevel 1 goto :dumpbin_failed
if not exist "%DEPENDENCIES%" goto :missing_dependency_report
python "%ROOT%\build\scripts\validate_windows_dll_dependencies.py" "%DEPENDENCIES%"
if errorlevel 1 goto :dependency_validation_failed
echo Dependency validation passed.
del /q "%DEPENDENCIES%" >nul 2>nul
echo Native stage completed: compilation-and-dependency-validation output="%DLL_PATH%"
exit /b 0

:resolve_tool
set "%~2="
for /f "delims=" %%T in ('where "%~1" 2^>nul') do if not defined %~2 set "%~2=%%~fT"
if not defined %~2 exit /b 1
echo Native prerequisite tool=%~1 resolved="!%~2!" check=PATH-resolution
exit /b 0

:unsupported_architecture
echo Native prerequisite failed: stage=%NATIVE_STAGE% tool=cl.exe classification=unsupported_architecture architecture="%PROCESSOR_ARCHITECTURE%" exit=2
exit /b 2

:missing_java_home
echo Native prerequisite failed: stage=%NATIVE_STAGE% tool=JNI classification=missing_java_home JAVA_HOME=^<unset^> exit=3
exit /b 3

:missing_jni_headers
echo Native prerequisite failed: stage=%NATIVE_STAGE% tool=JNI classification=missing_headers expected="%JAVA_HOME%\include\jni.h" exit=3
exit /b 3

:missing_cl
echo Native prerequisite failed: stage=%NATIVE_STAGE% tool=cl.exe classification=missing resolved=^<not-found^> check=PATH-resolution exit=4
exit /b 4

:missing_dumpbin
echo Native prerequisite failed: stage=%NATIVE_STAGE% tool=dumpbin.exe classification=missing resolved=^<not-found^> check=PATH-resolution exit=8
exit /b 8

:missing_output_argument
echo Native prerequisite failed: stage=%NATIVE_STAGE% classification=missing_output_argument script="%~f0" exit=5
exit /b 5

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
set "NATIVE_EXIT=%ERRORLEVEL%"
echo Native Windows toast DLL compilation failed: tool="!CL_PATH!" output="%DLL_PATH%" exit=!NATIVE_EXIT!
exit /b 7

:missing_dll
echo Compiled native DLL was not found: "%DLL_PATH%"
exit /b 13

:dumpbin_failed
set "NATIVE_EXIT=%ERRORLEVEL%"
echo dumpbin dependency inspection failed: tool="!DUMPBIN_PATH!" input="%DLL_PATH%" report="%DEPENDENCIES%" exit=!NATIVE_EXIT!
exit /b 9

:missing_dependency_report
echo dumpbin dependency report was not created: "%DEPENDENCIES%"
exit /b 14

:dependency_validation_failed
echo Native DLL dependency validation failed for: "%DEPENDENCIES%"
exit /b 10

@echo off
setlocal
if /I not "%PROCESSOR_ARCHITECTURE%"=="AMD64" if /I not "%PROCESSOR_ARCHITEW6432%"=="AMD64" exit /b 2
if "%JAVA_HOME%"=="" exit /b 3
where cl.exe >nul 2>nul || exit /b 4
where dumpbin.exe >nul 2>nul || exit /b 8
if "%~1"=="" exit /b 5
if not exist "%~1" mkdir "%~1" || exit /b 6
cl.exe /nologo /std:c++20 /EHsc /LD /O2 /MT /DWIN32_LEAN_AND_MEAN ^
  /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
  "%~dp0shale_windows_toast.cpp" /link /OUT:"%~1\shale_windows_toast.dll" runtimeobject.lib windowsapp.lib || exit /b 7
set DEPENDENCIES=%TEMP%\shale_windows_toast.dependencies.%RANDOM%.txt
dumpbin.exe /nologo /dependents "%~1\shale_windows_toast.dll" > "%DEPENDENCIES%" || exit /b 9
python "%~dp0..\..\scripts\validate_windows_dll_dependencies.py" "%DEPENDENCIES%" || exit /b 10
del /q "%DEPENDENCIES%" >nul 2>nul
exit /b 0

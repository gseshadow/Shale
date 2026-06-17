@echo off
setlocal

if "%SPRING_PROFILES_ACTIVE%"=="" set SPRING_PROFILES_ACTIVE=azure
if "%SHALE_SERVER_JAR%"=="" set SHALE_SERVER_JAR=D:\home\site\wwwroot\shale-server.jar

if not exist "%SHALE_SERVER_JAR%" (
  echo Shale startup failed: jar not found at %SHALE_SERVER_JAR% 1>&2
  exit /b 1
)

java %JAVA_OPTS% -jar "%SHALE_SERVER_JAR%"

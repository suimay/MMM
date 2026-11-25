@echo off
setlocal
cd /d "%~dp0.."
if not exist "%~dp0target\MMM-dev-server.jar" (
  call "%~dp0..\mvnw.cmd" -B -ntp -Pwindows-dev -DskipTests package
  if errorlevel 1 (pause & exit /b 1)
)
set "MMM_JAVA=java"
if exist "%JAVA_HOME%\bin\java.exe" set "MMM_JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%~dp0..\app\tools\jdk21\bin\java.exe" set "MMM_JAVA=%~dp0..\app\tools\jdk21\bin\java.exe"
"%MMM_JAVA%" -jar "%~dp0target\MMM-dev-server.jar" --stdin-control %*
if errorlevel 1 pause

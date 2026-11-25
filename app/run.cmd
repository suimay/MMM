@echo off
setlocal
cd /d "%~dp0"
if not exist "%~dp0build\MMM.jar" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
  if errorlevel 1 (pause & exit /b 1)
)
set "MMM_JAVA=java"
if exist "%JAVA_HOME%\bin\java.exe" set "MMM_JAVA=%JAVA_HOME%\bin\java.exe"
"%MMM_JAVA%" -jar "%~dp0build\MMM.jar" %*
if errorlevel 1 pause

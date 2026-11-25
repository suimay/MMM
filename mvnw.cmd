@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0app\maven.ps1" %*
exit /b %errorlevel%

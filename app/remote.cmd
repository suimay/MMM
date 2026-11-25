@echo off
setlocal
if not "%~1"=="" set "MMM_SERVER_URL=%~1"
if "%MMM_SERVER_URL%"=="" set "MMM_SERVER_URL=http://127.0.0.1:8080"
call "%~dp0run.cmd"

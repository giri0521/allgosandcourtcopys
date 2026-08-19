@echo off
setlocal enabledelayedexpansion

rem ---------------------------------------------------------------------------
rem  Stop everything start.bat started. Data volumes are kept, so the next
rem  start.bat comes back with the same database and documents.
rem ---------------------------------------------------------------------------

cd /d "%~dp0"
title ALLGOS DMS - stop

echo(
echo Stopping ALLGOS DMS
call :killport 8080 "backend"
call :killport 5173 "web app"
call :killport 5174 "web app fallback port"

docker info >nul 2>&1
if errorlevel 1 (
  echo   Docker is not running - no containers to stop.
) else (
  docker compose down --remove-orphans
  echo   containers removed, volumes kept
)
echo(
echo Stopped.
echo(
pause
goto :eof

:killport
set "_port=%~1"
set "_hit="
for /f "tokens=5" %%P in ('netstat -ano -p tcp ^| findstr /r /c:":%_port% .*LISTENING"') do (
  if not "%%P"=="0" (
    taskkill /f /pid %%P >nul 2>&1
    set "_hit=1"
  )
)
if defined _hit (echo   stopped the %~2 on port %_port%) else (echo   nothing on port %_port%)
exit /b 0

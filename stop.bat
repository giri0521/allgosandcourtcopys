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
rem  The windows go first. Freeing a port only kills the child holding the socket
rem  - Vite's node, the application's java - and leaves the "cmd /k" window and
rem  npm's wrapper behind, still on screen and still claiming to be the app.
call :killwindow "ALLGOS backend"
call :killwindow "ALLGOS web"
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

rem  netstat runs without "-p tcp" on purpose: that flag limits it to IPv4, and
rem  Vite binds [::1]:5173 - IPv6 loopback only. With the flag this routine never
rem  saw port 5173 at all and reported "nothing" while Vite kept running.
:killport
set "_port=%~1"
set "_hit="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%_port% .*LISTENING"') do (
  if not "%%P"=="0" (
    taskkill /f /pid %%P >nul 2>&1
    set "_hit=1"
  )
)
if defined _hit (echo   stopped the %~2 on port %_port%) else (echo   nothing on port %_port%)
exit /b 0

rem  Closes a window this project opened, and everything inside it. Matched by
rem  title so it can only ever hit a window start.bat itself named.
rem  taskkill exits 0 whether or not the filter matched anything, so its output is
rem  read instead - only a real kill prints a SUCCESS line.
:killwindow
set "_win="
for /f "delims=" %%L in ('taskkill /f /t /fi "WINDOWTITLE eq %~1" 2^>nul ^| findstr /b /c:"SUCCESS"') do set "_win=1"
if defined _win echo   closed the %~1 window
exit /b 0

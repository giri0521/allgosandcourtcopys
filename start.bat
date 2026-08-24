@echo off
setlocal enabledelayedexpansion

rem ---------------------------------------------------------------------------
rem  ALLGOSANDCOURTCOPYS - start the whole stack end to end.
rem
rem  Anything already running is stopped first, so this is safe to re-run: it
rem  always leaves you with one clean set of services. Infrastructure runs in
rem  Docker; the backend and the web app each get their own console window.
rem ---------------------------------------------------------------------------

cd /d "%~dp0"
title ALLGOS DMS - launcher

echo(
echo ===========================================================================
echo   ALLGOS DMS
echo ===========================================================================
echo(

rem ===========================================================================
rem  0. Toolchain
rem ===========================================================================
where node >nul 2>&1
if errorlevel 1 (
  echo [x] Node is not on PATH. Node 22.12 or newer is required.
  goto :fail
)
where npm.cmd >nul 2>&1
if errorlevel 1 (
  echo [x] npm is not on PATH.
  goto :fail
)
rem  Maven needs JAVA_HOME to point at the JDK *root* - the folder that holds
rem  bin\java.exe - not at the bin folder itself, and not at a JRE. Getting that
rem  wrong is the most common cause of "The JAVA_HOME environment variable is
rem  not defined correctly", and the message does not say which mistake it was.
rem
rem  So the value is checked rather than trusted, and a JDK is located when it is
rem  wrong or missing. Whatever is resolved here is passed to the backend window,
rem  so a machine with a broken JAVA_HOME still starts.
set "JDK="
call :checkjdk "%JAVA_HOME%"
if not defined JDK if defined JAVA_HOME echo       JAVA_HOME is set but is not a JDK root - searching instead

rem  21 first: that is the version the project targets. Every root is searched
rem  one level deeper as well, because an installer does not always drop the JDK
rem  directly into it - Oracle's puts it in Java\latest\jdk-21, and a pattern
rem  anchored at Java\jdk-* walks straight past that.
call :findjdk "jdk-21*"
call :findjdk "jdk21*"
call :findjdk "jdk-*"
call :findjdk "jdk*"

rem  Last resort: if a JDK is already on PATH, work back from javac to its root.
if not defined JDK for /f "delims=" %%J in ('where javac 2^>nul') do call :jdkfrombin "%%~dpJ"

if not defined JDK (
  echo [x] No JDK found. JDK 21 is required. Install it with:
  echo       winget install EclipseAdoptium.Temurin.21.JDK
  goto :fail
)
set "JAVA_HOME=%JDK%"
echo       JDK      %JAVA_HOME%
where docker >nul 2>&1
if errorlevel 1 (
  echo [x] Docker is not on PATH. Install Docker Desktop and retry.
  goto :fail
)

rem ===========================================================================
rem  1. Docker engine - start Docker Desktop if the daemon is down
rem ===========================================================================
echo [1/6] Docker engine
docker info >nul 2>&1
if not errorlevel 1 (
  echo       already running
  goto :dockerup
)

set "DD_EXE="
if exist "%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe" set "DD_EXE=%LOCALAPPDATA%\Programs\DockerDesktop\Docker Desktop.exe"
if not defined DD_EXE if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" set "DD_EXE=%ProgramFiles%\Docker\Docker\Docker Desktop.exe"

if not defined DD_EXE (
  echo [x] The Docker daemon is not running and Docker Desktop was not found.
  echo     Start Docker Desktop by hand, then run this script again.
  goto :fail
)

echo       daemon is down - launching Docker Desktop
start "" "%DD_EXE%"
echo       waiting for the engine ^(this can take a minute on a cold start^)
set /a _n=0
:waitdocker
docker info >nul 2>&1
if not errorlevel 1 goto :dockerup
set /a _n+=1
if !_n! geq 90 (
  echo [x] The Docker engine did not come up within 3 minutes.
  goto :fail
)
ping -n 3 127.0.0.1 >nul
goto :waitdocker
:dockerup
echo       engine ready

rem ===========================================================================
rem  2. Stop anything already running
rem ===========================================================================
echo [2/6] Stopping any previous run
rem  The console windows are closed by title before the ports are freed. Killing
rem  the process on the port only takes out the child that holds the socket -
rem  Vite's node, Maven's java - and leaves the "cmd /k" window and the npm
rem  wrapper above it alive. Those orphans survive into the next run, which is
rem  how a machine ends up with three Vite instances and no clean set at all.
call :killwindow "ALLGOS backend"
call :killwindow "ALLGOS web"
call :killport 8080 "backend"
call :killport 5173 "web app"
call :killport 5174 "web app fallback port"
docker compose down --remove-orphans >nul 2>&1
echo       previous containers removed ^(data volumes are kept^)

rem ===========================================================================
rem  3. Environment file
rem ===========================================================================
if exist ".env" (
  echo [3/6] .env present
) else (
  echo [3/6] .env missing - creating it from .env.example
  copy /y ".env.example" ".env" >nul
)

rem ===========================================================================
rem  4. Infrastructure
rem ===========================================================================
echo [4/6] Starting Postgres :5433 and MinIO :9000 ^(console :9002^)
docker compose up -d
if errorlevel 1 (
  echo [x] docker compose up failed - see the output above.
  goto :fail
)

echo       waiting for Postgres to accept connections
set /a _n=0
:waitdb
docker exec allgos-postgres pg_isready -U allgos -d allgos_dms >nul 2>&1
if not errorlevel 1 goto :dbready
set /a _n+=1
if !_n! geq 60 (
  echo [x] Postgres did not become ready within 2 minutes.
  echo     Try:  docker compose logs postgres
  goto :fail
)
ping -n 3 127.0.0.1 >nul
goto :waitdb
:dbready
echo       Postgres ready, MinIO bucket created

rem ===========================================================================
rem  5. Web dependencies
rem
rem  Run npm install every time, not just when node_modules is absent. A pull
rem  that adds a dependency leaves node_modules present but stale, and the app
rem  then dies in the browser on an unresolved import. When nothing has
rem  changed this costs a couple of seconds.
rem ===========================================================================
echo [5/6] Syncing web dependencies
pushd admin-web
call npm.cmd install --no-fund --no-audit
set "NPMFAIL=!errorlevel!"
popd
if not "!NPMFAIL!"=="0" (
  echo [x] npm install failed - see the output above.
  goto :fail
)

echo(
echo [6/6] Starting the application
echo       backend  -^> http://localhost:8080   ^(own window^)
rem  mvnw.cmd is run as .\mvnw.cmd, not as a bare name. When
rem  NoDefaultCurrentDirectoryInExePath is set - it is on this machine, and on any
rem  locked-down one - cmd stops resolving commands from the working directory,
rem  so the bare name dies with "not recognized" and the window closes on it.
start "ALLGOS backend" /d "%~dp0backend" cmd /k .\mvnw.cmd spring-boot:run

echo       waiting for the API to report healthy
set /a _n=0
:waitapi
curl -s -f -o nul http://localhost:8080/actuator/health
if not errorlevel 1 goto :apiready
set /a _n+=1
if !_n! geq 120 (
  echo(
  echo [!] The API has not reported healthy after 4 minutes.
  echo     Look at the "ALLGOS backend" window for the cause. Continuing anyway.
  goto :apidone
)
ping -n 3 127.0.0.1 >nul
goto :waitapi
:apiready
echo       API healthy
:apidone

echo       web app  -^> http://localhost:5173   ^(own window^)
start "ALLGOS web" /d "%~dp0admin-web" cmd /k npm.cmd run dev

echo       waiting for Vite
set /a _n=0
:waitweb
curl -s -o nul http://localhost:5173
if not errorlevel 1 goto :webready
set /a _n+=1
if !_n! geq 45 goto :webready
ping -n 3 127.0.0.1 >nul
goto :waitweb
:webready

start "" http://localhost:5173

echo(
echo ===========================================================================
echo   Running
echo ===========================================================================
echo   Web app     http://localhost:5173
echo   API         http://localhost:8080
echo   MinIO       http://localhost:9002        minioadmin / minioadmin
echo   Postgres    localhost:5433               allgos / allgos
echo(
echo   Sign in with mobile 9999999999. OTP_PROVIDER=mock, so the OTP is
echo   printed in the "ALLGOS backend" window.
echo(
echo   To stop everything:  stop.bat   ^(or just re-run start.bat^)
echo ===========================================================================
echo(
pause
goto :eof

rem ===========================================================================
rem  :killport <port> <label>  - kill whatever is listening on <port>
rem
rem  netstat is called without "-p tcp" on purpose: that flag limits the output to
rem  IPv4, and Vite binds [::1]:5173 - IPv6 loopback only. With the flag the port
rem  never appeared in the listing and this routine silently killed nothing.
rem ===========================================================================
:killport
set "_port=%~1"
set "_hit="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%_port% .*LISTENING"') do (
  if not "%%P"=="0" (
    taskkill /f /pid %%P >nul 2>&1
    set "_hit=1"
  )
)
if defined _hit echo       stopped the old %~2 on port %_port%
exit /b 0

rem ===========================================================================
rem  :findjdk <pattern>  - look for <pattern> under each known install root and
rem                        one level below it, and keep the first real JDK
rem ===========================================================================
:findjdk
if defined JDK exit /b 0
for %%R in ("%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Java" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Amazon Corretto" "%ProgramFiles%\Zulu" "%ProgramFiles%\BellSoft" "%ProgramFiles%\Android\Android Studio" "%LOCALAPPDATA%\Programs\Eclipse Adoptium") do (
  for /d %%D in ("%%~R\%~1") do call :checkjdk "%%~fD"
  for /d %%P in ("%%~R\*") do for /d %%D in ("%%~fP\%~1") do call :checkjdk "%%~fD"
)
exit /b 0

rem ===========================================================================
rem  :checkjdk <dir>  - accept <dir> only when it is a JDK root. javac has to be
rem                     there too: a JRE has bin\java.exe and cannot build.
rem ===========================================================================
:checkjdk
if defined JDK exit /b 0
if "%~1"=="" exit /b 0
if not exist "%~1\bin\java.exe" exit /b 0
if not exist "%~1\bin\javac.exe" exit /b 0
set "JDK=%~1"
exit /b 0

rem ===========================================================================
rem  :jdkfrombin <dir>  - <dir> is a bin folder holding javac; test its parent
rem ===========================================================================
:jdkfrombin
if defined JDK exit /b 0
set "_bin=%~1"
if "%_bin:~-1%"=="\" set "_bin=%_bin:~0,-1%"
for %%B in ("%_bin%") do for %%H in ("%%~dpB.") do call :checkjdk "%%~fH"
exit /b 0

rem ===========================================================================
rem  :killwindow <title>  - close a console window this script opened, and
rem                         everything running inside it
rem ===========================================================================
:killwindow
taskkill /f /t /fi "WINDOWTITLE eq %~1" >nul 2>&1
exit /b 0

:fail
echo(
echo Startup aborted.
echo(
pause
exit /b 1

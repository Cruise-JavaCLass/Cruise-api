@echo off
title Cruise CMS Backend Launcher
echo ========================================================
echo  CRUISE CMS - RESTARTING ALL 5 MICROSERVICES CLEANLY
echo ========================================================

cd /d "%~dp0"

REM 1. Set environment variables
if not defined CRUISE_QR_SECRET (
    echo [INFO] CRUISE_QR_SECRET not set in environment.
    echo Generating dynamic local session secret for HMAC-SHA256...
    for /f "tokens=*" %%a in ('powershell -NoProfile -Command "[System.Guid]::NewGuid().ToString('N') + [System.Guid]::NewGuid().ToString('N')"') do set "CRUISE_QR_SECRET=%%a"
)

REM 2. Detect Java runtime
set "JAVABIN=C:\PROGRA~1\Android\ANDROI~1\jbr\bin\java.exe"
if not exist "%JAVABIN%" (
    for %%I in ("%JAVA_HOME%\bin\java.exe") do set "JAVABIN=%%~sI"
)
if not exist "%JAVABIN%" (
    set "JAVABIN=java"
)
echo Java Runtime: %JAVABIN%

REM 3. Stop any existing processes occupying ports 8080-8084 to prevent conflicts
echo [1/7] Cleaning up previous processes on ports 8080-8084...
for %%P in (8080 8081 8082 8083 8084) do (
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr /R /C:":%%P[ ]" ^| findstr LISTENING') do (
        taskkill /F /PID %%a >nul 2>&1
    )
)

REM 4. Validate that all 5 microservice JARs exist before launching
set "MISSING_JARS=0"
for %%M in (
    "cruise-gateway\target\cruise-gateway-0.0.1-SNAPSHOT.jar"
    "cruise-auth-service\target\cruise-auth-service-0.0.1-SNAPSHOT.jar"
    "cruise-itinerary-service\target\cruise-itinerary-service-0.0.1-SNAPSHOT.jar"
    "cruise-activity-service\target\cruise-activity-service-0.0.1-SNAPSHOT.jar"
    "cruise-pos-service\target\cruise-pos-service-0.0.1-SNAPSHOT.jar"
) do (
    if not exist %%M (
        echo [ERROR] Required JAR not found: %%M
        set "MISSING_JARS=1"
    )
)

if "%MISSING_JARS%"=="1" (
    echo.
    echo [ERROR] Missing service artifacts in target/ directories!
    echo Please build the project first: .\mvnw.cmd clean package -DskipTests
    exit /b 1
)

REM 5. Ensure Docker PostgreSQL is running and healthy
echo [2/7] Ensuring PostgreSQL container is running and healthy...
docker compose up -d --wait

REM 6. Launch all 5 Microservices cleanly
echo [3/7] Launching cruise-gateway on port 8080...
start "Cruise-Gateway [8080]" cmd /k "%JAVABIN% -jar cruise-gateway\target\cruise-gateway-0.0.1-SNAPSHOT.jar"

echo [4/7] Launching cruise-auth-service on port 8081...
start "Cruise-Auth [8081]" cmd /k "%JAVABIN% -jar cruise-auth-service\target\cruise-auth-service-0.0.1-SNAPSHOT.jar"

echo [5/7] Launching cruise-itinerary-service on port 8082...
start "Cruise-Itinerary [8082]" cmd /k "%JAVABIN% -jar cruise-itinerary-service\target\cruise-itinerary-service-0.0.1-SNAPSHOT.jar"

echo [6/7] Launching cruise-activity-service on port 8083...
start "Cruise-Activity [8083]" cmd /k "%JAVABIN% -jar cruise-activity-service\target\cruise-activity-service-0.0.1-SNAPSHOT.jar"

echo [7/7] Launching cruise-pos-service on port 8084...
start "Cruise-POS [8084]" cmd /k "%JAVABIN% -jar cruise-pos-service\target\cruise-pos-service-0.0.1-SNAPSHOT.jar"

echo ========================================================
echo  All 5 Microservices launched successfully!
echo ========================================================

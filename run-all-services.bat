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

REM 2. Detect Java runtime (check JAVA_HOME, Android Studio JBR, and PATH for Java 21 compatibility)
set "JAVABIN="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /i "version" | findstr /R /C:"\"21[\.\"]" >nul
        if not errorlevel 1 (
            set "JAVABIN=%JAVA_HOME%\bin\java.exe"
        )
    )
)

if not defined JAVABIN (
    if exist "C:\PROGRA~1\Android\ANDROI~1\jbr\bin\java.exe" (
        "C:\PROGRA~1\Android\ANDROI~1\jbr\bin\java.exe" -version 2>&1 | findstr /i "version" | findstr /R /C:"\"21[\.\"]" >nul
        if not errorlevel 1 (
            set "JAVABIN=C:\PROGRA~1\Android\ANDROI~1\jbr\bin\java.exe"
        )
    )
)

if not defined JAVABIN (
    java -version 2>&1 | findstr /i "version" | findstr /R /C:"\"21[\.\"]" >nul
    if not errorlevel 1 (
        set "JAVABIN=java"
    )
)

if not defined JAVABIN (
    echo.
    echo [ERROR] Java 21 LTS runtime not found!
    echo Please install Java 21 LTS or set JAVA_HOME pointing to a valid JDK 21 installation.
    exit /b 1
)

echo Java Runtime: %JAVABIN%

REM 3. Validate that all 5 microservice JARs exist before stopping running processes or launching
echo [1/7] Validating packaged service JAR artifacts...
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

REM 4. Ensure Docker PostgreSQL is running and healthy BEFORE terminating existing services
echo [2/7] Ensuring PostgreSQL container is running and healthy...
docker compose up -d --wait
if errorlevel 1 (
    echo [ERROR] Docker Compose failed to start healthy PostgreSQL container!
    exit /b 1
)

REM 5. Stop any existing Cruise processes occupying ports 8080-8084 (verifying PID ownership before kill)
echo [3/7] Cleaning up previous processes on ports 8080-8084...
call "%~dp0stop-all-services.bat"
if errorlevel 1 (
    echo [FATAL] Cannot start services due to occupied ports or active non-Cruise processes.
    exit /b 1
)

REM 6. Launch all 5 Microservices cleanly
echo [4/7] Launching cruise-gateway on port 8080...
start "Cruise-Gateway [8080]" cmd /k "%JAVABIN% -jar cruise-gateway\target\cruise-gateway-0.0.1-SNAPSHOT.jar"

echo [5/7] Launching cruise-auth-service on port 8081...
start "Cruise-Auth [8081]" cmd /k "%JAVABIN% -jar cruise-auth-service\target\cruise-auth-service-0.0.1-SNAPSHOT.jar"

echo [6/7] Launching cruise-itinerary-service on port 8082...
start "Cruise-Itinerary [8082]" cmd /k "%JAVABIN% -jar cruise-itinerary-service\target\cruise-itinerary-service-0.0.1-SNAPSHOT.jar"

echo [7/7] Launching cruise-activity-service on port 8083...
start "Cruise-Activity [8083]" cmd /k "%JAVABIN% -jar cruise-activity-service\target\cruise-activity-service-0.0.1-SNAPSHOT.jar"

echo Launching cruise-pos-service on port 8084...
start "Cruise-POS [8084]" cmd /k "%JAVABIN% -jar cruise-pos-service\target\cruise-pos-service-0.0.1-SNAPSHOT.jar"

echo ========================================================
echo  All 5 Microservices launched successfully!
echo ========================================================

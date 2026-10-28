@echo off
setlocal enabledelayedexpansion
title Cruise CMS Backend Stopper
echo Stopping all Cruise Java services on ports 8080-8084...
set "STOP_FAILED=0"

for %%P in (8080 8081 8082 8083 8084) do (
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr /R /C:":%%P[ ]" ^| findstr LISTENING') do (
        set "TARGET_PID=%%a"
        set "IS_JAVA=0"
        for /f "usebackq tokens=1 delims=," %%n in (`tasklist /FI "PID eq %%a" /FO CSV /NH 2^>nul`) do (
            echo %%~n | findstr /i "java" >nul && set "IS_JAVA=1"
        )
        if "!IS_JAVA!"=="1" (
            echo Stopping Cruise service [PID %%a] on port %%P...
            taskkill /F /PID %%a >nul 2>&1
            if errorlevel 1 (
                echo [ERROR] Failed to terminate Cruise process [PID %%a] on port %%P.
                set "STOP_FAILED=1"
            ) else (
                tasklist /FI "PID eq %%a" 2>nul | findstr /i "%%a" >nul
                if not errorlevel 1 (
                    echo [ERROR] Process [PID %%a] on port %%P is still running after taskkill.
                    set "STOP_FAILED=1"
                ) else (
                    echo [OK] Cruise service [PID %%a] stopped successfully.
                )
            )
        ) else (
            echo [WARN] Port %%P is held by non-Cruise process [PID %%a]. Leaving it running.
            set "STOP_FAILED=1"
        )
    )
)

if "%STOP_FAILED%"=="1" (
    echo.
    echo [WARNING] One or more ports could not be freed or were held by non-Cruise processes.
    exit /b 1
)

echo All Cruise Backend services stopped.
echo Note: PostgreSQL Docker container is preserved (use 'docker compose down' to stop database).
exit /b 0

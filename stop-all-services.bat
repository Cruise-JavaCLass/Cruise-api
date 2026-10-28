@echo off
title Cruise CMS Backend Stopper
echo Stopping all Cruise Java services on ports 8080-8084...
for %%P in (8080 8081 8082 8083 8084) do (
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr :%%P ^| findstr LISTENING') do (
        echo Killing process %%a on port %%P...
        taskkill /F /PID %%a >nul 2>&1
    )
)
echo All Cruise Backend services stopped.

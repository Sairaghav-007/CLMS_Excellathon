@echo off
title CLMS - Corporate Learning Management System Launcher
color 0A
echo ====================================================================
echo        CLMS - CORPORATE LEARNING MANAGEMENT SYSTEM LAUNCHER
echo ====================================================================
echo.

set SCRIPT_DIR=%~dp0

:: 1. Check PostgreSQL Database Connectivity
echo [1/3] Checking PostgreSQL Database on port 5434...
powershell -Command "if ((Test-NetConnection -ComputerName localhost -Port 5434 -WarningAction SilentlyContinue).TcpTestSucceeded) { exit 0 } else { exit 1 }"
if %ERRORLEVEL% equ 0 (
    echo [OK] PostgreSQL is active and listening on port 5434.
) else (
    echo [WARN] PostgreSQL is NOT detected on port 5434!
    echo Attempting to start PostgreSQL via Docker Compose...
    docker compose up -d postgres >nul 2>&1
    timeout /t 3 /nobreak >nul
    powershell -Command "if ((Test-NetConnection -ComputerName localhost -Port 5434 -WarningAction SilentlyContinue).TcpTestSucceeded) { exit 0 } else { exit 1 }"
    if %ERRORLEVEL% equ 0 (
        echo [OK] PostgreSQL container started successfully on port 5434.
    ) else (
        echo [NOTE] If you have native PostgreSQL, please ensure the service is running on port 5434.
    )
)
echo.

:: 2. Launch Spring Boot Backend
echo [2/3] Launching Backend (Spring Boot)...
start "CLMS Backend [Spring Boot - Port 8080]" cmd /k "cd /d %SCRIPT_DIR%Backend && title CLMS Backend && echo Starting Spring Boot... && mvnw.cmd spring-boot:run"
echo [OK] Backend process initiated in separate terminal window.
echo.

:: 3. Launch React/Vite Frontend
echo [3/3] Launching Frontend (React + Vite)...
start "CLMS Frontend [Vite - Port 5173]" cmd /k "cd /d %SCRIPT_DIR%FrontEnd && title CLMS Frontend && echo Starting Vite Dev Server... && npm run dev"
echo [OK] Frontend process initiated in separate terminal window.
echo.

:: 4. Wait and Open Browser
echo Waiting for servers to initialize...
timeout /t 5 /nobreak >nul
echo Opening CLMS in default web browser: http://localhost:5173
start http://localhost:5173

echo.
echo ====================================================================
echo [DONE] CLMS Full Stack is running!
echo.
echo  - Frontend: http://localhost:5173
echo  - Backend:  http://localhost:8080
echo  - Database: localhost:5434/clms_db
echo.
echo Pre-seeded Demo Accounts (Password: Welcome@123):
echo  - Admin:    admin@clms.com
echo  - HR:       hr@clms.com
echo  - Manager:  manager@clms.com
echo  - Employee: employee@clms.com
echo ====================================================================
pause

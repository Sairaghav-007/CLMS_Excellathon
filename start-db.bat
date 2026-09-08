@echo off
title CLMS Database [PostgreSQL Docker]
color 0D
echo ====================================================================
echo             STARTING CLMS POSTGRESQL DATABASE (DOCKER)
echo ====================================================================
cd /d %~dp0
docker compose up -d postgres
echo.
echo Checking database health...
docker compose ps postgres
echo.
echo Database is running on port 5434 (mapped to container port 5432).
echo User: postgres | Password: 12345 | DB: clms_db
echo.
pause

@echo off
title CLMS Backend [Spring Boot]
color 0B
echo ====================================================================
echo             STARTING CLMS BACKEND (SPRING BOOT 3)
echo ====================================================================
cd /d %~dp0Backend
mvnw.cmd spring-boot:run
pause

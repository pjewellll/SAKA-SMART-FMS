@echo off
title SAKA-SMART - PostgreSQL Mode
cd /d "%~dp0"

set "SAKA_MODE=postgres"
set "SAKA_DB_URL=jdbc:postgresql://localhost:5432/sakasmart"
set "SAKA_DB_USER=postgres"

set /p "SAKA_DB_PASSWORD=Enter PostgreSQL password: "

java -cp "build;lib\*" com.sakasmart.Main

pause

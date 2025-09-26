@echo off
title Repo Browser
cd /d "%~dp0"

echo Starting Repo Browser...
echo.

java -jar repo-browser.jar
if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Failed to start the application
    echo Make sure Java 17+ is installed
    echo.
    pause
)

echo.
echo Application closed.
pause
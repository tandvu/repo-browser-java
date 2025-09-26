@echo off
title Repo Browser Test

echo ==========================================
echo Repo Browser Test Script
echo ==========================================
echo.

REM Show current directory
echo Current directory: %CD%
echo.

REM List files in current directory
echo Files in this directory:
dir /b
echo.

REM Check Java version
echo Checking Java version:
java -version
echo.

REM Check if JAR file exists
if exist "repo-browser.jar" (
    echo repo-browser.jar found - Size: 
    dir repo-browser.jar | findstr repo-browser.jar
) else (
    echo ERROR: repo-browser.jar not found!
)
echo.

REM Try to run the application with detailed output
echo Attempting to run application...
echo ==========================================
java -jar repo-browser.jar

echo.
echo ==========================================
echo Application finished with exit code: %ERRORLEVEL%
echo.
echo Press any key to exit...
pause >nul
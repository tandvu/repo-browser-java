@echo off
title Repo Browser JavaFX

echo ==========================================
echo Repo Browser JavaFX
echo ==========================================
echo.

REM Change to the directory where the batch file is located
cd /d "%~dp0"

REM Check if Java is available
echo Checking Java installation...
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Java is not installed or not found in PATH
    echo.
    echo Please install Java 17 or higher from: https://adoptium.net/
    echo.
    goto :pause_and_exit
)

REM Check if JAR file exists
if not exist "repo-browser.jar" (
    echo.
    echo ERROR: repo-browser.jar not found!
    echo.
    echo Make sure both files are in the same folder:
    echo - repo-browser.jar
    echo - Start Repo Browser (Distribution).bat
    echo.
    goto :pause_and_exit
)

echo Java found. Starting Repo Browser...
echo Please wait while the application loads...
echo.
echo (You can minimize this window - the GUI will open separately)
echo.

REM Try to run the application
java -jar repo-browser.jar
set APP_EXIT_CODE=%ERRORLEVEL%

echo.
if %APP_EXIT_CODE% == 0 (
    echo Application closed normally.
) else (
    echo Application exited with error code: %APP_EXIT_CODE%
    echo.
    echo This might be due to:
    echo - Java version too old (need Java 17+)
    echo - JavaFX modules missing from your Java installation
    echo - Corrupted JAR file
    echo.
    echo Please try:
    echo 1. Update to Java 17 or newer
    echo 2. Download from https://adoptium.net/ (includes JavaFX)
)

:pause_and_exit
echo.
echo Press any key to exit...
pause >nul
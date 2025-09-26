@echo off
title Repo Browser JavaFX

REM Change to the directory where the batch file is located
cd /d "%~dp0"

REM Hide the console window after a brief moment (optional)
REM You can uncomment the next line to minimize the window
REM powershell -WindowStyle Minimized -Command ""

echo Starting Repo Browser...
echo Please wait while the application loads...
echo.

REM Use Maven to run the JavaFX application (most reliable method)
mvn javafx:run >nul 2>&1
if %ERRORLEVEL% == 0 goto :success

REM If Maven fails, try the smart JAR launcher
echo Maven not available, trying JAR launcher...
java -jar repo-browser.jar >nul 2>&1
if %ERRORLEVEL% == 0 goto :success

REM If both fail, show error and wait for user
echo.
echo ===========================================
echo ERROR: Could not start Repo Browser
echo.
echo Please ensure you have:
echo 1. Java 17 or higher installed
echo 2. Maven installed (recommended)
echo.
echo Try running from command line:
echo   mvn javafx:run
echo ===========================================
echo.
echo Press any key to exit...
pause >nul
exit /b 1

:success
echo.
echo Application started successfully.
exit /b 0po Browser JavaFX
echo Starting Repo Browser...
echo.

REM Change to the directory where the batch file is located
cd /d "%~dp0"

REM Use Maven to run the JavaFX application (this ensures proper JavaFX setup)
echo Using Maven to run the application with proper JavaFX configuration...
mvn javafx:run

REM Alternative: If you prefer to try the JAR directly (may not work on all systems)
REM echo Alternatively, trying to run the fat JAR directly...
REM java -jar repo-browser.jar

echo.
echo Application closed.
pause
@echo off
title Repo Browser JavaFX

REM Change to the directory where the batch file is located
cd /d "%~dp0"

echo ==========================================
echo Repo Browser JavaFX Launcher
echo ==========================================
echo.
echo Checking Java installation...

REM Check if Java is available
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo.
    echo Please install Java 17 or higher and try again.
    echo Download from: https://adoptium.net/
    echo.
    echo Press any key to exit...
    pause >nul
    exit /b 1
)

echo Java found. Starting application...
echo Please wait while the application loads...
echo.

REM Try to run the smart JAR launcher
java -jar repo-browser.jar
set JAVA_EXIT_CODE=%ERRORLEVEL%

if %JAVA_EXIT_CODE% == 0 (
    echo.
    echo Application closed normally.
) else (
    echo.
    echo ===========================================
    echo ERROR: Application failed to start (Exit code: %JAVA_EXIT_CODE%)
    echo.
    echo Possible causes:
    echo 1. Java version is too old (need Java 17+)
    echo 2. JavaFX modules are missing
    echo 3. repo-browser.jar file is corrupted
    echo.
    echo Trying alternative launch method...
    echo.
    
    REM Try with explicit JavaFX modules
    java --add-modules javafx.controls,javafx.fxml -jar repo-browser.jar
    set JAVA_EXIT_CODE2=%ERRORLEVEL%
    
    if %JAVA_EXIT_CODE2% neq 0 (
        echo.
        echo Alternative launch also failed.
        echo Please check your Java installation.
    )
)

echo.
echo Press any key to exit...
pause >nul
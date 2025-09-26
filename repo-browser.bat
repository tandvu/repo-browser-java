@echo off
setlocal

REM Get the directory where this batch file is located
set "APP_DIR=%~dp0"
cd /d "%APP_DIR%"

REM Try to run with Maven first (most reliable)
where mvn >nul 2>nul
if %ERRORLEVEL% == 0 (
    echo Starting Repo Browser with Maven...
    mvn javafx:run
    goto :end
)

REM Fallback: try to run the JAR directly
echo Maven not found. Trying to run JAR directly...
echo Note: This may fail if JavaFX is not properly configured.

java -jar repo-browser.jar
if %ERRORLEVEL% neq 0 (
    echo.
    echo ===========================================
    echo Failed to run the application directly.
    echo.
    echo Please install Maven and try again, or run:
    echo   mvn javafx:run
    echo.
    echo Alternatively, ensure JavaFX is available and run:
    echo   java --module-path "path\to\javafx\lib" --add-modules javafx.controls,javafx.fxml -jar repo-browser.jar
    echo ===========================================
    echo.
    echo Press any key to exit...
    pause >nul
)

:end
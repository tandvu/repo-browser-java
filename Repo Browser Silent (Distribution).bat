@echo off

REM Change to the directory where the batch file is located
cd /d "%~dp0"

REM Try to run the smart JAR launcher silently
java -jar repo-browser.jar >nul 2>&1
if %ERRORLEVEL% == 0 exit /b 0

REM If that fails, try with explicit JavaFX modules
java --add-modules javafx.controls,javafx.fxml -jar repo-browser.jar >nul 2>&1
if %ERRORLEVEL% == 0 exit /b 0

REM If all fails, show a simple error dialog
powershell -Command "Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.MessageBox]::Show('Could not start Repo Browser. Please ensure Java 17+ is installed.', 'Repo Browser Error', 'OK', 'Error')" >nul 2>&1
@echo off

REM Change to the directory where the batch file is located
cd /d "%~dp0"

REM Run the application silently without showing console output
mvn javafx:run >nul 2>&1
if %ERRORLEVEL% == 0 exit /b 0

REM If Maven fails, try the JAR
java -jar repo-browser.jar >nul 2>&1
if %ERRORLEVEL% == 0 exit /b 0

REM If all fails, show a simple error dialog
powershell -Command "Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.MessageBox]::Show('Could not start Repo Browser. Please ensure Java 17+ and Maven are installed.', 'Repo Browser Error', 'OK', 'Error')"
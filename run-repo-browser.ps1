# Repo Browser JavaFX Launcher
# PowerShell script to launch the Repo Browser application

Write-Host "Starting Repo Browser JavaFX Application..." -ForegroundColor Green
Write-Host ""

# Change to the script directory
Set-Location $PSScriptRoot

# Check if Maven is available
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Write-Host "Using Maven to run with proper JavaFX configuration..." -ForegroundColor Yellow
    mvn javafx:run
} else {
    Write-Host "Maven not found. Attempting to run JAR directly..." -ForegroundColor Yellow
    Write-Host "Note: This may not work if JavaFX modules are not properly configured." -ForegroundColor Red
    java -jar repo-browser.jar
}

Write-Host ""
Write-Host "Application closed. Press any key to exit..." -ForegroundColor Cyan
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
@echo off
echo Building Repo Browser...
call mvn -q -DskipTests clean package
echo Maven exit code: %ERRORLEVEL%
if %ERRORLEVEL% EQU 0 (
    echo Build successful, copying JAR...
    if exist "target\repo-browser-java-1.0.0.jar" (
        copy "target\repo-browser-java-1.0.0.jar" "repo-browser.jar" /Y >nul
        echo Done! Use start.bat to run the application.
    ) else (
        echo ERROR: JAR file not found in target folder!
    )
) else (
    echo Build failed with exit code: %ERRORLEVEL%
    pause
)
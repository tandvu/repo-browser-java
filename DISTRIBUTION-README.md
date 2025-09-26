# Repo Browser Distribution

## For Distribution (Give to Clients)

**Files needed:**
- `repo-browser.jar`
- `Start Repo Browser - Fixed.bat`

**Instructions for clients:**
1. Copy both files to any folder
2. Double-click `Start Repo Browser - Fixed.bat`
3. Application will start

**Client requirements:**
- Windows 10/11
- Java 17 or higher

## For Development

**Files needed:**
- `src/` (source code)
- `pom.xml` (Maven configuration)
- `README.md` (project documentation)

**Development commands:**
```cmd
mvn javafx:run        # Run in development
mvn clean package     # Build new JAR
```

**Note:** After rebuilding, copy the new JAR:
```cmd
copy target\repo-browser-java-1.0.0.jar repo-browser.jar
```
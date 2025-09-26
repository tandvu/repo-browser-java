# Running Repo Browser JavaFX

## ✅ WORKING: Double-Click Solutions (In Order of Preference)

### 1. `Start Repo Browser.bat` ⭐ **RECOMMENDED**
**Double-click to start** - Most reliable, shows startup progress

### 2. `Repo Browser (Silent).bat` 🔇 **SILENT**  
**Double-click for silent launch** - No console window, clean experience

### 3. `run-repo-browser.bat` 🛠️ **TECHNICAL**
**Original batch launcher** - Detailed output and error handling

## Why Batch Files Work Best

Batch files are the most reliable because they:
- ✅ Handle JavaFX module paths correctly
- ✅ Work on all Windows systems with Java installed  
- ✅ Provide fallback options if Maven isn't available
- ✅ Give helpful error messages when things go wrong
- ✅ Don't depend on Windows file associations

## Alternative Methods (Less Reliable)

### JAR File Direct Launch
```cmd
java -jar repo-browser.jar
```
⚠️ May not work when double-clicked due to JavaFX module issues

### Maven Command Line
```cmd
mvn javafx:run
```
✅ Always works if Maven is installed

## What To Share

For the best user experience, share these files:
- `Start Repo Browser.bat` - Main launcher (recommended)
- `Repo Browser (Silent).bat` - Silent launcher option
- `repo-browser.jar` - The application JAR
- `pom.xml` - Required for Maven to work

## System Requirements

- Java 17 or higher
- Maven 3.6+ (recommended for batch launchers)
- Windows 10/11

## User Instructions

**Simply double-click `Start Repo Browser.bat` to run the application.**

The batch file will:
1. Try Maven (most reliable)
2. Fall back to direct JAR execution
3. Show helpful error messages if nothing works
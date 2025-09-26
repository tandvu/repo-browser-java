# Repo Browser - Installation & Troubleshooting Guide

## Quick Start

1. **Download and extract** the Repo Browser files to any folder
2. **Double-click** `Repo Browser.bat`
3. **Wait** for the GUI window to open (may take 5-15 seconds)

## Required Files

Make sure you have both files in the same folder:
- `repo-browser.jar` (12.6 MB)
- `Repo Browser.bat` (1 KB)

## System Requirements

- **Windows 10/11**
- **Java 17 or higher**

## If It Doesn't Work

### Problem: "Popup screen appears and disappears"

**Solution:** The batch file is trying to run but encountering an error.

1. **Right-click** on `Repo Browser.bat`
2. Select **"Edit"** or **"Open with Notepad"**
3. The file should contain proper batch commands
4. If corrupted, download again

### Problem: "Java is not recognized" or "Java not found"

**Solution:** Install Java 17 or higher.

1. Go to **https://adoptium.net/**
2. Download **"Eclipse Temurin JDK 17"** (or newer)
3. Install with default settings
4. Restart your computer
5. Try running Repo Browser again

### Problem: "JavaFX errors" or "Module not found"

**Solution:** Use a JDK that includes JavaFX.

1. Download **Eclipse Temurin JDK** from https://adoptium.net/
2. OR try **Oracle JDK** which includes JavaFX
3. Uninstall old Java versions if needed

### Problem: Application starts but crashes immediately

**Solution:** Check file permissions and antivirus.

1. **Run as Administrator:** Right-click batch file → "Run as administrator"
2. **Antivirus:** Add the folder to antivirus exceptions
3. **File corruption:** Re-download the files

### Problem: "Access denied" or "Permission denied"

**Solution:** File/folder permissions issue.

1. **Move to different folder:** Try copying files to your Desktop
2. **Run as Administrator:** Right-click → "Run as administrator"
3. **Unblock files:** Right-click each file → Properties → Check "Unblock" if present

## Alternative Launch Methods

### Method 1: Command Prompt
1. Open **Command Prompt** in the folder with the files
2. Type: `java -jar repo-browser.jar`
3. Press Enter

### Method 2: PowerShell
1. Open **PowerShell** in the folder with the files  
2. Type: `java -jar repo-browser.jar`
3. Press Enter

## Testing Your Installation

### Test Java Installation:
1. Press **Windows + R**
2. Type: `cmd`
3. Type: `java -version`
4. Should show Java 17 or higher

### Test Files:
1. Check file sizes:
   - `repo-browser.jar` should be about 12.6 MB
   - `Repo Browser.bat` should be about 1 KB
2. Both files should be in the same folder

## Contact Support

If none of these solutions work, provide this information:

1. **Windows version** (Windows 10/11)
2. **Java version** (from `java -version` command)
3. **Error message** (screenshot or text)
4. **File sizes** of both files
5. **Folder location** where you placed the files

---

**Note:** The application may show a console window briefly during startup - this is normal. The main application window will open separately.
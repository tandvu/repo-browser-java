# Distribution Package

## Files to Give to Clients

**Only these 2 files are needed for distribution:**

1. **`repo-browser.jar`** (12.6 MB)
   - The complete application with all dependencies
   - Contains JavaFX libraries and your application code

2. **`Start Repo Browser (Distribution).bat`** 
   - Launcher that works with just the JAR file
   - No Maven or source code required
   - Shows startup progress and helpful error messages

**Optional file:**
3. **`Repo Browser Silent (Distribution).bat`**
   - Silent launcher (no console window)
   - Shows error popup if something goes wrong

## Client Instructions

**To run the application:**
- Double-click `Start Repo Browser (Distribution).bat`

**System Requirements for Clients:**
- Windows 10/11
- Java 17 or higher installed

## What Clients Don't Get

❌ Source code (`src/` folder)  
❌ Maven files (`pom.xml`, `target/`)  
❌ Development batch files (the ones that use Maven)  
❌ Any `.java` files  

## Testing the Distribution

To test what your clients will receive:
1. Create a new folder (e.g., `RepobrowserDistribution`)
2. Copy only these files:
   - `repo-browser.jar`
   - `Start Repo Browser (Distribution).bat`
3. Double-click the batch file to verify it works

## Distribution Size

- Total: ~12.7 MB (very manageable for distribution)
- Single JAR contains everything needed
- No additional installations required (except Java)

## Distribution Methods

- **Email**: Zip the 2 files and email
- **Network Share**: Copy to shared folder
- **USB/Flash Drive**: Copy directly
- **Cloud Storage**: Upload and share link

The application is now ready for clean, professional distribution! 🚀
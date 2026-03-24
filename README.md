# Repo Browser JavaFX

A desktop JavaFX application for browsing and managing local code repositories.

## Features

- Browse local directories to find repositories
- Filter repositories by name
- Select/deselect repositories with checkboxes
- Clean, modern JavaFX interface
- No server required - pure desktop application

## Requirements

- Java 17 or higher
- Maven 3.6 or higher

## Checking Java

Run these commands to verify Java is installed and is version 17 or higher.

- PowerShell (Windows):

```powershell
java -version
javac -version
where.exe java
echo $env:JAVA_HOME
```

- Command Prompt (Windows):

```cmd
java -version
javac -version
where java
echo %JAVA_HOME%
```

Expected output: `java -version` should show a Java 17+ runtime (for example `openjdk version "17.0.x"` or `java version "17.x"`). If the version is lower than 17, install a Java 17+ JDK and set `JAVA_HOME` to the JDK installation path.

## Building

```bash
mvn clean compile
```

## Running

### Development Mode

```bash
mvn javafx:run
```

### Building Fat JAR

```bash
mvn clean package
java -jar target/repo-browser-java-1.0.0.jar
```

## Project Structure

```
src/main/java/com/tandvu/repobrowser/
├── RepoBrowserApplication.java      # Main JavaFX application
├── controller/
│   └── MainController.java          # Main UI controller
├── model/
│   └── Repository.java              # Repository data model
└── service/
    └── RepositoryScanner.java       # Repository scanning logic

src/main/resources/
├── fxml/
│   └── main.fxml                    # Main UI layout
└── css/
    └── application.css              # Application styling
```

## Usage

1. Launch the application

- Quick (recommended - Windows): run the provided build and start scripts from the repository root:

```powershell
.\build.bat
.\start.bat
```

- Manual build + run:

```powershell
mvn -DskipTests clean package
java -jar target\repo-browser-java-1.0.0.jar
```

- Run in development mode (no fat JAR):

```powershell
mvn javafx:run
```

2. Click "Browse..." to select a base directory
3. Click "Scan" to find repositories in that directory
4. Use the filter box to narrow results
5. Select/deselect repositories as needed

Run by double-clicking

- Double-click `start.bat` (Windows):
  - After `build.bat` has created `repo-browser.jar`, double-clicking `start.bat` will run `java -jar repo-browser.jar`.

- Or double-click the JAR directly:
  - If `repo-browser.jar` exists in the repo root and `.jar` files are associated with Java on your system, you can double-click `repo-browser.jar` to run the app.
  - If you prefer the packaged artifact in `target/`, double-click `target\repo-browser-java-1.0.0.jar` after building.

- Notes:
  - If double-clicking a JAR doesn't work, ensure Java 17+ is installed and `.jar` files are associated with the Java runtime, or use `java -jar path\to\repo-browser.jar` from a terminal.
  - To create `repo-browser.jar`, run `build.bat` (or `mvn -DskipTests clean package`).

## Migration Notes

This is a JavaFX desktop port of the original Node.js/React web application, providing the same functionality without requiring a server or browser.

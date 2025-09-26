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
2. Click "Browse..." to select a base directory
3. Click "Scan" to find repositories in that directory
4. Use the filter box to narrow results
5. Select/deselect repositories as needed

## Migration Notes

This is a JavaFX desktop port of the original Node.js/React web application, providing the same functionality without requiring a server or browser.
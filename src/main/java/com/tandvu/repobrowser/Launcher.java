package com.tandvu.repobrowser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Launcher class to work around JavaFX module issues in fat JARs
 * This class attempts to launch JavaFX with proper module configuration
 */
public class Launcher {
    
    public static void main(String[] args) {
        System.out.println("Repo Browser JavaFX Launcher");
        System.out.println("=============================");
        
        // First try: Direct launch (works if JavaFX is already configured)
        try {
            System.out.println("Attempting direct JavaFX launch...");
            RepoBrowserApplication.main(args);
            return; // Success!
        } catch (Exception e) {
            System.out.println("Direct launch failed: " + e.getMessage());
            System.out.println("Attempting alternative launch methods...");
        }
        
        // Second try: Launch with explicit JavaFX module path
        try {
            launchWithJavaFXModules(args);
            return; // Success!
        } catch (Exception e) {
            System.out.println("Module path launch failed: " + e.getMessage());
        }
        
        // If all fails, show helpful error message
        showErrorMessage();
    }
    
    private static void launchWithJavaFXModules(String[] args) throws Exception {
        // Try to find JavaFX installation
        List<String> possibleJavaFXPaths = Arrays.asList(
            System.getProperty("java.home") + "/lib",
            "C:/Program Files/Java/javafx/lib",
            "C:/OPT/javafx/lib",
            System.getProperty("user.home") + "/.m2/repository/org/openjfx"
        );
        
        String javaFXPath = null;
        for (String path : possibleJavaFXPaths) {
            if (Files.exists(Paths.get(path)) && containsJavaFXJars(path)) {
                javaFXPath = path;
                break;
            }
        }
        
        if (javaFXPath != null) {
            System.out.println("Found JavaFX at: " + javaFXPath);
            
            // Build command to restart with proper modules
            List<String> command = new ArrayList<>();
            command.add("java");
            command.add("--module-path");
            command.add(javaFXPath);
            command.add("--add-modules");
            command.add("javafx.controls,javafx.fxml");
            command.add("-cp");
            command.add(getCurrentJarPath());
            command.add("com.tandvu.repobrowser.RepoBrowserApplication");
            
            System.out.println("Restarting with JavaFX modules...");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            Process process = pb.start();
            System.exit(process.waitFor());
        } else {
            throw new Exception("JavaFX libraries not found in common locations");
        }
    }
    
    private static boolean containsJavaFXJars(String path) {
        try {
            Path dirPath = Paths.get(path);
            return Files.walk(dirPath, 2)
                .anyMatch(p -> p.getFileName().toString().contains("javafx-controls"));
        } catch (Exception e) {
            return false;
        }
    }
    
    private static String getCurrentJarPath() {
        try {
            return new File(Launcher.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            return "repo-browser.jar";
        }
    }
    
    private static void showErrorMessage() {
        System.err.println();
        System.err.println("=== Repo Browser Launch Error ===");
        System.err.println("The application could not start due to JavaFX configuration issues.");
        System.err.println();
        System.err.println("Please try one of these solutions:");
        System.err.println("1. Double-click: run-repo-browser.bat (recommended)");
        System.err.println("2. Command line: mvn javafx:run");
        System.err.println("3. Install a JDK with JavaFX included");
        System.err.println();
        System.err.println("For more help, see RUNNING.md");
        System.err.println();
        System.err.println("Press Enter to exit...");
        
        try {
            System.in.read();
        } catch (Exception ignored) {}
    }
}
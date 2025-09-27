package com.tandvu.repobrowser.service;

import com.tandvu.repobrowser.model.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for scanning directories to find repositories
 */
public class RepositoryScanner {
    
    private static final Logger logger = LoggerFactory.getLogger(RepositoryScanner.class);
    
    /**
     * Scan the given base path for repositories (top-level directories only)
     * 
     * @param basePath The base path to scan
     * @return List of found repositories
     * @throws IOException if there's an error accessing the file system
     */
    public List<Repository> scanForRepositories(Path basePath) throws IOException {
        logger.info("Scanning for repositories in: {}", basePath);
        
        if (!Files.exists(basePath)) {
            throw new IOException("Base path does not exist: " + basePath);
        }
        
        if (!Files.isDirectory(basePath)) {
            throw new IOException("Base path is not a directory: " + basePath);
        }
        
        List<Repository> repositories = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(basePath, Files::isDirectory)) {
            for (Path dir : stream) {
                String dirName = dir.getFileName().toString();
                
                // Skip hidden directories
                if (dirName.startsWith(".")) {
                    continue;
                }
                
                // Create repository entry
                Repository repo = new Repository(dirName, dir.toString());
                
                // Try to detect repository version
                String version = detectRepositoryVersion(dir);
                repo.setRepoVersion(version);
                
                repositories.add(repo);
                
                logger.debug("Found repository: {} at {}", dirName, dir);
            }
        }
        
        // Sort by name for consistent ordering
        repositories = repositories.stream()
            .sorted((r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()))
            .collect(Collectors.toList());
        
        logger.info("Found {} repositories in {}", repositories.size(), basePath);
        return repositories;
    }
    
    /**
     * Check if a directory contains indicators that it's a repository
     * (e.g., .git folder, package.json, pom.xml, etc.)
     * 
     * @param path The directory to check
     * @return true if it looks like a repository
     */
    public boolean isRepository(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        
        // Check for common repository indicators
        String[] indicators = {
            ".git",           // Git repository
            "package.json",   // Node.js project
            "pom.xml",        // Maven project
            "build.gradle",   // Gradle project
            ".gitignore",     // Has git ignore file
            "README.md",      // Has README
            "src"             // Has source directory
        };
        
        for (String indicator : indicators) {
            if (Files.exists(path.resolve(indicator))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Try to detect the version of a repository by examining common version files
     * 
     * @param repoPath The repository directory path
     * @return The detected version or empty string if not found
     */
    public String detectRepositoryVersion(Path repoPath) {
        // Try package.json for Node.js projects
        Path packageJson = repoPath.resolve("package.json");
        if (Files.exists(packageJson)) {
            try {
                String content = Files.readString(packageJson);
                // Simple regex to extract version from package.json
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception e) {
                logger.debug("Error reading package.json for {}: {}", repoPath.getFileName(), e.getMessage());
            }
        }
        
        // Try pom.xml for Maven projects
        Path pomXml = repoPath.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            try {
                String content = Files.readString(pomXml);
                // Simple regex to extract version from pom.xml
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("<version>([^<]+)</version>");
                java.util.regex.Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception e) {
                logger.debug("Error reading pom.xml for {}: {}", repoPath.getFileName(), e.getMessage());
            }
        }
        
        // Try build.gradle for Gradle projects
        Path buildGradle = repoPath.resolve("build.gradle");
        if (Files.exists(buildGradle)) {
            try {
                String content = Files.readString(buildGradle);
                // Simple regex to extract version from build.gradle
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("version\\s*[=:]\\s*['\"]([^'\"]+)['\"]");
                java.util.regex.Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Exception e) {
                logger.debug("Error reading build.gradle for {}: {}", repoPath.getFileName(), e.getMessage());
            }
        }
        
        return ""; // No version found
    }
}
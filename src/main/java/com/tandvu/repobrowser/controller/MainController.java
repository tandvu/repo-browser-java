package com.tandvu.repobrowser.controller;

import com.tandvu.repobrowser.model.Repository;
import com.tandvu.repobrowser.service.RepositoryScanner;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Main controller for the Repo Browser application
 */
public class MainController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    // Preferences constants
    private static final String PREF_REPOSITORY_PATH = "repository_path";
    private static final String PREF_DEPLOYMENT_PATH = "deployment_path";
    private static final String DEFAULT_REPOSITORY_PATH = "C:\\AMPT";
    private static final String DEFAULT_DEPLOYMENT_PATH = "C:\\OPT";
    
    // Preferences instance
    private final Preferences preferences = Preferences.userNodeForPackage(MainController.class);
    
    @FXML private TextField basePathField;
    @FXML private Button browseButton;
    @FXML private TextField deploymentPathField;
    @FXML private Button browseDeploymentButton;
    @FXML private Label soaPathLabel;
    @FXML private TextArea filterField;
    @FXML private TableView<Repository> repoTable;
    @FXML private TableColumn<Repository, Boolean> selectedColumn;
    @FXML private TableColumn<Repository, String> nameColumn;
    @FXML private TableColumn<Repository, String> repoVersionColumn;
    @FXML private TableColumn<Repository, String> targetedVersionColumn;
        @FXML
    private TableColumn<Repository, String> deploymentVersionColumn;
    @FXML
    private Button buildMasterButton;
    @FXML
    private VBox buildLogContainer;
    @FXML
    private TextArea buildLogArea;
    @FXML
    private Label buildStatusLabel;
    @FXML
    private Button backToTableButton;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    
    private final ObservableList<Repository> repositories = FXCollections.observableArrayList();
    private final ObservableList<Repository> filteredRepositories = FXCollections.observableArrayList();
    private final RepositoryScanner repositoryScanner = new RepositoryScanner();
    private final Map<String, String> targetedVersions = new HashMap<>();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing MainController");
        
        // Initialize build log area first to prevent JavaFX text rendering issues
        initializeBuildLogArea();
        
        // Load saved paths from preferences
        loadSavedPaths();
        
        // Setup table columns
        setupTableColumns();
        
        // Setup filter functionality
        setupFilter();
        
        // Setup automatic scanning when path changes
        setupAutoScan();
        
        // Set initial status
        statusLabel.setText("Ready - Select a repository path to browse");
        progressBar.setVisible(false);
        
        // Perform initial scan if default path exists
        performInitialScan();
        
        logger.info("MainController initialized successfully");
    }

    /**
     * Safely initialize the build log area to prevent JavaFX text rendering issues
     */
    private void initializeBuildLogArea() {
        try {
            if (buildLogArea != null) {
                // Set basic properties
                buildLogArea.setEditable(false);
                buildLogArea.setWrapText(true);
                buildLogArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 12px;");
                
                // Clear the default text from FXML to start fresh
                buildLogArea.setText("");
                
                // Force initial text layout by setting and clearing content
                buildLogArea.setText("Initializing...");
                Thread.sleep(50); // Small delay to let JavaFX process the text
                buildLogArea.clear();
                
                // Hide the build log container initially
                if (buildLogContainer != null) {
                    buildLogContainer.setVisible(false);
                }
                
                logger.debug("Build log area initialized successfully");
            }
        } catch (Exception e) {
            logger.error("Error initializing build log area: " + e.getMessage(), e);
            // If all else fails, try to set a simple TextArea with minimal formatting
            if (buildLogArea != null) {
                try {
                    buildLogArea.setStyle("-fx-font-family: monospace;");
                    buildLogArea.setText("");
                } catch (Exception fallbackEx) {
                    logger.error("Even fallback TextArea initialization failed: " + fallbackEx.getMessage());
                }
            }
        }
    }

    /**
     * Load saved paths from preferences
     */
    private void loadSavedPaths() {
        // Load repository path
        String savedRepositoryPath = preferences.get(PREF_REPOSITORY_PATH, DEFAULT_REPOSITORY_PATH);
        basePathField.setText(savedRepositoryPath);
        logger.info("Loaded repository path from preferences: {}", savedRepositoryPath);
        
        // Load deployment path
        String savedDeploymentPath = preferences.get(PREF_DEPLOYMENT_PATH, DEFAULT_DEPLOYMENT_PATH);
        deploymentPathField.setText(savedDeploymentPath);
        logger.info("Loaded deployment path from preferences: {}", savedDeploymentPath);
    }
    
    /**
     * Save repository path to preferences
     */
    private void saveRepositoryPath(String path) {
        if (path != null && !path.trim().isEmpty()) {
            preferences.put(PREF_REPOSITORY_PATH, path.trim());
            logger.info("Saved repository path to preferences: {}", path.trim());
        }
    }
    
    /**
     * Save deployment path to preferences
     */
    private void saveDeploymentPath(String path) {
        if (path != null && !path.trim().isEmpty()) {
            preferences.put(PREF_DEPLOYMENT_PATH, path.trim());
            logger.info("Saved deployment path to preferences: {}", path.trim());
        }
    }
    
    private void setupTableColumns() {
        // Selected column with checkboxes and header checkbox
        selectedColumn.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectedColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectedColumn));
        selectedColumn.setEditable(true);
        
        // Add header checkbox for select all/clear all
        setupHeaderCheckbox();
        
        // Repository name column
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        
        // Repo version column
        repoVersionColumn.setCellValueFactory(new PropertyValueFactory<>("repoVersion"));
        
        // Targeted version column
        targetedVersionColumn.setCellValueFactory(new PropertyValueFactory<>("targetedVersion"));
        
        // Deployment version column
        deploymentVersionColumn.setCellValueFactory(new PropertyValueFactory<>("deploymentVersion"));

        // Make table editable for checkboxes
        repoTable.setEditable(true);
        repoTable.setItems(filteredRepositories);        // Setup row factory for click-to-select functionality
        setupRowFactory();
    }
    
    /**
     * Setup row factory for click-to-select functionality and visual styling
     */
    private void setupRowFactory() {
        repoTable.setRowFactory(tv -> {
            TableRow<Repository> row = new TableRow<Repository>() {
                @Override
                protected void updateItem(Repository repository, boolean empty) {
                    super.updateItem(repository, empty);
                    
                    if (empty || repository == null) {
                        getStyleClass().removeAll("repository-selected", "version-mismatch");
                    } else {
                        // Update row styling based on selection state
                        updateRowStyle(repository);
                        
                        // Check for version mismatch and apply styling
                        updateVersionMismatchStyle(repository);
                    }
                }
                
                private void updateRowStyle(Repository repository) {
                    if (repository != null && repository.isSelected()) {
                        if (!getStyleClass().contains("repository-selected")) {
                            getStyleClass().add("repository-selected");
                        }
                    } else {
                        getStyleClass().remove("repository-selected");
                    }
                }
                
                private void updateVersionMismatchStyle(Repository repository) {
                    if (repository != null) {
                        boolean hasMismatch = hasVersionMismatch(repository);
                        if (hasMismatch) {
                            if (!getStyleClass().contains("version-mismatch")) {
                                getStyleClass().add("version-mismatch");
                            }
                        } else {
                            getStyleClass().remove("version-mismatch");
                        }
                    }
                }
            };
            
            // Add click handler to toggle selection
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    Repository repository = row.getItem();
                    if (repository != null) {
                        boolean newSelection = !repository.isSelected();
                        repository.setSelected(newSelection);
                        
                        logger.info("Repository '{}' {} via row click", 
                            repository.getName(), 
                            newSelection ? "selected" : "deselected");
                        
                        // Refresh the table to update all row styles
                        repoTable.refresh();
                        
                        // Note: updateStatusLabel() and updateHeaderCheckboxState() 
                        // will be called automatically by the listener in scanRepositories
                    }
                }
            });
            
            return row;
        });
    }
    
    /**
     * Setup header checkbox for select all/clear all functionality
     */
    private void setupHeaderCheckbox() {
        CheckBox headerCheckBox = new CheckBox();
        headerCheckBox.setSelected(false);
        
        // Set the checkbox as the column header graphic
        selectedColumn.setGraphic(headerCheckBox);
        
        // Handle header checkbox action
        headerCheckBox.setOnAction(event -> {
            boolean isSelected = headerCheckBox.isSelected();
            
            // Apply to all visible (filtered) repositories
            filteredRepositories.forEach(repo -> repo.setSelected(isSelected));
            repoTable.refresh();
            
            if (isSelected) {
                logger.info("Selected all visible repositories via header checkbox");
            } else {
                logger.info("Cleared all repository selections via header checkbox");
            }
            
            updateStatusLabel();
        });
    }
    
    /**
     * Update the header checkbox state based on current selections
     */
    private void updateHeaderCheckboxState() {
        CheckBox headerCheckBox = (CheckBox) selectedColumn.getGraphic();
        if (headerCheckBox == null || filteredRepositories.isEmpty()) {
            if (headerCheckBox != null) {
                headerCheckBox.setIndeterminate(false);
                headerCheckBox.setSelected(false);
            }
            return;
        }
        
        long selectedCount = filteredRepositories.stream()
            .mapToLong(repo -> repo.isSelected() ? 1 : 0)
            .sum();
        
        // Temporarily remove the action listener to avoid recursion
        var currentAction = headerCheckBox.getOnAction();
        headerCheckBox.setOnAction(null);
        
        if (selectedCount == 0) {
            headerCheckBox.setIndeterminate(false);
            headerCheckBox.setSelected(false);
        } else if (selectedCount == filteredRepositories.size()) {
            headerCheckBox.setIndeterminate(false);
            headerCheckBox.setSelected(true);
        } else {
            headerCheckBox.setIndeterminate(true);
            headerCheckBox.setSelected(false);
        }
        
        // Restore the action listener
        headerCheckBox.setOnAction(currentAction);
    }
    
    private void setupFilter() {
        filterField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterRepositories(newValue);
        });
    }
    
    private void setupAutoScan() {
        // Repository path listener for auto-scan and saving
        basePathField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.trim().isEmpty()) {
                File dir = new File(newValue.trim());
                if (dir.exists() && dir.isDirectory()) {
                    scanRepositories(Path.of(newValue.trim()));
                    saveRepositoryPath(newValue.trim()); // Save to preferences when manually typed
                }
            } else {
                // Clear repositories when path is empty
                repositories.clear();
                filteredRepositories.clear();
                updateStatusLabel();
            }
        });
        
        // Deployment path listener for saving
        deploymentPathField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.trim().isEmpty()) {
                File dir = new File(newValue.trim());
                if (dir.exists() && dir.isDirectory()) {
                    saveDeploymentPath(newValue.trim()); // Save to preferences when manually typed
                    updateDeploymentVersions(dir.toPath());
                }
            }
        });
    }
    
    private void performInitialScan() {
        String initialPath = basePathField.getText();
        if (initialPath != null && !initialPath.trim().isEmpty()) {
            File dir = new File(initialPath);
            if (dir.exists() && dir.isDirectory()) {
                scanRepositories(Path.of(initialPath));
            }
        }
    }
    
    private void filterRepositories(String filter) {
        filteredRepositories.clear();
        targetedVersions.clear();
        
        // Clear targeted versions from all repositories
        repositories.forEach(repo -> repo.setTargetedVersion(""));
        
        if (filter == null || filter.trim().isEmpty()) {
            filteredRepositories.addAll(repositories);
        } else {
            // Split filter text by lines and extract repository names and versions
            String[] filterLines = filter.split("\\r?\\n");
            List<String> repoNames = new ArrayList<>();
            
            for (String line : filterLines) {
                String cleanLine = line.trim();
                if (!cleanLine.isEmpty()) {
                    // Split by tabs or multiple spaces to get parts
                    String[] parts = cleanLine.split("[\\s\\t]+");
                    if (parts.length >= 2) {
                        String repoName = parts[0].toLowerCase();
                        String version = parts[1];
                        if (!repoName.isEmpty()) {
                            repoNames.add(repoName);
                            targetedVersions.put(repoName, version);
                        }
                    } else if (parts.length == 1) {
                        // Just repository name, no version
                        String repoName = parts[0].toLowerCase();
                        if (!repoName.isEmpty()) {
                            repoNames.add(repoName);
                        }
                    }
                }
            }
            
            if (repoNames.isEmpty()) {
                filteredRepositories.addAll(repositories);
            } else {
                // Include repositories that match any of the extracted names
                // and set their targeted versions
                filteredRepositories.addAll(
                    repositories.stream()
                        .filter(repo -> {
                            String repoNameLower = repo.getName().toLowerCase();
                            String matchingFilter = repoNames.stream()
                                .filter(filterName -> repoNameLower.contains(filterName) || filterName.contains(repoNameLower))
                                .findFirst()
                                .orElse(null);
                            
                            if (matchingFilter != null) {
                                // Set the targeted version if available
                                String targetedVersion = targetedVersions.get(matchingFilter);
                                repo.setTargetedVersion(targetedVersion != null ? targetedVersion : "");
                                return true;
                            }
                            return false;
                        })
                        .collect(Collectors.toList())
                );
            }
        }
        
        updateStatusLabel();
        updateHeaderCheckboxState();
    }
    
    @FXML
    private void handleBrowseButton() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Repository Path for Browsing");
        
        // Set initial directory if current path exists
        String currentPath = basePathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                directoryChooser.setInitialDirectory(currentDir);
            }
        }
        
        File selectedDirectory = directoryChooser.showDialog(browseButton.getScene().getWindow());
        if (selectedDirectory != null) {
            String newPath = selectedDirectory.getAbsolutePath();
            basePathField.setText(newPath);
            saveRepositoryPath(newPath); // Save to preferences
            logger.info("Selected repository path: {}", newPath);
            // Scanning will happen automatically via the text field listener
        }
    }
    
    @FXML
    private void handleBrowseDeploymentButton() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Deployment Path");
        
        // Set initial directory if current deployment path exists
        String currentPath = deploymentPathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                directoryChooser.setInitialDirectory(currentDir);
            }
        }
        
        File selectedDirectory = directoryChooser.showDialog(browseDeploymentButton.getScene().getWindow());
        if (selectedDirectory != null) {
            String newPath = selectedDirectory.getAbsolutePath();
            deploymentPathField.setText(newPath);
            saveDeploymentPath(newPath); // Save to preferences
            logger.info("Selected deployment path: {}", newPath);
        }
    }
    
    private void scanRepositories(Path basePath) {
        logger.info("Scanning repositories in: {}", basePath);
        
        // Show progress
        progressBar.setVisible(true);
        statusLabel.setText("Scanning repositories...");
        
        // Clear existing repositories
        repositories.clear();
        filteredRepositories.clear();
        
        try {
            List<Repository> foundRepos = repositoryScanner.scanForRepositories(basePath);
            repositories.addAll(foundRepos);
            
            // Add opt-soa repository from SOA Path if it exists
            addSoaRepository();
            
            // Sort all repositories (including opt-soa) alphabetically by name
            repositories.sort((r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));
            
            filteredRepositories.addAll(repositories);
            
            // Add listeners to each repository to update header checkbox
            repositories.forEach(repo -> {
                repo.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    updateHeaderCheckboxState();
                    updateStatusLabel();
                    // Refresh the table to update row styles
                    Platform.runLater(() -> repoTable.refresh());
                });
            });
            
            logger.info("Found {} repositories", repositories.size());
            updateStatusLabel();
            updateHeaderCheckboxState();
            
            // After scanning repositories, try updating deployment versions if deployment path is valid
            String depPath = deploymentPathField.getText();
            if (depPath != null && !depPath.isBlank()) {
                File depDir = new File(depPath.trim());
                if (depDir.exists() && depDir.isDirectory()) {
                    updateDeploymentVersions(depDir.toPath());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error scanning repositories", e);
            showAlert("Error", "Failed to scan repositories: " + e.getMessage());
            statusLabel.setText("Error occurred during scanning");
        } finally {
            progressBar.setVisible(false);
        }
    }

    /**
     * Add opt-soa repository from the SOA Path if the directory exists
     */
    private void addSoaRepository() {
        String soaPath = soaPathLabel.getText();
        logger.info("Attempting to add opt-soa from SOA Path: '{}'", soaPath);
        
        if (soaPath != null && !soaPath.trim().isEmpty()) {
            // Normalize path separators for Windows
            String normalizedPath = soaPath.trim().replace('/', File.separatorChar).replace('\\', File.separatorChar);
            File soaDir = new File(normalizedPath);
            
            logger.info("Checking if SOA directory exists: '{}' -> exists: {}, isDirectory: {}", 
                normalizedPath, soaDir.exists(), soaDir.isDirectory());
            
            if (soaDir.exists() && soaDir.isDirectory()) {
                // Check if opt-soa already exists in the list (avoid duplicates)
                boolean alreadyExists = repositories.stream()
                    .anyMatch(repo -> "opt-soa".equalsIgnoreCase(repo.getName()));
                
                logger.info("opt-soa already exists in repository list: {}", alreadyExists);
                
                if (!alreadyExists) {
                    Repository soaRepo = new Repository("opt-soa", normalizedPath);
                    
                    // Try to detect version from the SOA directory with custom logic
                    try {
                        String version = detectSoaRepositoryVersion(soaDir.toPath());
                        logger.info("Detected version for opt-soa: '{}'", version);
                        soaRepo.setRepoVersion(version);
                    } catch (Exception e) {
                        logger.warn("Could not detect version for opt-soa: {}", e.getMessage());
                    }
                    
                    repositories.add(soaRepo);
                    logger.info("Successfully added opt-soa repository from SOA Path: {}", normalizedPath);
                } else {
                    logger.info("opt-soa repository already exists, skipping SOA Path addition");
                }
            } else {
                logger.warn("SOA Path does not exist or is not a directory: {} (normalized: {})", soaPath, normalizedPath);
            }
        } else {
            logger.warn("SOA Path is null or empty: '{}'", soaPath);
        }
    }

    /**
     * Detect version for opt-soa repository which may have a different structure
     */
    private String detectSoaRepositoryVersion(Path soaPath) {
        logger.info("Detecting SOA repository version in: {}", soaPath);
        
        // Try standard version detection first
        try {
            String version = repositoryScanner.detectRepositoryVersion(soaPath);
            if (version != null && !version.trim().isEmpty()) {
                logger.info("Found SOA version using standard detection: {}", version);
                return version;
            }
        } catch (Exception e) {
            logger.debug("Standard version detection failed for SOA: {}", e.getMessage());
        }
        
        // Try looking in subdirectories for version files
        try {
            java.nio.file.DirectoryStream<java.nio.file.Path> stream = 
                java.nio.file.Files.newDirectoryStream(soaPath, java.nio.file.Files::isDirectory);
            for (java.nio.file.Path subDir : stream) {
                logger.debug("Checking SOA subdirectory: {}", subDir.getFileName());
                try {
                    String version = repositoryScanner.detectRepositoryVersion(subDir);
                    if (version != null && !version.trim().isEmpty()) {
                        logger.info("Found SOA version in subdirectory {}: {}", subDir.getFileName(), version);
                        return version;
                    }
                } catch (Exception e) {
                    logger.debug("Version detection failed in subdirectory {}: {}", subDir.getFileName(), e.getMessage());
                }
            }
            stream.close();
        } catch (Exception e) {
            logger.debug("Error scanning SOA subdirectories: {}", e.getMessage());
        }
        
        // Try looking for specific SOA version patterns in files
        try {
            // Look for version.properties, version.txt, or similar files
            String[] versionFiles = {"version.properties", "version.txt", "VERSION", ".version"};
            for (String fileName : versionFiles) {
                java.nio.file.Path versionFile = soaPath.resolve(fileName);
                if (java.nio.file.Files.exists(versionFile)) {
                    String content = java.nio.file.Files.readString(versionFile);
                    // Look for version patterns
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("(?i)(?:version[=:\\s]+)([0-9]+(?:\\.[0-9]+)*(?:-[a-z0-9]+)?)")
                        .matcher(content);
                    if (matcher.find()) {
                        String version = matcher.group(1);
                        logger.info("Found SOA version in {}: {}", fileName, version);
                        return version;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error reading SOA version files: {}", e.getMessage());
        }
        
        logger.info("Could not detect version for opt-soa repository");
        return "";
    }

    @FXML
    private void handleBuildMaster() {
        // Get selected repositories
        List<Repository> selectedRepos = repositories.stream()
            .filter(Repository::isSelected)
            .collect(Collectors.toList());

        if (selectedRepos.isEmpty()) {
            showAlert("No Selection", "Please select at least one repository to build.");
            return;
        }

        if (selectedRepos.size() > 1) {
            showAlert("Multiple Selection", "Please select only one repository to build.");
            return;
        }

        Repository selectedRepo = selectedRepos.get(0);
        startBuildProcess(selectedRepo);
    }

    @FXML
    private void handleBackToTable() {
        // Show table, hide build log
        repoTable.setVisible(true);
        buildLogContainer.setVisible(false);
        buildMasterButton.setDisable(false);
    }

    private void startBuildProcess(Repository repository) {
        // Hide table, show build log
        repoTable.setVisible(false);
        buildLogContainer.setVisible(true);
        buildMasterButton.setDisable(true);
        
        // Clear previous log and ensure it's ready
        Platform.runLater(() -> {
            if (buildLogArea != null) {
                buildLogArea.clear();
                buildLogArea.setText(""); // Explicitly set to empty string
            }
            buildStatusLabel.setText("Building " + repository.getName() + "...");
        });
        
        // Small delay to ensure UI is updated
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Get repository path
        String basePath = basePathField.getText().trim();
        if (basePath.isEmpty()) {
            appendToBuildLog("ERROR: No repository path configured\n");
            buildStatusLabel.setText("Build Failed");
            buildMasterButton.setDisable(false);
            return;
        }
        
        Path repoPath = Paths.get(basePath, repository.getName());
        if (!Files.exists(repoPath)) {
            appendToBuildLog("ERROR: Repository path does not exist: " + repoPath + "\n");
            buildStatusLabel.setText("Build Failed");
            buildMasterButton.setDisable(false);
            return;
        }
        
        // Start build process in background thread
        Task<Void> buildTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    final Repository finalRepository = repository;
                    final Path finalRepoPath = repoPath;
                    
                    // Step 1: Check if it's a git repository
                    appendToBuildLog("=== Checking Git Repository ===\n");
                    if (!Files.exists(finalRepoPath.resolve(".git"))) {
                        appendToBuildLog("ERROR: Not a git repository: " + finalRepoPath + "\n");
                        Platform.runLater(() -> {
                            buildStatusLabel.setText("Build Failed");
                            buildMasterButton.setDisable(false);
                        });
                        return null;
                    }
                    
                    // Step 2: Checkout master branch
                    appendToBuildLog("=== Checking out master branch ===\n");
                    ProcessBuilder gitCheckout = new ProcessBuilder("git", "checkout", "master");
                    gitCheckout.directory(finalRepoPath.toFile());
                    gitCheckout.redirectErrorStream(true);
                    
                    Process gitProcess = gitCheckout.start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(gitProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            final String logLine = line != null ? line : "";
                            Platform.runLater(() -> appendToBuildLog(logLine + "\n"));
                        }
                    }
                    
                    int gitExitCode = gitProcess.waitFor();
                    if (gitExitCode != 0) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: Git checkout failed with exit code: " + gitExitCode + "\n");
                            buildStatusLabel.setText("Build Failed");
                            buildMasterButton.setDisable(false);
                        });
                        return null;
                    }
                    
                    // Step 3: Pull latest changes
                    appendToBuildLog("=== Pulling latest changes ===\n");
                    ProcessBuilder gitPull = new ProcessBuilder("git", "pull");
                    gitPull.directory(finalRepoPath.toFile());
                    gitPull.redirectErrorStream(true);
                    
                    Process pullProcess = gitPull.start();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(pullProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            final String logLine = line != null ? line : "";
                            Platform.runLater(() -> appendToBuildLog(logLine + "\n"));
                        }
                    }
                    
                    int pullExitCode = pullProcess.waitFor();
                    if (pullExitCode != 0) {
                        Platform.runLater(() -> {
                            appendToBuildLog("WARNING: Git pull failed with exit code: " + pullExitCode + "\n");
                            appendToBuildLog("Continuing with build...\n");
                        });
                    }
                    
                    // Step 4: Check for package.json
                    appendToBuildLog("=== Checking for package.json ===\n");
                    if (!Files.exists(repoPath.resolve("package.json"))) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: package.json not found in repository\n");
                            buildStatusLabel.setText("Build Failed");
                            buildMasterButton.setDisable(false);
                        });
                        return null;
                    }
                    
                    // Step 5: Run npm run build
                    appendToBuildLog("=== Running npm run build ===\n");
                    
                    // Create npm command for Windows compatibility
                    ProcessBuilder npmBuild;
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        // On Windows, use cmd to run npm
                        npmBuild = new ProcessBuilder("cmd", "/c", "npm", "run", "build");
                    } else {
                        // On Unix-like systems
                        npmBuild = new ProcessBuilder("npm", "run", "build");
                    }
                    
                    npmBuild.directory(finalRepoPath.toFile());
                    npmBuild.redirectErrorStream(true);
                    
                    // Log the command being executed
                    String commandStr = String.join(" ", npmBuild.command());
                    appendToBuildLog("Executing: " + commandStr + "\n");
                    appendToBuildLog("Working directory: " + repoPath.toString() + "\n\n");
                    
                    Process npmProcess;
                    try {
                        npmProcess = npmBuild.start();
                    } catch (IOException e) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: Failed to start npm process: " + e.getMessage() + "\n");
                            appendToBuildLog("This might indicate that npm is not installed or not in PATH.\n");
                            appendToBuildLog("Please ensure Node.js and npm are properly installed.\n");
                            buildStatusLabel.setText("Build Failed - npm not found");
                            buildMasterButton.setDisable(false);
                        });
                        return null;
                    }
                    
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(npmProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            final String logLine = line != null ? line : "";
                            Platform.runLater(() -> appendToBuildLog(logLine + "\n"));
                        }
                    }
                    
                    int npmExitCode = npmProcess.waitFor();
                    Platform.runLater(() -> {
                        if (npmExitCode == 0) {
                            appendToBuildLog("\n=== Build Completed Successfully ===\n");
                            buildStatusLabel.setText("Build Successful - Deploying...");
                            
                            // Start deployment in background
                            Task<Void> deployTask = createDeploymentTask(finalRepository, finalRepoPath);
                            Thread deployThread = new Thread(deployTask);
                            deployThread.setDaemon(true);
                            deployThread.start();
                        } else {
                            appendToBuildLog("\nERROR: npm run build failed with exit code: " + npmExitCode + "\n");
                            buildStatusLabel.setText("Build Failed");
                            buildMasterButton.setDisable(false);
                        }
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        appendToBuildLog("ERROR: " + e.getMessage() + "\n");
                        buildStatusLabel.setText("Build Failed");
                        buildMasterButton.setDisable(false);
                    });
                    logger.error("Build process failed", e);
                }
                
                return null;
            }
        };
        
        // Run build task in background
        Thread buildThread = new Thread(buildTask);
        buildThread.setDaemon(true);
        buildThread.start();
    }
    
    /**
     * Create deployment task to copy WAR file to deployment directory
     */
    private Task<Void> createDeploymentTask(Repository repository, Path repoPath) {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    Platform.runLater(() -> appendToBuildLog("\n=== Starting Deployment ===\n"));
                    
                    // Get deployment path
                    String deploymentPath = deploymentPathField.getText();
                    if (deploymentPath == null || deploymentPath.trim().isEmpty()) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: No deployment path specified\n");
                            buildStatusLabel.setText("Deployment Failed - No path");
                            buildMasterButton.setDisable(false);
                        });
                        return null;
                    }
                    
                    Path deploymentDir = Path.of(deploymentPath.trim());
                    if (!Files.exists(deploymentDir) || !Files.isDirectory(deploymentDir)) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: Deployment directory does not exist: " + deploymentPath + "\n");
                            buildStatusLabel.setText("Deployment Failed - Invalid path");
                            buildMasterButton.setDisable(false);
                        });
                        return null;
                    }
                    
                    // Look for WAR files in the build output directory
                    Path targetDir = repoPath.resolve("target");
                    if (!Files.exists(targetDir)) {
                        // Try dist directory for some projects
                        targetDir = repoPath.resolve("dist");
                    }
                    if (!Files.exists(targetDir)) {
                        // Try build directory
                        targetDir = repoPath.resolve("build");
                    }
                    
                    final Path finalTargetDir = targetDir;
                    
                    if (!Files.exists(finalTargetDir)) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: No target/dist/build directory found in repository\n");
                            buildStatusLabel.setText("Deployment Failed - No build output");
                            buildMasterButton.setDisable(false);
                        });
                        return null;
                    }
                    
                    Platform.runLater(() -> appendToBuildLog("Looking for WAR files in: " + finalTargetDir + "\n"));
                    
                    // Find WAR files
                    List<Path> warFiles = new ArrayList<>();
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(finalTargetDir, "*.war")) {
                        for (Path warFile : stream) {
                            warFiles.add(warFile);
                        }
                    }
                    
                    if (warFiles.isEmpty()) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: No WAR files found in build output directory\n");
                            buildStatusLabel.setText("Deployment Failed - No WAR files");
                            buildMasterButton.setDisable(false);
                        });
                        return null;
                    }
                    
                    // Deploy each WAR file
                    for (Path warFile : warFiles) {
                        String warFileName = warFile.getFileName().toString();
                        
                        // Extract version from new WAR file name
                        String newVersion = extractVersionFromWarFile(warFileName);
                        String displayVersion = newVersion.isEmpty() ? "unknown" : newVersion;
                        
                        Platform.runLater(() -> appendToBuildLog("Preparing to deploy: " + warFileName + " (version: " + displayVersion + ")\n"));
                        
                        // Find and delete existing WAR files for this repository
                        String repoName = repository.getName();
                        deleteExistingWarFiles(deploymentDir, repoName, warFileName);
                        
                        // Deploy the new WAR file
                        Path deploymentTarget = deploymentDir.resolve(warFileName);
                        Platform.runLater(() -> appendToBuildLog("Deploying new WAR: " + warFileName + "\n"));
                        
                        Files.copy(warFile, deploymentTarget, StandardCopyOption.REPLACE_EXISTING);
                        
                        Platform.runLater(() -> appendToBuildLog("Successfully deployed: " + warFileName + " (version: " + displayVersion + ") -> " + deploymentTarget + "\n"));
                    }
                    
                    Platform.runLater(() -> {
                        appendToBuildLog("\n=== Deployment Completed Successfully ===\n");
                        appendToBuildLog("Deployed " + warFiles.size() + " WAR file(s) to: " + deploymentPath + "\n");
                        buildStatusLabel.setText("Build & Deployment Successful");
                        buildMasterButton.setDisable(false);
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        appendToBuildLog("ERROR: Deployment failed - " + e.getMessage() + "\n");
                        buildStatusLabel.setText("Deployment Failed");
                        buildMasterButton.setDisable(false);
                    });
                    logger.error("Deployment failed", e);
                }
                
                return null;
            }
        };
    }
    
    /**
     * Delete existing WAR files for the repository in the deployment directory
     */
    private void deleteExistingWarFiles(Path deploymentDir, String repoName, String newWarFileName) {
        try {
            // Create patterns to match WAR files for this repository
            List<String> patterns = createWarFilePatterns(repoName);
            
            Platform.runLater(() -> appendToBuildLog("Checking for existing WAR files for repository: " + repoName + "\n"));
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(deploymentDir, "*.war")) {
                for (Path existingWar : stream) {
                    String existingWarName = existingWar.getFileName().toString();
                    
                    // Don't delete the same file we're about to deploy
                    if (existingWarName.equals(newWarFileName)) {
                        continue;
                    }
                    
                    // Check if this WAR file belongs to the same repository
                    if (isWarFileForRepository(existingWarName, patterns)) {
                        String oldVersion = extractVersionFromWarFile(existingWarName);
                        String displayOldVersion = oldVersion.isEmpty() ? "unknown" : oldVersion;
                        
                        Platform.runLater(() -> appendToBuildLog("Found existing WAR file: " + existingWarName + " (version: " + displayOldVersion + ")\n"));
                        Platform.runLater(() -> appendToBuildLog("Deleting old WAR file: " + existingWarName + "\n"));
                        
                        Files.delete(existingWar);
                        
                        Platform.runLater(() -> appendToBuildLog("Successfully deleted: " + existingWarName + "\n"));
                    }
                }
            }
        } catch (Exception e) {
            Platform.runLater(() -> appendToBuildLog("Warning: Failed to clean up existing WAR files: " + e.getMessage() + "\n"));
            logger.warn("Failed to clean up existing WAR files for repository: " + repoName, e);
        }
    }
    
    /**
     * Create patterns to match WAR files for a repository
     */
    private List<String> createWarFilePatterns(String repoName) {
        List<String> patterns = new ArrayList<>();
        
        // Standard AMPT pattern: ampt-<repo>-<version>.war
        if (repoName.startsWith("opt-")) {
            String amptName = repoName.substring(4); // Remove "opt-" prefix
            patterns.add("ampt-" + amptName + "-");
        }
        
        // SOA pattern: opt-soa-<version>.war
        if ("opt-soa".equals(repoName)) {
            patterns.add("opt-soa-");
        }
        
        // Direct pattern: <repo>-<version>.war
        patterns.add(repoName + "-");
        
        return patterns;
    }
    
    /**
     * Check if a WAR file belongs to the given repository
     */
    private boolean isWarFileForRepository(String warFileName, List<String> patterns) {
        for (String pattern : patterns) {
            if (warFileName.startsWith(pattern) && warFileName.endsWith(".war")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extract version from WAR file name
     */
    private String extractVersionFromWarFile(String warFileName) {
        // Remove .war extension
        String baseName = warFileName.replace(".war", "");
        
        // Try to extract version using regex patterns
        // Pattern 1: ampt-<name>-<version> or opt-soa-<version> or <name>-<version>
        Pattern versionPattern = Pattern.compile(".*-(\\d+(?:\\.\\d+)*(?:-[a-zA-Z0-9]+)?)$");
        Matcher matcher = versionPattern.matcher(baseName);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "";
    }
    
    private void appendToBuildLog(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        // Ensure we're on the JavaFX Application Thread
        final String safeText = text.replace("\r\n", "\n").replace("\r", "\n");
        
        if (Platform.isFxApplicationThread()) {
            try {
                if (buildLogArea != null) {
                    // Get current text safely
                    String currentText = buildLogArea.getText();
                    if (currentText == null) {
                        currentText = "";
                    }
                    
                    // Append the new text
                    String newText = currentText + safeText;
                    
                    // Limit log size to prevent memory issues
                    if (newText.length() > 100000) {
                        int startIndex = newText.length() - 80000;
                        newText = "... [log truncated] ...\n" + newText.substring(startIndex);
                    }
                    
                    buildLogArea.setText(newText);
                    
                    // Position caret at end and scroll to bottom
                    buildLogArea.positionCaret(buildLogArea.getText().length());
                }
            } catch (Exception e) {
                // If JavaFX fails, at least log to console
                System.out.println("[BUILD LOG] " + safeText);
                logger.warn("Error appending to build log: {}", e.getMessage());
            }
        } else {
            Platform.runLater(() -> appendToBuildLog(safeText));
        }
    }

    /**
     * Check if a repository has version mismatch between deployment, repo, and targeted versions
     */
    private boolean hasVersionMismatch(Repository repository) {
        String deploymentVersion = repository.getDeploymentVersion();
        String repoVersion = repository.getRepoVersion();
        String targetedVersion = repository.getTargetedVersion();
        
        // No mismatch if deployment version is empty (not deployed)
        if (deploymentVersion == null || deploymentVersion.trim().isEmpty()) {
            return false;
        }
        
        // Check against repo version if it exists
        if (repoVersion != null && !repoVersion.trim().isEmpty()) {
            if (!deploymentVersion.equals(repoVersion)) {
                return true;
            }
        }
        
        // Check against targeted version if it exists
        if (targetedVersion != null && !targetedVersion.trim().isEmpty()) {
            if (!deploymentVersion.equals(targetedVersion)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Update deploymentVersion for repositories by scanning the deployment folder for .war files
     * Expected filename pattern example: ampt-orgchart-3.4.0.war -> repo name: opt-orgchart, version: 3.4.0
     */
    private void updateDeploymentVersions(Path deploymentPath) {
        try {
            File depDir = deploymentPath.toFile();
            if (!depDir.exists() || !depDir.isDirectory()) {
                return;
            }
            // Build a map from repo key (e.g., orgchart) to version from WAR files
            Map<String, String> deployedVersions = new HashMap<>();
            File[] warFiles = depDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".war"));
            if (warFiles != null) {
                logger.info("Found {} WAR files in deployment directory", warFiles.length);
                for (File war : warFiles) {
                    String name = war.getName().toLowerCase();
                    logger.debug("Processing WAR file: {}", name);
                    
                    // Handle opt-soa pattern: opt-soa-<version>.war
                    java.util.regex.Matcher soaMatcher = java.util.regex.Pattern
                        .compile("^opt-soa-([0-9][a-z0-9.-]*)\\.war$")
                        .matcher(name);
                    if (soaMatcher.find()) {
                        String version = soaMatcher.group(1);
                        deployedVersions.put("soa", version); // map to "soa" suffix for opt-soa
                        logger.info("Detected SOA deployment: opt-soa -> version {}", version);
                        continue;
                    }
                    
                    // Handle regular ampt pattern: ampt-<suffix>-<version>.war
                    java.util.regex.Matcher amptMatcher = java.util.regex.Pattern
                        .compile("^ampt-([a-z0-9-]+)-([0-9][a-z0-9.-]*)\\.war$")
                        .matcher(name);
                    if (amptMatcher.find()) {
                        String suffix = amptMatcher.group(1);      // e.g., orgchart
                        String version = amptMatcher.group(2);     // e.g., 3.4.0 or 3.4.0-SNAPSHOT
                        deployedVersions.put(suffix, version);
                        logger.info("Detected AMPT deployment: {} -> version {}", suffix, version);
                    } else {
                        logger.debug("WAR file does not match expected patterns: {}", name);
                    }
                }
            }
            
            // Clear previous deployment versions
            repositories.forEach(r -> r.setDeploymentVersion(""));
            
            if (!deployedVersions.isEmpty()) {
                for (Repository repo : repositories) {
                    String repoName = repo.getName().toLowerCase(); // e.g., opt-orgchart, opt-soa
                    // map opt-<suffix> -> <suffix>
                    if (repoName.startsWith("opt-")) {
                        String suffix = repoName.substring(4);
                        String version = deployedVersions.get(suffix);
                        if (version != null) {
                            repo.setDeploymentVersion(version);
                        }
                    }
                }
                repoTable.refresh();
                logger.info("Updated deployment versions for {} repositories based on {} WAR files",
                        repositories.stream().filter(r -> !r.getDeploymentVersion().isEmpty()).count(),
                        deployedVersions.size());
            }
        } catch (Exception e) {
            logger.error("Error updating deployment versions from {}", deploymentPath, e);
        }
    }
    
    private void updateStatusLabel() {
        int total = repositories.size();
        int filtered = filteredRepositories.size();
        int selected = (int) repositories.stream().mapToInt(repo -> repo.isSelected() ? 1 : 0).sum();
        
        if (total == 0) {
            statusLabel.setText("No repositories found");
        } else if (filtered == total) {
            statusLabel.setText(String.format("Found %d repositories, %d selected", total, selected));
        } else {
            statusLabel.setText(String.format("Showing %d of %d repositories, %d selected", filtered, total, selected));
        }
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Open the Released hyperlink target in the system browser
     */
    @FXML
    private void openReleasedLink() {
        final String url = "https://www.trmc.osd.mil/wiki/spaces/MINERVA/pages/194219554/AMPT+Releases";
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
                logger.info("Opened Released link: {}", url);
            } else {
                logger.warn("Desktop browsing is not supported on this platform");
                showAlert("Unsupported", "Opening links is not supported on this platform.");
            }
        } catch (Exception e) {
            logger.error("Failed to open Released link", e);
            showAlert("Error", "Failed to open link: " + e.getMessage());
        }
    }

    /**
     * Paste clipboard text into the filter TextArea
     */
    @FXML
    private void handlePasteFromClipboard() {
        try {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard != null && clipboard.hasString()) {
                String text = clipboard.getString();
                if (text != null) {
                    filterField.setText(text);
                    logger.info("Pasted {} characters from clipboard into filter", text.length());
                }
            } else {
                showAlert("Clipboard", "Clipboard has no text to paste.");
            }
        } catch (Exception e) {
            logger.error("Failed to paste from clipboard", e);
            showAlert("Error", "Failed to paste from clipboard: " + e.getMessage());
        }
    }

    /**
     * Clear the filter TextArea
     */
    @FXML
    private void handleClearFilter() {
        filterField.clear();
        logger.info("Cleared filter text");
    }

    /**
     * Play the How to Copy video in a new window
     */
    @FXML
    private void playHowToCopyVideo() {
        try {
            URL videoUrl = getClass().getResource("/videos/Copy.mp4");
            if (videoUrl == null) {
                showAlert("Video Not Found", "The How to Copy video file (Copy.mp4) was not found in the resources.");
                return;
            }

            Media media = new Media(videoUrl.toString());
            MediaPlayer mediaPlayer = new MediaPlayer(media);
            MediaView mediaView = new MediaView(mediaPlayer);
            
            // Set up the media view
            mediaView.setFitWidth(800);
            mediaView.setFitHeight(600);
            mediaView.setPreserveRatio(true);

            // Create a new stage for the video
            Stage videoStage = new Stage();
            videoStage.setTitle("How to Copy - Tutorial Video");
            videoStage.setScene(new javafx.scene.Scene(new javafx.scene.layout.StackPane(mediaView), 800, 600));
            
            // Play the video when the stage is shown
            videoStage.setOnShown(e -> mediaPlayer.play());
            
            // Clean up when the stage is closed
            videoStage.setOnCloseRequest(e -> {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            });
            
            videoStage.show();
            logger.info("Playing How to Copy video");
            
        } catch (Exception e) {
            logger.error("Failed to play How to Copy video", e);
            showAlert("Error", "Failed to play video: " + e.getMessage());
        }
    }
}
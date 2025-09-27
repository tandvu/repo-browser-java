package com.tandvu.repobrowser.controller;

import com.tandvu.repobrowser.model.Repository;
import com.tandvu.repobrowser.service.RepositoryScanner;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
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
    @FXML private TableColumn<Repository, String> deploymentVersionColumn;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    
    private final ObservableList<Repository> repositories = FXCollections.observableArrayList();
    private final ObservableList<Repository> filteredRepositories = FXCollections.observableArrayList();
    private final RepositoryScanner repositoryScanner = new RepositoryScanner();
    private final Map<String, String> targetedVersions = new HashMap<>();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing MainController");
        
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
                        
                        // Listen for changes to the repository's selected property for visual updates
                        repository.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                            updateRowStyle(repository);
                        });
                        
                        // Listen for version changes to update mismatch styling
                        repository.repoVersionProperty().addListener((obs, oldVersion, newVersion) -> {
                            updateVersionMismatchStyle(repository);
                        });
                        
                        repository.targetedVersionProperty().addListener((obs, oldVersion, newVersion) -> {
                            updateVersionMismatchStyle(repository);
                        });
                        
                        repository.deploymentVersionProperty().addListener((obs, oldVersion, newVersion) -> {
                            updateVersionMismatchStyle(repository);
                        });
                    }
                }
                
                private void updateRowStyle(Repository repository) {
                    if (repository.isSelected()) {
                        if (!getStyleClass().contains("repository-selected")) {
                            getStyleClass().add("repository-selected");
                        }
                    } else {
                        getStyleClass().remove("repository-selected");
                    }
                }
                
                private void updateVersionMismatchStyle(Repository repository) {
                    boolean hasMismatch = hasVersionMismatch(repository);
                    if (hasMismatch) {
                        if (!getStyleClass().contains("version-mismatch")) {
                            getStyleClass().add("version-mismatch");
                        }
                    } else {
                        getStyleClass().remove("version-mismatch");
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
            filteredRepositories.addAll(foundRepos);
            
            // Add listeners to each repository to update header checkbox
            foundRepos.forEach(repo -> {
                repo.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    updateHeaderCheckboxState();
                    updateStatusLabel();
                });
            });
            
            logger.info("Found {} repositories", foundRepos.size());
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
                for (File war : warFiles) {
                    String name = war.getName().toLowerCase();
                    // Match ampt-<suffix>-<version>.war
                    // capture suffix and version; allow dots and hyphens in version
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("^ampt-([a-z0-9-]+)-([0-9][a-z0-9.-]*)\\.war$")
                        .matcher(name);
                    if (m.find()) {
                        String suffix = m.group(1);      // e.g., orgchart
                        String version = m.group(2);     // e.g., 3.4.0 or 3.4.0-SNAPSHOT
                        deployedVersions.put(suffix, version);
                    }
                }
            }
            
            // Clear previous deployment versions
            repositories.forEach(r -> r.setDeploymentVersion(""));
            
            if (!deployedVersions.isEmpty()) {
                for (Repository repo : repositories) {
                    String repoName = repo.getName().toLowerCase(); // e.g., opt-orgchart
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
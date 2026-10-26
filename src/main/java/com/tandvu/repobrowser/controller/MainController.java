package com.tandvu.repobrowser.controller;

import javafx.scene.layout.StackPane;

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
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * Main controller for the Repo Browser application
 */
public class MainController implements Initializable {
    @FXML
    private Button toggleIgnoredButton;

    private boolean hideIgnored = false;
    private static final String PREF_HIDE_IGNORED = "hide_ignored";

    /**
     * Toggle hiding/showing ignored repositories
     */
    @FXML
    private void handleToggleIgnored() {
    hideIgnored = !hideIgnored;
    preferences.putBoolean(PREF_HIDE_IGNORED, hideIgnored);
    updateFilteredRepositories();
    toggleIgnoredButton.setText(hideIgnored ? "Show Ignored Repos" : "Hide Ignored Repos");
    }

    /**
     * Update filteredRepositories based on filter and hideIgnored flag
     */
    private void updateFilteredRepositories() {
        // Preserve any active text filter when updating hidden/ignored state.
        String currentFilter = (filterField != null) ? filterField.getText() : null;
        if (currentFilter != null && !currentFilter.trim().isEmpty()) {
            // Re-apply the filter then enforce hideIgnored on the filtered result
            filterRepositories(currentFilter);
            if (hideIgnored) {
                filteredRepositories.removeIf(Repository::isIgnore);
            }
        } else {
            // No active text filter — show either all or non-ignored repositories
            filteredRepositories.clear();
            if (hideIgnored) {
                filteredRepositories.addAll(repositories.stream().filter(r -> !r.isIgnore()).toList());
            } else {
                filteredRepositories.addAll(repositories);
            }
        }

        repoTable.refresh();
        updateHeaderCheckboxState();
        updateStatusLabel();
    }
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    /** Build/deploy console output, mirrored to the log file (see logback.xml). */
    private static final Logger buildFileLogger = LoggerFactory.getLogger("BUILD");
    /** Matches the FILE appender in logback.xml; relative to the working directory (start.bat cd's to the app dir). */
    private static final Path LOG_FILE = Paths.get("logs", "repo-browser.log");
    
    // Preferences constants
    private static final String PREF_REPOSITORY_PATH = "repository_path";
    private static final String PREF_DEPLOYMENT_PATH = "deployment_path";
    private static final String DEFAULT_REPOSITORY_PATH = "C:\\AMPT";
    private static final String DEFAULT_DEPLOYMENT_PATH = "C:\\OPT";
    private static final String DEFAULT_SPRING_SOA_DEPLOY_PATH = "C:\\OPT\\spring-soa";
    private static final String OPT_SOA_EXEC_JAR = "opt-soa-exec.jar";
    private static final String DEFAULT_GIT_BUILD_BRANCH = "master";
    private static final String WEBMAP_GIT_BUILD_BRANCH = "feature/ampt-arcgis4";
    
    // Preferences instance
    private final Preferences preferences = Preferences.userNodeForPackage(MainController.class);
    private static final String PREF_IGNORE_MAP = "ignore_map";
    /** Not deployed via JBoss WAR in this tool — ignored unless the user saved a preference. */
    private static final Set<String> DEFAULT_IGNORED_REPOS = Set.of(
        "opt-utilities",
        "onr-transit"
    );

    /** Version is the last numeric segment: ampt-common-map-interface-4.20.0.war */
    private static final Pattern AMPT_WAR_FILE_PATTERN = Pattern.compile(
        "^ampt-(.+)-(\\d+(?:\\.\\d+)*(?:-[a-zA-Z0-9]+)?)\\.war$", Pattern.CASE_INSENSITIVE);

    /** opt-soa-5.20.1.war or opt-soa-5.20.1-exec.war (file or exploded directory under JBoss) */
    private static final Pattern OPT_SOA_DEPLOYMENT_PATTERN = Pattern.compile(
        "^opt-soa-(\\d+(?:\\.\\d+)*(?:-[a-zA-Z0-9]+)?)(?:-exec)?\\.war$", Pattern.CASE_INSENSITIVE);

    /** webmap2-1.7.0.war (repo folder name is {@code webmap}) */
    private static final Pattern WEBMAP_DEPLOYMENT_WAR_PATTERN = Pattern.compile(
        "^webmap2-(\\d+(?:\\.\\d+)*(?:-[a-zA-Z0-9]+)?)\\.war$", Pattern.CASE_INSENSITIVE);

    /** Version segment at end of a WAR file name (handles webmap2-1.7.0, ampt-zones-1.7.0, etc.) */
    private static final Pattern WAR_FILE_VERSION_PATTERN = Pattern.compile(
        "^(?:.+)-(\\d+(?:\\.\\d+)*(?:-[a-zA-Z0-9]+)?)(?:-exec)?\\.war$", Pattern.CASE_INSENSITIVE);
    
    @FXML private TextField basePathField;
    @FXML private Button browseButton;
    @FXML private TextField deploymentPathField;
    @FXML private Button browseDeploymentButton;
    @FXML private Label soaPathLabel;
    @FXML private Label soaDeployPathLabel;
    @FXML private TextArea filterField;
    @FXML private TableView<Repository> repoTable;
    @FXML private TableColumn<Repository, Boolean> selectedColumn;
    @FXML private TableColumn<Repository, Boolean> ignoredColumn;
    @FXML private TableColumn<Repository, String> nameColumn;
    @FXML private TableColumn<Repository, String> repoVersionColumn;
    @FXML private TableColumn<Repository, String> targetedVersionColumn;
        @FXML
        private TableColumn<Repository, String> deploymentVersionColumn;
        @FXML
        private TableColumn<Repository, String> deploymentModifiedColumn;
    @FXML
    private Button buildMasterButton;
    @FXML
    private Button refreshButton;
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
    /** True while a multi-repo batch build is running (suppresses per-repo "successful" header text). */
    private volatile boolean batchBuildInProgress = false;
    private final RepositoryScanner repositoryScanner = new RepositoryScanner();
    private final Map<String, String> targetedVersions = new HashMap<>();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (buildLogContainer != null) {
            buildLogContainer.setVisible(false);
        }
        if (buildLogArea != null) {
            buildLogArea.setPrefHeight(400);
        }
        if (buildLogArea != null) {
            buildLogArea.setText("Log area is working. If you see this, UI is correct.\n");
        }
        // Runtime check for FXML injection
        if (buildLogArea == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("FXML Injection Error");
            alert.setHeaderText("buildLogArea is not injected");
            alert.setContentText("The buildLogArea TextArea was not injected from FXML. Check fx:id and controller mapping.");
            alert.showAndWait();
        }
        logger.info("Initializing MainController");

        // Restore hideIgnored state from preferences
        hideIgnored = preferences.getBoolean(PREF_HIDE_IGNORED, true);
        if (toggleIgnoredButton != null) {
            toggleIgnoredButton.setText(hideIgnored ? "Show Ignored Repos" : "Hide Ignored Repos");
        }

        // Initialize build log area first to prevent JavaFX text rendering issues
        initializeBuildLogArea();

        // Load saved paths from preferences
        loadSavedPaths();

        // Setup table columns
        setupTableColumns();

        // Ensure all columns are visible and table is editable
        repoTable.setEditable(true);
        repoTable.getColumns().forEach(col -> col.setVisible(true));
        repoTable.refresh();

        // Setup filter functionality
        setupFilter();

        // Setup automatic scanning when path changes
        setupAutoScan();

        // Setup build button state management
        setupBuildButtonState();

        // Set initial status
        statusLabel.setText("Ready - Select a repository path to browse");
        hideOperationProgress();

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

    // Ignore column with checkboxes
    ignoredColumn.setCellValueFactory(new PropertyValueFactory<>("ignore"));
    ignoredColumn.setCellFactory(CheckBoxTableCell.forTableColumn(ignoredColumn));
    ignoredColumn.setEditable(true);
        
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
    deploymentModifiedColumn.setCellValueFactory(new PropertyValueFactory<>("deploymentModified"));

        // Make table editable for checkboxes
        repoTable.setEditable(true);
        repoTable.setItems(filteredRepositories);        // Setup row factory for click-to-select functionality
        setupRowFactory();
    }
    
    /**
     * Setup row factory for click-to-select functionality and visual styling
     */
    private void setupRowFactory() {
        repoTable.setRowFactory(tv -> new TableRow<Repository>() {
            @Override
            public void updateItem(Repository repo, boolean empty) {
                super.updateItem(repo, empty);
                if (repo == null || empty) {
                    setStyle("");
                    setDisable(false);
                } else {
                    setStyle(shouldHighlightRepositoryRow(repo) ? "-fx-background-color: #ffe0e0;" : "");
                    // Listen for changes to ignore property
                    repo.ignoreProperty().addListener((obs, wasIgnored, isIgnored) -> {
                        if (isIgnored) {
                            repo.setSelected(false);
                        }
                        if (hideIgnored) {
                            updateFilteredRepositories();
                        } else {
                            repoTable.refresh();
                        }
                    });
                }
            }
        });

        // Custom cell factory for Selected column to hide checkbox if Ignored is checked
        selectedColumn.setCellFactory(col -> new CheckBoxTableCell<Repository, Boolean>() {
            @Override
            public void updateItem(Boolean selected, boolean empty) {
                super.updateItem(selected, empty);
                TableRow<Repository> row = getTableRow();
                Repository repo = row == null ? null : row.getItem();
                if (repo != null && repo.isIgnore()) {
                    setGraphic(null); // Hide checkbox
                } else {
                    setGraphic(getGraphic()); // Show checkbox
                }
                setDisable(repo != null && repo.isIgnore());
            }
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
            // Apply to all visible (filtered) repositories except ignored
            filteredRepositories.forEach(repo -> {
                if (!repo.isIgnore()) {
                    repo.setSelected(isSelected);
                }
            });
            repoTable.refresh();
            if (isSelected) {
                logger.info("Selected all visible repositories via header checkbox (excluding ignored)");
            } else {
                logger.info("Cleared all repository selections via header checkbox");
            }
            updateStatusLabel();
            updateBuildButtonState();
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
            // Apply the text filter only; updateFilteredRepositories will re-apply hideIgnored when needed
            filterRepositories(newValue);
            // Ensure header/status reflect the new filtered set
            updateHeaderCheckboxState();
            updateStatusLabel();
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
                updateBuildButtonState();
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
    
    /**
     * Setup build button state management based on repository selection
     */
    private void setupBuildButtonState() {
        // Initially disable the button since no repositories are selected
        if (buildMasterButton != null) {
            buildMasterButton.setDisable(true);
        }
        
        // Add listener to repository list to update button state when selection changes
        repositories.addListener((ListChangeListener<Repository>) change -> {
            updateBuildButtonState();
        });
        
        // Also listen to filtered list changes
        filteredRepositories.addListener((ListChangeListener<Repository>) change -> {
            updateBuildButtonState();
        });
    }
    
    /**
     * Update build button state based on repository selection
     */
    private void updateBuildButtonState() {
        if (buildMasterButton != null) {
            boolean hasSelectedRepos = repositories.stream().anyMatch(Repository::isSelected);
            buildMasterButton.setDisable(!hasSelectedRepos);
        }
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
        repoTable.refresh();
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

        showOperationProgress("Scanning repositories...");

        // Clear existing repositories
    repositories.clear();
    filteredRepositories.clear();

        try {
            List<Repository> foundRepos = repositoryScanner.scanForRepositories(basePath);
            // Load ignore map from preferences
            String ignoreMapStr = preferences.get(PREF_IGNORE_MAP, "");
            Map<String, Boolean> ignoreMap = new HashMap<>();
            if (!ignoreMapStr.isEmpty()) {
                for (String entry : ignoreMapStr.split(";")) {
                    String[] kv = entry.split(":");
                    if (kv.length == 2) {
                        ignoreMap.put(kv[0], Boolean.parseBoolean(kv[1]));
                    }
                }
            }
            repositories.addAll(foundRepos);

            // Ensure opt-soa row reflects SOA Path/opt-soa (same folder used for builds)
            syncOptSoaRepository();

            // Restore ignore state (library/quarkus repos stay ignored by default)
            for (Repository repo : repositories) {
                String repoKey = repo.getName().toLowerCase();
                if (DEFAULT_IGNORED_REPOS.contains(repoKey)) {
                    repo.setIgnore(true);
                } else if (ignoreMap.containsKey(repo.getName())) {
                    repo.setIgnore(ignoreMap.get(repo.getName()));
                }
            }

            // Sort all repositories (including opt-soa) alphabetically by name
            repositories.sort((r1, r2) -> r1.getName().compareToIgnoreCase(r2.getName()));

            updateFilteredRepositories();

            // Add listeners to each repository to update header checkbox and persist ignore state
            repositories.forEach(repo -> {
                repo.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    updateHeaderCheckboxState();
                    updateStatusLabel();
                    updateBuildButtonState();
                    Platform.runLater(() -> repoTable.refresh());
                });
                repo.ignoreProperty().addListener((observable, oldValue, newValue) -> {
                    saveIgnoreMap();
                    if (hideIgnored) {
                        updateFilteredRepositories();
                    }
                    Platform.runLater(() -> repoTable.refresh());
                });
            });

            saveIgnoreMap();

            logger.info("Found {} repositories", repositories.size());
            updateStatusLabel();
            updateHeaderCheckboxState();
            updateBuildButtonState();

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
            hideOperationProgress();
            updateStatusLabel();
        }
    }

    private void showOperationProgress(String statusMessage) {
        Runnable updateUi = () -> {
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            progressBar.setVisible(true);
            if (statusMessage != null && !statusMessage.isBlank()) {
                statusLabel.setText(statusMessage);
            }
        };
        if (Platform.isFxApplicationThread()) {
            updateUi.run();
        } else {
            Platform.runLater(updateUi);
        }
    }

    private void setBuildPhaseStatus(String message) {
        Runnable update = () -> buildStatusLabel.setText(message);
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    /** Bold build header, bottom status, and progress bar while batch build runs. */
    private void setActiveBatchRepository(String repoName, String phase) {
        String message = phase + " " + repoName + "...";
        Runnable update = () -> {
            buildStatusLabel.setText(message);
            statusLabel.setText(message);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            progressBar.setVisible(true);
        };
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private void runOnFxThreadAndWait(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void setActiveBatchRepositorySync(String repoName, String phase) {
        runOnFxThreadAndWait(() -> setActiveBatchRepository(repoName, phase));
    }

    private void hideOperationProgress() {
        Runnable updateUi = () -> {
            progressBar.setProgress(0);
            progressBar.setVisible(false);
        };
        if (Platform.isFxApplicationThread()) {
            updateUi.run();
        } else {
            Platform.runLater(updateUi);
        }
    }

    private void saveIgnoreMap() {
        StringBuilder sb = new StringBuilder();
        for (Repository repo : repositories) {
            sb.append(repo.getName()).append(":").append(repo.isIgnore()).append(";");
        }
        preferences.put(PREF_IGNORE_MAP, sb.toString());
    }

    /**
     * Maven build directory for opt-soa: {@code <SOA Path>/opt-soa}.
     */
    /**
     * WAR filename prefix in JBoss (repo folder {@code webmap} publishes {@code webmap2-<version>.war}).
     */
    private String getWarArtifactPrefix(String repoName) {
        if (repoName != null && "webmap".equalsIgnoreCase(repoName)) {
            return "webmap2";
        }
        return repoName;
    }

    /**
     * npm script that produces the deployable WAR. Prefers {@code build:war} (e.g. webmap, whose plain
     * {@code build} only emits dist/), otherwise {@code build}.
     */
    private String resolveNpmBuildScript(Path repoPath) {
        Path packageJson = repoPath.resolve("package.json");
        if (!Files.isRegularFile(packageJson)) {
            return "build";
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(packageJson));
            com.fasterxml.jackson.databind.JsonNode scripts = root.get("scripts");
            if (scripts != null && scripts.has("build:war")) {
                return "build:war";
            }
        } catch (IOException e) {
            logger.warn("Could not read {} to pick npm build script: {}", packageJson, e.getMessage());
        }
        return "build";
    }

    /**
     * Git branch used before npm/Maven build. Most repos use master; webmap uses a release feature branch.
     */
    private String resolveGitBuildBranch(Repository repository) {
        if (repository != null && "webmap".equalsIgnoreCase(repository.getName())) {
            return WEBMAP_GIT_BUILD_BRANCH;
        }
        return DEFAULT_GIT_BUILD_BRANCH;
    }

    /**
     * Checkout the build branch and pull latest. Required for opt-soa so master matches release targets.
     */
    private boolean runGitCheckoutAndPull(Path repoPath, Repository repository) throws IOException, InterruptedException {
        appendToBuildLog("=== Checking Git Repository ===\n");
        if (!Files.exists(repoPath.resolve(".git"))) {
            appendToBuildLog("ERROR: Not a git repository: " + repoPath + "\n");
            return false;
        }
        String buildBranch = resolveGitBuildBranch(repository);
        appendToBuildLog("=== Checking out branch: " + buildBranch + " ===\n");
        ProcessBuilder gitCheckout = new ProcessBuilder("git", "checkout", buildBranch);
        gitCheckout.directory(repoPath.toFile());
        gitCheckout.redirectErrorStream(true);
        Process gitProcess = gitCheckout.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(gitProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendToBuildLog(line + "\n");
            }
        }
        int gitExit = gitProcess.waitFor();
        if (gitExit != 0) {
            appendToBuildLog("ERROR: Git checkout failed: " + gitExit + "\n");
            return false;
        }

        appendToBuildLog("=== Pulling latest changes ===\n");
        ProcessBuilder gitPull = new ProcessBuilder("git", "pull");
        gitPull.directory(repoPath.toFile());
        gitPull.redirectErrorStream(true);
        Process pull = gitPull.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pull.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendToBuildLog(line + "\n");
            }
        }
        int pullExit = pull.waitFor();
        if (pullExit != 0) {
            appendToBuildLog("WARNING: Git pull failed with exit code: " + pullExit + "\n");
            appendToBuildLog("Continuing with build...\n");
        }
        refreshRepositoryVersionFromDisk(repository, repoPath);
        return true;
    }

    /**
     * Re-read version from package.json/pom on disk so build/deploy logic matches master after git pull.
     */
    private void refreshRepositoryVersionFromDisk(Repository repository, Path repoPath) {
        String detected;
        if (repository.getName().equalsIgnoreCase("opt-soa")) {
            detected = detectOptSoaRepoVersion(repoPath);
        } else {
            detected = repositoryScanner.detectRepositoryVersion(repoPath);
        }
        if (detected == null || detected.isBlank()) {
            appendToBuildLog("WARNING: Could not detect repository version from disk\n");
            return;
        }
        String previous = repository.getRepoVersion();
        final String newVersion = detected;
        runOnFxThreadAndWait(() -> {
            repository.setRepoVersion(newVersion);
            repoTable.refresh();
        });
        if (!newVersion.equals(previous)) {
            appendToBuildLog("Updated repo version after git pull: " + previous + " -> " + newVersion + "\n");
        } else {
            appendToBuildLog("Repo version on disk: " + newVersion + "\n");
        }
    }

    private Path getOptSoaProjectPath() {
        String soaPath = soaPathLabel.getText();
        if (soaPath == null || soaPath.isBlank()) {
            return null;
        }
        String normalizedPath = soaPath.trim()
            .replace('/', File.separatorChar)
            .replace('\\', File.separatorChar);
        return Paths.get(normalizedPath, "opt-soa");
    }

    /**
     * Keep a single opt-soa row aligned with the project folder used for build/deploy.
     * Repo version comes from that tree; deployment version still comes from Deployment Path.
     */
    private void syncOptSoaRepository() {
        Path optSoaProjectPath = getOptSoaProjectPath();
        if (optSoaProjectPath == null || !Files.isDirectory(optSoaProjectPath)) {
            logger.warn("opt-soa project path does not exist: {}", optSoaProjectPath);
            return;
        }

        Repository optSoa = repositories.stream()
            .filter(repo -> "opt-soa".equalsIgnoreCase(repo.getName()))
            .findFirst()
            .orElse(null);

        if (optSoa == null) {
            optSoa = new Repository("opt-soa", optSoaProjectPath.toString());
            repositories.add(optSoa);
            logger.info("Added opt-soa from project path: {}", optSoaProjectPath);
        } else {
            String previousPath = optSoa.getPath();
            optSoa.setPath(optSoaProjectPath.toString());
            if (!optSoaProjectPath.toString().equals(previousPath)) {
                logger.info("Updated opt-soa path from '{}' to '{}'", previousPath, optSoaProjectPath);
            }
        }

        String version = detectOptSoaRepoVersion(optSoaProjectPath);
        optSoa.setRepoVersion(version);
        logger.info("opt-soa repo version from {}: '{}'", optSoaProjectPath, version);
    }

    /**
     * Detect source version for opt-soa from the same folders the build uses.
     */
    private String detectOptSoaRepoVersion(Path optSoaProjectPath) {
        // WAR is produced under SOA/target — prefer that module's version for deploy comparison
        Path soaModulePath = optSoaProjectPath.resolve("SOA");
        if (Files.isDirectory(soaModulePath)) {
            String version = repositoryScanner.detectRepositoryVersion(soaModulePath);
            if (version != null && !version.isBlank()) {
                return version;
            }
        }

        String version = repositoryScanner.detectRepositoryVersion(optSoaProjectPath);
        if (version != null && !version.isBlank()) {
            return version;
        }

        return readVersionFromMetadataFiles(optSoaProjectPath);
    }

    private String readVersionFromMetadataFiles(Path projectPath) {
        try {
            String[] versionFiles = {"version.properties", "version.txt", "VERSION", ".version"};
            for (String fileName : versionFiles) {
                Path versionFile = projectPath.resolve(fileName);
                if (Files.exists(versionFile)) {
                    String content = Files.readString(versionFile);
                    Matcher matcher = Pattern
                        .compile("(?i)(?:version[=:\\s]+)([0-9]+(?:\\.[0-9]+)*(?:-[a-z0-9]+)?)")
                        .matcher(content);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error reading version metadata under {}: {}", projectPath, e.getMessage());
        }
        return "";
    }
    
    @FXML
    private void handleRefresh() {
        // Get the current repository path
        String currentPath = basePathField.getText();
        if (currentPath != null && !currentPath.trim().isEmpty()) {
            File dir = new File(currentPath.trim());
            if (dir.exists() && dir.isDirectory()) {
                // Trigger a rescan of repositories
                scanRepositories(Path.of(currentPath.trim()));
                logger.info("Repositories refreshed from path: {}", currentPath);
            } else {
                showAlert("Invalid Path", "Repository path does not exist: " + currentPath);
            }
        } else {
            showAlert("No Path", "Please specify a repository path to refresh.");
        }
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

        // Use batch build for one or more selected repositories to support sequential builds
        startBatchBuild(selectedRepos);
    }

    /**
     * Open logs/repo-browser.log in the system default viewer.
     */
    @FXML
    private void handleOpenLogFile() {
        Path logFile = LOG_FILE.toAbsolutePath();
        if (!Files.isRegularFile(logFile)) {
            showAlert("Log file not found", "No log file at:\n" + logFile);
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(logFile.toFile());
            } else {
                showAlert("Unsupported", "Opening files is not supported on this platform.\n" + logFile);
            }
        } catch (Exception e) {
            logger.error("Failed to open log file {}", logFile, e);
            showAlert("Error", "Failed to open log file:\n" + logFile + "\n\n" + e.getMessage());
        }
    }

    @FXML
    private void handleBackToTable() {
        // Show table, hide build log
        repoTable.setVisible(true);
        buildLogContainer.setVisible(false);
        buildMasterButton.setDisable(false);
    }

    private void startBuildProcess(Repository repository) {
        if (buildLogArea != null) {
            buildLogArea.appendText("[TEST] buildLogArea injection works.\n");
        }
        // Ensure build log container is visible and brought to front
        if (buildLogContainer != null && buildLogContainer.getParent() instanceof StackPane) {
            StackPane stack = (StackPane) buildLogContainer.getParent();
            buildLogContainer.setVisible(true);
            buildLogContainer.toFront();
        }
        // Hide table, show build log
        if (buildLogArea != null) {
            buildLogArea.appendText("[DIAG] startBuildProcess called for " + repository.getName() + "\n");
            buildLogArea.appendText("Build started for " + repository.getName() + "\n");
            buildLogArea.appendText("Building " + repository.getName() + "...\n");
        }
        repoTable.setVisible(false);
        if (buildLogContainer != null) {
            buildLogContainer.setVisible(true);
        }
        buildMasterButton.setDisable(true);
        buildStatusLabel.setText("Building " + repository.getName() + "...");
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

        Path repoPath;
        if (repository.getName().equalsIgnoreCase("opt-soa")) {
            String soaPath = soaPathLabel.getText().trim();
            repoPath = Paths.get(soaPath, "opt-soa");
        } else {
            repoPath = Paths.get(basePath, repository.getName());
        }
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

                    {
                        // Step 1: Check if it's a git repository (including opt-soa under SOA Path)
                        appendToBuildLog("=== Checking Git Repository ===\n");
                        if (!Files.exists(finalRepoPath.resolve(".git"))) {
                            appendToBuildLog("ERROR: Not a git repository: " + finalRepoPath + "\n");
                            Platform.runLater(() -> {
                                buildStatusLabel.setText("Build Failed");
                                buildMasterButton.setDisable(false);
                            });
                            return null;
                        }

                        String buildBranch = resolveGitBuildBranch(finalRepository);
                        appendToBuildLog("=== Checking out branch: " + buildBranch + " ===\n");
                        ProcessBuilder gitCheckout = new ProcessBuilder("git", "checkout", buildBranch);
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
                        refreshRepositoryVersionFromDisk(finalRepository, finalRepoPath);
                    }

                    // Step 4: Build logic for opt-soa or others
                    if (finalRepository.getName().equalsIgnoreCase("opt-soa")) {
                        appendToBuildLog("[DIAG] About to start Maven process for opt-soa...\n");
                        appendToBuildLog("[DIAG] opt-soa build directory: " + finalRepoPath.toString() + "\n");
                        if (!new File(finalRepoPath.toFile(), "pom.xml").exists()) {
                            appendToBuildLog("[DIAG] pom.xml not found in opt-soa directory!\n");
                        } else {
                            appendToBuildLog("[DIAG] pom.xml found in opt-soa directory.\n");
                        }
                        appendToBuildLog("=== Running mvn clean install for opt-soa ===\n");
                        ProcessBuilder mvnBuild;
                        String os = System.getProperty("os.name").toLowerCase();
                        if (os.contains("win")) {
                            mvnBuild = new ProcessBuilder("cmd", "/c", "mvn", "clean", "install");
                        } else {
                            mvnBuild = new ProcessBuilder("mvn", "clean", "install");
                        }
                        mvnBuild.directory(finalRepoPath.toFile());
                        mvnBuild.redirectErrorStream(true);

                        String commandStr = String.join(" ", mvnBuild.command());
                        appendToBuildLog("Executing: " + commandStr + "\n");
                        appendToBuildLog("Working directory: " + repoPath.toString() + "\n\n");

                        Process mvnProcess;
                        try {
                            mvnProcess = mvnBuild.start();
                            appendToBuildLog("[DIAG] Maven process started.\n");
                            appendToBuildLog("[DIAG] Exception occurred while starting Maven process.\n");
                        } catch (IOException e) {
                            Platform.runLater(() -> {
                                appendToBuildLog("[DIAG] ERROR: Failed to start Maven process: " + e.getMessage() + "\n");
                                appendToBuildLog("This might indicate that Maven is not installed or not in PATH.\n");
                                appendToBuildLog("Please ensure Maven is properly installed.\n");
                                buildStatusLabel.setText("Build Failed - Maven not found");
                                buildMasterButton.setDisable(false);
                            });
                            return null;
                        }

                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(mvnProcess.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                final String logLine = line != null ? line : "";
                                if (Platform.isFxApplicationThread()) {
                                    appendToBuildLog(logLine + "\n");
                                } else {
                                    Platform.runLater(() -> appendToBuildLog(logLine + "\n"));
                                }
                            }
                        }

                        int mvnExitCode = mvnProcess.waitFor();
                        Platform.runLater(() -> {
                            if (mvnExitCode == 0) {
                                appendToBuildLog("\n=== Build Completed Successfully ===\n");
                                buildStatusLabel.setText("Build Successful - Deploying...");
                                // Start deployment in background
                                Task<Void> deployTask = createDeploymentTask(finalRepository, finalRepoPath);
                                Thread deployThread = new Thread(deployTask);
                                deployThread.setDaemon(true);
                                deployThread.start();
                            } else {
                                appendToBuildLog("\nERROR: mvn clean install failed with exit code: " + mvnExitCode + "\n");
                                buildStatusLabel.setText("Build Failed");
                                buildMasterButton.setDisable(false);
                            }
                        });
                    } else {
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

                        // Step 5: Run npm build script (build:war when the repo defines it)
                        String npmScript = resolveNpmBuildScript(finalRepoPath);
                        appendToBuildLog("=== Running npm run " + npmScript + " ===\n");

                        // Create npm command for Windows compatibility
                        ProcessBuilder npmBuild;
                        String os = System.getProperty("os.name").toLowerCase();
                        if (os.contains("win")) {
                            // On Windows, use cmd to run npm
                            npmBuild = new ProcessBuilder("cmd", "/c", "npm", "run", npmScript);
                        } else {
                            // On Unix-like systems
                            npmBuild = new ProcessBuilder("npm", "run", npmScript);
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
                    }

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
     * Start a sequential batch build for multiple repositories.
     */
    private void startBatchBuild(List<Repository> repos) {
        // Hide table and show build log once for the batch
        if (buildLogContainer != null) buildLogContainer.setVisible(true);
        repoTable.setVisible(false);
        buildMasterButton.setDisable(true);
        refreshButton.setDisable(true);
        batchBuildInProgress = true;
        if (!repos.isEmpty()) {
            setActiveBatchRepository(repos.get(0).getName(), "Building");
        }

        Task<Void> batchTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    for (Repository r : repos) {
                        runOnFxThreadAndWait(() ->
                            appendToBuildLog("\n=== Batch: Building " + r.getName() + " ===\n"));
                        setActiveBatchRepositorySync(r.getName(), "Building");
                        boolean success = buildRepository(r);
                        Platform.runLater(() -> appendToBuildLog("=== Batch: " + r.getName() + " " + (success ? "SUCCEEDED" : "FAILED") + " ===\n"));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                } finally {
                    batchBuildInProgress = false;
                    Platform.runLater(() -> {
                        buildStatusLabel.setText("Batch build completed");
                        buildMasterButton.setDisable(false);
                        refreshButton.setDisable(false);
                        repoTable.setVisible(true);
                        buildLogContainer.setVisible(false);
                        syncOptSoaRepository();
                        refreshDeploymentVersionsFromConfiguredPath();
                        hideOperationProgress();
                        updateStatusLabel();
                        repoTable.refresh();
                    });
                }
                return null;
            }
        };

        Thread t = new Thread(batchTask);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Build a single repository synchronously on the calling thread and return success.
     * This method reuses the existing startBuildProcess logic but extracts the core
     * steps so they can be called sequentially for batch builds.
     */
    private boolean buildRepository(Repository repository) {
        try {
            // replicate the setup used in startBuildProcess to determine repoPath
            String basePath = basePathField.getText().trim();
            Path repoPath;
            if (repository.getName().equalsIgnoreCase("opt-soa")) {
                String soaPath = soaPathLabel.getText().trim();
                repoPath = Paths.get(soaPath, "opt-soa");
            } else {
                repoPath = Paths.get(basePath, repository.getName());
            }
            if (!Files.exists(repoPath)) {
                Platform.runLater(() -> appendToBuildLog("ERROR: Repository path does not exist: " + repoPath + "\n"));
                return false;
            }

            // Step 1: Git checkout + pull (opt-soa uses SOA Path; release builds expect master)
            if (!runGitCheckoutAndPull(repoPath, repository)) {
                return false;
            }

            // Step 2: Build (mvn for opt-soa, npm for others)
            if (repository.getName().equalsIgnoreCase("opt-soa")) {
                appendToBuildLog("=== Running mvn clean install for opt-soa ===\n");
                ProcessBuilder mvnBuild = System.getProperty("os.name").toLowerCase().contains("win")
                        ? new ProcessBuilder("cmd", "/c", "mvn", "clean", "install")
                        : new ProcessBuilder("mvn", "clean", "install");
                mvnBuild.directory(repoPath.toFile());
                mvnBuild.redirectErrorStream(true);
                Process mvn = mvnBuild.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(mvn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) appendToBuildLog(line + "\n");
                }
                int code = mvn.waitFor();
                if (code != 0) { appendToBuildLog("ERROR: mvn failed with code " + code + "\n"); return false; }
            } else {
                // npm build (build:war when the repo defines it, e.g. webmap)
                String npmScript = resolveNpmBuildScript(repoPath);
                appendToBuildLog("=== Running npm run " + npmScript + " ===\n");
                ProcessBuilder npmBuild = System.getProperty("os.name").toLowerCase().contains("win")
                        ? new ProcessBuilder("cmd", "/c", "npm", "run", npmScript)
                        : new ProcessBuilder("npm", "run", npmScript);
                npmBuild.directory(repoPath.toFile());
                npmBuild.redirectErrorStream(true);
                Process npm = npmBuild.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(npm.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) appendToBuildLog(line + "\n");
                }
                int code = npm.waitFor();
                if (code != 0) { appendToBuildLog("ERROR: npm build failed with code " + code + "\n"); return false; }
            }

            if (repository.getName().equalsIgnoreCase("opt-soa")) {
                if (!deployOptSoaSpringJar(repoPath)) {
                    return false;
                }
                runOnFxThreadAndWait(() -> {
                    Path springJar = getSpringSoaDeployDirectory().resolve(OPT_SOA_EXEC_JAR);
                    syncRepositoryDeploymentFromArtifact(repository, springJar);
                    syncOptSoaRepository();
                    refreshDeploymentVersionsFromConfiguredPath();
                    repoTable.refresh();
                });
                return true;
            }

            Task<Void> deployTask = createDeploymentTask(repository, repoPath);
            deployTask.run();
            return true;
        } catch (Exception e) {
            appendToBuildLog("ERROR: Batch build exception: " + e.getMessage() + "\n");
            logger.error("Batch build failed", e);
            return false;
        }
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
                    if (batchBuildInProgress) {
                        setActiveBatchRepositorySync(repository.getName(), "Deploying");
                    }
                    
                    // Get deployment path
                    String deploymentPath = deploymentPathField.getText();
                    if (deploymentPath == null || deploymentPath.trim().isEmpty()) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: No deployment path specified\n");
                            if (!batchBuildInProgress) {
                                buildStatusLabel.setText("Deployment Failed - No path");
                                buildMasterButton.setDisable(false);
                            }
                        });
                        return null;
                    }
                    
                    if (repository.getName().equalsIgnoreCase("opt-soa")) {
                        boolean deployed = deployOptSoaSpringJar(repoPath);
                        Platform.runLater(() -> {
                            if (batchBuildInProgress) {
                                setActiveBatchRepository(repository.getName(), "Deploying");
                            } else {
                                buildStatusLabel.setText(deployed ? "Build & Deployment Successful" : "Deployment Failed");
                                buildMasterButton.setDisable(false);
                            }
                            if (deployed) {
                                Path springJar = getSpringSoaDeployDirectory().resolve(OPT_SOA_EXEC_JAR);
                                syncRepositoryDeploymentFromArtifact(repository, springJar);
                                syncOptSoaRepository();
                                refreshDeploymentVersionsFromConfiguredPath();
                                repoTable.refresh();
                            }
                        });
                        return null;
                    }

                    Path deploymentDir = Path.of(deploymentPath.trim());
                    if (!Files.exists(deploymentDir) || !Files.isDirectory(deploymentDir)) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: Deployment directory does not exist: " + deploymentPath + "\n");
                            if (!batchBuildInProgress) {
                                buildStatusLabel.setText("Deployment Failed - Invalid path");
                                buildMasterButton.setDisable(false);
                            }
                        });
                        return null;
                    }
                    
                    Path targetDir = repoPath.resolve("target");
                    if (!Files.exists(targetDir)) {
                        targetDir = repoPath.resolve("dist");
                    }
                    if (!Files.exists(targetDir)) {
                        targetDir = repoPath.resolve("build");
                    }

                    final Path finalTargetDir = targetDir;

                    if (!Files.exists(finalTargetDir)) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: No target/dist/build directory found in repository\n");
                            if (!batchBuildInProgress) {
                                buildStatusLabel.setText("Deployment Failed - No build output");
                                buildMasterButton.setDisable(false);
                            }
                        });
                        return null;
                    }
                    
                    Platform.runLater(() -> appendToBuildLog("Looking for WAR files in: " + finalTargetDir + "\n"));
                    
                    // Find WAR file(s) for this repository (prefer version matching repo package.json/pom)
                    List<Path> warFiles = findWarFilesToDeploy(repoPath, repository);
                    
                    if (warFiles.isEmpty()) {
                        Platform.runLater(() -> {
                            appendToBuildLog("ERROR: No WAR files found in build output directory\n");
                            if (!batchBuildInProgress) {
                                buildStatusLabel.setText("Deployment Failed - No WAR files");
                                buildMasterButton.setDisable(false);
                            }
                        });
                        return null;
                    }
                    
                    String repoName = repository.getName();
                    // Remove old WARs and JBoss marker files once before copying the new artifact
                    deleteExistingWarFiles(deploymentDir, repoName, warFiles.stream()
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.toSet()));

                    // Deploy each WAR file (normally exactly one)
                    for (Path warFile : warFiles) {
                        String warFileName = warFile.getFileName().toString();
                        
                        // Extract version from new WAR file name
                        String newVersion = extractVersionFromWarFile(warFileName);
                        String displayVersion = newVersion.isEmpty() ? "unknown" : newVersion;
                        
                        Platform.runLater(() -> appendToBuildLog("Preparing to deploy: " + warFileName + " (version: " + displayVersion + ")\n"));
                        
                        // Deploy the new WAR file
                        Path deploymentTarget = deploymentDir.resolve(warFileName);
                        Platform.runLater(() -> appendToBuildLog("Deploying new WAR: " + warFileName + "\n"));
                        
                        Files.copy(warFile, deploymentTarget, StandardCopyOption.REPLACE_EXISTING);
                        
                        Platform.runLater(() -> appendToBuildLog("Successfully deployed: " + warFileName + " (version: " + displayVersion + ") -> " + deploymentTarget + "\n"));
                        syncRepositoryDeploymentFromArtifact(repository, deploymentTarget);
                    }
                    
                    Platform.runLater(() -> {
                        appendToBuildLog("\n=== Deployment Completed Successfully ===\n");
                        appendToBuildLog("Deployed " + warFiles.size() + " WAR file(s) to: " + deploymentPath + "\n");
                        if (batchBuildInProgress) {
                            setActiveBatchRepository(repository.getName(), "Deploying");
                        } else {
                            buildStatusLabel.setText("Build & Deployment Successful");
                            buildMasterButton.setDisable(false);
                        }
                        refreshDeploymentVersionsFromConfiguredPath();
                        repoTable.refresh();
                    });
                    
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        appendToBuildLog("ERROR: Deployment failed - " + e.getMessage() + "\n");
                        if (!batchBuildInProgress) {
                            buildStatusLabel.setText("Deployment Failed");
                            buildMasterButton.setDisable(false);
                        }
                    });
                    logger.error("Deployment failed", e);
                }
                
                return null;
            }
        };
    }
    
    /**
     * Delete existing WAR files and JBoss deployment markers for the repository.
     */
    private void deleteExistingWarFiles(Path deploymentDir, String repoName, Set<String> newWarFileNames) {
        try {
            List<String> patterns = createWarFilePatterns(repoName);
            Set<String> skipNames = newWarFileNames != null ? newWarFileNames : Set.of();

            Platform.runLater(() -> appendToBuildLog("Checking for existing deployment artifacts for repository: " + repoName + "\n"));

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(deploymentDir)) {
                for (Path existing : stream) {
                    String existingName = existing.getFileName().toString();
                    String warName = normalizeToWarFileName(existingName);

                    if (skipNames.contains(existingName) || skipNames.contains(warName)) {
                        continue;
                    }

                    if (!isWarFileForRepository(warName, patterns)) {
                        continue;
                    }

                    String oldVersion = extractVersionFromWarFile(warName);
                    String displayOldVersion = oldVersion.isEmpty() ? "unknown" : oldVersion;

                    Platform.runLater(() -> appendToBuildLog("Removing old deployment artifact: " + existingName
                        + " (version: " + displayOldVersion + ")\n"));
                    Files.deleteIfExists(existing);
                }
            }
        } catch (Exception e) {
            Platform.runLater(() -> appendToBuildLog("Warning: Failed to clean up existing WAR files: " + e.getMessage() + "\n"));
            logger.warn("Failed to clean up existing WAR files for repository: " + repoName, e);
        }
    }

    /**
     * WAR files to deploy from build output for the given repository.
     * Searches target/dist/build and repo root. Prefers a WAR whose version matches repo version.
     */
    private List<Path> findWarFilesToDeploy(Path repoPath, Repository repository) throws IOException {
        String repoName = repository.getName();
        List<String> patterns = createWarFilePatterns(repoName);
        List<Path> matching = new ArrayList<>();
        java.util.LinkedHashSet<Path> seen = new java.util.LinkedHashSet<>();

        List<Path> searchDirs = new ArrayList<>();
        for (String subDir : List.of("target", "dist", "build")) {
            Path dir = repoPath.resolve(subDir);
            if (Files.isDirectory(dir)) {
                searchDirs.add(dir);
            }
        }
        searchDirs.add(repoPath);

        for (Path dir : searchDirs) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.war")) {
                for (Path warFile : stream) {
                    String name = warFile.getFileName().toString();
                    if (isWarFileForRepository(name, patterns) && seen.add(warFile.toAbsolutePath())) {
                        matching.add(warFile);
                    }
                }
            }
        }

        if (matching.isEmpty()) {
            for (Path dir : searchDirs) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.war")) {
                    for (Path warFile : stream) {
                        if (seen.add(warFile.toAbsolutePath())) {
                            matching.add(warFile);
                        }
                    }
                }
            }
            if (matching.size() > 1) {
                Platform.runLater(() -> appendToBuildLog(
                    "WARNING: Multiple WAR files in build output; could not match repository name — using best match.\n"));
            }
        }

        if (matching.isEmpty()) {
            return matching;
        }
        if (matching.size() == 1) {
            return matching;
        }

        String repoVersion = repository.getRepoVersion();
        if (repoVersion != null && !repoVersion.isBlank()) {
            for (Path candidate : matching) {
                String warVersion = extractVersionFromWarFile(candidate.getFileName().toString());
                if (repoVersion.equals(warVersion)) {
                    Platform.runLater(() -> appendToBuildLog(
                        "Selected WAR matching repo version " + repoVersion + ": " + candidate.getFileName() + "\n"));
                    return List.of(candidate);
                }
            }
        }

        matching.sort((a, b) -> Long.compare(
            b.toFile().lastModified(),
            a.toFile().lastModified()));
        Path newest = matching.get(0);
        Platform.runLater(() -> appendToBuildLog(
            "Multiple matching WAR files in build output; deploying newest: " + newest.getFileName() + "\n"));
        return List.of(newest);
    }

    private void syncRepositoryDeploymentFromArtifact(Repository repository, Path deployedArtifact) {
        if (repository == null || deployedArtifact == null || !Files.exists(deployedArtifact)) {
            return;
        }
        try {
            String version = extractVersionFromArtifact(deployedArtifact);
            if (version.isBlank()) {
                return;
            }
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("M/d/yyyy h:mm a");
            String modified = sdf.format(new java.util.Date(Files.getLastModifiedTime(deployedArtifact).toMillis()));
            final String deploymentVersion = version;
            Platform.runLater(() -> {
                repository.setRepoVersion(deploymentVersion);
                repository.setDeploymentVersion(deploymentVersion);
                repository.setDeploymentModified(modified);
                repoTable.refresh();
            });
        } catch (IOException e) {
            logger.warn("Could not sync deployment version from {}", deployedArtifact, e);
        }
    }

    private String extractVersionFromArtifact(Path artifact) {
        String name = artifact.getFileName().toString();
        if (name.toLowerCase().endsWith(".jar")) {
            return readImplementationVersionFromJar(artifact);
        }
        return extractVersionFromWarFile(name);
    }

    /**
     * Create filename prefix patterns to match WAR files for a repository.
     */
    private List<String> createWarFilePatterns(String repoName) {
        List<String> patterns = new ArrayList<>();

        if (repoName.startsWith("opt-")) {
            String amptName = repoName.substring(4);
            patterns.add("ampt-" + amptName + "-");
        } else {
            // e.g. common-map-interface -> ampt-common-map-interface-<version>.war
            patterns.add("ampt-" + repoName + "-");
        }

        if ("opt-soa".equals(repoName)) {
            patterns.add("opt-soa-");
        }

        patterns.add(getWarArtifactPrefix(repoName) + "-");

        return patterns;
    }

    /**
     * Map JBoss marker files (e.g. foo.war.failed) back to the WAR file name (foo.war).
     */
    private String normalizeToWarFileName(String fileName) {
        int warMarker = fileName.toLowerCase().indexOf(".war.");
        if (warMarker >= 0) {
            return fileName.substring(0, warMarker + 4);
        }
        return fileName;
    }

    /**
     * Check if a WAR file belongs to the given repository
     */
    private boolean isWarFileForRepository(String warFileName, List<String> patterns) {
        if (warFileName == null || !warFileName.toLowerCase().endsWith(".war")) {
            return false;
        }
        for (String pattern : patterns) {
            if (warFileName.startsWith(pattern)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extract version from WAR file name
     */
    private String extractVersionFromWarFile(String warFileName) {
        if (warFileName == null || warFileName.isBlank()) {
            return "";
        }
        Matcher matcher = WAR_FILE_VERSION_PATTERN.matcher(warFileName.trim());
        if (matcher.matches()) {
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
        // Mirror the on-screen build log into the log file so it survives after the window is closed
        buildFileLogger.info(safeText.stripTrailing());
        
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
        if (repository.isIgnore()) {
            return false;
        }
        String deploymentVersion = repository.getDeploymentVersion();
        String repoVersion = repository.getRepoVersion();
        String targetedVersion = repository.getTargetedVersion();

        // If deployment version is empty and repo version is present, it's a mismatch
        if (deploymentVersion == null || deploymentVersion.trim().isEmpty()) {
            return repoVersion != null && !repoVersion.trim().isEmpty();
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

    private boolean shouldHighlightRepositoryRow(Repository repository) {
        if (repository == null || repository.isIgnore()) {
            return false;
        }
        if (isRepoVersionBehindTarget(repository)) {
            return true;
        }
        String repoVer = repository.getRepoVersion();
        String depVer = repository.getDeploymentVersion();
        if (repoVer != null && !repoVer.isBlank() && depVer != null && !depVer.isBlank() && !repoVer.equals(depVer)) {
            return true;
        }
        return repoVer != null && !repoVer.isBlank() && (depVer == null || depVer.isBlank());
    }

    /** True when a targeted version is set and repo version sorts lower (e.g. 5.20.1 vs 5.24.0). */
    private boolean isRepoVersionBehindTarget(Repository repository) {
        String repoVer = repository.getRepoVersion();
        String targetedVer = repository.getTargetedVersion();
        if (repoVer == null || repoVer.isBlank() || targetedVer == null || targetedVer.isBlank()) {
            return false;
        }
        return compareVersionStrings(repoVer, targetedVer) < 0;
    }

    private int compareVersionStrings(String left, String right) {
        int[] leftParts = parseVersionParts(left);
        int[] rightParts = parseVersionParts(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int lv = i < leftParts.length ? leftParts[i] : 0;
            int rv = i < rightParts.length ? rightParts[i] : 0;
            if (lv != rv) {
                return Integer.compare(lv, rv);
            }
        }
        return 0;
    }

    private int[] parseVersionParts(String version) {
        String normalized = version.trim();
        int dash = normalized.indexOf('-');
        if (dash > 0) {
            normalized = normalized.substring(0, dash);
        }
        String[] segments = normalized.split("\\.");
        int[] parts = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].replaceAll("[^0-9].*", "");
            if (segment.isEmpty()) {
                parts[i] = 0;
            } else {
                try {
                    parts[i] = Integer.parseInt(segment);
                } catch (NumberFormatException e) {
                    parts[i] = 0;
                }
            }
        }
        return parts;
    }

    private void refreshDeploymentVersionsFromConfiguredPath() {
        String depPath = deploymentPathField.getText();
        if (depPath != null && !depPath.isBlank()) {
            updateDeploymentVersions(Path.of(depPath.trim()));
        }
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
            // When multiple WARs map to the same suffix, keep the newest file on disk
            Map<String, Long> deployedVersionTimestamps = new HashMap<>();
            // Map from war file name (lowercase) to last modified date string
            Map<String, String> warModifiedMap = new HashMap<>();
            Map<String, String> deployedArtifactNames = new HashMap<>();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("M/d/yyyy h:mm a");

            File[] warFiles = depDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".war"));
            if (warFiles != null) {
                logger.info("Found {} WAR files in deployment directory", warFiles.length);
                for (File war : warFiles) {
                    String name = war.getName().toLowerCase();
                    logger.debug("Processing WAR file: {}", name);
                    String modified = sdf.format(new java.util.Date(war.lastModified()));
                    warModifiedMap.put(name, modified);

                    Matcher optSoaMatcher = OPT_SOA_DEPLOYMENT_PATTERN.matcher(name);
                    if (optSoaMatcher.matches()) {
                        // opt-soa is deployed via Spring SOA jar, not JBoss WARs
                        continue;
                    }

                    Matcher webmapMatcher = WEBMAP_DEPLOYMENT_WAR_PATTERN.matcher(name);
                    if (webmapMatcher.matches()) {
                        putDeployedVersion(deployedVersions, deployedVersionTimestamps,
                            "webmap", webmapMatcher.group(1), war);
                        continue;
                    }

                    java.util.regex.Matcher webmapDataMatcher = java.util.regex.Pattern
                        .compile("^webmap-data-service-(\\d+(?:\\.\\d+)*(?:-[a-zA-Z0-9]+)?)\\.war$")
                        .matcher(name);
                    if (webmapDataMatcher.matches()) {
                        putDeployedVersion(deployedVersions, deployedVersionTimestamps,
                            "webmap-data-service", webmapDataMatcher.group(1), war);
                        continue;
                    }

                    // ampt-<suffix>-<version>.war (suffix may contain hyphens)
                    Matcher amptMatcher = AMPT_WAR_FILE_PATTERN.matcher(name);
                    if (amptMatcher.matches()) {
                        putDeployedVersion(deployedVersions, deployedVersionTimestamps,
                            amptMatcher.group(1), amptMatcher.group(2), war);
                    }
                }
            }

            // opt-soa: version from SOA Deploy Path only (opt-soa-exec.jar), not JBoss deployments
            applyOptSoaSpringJarDeploymentVersion(deployedVersions, deployedVersionTimestamps,
                warModifiedMap, deployedArtifactNames, sdf);

            // Clear previous deployment versions and modified dates
            repositories.forEach(r -> { r.setDeploymentVersion(""); r.setDeploymentModified(""); });

            for (Repository repo : repositories) {
                String repoName = repo.getName().toLowerCase();
                String warFileName = null;
                String version = null;
                // map opt-<suffix> -> <suffix>
                if (repoName.startsWith("opt-")) {
                    String suffix = repoName.substring(4);
                    version = deployedVersions.get(suffix);
                    if (version != null) {
                        repo.setDeploymentVersion(version);
                        warFileName = "ampt-" + suffix + "-" + version + ".war";
                    }
                }
                // Handle webmap and webmap-data-service directly
                if (repoName.equals("webmap") || repoName.equals("webmap-data-service")) {
                    version = deployedVersions.get(repoName);
                    if (version != null) {
                        repo.setDeploymentVersion(version);
                        warFileName = getWarArtifactPrefix(repoName) + "-" + version + ".war";
                        if ("webmap-data-service".equals(repoName)) {
                            warFileName = repoName + "-" + version + ".war";
                        }
                    }
                }
                // Handle opt-soa
                if (repoName.equals("opt-soa")) {
                    version = deployedVersions.get("soa");
                    if (version != null) {
                        repo.setDeploymentVersion(version);
                        warFileName = deployedArtifactNames.getOrDefault("soa", "opt-soa-" + version + ".war");
                    }
                }
                // Repos without opt- prefix (e.g. common-map-interface) use ampt-<name>-<version>.war
                if (version == null && !repoName.startsWith("opt-")
                        && !repoName.equals("webmap") && !repoName.equals("webmap-data-service")) {
                    version = deployedVersions.get(repoName);
                    if (version != null) {
                        repo.setDeploymentVersion(version);
                        warFileName = "ampt-" + repoName + "-" + version + ".war";
                    }
                }
                // Set deploymentModified if warFileName found
                if (warFileName != null) {
                    String modified = warModifiedMap.get(warFileName.toLowerCase());
                    if (modified != null) {
                        repo.setDeploymentModified(modified);
                    }
                }
            }

            // Ensure webmap row matches webmap2-*.war even if generic parsing missed it
            applyWebmapDeploymentFromDirectory(depDir, sdf);

            repoTable.refresh();
            logger.info("Updated deployment versions for {} repositories ({} WAR mappings)",
                    repositories.stream().filter(r -> !r.getDeploymentVersion().isEmpty()).count(),
                    deployedVersions.size());
        } catch (Exception e) {
            logger.error("Error updating deployment versions from {}", deploymentPath, e);
        }
    }

    private void applyWebmapDeploymentFromDirectory(File depDir, java.text.SimpleDateFormat sdf) {
        Repository webmapRepo = repositories.stream()
            .filter(r -> "webmap".equalsIgnoreCase(r.getName()))
            .findFirst()
            .orElse(null);
        if (webmapRepo == null) {
            return;
        }

        File[] warFiles = depDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".war") && lower.startsWith("webmap2-");
        });
        if (warFiles == null || warFiles.length == 0) {
            return;
        }

        File newest = null;
        String version = null;
        for (File war : warFiles) {
            Matcher matcher = WEBMAP_DEPLOYMENT_WAR_PATTERN.matcher(war.getName().toLowerCase());
            if (!matcher.matches()) {
                continue;
            }
            if (newest == null || war.lastModified() >= newest.lastModified()) {
                newest = war;
                version = matcher.group(1);
            }
        }
        if (version == null || version.isBlank()) {
            return;
        }

        webmapRepo.setDeploymentVersion(version);
        webmapRepo.setDeploymentModified(sdf.format(new java.util.Date(newest.lastModified())));
        logger.info("Applied webmap deployment from {} -> version {}", newest.getName(), version);
    }

    private void putDeployedVersion(Map<String, String> deployedVersions,
                                    Map<String, Long> deployedVersionTimestamps,
                                    String repoKey,
                                    String version,
                                    File warFile) {
        long modified = warFile.lastModified();
        Long previous = deployedVersionTimestamps.get(repoKey);
        if (previous == null || modified >= previous) {
            deployedVersions.put(repoKey, version);
            deployedVersionTimestamps.put(repoKey, modified);
        }
    }

    private void recordOptSoaDeployment(Map<String, String> deployedVersions,
                                        Map<String, Long> deployedVersionTimestamps,
                                        Map<String, String> warModifiedMap,
                                        Map<String, String> deployedArtifactNames,
                                        String version,
                                        File artifact) {
        long modified = artifact.lastModified();
        Long previous = deployedVersionTimestamps.get("soa");
        if (previous == null || modified >= previous) {
            deployedVersions.put("soa", version);
            deployedVersionTimestamps.put("soa", modified);
            String artifactName = artifact.getName();
            deployedArtifactNames.put("soa", artifactName);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("M/d/yyyy h:mm a");
            warModifiedMap.put(artifactName.toLowerCase(), sdf.format(new java.util.Date(modified)));
            logger.info("Detected opt-soa deployment: {} (version {})", artifact.getAbsolutePath(), version);
        }
    }

    /**
     * Scan JBoss standalone/tmp (and deployments) for opt-soa exploded deployments or WAR files.
     */
    private Path getSpringSoaDeployDirectory() {
        String path = soaDeployPathLabel != null ? soaDeployPathLabel.getText() : null;
        if (path == null || path.isBlank()) {
            path = DEFAULT_SPRING_SOA_DEPLOY_PATH;
        }
        return Paths.get(path.trim().replace('/', File.separatorChar).replace('\\', File.separatorChar));
    }

    private String readImplementationVersionFromJar(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String version = manifest.getMainAttributes().getValue("Implementation-Version");
                if (version != null && !version.isBlank()) {
                    return version.trim();
                }
            }
        } catch (IOException e) {
            logger.warn("Could not read manifest from {}", jarPath, e);
        }
        return "";
    }

    private Path findBuiltOptSoaExecJar(Path optSoaProjectPath) {
        Path[] searchDirs = {
            optSoaProjectPath.resolve("SOA").resolve("target"),
            optSoaProjectPath.resolve("target")
        };
        Path newest = null;
        long newestTime = -1;
        for (Path dir : searchDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            Path legacy = dir.resolve(OPT_SOA_EXEC_JAR);
            if (Files.isRegularFile(legacy)) {
                try {
                    long t = Files.getLastModifiedTime(legacy).toMillis();
                    if (t >= newestTime) {
                        newest = legacy;
                        newestTime = t;
                    }
                } catch (IOException e) {
                    logger.debug("Could not read timestamp for {}", legacy, e);
                }
            }
            // Spring Boot repackage of a war-packaged module yields opt-soa-<ver>-exec.war;
            // it is deployed renamed as opt-soa-exec.jar.
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "opt-soa*-exec.{jar,war}")) {
                for (Path candidate : stream) {
                    if (!Files.isRegularFile(candidate)) {
                        continue;
                    }
                    long t = Files.getLastModifiedTime(candidate).toMillis();
                    if (newest == null || t >= newestTime) {
                        newest = candidate;
                        newestTime = t;
                    }
                }
            } catch (IOException e) {
                logger.debug("Could not scan {} for opt-soa exec jar: {}", dir, e.getMessage());
            }
        }
        if (newest != null) {
            appendToBuildLog("Using built Spring SOA artifact: " + newest.getFileName() + "\n");
        }
        return newest;
    }

    private boolean deployOptSoaSpringJar(Path optSoaProjectPath) {
        try {
            appendToBuildLog("\n=== Starting Spring SOA deployment ===\n");
            Path deployDir = getSpringSoaDeployDirectory();
            if (!Files.isDirectory(deployDir)) {
                appendToBuildLog("ERROR: SOA deploy directory does not exist: " + deployDir + "\n");
                return false;
            }

            Path sourceJar = findBuiltOptSoaExecJar(optSoaProjectPath);
            if (sourceJar == null) {
                appendToBuildLog("ERROR: no opt-soa-<version>-exec.war/.jar found under SOA/target or target\n");
                return false;
            }

            String version = readImplementationVersionFromJar(sourceJar);
            appendToBuildLog("Deploying " + OPT_SOA_EXEC_JAR
                + " (version: " + (version.isEmpty() ? "unknown" : version) + ")\n");
            appendToBuildLog("Source: " + sourceJar + "\n");
            appendToBuildLog("Target directory: " + deployDir + "\n");

            Path targetJar = deployDir.resolve(OPT_SOA_EXEC_JAR);
            if (Files.exists(targetJar)) {
                String backupName = OPT_SOA_EXEC_JAR + ".backup-"
                    + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
                Path backupPath = deployDir.resolve(backupName);
                Files.copy(targetJar, backupPath, StandardCopyOption.REPLACE_EXISTING);
                appendToBuildLog("Backed up existing JAR to " + backupName + "\n");
            }

            // A running Spring SOA JVM holds opt-soa-exec.jar open; Windows will not let us overwrite it.
            boolean wasRunning = stopRunningOptSoaProcesses();

            copyWithRetry(sourceJar, targetJar);
            appendToBuildLog("Successfully deployed: " + targetJar + "\n");

            if (wasRunning) {
                restartOptSoaService(deployDir);
            } else {
                appendToBuildLog("Spring SOA was not running; not starting it.\n");
            }

            appendToBuildLog("\n=== Spring SOA Deployment Completed ===\n");
            return true;
        } catch (Exception e) {
            appendToBuildLog("ERROR: Spring SOA deployment failed - " + e.getMessage() + "\n");
            logger.error("Spring SOA deployment failed", e);
            return false;
        }
    }

    /** PIDs of java processes whose command line runs opt-soa-exec.jar. */
    private List<Long> findRunningOptSoaProcessIds() {
        List<Long> pids = new ArrayList<>();
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        if (windows) {
            String script = "Get-CimInstance Win32_Process -Filter \"name='java.exe' or name='javaw.exe'\" "
                + "| Where-Object { $_.CommandLine -like '*" + OPT_SOA_EXEC_JAR + "*' } "
                + "| ForEach-Object { $_.ProcessId }";
            // -EncodedCommand avoids Windows argument quoting mangling the embedded quotes in the script
            String encoded = java.util.Base64.getEncoder()
                .encodeToString(script.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.matches("\\d+")) {
                            pids.add(Long.parseLong(trimmed));
                        }
                    }
                }
                p.waitFor();
            } catch (Exception e) {
                logger.warn("Could not query running Spring SOA processes", e);
            }
        } else {
            ProcessHandle.allProcesses()
                .filter(h -> h.info().commandLine().map(c -> c.contains(OPT_SOA_EXEC_JAR)).orElse(false))
                .forEach(h -> pids.add(h.pid()));
        }
        pids.remove(Long.valueOf(ProcessHandle.current().pid()));
        logger.info("Running Spring SOA process ids: {}", pids);
        return pids;
    }

    /** Stop running Spring SOA JVM(s). Returns true if at least one was running. */
    private boolean stopRunningOptSoaProcesses() {
        List<Long> pids = findRunningOptSoaProcessIds();
        if (pids.isEmpty()) {
            appendToBuildLog("No running Spring SOA process found.\n");
            return false;
        }
        for (long pid : pids) {
            ProcessHandle.of(pid).ifPresent(handle -> {
                appendToBuildLog("Stopping running Spring SOA (PID " + pid + ")...\n");
                handle.destroy();
                try {
                    handle.onExit().get(20, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException te) {
                    appendToBuildLog("PID " + pid + " did not exit; forcing...\n");
                    handle.destroyForcibly();
                    try {
                        handle.onExit().get(10, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception ignored) {
                        // fall through; copyWithRetry will report if the file is still locked
                    }
                } catch (Exception e) {
                    logger.warn("Error waiting for PID {} to exit", pid, e);
                }
                appendToBuildLog("Stopped PID " + pid + "\n");
            });
        }
        return true;
    }

    /** Copy with a few retries; Windows can hold the file handle briefly after the JVM exits. */
    private void copyWithRetry(Path source, Path target) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= 10; attempt++) {
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (java.nio.file.FileSystemException e) {
                last = e;
                appendToBuildLog("Target still locked (attempt " + attempt + "/10), retrying...\n");
                Thread.sleep(1000);
            }
        }
        throw last;
    }

    /** Relaunch Spring SOA using the startup script that lives next to the jar. */
    private void restartOptSoaService(Path deployDir) {
        Path startupScript = deployDir.resolve("startup SOA.vbs");
        if (!Files.isRegularFile(startupScript)) {
            appendToBuildLog("WARNING: " + startupScript.getFileName()
                + " not found; start Spring SOA manually.\n");
            return;
        }
        try {
            appendToBuildLog("Restarting Spring SOA via " + startupScript.getFileName() + "...\n");
            ProcessBuilder pb = new ProcessBuilder("wscript.exe", startupScript.toString());
            pb.directory(deployDir.toFile());
            pb.redirectErrorStream(true);
            pb.start();
            appendToBuildLog("Spring SOA start requested.\n");
        } catch (IOException e) {
            appendToBuildLog("WARNING: could not launch " + startupScript.getFileName()
                + " - " + e.getMessage() + "\n");
            logger.warn("Failed to restart Spring SOA", e);
        }
    }

    private void applyOptSoaSpringJarDeploymentVersion(Map<String, String> deployedVersions,
                                                      Map<String, Long> deployedVersionTimestamps,
                                                      Map<String, String> warModifiedMap,
                                                      Map<String, String> deployedArtifactNames,
                                                      java.text.SimpleDateFormat sdf) {
        Path jarPath = getSpringSoaDeployDirectory().resolve(OPT_SOA_EXEC_JAR);
        if (!Files.isRegularFile(jarPath)) {
            logger.debug("No Spring SOA executable at {}", jarPath);
            return;
        }

        String version = readImplementationVersionFromJar(jarPath);
        if (version.isBlank()) {
            logger.warn("Spring SOA jar exists but Implementation-Version is missing: {}", jarPath);
            return;
        }

        File jarFile = jarPath.toFile();
        deployedVersions.put("soa", version);
        deployedVersionTimestamps.put("soa", jarFile.lastModified());
        deployedArtifactNames.put("soa", OPT_SOA_EXEC_JAR);
        warModifiedMap.put(OPT_SOA_EXEC_JAR.toLowerCase(), sdf.format(new java.util.Date(jarFile.lastModified())));
        logger.info("opt-soa deployment version from Spring SOA jar {}: {}", jarPath, version);
    }

    private void scanOptSoaInSiblingLocations(Path deploymentPath,
                                              Map<String, String> deployedVersions,
                                              Map<String, Long> deployedVersionTimestamps,
                                              Map<String, String> warModifiedMap,
                                              Map<String, String> deployedArtifactNames) {
        Path standaloneDir = deploymentPath.getParent();
        if (standaloneDir == null) {
            return;
        }
        List<Path> locations = List.of(deploymentPath, standaloneDir.resolve("tmp"));
        for (Path location : locations) {
            if (!Files.isDirectory(location)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(location)) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    Matcher matcher = OPT_SOA_DEPLOYMENT_PATTERN.matcher(name);
                    if (!matcher.matches()) {
                        continue;
                    }
                    recordOptSoaDeployment(deployedVersions, deployedVersionTimestamps, warModifiedMap,
                        deployedArtifactNames, matcher.group(1), entry.toFile());
                }
            } catch (IOException e) {
                logger.debug("Could not scan {} for opt-soa: {}", location, e.getMessage());
            }
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
                    // After pasting, select rows that have a targeted version set by the filter
                    Platform.runLater(() -> selectRowsWithTargetedVersion());
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
     * Select repositories which have a targeted version set.
     * This is called after pasting into the filter TextArea to automatically
     * mark matching repositories for batch operations.
     */
    private void selectRowsWithTargetedVersion() {
        if (filteredRepositories == null || filteredRepositories.isEmpty()) return;

        int selectedCount = 0;
        for (Repository repo : filteredRepositories) {
            String targeted = repo.getTargetedVersion();
            if (targeted != null && !targeted.isBlank()) {
                if (!repo.isIgnore()) {
                    repo.setSelected(true);
                    selectedCount++;
                }
            } else {
                // do not change selection for repos without targeted version
            }
        }
        repoTable.refresh();
        updateHeaderCheckboxState();
        updateBuildButtonState();
        updateStatusLabel();
        logger.info("Auto-selected {} repositories based on targeted versions", selectedCount);
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
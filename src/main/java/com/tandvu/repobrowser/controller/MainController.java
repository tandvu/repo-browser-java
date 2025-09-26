package com.tandvu.repobrowser.controller;

import com.tandvu.repobrowser.model.Repository;
import com.tandvu.repobrowser.service.RepositoryScanner;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Main controller for the Repo Browser application
 */
public class MainController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    @FXML private TextField basePathField;
    @FXML private Button browseButton;
    @FXML private Button scanButton;
    @FXML private TextField filterField;
    @FXML private TableView<Repository> repoTable;
    @FXML private TableColumn<Repository, Boolean> selectedColumn;
    @FXML private TableColumn<Repository, String> nameColumn;
    @FXML private TableColumn<Repository, String> pathColumn;
    @FXML private Button selectAllButton;
    @FXML private Button clearAllButton;
    @FXML private Label statusLabel;
    @FXML private ProgressBar progressBar;
    
    private final ObservableList<Repository> repositories = FXCollections.observableArrayList();
    private final ObservableList<Repository> filteredRepositories = FXCollections.observableArrayList();
    private final RepositoryScanner repositoryScanner = new RepositoryScanner();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Initializing MainController");
        
        // Set default base path
        basePathField.setText("C:\\AMPT");
        
        // Setup table columns
        setupTableColumns();
        
        // Setup filter functionality
        setupFilter();
        
        // Set initial status
        statusLabel.setText("Ready - Select a base path and click Scan");
        progressBar.setVisible(false);
        
        logger.info("MainController initialized successfully");
    }
    
    private void setupTableColumns() {
        // Selected column with checkboxes
        selectedColumn.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectedColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectedColumn));
        selectedColumn.setEditable(true);
        
        // Repository name column
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        
        // Repository path column  
        pathColumn.setCellValueFactory(new PropertyValueFactory<>("path"));
        
        // Make table editable for checkboxes
        repoTable.setEditable(true);
        repoTable.setItems(filteredRepositories);
    }
    
    private void setupFilter() {
        filterField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterRepositories(newValue);
        });
    }
    
    private void filterRepositories(String filter) {
        filteredRepositories.clear();
        
        if (filter == null || filter.trim().isEmpty()) {
            filteredRepositories.addAll(repositories);
        } else {
            String lowerFilter = filter.toLowerCase().trim();
            filteredRepositories.addAll(
                repositories.stream()
                    .filter(repo -> repo.getName().toLowerCase().contains(lowerFilter))
                    .collect(Collectors.toList())
            );
        }
        
        updateStatusLabel();
    }
    
    @FXML
    private void handleBrowseButton() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Base Path for Repository Scanning");
        
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
            basePathField.setText(selectedDirectory.getAbsolutePath());
            logger.info("Selected base path: {}", selectedDirectory.getAbsolutePath());
        }
    }
    
    @FXML
    private void handleScanButton() {
        String basePath = basePathField.getText();
        if (basePath == null || basePath.trim().isEmpty()) {
            showAlert("Error", "Please select a base path first.");
            return;
        }
        
        File baseDir = new File(basePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            showAlert("Error", "Selected path does not exist or is not a directory.");
            return;
        }
        
        scanRepositories(Path.of(basePath));
    }
    
    private void scanRepositories(Path basePath) {
        logger.info("Scanning repositories in: {}", basePath);
        
        // Show progress
        progressBar.setVisible(true);
        statusLabel.setText("Scanning repositories...");
        scanButton.setDisable(true);
        
        // Clear existing repositories
        repositories.clear();
        filteredRepositories.clear();
        
        try {
            List<Repository> foundRepos = repositoryScanner.scanForRepositories(basePath);
            repositories.addAll(foundRepos);
            filteredRepositories.addAll(foundRepos);
            
            logger.info("Found {} repositories", foundRepos.size());
            updateStatusLabel();
            
        } catch (Exception e) {
            logger.error("Error scanning repositories", e);
            showAlert("Error", "Failed to scan repositories: " + e.getMessage());
            statusLabel.setText("Error occurred during scanning");
        } finally {
            progressBar.setVisible(false);
            scanButton.setDisable(false);
        }
    }
    
    @FXML
    private void handleSelectAll() {
        repositories.forEach(repo -> repo.setSelected(true));
        repoTable.refresh();
        logger.info("Selected all repositories");
    }
    
    @FXML
    private void handleClearAll() {
        repositories.forEach(repo -> repo.setSelected(false));
        repoTable.refresh();
        logger.info("Cleared all repository selections");
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
}
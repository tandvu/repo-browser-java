package com.tandvu.repobrowser;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.prefs.Preferences;

/**
 * JavaFX Application entry point for Repo Browser
 */
public class RepoBrowserApplication extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(RepoBrowserApplication.class);
    
    // Preferences constants for window size
    private static final String PREF_WINDOW_WIDTH = "window_width";
    private static final String PREF_WINDOW_HEIGHT = "window_height";
    private static final double DEFAULT_WIDTH = 1000.0;
    private static final double DEFAULT_HEIGHT = 700.0;
    
    // Preferences instance
    private final Preferences preferences = Preferences.userNodeForPackage(RepoBrowserApplication.class);
    
    @Override
    public void start(Stage stage) throws IOException {
        logger.info("Starting Repo Browser JavaFX Application");
        
        FXMLLoader fxmlLoader = new FXMLLoader(RepoBrowserApplication.class.getResource("/fxml/main.fxml"));
        Parent root = fxmlLoader.load();
        
        // Load saved window size
        double savedWidth = preferences.getDouble(PREF_WINDOW_WIDTH, DEFAULT_WIDTH);
        double savedHeight = preferences.getDouble(PREF_WINDOW_HEIGHT, DEFAULT_HEIGHT);
        
        Scene scene = new Scene(root, savedWidth, savedHeight);
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        
        stage.setTitle("Repo Browser - JavaFX Desktop");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        
        // Save window size when it changes
        stage.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (newWidth != null) {
                preferences.putDouble(PREF_WINDOW_WIDTH, newWidth.doubleValue());
                logger.debug("Saved window width: {}", newWidth);
            }
        });
        
        stage.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            if (newHeight != null) {
                preferences.putDouble(PREF_WINDOW_HEIGHT, newHeight.doubleValue());
                logger.debug("Saved window height: {}", newHeight);
            }
        });
        
        stage.show();
        
        logger.info("Application started successfully with size: {}x{}", savedWidth, savedHeight);
    }

    public static void main(String[] args) {
        launch();
    }
}
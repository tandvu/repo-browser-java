package com.tandvu.repobrowser;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JavaFX Application entry point for Repo Browser
 */
public class RepoBrowserApplication extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(RepoBrowserApplication.class);
    
    @Override
    public void start(Stage stage) throws IOException {
        logger.info("Starting Repo Browser JavaFX Application");
        
        FXMLLoader fxmlLoader = new FXMLLoader(RepoBrowserApplication.class.getResource("/fxml/main.fxml"));
        Parent root = fxmlLoader.load();
        
        Scene scene = new Scene(root, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        
        stage.setTitle("Repo Browser - JavaFX Desktop");
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.show();
        
        logger.info("Application started successfully");
    }

    public static void main(String[] args) {
        launch();
    }
}
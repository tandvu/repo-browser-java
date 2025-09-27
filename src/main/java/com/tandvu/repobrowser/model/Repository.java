package com.tandvu.repobrowser.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Model class representing a repository
 */
public class Repository {
    
    private final StringProperty name;
    private final StringProperty path;
    private final BooleanProperty selected;
    private final StringProperty repoVersion;
    private final StringProperty targetedVersion;
    private final StringProperty deploymentVersion;
    private final BooleanProperty ignore; // Added ignore property
    
    public Repository(String name, String path) {
        this.name = new SimpleStringProperty(name);
        this.path = new SimpleStringProperty(path);
        this.selected = new SimpleBooleanProperty(false);
        this.repoVersion = new SimpleStringProperty("");
        this.targetedVersion = new SimpleStringProperty("");
        this.deploymentVersion = new SimpleStringProperty("");
        this.ignore = new SimpleBooleanProperty(false); // Initialize ignore property
    }
    
    // Name property
    public String getName() {
        return name.get();
    }
    
    public void setName(String name) {
        this.name.set(name);
    }
    
    public StringProperty nameProperty() {
        return name;
    }
    
    // Path property
    public String getPath() {
        return path.get();
    }
    
    public void setPath(String path) {
        this.path.set(path);
    }
    
    public StringProperty pathProperty() {
        return path;
    }
    
    // Selected property
    public boolean isSelected() {
        return selected.get();
    }
    
    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }
    
    public BooleanProperty selectedProperty() {
        return selected;
    }
    
    // Repo Version property
    public String getRepoVersion() {
        return repoVersion.get();
    }
    
    public void setRepoVersion(String repoVersion) {
        this.repoVersion.set(repoVersion != null ? repoVersion : "");
    }
    
    public StringProperty repoVersionProperty() {
        return repoVersion;
    }
    
    // Targeted Version property
    public String getTargetedVersion() {
        return targetedVersion.get();
    }
    
    public void setTargetedVersion(String targetedVersion) {
        this.targetedVersion.set(targetedVersion != null ? targetedVersion : "");
    }
    
    public StringProperty targetedVersionProperty() {
        return targetedVersion;
    }
    
    // Deployment Version property
    public String getDeploymentVersion() {
        return deploymentVersion.get();
    }
    
    public void setDeploymentVersion(String deploymentVersion) {
        this.deploymentVersion.set(deploymentVersion != null ? deploymentVersion : "");
    }
    
    public StringProperty deploymentVersionProperty() {
        return deploymentVersion;
    }
    
    // Ignore property
    public boolean isIgnore() {
        return ignore.get();
    }
    
    public void setIgnore(boolean ignore) {
        this.ignore.set(ignore);
    }
    
    public BooleanProperty ignoreProperty() {
        return ignore;
    }
    
    @Override
    public String toString() {
        return String.format("Repository{name='%s', path='%s', selected=%s, repoVersion='%s', targetedVersion='%s', deploymentVersion='%s'}", 
            getName(), getPath(), isSelected(), getRepoVersion(), getTargetedVersion(), getDeploymentVersion());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Repository that = (Repository) o;
        return getName().equals(that.getName()) && getPath().equals(that.getPath());
    }
    
    @Override
    public int hashCode() {
        return getName().hashCode() * 31 + getPath().hashCode();
    }
}
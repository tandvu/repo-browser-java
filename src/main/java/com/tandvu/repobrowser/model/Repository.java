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
    
    public Repository(String name, String path) {
        this.name = new SimpleStringProperty(name);
        this.path = new SimpleStringProperty(path);
        this.selected = new SimpleBooleanProperty(false);
        this.repoVersion = new SimpleStringProperty("");
        this.targetedVersion = new SimpleStringProperty("");
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
    
    @Override
    public String toString() {
        return String.format("Repository{name='%s', path='%s', selected=%s, repoVersion='%s', targetedVersion='%s'}", 
            getName(), getPath(), isSelected(), getRepoVersion(), getTargetedVersion());
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
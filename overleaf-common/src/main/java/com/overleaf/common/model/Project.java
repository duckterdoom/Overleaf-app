package com.overleaf.common.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Shared Model: Thực thể dự án LaTeX (Project).
 */
public class Project implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String ownerUsername;
    private Date lastModified;
    private List<String> collaborators;

    public Project() {
        this.lastModified = new Date();
        this.collaborators = new ArrayList<>();
    }

    public Project(String id, String title, String ownerUsername) {
        this.id = id;
        this.title = title;
        this.ownerUsername = ownerUsername;
        this.lastModified = new Date();
        this.collaborators = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public List<String> getCollaborators() {
        return collaborators;
    }

    public void setCollaborators(List<String> collaborators) {
        this.collaborators = collaborators;
    }

    public void addCollaborator(String username) {
        if (!this.collaborators.contains(username)) {
            this.collaborators.add(username);
        }
    }
}

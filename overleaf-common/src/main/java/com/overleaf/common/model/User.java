package com.overleaf.common.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared Model: Thực thể người dùng (User).
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String username;
    private String passwordHash;
    private String email;
    private List<String> projectIds;

    public User() {
        this.projectIds = new ArrayList<>();
    }

    public User(String id, String username, String passwordHash, String email) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.projectIds = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<String> projectIds) {
        this.projectIds = projectIds;
    }

    public void addProjectId(String projectId) {
        if (!this.projectIds.contains(projectId)) {
            this.projectIds.add(projectId);
        }
    }
}

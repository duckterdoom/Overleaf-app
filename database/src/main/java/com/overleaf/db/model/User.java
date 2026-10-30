package com.overleaf.db.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class User {
    @BsonId
    private ObjectId id;

    @BsonProperty("username")
    private String username;

    @BsonProperty("email")
    private String email;

    @BsonProperty("password_hash")
    private String passwordHash;

    @BsonProperty("project_ids")
    private List<ObjectId> projectIds = new ArrayList<>();

    public User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // Getters & Setters
    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public List<ObjectId> getProjectIds() { return projectIds; }
    public void setProjectIds(List<ObjectId> projectIds) { this.projectIds = projectIds; }
}
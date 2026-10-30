package com.overleaf.db.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.Instant;

public class Document {

    @BsonId
    private ObjectId id;

    @BsonProperty("project_id")
    private ObjectId projectId;

    @BsonProperty("name")
    private String name; // Tên file: main.tex, logo.png, images/

    @BsonProperty("path")
    private String path; // Đường dẫn tương đối trong project: /main.tex hoặc /images/logo.png

    @BsonProperty("content")
    private String content; // Nội dung văn bản đối với file .tex/.bib

    @BsonProperty("is_directory")
    private boolean isDirectory;

    @BsonProperty("created_at")
    private Instant createdAt;

    @BsonProperty("updated_at")
    private Instant updatedAt;

    public Document() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Document(ObjectId projectId, String name, String path, String content, boolean isDirectory) {
        this.projectId = projectId;
        this.name = name;
        this.path = path;
        this.content = content;
        this.isDirectory = isDirectory;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters & Setters
    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public ObjectId getProjectId() { return projectId; }
    public void setProjectId(ObjectId projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean isDirectory() { return isDirectory; }
    public void setDirectory(boolean directory) { isDirectory = directory; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
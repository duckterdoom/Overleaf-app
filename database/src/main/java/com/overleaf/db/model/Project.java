package com.overleaf.db.model;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Project {

    @BsonId
    private ObjectId id;

    @BsonProperty("name")
    private String name;

    @BsonProperty("owner_id")
    private ObjectId ownerId;

    @BsonProperty("collaborator_ids")
    private List<ObjectId> collaboratorIds = new ArrayList<>();

    @BsonProperty("root_document_id")
    private ObjectId rootDocumentId; // ID file main.tex chính để compile

    @BsonProperty("created_at")
    private Instant createdAt;

    @BsonProperty("updated_at")
    private Instant updatedAt;

    public Project() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Project(String name, ObjectId ownerId) {
        this.name = name;
        this.ownerId = ownerId;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters & Setters
    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ObjectId getOwnerId() { return ownerId; }
    public void setOwnerId(ObjectId ownerId) { this.ownerId = ownerId; }

    public List<ObjectId> getCollaboratorIds() { return collaboratorIds; }
    public void setCollaboratorIds(List<ObjectId> collaboratorIds) { this.collaboratorIds = collaboratorIds; }

    public ObjectId getRootDocumentId() { return rootDocumentId; }
    public void setRootDocumentId(ObjectId rootDocumentId) { this.rootDocumentId = rootDocumentId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
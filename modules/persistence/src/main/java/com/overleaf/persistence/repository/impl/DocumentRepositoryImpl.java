package com.overleaf.persistence.repository.impl;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.overleaf.persistence.config.MongoDatabaseManager;
import com.overleaf.persistence.model.Document;
import com.overleaf.persistence.repository.DocumentRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;

public class DocumentRepositoryImpl implements DocumentRepository {

    private final MongoCollection<Document> documentCollection;

    public DocumentRepositoryImpl() {
        MongoDatabase database = MongoDatabaseManager.getDatabase();
        this.documentCollection = database.getCollection("documents", Document.class);
    }

    @Override
    public Document save(Document document) {
        document.setUpdatedAt(Instant.now());
        if (document.getId() == null) {
            documentCollection.insertOne(document);
        } else {
            documentCollection.replaceOne(Filters.eq("_id", document.getId()), document);
        }
        return document;
    }

    @Override
    public Optional<Document> findById(String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            Document doc = documentCollection.find(Filters.eq("_id", objectId)).first();
            return Optional.ofNullable(doc);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Document> findByProjectId(String projectId) {
        try {
            ObjectId projObjectId = new ObjectId(projectId);
            return documentCollection
                    .find(Filters.eq("project_id", projObjectId))
                    .into(new ArrayList<>());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public Optional<Document> findByProjectIdAndPath(String projectId, String path) {
        try {
            ObjectId projObjectId = new ObjectId(projectId);
            Document doc =
                    documentCollection
                            .find(
                                    Filters.and(
                                            Filters.eq("project_id", projObjectId),
                                            Filters.eq("path", path)))
                            .first();
            return Optional.ofNullable(doc);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean updateContent(String documentId, String newContent) {
        try {
            ObjectId docObjectId = new ObjectId(documentId);
            return documentCollection
                            .updateOne(
                                    Filters.eq("_id", docObjectId),
                                    Updates.combine(
                                            Updates.set("content", newContent),
                                            Updates.set("updated_at", Instant.now())))
                            .getModifiedCount()
                    > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean deleteById(String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            return documentCollection.deleteOne(Filters.eq("_id", objectId)).getDeletedCount() > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

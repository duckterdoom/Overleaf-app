package com.overleaf.db.repository.impl;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.overleaf.db.config.MongoDatabaseManager;
import com.overleaf.db.model.Project;
import com.overleaf.db.repository.ProjectRepository;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProjectRepositoryImpl implements ProjectRepository {

    private final MongoCollection<Project> projectCollection;

    public ProjectRepositoryImpl() {
        MongoDatabase database = MongoDatabaseManager.getDatabase();
        this.projectCollection = database.getCollection("projects", Project.class);
    }

    @Override
    public Project save(Project project) {
        project.setUpdatedAt(Instant.now());
        if (project.getId() == null) {
            projectCollection.insertOne(project);
        } else {
            projectCollection.replaceOne(Filters.eq("_id", project.getId()), project);
        }
        return project;
    }

    @Override
    public Optional<Project> findById(String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            Project project = projectCollection.find(Filters.eq("_id", objectId)).first();
            return Optional.ofNullable(project);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Project> findByOwnerId(String ownerId) {
        try {
            ObjectId ownerObjectId = new ObjectId(ownerId);
            return projectCollection.find(Filters.eq("owner_id", ownerObjectId))
                    .into(new ArrayList<>());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public List<Project> findByCollaboratorId(String collaboratorId) {
        try {
            ObjectId collabObjectId = new ObjectId(collaboratorId);
            return projectCollection.find(Filters.eq("collaborator_ids", collabObjectId))
                    .into(new ArrayList<>());
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }

    @Override
    public void addCollaborator(String projectId, String userId) {
        try {
            projectCollection.updateOne(
                    Filters.eq("_id", new ObjectId(projectId)),
                    Updates.combine(
                            Updates.addToSet("collaborator_ids", new ObjectId(userId)),
                            Updates.set("updated_at", Instant.now())
                    )
            );
        } catch (IllegalArgumentException ignored) {}
    }

    @Override
    public boolean deleteById(String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            return projectCollection.deleteOne(Filters.eq("_id", objectId)).getDeletedCount() > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
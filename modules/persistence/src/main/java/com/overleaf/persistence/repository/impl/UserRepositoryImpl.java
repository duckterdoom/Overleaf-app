package com.overleaf.persistence.repository.impl;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.overleaf.persistence.config.MongoDatabaseManager;
import com.overleaf.persistence.model.User;
import com.overleaf.persistence.repository.UserRepository;
import java.util.Optional;
import org.bson.types.ObjectId;

public class UserRepositoryImpl implements UserRepository {

    private final MongoCollection<User> userCollection;

    public UserRepositoryImpl() {
        MongoDatabase database = MongoDatabaseManager.getDatabase();
        this.userCollection = database.getCollection("users", User.class);
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            userCollection.insertOne(user);
        } else {
            userCollection.replaceOne(
                    Filters.eq("_id", user.getId()),
                    user,
                    new com.mongodb.client.model.ReplaceOptions()
                            .upsert(true) // <--- Thêm upsert(true)
                    );
        }
        return user;
    }

    @Override
    public String createUser(String username, String email, String passwordHash) {
        User user = new User(username, email, passwordHash);
        userCollection.insertOne(user);
        return user.getId().toHexString();
    }

    @Override
    public Optional<User> findById(String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            User user = userCollection.find(Filters.eq("_id", objectId)).first();
            return Optional.ofNullable(user);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        User user = userCollection.find(Filters.eq("email", email)).first();
        return Optional.ofNullable(user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        User user = userCollection.find(Filters.eq("username", username)).first();
        return Optional.ofNullable(user);
    }

    @Override
    public void addProjectToUser(String userId, String projectId) {
        try {
            userCollection.updateOne(
                    Filters.eq("_id", new ObjectId(userId)),
                    Updates.push("project_ids", new ObjectId(projectId)));
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public void removeProjectFromUser(String userId, String projectId) {
        try {
            userCollection.updateOne(
                    Filters.eq("_id", new ObjectId(userId)),
                    Updates.pull("project_ids", new ObjectId(projectId)));
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public boolean deleteById(String id) {
        try {
            ObjectId objectId = new ObjectId(id);
            return userCollection.deleteOne(Filters.eq("_id", objectId)).getDeletedCount() > 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

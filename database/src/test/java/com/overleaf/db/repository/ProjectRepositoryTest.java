package com.overleaf.db.repository;

import com.overleaf.db.config.MongoDatabaseManager;
import com.overleaf.db.model.Project;
import com.overleaf.db.model.User;
import com.overleaf.db.repository.impl.ProjectRepositoryImpl;
import com.overleaf.db.repository.impl.UserRepositoryImpl;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRepositoryTest {

    private ProjectRepository projectRepository;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        MongoDatabaseManager.getDatabase().getCollection("projects").drop();
        MongoDatabaseManager.getDatabase().getCollection("users").drop();

        projectRepository = new ProjectRepositoryImpl();
        userRepository = new UserRepositoryImpl();
    }

    @Test
    void testCreateProjectAndAddCollaborator() {
        // 1. Tạo Owner User
        User owner = new User("owner_user", "owner@overleaf.com", "pass");
        userRepository.save(owner);

        // 2. Tạo Project
        Project project = new Project("Luan Van Tot Nghiep", owner.getId());
        projectRepository.save(project);
        assertNotNull(project.getId());

        // 3. Thêm Collaborator
        User collab = new User("collab_user", "collab@overleaf.com", "pass");
        userRepository.save(collab);

        projectRepository.addCollaborator(project.getId().toHexString(), collab.getId().toHexString());

        // 4. Kiểm tra truy vấn theo Owner & Collaborator
        List<Project> ownerProjects = projectRepository.findByOwnerId(owner.getId().toHexString());
        assertEquals(1, ownerProjects.size());

        List<Project> collabProjects = projectRepository.findByCollaboratorId(collab.getId().toHexString());
        assertEquals(1, collabProjects.size());
        assertEquals("Luan Van Tot Nghiep", collabProjects.get(0).getName());
    }

    @AfterAll
    static void tearDown() {
        MongoDatabaseManager.close();
    }
}
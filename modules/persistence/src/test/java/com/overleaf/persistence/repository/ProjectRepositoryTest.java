package com.overleaf.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.overleaf.persistence.config.MongoDatabaseManager;
import com.overleaf.persistence.model.Project;
import com.overleaf.persistence.model.User;
import com.overleaf.persistence.repository.impl.ProjectRepositoryImpl;
import com.overleaf.persistence.repository.impl.UserRepositoryImpl;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        projectRepository.addCollaborator(
                project.getId().toHexString(), collab.getId().toHexString());

        // 4. Kiểm tra truy vấn theo Owner & Collaborator
        List<Project> ownerProjects = projectRepository.findByOwnerId(owner.getId().toHexString());
        assertEquals(1, ownerProjects.size());

        List<Project> collabProjects =
                projectRepository.findByCollaboratorId(collab.getId().toHexString());
        assertEquals(1, collabProjects.size());
        assertEquals("Luan Van Tot Nghiep", collabProjects.get(0).getName());
    }

    @AfterAll
    static void tearDown() {
        MongoDatabaseManager.close();
    }
}

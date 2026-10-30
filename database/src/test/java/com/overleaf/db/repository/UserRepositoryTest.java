package com.overleaf.db.repository;

import com.overleaf.db.config.MongoDatabaseManager;
import com.overleaf.db.model.User;
import com.overleaf.db.repository.impl.UserRepositoryImpl;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest {

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userCollectionCleanUp();
        userRepository = new UserRepositoryImpl();
    }

    private void userCollectionCleanUp() {
        MongoDatabaseManager.getDatabase().getCollection("users").drop();
    }

    @Test
    void testSaveAndFindUser() {
        // 1. Lưu User mới
        User newUser = new User("dev_user", "dev@overleaf.com", "hashed_password_123");
        userRepository.save(newUser);

        assertNotNull(newUser.getId(), "User ID phải được tạo tự động bởi MongoDB");

        // 2. Tìm theo Email
        Optional<User> foundUser = userRepository.findByEmail("dev@overleaf.com");
        assertTrue(foundUser.isPresent(), "Không tìm thấy User theo email");
        assertEquals("dev_user", foundUser.get().getUsername());

        // 3. Tìm theo ID
        Optional<User> userById = userRepository.findById(newUser.getId().toHexString());
        assertTrue(userById.isPresent());
    }

    @Test
    void testAddProjectToUser() {
        // 1. Tạo user
        String userId = userRepository.createUser("project_owner", "owner@example.com", "pwd");
        String projectId = new ObjectId().toHexString();

        // 2. Thêm project
        userRepository.addProjectToUser(userId, projectId);

        // 3. Kiểm tra danh sách project_ids
        Optional<User> userOpt = userRepository.findById(userId);
        assertTrue(userOpt.isPresent());
        assertEquals(1, userOpt.get().getProjectIds().size());
        assertEquals(projectId, userOpt.get().getProjectIds().get(0).toHexString());
    }

    @AfterAll
    static void tearDown() {
        MongoDatabaseManager.close();
    }
}
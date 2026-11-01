package com.overleaf.persistence.repository;

import com.overleaf.persistence.config.MongoDatabaseManager;
import com.overleaf.persistence.model.User;
import com.overleaf.persistence.repository.impl.UserRepositoryImpl;
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

    @Test
    void testRemoveProjectFromUser() {
        // 1. Tạo và lưu User mới vào Database để lấy ID thực tế
        User testUser = new User("user_test_remove", "remove_proj@example.com", "pass_hash");
        userRepository.save(testUser);

        assertNotNull(testUser.getId(), "User ID không được null");
        String userIdStr = testUser.getId().toHexString();

        // 2. Tạo 2 Project ID giả lập
        ObjectId mockProjectId1 = new ObjectId();
        ObjectId mockProjectId2 = new ObjectId();

        // 3. Thêm 2 project vào User trong DB
        userRepository.addProjectToUser(userIdStr, mockProjectId1.toHexString());
        userRepository.addProjectToUser(userIdStr, mockProjectId2.toHexString());

        // Kiểm tra đã có đủ 2 project trước khi xóa
        Optional<User> userBeforeRemove = userRepository.findById(userIdStr);
        assertTrue(userBeforeRemove.isPresent());
        assertEquals(2, userBeforeRemove.get().getProjectIds().size());

        // 4. Thực thi hàm cần test: Xóa mockProjectId1 khỏi User
        userRepository.removeProjectFromUser(userIdStr, mockProjectId1.toHexString());

        // 5. Kiểm tra kết quả (Assertions)
        Optional<User> updatedUserOpt = userRepository.findById(userIdStr);
        assertTrue(updatedUserOpt.isPresent(), "User phải tồn tại trong DB");

        User updatedUser = updatedUserOpt.get();

        // Đảm bảo danh sách project_ids chỉ còn 1 phần tử
        assertEquals(1, updatedUser.getProjectIds().size(), "Danh sách project_ids phải giảm xuống còn 1");

        // Đảm bảo mockProjectId1 không còn nằm trong danh sách
        assertFalse(updatedUser.getProjectIds().contains(mockProjectId1), "Project ID 1 phải bị loại bỏ");

        // Đảm bảo mockProjectId2 vẫn còn giữ nguyên
        assertTrue(updatedUser.getProjectIds().contains(mockProjectId2), "Project ID 2 vẫn phải tồn tại");
    }

    @AfterAll
    static void tearDown() {
        MongoDatabaseManager.close();
    }
}

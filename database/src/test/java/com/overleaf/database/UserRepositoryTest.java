package com.overleaf.database;

import com.overleaf.common.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M5: Kiểm thử đơn vị (Unit Test) cho UserRepository bằng JUnit 5.
 */
public class UserRepositoryTest {
    private UserRepository userRepository;

    @BeforeEach
    public void setUp() {
        userRepository = new UserRepository();
    }

    @AfterEach
    public void tearDown() {
        userRepository.clear();
    }

    @Test
    public void testSaveAndFindUser() {
        User user = new User("test_id", "david", "passwordHash123", "david@overleaf.edu.vn");
        userRepository.save(user);

        Optional<User> found = userRepository.findByUsername("david");
        assertTrue(found.isPresent(), "Người dùng phải được tìm thấy theo username");
        assertEquals("david@overleaf.edu.vn", found.get().getEmail());
    }

    @Test
    public void testDeleteUser() {
        User user = new User("del_id", "charlie", "pwd", "charlie@test.com");
        userRepository.save(user);

        boolean deleted = userRepository.delete("del_id");
        assertTrue(deleted, "Phải xóa thành công");

        Optional<User> notFound = userRepository.findById("del_id");
        assertFalse(notFound.isPresent(), "Không tìm thấy user sau khi xóa");
    }
}

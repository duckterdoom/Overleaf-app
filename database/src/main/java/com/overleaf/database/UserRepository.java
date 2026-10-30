package com.overleaf.database;

import com.overleaf.common.model.User;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M5: Database - Repository Pattern cho đối tượng Người dùng (User).
 */
public class UserRepository {
    private final Map<String, User> memoryStorage = new ConcurrentHashMap<>();

    public UserRepository() {
        // Tài khoản mẫu ban đầu
        save(new User("u1", "admin", "admin123", "admin@overleaf.edu.vn"));
        save(new User("u2", "alice", "alice123", "alice@overleaf.edu.vn"));
        save(new User("u3", "bob", "bob123", "bob@overleaf.edu.vn"));
    }

    public Optional<User> findById(String id) {
        return Optional.ofNullable(memoryStorage.get(id));
    }

    public Optional<User> findByUsername(String username) {
        return memoryStorage.values().stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    public User save(User user) {
        if (user.getId() == null || user.getId().isEmpty()) {
            user.setId("u_" + UUID.randomUUID().toString().substring(0, 8));
        }
        memoryStorage.put(user.getId(), user);
        return user;
    }

    public boolean delete(String id) {
        return memoryStorage.remove(id) != null;
    }

    public List<User> findAll() {
        return new ArrayList<>(memoryStorage.values());
    }

    public void clear() {
        memoryStorage.clear();
    }
}

package com.overleaf.db.repository;

import com.overleaf.db.model.User;
import java.util.Optional;

public interface UserRepository {

    /**
     * Lưu hoặc cập nhật thông tin User vào Database.
     */
    User save(User user);

    /**
     * Tạo mới User và trả về ID chuỗi (Hex String).
     */
    String createUser(String username, String email, String passwordHash);

    /**
     * Tìm kiếm User theo ID.
     */
    Optional<User> findById(String id);

    /**
     * Tìm kiếm User theo Email.
     */
    Optional<User> findByEmail(String email);

    /**
     * Tìm kiếm User theo Username.
     */
    Optional<User> findByUsername(String username);

    /**
     * Thêm ID của Project vào danh sách project của User.
     */
    void addProjectToUser(String userId, String projectId);

    void removeProjectFromUser(String userId, String projectId);

    /**
     * Xóa User theo ID.
     */
    boolean deleteById(String id);
}
package com.overleaf.db.repository;

import com.overleaf.db.model.Project;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {

    /**
     * Tạo hoặc cập nhật thông tin Project.
     */
    Project save(Project project);

    /**
     * Tìm Project theo ID.
     */
    Optional<Project> findById(String id);

    /**
     * Lấy danh sách tất cả các Project thuộc về 1 User (chủ sở hữu).
     */
    List<Project> findByOwnerId(String ownerId);

    /**
     * Lấy danh sách Project mà User được chia sẻ làm Collaborator.
     */
    List<Project> findByCollaboratorId(String collaboratorId);

    /**
     * Thêm người dùng cộng tác vào Project.
     */
    void addCollaborator(String projectId, String userId);

    /**
     * Xóa Project theo ID.
     */
    boolean deleteById(String id);
}
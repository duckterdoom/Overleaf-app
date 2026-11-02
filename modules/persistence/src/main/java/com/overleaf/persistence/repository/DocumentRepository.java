package com.overleaf.persistence.repository;

import com.overleaf.persistence.model.Document;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    /** Tạo mới hoặc cập nhật thông tin/nội dung Document. */
    Document save(Document document);

    /** Tìm Document theo ID. */
    Optional<Document> findById(String id);

    /** Lấy toàn bộ danh sách tập tin/thư mục thuộc về một Project. */
    List<Document> findByProjectId(String projectId);

    /** Tìm file theo đường dẫn tương đối trong Project (ví dụ: "/main.tex"). */
    Optional<Document> findByProjectIdAndPath(String projectId, String path);

    /** Cập nhật nội dung văn bản cho file (khi người dùng gõ phím trên Editor). */
    boolean updateContent(String documentId, String newContent);

    /** Xóa file theo ID. */
    boolean deleteById(String id);
}

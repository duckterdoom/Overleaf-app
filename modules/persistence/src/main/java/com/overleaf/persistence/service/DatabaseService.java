package com.overleaf.persistence.service;

import com.overleaf.persistence.model.Document;
import com.overleaf.persistence.model.User;
import com.overleaf.persistence.repository.DocumentRepository;
import com.overleaf.persistence.repository.ProjectRepository;
import com.overleaf.persistence.repository.UserRepository;
import com.overleaf.persistence.repository.impl.DocumentRepositoryImpl;
import com.overleaf.persistence.repository.impl.ProjectRepositoryImpl;
import com.overleaf.persistence.repository.impl.UserRepositoryImpl;
import java.util.Optional;

public class DatabaseService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;

    public DatabaseService() {
        this.userRepository = new UserRepositoryImpl();
        this.projectRepository = new ProjectRepositoryImpl();
        this.documentRepository = new DocumentRepositoryImpl();
    }

    // 1. Phục vụ M3: Xác thực đăng nhập
    public Optional<User> authenticate(String email, String passwordHash) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent() && userOpt.get().getPasswordHash().equals(passwordHash)) {
            return userOpt;
        }
        return Optional.empty();
    }

    // 2. Phục vụ M4: Lấy nội dung file ban đầu khi mở Room
    public String loadInitialDocumentContent(String projectId, String path) {
        Optional<Document> docOpt = documentRepository.findByProjectIdAndPath(projectId, path);
        return docOpt.map(Document::getContent).orElse("");
    }

    // 3. Phục vụ M4: Auto-save nội dung văn bản từ RAM xuống DB Atlas
    public boolean saveDocumentContent(String documentId, String newContent) {
        return documentRepository.updateContent(documentId, newContent);
    }

    // Getters nếu M3/M4 muốn dùng riêng lẻ
    public UserRepository getUserRepository() {
        return userRepository;
    }

    public ProjectRepository getProjectRepository() {
        return projectRepository;
    }

    public DocumentRepository getDocumentRepository() {
        return documentRepository;
    }
}

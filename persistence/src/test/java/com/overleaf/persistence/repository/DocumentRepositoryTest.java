package com.overleaf.persistence.repository;

import com.overleaf.persistence.config.MongoDatabaseManager;
import com.overleaf.persistence.model.Document;
import com.overleaf.persistence.model.Project;
import com.overleaf.persistence.repository.impl.DocumentRepositoryImpl;
import com.overleaf.persistence.repository.impl.ProjectRepositoryImpl;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DocumentRepositoryTest {

    private DocumentRepository documentRepository;
    private ProjectRepository projectRepository;

    @BeforeEach
    void setUp() {
        MongoDatabaseManager.getDatabase().getCollection("documents").drop();
        MongoDatabaseManager.getDatabase().getCollection("projects").drop();

        documentRepository = new DocumentRepositoryImpl();
        projectRepository = new ProjectRepositoryImpl();
    }

    @Test
    void testCreateAndUpdateDocument() {
        // 1. Tạo project giả định
        Project project = new Project("Test Project", new ObjectId());
        projectRepository.save(project);

        // 2. Tạo file main.tex
        Document mainTex = new Document(
                project.getId(),
                "main.tex",
                "/main.tex",
                "\\documentclass{article}\n\\begin{document}\nHello World\n\\end{document}",
                false
        );
        documentRepository.save(mainTex);
        assertNotNull(mainTex.getId());

        // 3. Cập nhật nội dung
        String updatedContent = "\\documentclass{article}\n\\begin{document}\nHello Overleaf\n\\end{document}";
        boolean isUpdated = documentRepository.updateContent(mainTex.getId().toHexString(), updatedContent);
        assertTrue(isUpdated);

        // 4. Kiểm tra truy vấn
        Optional<Document> fetchedDoc = documentRepository.findByProjectIdAndPath(
                project.getId().toHexString(),
                "/main.tex"
        );
        assertTrue(fetchedDoc.isPresent());
        assertEquals(updatedContent, fetchedDoc.get().getContent());

        List<Document> docsInProject = documentRepository.findByProjectId(project.getId().toHexString());
        assertEquals(1, docsInProject.size());
    }

    @AfterAll
    static void tearDown() {
        MongoDatabaseManager.close();
    }
}

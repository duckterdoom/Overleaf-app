package com.overleaf.client.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CompileWorkspaceTest {
    @TempDir Path root;

    @Test void includeAuxiliaryFoldersAreCreatedWithoutRecursingIntoBuildOrHiddenFolders() throws Exception {
        Path project = Files.createDirectories(root.resolve("project/chapters/part-one")).getParent().getParent();
        Files.createDirectories(project.resolve("appendix"));
        Files.createDirectories(project.resolve(".git/objects"));
        Files.createDirectories(project.resolve("build/existing-cache"));
        Files.writeString(project.resolve("chapters/part-one/a.tex"), "source");
        CompileWorkspace.prepareOutputDirectories(project);
        assertTrue(Files.isDirectory(project.resolve("build/chapters/part-one")));
        assertTrue(Files.isDirectory(project.resolve("build/appendix")));
        assertFalse(Files.exists(project.resolve("build/build")));
        assertFalse(Files.exists(project.resolve("build/.git")));
        assertEquals("source", Files.readString(project.resolve("chapters/part-one/a.tex")));
    }

    @Test void unicodeProjectPublishesPdfWithoutChangingSource() throws Exception {
        Path project = Files.createDirectories(root.resolve("Luận văn"));
        Files.writeString(project.resolve("main.tex"), "original source");
        Files.createDirectories(project.resolve("build"));
        Path working;
        try (var workspace = CompileWorkspace.prepare(project)) {
            working = workspace.directory();
            assertEquals("original source", Files.readString(working.resolve("main.tex")));
            Files.writeString(working.resolve("build/main.pdf"), "verified PDF");
            assertEquals(project.resolve("build/main.pdf"), workspace.publishPdf("main.pdf"));
        }
        assertEquals("original source", Files.readString(project.resolve("main.tex")));
        assertEquals("verified PDF", Files.readString(project.resolve("build/main.pdf")));
        if (!working.equals(project)) assertFalse(Files.exists(working));
    }

    @Test void unsuccessfulStagedBuildKeepsExistingPdf() throws Exception {
        Path project = Files.createDirectories(root.resolve("Tiếng Việt/build")).getParent();
        Files.writeString(project.resolve("main.tex"), "source");
        Files.writeString(project.resolve("build/main.pdf"), "last good PDF");
        try (var workspace = CompileWorkspace.prepare(project)) {
            Files.writeString(workspace.directory().resolve("build/main.log"), "diagnostic");
        }
        assertEquals("last good PDF", Files.readString(project.resolve("build/main.pdf")));
        assertEquals("diagnostic", Files.readString(project.resolve("build/main.log")));
    }
}

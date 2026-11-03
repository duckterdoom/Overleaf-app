package com.overleaf.client.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompileWorkspaceTest {
    @TempDir Path root;

    @Test
    void syncIndexSurvivesUnicodeStagingWithRelativeSourcesAndUnixNewlines() throws Exception {
        Path project = Files.createDirectories(root.resolve("Đồng bộ/build")).getParent();
        Files.writeString(project.resolve("main.tex"), "source");
        try (var workspace = CompileWorkspace.prepare(project)) {
            Files.createDirectories(workspace.directory().resolve("chapters"));
            Files.writeString(workspace.directory().resolve("build/main.pdf"), "PDF");
            try (var gzip =
                    new java.util.zip.GZIPOutputStream(
                            Files.newOutputStream(
                                    workspace.directory().resolve("build/main.synctex.gz")))) {
                gzip.write(
                        ("SyncTeX Version:1\nInput:1:"
                                        + workspace.directory().resolve("main.tex")
                                        + "\nInput:2:\""
                                        + workspace.directory().resolve("chapters/part one.tex")
                                        + "\"\nContent:\n")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            workspace.publishPdf("main.pdf");
        }
        try (var gzip =
                new java.util.zip.GZIPInputStream(
                        Files.newInputStream(project.resolve("build/main.synctex.gz")))) {
            String index = new String(gzip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(index.contains("Input:1:./main.tex\n"));
            assertTrue(index.contains("Input:2:./chapters/part one.tex\n"));
            assertFalse(index.contains("\r"));
        }
    }

    @Test
    void successfulBuildWithoutSyncDataDropsPreviousIndex() throws Exception {
        Path project = Files.createDirectories(root.resolve("No index/build")).getParent();
        Files.writeString(project.resolve("main.tex"), "source");
        try (var workspace = CompileWorkspace.prepare(project)) {
            Files.writeString(project.resolve("build/main.synctex.gz"), "old index");
            Files.deleteIfExists(workspace.directory().resolve("build/main.synctex.gz"));
            Files.writeString(workspace.directory().resolve("build/main.pdf"), "PDF");
            workspace.publishPdf("main.pdf");
            assertFalse(Files.exists(project.resolve("build/main.synctex.gz")));
        }
    }

    @Test
    void publishedPdfHasItsFinalTexLogAvailableBeforeClosingStaging() throws Exception {
        Path project = Files.createDirectories(root.resolve("Nhật ký/build")).getParent();
        Files.writeString(project.resolve("main.tex"), "source");
        try (var workspace = CompileWorkspace.prepare(project)) {
            Files.writeString(workspace.directory().resolve("build/main.pdf"), "PDF bytes");
            Files.writeString(workspace.directory().resolve("build/main.log"), "final diagnostics");
            workspace.publishPdf("main.pdf");
            assertEquals("final diagnostics", Files.readString(project.resolve("build/main.log")));
        }
    }

    @Test
    void includeAuxiliaryFoldersAreCreatedWithoutRecursingIntoBuildOrHiddenFolders()
            throws Exception {
        Path project =
                Files.createDirectories(root.resolve("project/chapters/part-one"))
                        .getParent()
                        .getParent();
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

    @Test
    void unicodeProjectPublishesPdfWithoutChangingSource() throws Exception {
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

    @Test
    void unsuccessfulStagedBuildKeepsExistingPdf() throws Exception {
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

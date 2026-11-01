package com.overleaf.client.workspace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceFilesTest {
    @TempDir Path home;

    private Path root() throws Exception {
        return Files.createDirectories(home.resolve("project"));
    }

    @Test
    void nestedFoldersAndFilesRetainUnicodeAndEmptyDirectories() throws Exception {
        WorkspaceFiles files = new WorkspaceFiles(root());
        files.createFolder("Chương một/hình ảnh");
        files.createFile("Chương một/giới thiệu.tex");
        assertEquals(List.of("", "Chương một", "Chương một/hình ảnh"), files.directories());
        assertTrue(Files.isRegularFile(root().resolve("Chương một/giới thiệu.tex")));
    }

    @Test
    void invalidAndEscapingDestinationsAreRejectedBeforeWriting() throws Exception {
        WorkspaceFiles files = new WorkspaceFiles(root());
        for (String name :
                List.of(
                        "",
                        "../escape.tex",
                        "/escape",
                        "dir/../escape",
                        "C:/escape",
                        "a\\b.tex",
                        "CON.txt",
                        "a./b",
                        "a:b",
                        ".git/config",
                        "build/main.pdf",
                        "a//b")) {
            assertThrows(WorkspaceFiles.Failure.class, () -> files.createFile(name), name);
        }
        assertFalse(Files.exists(home.resolve("escape.tex")));
    }

    @Test
    void creatingExistingFileOrFolderNeverOverwrites() throws Exception {
        Path root = root();
        WorkspaceFiles files = new WorkspaceFiles(root);
        files.createFile("main.tex");
        Files.writeString(root.resolve("main.tex"), "keep");
        assertThrows(WorkspaceFiles.Failure.class, () -> files.createFile("main.tex"));
        files.createFolder("chapters");
        assertThrows(WorkspaceFiles.Failure.class, () -> files.createFolder("chapters"));
        assertEquals("keep", Files.readString(root.resolve("main.tex")));
    }

    @Test
    void folderUploadKeepsHierarchyEmptyFoldersAndBinaryBytes() throws Exception {
        Path root = root();
        WorkspaceFiles files = new WorkspaceFiles(root);
        Path source =
                Files.createDirectories(home.resolve("upload/images/empty"))
                        .getParent()
                        .getParent();
        Files.write(source.resolve("images/plot.png"), new byte[] {0, 1, -1, 5});
        files.importPaths(List.of(source), "assets");
        assertArrayEquals(
                new byte[] {0, 1, -1, 5},
                Files.readAllBytes(root.resolve("assets/upload/images/plot.png")));
        assertTrue(Files.isDirectory(root.resolve("assets/upload/images/empty")));
        try (var list = Files.list(root)) {
            assertFalse(
                    list.anyMatch(p -> p.getFileName().toString().startsWith(".overleaf-upload-")));
        }
    }

    @Test
    void conflictingBatchDoesNotPublishEarlierFiles() throws Exception {
        Path root = root();
        WorkspaceFiles files = new WorkspaceFiles(root);
        Path a = Files.writeString(home.resolve("a.tex"), "new");
        Path b = Files.writeString(home.resolve("b.tex"), "other");
        Files.writeString(root.resolve("b.tex"), "original");
        assertThrows(WorkspaceFiles.Failure.class, () -> files.importPaths(List.of(a, b), ""));
        assertFalse(Files.exists(root.resolve("a.tex")));
        assertEquals("original", Files.readString(root.resolve("b.tex")));
    }

    @Test
    void batchCaseDuplicatesAreRejected() throws Exception {
        WorkspaceFiles files = new WorkspaceFiles(root());
        Path source = Files.writeString(home.resolve("source"), "text");
        assertThrows(
                WorkspaceFiles.Failure.class,
                () ->
                        files.importFiles(
                                List.of(
                                        new WorkspaceFiles.Addition(source, "Main.tex"),
                                        new WorkspaceFiles.Addition(source, "MAIN.tex"))));
    }

    @Test
    void copyFromAnotherProjectCanRenameOutputAndPreservesSource() throws Exception {
        Path root = root();
        WorkspaceFiles files = new WorkspaceFiles(root);
        Path source = Files.createDirectories(home.resolve("other/build")).resolve("main.pdf");
        Files.write(source, new byte[] {9, 8, 7});
        files.importFiles(List.of(new WorkspaceFiles.Addition(source, "figures/reference.pdf")));
        assertArrayEquals(
                Files.readAllBytes(source),
                Files.readAllBytes(root.resolve("figures/reference.pdf")));
    }

    @Test
    void copyingContainingFolderIntoProjectIsRejected() throws Exception {
        WorkspaceFiles files = new WorkspaceFiles(root());
        assertThrows(WorkspaceFiles.Failure.class, () -> files.importPaths(List.of(home), ""));
    }

    @Test
    void failureDuringPublishRollsBackOnlyNewFilesAndFolders() throws Exception {
        Path root = root();
        WorkspaceFiles files = new WorkspaceFiles(root);
        Path source = Files.writeString(home.resolve("source.tex"), "data");
        Files.writeString(root.resolve("blocked"), "keep this file");
        assertThrows(
                java.io.IOException.class,
                () ->
                        files.importFiles(
                                List.of(
                                        new WorkspaceFiles.Addition(source, "new/one.tex"),
                                        new WorkspaceFiles.Addition(source, "blocked/two.tex"))));
        assertFalse(Files.exists(root.resolve("new")));
        assertEquals("keep this file", Files.readString(root.resolve("blocked")));
    }

    @Test
    void uploadCountIsBounded() throws Exception {
        WorkspaceFiles files = new WorkspaceFiles(root());
        Path source = Files.createDirectory(home.resolve("large"));
        for (int i = 0; i < 10_000; i++) Files.createFile(source.resolve("f" + i));
        assertEquals(
                WorkspaceFiles.Problem.LIMIT,
                assertThrows(
                                WorkspaceFiles.Failure.class,
                                () -> files.importPaths(List.of(source), ""))
                        .problem());
        assertFalse(Files.exists(root().resolve("large")));
    }

    @Test
    void urlRequiresHttpsAndNeverCreatesDestinationOnFailure() throws Exception {
        Path root = root();
        WorkspaceFiles files = new WorkspaceFiles(root);
        for (String url :
                List.of(
                        "file:///etc/passwd",
                        "http://example.test/a",
                        "https://user:pass@example.test/a",
                        "invalid"))
            assertEquals(
                    WorkspaceFiles.Problem.URL,
                    assertThrows(WorkspaceFiles.Failure.class, () -> files.download(url, "a.tex"))
                            .problem());
        assertFalse(Files.exists(root.resolve("a.tex")));
    }

    @Test
    void cancelledUploadLeavesNoPublishedEntries() throws Exception {
        Path root = root();
        WorkspaceFiles files = new WorkspaceFiles(root);
        Path source = Files.writeString(home.resolve("source"), "data");
        Thread.currentThread().interrupt();
        try {
            assertEquals(
                    WorkspaceFiles.Problem.CANCELLED,
                    assertThrows(
                                    WorkspaceFiles.Failure.class,
                                    () -> files.importPaths(List.of(source), ""))
                            .problem());
        } finally {
            Thread.interrupted();
        }
        assertFalse(Files.exists(root.resolve("source")));
    }
}

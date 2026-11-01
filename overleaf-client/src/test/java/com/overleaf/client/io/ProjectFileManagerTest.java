package com.overleaf.client.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFileManagerTest {

    @TempDir
    Path tempDir;

    private ProjectFileManager manager;

    @BeforeEach
    void setUp() {
        manager = new ProjectFileManager(tempDir.toString());
    }

    @Test
    void writeThenReadRoundTripKeepsUnicode() throws IOException {
        String content = "\\section{Xin chào} Tiếng Việt có dấu";
        manager.writeTexFile("main.tex", content);
        assertEquals(content, manager.readTexFile("main.tex"));
    }

    @Test
    void readMissingFileReturnsEmptyString() throws IOException {
        assertEquals("", manager.readTexFile("nope.tex"));
    }

    @Test
    void writeCreatesSubdirectoriesAndLeavesNoTempFile() throws IOException {
        manager.writeTexFile("chapters/intro.tex", "hello");
        assertTrue(Files.isRegularFile(tempDir.resolve("chapters/intro.tex")));
        assertFalse(Files.exists(tempDir.resolve("chapters/intro.tex.tmp")));
    }

    @Test
    void pathTraversalIsRejected() {
        assertThrows(IOException.class, () -> manager.readTexFile("../secret.tex"));
        assertThrows(IOException.class, () -> manager.writeTexFile("../../evil.tex", "x"));
        assertThrows(IOException.class, () -> manager.deleteFile("../x.tex"));
    }

    @Test
    void isOwnWriteDetectsOnlyOwnContent() throws IOException {
        manager.writeTexFile("main.tex", "mine");
        assertTrue(manager.isOwnWrite("main.tex"));

        Files.writeString(tempDir.resolve("main.tex"), "changed by another editor");
        assertFalse(manager.isOwnWrite("main.tex"));
    }

    @Test
    void listTexFilesIgnoresBuildDirAndOtherExtensions() throws IOException {
        manager.writeTexFile("main.tex", "a");
        manager.writeTexFile("chapters/one.tex", "b");
        Files.createDirectories(tempDir.resolve("build"));
        Files.writeString(tempDir.resolve("build/generated.tex"), "c");
        Files.writeString(tempDir.resolve("refs.bib"), "d");

        List<String> files = manager.listTexFiles();
        assertEquals(List.of("chapters/one.tex", "main.tex"), files);
    }

    @Test
    void renameAndDelete() throws IOException {
        manager.writeTexFile("a.tex", "content");
        manager.renameFile("a.tex", "b.tex");
        assertEquals("", manager.readTexFile("a.tex"));
        assertEquals("content", manager.readTexFile("b.tex"));

        manager.deleteFile("b.tex");
        assertFalse(Files.exists(tempDir.resolve("b.tex")));
    }

    @Test
    void pdfPathFollowsBuildDirConvention() {
        assertEquals(tempDir.resolve("build").resolve("main.pdf"), manager.pdfPathFor("main.tex"));
    }

    @Test
    void tc03_writeTexFile_OverwritesExistingFileAtomically() throws IOException {
        // Ghi nội dung ban đầu vào file
        manager.writeTexFile("article.tex", "Old Content");
        assertEquals("Old Content", manager.readTexFile("article.tex"));

        // Ghi đè bằng nội dung mới
        manager.writeTexFile("article.tex", "New Updated Content");

        // Kiểm tra nội dung đã được cập nhật chính xác
        assertEquals("New Updated Content", manager.readTexFile("article.tex"));

        // Kiểm tra chắc chắn không còn file tạm (.tmp) nào sót lại
        assertFalse(Files.exists(tempDir.resolve("article.tex.tmp")));
    }
}
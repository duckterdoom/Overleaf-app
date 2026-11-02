package com.overleaf.client.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.overleaf.client.compiler.ProjectInputs;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectHistoryTest {
    @TempDir Path root;

    @Test
    void writesPreservePreviousContentAndUnicodeAcrossRestart() throws Exception {
        Files.writeString(root.resolve("main.tex"), "Bản ban đầu");
        ProjectFileManager files = new ProjectFileManager(root.toString());
        files.writeTexFile("main.tex", "Bản mới", "Test User", "Edited");
        ProjectHistory history = new ProjectHistory(root);
        var versions = history.revisions();
        assertEquals(2, versions.size());
        assertEquals("Bản mới", history.read(versions.getFirst()));
        assertEquals("Bản ban đầu", history.read(versions.getLast()));
        assertEquals("Test User", versions.getFirst().author());
    }

    @Test
    void identicalSavesAreDeduplicatedButDifferentFilesRemainSeparate() throws Exception {
        ProjectHistory history = new ProjectHistory(root);
        history.capture("main.tex", "same", "A", "Edited");
        history.capture("main.tex", "same", "B", "Edited");
        history.capture("chapters/a.tex", "same", "B", "Edited");
        assertEquals(2, history.revisions().size());
    }

    @Test
    void idleCheckpointDoesNotCreateFalseRollbackVersionsWhenSaving() throws Exception {
        Files.writeString(root.resolve("main.tex"), "old");
        ProjectHistory history = new ProjectHistory(root);
        history.capture("main.tex", "old", "A", "Opened");
        history.capture("main.tex", "new unsaved", "A", "Edited");
        new ProjectFileManager(root.toString())
                .writeTexFile("main.tex", "new unsaved", "A", "Edited");
        assertEquals(2, history.revisions().size());
        assertEquals("new unsaved", history.read(history.revisions().getFirst()));
    }

    @Test
    void labelsPersistWithoutChangingCompileFingerprint() throws Exception {
        Files.writeString(root.resolve("main.tex"), "source");
        String before = ProjectInputs.fingerprint(root);
        ProjectHistory history = new ProjectHistory(root);
        history.capture("main.tex", "source", "A", "Edited");
        var revision = history.revisions().getFirst();
        history.label(revision, "Bản nộp");
        assertEquals("Bản nộp", new ProjectHistory(root).revisions().getFirst().label());
        assertEquals(before, ProjectInputs.fingerprint(root));
    }

    @Test
    void corruptContentIsNeverRestoredAsAValidRevision() throws Exception {
        ProjectHistory history = new ProjectHistory(root);
        history.capture("main.tex", "source", "A", "Edited");
        var revision = history.revisions().getFirst();
        Files.writeString(
                root.resolve(ProjectHistory.DIRECTORY)
                        .resolve(revision.id())
                        .resolve("content.txt"),
                "tampered");
        assertThrows(java.io.IOException.class, () -> history.read(revision));
    }

    @Test
    void untrustedMetadataPathsAndIdsAreRejected() throws Exception {
        ProjectHistory history = new ProjectHistory(root);
        for (String path :
                java.util.List.of(
                        "../outside.tex",
                        "/absolute.tex",
                        ".git/config",
                        "build/main.tex",
                        "a\\b.tex",
                        "C:/other.tex"))
            assertThrows(
                    java.io.IOException.class,
                    () -> history.capture(path, "source", "A", "Edited"));
        var revision =
                new ProjectHistory.Revision(
                        "../../outside", "main.tex", java.time.Instant.now(), "A", "Edited", "", 0);
        assertThrows(java.io.IOException.class, () -> history.read(revision));
    }

    @Test
    void oversizedRevisionDoesNotPublishAnEntry() throws Exception {
        ProjectHistory history = new ProjectHistory(root);
        assertThrows(
                java.io.IOException.class,
                () -> history.capture("main.tex", "x".repeat(2 * 1024 * 1024 + 1), "A", "Edited"));
        assertTrue(history.revisions().isEmpty());
    }

    @Test
    void historyFollowsTheProjectWhenItsFolderMoves() throws Exception {
        Path project = Files.createDirectory(root.resolve("project"));
        ProjectHistory history = new ProjectHistory(project);
        history.capture("main.tex", "saved", "A", "Edited");
        Path moved = Files.move(project, root.resolve("moved"));
        ProjectHistory reopened = new ProjectHistory(moved);
        assertEquals("saved", reopened.read(reopened.revisions().getFirst()));
    }

    @Test
    void failedSourceWriteDoesNotRecordTheUnsavedNewText() throws Exception {
        ProjectFileManager files = new ProjectFileManager(root.toString());
        Files.createDirectory(root.resolve("main.tex"));
        assertThrows(
                java.io.IOException.class, () -> files.writeTexFile("main.tex", "must not appear"));
        assertTrue(new ProjectHistory(root).revisions().isEmpty());
    }
}

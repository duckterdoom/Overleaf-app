package com.overleaf.client.ui.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ProjectWorkflowTest {
    @TempDir Path root;
    private LocalBackend backend() { return new LocalBackend(root.resolve("home")); }
    private NewProjectRequest request(String name, Path parent, NewProjectRequest.Kind kind, Path source) {
        return new NewProjectRequest(name, "book", "#23664A", parent, kind, source, null, null, true);
    }
    private Path zip(String name, String... entries) throws Exception {
        Path archive = root.resolve(name + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int i = 0; i < entries.length; i += 2) {
                out.putNextEntry(new ZipEntry(entries[i]));
                out.write(entries[i + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.closeEntry();
            }
        }
        return archive;
    }

    @Test void customLocationIsRememberedAcrossRestarts() throws Exception {
        LocalBackend backend = backend();
        var created = backend.create(request("Luận văn", root.resolve("custom location"), NewProjectRequest.Kind.BLANK, null));
        assertEquals(root.resolve("custom location/Luận văn").toString(), created.folder());
        assertTrue(Files.readString(Path.of(created.folder(), "main.tex")).contains("Giới thiệu"));
        assertEquals(created.folder(), backend().projects().get(0).folder());
        assertEquals("book", backend().projects().get(0).icon());
    }

    @Test void changingDefaultKeepsOldProjectsAndUsesNewFolderForNewProjects() {
        LocalBackend backend = backend();
        var old = backend.create("Old");
        backend.setProjectsDirectory(root.resolve("new root"));
        var next = backend.create("Next");
        assertEquals(root.resolve("new root/Next").toString(), next.folder());
        assertTrue(Files.isDirectory(Path.of(old.folder())));
        assertEquals(2, backend().projects().size());
        assertEquals(root.resolve("new root"), backend().projectsDirectory());
    }

    @Test void moveKeepsFilesBackupCatalogAndPreferredIdentity() throws Exception {
        LocalBackend backend = backend();
        var project = backend.create("Thesis");
        Path old = Path.of(project.folder());
        Files.createDirectory(old.resolve("images"));
        Files.write(old.resolve("images/plot.bin"), new byte[]{1,2,3,4});
        backend.rememberProject(old);
        var moved = backend.move(project, root.resolve("destination"));
        assertFalse(Files.exists(old));
        assertArrayEquals(new byte[]{1,2,3,4}, Files.readAllBytes(Path.of(moved.folder(), "images/plot.bin")));
        try (var backup = Files.walk(old.getParent().resolve(".overleaf-moved-backups"))) {
            assertTrue(backup.anyMatch(p -> p.getFileName().toString().equals("plot.bin")));
        }
        assertEquals(1, backend().projects().size());
        assertEquals(moved.folder(), backend().preferredProjectFolder());
    }

    @Test void moveRejectsCollisionAndDescendantWithoutTouchingOriginal() throws Exception {
        LocalBackend backend = backend();
        var project = backend.create("Thesis");
        Path old = Path.of(project.folder());
        Path parent = Files.createDirectory(root.resolve("collision"));
        Files.createDirectory(parent.resolve("Thesis"));
        assertEquals(ProjectFailure.Code.EXISTS, assertThrows(ProjectFailure.class, () -> backend.move(project, parent)).code());
        assertThrows(ProjectFailure.class, () -> backend.move(project, old.resolve("inside")));
        assertTrue(Files.isRegularFile(old.resolve("main.tex")));
        assertFalse(Files.exists(old.resolve("inside")));
    }

    @Test void externalProjectCanBeRenamedAndDeletedRecoverably() throws Exception {
        LocalBackend backend = backend();
        var created = backend.create(request("First", root.resolve("external"), NewProjectRequest.Kind.BLANK, null));
        var renamed = backend.rename(created, "Second");
        assertEquals(root.resolve("external/Second").toString(), renamed.folder());
        backend.delete(renamed);
        assertTrue(backend().projects().isEmpty());
        assertFalse(Files.exists(Path.of(renamed.folder())));
        try (var files = Files.walk(backend.trashDir())) { assertTrue(files.anyMatch(p -> p.getFileName().toString().equals("main.tex"))); }
    }

    @Test void sameNamesInDifferentLocationsRemainDistinct() {
        LocalBackend backend = backend();
        var one = backend.create(request("Same", root.resolve("one"), NewProjectRequest.Kind.BLANK, null));
        var two = backend.create(request("Same", root.resolve("two"), NewProjectRequest.Kind.BLANK, null));
        backend.rememberProject(Path.of(two.folder()));
        assertEquals(2, backend().projects().size());
        assertEquals(two.folder(), backend().preferredProjectFolder());
        assertNotEquals(one.folder(), two.folder());
    }

    @Test void zipWrapperIsFlattenedAndAlternativeRootIsCopiedToMain() throws Exception {
        LocalBackend backend = backend();
        Path zip = zip("valid", "paper/report.tex", "\\documentclass{article}\n\\begin{document}Xin chào\\end{document}", "paper/img/data.txt", "asset");
        var created = backend.create(request("Imported", null, NewProjectRequest.Kind.ZIP, zip));
        Path dir = Path.of(created.folder());
        assertEquals(Files.readString(dir.resolve("report.tex")), Files.readString(dir.resolve("main.tex")));
        assertEquals("asset", Files.readString(dir.resolve("img/data.txt")));
    }

    @Test void archiveTraversalAbsoluteAdsAndReservedPathsAreRejected() throws Exception {
        LocalBackend backend = backend();
        int index = 0;
        for (String path : List.of("../escape.tex", "/absolute.tex", "C:/escape.tex", "main.tex:stream", "CON.txt", "dir./a.tex")) {
            Path archive = zip("bad" + index++, path, "bad");
            assertThrows(ProjectFailure.class, () -> backend.create(request("Bad", null, NewProjectRequest.Kind.ZIP, archive)));
        }
        assertTrue(backend.projects().isEmpty());
        assertFalse(Files.exists(root.resolve("escape.tex")));
    }

    @Test void caseDuplicateAndNonLatexArchivesNeverPublishProjects() throws Exception {
        LocalBackend backend = backend();
        for (Path archive : List.of(zip("duplicate", "main.tex", "one", "MAIN.tex", "two"), zip("emptydoc", "readme.md", "text"))) {
            assertThrows(ProjectFailure.class, () -> backend.create(request("Bad", null, NewProjectRequest.Kind.ZIP, archive)));
        }
        assertTrue(backend.projects().isEmpty());
    }

    @Test void archiveFileCountIsBounded() throws Exception {
        Path archive = root.resolve("many.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int i = 0; i < 10_001; i++) { out.putNextEntry(new ZipEntry("f" + i + ".txt")); out.closeEntry(); }
        }
        LocalBackend backend = backend();
        assertEquals(ProjectFailure.Code.LIMIT, assertThrows(ProjectFailure.class,
                () -> backend.create(request("Large", null, NewProjectRequest.Kind.ZIP, archive))).code());
        assertTrue(backend.projects().isEmpty());
    }

    @Test void githubUrlAcceptsOnlyCredentialFreePublicHttpsRepositoryAddresses() {
        assertEquals("https://github.com/owner/repo.git", ProjectImports.githubUrl("https://github.com/owner/repo.git/"));
        for (String bad : List.of("file:///tmp/repo", "https://evil.test/owner/repo", "https://token@github.com/owner/repo",
                "https://github.com/owner/repo?token=secret", "https://github.com/owner/repo/tree/main", "--upload-pack=bad")) {
            assertEquals(ProjectFailure.Code.REPOSITORY, assertThrows(ProjectFailure.class, () -> ProjectImports.githubUrl(bad)).code());
        }
    }

    @Test void affiliationEscapesLatexAndErrorMessagesAreBilingual() throws Exception {
        LocalBackend backend = backend();
        backend.setAffiliation("Trường A & B");
        var project = backend.create(request("Report", null, NewProjectRequest.Kind.BLANK, null));
        assertTrue(Files.readString(Path.of(project.folder(), "main.tex")).contains("Trường A \\& B"));
        for (var code : ProjectFailure.Code.values()) {
            var failure = new ProjectFailure(code);
            assertFalse(failure.localized(true).isBlank());
            assertNotEquals(failure.localized(true), failure.localized(false));
        }
    }
}

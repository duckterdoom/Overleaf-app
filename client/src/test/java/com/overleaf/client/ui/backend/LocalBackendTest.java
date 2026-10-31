package com.overleaf.client.ui.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class LocalBackendTest {

    @TempDir
    Path home;

    @Test
    void registerThenSignInStoresOnlyASaltedHash() throws Exception {
        LocalBackend backend = new LocalBackend(home);
        AuthGateway.Session created = backend.register("Ada", "Ada@Example.edu", "analytical", true).join();
        assertEquals("ada@example.edu", created.email());
        assertFalse(created.offline());

        String stored = Files.readString(home.resolve(".app/accounts.properties"));
        assertFalse(stored.contains("analytical"), "password must not be stored");

        AuthGateway.Session again = backend.signIn(" ADA@example.edu ", "analytical", false).join();
        assertEquals("Ada", again.name());
    }

    @Test
    void wrongPasswordUnknownEmailAndDuplicateAreRejected() {
        LocalBackend backend = new LocalBackend(home);
        backend.register("Ada", "ada@example.edu", "analytical", false).join();

        assertMessage(() -> backend.signIn("ada@example.edu", "wrong-one", false).join(), "not correct");
        assertMessage(() -> backend.signIn("bob@example.edu", "whatever", false).join(), "No account");
        assertMessage(() -> backend.register("Ada 2", "ada@example.edu", "another1", false).join(), "already exists");
        assertMessage(() -> backend.register("Short", "s@example.edu", "123", false).join(), "at least 6");
    }

    @Test
    void rememberRestoresSessionAndForgetClearsIt() {
        LocalBackend backend = new LocalBackend(home);
        backend.register("Ada", "ada@example.edu", "analytical", true).join();
        assertEquals("ada@example.edu", new LocalBackend(home).restoreSession().email());

        backend.forgetSession();
        assertNull(new LocalBackend(home).restoreSession());
        assertEquals("ada@example.edu", backend.lastEmail(), "email is still offered on the login form");

        backend.signIn("ada@example.edu", "analytical", false).join();
        assertNull(backend.restoreSession(), "unchecked 'keep me signed in' must not remember");
    }

    @Test
    void projectsAreRealFoldersWithMainTex() throws Exception {
        LocalBackend backend = new LocalBackend(home);
        assertTrue(backend.projects().isEmpty());
        backend.ensureStarterProject();
        assertEquals(1, backend.projects().size());

        ProjectGateway.ProjectSummary thesis = backend.create("Thesis: Chapter 3");
        assertEquals("Thesis- Chapter 3", thesis.name(), "characters invalid in folder names are replaced");
        Path main = Path.of(thesis.folder()).resolve("main.tex");
        assertTrue(Files.readString(main).contains("\\title{Thesis- Chapter 3}"));
        assertEquals(1, thesis.files());

        ProjectGateway.ProjectSummary duplicate = backend.create("Thesis: Chapter 3");
        assertEquals("Thesis- Chapter 3 (2)", duplicate.name());

        List<ProjectGateway.ProjectSummary> listed = backend.projects();
        assertEquals(3, listed.size());
        assertEquals("Edited just now", listed.get(0).modified());
    }

    @Test
    void renameAndDeleteKeepFilesRecoverable() throws Exception {
        LocalBackend backend = new LocalBackend(home);
        ProjectGateway.ProjectSummary paper = backend.create("Paper");
        backend.rememberProject("Paper");
        backend.create("Other");

        assertMessage(() -> backend.rename(paper, "Other"), "already exists");
        ProjectGateway.ProjectSummary renamed = backend.rename(paper, "Final Paper");
        assertTrue(Files.isRegularFile(Path.of(renamed.folder()).resolve("main.tex")));
        assertFalse(Files.exists(Path.of(paper.folder())));
        assertEquals("Final Paper", backend.lastProject(), "last-opened project follows the rename");

        backend.delete(renamed);
        assertFalse(Files.exists(Path.of(renamed.folder())));
        try (var trash = Files.list(backend.trashDir())) {
            Path kept = trash.findFirst().orElseThrow();
            assertTrue(Files.isRegularFile(kept.resolve("main.tex")));
        }
        assertEquals(1, backend.projects().size());
    }

    @Test
    void activityCountsEditsPerDayAndCompilesPerYear() {
        LocalBackend backend = new LocalBackend(home);
        backend.recordEdits(2);
        backend.recordEdits(1);
        backend.recordCompile();
        backend.recordEdits(0);

        LocalBackend reloaded = new LocalBackend(home);
        LocalDate today = LocalDate.now();
        assertEquals(3, reloaded.editsByDay(today.minusDays(7), today).get(today));
        assertEquals(1, reloaded.compilesThisYear());
        assertTrue(reloaded.editsByDay(today.minusDays(30), today.minusDays(1)).isEmpty());
    }

    @Test
    void modifiedPhrasesMatchHomeViewTranslations() {
        Instant now = Instant.now();
        assertEquals("Edited just now", LocalBackend.describeModified(now));
        assertEquals("Edited 5 minutes ago", LocalBackend.describeModified(now.minus(5, ChronoUnit.MINUTES)));
        assertEquals("Edited 1 hour ago", LocalBackend.describeModified(now.minus(61, ChronoUnit.MINUTES)));
        assertEquals("Edited 3 days ago", LocalBackend.describeModified(now.minus(3, ChronoUnit.DAYS)));
    }

    @Test
    void profilePictureIsStoredPerAccountAndRemovable() throws Exception {
        LocalBackend backend = new LocalBackend(home);
        AuthGateway.Session ada = new AuthGateway.Session("Ada", "ada@example.edu", false);
        AuthGateway.Session bob = new AuthGateway.Session("Bob", "bob@example.edu", false);
        Path png = home.resolve("me.PNG");
        Files.write(png, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        assertTrue(backend.avatar(ada).isEmpty());
        backend.setAvatar(ada, png);
        Path stored = backend.avatar(ada).orElseThrow();
        assertTrue(stored.getFileName().toString().endsWith(".png"));
        assertTrue(backend.avatar(bob).isEmpty(), "pictures are per account");
        assertTrue(new LocalBackend(home).avatar(new AuthGateway.Session("Ada", "ADA@example.edu", false)).isPresent());

        Path jpg = home.resolve("new.jpeg");
        Files.write(jpg, new byte[]{1, 2, 3});
        backend.setAvatar(ada, jpg);
        assertTrue(backend.avatar(ada).orElseThrow().toString().endsWith(".jpg"), "replacing keeps one file");
        assertFalse(Files.exists(stored));

        assertMessage(() -> backend.setAvatar(ada, home.resolve("notes.txt")), "PNG, JPG or GIF");
        Path huge = home.resolve("huge.png");
        Files.write(huge, new byte[(int) ProfileGateway.MAX_AVATAR_BYTES + 1]);
        assertMessage(() -> backend.setAvatar(ada, huge), "under 5 MB");

        backend.removeAvatar(ada);
        assertTrue(backend.avatar(ada).isEmpty());
    }

    @Test
    void vietnameseNamesSurviveStorageAndWindowsUnsafeNamesAreFixed() throws Exception {
        LocalBackend backend = new LocalBackend(home);
        backend.register("Nguyễn Văn Lợi", "loi@example.vn", "mậtkhẩu123", true).join();
        LocalBackend reloaded = new LocalBackend(home);
        assertEquals("Nguyễn Văn Lợi", reloaded.restoreSession().name());
        assertEquals("Nguyễn Văn Lợi", reloaded.signIn("loi@example.vn", "mậtkhẩu123", false).join().name());

        ProjectGateway.ProjectSummary thesis = backend.create("Luận văn tốt nghiệp");
        assertEquals("Luận văn tốt nghiệp", thesis.name());
        assertTrue(Files.readString(Path.of(thesis.folder()).resolve("main.tex")).contains("\\title{Luận văn tốt nghiệp}"));
        assertTrue(backend.projects().stream().anyMatch(p -> p.name().equals("Luận văn tốt nghiệp")));

        assertEquals("con project", backend.create("con").name(), "reserved Windows device name");
        assertEquals("Draft", backend.create("Draft. . ").name());
    }

    @Test
    void templateCompilesVietnameseWithEveryEngine() {
        String template = LocalBackend.template("Tiếng Việt");
        assertTrue(template.contains("\\usepackage[T5]{fontenc}"), "pdfLaTeX needs T5 fonts for Vietnamese");
        assertTrue(template.contains("\\usepackage{fontspec}"), "XeLaTeX/LuaLaTeX need Unicode fonts");
        assertTrue(template.contains("{babel}") && template.contains("vietnamese"));
    }

    private static void assertMessage(Runnable action, String expected) {
        RuntimeException error = assertThrows(RuntimeException.class, action::run);
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        assertTrue(cause.getMessage().contains(expected), "expected '" + expected + "' in: " + cause.getMessage());
    }
}

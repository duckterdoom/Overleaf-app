package com.overleaf.client.ui;

import com.overleaf.client.net.NetworkClient;
import com.overleaf.common.model.CRDTOperation;
import com.overleaf.common.protocol.MessageType;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.common.sync.OperationCodec;
import com.overleaf.server.net.OverleafServer;
import com.overleaf.server.net.ServerConfig;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end smoke run of the JavaFX client (needs a display, so it is a main
 * class rather than a Surefire test). Drives the real screens, checks the files
 * on disk, joins a real in-process server with a second client, and writes a
 * PNG per screen.
 *
 * <pre>
 * mvn -q -DskipTests install
 * mvn -q -pl client dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
 * java -cp client/target/classes:client/target/test-classes:$(cat client/target/cp.txt) \
 *      com.overleaf.client.ui.UiSmoke [screenshot-dir]
 * </pre>
 */
public final class UiSmoke {

    private static final List<String> results = new ArrayList<>();
    private static Path shots;
    private static Stage stage;

    public static void main(String[] args) throws Exception {
        Path home = Files.createTempDirectory("overleaf-smoke-");
        shots = Path.of(args.length > 0 ? args[0] : home.resolve("shots").toString());
        Files.createDirectories(shots);
        System.setProperty("overleaf.home", home.toString());
        System.out.println("[smoke] home=" + home + " shots=" + shots);

        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> started.complete(null));
        started.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);

        OverleafFxApp app = fx(OverleafFxApp::new);
        fx(() -> {
            stage = new Stage();
            app.start(stage);
            return null;
        });

        int failures = 0;
        try {
            run(app, home);
        } catch (Throwable t) {
            failures++;
            results.add("FAIL  " + t);
            t.printStackTrace();
            snap("zz-failure");
        } finally {
            for (String line : results) {
                System.out.println("[smoke] " + line);
                if (line.startsWith("FAIL")) failures++;
            }
            fx(() -> {
                stage.close();
                return null;
            });
            Platform.exit();
        }
        System.out.println(failures == 0 ? "[smoke] ALL PASSED" : "[smoke] FAILURES: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void run(OverleafFxApp app, Path home) throws Exception {
        StackPane host = field(app, "screenHost");

        // 1. First launch: register mode.
        waitFor("login screen", () -> fx(() -> host.getChildren().get(0) instanceof LoginView));
        snap("01-login-register");
        LoginView login = fx(() -> (LoginView) host.getChildren().get(0));
        fx(() -> {
            ((TextField) field(login, "nameField")).setText("Ada Lovelace");
            ((TextField) field(login, "emailField")).setText("ada@example.edu");
            ((TextField) field(login, "passwordField")).setText("analytical");
            ((Button) field(login, "primaryButton")).fire();
            return null;
        });
        waitFor("home after register", () -> fx(() -> host.getChildren().get(0) instanceof HomeView));
        check("account stored with a hash, not the password",
                fx(() -> !Files.readString(home.resolve(".app/accounts.properties")).contains("analytical")));
        check("starter project created", Files.isRegularFile(home.resolve("projects/My first paper/main.tex")));
        snap("02-home");

        // 2. New project via the modal.
        fx(() -> {
            button(host, "New project").fire();
            return null;
        });
        waitFor("new project modal", () -> fx(() -> host.getChildren().size() == 2));
        snap("03-new-project-modal");
        fx(() -> {
            Node modal = host.getChildren().get(1);
            TextField name = (TextField) modal.lookup(".modal-field");
            name.setText("Smoke Thesis");
            button(modal, "Create project").fire();
            return null;
        });
        Path project = home.resolve("projects/Smoke Thesis");
        waitFor("editor for new project", () -> fx(() -> host.getChildren().get(0) == field(app, "editorRoot")));
        check("project folder + main.tex on disk", Files.isRegularFile(project.resolve("main.tex")));
        TextArea editor = field(app, "codeEditor");
        check("editor shows the project's main.tex",
                fx(() -> editor.getText().contains("\\title{Smoke Thesis}")));
        snap("04-editor");

        // 3. Type, save, verify disk.
        fx(() -> {
            editor.positionCaret(editor.getText().indexOf("Start writing here."));
            editor.insertText(editor.getCaretPosition(), "Smoke edit. ");
            return null;
        });
        check("dirty marker shown", fx(() -> ((Label) field(app, "dirtyLabel")).isVisible()));
        fx(() -> {
            invoke(app, "saveFile");
            return null;
        });
        check("save wrote main.tex", Files.readString(project.resolve("main.tex")).contains("Smoke edit."));
        check("dirty marker cleared", fx(() -> !((Label) field(app, "dirtyLabel")).isVisible()));

        // 4. Compile (works with or without a TeX install; must not hang).
        fx(() -> {
            ((Button) field(app, "btnRecompile")).fire();
            return null;
        });
        waitFor("compile finished", () -> fx(() -> !((Button) field(app, "btnRecompile")).isDisabled()), 120);
        String status = fx(() -> ((Label) field(app, "statusLabel")).getText());
        check("compile reported a result: " + status, status.startsWith("Compiled") || status.contains("LaTeX")
                || status.contains("Compile"));
        snap("05-after-compile");

        // 5. A second file: create on disk, watcher shows it, open it.
        Files.writeString(project.resolve("chapter1.tex"), "\\section{Chapter one}\nBody.\n", StandardCharsets.UTF_8);
        waitFor("watcher adds chapter1.tex to tree", () -> fx(() ->
                ((java.util.Map<?, ?>) field(app, "treePaths")).containsValue("chapter1.tex")), 10);
        fx(() -> invoke(app, "openFile", "chapter1.tex"));
        check("editor switched to chapter1.tex", fx(() -> editor.getText().startsWith("\\section{Chapter one}")));
        fx(() -> {
            editor.appendText("More.\n");
            return invoke(app, "openFile", "main.tex");
        });
        check("switching files autosaved chapter1.tex",
                Files.readString(project.resolve("chapter1.tex")).contains("More."));
        snap("06-main-again");

        // 5b. New file / rename / delete through the in-app modals.
        fx(() -> invoke(app, "showNewFileDialog", stage));
        waitFor("new file modal", () -> fx(() -> host.getChildren().size() == 2));
        snap("06b-new-file-modal");
        fx(() -> {
            Node modal = host.getChildren().get(1);
            ((TextField) modal.lookup(".modal-field")).setText("notes");
            button(modal, "Create file").fire();
            return null;
        });
        check("new file created as notes.tex", Files.isRegularFile(project.resolve("notes.tex")));
        check("new file opened in editor", fx(() -> "notes.tex".equals(field(app, "currentFile"))));
        fx(() -> invoke(app, "renameFileDialog", "notes.tex"));
        fx(() -> {
            Node modal = host.getChildren().get(1);
            ((TextField) modal.lookup(".modal-field")).setText("appendix.tex");
            button(modal, "Rename").fire();
            return null;
        });
        check("rename moved the file", Files.isRegularFile(project.resolve("appendix.tex"))
                && !Files.exists(project.resolve("notes.tex")));
        check("editor follows the renamed file", fx(() -> "appendix.tex".equals(field(app, "currentFile"))));
        fx(() -> invoke(app, "deleteFileDialog", "appendix.tex"));
        snap("06c-delete-modal");
        fx(() -> {
            button(host.getChildren().get(1), "Delete file").fire();
            return null;
        });
        check("delete removed the file", !Files.exists(project.resolve("appendix.tex")));
        check("editor fell back to main.tex", fx(() -> "main.tex".equals(field(app, "currentFile"))));
        check("all modals closed", fx(() -> host.getChildren().size() == 1));

        // 6. External edit to the open file reloads it.
        String external = Files.readString(project.resolve("main.tex")).replace("Introduction", "Overview");
        Files.writeString(project.resolve("main.tex"), external, StandardCharsets.UTF_8);
        waitFor("external change reloaded", () -> fx(() -> editor.getText().contains("\\section{Overview}")), 10);

        // 7. Collaboration with a real server and a second client.
        String token = "smoke-token-at-least-24-characters";
        try (OverleafServer server = new OverleafServer(
                new ServerConfig("127.0.0.1", 0, 8, 60000, token, false, 32, 100000))) {
            server.start();
            fx(() -> invoke(app, "showConnectDialog", stage));
            waitFor("connect modal", () -> fx(() -> host.getChildren().size() == 2));
            snap("07a-connect-modal");
            fx(() -> {
                Node modal = host.getChildren().get(1);
                List<Node> fields = new ArrayList<>(modal.lookupAll(".modal-field"));
                ((TextField) fields.get(0)).setText("127.0.0.1");
                ((TextField) fields.get(1)).setText(String.valueOf(server.getPort()));
                ((TextField) fields.get(2)).setText("SMOKE");
                ((TextField) fields.get(3)).setText("Ada");
                ((TextField) fields.get(4)).setText(token);
                button(modal, "Connect").fire();
                return null;
            });
            waitFor("joined room (editor unlocked)", () -> fx(() ->
                    (boolean) field(app, "isConnected") && editor.isEditable()), 10);
            String local = Files.readString(project.resolve("main.tex"));
            waitFor("empty room is seeded with the local main.tex", () -> fx(() -> editor.getText().equals(local)), 10);

            BlockingQueue<SyncMessage> bobInbox = new LinkedBlockingQueue<>();
            try (NetworkClient bob = new NetworkClient("127.0.0.1", server.getPort(), false, token)) {
                bob.start(bobInbox::add, e -> { });
                bob.send(SyncMessage.createJoinRoom("SMOKE", "bob", "Bob"));
                check("Bob's snapshot is Ada's document", await(bobInbox, MessageType.FULL_SYNC).getPayload().equals(local));
                await(bobInbox, MessageType.CHECKPOINT);
                CRDTOperation op = new CRDTOperation("bob-1", CRDTOperation.OpType.INSERT, 0, "Hi from Bob. ", "bob", 100, 1);
                bob.send(SyncMessage.createTextEdit("SMOKE", "bob", "Bob", OperationCodec.toJson(op)));
                waitFor("Bob's edit appears in Ada's editor", () -> fx(() -> editor.getText().equals("Hi from Bob. " + local)), 10);
                waitFor("presence lists Bob", () -> fx(() ->
                        ((List<?>) field(app, "roomMemberLabels")).stream().anyMatch(l -> l.toString().startsWith("Bob"))), 10);
                fx(() -> {
                    editor.appendText("Hello Bob!");
                    return null;
                });
                SyncMessage edit = await(bobInbox, MessageType.TEXT_EDIT);
                check("Ada's typing reaches Bob", OperationCodec.fromJson(edit.getPayload()).getContent().contains("Hello Bob!"));
                snap("07-in-room");
            }
            fx(() -> invoke(app, "disconnectRoom"));
            // Re-join from a fresh project whose text differs: local copy must be backed up.
            fx(() -> invoke(app, "openProject", ((com.overleaf.client.ui.backend.LocalBackend) field(app, "backend"))
                    .projects().stream().filter(p -> p.name().equals("My first paper")).findFirst().orElseThrow()));
            Path first = home.resolve("projects/My first paper");
            fx(() -> invoke(app, "connectToRoom", "127.0.0.1", server.getPort(), "SMOKE", "Ada", false));
            waitFor("re-joined room", () -> fx(() -> (boolean) field(app, "isConnected") && editor.isEditable()), 10);
            check("room text replaced the editor", fx(() -> editor.getText().startsWith("Hi from Bob.")));
            check("local main.tex backed up before the room snapshot",
                    Files.isRegularFile(first.resolve("main.before-SMOKE.tex")));
            fx(() -> invoke(app, "disconnectRoom"));
            check("left the room", !fx(() -> (boolean) field(app, "isConnected")));
            check("project marked shared", Files.isRegularFile(project.resolve(".overleaf-room")));
        }

        // 8. Home shows real activity, then sign out.
        fx(() -> invoke(app, "showHome"));
        waitFor("home again", () -> fx(() -> host.getChildren().get(0) instanceof HomeView));
        check("activity recorded", Files.readString(home.resolve(".app/activity.properties")).contains("edits."));
        snap("08-home-activity");
        // Profile picture: store one, rebuild Home, check both avatars render it, then remove via the modal.
        com.overleaf.client.ui.backend.LocalBackend profiles = field(app, "backend");
        var session = (com.overleaf.client.ui.backend.AuthGateway.Session) field(app, "session");
        Path photo = home.resolve("me.png");
        BufferedImage img = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 80; y++) for (int x = 0; x < 120; x++) img.setRGB(x, y, x < 60 ? 0x23664A : 0xC9822B);
        ImageIO.write(img, "png", photo.toFile());
        profiles.setAvatar(session, photo);
        fx(() -> invoke(app, "showHome"));
        HomeView withPhoto = fx(() -> (HomeView) host.getChildren().get(0));
        check("home avatar shows the picture", fx(() ->
                ((Label) withPhoto.lookup(".user-chip .avatar")).getGraphic() instanceof javafx.scene.image.ImageView));
        fx(() -> {
            invoke(app, "updateSideUserChip");
            return null;
        });
        check("editor avatar shows the picture", fx(() ->
                ((Label) field(app, "sideUserAvatarLabel")).getGraphic() instanceof javafx.scene.image.ImageView));
        fx(() -> {
            Node chip = withPhoto.lookup(".user-chip");
            chip.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5,
                    javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true,
                    false, false, null));
            return null;
        });
        waitFor("profile picture modal", () -> fx(() -> host.getChildren().size() == 2));
        snap("08a-profile-modal");
        fx(() -> {
            button(host.getChildren().get(1), "Remove photo").fire();
            return null;
        });
        check("photo removed from disk", profiles.avatar(session).isEmpty());
        check("home avatar back to initials", fx(() -> "AL".equals(((Label) withPhoto.lookup(".user-chip .avatar")).getText())));
        fx(() -> {
            button(host.getChildren().get(1), "Done").fire();
            return null;
        });

        com.overleaf.client.ui.backend.LocalBackend backend = field(app, "backend");
        var thesis = backend.projects().stream().filter(p -> p.name().equals("Smoke Thesis")).findFirst().orElseThrow();
        fx(() -> invoke(withPhoto, "renameProjectDialog", thesis));
        snap("08b-rename-project-modal");
        fx(() -> {
            Node modal = host.getChildren().get(1);
            ((TextField) modal.lookup(".modal-field")).setText("Renamed Thesis");
            button(modal, "Rename").fire();
            return null;
        });
        check("project folder renamed", Files.isDirectory(home.resolve("projects/Renamed Thesis"))
                && !Files.exists(project));
        var first = backend.projects().stream().filter(p -> p.name().equals("My first paper")).findFirst().orElseThrow();
        fx(() -> invoke(withPhoto, "deleteProjectDialog", first));
        fx(() -> {
            button(host.getChildren().get(1), "Delete project").fire();
            return null;
        });
        check("deleted project moved to .trash", !Files.exists(home.resolve("projects/My first paper"))
                && Files.list(home.resolve(".trash")).findAny().isPresent());
        fx(() -> invoke(app, "openProject", backend.projects().get(0)));
        check("renamed project still opens", fx(() -> editor.getText().contains("\\documentclass")));
        fx(() -> invoke(app, "showHome"));
        fx(() -> invoke(app, "signOut"));
        waitFor("login after sign out", () -> fx(() -> host.getChildren().get(0) instanceof LoginView));
        LoginView again = fx(() -> (LoginView) host.getChildren().get(0));
        check("login pre-fills the last email",
                fx(() -> ((TextField) field(again, "emailField")).getText().equals("ada@example.edu")));
        fx(() -> {
            ((TextField) field(again, "passwordField")).setText("wrong-password");
            ((Button) field(again, "primaryButton")).fire();
            return null;
        });
        waitFor("wrong password shows an error", () -> fx(() -> ((Label) field(again, "errorLabel")).isVisible()), 10);
        snap("09-login-error");
        fx(() -> {
            ((TextField) field(again, "passwordField")).setText("analytical");
            ((Button) field(again, "primaryButton")).fire();
            return null;
        });
        waitFor("sign in with the right password", () -> fx(() -> host.getChildren().get(0) instanceof HomeView), 10);

        // 9. Vietnamese: project name, typing every diacritic form, saving, rendering.
        fx(() -> {
            button(host, "New project").fire();
            return null;
        });
        waitFor("new project modal (vi)", () -> fx(() -> host.getChildren().size() == 2));
        fx(() -> {
            Node modal = host.getChildren().get(1);
            ((TextField) modal.lookup(".modal-field")).setText("Luận văn tốt nghiệp");
            button(modal, "Create project").fire();
            return null;
        });
        Path vi = home.resolve("projects/Luận văn tốt nghiệp");
        waitFor("editor for Vietnamese project", () -> fx(() -> host.getChildren().get(0) == field(app, "editorRoot")));
        String sample = "\\section{Giới thiệu}\nXin chào thế giới! Tiếng Việt có dấu: ă â đ ê ô ơ ư "
                + "à á ả ã ạ ằ ắ ẳ ẵ ặ ầ ấ ẩ ẫ ậ è é ẻ ẽ ẹ ề ế ể ễ ệ ì í ỉ ĩ ị ò ó ỏ õ ọ ồ ố ổ ỗ ộ ờ ớ ở ỡ ợ "
                + "ù ú ủ ũ ụ ừ ứ ử ữ ự ỳ ý ỷ ỹ ỵ Đ Ơ Ư Ằ Ệ Ỗ Ữ\n";
        fx(() -> {
            int at = editor.getText().indexOf("\\section{Introduction}");
            editor.insertText(at, sample);
            invoke(app, "saveFile");
            return null;
        });
        check("Vietnamese text saved byte-for-byte as UTF-8",
                Files.readString(vi.resolve("main.tex"), StandardCharsets.UTF_8).contains(sample));
        check("outline shows the Vietnamese heading", fx(() ->
                all((Node) field(app, "outlineRows")).stream().anyMatch(n -> n instanceof Label l && "Giới thiệu".equals(l.getText()))));
        snap("10-vietnamese");
    }

    // ---------------------------------------------------------------- helpers

    private static void check(String what, boolean ok) {
        results.add((ok ? "PASS  " : "FAIL  ") + what);
    }

    private static void waitFor(String what, BooleanSupplierEx condition) throws Exception {
        waitFor(what, condition, 15);
    }

    private static void waitFor(String what, BooleanSupplierEx condition, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                results.add("PASS  " + what);
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }

    @FunctionalInterface
    interface BooleanSupplierEx {
        boolean get() throws Exception;
    }

    private static SyncMessage await(BlockingQueue<SyncMessage> inbox, MessageType type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            SyncMessage m = inbox.poll(100, TimeUnit.MILLISECONDS);
            if (m != null && m.getMessageType() == type) return m;
        }
        throw new AssertionError("Bob never received " + type);
    }

    static <T> T fx(Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) return work.call();
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(work.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(30, TimeUnit.SECONDS);
    }

    private static Button button(Node root, String text) {
        for (Node node : all(root)) {
            if (node instanceof Button b && text.equals(b.getText()) && b.isVisible()) return b;
        }
        throw new AssertionError("No button '" + text + "'");
    }

    private static List<Node> all(Node root) {
        List<Node> out = new ArrayList<>();
        out.add(root);
        if (root instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) out.addAll(all(child));
        }
        return out;
    }

    private static void snap(String name) {
        try {
            fx(() -> {
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                return null;
            });
            Thread.sleep(250);
            WritableImage image = fx(() -> stage.getScene().snapshot(null));
            PixelReader reader = image.getPixelReader();
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out.setRGB(x, y, reader.getArgb(x, y));
                }
            }
            ImageIO.write(out, "png", shots.resolve(name + ".png").toFile());
        } catch (Exception e) {
            results.add("WARN  screenshot " + name + ": " + e);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object target, String name) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep looking
            }
        }
        throw new NoSuchFieldException(name);
    }

    static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private UiSmoke() {
    }
}

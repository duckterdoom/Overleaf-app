package com.overleaf.client.ui;

import com.overleaf.client.net.NetworkClient;
import com.overleaf.common.model.CRDTOperation;
import com.overleaf.common.protocol.MessageType;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.common.sync.OperationCodec;
import com.overleaf.server.net.OverleafServer;
import com.overleaf.server.net.ServerConfig;
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

/**
 * End-to-end smoke run of the JavaFX client (needs a display, so it is a main class rather than a
 * Surefire test). Drives the real screens, checks the files on disk, joins a real in-process server
 * with a second client, and writes a PNG per screen.
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
            return null;
        });
        // Eye button: reveals the typed password, then hides it again.
        TextField shown = field(login, "passwordShown");
        TextField hidden = field(login, "passwordField");
        Button eye = fx(() -> (Button) login.lookup(".eye-button"));
        check("password hidden by default", fx(() -> !shown.isVisible() && hidden.isVisible()));
        fx(() -> {
            eye.fire();
            return null;
        });
        check(
                "eye reveals the password",
                fx(() ->
                        shown.isVisible()
                                && !hidden.isVisible()
                                && shown.getText().equals("analytical")));
        snap("01b-login-password-shown");
        fx(() -> {
            eye.fire();
            return null;
        });
        check("eye hides it again", fx(() -> !shown.isVisible() && hidden.isVisible()));
        fx(() -> {
            ((Button) field(login, "primaryButton")).fire();
            return null;
        });
        waitFor(
                "home after register",
                () -> fx(() -> host.getChildren().get(0) instanceof HomeView));
        check(
                "account stored with a hash, not the password",
                fx(() ->
                        !Files.readString(home.resolve(".app/accounts.properties"))
                                .contains("analytical")));
        check(
                "starter project created",
                Files.isRegularFile(home.resolve("projects/My first paper/main.tex")));
        snap("02-home");

        // 2. New project via the modal.
        fx(() -> {
            button(host, "New project").fire();
            chooseBlankProject(host);
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
        waitFor(
                "editor for new project",
                () -> fx(() -> host.getChildren().get(0) == field(app, "editorRoot")));
        check(
                "project folder + main.tex on disk",
                Files.isRegularFile(project.resolve("main.tex")));
        TextArea editor = field(app, "codeEditor");
        check(
                "editor shows the project's main.tex",
                fx(() -> editor.getText().contains("\\title{Smoke Thesis}")));
        String original0 = fx(editor::getText);
        snap("04-editor");
        // Highlight layer alignment: show the text area's own text in red over the colored layer,
        // on a long document scrolled down; any drift shows as doubled glyphs.
        fx(() -> {
            StringBuilder longDoc = new StringBuilder(editor.getText());
            for (int i = 1; i <= 80; i++)
                longDoc.append("Line ")
                        .append(i)
                        .append(" with $x^")
                        .append(i)
                        .append("$ % note\n");
            editor.setText(longDoc.toString());
            editor.setStyle(editor.getStyle() + " -fx-text-fill: rgba(255,0,0,0.75);");
            editor.requestFocus();
            int at = editor.getText().indexOf("Line 40");
            editor.selectRange(at, at + 7);
            editor.setScrollTop(500);
            return null;
        });
        fx(() -> null);
        snap("04x-overlay-alignment");
        check(
                "overlay matches the editor, no wrap (max drift px < 0.5)",
                fx(() -> overlayDrift(app, editor) < 0.5));
        AppSettings wrapPrefs = field(app, "settings");
        fx(() -> {
            StringBuilder wrapped = new StringBuilder();
            for (int i = 1; i <= 30; i++) {
                wrapped.append("Paragraph ")
                        .append(i)
                        .append(" is long enough to wrap: \\textbf{bold} words $a+b$ ");
                wrapped.append(
                        "and more words so the line runs past the right edge of the panel. % c\n");
            }
            editor.setText(wrapped.toString());
            wrapPrefs.set(AppSettings.WORD_WRAP, true);
            editor.setScrollTop(200);
            return null;
        });
        fx(() -> null);
        fx(() -> {
            if (!editor.getStyle().contains("rgba(255,0,0"))
                editor.setStyle(editor.getStyle() + " -fx-text-fill: rgba(255,0,0,0.75);");
            return null;
        });
        fx(() -> null);
        snap("04y-overlay-wrap");
        check(
                "overlay matches the editor, word wrap on (max drift px < 0.5)",
                fx(() -> overlayDrift(app, editor) < 0.5));
        fx(() -> {
            wrapPrefs.set(AppSettings.WORD_WRAP, false);
            StringBuilder big = new StringBuilder();
            for (int i = 0; i < 5000; i++)
                big.append("\\section{Part ")
                        .append(i)
                        .append("} text $x_")
                        .append(i)
                        .append("$ % note\n");
            editor.setText(big.toString());
            return null;
        });
        fx(() -> null);
        fx(() -> {
            if (!editor.getStyle().contains("rgba(255,0,0"))
                editor.setStyle(editor.getStyle() + " -fx-text-fill: rgba(255,0,0,0.75);");
            return null;
        });
        long started = System.nanoTime();
        for (int k = 0; k < 10; k++) {
            fx(() -> {
                editor.insertText(editor.getText().length() / 2, "a");
                return null;
            });
            fx(() -> null);
        }
        long perKeyMs = (System.nanoTime() - started) / 10 / 1_000_000;
        System.out.println(
                "[smoke] INFO  typing in a 5000-line file: ~"
                        + perKeyMs
                        + " ms per keystroke incl. recolor");
        check("long file types quickly (< 150 ms per key)", perKeyMs < 150);
        check(
                "only the lines on screen are drawn",
                fx(() ->
                        (int) invoke(field(app, "codeOverlay"), "renderedLineCountForTests")
                                < 120));
        fx(() -> {
            editor.setScrollTop(Double.MAX_VALUE);
            return null;
        });
        fx(() -> null);
        fx(() -> null);
        check(
                "overlay aligned at the end of a 5000-line file",
                fx(() -> overlayDrift(app, editor) < 0.5));
        snap("04z-overlay-long-file-end");
        fx(() -> {
            editor.setText(original0);
            editor.setScrollTop(0);
            return null;
        });
        fx(() -> null);
        fx(() -> {
            editor.setStyle(editor.getStyle().replace(" -fx-text-fill: rgba(255,0,0,0.75);", ""));
            editor.setText(original0);
            editor.setScrollTop(0);
            return null;
        });

        // 2b. Code toolbar: each action edits the text; the original is restored afterwards.
        String original = fx(editor::getText);
        fx(() -> {
            int at = editor.getText().indexOf("Start writing here.");
            editor.selectRange(at, at + "Start writing here.".length());
            invoke(app, "wrapSelection", "\\textbf{", "}");
            return null;
        });
        check(
                "bold wraps the selection",
                fx(() -> editor.getText().contains("\\textbf{Start writing here.}")));
        fx(() -> {
            editor.setText(original);
            editor.positionCaret(editor.getText().indexOf("\\section{Introduction}") + 3);
            invoke(app, "applyHeading", "subsection");
            return null;
        });
        check(
                "text style makes a subsection",
                fx(() -> editor.getText().contains("\\subsection{Introduction}")));
        fx(() -> {
            invoke(app, "applyHeading", (Object) null);
            return null;
        });
        check(
                "normal text strips the heading",
                fx(() -> editor.getText().contains("\nIntroduction\n")));
        fx(() -> {
            editor.setText(original);
            editor.positionCaret(editor.getText().indexOf("Start writing here."));
            invoke(app, "toggleComment");
            return null;
        });
        check(
                "comment adds a percent",
                fx(() -> editor.getText().contains("% Start writing here.")));
        fx(() -> {
            invoke(app, "toggleComment");
            return null;
        });
        check("comment toggles back", fx(() -> editor.getText().equals(original)));
        fx(() -> {
            invoke(app, "toggleFindBar");
            ((TextField) field(app, "findField")).setText("introduction");
            invoke(app, "findNext", true);
            return null;
        });
        check(
                "find selects a match",
                fx(() -> editor.getSelectedText().equalsIgnoreCase("introduction")));
        check(
                "find shows a count",
                fx(() -> ((Label) field(app, "findCount")).getText().contains(" of ")));
        snap("04b-editor-toolbar");
        fx(() -> {
            invoke(app, "toggleFindBar");
            editor.setText(original);
            return null;
        });
        // Auto-close brackets: typing { adds }, typing } over it steps past it.
        fx(() -> {
            editor.setText("");
            editor.requestFocus();
            javafx.event.Event.fireEvent(
                    editor,
                    new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_TYPED,
                            "{",
                            "",
                            javafx.scene.input.KeyCode.UNDEFINED,
                            false,
                            false,
                            false,
                            false));
            return null;
        });
        check(
                "typing { adds the closing brace",
                fx(() -> editor.getText().equals("{}") && editor.getCaretPosition() == 1));
        fx(() -> {
            javafx.event.Event.fireEvent(
                    editor,
                    new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_TYPED,
                            "}",
                            "",
                            javafx.scene.input.KeyCode.UNDEFINED,
                            false,
                            false,
                            false,
                            false));
            return null;
        });
        check(
                "typing } steps over it",
                fx(() -> editor.getText().equals("{}") && editor.getCaretPosition() == 2));
        fx(() -> {
            editor.setText(original);
            ((AppSettings) field(app, "settings")).set(AppSettings.MODE, "dark");
            return null;
        });
        fx(() -> null);
        snap("04c-editor-dark");
        fx(() -> {
            ((AppSettings) field(app, "settings")).resetAll();
            return null;
        });
        fx(() -> null);

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
        check(
                "save wrote main.tex",
                Files.readString(project.resolve("main.tex")).contains("Smoke edit."));
        check("dirty marker cleared", fx(() -> !((Label) field(app, "dirtyLabel")).isVisible()));

        // 4. Compile (works with or without a TeX install; must not hang).
        fx(() -> {
            ((Button) field(app, "btnRecompile")).fire();
            return null;
        });
        waitFor(
                "compile finished",
                () -> fx(() -> !((Button) field(app, "btnRecompile")).isDisabled()),
                120);
        String status = fx(() -> ((Label) field(app, "statusLabel")).getText());
        check(
                "compile reported a result: " + status,
                status.startsWith("Compiled")
                        || status.contains("LaTeX")
                        || status.contains("Compile"));
        snap("05-after-compile");

        // 5. A second file: create on disk, watcher shows it, open it.
        Files.writeString(
                project.resolve("chapter1.tex"),
                "\\section{Chapter one}\nBody.\n",
                StandardCharsets.UTF_8);
        waitFor(
                "watcher adds chapter1.tex to tree",
                () ->
                        fx(() ->
                                ((java.util.Map<?, ?>) field(app, "treePaths"))
                                        .containsValue("chapter1.tex")),
                10);
        fx(() -> invoke(app, "openFile", "chapter1.tex"));
        check(
                "editor switched to chapter1.tex",
                fx(() -> editor.getText().startsWith("\\section{Chapter one}")));
        fx(() -> {
            editor.appendText("More.\n");
            return invoke(app, "openFile", "main.tex");
        });
        check(
                "switching files autosaved chapter1.tex",
                Files.readString(project.resolve("chapter1.tex")).contains("More."));
        snap("06-main-again");

        // 5b. New file / rename / delete through the in-app modals.
        fx(() -> invoke(app, "showNewFileDialog", stage));
        waitFor("new file modal", () -> fx(() -> host.getChildren().size() == 2));
        snap("06b-new-file-modal");
        fx(() -> {
            Node modal = host.getChildren().get(1);
            ((TextField) modal.lookup("#add-files-name")).setText("notes");
            button(modal, "Create").fire();
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
        check(
                "rename moved the file",
                Files.isRegularFile(project.resolve("appendix.tex"))
                        && !Files.exists(project.resolve("notes.tex")));
        check(
                "editor follows the renamed file",
                fx(() -> "appendix.tex".equals(field(app, "currentFile"))));
        fx(() -> invoke(app, "deleteFileDialog", "appendix.tex"));
        snap("06c-delete-modal");
        fx(() -> {
            button(host.getChildren().get(1), "Delete file").fire();
            return null;
        });
        check("delete removed the file", !Files.exists(project.resolve("appendix.tex")));
        check(
                "editor fell back to main.tex",
                fx(() -> "main.tex".equals(field(app, "currentFile"))));
        check("all modals closed", fx(() -> host.getChildren().size() == 1));

        // 6. External edit to the open file reloads it.
        String external =
                Files.readString(project.resolve("main.tex")).replace("Introduction", "Overview");
        Files.writeString(project.resolve("main.tex"), external, StandardCharsets.UTF_8);
        waitFor(
                "external change reloaded",
                () -> fx(() -> editor.getText().contains("\\section{Overview}")),
                10);

        // 7. Collaboration with a real server and a second client.
        String token = "smoke-token-at-least-24-characters";
        try (OverleafServer server =
                new OverleafServer(
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
            waitFor(
                    "joined room (editor unlocked)",
                    () -> fx(() -> (boolean) field(app, "isConnected") && editor.isEditable()),
                    10);
            String local = Files.readString(project.resolve("main.tex"));
            waitFor(
                    "empty room is seeded with the local main.tex",
                    () -> fx(() -> editor.getText().equals(local)),
                    10);

            BlockingQueue<SyncMessage> bobInbox = new LinkedBlockingQueue<>();
            try (NetworkClient bob =
                    new NetworkClient("127.0.0.1", server.getPort(), false, token)) {
                bob.start(bobInbox::add, e -> {});
                bob.send(SyncMessage.createJoinRoom("SMOKE", "bob", "Bob"));
                check(
                        "Bob's snapshot is Ada's document",
                        await(bobInbox, MessageType.FULL_SYNC).getPayload().equals(local));
                await(bobInbox, MessageType.CHECKPOINT);
                CRDTOperation op =
                        new CRDTOperation(
                                "bob-1",
                                CRDTOperation.OpType.INSERT,
                                0,
                                "Hi from Bob. ",
                                "bob",
                                100,
                                1);
                bob.send(
                        SyncMessage.createTextEdit(
                                "SMOKE", "bob", "Bob", OperationCodec.toJson(op)));
                waitFor(
                        "Bob's edit appears in Ada's editor",
                        () -> fx(() -> editor.getText().equals("Hi from Bob. " + local)),
                        10);
                waitFor(
                        "presence lists Bob",
                        () ->
                                fx(() ->
                                        ((List<?>) field(app, "roomMemberLabels"))
                                                .stream()
                                                        .anyMatch(l ->
                                                                l.toString().startsWith("Bob"))),
                        10);
                fx(() -> {
                    editor.appendText("Hello Bob!");
                    return null;
                });
                SyncMessage edit = await(bobInbox, MessageType.TEXT_EDIT);
                check(
                        "Ada's typing reaches Bob",
                        OperationCodec.fromJson(edit.getPayload())
                                .getContent()
                                .contains("Hello Bob!"));
                snap("07-in-room");
            }
            fx(() -> invoke(app, "disconnectRoom"));
            // Re-join from a fresh project whose text differs: local copy must be backed up.
            fx(() ->
                    invoke(
                            app,
                            "openProject",
                            ((com.overleaf.client.ui.backend.LocalBackend) field(app, "backend"))
                                    .projects().stream()
                                            .filter(p -> p.name().equals("My first paper"))
                                            .findFirst()
                                            .orElseThrow()));
            Path first = home.resolve("projects/My first paper");
            fx(() ->
                    invoke(
                            app,
                            "connectToRoom",
                            "127.0.0.1",
                            server.getPort(),
                            "SMOKE",
                            "Ada",
                            false));
            waitFor(
                    "re-joined room",
                    () -> fx(() -> (boolean) field(app, "isConnected") && editor.isEditable()),
                    10);
            check(
                    "room text replaced the editor",
                    fx(() -> editor.getText().startsWith("Hi from Bob.")));
            check(
                    "local main.tex backed up before the room snapshot",
                    Files.isRegularFile(first.resolve("main.before-SMOKE.tex")));
            fx(() -> invoke(app, "disconnectRoom"));
            check("left the room", !fx(() -> (boolean) field(app, "isConnected")));
            check("project marked shared", Files.isRegularFile(project.resolve(".overleaf-room")));
        }

        // 8. Home shows real activity, then sign out.
        fx(() -> invoke(app, "showHome"));
        waitFor("home again", () -> fx(() -> host.getChildren().get(0) instanceof HomeView));
        check(
                "activity recorded",
                Files.readString(home.resolve(".app/activity.properties")).contains("edits."));
        snap("08-home-activity");
        // Settings: gear opens the page; theme and mode restyle the live scene; reset restores
        // defaults.
        AppSettings prefs = field(app, "settings");
        fx(() -> {
            ((Button) host.lookupAll(".user-chip .icon-button").iterator().next()).fire();
            return null;
        });
        check(
                "settings page opens from the home gear",
                fx(() -> host.getChildren().get(0) instanceof SettingsView));
        snap("08d-settings-appearance");
        fx(() -> {
            prefs.set(AppSettings.THEME, "parchment");
            return null;
        });
        fx(() -> null);
        check(
                "theme applied to the scene",
                fx(() ->
                        host.getScene().getStylesheets().size() == 1
                                && host.getScene()
                                        .getStylesheets()
                                        .get(0)
                                        .contains("overleaf-theme-")));
        snap("08e-settings-parchment");
        fx(() -> {
            prefs.set(AppSettings.MODE, "dark");
            return null;
        });
        fx(() -> null);
        check(
                "dark mode flagged on the root",
                fx(() -> host.getScene().getRoot().getStyleClass().contains("theme-dark")));
        snap("08f-settings-dark");
        fx(() -> {
            invoke(app, "showHome");
            return null;
        });
        snap("08g-home-dark");
        // Dark mode in every theme, for review.
        for (String themeId : new String[] {"forest", "mint", "bee", "parchment"}) {
            fx(() -> {
                prefs.set(AppSettings.THEME, themeId);
                return null;
            });
            fx(() -> null);
            fx(() -> invoke(app, "showHome"));
            snap("09-dark-" + themeId);
        }
        // Snake takes the theme's colors: dark + Parchment, then light + Bee.
        fx(() -> {
            host.applyCss();
            host.layout();
            ActivityHeatmap map =
                    (ActivityHeatmap) host.getChildren().get(0).lookup(".activity-heatmap");
            map.startGame();
            return null;
        });
        snap("08i-snake-dark-parchment");
        fx(() -> {
            prefs.set(AppSettings.MODE, "light");
            prefs.set(AppSettings.THEME, "bee");
            return null;
        });
        fx(() -> invoke(app, "showHome"));
        fx(() -> {
            // A freshly built Home is only reachable through its scroll pane after a layout pass.
            host.applyCss();
            host.layout();
            ((ActivityHeatmap) host.getChildren().get(0).lookup(".activity-heatmap")).startGame();
            return null;
        });
        snap("08j-snake-light-bee");
        fx(() -> {
            prefs.set(AppSettings.THEME, "parchment");
            prefs.set(AppSettings.MODE, "dark");
            return null;
        });
        fx(() -> {
            invoke(app, "showSettings", false, "appearance");
            return null;
        });
        fx(() -> {
            for (Node n : host.getChildren().get(0).lookupAll(".settings-nav-item")) {
                if (((Button) n).getText().equals("Editor")) ((Button) n).fire();
            }
            return null;
        });
        snap("08h-settings-editor-dark");
        fx(() -> {
            prefs.resetAll();
            prefs.set(AppSettings.CONTENT_WIDTH, "compact");
            return null;
        });
        fx(() -> null);
        fx(() -> invoke(app, "showHome"));
        snap("08m-home-compact");
        fx(() -> {
            invoke(app, "showSettings", false, "appearance");
            return null;
        });
        fx(() -> {
            prefs.resetAll();
            return null;
        });
        fx(() -> null);
        check(
                "reset restores light mode",
                fx(() -> host.getScene().getRoot().getStyleClass().contains("theme-light")));
        fx(() -> {
            invoke(app, "showHome");
            return null;
        });
        waitFor(
                "home after settings",
                () -> fx(() -> host.getChildren().get(0) instanceof HomeView));
        // Profile picture: store one, rebuild Home, check both avatars render it, then remove via
        // the modal.
        com.overleaf.client.ui.backend.LocalBackend profiles = field(app, "backend");
        var session = (com.overleaf.client.ui.backend.AuthGateway.Session) field(app, "session");
        Path photo = home.resolve("me.png");
        BufferedImage img = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 80; y++)
            for (int x = 0; x < 120; x++) img.setRGB(x, y, x < 60 ? 0x23664A : 0xC9822B);
        ImageIO.write(img, "png", photo.toFile());
        profiles.setAvatar(session, photo);
        fx(() -> invoke(app, "showHome"));
        HomeView withPhoto = fx(() -> (HomeView) host.getChildren().get(0));
        check(
                "home avatar shows the picture",
                fx(() ->
                        ((Label) withPhoto.lookup(".user-chip .avatar")).getGraphic()
                                instanceof javafx.scene.image.ImageView));
        fx(() -> {
            invoke(app, "updateSideUserChip");
            return null;
        });
        check(
                "editor avatar shows the picture",
                fx(() ->
                        ((Label) field(app, "sideUserAvatarLabel")).getGraphic()
                                instanceof javafx.scene.image.ImageView));
        fx(() -> {
            Node chip = withPhoto.lookup(".user-chip");
            chip.fireEvent(
                    new javafx.scene.input.MouseEvent(
                            javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                            5,
                            5,
                            5,
                            5,
                            javafx.scene.input.MouseButton.PRIMARY,
                            1,
                            false,
                            false,
                            false,
                            false,
                            true,
                            false,
                            false,
                            true,
                            false,
                            false,
                            null));
            return null;
        });
        waitFor(
                "profile settings page",
                () -> fx(() -> host.getChildren().get(0) instanceof SettingsView));
        snap("08a-profile-settings");
        fx(() -> {
            button(host.getChildren().get(0), "Remove photo").fire();
            return null;
        });
        check("photo removed from disk", profiles.avatar(session).isEmpty());
        // Display name: Save is disabled until the name changes, then the account and session
        // follow.
        fx(() -> {
            TextField nameField = (TextField) host.getChildren().get(0).lookup(".modal-field");
            check("save starts disabled", button(host.getChildren().get(0), "Save").isDisabled());
            nameField.setText("Ada L.");
            check(
                    "save enables on change",
                    !button(host.getChildren().get(0), "Save").isDisabled());
            button(host.getChildren().get(0), "Save").fire();
            return null;
        });
        check(
                "new name stored in the account",
                Files.readString(home.resolve(".app/accounts.properties")).contains("|Ada L."));
        check(
                "session carries the new name",
                fx(() ->
                        "Ada L."
                                .equals(
                                        ((com.overleaf.client.ui.backend.AuthGateway.Session)
                                                        field(app, "session"))
                                                .name())));
        fx(() -> {
            TextField nameField = (TextField) host.getChildren().get(0).lookup(".modal-field");
            nameField.setText("Ada Lovelace");
            button(host.getChildren().get(0), "Save").fire();
            return null;
        });
        // Change email and change password dialogs (each reverted so later steps keep working).
        fx(() -> {
            changeButtons(host.getChildren().get(0)).get(0).fire();
            return null;
        });
        waitFor("change email dialog", () -> fx(() -> host.getChildren().size() == 2));
        snap("08k-change-email");
        fx(() -> {
            Node modal = host.getChildren().get(1);
            var fields = new ArrayList<>(modal.lookupAll(".modal-field"));
            ((TextField) fields.get(0)).setText("ada2@example.edu");
            ((TextField) fields.get(1)).setText("wrong-password");
            button(modal, "Change email").fire();
            return null;
        });
        check(
                "wrong password keeps the email dialog open",
                fx(() ->
                        host.getChildren().size() == 2
                                && host.getChildren().get(1).lookup(".modal-error").isVisible()));
        fx(() -> {
            Node modal = host.getChildren().get(1);
            var fields = new ArrayList<>(modal.lookupAll(".modal-field"));
            ((TextField) fields.get(1)).setText("analytical");
            button(modal, "Change email").fire();
            return null;
        });
        check(
                "email changed in the account file",
                Files.readString(home.resolve(".app/accounts.properties"))
                        .contains("ada2@example.edu="));
        check(
                "session carries the new email",
                fx(() ->
                        "ada2@example.edu"
                                .equals(
                                        ((com.overleaf.client.ui.backend.AuthGateway.Session)
                                                        field(app, "session"))
                                                .email())));
        fx(() -> {
            changeButtons(host.getChildren().get(0)).get(0).fire();
            return null;
        });
        waitFor("change email dialog again", () -> fx(() -> host.getChildren().size() == 2));
        fx(() -> {
            Node modal = host.getChildren().get(1);
            var fields = new ArrayList<>(modal.lookupAll(".modal-field"));
            ((TextField) fields.get(0)).setText("ada@example.edu");
            ((TextField) fields.get(1)).setText("analytical");
            button(modal, "Change email").fire();
            return null;
        });
        check(
                "email changed back",
                Files.readString(home.resolve(".app/accounts.properties"))
                        .contains("ada@example.edu="));

        fx(() -> {
            changeButtons(host.getChildren().get(0)).get(1).fire();
            return null;
        });
        waitFor("change password dialog", () -> fx(() -> host.getChildren().size() == 2));
        snap("08l-change-password");
        fx(() -> {
            Node modal = host.getChildren().get(1);
            var fields = new ArrayList<>(modal.lookupAll(".modal-field"));
            ((TextField) fields.get(0)).setText("analytical");
            ((TextField) fields.get(1)).setText("analytical2");
            ((TextField) fields.get(2)).setText("different");
            button(modal, "Change password").fire();
            return null;
        });
        check(
                "mismatched confirmation is refused",
                fx(() ->
                        host.getChildren().size() == 2
                                && host.getChildren().get(1).lookup(".modal-error").isVisible()));
        fx(() -> {
            Node modal = host.getChildren().get(1);
            var fields = new ArrayList<>(modal.lookupAll(".modal-field"));
            ((TextField) fields.get(2)).setText("analytical2");
            button(modal, "Change password").fire();
            return null;
        });
        check(
                "new password works",
                profiles.signIn("ada@example.edu", "analytical2", false).join().name() != null);
        profiles.changePassword(
                (com.overleaf.client.ui.backend.AuthGateway.Session)
                        fx(() -> field(app, "session")),
                "analytical2",
                "analytical");

        fx(() -> {
            button(host.getChildren().get(0), "Back").fire();
            return null;
        });
        waitFor(
                "home after profile",
                () -> fx(() -> host.getChildren().get(0) instanceof HomeView));
        check(
                "home avatar back to initials",
                fx(() ->
                        "AL"
                                .equals(
                                        ((Label)
                                                        host.getChildren()
                                                                .get(0)
                                                                .lookup(".user-chip .avatar"))
                                                .getText())));

        com.overleaf.client.ui.backend.LocalBackend backend = field(app, "backend");
        var thesis =
                backend.projects().stream()
                        .filter(p -> p.name().equals("Smoke Thesis"))
                        .findFirst()
                        .orElseThrow();
        fx(() -> invoke(withPhoto, "renameProjectDialog", thesis));
        snap("08b-rename-project-modal");
        fx(() -> {
            Node modal = host.getChildren().get(1);
            ((TextField) modal.lookup(".modal-field")).setText("Renamed Thesis");
            button(modal, "Rename").fire();
            return null;
        });
        check(
                "project folder renamed",
                Files.isDirectory(home.resolve("projects/Renamed Thesis"))
                        && !Files.exists(project));
        var first =
                backend.projects().stream()
                        .filter(p -> p.name().equals("My first paper"))
                        .findFirst()
                        .orElseThrow();
        fx(() -> invoke(withPhoto, "deleteProjectDialog", first));
        fx(() -> {
            button(host.getChildren().get(1), "Delete project").fire();
            return null;
        });
        check(
                "deleted project moved to .trash",
                !Files.exists(home.resolve("projects/My first paper"))
                        && Files.list(home.resolve(".trash")).findAny().isPresent());
        fx(() -> invoke(app, "openProject", backend.projects().get(0)));
        check(
                "renamed project still opens",
                fx(() -> editor.getText().contains("\\documentclass")));
        fx(() -> invoke(app, "showHome"));
        // Sign out asks first: Cancel keeps the session, Sign out confirms.
        fx(() -> invoke(app, "confirmSignOut"));
        waitFor("sign-out confirmation", () -> fx(() -> host.getChildren().size() == 2));
        snap("08c-sign-out-confirm");
        fx(() -> {
            button(host.getChildren().get(1), "Cancel").fire();
            return null;
        });
        check(
                "cancel keeps you signed in",
                fx(() ->
                        host.getChildren().size() == 1
                                && host.getChildren().get(0) instanceof HomeView));
        fx(() -> invoke(app, "confirmSignOut"));
        waitFor("sign-out confirmation again", () -> fx(() -> host.getChildren().size() == 2));
        fx(() -> {
            button(host.getChildren().get(1), "Sign out").fire();
            return null;
        });
        waitFor(
                "login after sign out",
                () -> fx(() -> host.getChildren().get(0) instanceof LoginView));
        LoginView again = fx(() -> (LoginView) host.getChildren().get(0));
        check(
                "login pre-fills the last email",
                fx(() ->
                        ((TextField) field(again, "emailField"))
                                .getText()
                                .equals("ada@example.edu")));
        fx(() -> {
            ((TextField) field(again, "passwordField")).setText("wrong-password");
            ((Button) field(again, "primaryButton")).fire();
            return null;
        });
        waitFor(
                "wrong password shows an error",
                () -> fx(() -> ((Label) field(again, "errorLabel")).isVisible()),
                10);
        snap("09-login-error");
        fx(() -> {
            ((TextField) field(again, "passwordField")).setText("analytical");
            ((Button) field(again, "primaryButton")).fire();
            return null;
        });
        waitFor(
                "sign in with the right password",
                () -> fx(() -> host.getChildren().get(0) instanceof HomeView),
                10);

        // 9. Vietnamese: project name, typing every diacritic form, saving, rendering.
        fx(() -> {
            button(host, "New project").fire();
            chooseBlankProject(host);
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
        waitFor(
                "editor for Vietnamese project",
                () -> fx(() -> host.getChildren().get(0) == field(app, "editorRoot")));
        String sample =
                "\\section{Giới thiệu}\nXin chào thế giới! Tiếng Việt có dấu: ă â đ ê ô ơ ư "
                        + "à á ả ã ạ ằ ắ ẳ ẵ ặ ầ ấ ẩ ẫ ậ è é ẻ ẽ ẹ ề ế ể ễ ệ ì í ỉ ĩ ị ò ó ỏ õ ọ ồ ố ổ ỗ ộ ờ ớ ở ỡ ợ "
                        + "ù ú ủ ũ ụ ừ ứ ử ữ ự ỳ ý ỷ ỹ ỵ Đ Ơ Ư Ằ Ệ Ỗ Ữ\n";
        fx(() -> {
            int at = editor.getText().indexOf("\\section{Introduction}");
            editor.insertText(at, sample);
            invoke(app, "saveFile");
            return null;
        });
        check(
                "Vietnamese text saved byte-for-byte as UTF-8",
                Files.readString(vi.resolve("main.tex"), StandardCharsets.UTF_8).contains(sample));
        check(
                "outline shows the Vietnamese heading",
                fx(() ->
                        all((Node) field(app, "outlineRows")).stream()
                                .anyMatch(n ->
                                        n instanceof Label l && "Giới thiệu".equals(l.getText()))));
        snap("10-vietnamese");
        // The interface language is a saved setting: it survives a restart.
        fx(() -> invoke(app, "setVietnameseUi", true));
        fx(() -> null);
        check(
                "language choice saved",
                Files.readString(home.resolve(".app/settings.properties"))
                        .contains("pref.language=vi"));
        check(
                "a fresh start reads it back",
                "vi".equals(new AppSettings(field(app, "backend")).get(AppSettings.LANGUAGE)));
        fx(() -> invoke(app, "setVietnameseUi", false));
        fx(() -> null);
    }

    // ---------------------------------------------------------------- helpers

    private static void check(String what, boolean ok) {
        results.add((ok ? "PASS  " : "FAIL  ") + what);
    }

    private static void waitFor(String what, BooleanSupplierEx condition) throws Exception {
        waitFor(what, condition, 15);
    }

    private static void waitFor(String what, BooleanSupplierEx condition, int seconds)
            throws Exception {
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

    private static SyncMessage await(BlockingQueue<SyncMessage> inbox, MessageType type)
            throws Exception {
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

    /** Drift in pixels between the editor's text layout and the colored lines on screen. */
    private static double overlayDrift(OverleafFxApp app, TextArea editor) throws Exception {
        Object overlay = field(app, "codeOverlay");
        double drift = (double) invoke(overlay, "driftForTests");
        int drawn = (int) invoke(overlay, "renderedLineCountForTests");
        System.out.println(
                "[smoke] INFO  overlay drift=" + drift + "px over " + drawn + " drawn lines");
        return drawn > 0 ? drift : Double.MAX_VALUE;
    }

    /** The "Change…" buttons on the Profile page: email first, then password. */
    private static List<Button> changeButtons(Node root) {
        List<Button> out = new ArrayList<>();
        for (Node node : all(root)) {
            if (node instanceof Button b && "Change…".equals(b.getText())) out.add(b);
        }
        return out;
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

    private static void chooseBlankProject(javafx.scene.layout.StackPane host) throws Exception {
        var menu =
                (javafx.scene.control.ContextMenu)
                        field(host.getChildren().get(0), "newProjectMenu");
        menu.hide();
        menu.getItems().stream()
                .filter(i -> "new-project-blank".equals(i.getId()))
                .findFirst()
                .orElseThrow()
                .fire();
    }

    private UiSmoke() {}
}

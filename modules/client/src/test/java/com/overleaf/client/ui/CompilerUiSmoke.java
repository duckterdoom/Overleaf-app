package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.LocalBackend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/** Explicit UI smoke for the compile workflow. Needs a display and a real latexmk. */
public final class CompilerUiSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Usage: CompilerUiSmoke <latexmk-path> <new-home>");
        Path home = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(home))
            throw new IllegalArgumentException("Use a new home for this smoke check.");
        System.setProperty("overleaf.home", home.toString());
        LocalBackend backend = new LocalBackend(home);
        backend.register("Compile Test", "compile@example.test", "test-password", true).join();
        AppSettings prefs = new AppSettings(backend);
        require(
                prefs.get(AppSettings.LATEXMK_PATH).isBlank(),
                "fresh settings must not need a manual path");
        require(
                prefs.compileOptions()
                        .executable()
                        .equals(Path.of(args[0]).toAbsolutePath().toString()),
                "portable latexmk must be discovered automatically");
        System.out.println("PASS automatic portable discovery without saved path");
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> started.complete(null));
        started.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        OverleafFxApp app = UiSmoke.fx(OverleafFxApp::new);
        Stage stage =
                UiSmoke.fx(() -> {
                    Stage window = new Stage();
                    window.setOpacity(0);
                    app.start(window);
                    return window;
                });
        try {
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "openProject", backend.projects().get(0));
                UiSmoke.invoke(app, "showSettings", true, "compilation");
                return null;
            });
            UiSmoke.fx(() -> {
                TextField timeout = (TextField) stage.getScene().lookup("#compile-timeout");
                Button save = (Button) stage.getScene().lookup("#save-compiler-settings");
                timeout.setText("-1");
                save.fire();
                require(
                        "120".equals(prefs.get(AppSettings.COMPILE_TIMEOUT)),
                        "invalid timeout must not be saved");
                timeout.setText("0");
                save.fire();
                require(
                        "0".equals(prefs.get(AppSettings.COMPILE_TIMEOUT)),
                        "unlimited timeout must persist");
                ((Button) stage.getScene().lookup("#check-toolchain")).fire();
                return null;
            });
            waitFor(() ->
                    UiSmoke.fx(() ->
                            !((Button) stage.getScene().lookup("#check-toolchain")).isDisabled()));
            require(
                    UiSmoke.fx(() ->
                                    ((TextArea) stage.getScene().lookup("#compiler-diagnostics"))
                                            .getText())
                            .contains("OK latexmk"),
                    "toolchain diagnostic must show installed latexmk");
            System.out.println(
                    "PASS settings validation, persistence and asynchronous diagnostics");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "openProject", backend.projects().get(0));
                return null;
            });
            String source =
                    "\\documentclass{article}\n\\begin{document}\nHello compiler!\n\\end{document}\n";
            build(app, source);
            require(
                    UiSmoke.fx(() -> UiSmoke.field(app, "lastPdf")) != null,
                    "successful compile must expose a PDF");
            System.out.println("PASS compile from JavaFX UI produces PDF");
            AppSettings live = UiSmoke.field(app, "settings");
            UiSmoke.fx(() -> {
                live.set(AppSettings.LANGUAGE, "vi");
                return null;
            });
            UiSmoke.fx(() -> null);
            build(app, source.replace("Hello compiler!", "Hello \\texbf{broken}!"));
            String log =
                    UiSmoke.fx(() -> ((TextArea) UiSmoke.field(app, "terminalLogArea")).getText());
            require(
                    log.contains("[EXPLAIN] Lệnh không tồn tại"),
                    "Vietnamese explanation must reach Build output");
            System.out.println("PASS Vietnamese Error Explainer visible in Build output");
            UiSmoke.fx(() -> {
                ((TextArea) UiSmoke.field(app, "codeEditor"))
                        .setText(source.replace("Hello compiler!", "\\loop\\iftrue\\repeat"));
                UiSmoke.invoke(app, "triggerRecompile");
                return null;
            });
            waitFor(() ->
                    UiSmoke.field(UiSmoke.field(app, "compilerService"), "currentProcess") != null);
            UiSmoke.fx(() -> {
                ((Button) stage.getScene().lookup("#stop-compile")).fire();
                return null;
            });
            waitFor(() ->
                    UiSmoke.fx(() -> !((Button) UiSmoke.field(app, "btnRecompile")).isDisabled()));
            require(
                    UiSmoke.fx(() ->
                                    ((javafx.scene.control.Label) UiSmoke.field(app, "statusLabel"))
                                            .getText())
                            .equals("Compile stopped."),
                    "Stop must unlock the UI");
            System.out.println("PASS Stop terminates unlimited compile and unlocks UI");
            System.out.println("COMPILER_UI_SMOKE_OK");
        } finally {
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "cleanupOnExit");
                stage.hide();
                return null;
            });
            Platform.exit();
        }
    }

    private static void build(OverleafFxApp app, String source) throws Exception {
        UiSmoke.fx(() -> {
            ((TextArea) UiSmoke.field(app, "codeEditor")).setText(source);
            UiSmoke.invoke(app, "triggerRecompile");
            return null;
        });
        waitFor(() ->
                UiSmoke.fx(() -> !((Button) UiSmoke.field(app, "btnRecompile")).isDisabled()));
    }

    private interface Check {
        boolean get() throws Exception;
    }

    private static void waitFor(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < deadline) {
            if (check.get()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for compiler UI");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}

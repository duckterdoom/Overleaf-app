package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.LocalBackend;
import com.overleaf.client.workspace.ProjectHistory;
import java.nio.file.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * Real JavaFX, native compilation, persisted history and layout transitions in an isolated home.
 */
public final class HistoryLayoutUiSmoke {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(home)) throw new IllegalArgumentException("Use a new home");
        System.setProperty("overleaf.home", home.toString());
        LocalBackend backend = new LocalBackend(home);
        backend.register("History Test", "history@example.test", "test-password", true).join();
        var project = backend.create("History project");
        Path root = Path.of(project.folder());
        String original =
                "\\documentclass{article}\n\\begin{document}\nOriginal text\n\\end{document}\n";
        Files.writeString(root.resolve("main.tex"), original);
        new AppSettings(backend).set(AppSettings.LANGUAGE, "vi");
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Platform.startup(() -> ready.complete(null));
        ready.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        OverleafFxApp app = UiSmoke.fx(OverleafFxApp::new);
        Stage stage =
                UiSmoke.fx(() -> {
                    Stage s = new Stage();
                    s.setOpacity(0);
                    app.start(s);
                    UiSmoke.invoke(app, "openProject", project);
                    return s;
                });
        TextArea editor = UiSmoke.field(app, "codeEditor");
        PdfPreviewPane preview = UiSmoke.field(app, "pdfPreviewPane");
        try {
            UiSmoke.fx(() -> {
                menu(stage, "compile-mode", "compile-auto");
                editor.setText(original.replace("Original", "Version A"));
                return null;
            });
            Thread.sleep(180);
            UiSmoke.fx(() -> {
                editor.setText(original.replace("Original", "Version B"));
                return null;
            });
            Thread.sleep(180);
            UiSmoke.fx(() -> {
                editor.setText(original.replace("Original", "Version C"));
                return null;
            });
            waitFor(() -> UiSmoke.fx(preview::canExport));
            require(buildCount(app) == 1, "Typing burst was not debounced");
            require(
                    Files.readString(root.resolve("main.tex")).contains("Version C"),
                    "Auto compile did not save latest text");
            UiSmoke.fx(() -> {
                menu(stage, "compile-mode", "compile-manual");
                return null;
            });
            Thread.sleep(100);
            UiSmoke.fx(() -> {
                editor.setText(original.replace("Original", "Version D"));
                return null;
            });
            Thread.sleep(1600);
            require(buildCount(app) == 1, "Manual mode compiled automatically");
            Thread.sleep(1800);
            require(
                    Files.readString(root.resolve("main.tex")).contains("Version C"),
                    "History checkpoint unexpectedly saved the working file");
            require(
                    new ProjectHistory(root)
                            .revisions().stream()
                                    .anyMatch(r -> {
                                        try {
                                            return new ProjectHistory(root)
                                                    .read(r)
                                                    .contains("Version D");
                                        } catch (Exception failure) {
                                            throw new RuntimeException(failure);
                                        }
                                    }),
                    "Idle unsaved checkpoint missing");
            UiSmoke.fx(() -> {
                click(stage, "recompile-now");
                return null;
            });
            waitFor(() -> UiSmoke.fx(preview::canExport));
            require(buildCount(app) == 2, "Manual Recompile failed");
            UiSmoke.fx(() -> {
                click(stage, "workspace-history");
                return null;
            });
            waitFor(() ->
                    UiSmoke.fx(() ->
                            stage.getScene().lookup("#history-view") != null
                                    && ((ListView<?>) stage.getScene().lookup("#history-timeline"))
                                                    .getItems()
                                                    .size()
                                            >= 3));
            ProjectHistory history = new ProjectHistory(root);
            var first =
                    history.revisions().stream()
                            .filter(r -> {
                                try {
                                    return history.read(r).contains("Original text");
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            })
                            .findFirst()
                            .orElseThrow();
            UiSmoke.fx(() -> {
                timeline(stage).getSelectionModel().select(first);
                return null;
            });
            waitFor(() ->
                    UiSmoke.fx(() ->
                            !((Button) stage.getScene().lookup("#history-restore")).isDisabled()));
            UiSmoke.fx(() -> {
                click(stage, "history-label");
                field(stage, "history-label-name").setText("Bản đầu tiên");
                stage
                        .getScene()
                        .getRoot()
                        .lookupAll(".modal-cancel")
                        .iterator()
                        .next()
                        .getParent()
                        .getChildrenUnmodifiable()
                        .stream()
                        .filter(n -> n instanceof Button b && "Lưu".equals(b.getText()))
                        .map(n -> (Button) n)
                        .findFirst()
                        .orElseThrow()
                        .fire();
                return null;
            });
            waitFor(() ->
                    history.revisions().stream().anyMatch(r -> r.label().equals("Bản đầu tiên")));
            UiSmoke.fx(() -> {
                ((ToggleButton) stage.getScene().lookup("#history-labels")).fire();
                return null;
            });
            waitFor(() ->
                    UiSmoke.fx(() ->
                            timeline(stage).getItems().size() == 1
                                    && !((Button) stage.getScene().lookup("#history-restore"))
                                            .isDisabled()));
            UiSmoke.fx(() -> {
                screenshot(stage, home.resolve("history.png"));
                return null;
            });
            Files.writeString(
                    root.resolve("main.tex"),
                    original.replace("Original text", "External text before restore"));
            UiSmoke.fx(() -> {
                click(stage, "history-restore");
                click(stage, "history-confirm-restore");
                return null;
            });
            waitFor(() -> UiSmoke.fx(() -> stage.getScene().lookup("#history-view") == null));
            require(
                    Files.readString(root.resolve("main.tex")).equals(original),
                    "Restore content incorrect");
            require(
                    history.revisions().stream()
                            .anyMatch(r -> {
                                try {
                                    return history.read(r).contains("External text before restore");
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }),
                    "Before-restore backup missing");
            UiSmoke.fx(() -> {
                SplitPane split = UiSmoke.field(app, "workspaceSplit");
                menu(stage, "workspace-layout", "layout-editor");
                require(
                        split.getItems().size() == 2 && !split.getItems().contains(preview),
                        "Editor-only layout failed");
                menu(stage, "workspace-layout", "layout-pdf");
                require(
                        split.getItems().size() == 1 && split.getItems().getFirst() == preview,
                        "PDF-only layout failed");
                menu(stage, "workspace-layout", "layout-split");
                require(split.getItems().size() == 3, "Split layout failed");
                menu(stage, "workspace-layout", "layout-focus");
                require(split.getItems().size() == 2, "Focus sidebar still visible");
                require(
                        ((SplitPane) UiSmoke.field(app, "editorVerticalSplit")).getItems().size()
                                == 1,
                        "Focus build output visible");
                menu(stage, "workspace-layout", "layout-focus");
                require(split.getItems().size() == 3, "Focus exit failed");
                menu(stage, "workspace-layout", "layout-separate");
                Stage popup = UiSmoke.field(app, "detachedPdfStage");
                popup.setOpacity(0);
                require(
                        preview.getScene() == popup.getScene()
                                && !split.getItems().contains(preview),
                        "PDF was not detached");
                popup.getOnCloseRequest()
                        .handle(new WindowEvent(popup, WindowEvent.WINDOW_CLOSE_REQUEST));
                popup.close();
                require(
                        split.getItems().contains(preview)
                                && preview.getScene() == stage.getScene(),
                        "PDF not reattached");
                screenshot(stage, home.resolve("layout.png"));
                return null;
            });
            require(
                    !new AppSettings(backend).bool(AppSettings.AUTO_COMPILE),
                    "Manual preference not saved");
            require(
                    "split".equals(new AppSettings(backend).get(AppSettings.LAYOUT)),
                    "Layout preference not saved");
            System.out.println(
                    "HISTORY_LAYOUT_UI_SMOKE_OK auto debounce/manual compile, persisted history/labels/restore backup, all layouts/focus/detach");
        } finally {
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "cleanupOnExit");
                stage.hide();
                return null;
            });
            Platform.exit();
        }
    }

    @SuppressWarnings("unchecked")
    private static ListView<ProjectHistory.Revision> timeline(Stage stage) {
        layout(stage);
        return (ListView<ProjectHistory.Revision>) stage.getScene().lookup("#history-timeline");
    }

    private static void menu(Stage stage, String buttonId, String itemId) {
        layout(stage);
        MenuButton button = (MenuButton) stage.getScene().lookup("#" + buttonId);
        button.show();
        MenuItem item =
                button.getItems().stream()
                        .filter(i -> itemId.equals(i.getId()))
                        .findFirst()
                        .orElseThrow();
        button.hide();
        if (item instanceof CheckMenuItem check) check.setSelected(!check.isSelected());
        item.fire();
    }

    private static int buildCount(OverleafFxApp app) throws Exception {
        return UiSmoke.fx(() -> {
            TextArea log = UiSmoke.field(app, "terminalLogArea");
            return (int)
                    log.getText()
                            .lines()
                            .filter(line -> line.startsWith("[BUILD] Started"))
                            .count();
        });
    }

    private static void layout(Stage stage) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private static void click(Stage stage, String id) {
        layout(stage);
        ((Button) stage.getScene().lookup("#" + id)).fire();
    }

    private static TextField field(Stage stage, String id) {
        layout(stage);
        return (TextField) stage.getScene().lookup("#" + id);
    }

    private static void require(boolean okay, String why) {
        if (!okay) throw new AssertionError(why);
    }

    private static void waitFor(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (!condition.call()) {
            if (System.nanoTime() > end)
                throw new AssertionError("Timed out waiting for history/layout operation");
            Thread.sleep(80);
        }
    }

    private static void screenshot(Stage stage, Path file) throws Exception {
        layout(stage);
        var image = stage.getScene().snapshot(null);
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader()
                .getPixels(
                        0,
                        0,
                        w,
                        h,
                        javafx.scene.image.PixelFormat.getIntArgbInstance(),
                        pixels,
                        0,
                        w);
        var out =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        javax.imageio.ImageIO.write(out, "png", file.toFile());
    }
}

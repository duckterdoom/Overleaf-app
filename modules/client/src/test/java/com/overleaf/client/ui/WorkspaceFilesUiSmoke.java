package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.LocalBackend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Real controls and file IO in an isolated home; does not alter the system clipboard. */
public final class WorkspaceFilesUiSmoke {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(home)) throw new IllegalArgumentException("Use a fresh test home");
        System.setProperty("overleaf.home", home.toString());
        LocalBackend backend = new LocalBackend(home);
        backend.register("Workspace Test", "workspace@example.test", "test-password", true).join();
        new AppSettings(backend).set(AppSettings.LANGUAGE, "vi");
        var project = backend.create("Workspace project");
        Path root = Path.of(project.folder());
        var other = backend.create("Another project");
        Files.writeString(
                Path.of(other.folder(), "chapter.tex"), "\\section{Tiếng Việt từ dự án khác}");
        Files.createDirectories(Path.of(other.folder(), "build"));
        Files.write(Path.of(other.folder(), "build/main.pdf"), new byte[] {1, 2, 3, 4});
        Path upload = Files.createDirectories(home.resolve("fixtures/images/empty")).getParent();
        byte[] imageBytes =
                Base64.getDecoder()
                        .decode(
                                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j7XcAAAAASUVORK5CYII=");
        Files.write(upload.resolve("plot.png"), imageBytes);
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> started.complete(null));
        started.get(10, TimeUnit.SECONDS);
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
        try {
            UiSmoke.fx(() -> {
                ((Button) stage.getScene().lookup("#workspace-new-folder")).fire();
                field(stage, "add-files-name").setText("Chương một");
                submit(stage);
                return null;
            });
            if (!Files.isDirectory(root.resolve("Chương một")))
                throw new AssertionError("Folder not created");
            UiSmoke.fx(() -> {
                Map<?, ?> paths = (Map<?, ?>) UiSmoke.field(app, "treePaths");
                if (!paths.containsValue("Chương một"))
                    throw new AssertionError("Empty folder hidden");
                UiSmoke.invoke(app, "selectTreePath", "Chương một");
                ((Button) stage.getScene().lookup("#workspace-add-files")).fire();
                field(stage, "add-files-name").setText("giới thiệu");
                submit(stage);
                return null;
            });
            if (!Files.exists(root.resolve("Chương một/giới thiệu.tex")))
                throw new AssertionError("Selected folder ignored");
            UiSmoke.fx(() -> {
                Object dialog = UiSmoke.invoke(app, "showAddFilesDialog", 2, "Chương một");
                UiSmoke.invoke(dialog, "enqueue", List.of(upload));
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                snapshot(stage, home.resolve("upload-dialog.png"));
                submit(stage);
                return null;
            });
            await(() -> Files.isRegularFile(root.resolve("Chương một/images/plot.png")));
            closed(stage);
            if (!Arrays.equals(
                    imageBytes, Files.readAllBytes(root.resolve("Chương một/images/plot.png"))))
                throw new AssertionError("Image changed");
            UiSmoke.fx(() -> {
                Map<?, ?> paths = (Map<?, ?>) UiSmoke.field(app, "treePaths");
                if (!paths.containsValue("Chương một/images/plot.png")
                        || !paths.containsValue("Chương một/images/empty"))
                    throw new AssertionError("Upload not in tree");
                if (!"Chương một/giới thiệu.tex".equals(UiSmoke.field(app, "currentFile")))
                    throw new AssertionError("Binary opened in editor");
                Object dialog = UiSmoke.invoke(app, "showAddFilesDialog", 2, "");
                WritableImage image = new WritableImage(2, 2);
                image.getPixelWriter().setArgb(0, 0, 0xff123456);
                UiSmoke.invoke(dialog, "enqueueImage", image);
                submit(stage);
                return null;
            });
            closed(stage);
            Path pasted;
            try (var list = Files.list(root)) {
                pasted =
                        list.filter(p -> p.getFileName().toString().startsWith("pasted-image-"))
                                .findFirst()
                                .orElseThrow();
            }
            if (javax.imageio.ImageIO.read(pasted.toFile()).getRGB(0, 0) != 0xff123456)
                throw new AssertionError("Pasted pixel changed");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "showAddFilesDialog", 3, "Chương một");
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                ComboBox<Object> projects =
                        (ComboBox<Object>) stage.getScene().lookup("#add-files-source-project");
                projects.setValue(other);
                ComboBox<String> files =
                        (ComboBox<String>) stage.getScene().lookup("#add-files-source-file");
                files.setValue("chapter.tex");
                field(stage, "add-files-copy-name").setText("copied.tex");
                snapshot(stage, home.resolve("copy-dialog.png"));
                submit(stage);
                return null;
            });
            await(() -> Files.exists(root.resolve("Chương một/copied.tex")));
            closed(stage);
            if (!Files.readString(root.resolve("Chương một/copied.tex")).contains("Tiếng Việt"))
                throw new AssertionError("Copy content changed");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "showAddFilesDialog", 3, "Chương một");
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                ((ComboBox<Object>) stage.getScene().lookup("#add-files-source-project"))
                        .setValue(other);
                ((CheckBox) stage.getScene().lookup("#add-files-output-files")).fire();
                ComboBox<String> files =
                        (ComboBox<String>) stage.getScene().lookup("#add-files-source-file");
                if (!files.getItems().equals(List.of("build/main.pdf")))
                    throw new AssertionError("Output selection incorrect");
                files.setValue("build/main.pdf");
                field(stage, "add-files-copy-name").setText("attachments/reference.pdf");
                submit(stage);
                return null;
            });
            await(() -> Files.exists(root.resolve("Chương một/attachments/reference.pdf")));
            closed(stage);
            if (!Arrays.equals(
                    new byte[] {1, 2, 3, 4},
                    Files.readAllBytes(root.resolve("Chương một/attachments/reference.pdf"))))
                throw new AssertionError("Output bytes changed");
            UiSmoke.fx(() -> {
                Object dialog = UiSmoke.invoke(app, "showAddFilesDialog", 0, "Chương một");
                field(stage, "add-files-name").setText("copied.tex");
                submit(stage);
                if (stage.getScene().lookup("#add-files-submit") == null)
                    throw new AssertionError("Duplicate not rejected");
                ((UiModal) UiSmoke.field(dialog, "modal")).close();
                return null;
            });
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "setVietnameseUi", false);
                return null;
            });
            await(() ->
                    UiSmoke.fx(() ->
                            ((StackPane) UiSmoke.field(app, "screenHost")).getChildren().get(0)
                                    instanceof HomeView));
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "openProject", project);
                Object dialog = UiSmoke.invoke(app, "showAddFilesDialog", 0, "");
                stage.getScene().getRoot().applyCss();
                stage.getScene().getRoot().layout();
                ListView<?> navigation =
                        (ListView<?>) stage.getScene().lookup("#add-files-navigation");
                if (!"New file".equals(navigation.getItems().get(0)))
                    throw new AssertionError("English translation missing");
                if (!"Add files"
                        .equals(
                                ((Button) stage.getScene().lookup("#workspace-add-files"))
                                        .getText()))
                    throw new AssertionError("Sidebar translation stale");
                snapshot(stage, home.resolve("new-file-dialog.png"));
                ((UiModal) UiSmoke.field(dialog, "modal")).close();
                return null;
            });
            if (args.length > 1) {
                UiSmoke.fx(() -> {
                    UiSmoke.invoke(app, "showAddFilesDialog", 4, "");
                    field(stage, "add-files-url").setText(args[1]);
                    field(stage, "add-files-copy-name").setText("downloaded.md");
                    submit(stage);
                    return null;
                });
                await(() -> Files.exists(root.resolve("downloaded.md")));
                closed(stage);
                if (!Files.readString(root.resolve("downloaded.md"))
                        .toLowerCase(Locale.ROOT)
                        .contains("pandoc")) throw new AssertionError("URL file content incorrect");
                System.out.println("HTTPS_DOWNLOAD_OK");
            }
            System.out.println(
                    "WORKSPACE_FILES_UI_SMOKE_OK folders, selected destination, upload hierarchy/binary, pasted image pixels, project/output copy, duplicate rejection, vi/en");
        } finally {
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "cleanupOnExit");
                stage.hide();
                return null;
            });
            Platform.exit();
        }
    }

    private static TextField field(Stage stage, String id) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        return (TextField) stage.getScene().lookup("#" + id);
    }

    private static void submit(Stage stage) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        ((Button) stage.getScene().lookup("#add-files-submit")).fire();
    }

    private static void closed(Stage stage) throws Exception {
        await(() -> UiSmoke.fx(() -> stage.getScene().lookup("#add-files-submit") == null));
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (!condition.call()) {
            if (System.nanoTime() > end) throw new AssertionError("Timed out");
            Thread.sleep(70);
        }
    }

    private static void snapshot(Stage stage, Path path) throws Exception {
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
        var buffered =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, w, h, pixels, 0, w);
        javax.imageio.ImageIO.write(buffered, "png", path.toFile());
    }
}

package com.overleaf.client.ui;

import com.overleaf.client.compiler.*;
import com.overleaf.client.ui.backend.LocalBackend;
import java.nio.file.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** End-to-end controls: source button, PDF double-click, included file and stale guards. */
public final class SyncTexUiSmoke {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(home)) throw new IllegalArgumentException("Use a new home");
        System.setProperty("overleaf.home", home.toString());
        LocalBackend backend = new LocalBackend(home);
        backend.register("Sync test", "sync@example.test", "test-password", true).join();
        new AppSettings(backend).set(AppSettings.LATEXMK_PATH, args[1]);
        var project = backend.create("Luận văn SyncTeX");
        Path root = Path.of(project.folder());
        Files.createDirectories(root.resolve("chapters"));
        Files.writeString(
                root.resolve("main.tex"),
                "\\documentclass{article}\n\\begin{document}\nFirst page.\n\\newpage\nSecond page.\n\\newpage\n\\input{chapters/section one.tex}\n\\end{document}\n");
        Files.writeString(
                root.resolve("chapters/section one.tex"),
                "\\section{Target chapter}\n\n\nTarget paragraph for synchronization. This sentence maps back to the included source.\n");
        CompletableFuture<Void> startup = new CompletableFuture<>();
        Platform.startup(() -> startup.complete(null));
        startup.get(10, TimeUnit.SECONDS);
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
            PdfPreviewPane pane = UiSmoke.field(app, "pdfPreviewPane");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "triggerRecompile");
                return null;
            });
            waitFor(() -> UiSmoke.fx(() -> pane.canExport() && pane.hasRenderedPage()));
            SyncTexService index = UiSmoke.field(app, "syncTex");
            if (index == null) throw new AssertionError("Missing SyncTeX index");
            var point =
                    pane.snapshot()
                            .syncDestination(index.forward("chapters/section one.tex", 4, 0));
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "openFile", "chapters/section one.tex");
                TextArea editor = UiSmoke.field(app, "codeEditor");
                editor.positionCaret(editor.getText().indexOf("Target paragraph"));
                ((Button) stage.getScene().lookup("#pdf-sync-forward")).fire();
                return null;
            });
            waitFor(() -> UiSmoke.fx(() -> pane.pageIndex() == 2 && pane.hasRenderedPage()));
            var sceneImage = UiSmoke.fx(() -> stage.getScene().snapshot(null));
            var screenshot =
                    new java.awt.image.BufferedImage(
                            (int) sceneImage.getWidth(),
                            (int) sceneImage.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_ARGB);
            int[] pixels = new int[screenshot.getWidth() * screenshot.getHeight()];
            sceneImage
                    .getPixelReader()
                    .getPixels(
                            0,
                            0,
                            screenshot.getWidth(),
                            screenshot.getHeight(),
                            javafx.scene.image.PixelFormat.getIntArgbInstance(),
                            pixels,
                            0,
                            screenshot.getWidth());
            screenshot.setRGB(
                    0,
                    0,
                    screenshot.getWidth(),
                    screenshot.getHeight(),
                    pixels,
                    0,
                    screenshot.getWidth());
            javax.imageio.ImageIO.write(
                    screenshot, "png", home.resolve("synctex-preview.png").toFile());
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "openFile", "main.tex");
                ((AppSettings) UiSmoke.field(app, "settings")).set(AppSettings.LAYOUT, "pdf");
                return null;
            });
            UiSmoke.fx(() -> {
                Region sheet = (Region) stage.getScene().lookup("#pdf-sheet-2");
                double x = point.x() * sheet.getWidth(), y = point.y() * sheet.getHeight();
                var scene = sheet.localToScene(x, y);
                sheet.fireEvent(
                        new MouseEvent(
                                MouseEvent.MOUSE_CLICKED,
                                scene.getX(),
                                scene.getY(),
                                scene.getX(),
                                scene.getY(),
                                MouseButton.PRIMARY,
                                2,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false,
                                true,
                                false,
                                true,
                                new PickResult(sheet, x, y)));
                return null;
            });
            waitFor(() ->
                    UiSmoke.fx(() ->
                            "chapters/section one.tex".equals(UiSmoke.field(app, "currentFile"))));
            UiSmoke.fx(() -> {
                TextArea editor = UiSmoke.field(app, "codeEditor");
                if (!"split"
                        .equals(
                                ((AppSettings) UiSmoke.field(app, "settings"))
                                        .get(AppSettings.LAYOUT)))
                    throw new AssertionError("Inverse search did not reveal editor");
                int line =
                        (int)
                                        editor.getText()
                                                .substring(0, editor.getCaretPosition())
                                                .chars()
                                                .filter(c -> c == '\n')
                                                .count()
                                + 1;
                if (line != 4) throw new AssertionError("Wrong source line " + line);
                editor.appendText("\nChanged source.");
                if (!((Button) stage.getScene().lookup("#pdf-sync-forward")).isDisabled())
                    throw new AssertionError("Stale SyncTeX enabled");
                if (pane.canExport()) throw new AssertionError("Stale export enabled");
                return null;
            });
            System.out.println(
                    "SYNCTEX_UI_SMOKE_OK forward button, PDF double click, Unicode root/included file, line 4, stale guard");
        } finally {
            UiSmoke.fx(() -> {
                app.stop();
                stage.hide();
                return null;
            });
            Platform.exit();
        }
    }

    private static void waitFor(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline) {
            if (condition.call()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("SyncTeX UI timed out");
    }
}

package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.LocalBackend;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javax.imageio.ImageIO;

/** Explicit real-PDF/display smoke; writes only to a fresh isolated home. */
public final class PdfPreviewUiSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("Usage: PdfPreviewUiSmoke <source.tex> <new-home>");
        String source = Files.readString(Path.of(args[0]));
        Path home = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(home)) throw new IllegalArgumentException("Use a new home.");
        System.setProperty("overleaf.home", home.toString());
        LocalBackend backend = new LocalBackend(home);
        backend.register("PDF Test", "pdf@example.test", "test-password", true).join();
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
                    return s;
                });
        try {
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "openProject", backend.projects().get(0));
                return null;
            });
            PdfPreviewPane preview = UiSmoke.field(app, "pdfPreviewPane");
            require(!UiSmoke.fx(preview::canExport), "unverified files must not enable export");
            build(app, source);
            waitFor(() -> UiSmoke.fx(() -> preview.canExport() && preview.hasRenderedPage()));
            var snapshot = UiSmoke.fx(preview::snapshot);
            require(snapshot.pages() > 1, "use a multipage document");
            checkPixels(pageImage(stage), snapshot.render(0, 1.5f));
            System.out.println(
                    "PASS first page matches real PDF pixels; pages=" + snapshot.pages());
            Path export = home.resolve("exported.pdf");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "exportVerifiedPdf", export, false);
                return null;
            });
            waitFor(() -> Files.isRegularFile(export));
            Path original = UiSmoke.field(app, "lastPdf");
            require(
                    Files.mismatch(original, export) == -1,
                    "export must be byte-identical to compiled PDF");
            System.out.println("PASS export is byte-identical to preview PDF");
            UiSmoke.fx(() -> {
                ((Button) stage.getScene().lookup("#pdf-next")).fire();
                return null;
            });
            waitFor(() -> UiSmoke.fx(() -> preview.pageIndex() == 1 && preview.hasRenderedPage()));
            checkPixels(pageImage(stage), snapshot.render(1, 1.5f));
            for (int index = 2; index <= Math.min(3, snapshot.pages() - 1); index++) {
                int expected = index;
                UiSmoke.fx(() -> {
                    ((Button) stage.getScene().lookup("#pdf-next")).fire();
                    return null;
                });
                waitFor(() ->
                        UiSmoke.fx(() ->
                                preview.pageIndex() == expected && preview.hasRenderedPage()));
                checkPixels(pageImage(stage), snapshot.render(expected, 1.5f));
            }
            screenshot(stage, home.resolve("preview.png"));
            System.out.println("PASS page navigation and second page pixels");
            String broken =
                    source.replace(
                            "\\end{document}", "\\deliberatelyUnknownCommand\n\\end{document}");
            UiSmoke.fx(() -> {
                ((TextArea) UiSmoke.field(app, "codeEditor")).setText(broken);
                return null;
            });
            require(!UiSmoke.fx(preview::canExport), "editing must immediately disable export");
            Path blocked = home.resolve("must-not-export.pdf");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "exportVerifiedPdf", blocked, false);
                return null;
            });
            require(!Files.exists(blocked), "stale PDF must not be exported");
            build(app, broken);
            require(!UiSmoke.fx(preview::canExport), "failed compile must disable export");
            require(
                    UiSmoke.fx(preview::snapshot) == snapshot,
                    "keep last successful immutable snapshot on error");
            require(
                    UiSmoke.fx(preview::hasRenderedPage),
                    "old PDF remains visible with stale label");
            System.out.println("PASS stale/error preview retains last good PDF and blocks export");
            build(
                    app,
                    source.replace("\\end{document}", "New successful revision.\n\\end{document}"));
            waitFor(() -> UiSmoke.fx(() -> preview.canExport() && preview.hasRenderedPage()));
            require(
                    UiSmoke.fx(preview::snapshot) != snapshot,
                    "successful compile must replace snapshot");
            var other = backend.create("Other project");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "openProject", other);
                return null;
            });
            require(
                    !UiSmoke.fx(preview::canExport) && UiSmoke.fx(preview::snapshot) == null,
                    "isolate projects");
            System.out.println("PASS recovery and project isolation\nPDF_PREVIEW_UI_SMOKE_OK");
        } finally {
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "cleanupOnExit");
                stage.hide();
                return null;
            });
            Platform.exit();
        }
    }

    private static Image pageImage(Stage stage) throws Exception {
        return UiSmoke.fx(
                () -> ((ImageView) stage.getScene().lookup("#pdf-page-image")).getImage());
    }

    private static void checkPixels(Image image, BufferedImage expected) {
        require(
                image != null
                        && (int) image.getWidth() == expected.getWidth()
                        && (int) image.getHeight() == expected.getHeight(),
                "page dimensions must match");
        for (int y = 0; y < expected.getHeight(); y += 17)
            for (int x = 0; x < expected.getWidth(); x += 17)
                require(
                        image.getPixelReader().getArgb(x, y) == expected.getRGB(x, y),
                        "pixels must match at " + x + "," + y);
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
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
        while (System.nanoTime() < until) {
            if (check.get()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for PDF preview");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void screenshot(Stage stage, Path output) throws Exception {
        Image image = UiSmoke.fx(() -> stage.getScene().snapshot(null));
        BufferedImage pixels =
                new BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++)
            for (int x = 0; x < pixels.getWidth(); x++)
                pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        ImageIO.write(pixels, "png", output.toFile());
    }
}

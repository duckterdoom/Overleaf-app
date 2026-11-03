package com.overleaf.client.ui;

import com.overleaf.client.compiler.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Reproducible local workload; no claim of speed relative to Overleaf Cloud. */
public final class PdfPerformanceSmoke {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toAbsolutePath(), root = home.resolve("project");
        if (Files.exists(home)) throw new IllegalArgumentException("Use a new output directory");
        Files.createDirectories(root.resolve("chapters"));
        Files.createDirectories(root.resolve("images"));
        StringBuilder main =
                new StringBuilder(
                        "\\documentclass[11pt]{article}\n\\usepackage[margin=25mm]{geometry}\n\\usepackage{graphicx,tikz,hyperref}\n\\begin{document}\n\\tableofcontents\\clearpage\n");
        for (int i = 0; i < 12; i++) {
            var image =
                    new java.awt.image.BufferedImage(
                            800, 450, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var random = new Random(1234 + i);
            for (int y = 0; y < 450; y++)
                for (int x = 0; x < 800; x++)
                    image.setRGB(
                            x,
                            y,
                            java.awt.Color.HSBtoRGB(
                                    (x / 800f + i / 12f) % 1,
                                    .3f,
                                    .65f + random.nextFloat() * .35f));
            javax.imageio.ImageIO.write(
                    image, "png", root.resolve("images/chart" + i + ".png").toFile());
        }
        String paragraph =
                "This reproducible paragraph exercises local PDF rendering, embedded fonts, cross references, and page navigation. The measured document is synthetic and does not represent every thesis or book. See Section~\\ref{sec:1}.\\par\n";
        for (int i = 1; i <= 120; i++) {
            String chapter =
                    "\\section{Benchmark chapter "
                            + i
                            + "}\\label{sec:"
                            + i
                            + "}\n"
                            + paragraph.repeat(5)
                            + "\\begin{center}\\includegraphics[width=8cm,height=3cm]{images/chart"
                            + (i % 12)
                            + ".png}\\end{center}\n"
                            + "\\begin{tabular}{lrr}Stage & Samples & Value\\\\ First & 10 & 20\\\\ Warm & 15 & 30\\\\\\end{tabular}\n\\clearpage\n"
                            + "\\subsection{Detailed analysis "
                            + i
                            + "}\n"
                            + paragraph.repeat(6)
                            + "\\begin{tikzpicture}\\foreach \\x in {1,...,30} {\\draw (0,0) -- (\\x:1.3); }\\end{tikzpicture}\\clearpage\n";
            Files.writeString(root.resolve("chapters/ch" + i + ".tex"), chapter);
            main.append("\\input{chapters/ch").append(i).append(".tex}\n");
        }
        main.append("\\end{document}\n");
        Files.writeString(root.resolve("main.tex"), main);
        var first = SyncTexSmoke.compile(root, args[1], LatexCompilerService.Engine.PDFLATEX);
        List<Long> warm = new ArrayList<>(), edited = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            var result = SyncTexSmoke.compile(root, args[1], LatexCompilerService.Engine.PDFLATEX);
            if (!result.log().contains("Nothing to do") && !result.log().contains("up-to-date"))
                throw new AssertionError("Cache not reused");
            warm.add(result.durationMillis());
            Path last = root.resolve("chapters/ch120.tex");
            Files.writeString(
                    last,
                    Files.readString(last)
                            .replaceAll(
                                    "Detailed analysis 120[^}]*",
                                    "Detailed analysis 120 revision " + i));
            edited.add(
                    SyncTexSmoke.compile(root, args[1], LatexCompilerService.Engine.PDFLATEX)
                            .durationMillis());
        }
        long readStart = System.nanoTime();
        PdfSnapshot pdf = PdfSnapshot.read(first.pdfPath(), ProjectInputs.fingerprint(root));
        long readMs = elapsed(readStart);
        if (pdf.pages() < 200)
            throw new AssertionError("Expected at least 200 pages: " + pdf.pages());
        var largeIndex = SyncTexService.load(first.pdfPath(), args[1]);
        var lastPoint = largeIndex.forward("chapters/ch120.tex", 4, 0);
        if (lastPoint.page() < 200
                || !largeIndex
                        .inverse(lastPoint.page(), lastPoint.x(), lastPoint.y())
                        .file()
                        .equals("chapters/ch120.tex"))
            throw new AssertionError("Large-document SyncTeX failed");
        List<Long> reopen = new ArrayList<>(), reused = new ArrayList<>();
        // Same pages/scale, alternating order, 24 samples each; pixel comparison prevents
        // shortcuts.
        try (var session = pdf.openRenderer()) {
            for (int i = 0; i < 24; i++) {
                int page = (i * 31) % pdf.pages();
                java.awt.image.BufferedImage a, b;
                long start;
                if (i % 2 == 0) {
                    start = System.nanoTime();
                    a = pdf.render(page, 1.5f);
                    reopen.add(elapsed(start));
                    start = System.nanoTime();
                    b = session.renderPage(page, 1.5f).image();
                    reused.add(elapsed(start));
                } else {
                    start = System.nanoTime();
                    b = session.renderPage(page, 1.5f).image();
                    reused.add(elapsed(start));
                    start = System.nanoTime();
                    a = pdf.render(page, 1.5f);
                    reopen.add(elapsed(start));
                }
                if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
                    throw new AssertionError("Size differs");
                for (int y = 0; y < a.getHeight(); y += 13)
                    for (int x = 0; x < a.getWidth(); x += 13)
                        if (a.getRGB(x, y) != b.getRGB(x, y))
                            throw new AssertionError("Pixels differ");
                a.flush();
                b.flush();
            }
        }
        CompletableFuture<Void> startup = new CompletableFuture<>();
        Platform.startup(() -> startup.complete(null));
        startup.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        PdfPreviewPane pane = UiSmoke.fx(() -> new PdfPreviewPane(() -> {}));
        Stage stage =
                UiSmoke.fx(() -> {
                    Stage s = new Stage();
                    s.setOpacity(0);
                    s.setScene(new Scene(pane, 850, 850));
                    s.show();
                    return s;
                });
        List<Long> navigation = new ArrayList<>(), pulse = new ArrayList<>();
        int maxImages = 0;
        long maxPixels = 0;
        try {
            long start = System.nanoTime();
            UiSmoke.fx(() -> {
                pane.show(pdf, true);
                return null;
            });
            waitPage(pane, 0);
            long firstPreviewMs = elapsed(start);
            for (int i = 1; i <= 18; i++) {
                int page = i == 18 ? pdf.pages() - 1 : (i * 17) % pdf.pages();
                start = System.nanoTime();
                UiSmoke.fx(() -> {
                    UiSmoke.invoke(pane, "showPage", page);
                    return null;
                });
                long pulseStart = System.nanoTime();
                UiSmoke.fx(() -> null);
                pulse.add(elapsed(pulseStart));
                waitPage(pane, page);
                navigation.add(elapsed(start));
                long idleDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (UiSmoke.fx(() -> (Boolean) UiSmoke.field(pane, "busy"))) {
                    if (System.nanoTime() > idleDeadline)
                        throw new AssertionError("Renderer did not settle");
                    Thread.sleep(15);
                }
                var stats = UiSmoke.fx(pane::renderStats);
                maxImages = Math.max(maxImages, stats.retainedImages());
                maxPixels = Math.max(maxPixels, stats.retainedPixels());
                if (stats.retainedPixels() > 24_000_000 || stats.documentOpens() != 1)
                    throw new AssertionError("Viewer resource bound violated: " + stats);
            }
            Path exported = home.resolve("export.pdf");
            pdf.exportTo(exported);
            if (Files.mismatch(first.pdfPath(), exported) != -1)
                throw new AssertionError("Export differs");
            String report =
                    "# Local PDF benchmark\n\nWindows, Java "
                            + System.getProperty("java.version")
                            + "; synthetic 120 chapter files, 12 PNGs, TikZ, tables, hyperref. No Overleaf comparison.\n\n"
                            + "Pages: "
                            + pdf.pages()
                            + "; PDF bytes: "
                            + Files.size(first.pdfPath())
                            + "; metadata read ms: "
                            + readMs
                            + "\n\n"
                            + "Cold compile ms (n=1): "
                            + first.durationMillis()
                            + "\n\nWarm compile ms (n=5): "
                            + warm
                            + "; edit compile ms (n=5): "
                            + edited
                            + "\n\n"
                            + "| Metric | n | p50 ms | p95 ms |\n|---|---:|---:|---:|\n"
                            + row("Warm compile", warm)
                            + row("Small edit compile", edited)
                            + row("Reopen PDF each page", reopen)
                            + row("Reuse document/renderer", reused)
                            + row("UI navigation to rendered page", navigation)
                            + row("FX queue round trip", pulse)
                            + "\nFirst visible PDF ms: "
                            + firstPreviewMs
                            + "; max retained images: "
                            + maxImages
                            + "; max retained pixels: "
                            + maxPixels
                            + "; viewer document opens: "
                            + UiSmoke.fx(pane::renderStats).documentOpens()
                            + "\n\n"
                            + "Pixel samples matched between render strategies. Export byte-identical. Pixel bound covers retained JavaFX images only, not total JVM/native/GPU memory. FX queue round trip is not continuous frame latency. Cold n=1 and p95 from small samples are diagnostic, not a general performance guarantee.\n";
            Files.writeString(home.resolve("results.md"), report);
            System.out.println(report);
            System.out.println("PDF_PERFORMANCE_SMOKE_OK");
        } finally {
            UiSmoke.fx(() -> {
                pane.close();
                stage.hide();
                return null;
            });
            Platform.exit();
        }
    }

    private static long elapsed(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static String row(String name, List<Long> samples) {
        var sorted = samples.stream().sorted().toList();
        return "| "
                + name
                + " | "
                + samples.size()
                + " | "
                + sorted.get((int) Math.ceil(sorted.size() * .5) - 1)
                + " | "
                + sorted.get((int) Math.ceil(sorted.size() * .95) - 1)
                + " |\n";
    }

    private static void waitPage(PdfPreviewPane pane, int page) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
        while (System.nanoTime() < end) {
            if (UiSmoke.fx(() -> pane.pageIndex() == page && pane.hasRenderedPage())) return;
            Thread.sleep(15);
        }
        throw new AssertionError("Render timed out: " + page);
    }
}

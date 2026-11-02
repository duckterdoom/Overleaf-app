package com.overleaf.client.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Real-toolchain check for Vietnamese glyphs and PDF text in all three engines. */
public final class VietnamesePdfSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Usage: VietnamesePdfSmoke <source.tex> <new-output-dir>");
        String source = Files.readString(Path.of(args[0]));
        Path output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output)) throw new IllegalArgumentException("Use a new output directory.");
        Files.createDirectories(output);
        LatexCompilerService service = new LatexCompilerService();
        service.configure(new LatexCompilerService.Options(LatexToolchain.resolve(""), 120));
        try {
            for (var engine : LatexCompilerService.Engine.values()) {
                Path root = Files.createDirectory(output.resolve(engine.name()));
                Files.writeString(root.resolve("main.tex"), source);
                CompletableFuture<LatexCompilerService.CompileResult> done =
                        new CompletableFuture<>();
                service.compileAsync(
                        root.toString(),
                        "main.tex",
                        engine,
                        new LatexCompilerService.CompileCallback() {
                            @Override
                            public void onSuccess(LatexCompilerService.CompileResult result) {
                                done.complete(result);
                            }

                            @Override
                            public void onError(int code, String log) {
                                try {
                                    Files.writeString(root.resolve("diagnostic.log"), log);
                                } catch (Exception failure) {
                                    done.completeExceptionally(failure);
                                    return;
                                }
                                done.completeExceptionally(
                                        new IllegalStateException(engine + " exit=" + code));
                            }
                        });
                var result = done.get(140, TimeUnit.SECONDS);
                Files.writeString(root.resolve("diagnostic.log"), result.log());
                String text;
                try (var pdf = Loader.loadPDF(result.pdfPath().toFile())) {
                    text =
                            Normalizer.normalize(
                                            new PDFTextStripper().getText(pdf), Normalizer.Form.NFC)
                                    .replaceAll("\\s+", " ");
                }
                for (String sample : List.of("Tài liệu", "Định lý", "Tiếng Việt")) {
                    if (!text.contains(sample))
                        throw new AssertionError(engine + " PDF text is missing: " + sample);
                }
                Files.writeString(root.resolve("extracted-text.txt"), text);
                PdfSnapshot pdf = PdfSnapshot.read(result.pdfPath(), result.inputFingerprint());
                ImageIO.write(pdf.render(0, 1.5f), "png", root.resolve("page1.png").toFile());
                System.out.println(
                        "VIETNAMESE_PDF_OK "
                                + engine
                                + " pages="
                                + pdf.pages()
                                + " missing-glyphs=0 text-samples=3");
            }
        } finally {
            service.shutdown();
        }
    }
}

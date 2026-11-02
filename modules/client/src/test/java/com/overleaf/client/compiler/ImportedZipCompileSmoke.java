package com.overleaf.client.compiler;

import com.overleaf.client.ui.backend.LocalBackend;
import com.overleaf.client.ui.backend.NewProjectRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Explicit real-TeX regression for the user's flat multi-chapter ZIP; preserves the input. */
public final class ImportedZipCompileSmoke {
    public static void main(String[] args) throws Exception {
        Path zip = Path.of(args[0]).toAbsolutePath(), home = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(home)) throw new IllegalArgumentException("Use a fresh test home");
        LocalBackend backend = new LocalBackend(home);
        var project =
                backend.create(
                        new NewProjectRequest(
                                "Imported ZIP",
                                null,
                                null,
                                null,
                                NewProjectRequest.Kind.ZIP,
                                zip,
                                null,
                                null,
                                true));
        Path root = Path.of(project.folder()),
                evidence = Files.createDirectories(home.resolve("evidence"));
        Map<String, byte[]> originals = new HashMap<>();
        try (ZipFile archive = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                try (var in = archive.getInputStream(entry)) {
                    originals.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        LatexCompilerService compiler = new LatexCompilerService();
        compiler.configure(new LatexCompilerService.Options(LatexToolchain.resolve(""), 120));
        try {
            for (var engine : LatexCompilerService.Engine.values()) {
                CompletableFuture<LatexCompilerService.CompileResult> done =
                        new CompletableFuture<>();
                compiler.compileAsync(
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
                                    Files.writeString(evidence.resolve(engine + ".log"), log);
                                } catch (Exception failure) {
                                    done.completeExceptionally(failure);
                                    return;
                                }
                                done.completeExceptionally(
                                        new IllegalStateException(
                                                engine + " failed " + code + "; see evidence log"));
                            }
                        });
                var result = done.get(140, TimeUnit.SECONDS);
                Files.writeString(evidence.resolve(engine + ".log"), result.log());
                Path pdf = Files.copy(result.pdfPath(), evidence.resolve(engine + ".pdf"));
                try (var document = Loader.loadPDF(pdf.toFile())) {
                    String text =
                            Normalizer.normalize(
                                    new PDFTextStripper().getText(document), Normalizer.Form.NFC);
                    for (String phrase :
                            List.of(
                                    "Dự án kiểm thử",
                                    "Giới thiệu",
                                    "Dữ liệu thô",
                                    "Tài liệu tham khảo"))
                        if (!text.contains(phrase))
                            throw new AssertionError(
                                    engine + " missing Vietnamese phrase: " + phrase);
                    if (text.contains("[?]"))
                        throw new AssertionError(engine + " contains unresolved citations");
                    if (document.getNumberOfPages() < 10)
                        throw new AssertionError("Missing chapters");
                    Files.writeString(evidence.resolve(engine + ".txt"), text);
                    System.out.println(
                            "ZIP_PDF_OK " + engine + " pages=" + document.getNumberOfPages());
                }
                if (!Files.readString(root.resolve("build/main.bbl")).contains("knuth1984"))
                    throw new AssertionError("Biber bibliography missing");
                for (var entry : originals.entrySet())
                    if (!Arrays.equals(
                            entry.getValue(), Files.readAllBytes(root.resolve(entry.getKey()))))
                        throw new AssertionError("Original input changed: " + entry.getKey());
            }
            System.out.println(
                    "IMPORTED_ZIP_COMPILE_SMOKE_OK source bytes unchanged, Vietnamese, chapters, Biber, all engines");
        } finally {
            compiler.shutdown();
        }
    }
}

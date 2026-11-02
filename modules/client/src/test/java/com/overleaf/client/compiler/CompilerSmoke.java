package com.overleaf.client.compiler;

import com.overleaf.client.ui.backend.LocalBackend;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Explicit real-toolchain check; not run by Surefire. Writes only to the chosen output directory.
 */
public final class CompilerSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Usage: CompilerSmoke <absolute-latexmk-path> <new-output-dir>");
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        if (Files.exists(root))
            throw new IllegalArgumentException(
                    "Use a new output directory to measure a cold build.");
        Files.createDirectories(root);
        LatexCompilerService.Options options =
                new LatexCompilerService.Options(Path.of(args[0]).toAbsolutePath().toString(), 180);
        StringBuilder report =
                new StringBuilder(
                        "# Local compile smoke\n\nSingle run per engine; small document, not an Overleaf comparison.\n\n");
        for (var tool : ToolchainDiagnostics.inspect(options)) {
            report.append(tool.name())
                    .append(": ")
                    .append(tool.available())
                    .append(" — ")
                    .append(tool.detail())
                    .append("\n\n");
            if (!tool.available()) throw new IllegalStateException("Missing tool: " + tool);
        }
        report.append(
                "| Engine | First (ms) | Unchanged (ms) | Small edit (ms) |\n|---|---:|---:|---:|\n");
        LatexCompilerService service = new LatexCompilerService();
        service.configure(options);
        try {
            for (var engine : LatexCompilerService.Engine.values()) {
                Path project = Files.createDirectory(root.resolve(engine.name()));
                String source =
                        "\\documentclass{article}\n\\usepackage{amsmath}\n\\begin{document}\n"
                                + "\\section{Local compilation}\\label{sec:one}\nHello local compiler. See Section~\\ref{sec:one}.\n"
                                + "\\[ E=mc^2 \\]\n\\end{document}\n";
                Files.writeString(project.resolve("main.tex"), source);
                var first = compile(service, project, engine, "first");
                var warm = compile(service, project, engine, "unchanged");
                if (!warm.log().contains("Nothing to do") && !warm.log().contains("up-to-date")) {
                    throw new AssertionError("No evidence of cache reuse: " + engine);
                }
                Files.writeString(
                        project.resolve("main.tex"),
                        source.replace("Hello local compiler", "Hello updated local compiler"));
                var edit = compile(service, project, engine, "edit");
                report.append("| ")
                        .append(engine)
                        .append(" | ")
                        .append(first.durationMillis())
                        .append(" | ")
                        .append(warm.durationMillis())
                        .append(" | ")
                        .append(edit.durationMillis())
                        .append(" |\n");
                String tikz =
                        source.replace(
                                        "\\usepackage{amsmath}",
                                        "\\usepackage{amsmath}\n\\usepackage{tikz}")
                                .replace(
                                        "Hello local compiler.",
                                        "\\begin{tikzpicture}\\foreach \\x in {1,...,100} {"
                                                + "\\draw (0,0) -- (\\x:2); }\\end{tikzpicture}");
                Files.writeString(project.resolve("main.tex"), tikz);
                compile(service, project, engine, "tikz");
                // Use the actual application template too, including Vietnamese text.
                Files.writeString(
                        project.resolve("main.tex"),
                        LocalBackend.template("Luận văn")
                                .replace(
                                        "Start writing here.",
                                        "Xin chào thế giới! Tiếng Việt: ă â đ ê ô ơ ư."));
                compile(service, project, engine, "vietnamese-template");
                System.out.println("REAL_PDF_OK " + engine);
            }
            Files.writeString(root.resolve("results.md"), report, StandardCharsets.UTF_8);
            System.out.println(report);
        } finally {
            service.shutdown();
        }
    }

    private static LatexCompilerService.CompileResult compile(
            LatexCompilerService service,
            Path project,
            LatexCompilerService.Engine engine,
            String stage)
            throws Exception {
        CompletableFuture<LatexCompilerService.CompileResult> done = new CompletableFuture<>();
        service.compileAsync(
                project.toString(),
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
                            Files.writeString(project.resolve(stage + ".log"), log);
                        } catch (Exception writeFailure) {
                            done.completeExceptionally(writeFailure);
                            return;
                        }
                        done.completeExceptionally(
                                new IllegalStateException(
                                        engine
                                                + "/"
                                                + stage
                                                + " exit="
                                                + code
                                                + "; see "
                                                + project.resolve(stage + ".log")));
                    }
                });
        var result = done.get(200, TimeUnit.SECONDS);
        Files.writeString(project.resolve(stage + ".log"), result.log());
        try (var in = Files.newInputStream(result.pdfPath())) {
            if (!new String(in.readNBytes(5), StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new AssertionError("Invalid PDF header: " + result.pdfPath());
            }
        }
        return result;
    }
}

package com.overleaf.client.ui.backend;

import com.overleaf.client.compiler.LatexCompilerService;
import com.overleaf.client.compiler.LatexToolchain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Explicit real Pandoc/Git/TeX smoke; all generated projects live in a new test home. */
public final class ProjectImportSmoke {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(root)) throw new IllegalArgumentException("Use a new home");
        Files.createDirectories(root);
        LocalBackend backend = new LocalBackend(root.resolve("home"));
        LatexCompilerService compiler = new LatexCompilerService();
        compiler.configure(new LatexCompilerService.Options(LatexToolchain.resolve(""), 120));
        try {
            for (var template : ProjectTemplates.all()) {
                var created =
                        backend.create(
                                new NewProjectRequest(
                                        "Mẫu " + template.title(true),
                                        null,
                                        null,
                                        null,
                                        NewProjectRequest.Kind.TEMPLATE,
                                        null,
                                        null,
                                        template.id(),
                                        true));
                compile(compiler, created);
                System.out.println("TEMPLATE_PDF_OK " + template.id());
            }
            var example =
                    backend.create(
                            new NewProjectRequest(
                                    "Dự án mẫu",
                                    null,
                                    null,
                                    null,
                                    NewProjectRequest.Kind.EXAMPLE,
                                    null,
                                    null,
                                    null,
                                    true));
            compile(compiler, example);
            System.out.println("EXAMPLE_PDF_OK");
            Path input = Files.createDirectory(root.resolve("input"));
            Path markdown = input.resolve("source.md");
            Files.writeString(
                    markdown,
                    "# Giới thiệu\n\nXin chào thế giới. Tiếng Việt có dấu.\n\n- Một mục\n- Hai mục\n\nCông thức $E=mc^2$.\n");
            Path word = input.resolve("source.docx");
            Process p =
                    new ProcessBuilder(
                                    ProjectImports.pandoc(),
                                    markdown.toString(),
                                    "-o",
                                    word.toString())
                            .redirectErrorStream(true)
                            .redirectOutput(input.resolve("pandoc.log").toFile())
                            .start();
            if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0)
                throw new AssertionError("Could not create Word fixture");
            for (var kind :
                    java.util.List.of(
                            NewProjectRequest.Kind.MARKDOWN, NewProjectRequest.Kind.WORD)) {
                Path source = kind == NewProjectRequest.Kind.WORD ? word : markdown;
                var project =
                        backend.create(
                                new NewProjectRequest(
                                        "Nhập " + kind,
                                        null,
                                        null,
                                        root.resolve("custom"),
                                        kind,
                                        source,
                                        null,
                                        null,
                                        true));
                compile(compiler, project);
                try (var pdf =
                        Loader.loadPDF(Path.of(project.folder(), "build/main.pdf").toFile())) {
                    String text =
                            java.text.Normalizer.normalize(
                                    new PDFTextStripper().getText(pdf),
                                    java.text.Normalizer.Form.NFC);
                    if (!text.contains("Tiếng Việt"))
                        throw new AssertionError("Vietnamese missing from " + kind);
                }
                if (Files.mismatch(
                                source,
                                Path.of(
                                        project.folder(),
                                        "source",
                                        source.getFileName().toString()))
                        != -1) {
                    throw new AssertionError("Original source was not retained");
                }
                System.out.println("DOCUMENT_IMPORT_PDF_OK " + kind);
            }
            if (args.length > 1) {
                var repo =
                        backend.create(
                                new NewProjectRequest(
                                        "GitHub import",
                                        null,
                                        null,
                                        null,
                                        NewProjectRequest.Kind.GITHUB,
                                        null,
                                        args[1],
                                        null,
                                        false));
                if (!Files.isRegularFile(Path.of(repo.folder(), "main.tex"))
                        || !Files.isDirectory(Path.of(repo.folder(), ".git"))) {
                    throw new AssertionError("Git clone not preserved");
                }
                System.out.println("GITHUB_IMPORT_OK " + args[1]);
            }
            System.out.println("PROJECT_IMPORT_SMOKE_OK");
        } finally {
            compiler.shutdown();
        }
    }

    private static void compile(LatexCompilerService service, ProjectGateway.ProjectSummary project)
            throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        service.compileAsync(
                project.folder(),
                "main.tex",
                new LatexCompilerService.CompileCallback() {
                    @Override
                    public void onSuccess(LatexCompilerService.CompileResult result) {
                        done.complete(null);
                    }

                    @Override
                    public void onError(int code, String log) {
                        try {
                            Files.writeString(Path.of(project.folder(), "smoke-compile.log"), log);
                        } catch (Exception e) {
                            done.completeExceptionally(e);
                            return;
                        }
                        done.completeExceptionally(
                                new IllegalStateException(
                                        project.name()
                                                + " exit="
                                                + code
                                                + "; see smoke-compile.log"));
                    }
                });
        done.get(140, TimeUnit.SECONDS);
    }
}

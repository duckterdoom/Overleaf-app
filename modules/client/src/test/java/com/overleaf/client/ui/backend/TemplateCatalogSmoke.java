package com.overleaf.client.ui.backend;

import com.overleaf.client.compiler.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Real TeX matrix; every generated project is isolated under a Vietnamese path. */
public final class TemplateCatalogSmoke {
    public static void main(String[] args) throws Exception {
        verify(Path.of(args[0]), args.length < 2 || Boolean.parseBoolean(args[1]));
    }

    public static void verify(Path root, boolean allEngines) throws Exception {
        root = root.toAbsolutePath();
        if (Files.exists(root)) throw new IllegalArgumentException("Use a fresh output directory");
        Files.createDirectories(root);
        LocalBackend backend = new LocalBackend(root.resolve("Thư viện mẫu"));
        Path evidence = Files.createDirectories(root.resolve("evidence"));
        LatexCompilerService service = new LatexCompilerService();
        service.configure(new LatexCompilerService.Options(LatexToolchain.resolve(""), 180));
        int count = 0;
        List<String> outcomes = new ArrayList<>();
        try {
            for (var template : ProjectTemplates.all())
                for (boolean vi : List.of(false, true)) {
                    var project =
                            backend.create(
                                    new NewProjectRequest(
                                            template.id() + "-" + (vi ? "Tiếng Việt" : "English"),
                                            null,
                                            null,
                                            null,
                                            NewProjectRequest.Kind.TEMPLATE,
                                            null,
                                            null,
                                            template.id(),
                                            vi));
                    LatexCompilerService.Engine preferred =
                            LatexCompilerService.Engine.valueOf(
                                    template.engine().toUpperCase(Locale.ROOT));
                    List<LatexCompilerService.Engine> engines =
                            allEngines
                                    ? new ArrayList<>(List.of(LatexCompilerService.Engine.values()))
                                    : List.of(preferred);
                    if (allEngines) {
                        engines.remove(preferred);
                        engines.addFirst(preferred);
                    }
                    for (var engine : engines) {
                        String id = template.id() + "-" + (vi ? "vi" : "en") + "-" + engine;
                        CompletableFuture<LatexCompilerService.CompileResult> done =
                                new CompletableFuture<>();
                        service.compileAsync(
                                project.folder(),
                                template.mainFile(),
                                engine,
                                new LatexCompilerService.CompileCallback() {
                                    @Override
                                    public void onSuccess(
                                            LatexCompilerService.CompileResult result) {
                                        done.complete(result);
                                    }

                                    @Override
                                    public void onError(int code, String log) {
                                        try {
                                            Files.writeString(evidence.resolve(id + ".log"), log);
                                        } catch (Exception failure) {
                                            done.completeExceptionally(failure);
                                            return;
                                        }
                                        done.completeExceptionally(
                                                new IllegalStateException(
                                                        id
                                                                + " failed "
                                                                + code
                                                                + "; see evidence log"));
                                    }
                                });
                        var result = done.get(200, TimeUnit.SECONDS);
                        Path projectRoot = Path.of(project.folder());
                        String finalLog = Files.readString(projectRoot.resolve("build/main.log"));
                        if (java.util.regex.Pattern.compile(
                                        "(?im)^!|undefined references|(?:Citation|Reference) .+ undefined|Please \\(re\\)run Biber")
                                .matcher(finalLog)
                                .find())
                            throw new AssertionError(
                                    id + " unresolved diagnostics in " + projectRoot);
                        Files.writeString(evidence.resolve(id + ".log"), result.log());
                        try (var pdf = Loader.loadPDF(result.pdfPath().toFile())) {
                            if (pdf.getNumberOfPages() < 1) throw new AssertionError("Empty PDF");
                            String text =
                                    java.text.Normalizer.normalize(
                                            new PDFTextStripper().getText(pdf),
                                            java.text.Normalizer.Form.NFC);
                            if (vi && !text.contains("Tác giả") && !text.contains("Người làm đơn"))
                                throw new AssertionError(id + " Vietnamese text not extracted");
                        }
                        outcomes.add(id + ",PASS");
                        Files.write(evidence.resolve("results.csv"), outcomes);
                        System.out.println("TEMPLATE_MATRIX_OK " + id);
                        count++;
                    }
                }
            System.out.println("TEMPLATE_CATALOG_SMOKE_OK builds=" + count);
        } finally {
            service.shutdown();
        }
    }
}

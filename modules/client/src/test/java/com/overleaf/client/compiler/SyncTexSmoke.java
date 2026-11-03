package com.overleaf.client.compiler;

import java.nio.file.*;
import java.util.concurrent.*;

/** Explicit native checks: all engines, Unicode project roots, included sources and spaces. */
public final class SyncTexSmoke {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(home)) throw new IllegalArgumentException("Use a new home");
        Files.createDirectories(home);
        for (String folder : new String[] {"ascii", "Luận văn tiếng Việt"}) {
            for (var engine : LatexCompilerService.Engine.values()) {
                Path root = Files.createDirectories(home.resolve(folder).resolve(engine.name()));
                Files.createDirectories(root.resolve("chapters"));
                Files.writeString(
                        root.resolve("main.tex"),
                        "\\documentclass{article}\n\\usepackage{hyperref}\n\\begin{document}\nFirst page.\n\\newpage\nSecond page.\n\\newpage\n\\input{chapters/section one.tex}\n\\end{document}\n");
                Files.writeString(
                        root.resolve("chapters/section one.tex"),
                        "\\section{Target chapter}\n\\label{sec:target}\n\nTarget paragraph for synchronization. This sentence maps back to the included source.\n\nAnother paragraph at the end of the chapter.\n");
                var result = compile(root, args[1], engine);
                SyncTexService index = SyncTexService.load(result.pdfPath(), args[1]);
                var point = index.forward("chapters/section one.tex", 4, 0);
                if (point.page() != 2) throw new AssertionError("Wrong forward page: " + point);
                var source = index.inverse(point.page(), point.x(), point.y());
                if (!source.file().equals("chapters/section one.tex")
                        || Math.abs(source.line() - 4) > 1)
                    throw new AssertionError("Wrong inverse result: " + source);
                try {
                    index.forward("../outside.tex", 1, 0);
                    throw new AssertionError("Escaped workspace");
                } catch (java.io.IOException expected) {
                }
                System.out.println("SYNCTEX_NATIVE_OK " + folder + " " + engine + " " + source);
            }
        }
        System.out.println("SYNCTEX_SMOKE_OK builds=6");
    }

    public static LatexCompilerService.CompileResult compile(
            Path root, String tool, LatexCompilerService.Engine engine) throws Exception {
        LatexCompilerService compiler = new LatexCompilerService();
        compiler.configure(new LatexCompilerService.Options(tool, 240));
        CompletableFuture<LatexCompilerService.CompileResult> done = new CompletableFuture<>();
        try {
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
                        public void onError(int code, String text) {
                            try {
                                Files.writeString(root.resolve("failure.log"), text);
                            } catch (Exception ignored) {
                            }
                            done.completeExceptionally(
                                    new IllegalStateException(
                                            "Compile exit=" + code + "; " + root));
                        }
                    });
            return done.get(260, TimeUnit.SECONDS);
        } finally {
            compiler.shutdown();
        }
    }
}

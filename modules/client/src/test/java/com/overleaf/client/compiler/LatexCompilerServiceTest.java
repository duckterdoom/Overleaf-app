package com.overleaf.client.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LatexCompilerServiceTest {
    @TempDir Path workspace;

    record Outcome(int code, String log, Path pdf) {}

    static class Callback implements LatexCompilerService.CompileCallback {
        final CompletableFuture<Outcome> result = new CompletableFuture<>();
        final CompletableFuture<Void> ready = new CompletableFuture<>();
        final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        public void onLogLine(String line) {
            lines.add(line);
            if (line.equals("READY")) ready.complete(null);
        }

        @Override
        public void onSuccess(LatexCompilerService.CompileResult r) {
            result.complete(new Outcome(0, r.log(), r.pdfPath()));
        }

        @Override
        public void onError(int code, String log) {
            result.complete(new Outcome(code, log, null));
        }

        @Override
        public void onSuperseded() {
            result.complete(new Outcome(999, "superseded", null));
        }

        Outcome await() throws Exception {
            return result.get(15, TimeUnit.SECONDS);
        }
    }

    private LatexCompilerService fixture(List<List<String>> commands) {
        return new LatexCompilerService(builder -> {
            commands.add(List.copyOf(builder.command()));
            List<String> command =
                    new ArrayList<>(List.of(java(), "-cp", testClasses(), Fixture.class.getName()));
            command.add(builder.command().get(builder.command().size() - 1));
            return new ProcessBuilder(command)
                    .directory(builder.directory())
                    .redirectErrorStream(true)
                    .redirectOutput(builder.redirectOutput())
                    .start();
        });
    }

    @Test
    void enginesUseSelectedExecutableAndPreserveIncrementalFiles() throws Exception {
        List<List<String>> commands = new CopyOnWriteArrayList<>();
        LatexCompilerService service = fixture(commands);
        try {
            Path build = Files.createDirectories(workspace.resolve("build"));
            Files.writeString(build.resolve("cached.aux"), "keep");
            service.configure(new LatexCompilerService.Options("/custom/tex/latexmk", 30));
            for (var engine : LatexCompilerService.Engine.values()) {
                Callback cb = new Callback();
                service.compileAsync(workspace.toString(), "paper.tex", engine, cb);
                var result = cb.await();
                assertNotNull(result.pdf());
                assertTrue(
                        result.log().contains("fixture finished"),
                        "both pipe/file log capture must work");
                assertEquals(engine.flag(), commands.get(commands.size() - 1).get(1));
            }
            assertEquals("/custom/tex/latexmk", commands.get(0).get(0));
            assertEquals("keep", Files.readString(build.resolve("cached.aux")));
            assertTrue(commands.get(0).contains("-outdir=build"));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void startupFailureIsReportedWithConfigurationHint() throws Exception {
        LatexCompilerService service =
                new LatexCompilerService(b -> {
                    throw new IOException("not installed");
                });
        try {
            Callback cb = new Callback();
            service.compileAsync(workspace.toString(), "paper.tex", cb);
            var result = cb.await();
            assertEquals(LatexCompilerService.EXIT_START_FAILED, result.code());
            assertTrue(result.log().contains("Settings > Compilation"));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void exitZeroWithoutPdfIsStillAFailure() throws Exception {
        LatexCompilerService service = fixture(new CopyOnWriteArrayList<>());
        try {
            Callback cb = new Callback();
            service.compileAsync(workspace.toString(), "no-pdf.tex", cb);
            var result = cb.await();
            assertNull(result.pdf());
            assertTrue(result.log().contains("PDF"));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void activeBuildKeepsItsTimeoutSnapshotAndKillsDescendants() throws Exception {
        LatexCompilerService service = fixture(new CopyOnWriteArrayList<>());
        try {
            service.setTimeoutSeconds(2);
            Callback cb = new Callback();
            service.compileAsync(workspace.toString(), "hang.tex", cb);
            cb.ready.get(10, TimeUnit.SECONDS);
            service.setTimeoutSeconds(0);
            assertEquals(LatexCompilerService.EXIT_TIMEOUT, cb.await().code());
            long pid =
                    Long.parseLong(
                            cb.lines.stream()
                                    .filter(s -> s.startsWith("CHILD="))
                                    .findFirst()
                                    .orElseThrow()
                                    .substring(6));
            assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void unlimitedBuildCanBeCancelledAndQueuedBuildIsDiscarded() throws Exception {
        List<List<String>> commands = new CopyOnWriteArrayList<>();
        LatexCompilerService service = fixture(commands);
        try {
            service.setTimeoutSeconds(0);
            Callback active = new Callback();
            service.compileAsync(workspace.toString(), "hang.tex", active);
            active.ready.get(10, TimeUnit.SECONDS);
            Callback queued = new Callback();
            service.compileAsync(workspace.toString(), "queued.tex", queued);
            service.cancelCompile();
            assertEquals(LatexCompilerService.EXIT_INTERRUPTED, active.await().code());
            assertEquals(999, queued.await().code());
            assertEquals(1, commands.size());
            Callback next = new Callback();
            service.compileAsync(workspace.toString(), "paper.tex", next);
            assertNotNull(next.await().pdf(), "a cancelled service must be reusable");
        } finally {
            service.shutdown();
        }
    }

    @Test
    void latestQueuedRequestWinsAndKeepsItsExecutableSnapshot() throws Exception {
        List<List<String>> commands = new CopyOnWriteArrayList<>();
        LatexCompilerService service = fixture(commands);
        try {
            Callback active = new Callback();
            service.compileAsync(workspace.toString(), "brief.tex", active);
            active.ready.get(10, TimeUnit.SECONDS);
            Callback superseded = new Callback();
            service.compileAsync(workspace.toString(), "old.tex", superseded);
            service.configure(new LatexCompilerService.Options("chosen-latexmk", 10));
            Callback last = new Callback();
            service.compileAsync(workspace.toString(), "last.tex", last);
            service.configure(LatexCompilerService.Options.defaults());
            assertEquals(999, superseded.await().code());
            assertNotNull(active.await().pdf());
            assertNotNull(last.await().pdf());
            assertEquals(2, commands.size());
            assertEquals("chosen-latexmk", commands.get(1).get(0));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void invalidSettingsAreRejectedAndToolDirectoryIsPrepended() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LatexCompilerService.Options("latexmk", -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LatexCompilerService.Options("\"latexmk\" -pdf", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LatexCompilerService.Options("bad\0path", 1));
        ProcessBuilder builder = new ProcessBuilder("unused");
        Path executable = workspace.resolve("tex bin").resolve("latexmk");
        LatexCompilerService.useToolDirectory(
                builder, new LatexCompilerService.Options(executable.toString(), 0));
        String path =
                builder.environment().entrySet().stream()
                        .filter(e -> e.getKey().equalsIgnoreCase("PATH"))
                        .findFirst()
                        .orElseThrow()
                        .getValue();
        assertTrue(path.startsWith(executable.getParent().toString() + java.io.File.pathSeparator));
    }

    @Test
    void diagnosticReportsMissingToolsWithoutThrowing() {
        var tools =
                ToolchainDiagnostics.inspect(
                        new LatexCompilerService.Options(
                                workspace.resolve("missing-bin").resolve("latexmk").toString(), 0));
        assertEquals(4, tools.size());
        assertTrue(tools.stream().noneMatch(ToolchainDiagnostics.Tool::available));
    }

    @Test
    void exitZeroWithMissingGlyphsMustNotPublishSuccessfulPdf() throws Exception {
        LatexCompilerService service = fixture(new CopyOnWriteArrayList<>());
        try {
            Callback cb = new Callback();
            service.compileAsync(workspace.toString(), "missing-glyph.tex", cb);
            var result = cb.await();
            assertEquals(LatexCompilerService.EXIT_MISSING_GLYPHS, result.code());
            assertNull(result.pdf());
            assertTrue(result.log().contains("FONT ERROR"));
        } finally {
            service.shutdown();
        }
    }

    @Test
    void cachedTexLogStillPreventsExportOfMissingCharacters() throws Exception {
        Path log = workspace.resolve("main.log");
        Files.writeString(log, "Missing character: There is no ệ in font t5-lmr10!\n");
        assertNotNull(LatexCompilerService.missingGlyphMessage("All targets are up-to-date", log));
        Files.writeString(log, "Package babel Warning: No hyphenation patterns\n");
        assertNull(
                LatexCompilerService.missingGlyphMessage("", log),
                "unrelated warnings must not block a good PDF");
    }

    @Test
    void missingPackageKeepsRootCauseAndLocationInsteadOfLauncherSummary() {
        String log =
                "! LaTeX Error: File `csquotes.sty' not found.\n"
                        + "./preamble.tex:15: Emergency stop.\n./preamble.tex:15:  ==> Fatal error occurred, no output PDF file produced!\n"
                        + "  ! LaTeX Error: File `csquotes.sty' not found.\n"
                        + "D:\\TinyTeX\\runscript.tlu:933: command failed with exit code 12:\n";
        var issues = LatexCompilerService.parseErrors(log);
        assertEquals(1, issues.size());
        assertEquals("preamble.tex", issues.get(0).file());
        assertEquals(15, issues.get(0).line());
        assertTrue(issues.get(0).message().contains("csquotes.sty"));
    }

    @Test
    void launcherSummaryIsNotASourceErrorAndBangErrorsDoNotNeedALocation() {
        assertTrue(
                LatexCompilerService.parseErrors(
                                "D:\\TinyTeX\\runscript.tlu:933: command failed with exit code 12:")
                        .isEmpty());
        var issues =
                LatexCompilerService.parseErrors(
                        "! LaTeX Error: File `missing.sty' not found.\n! Emergency stop.");
        assertEquals(1, issues.size());
        assertEquals("", issues.get(0).file());
        assertTrue(issues.get(0).message().contains("missing.sty"));
    }

    @Test
    void auxiliaryWriteFailureIsKeptAndFatalDuplicatesAreSuppressed() {
        var issues =
                LatexCompilerService.parseErrors(
                        "./main.tex:42: I can't write on file `chapters/01-introduction.aux'.\n"
                                + "./main.tex:42: Emergency stop.\n./main.tex:42:  ==> Fatal error occurred, no output PDF file produced!");
        assertEquals(1, issues.size());
        assertEquals("main.tex", issues.get(0).file());
        assertEquals(42, issues.get(0).line());
        assertTrue(issues.get(0).message().contains(".aux"));
    }

    @Test
    void retryAfterAnAuxiliaryFailureForcesLatexmkWithoutClearingCache() throws Exception {
        Files.createDirectories(workspace.resolve("build"));
        Files.writeString(
                workspace.resolve("build/paper.log"), "! I can't write on file `chapters/a.aux'.");
        Files.writeString(workspace.resolve("build/preserved.aux"), "keep");
        List<List<String>> commands = new CopyOnWriteArrayList<>();
        LatexCompilerService service = fixture(commands);
        try {
            Callback cb = new Callback();
            service.compileAsync(workspace.toString(), "paper.tex", cb);
            assertNotNull(cb.await().pdf());
            assertTrue(commands.get(0).contains("-g"));
            assertEquals("keep", Files.readString(workspace.resolve("build/preserved.aux")));
        } finally {
            service.shutdown();
        }
    }

    static String java() {
        return Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        "java"
                                + (System.getProperty("os.name").startsWith("Windows")
                                        ? ".exe"
                                        : ""))
                .toString();
    }

    private static String testClasses() throws IOException {
        return System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
    }

    /** Process fixture, not a TeX compiler. Real PDF checks use a separate installed toolchain. */
    public static class Fixture {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("child")) {
                Thread.sleep(30000);
                return;
            }
            if (args[0].equals("hang.tex")) {
                Process child =
                        new ProcessBuilder(
                                        java(),
                                        "-cp",
                                        System.getProperty("java.class.path"),
                                        Fixture.class.getName(),
                                        "child")
                                .start();
                System.out.println("CHILD=" + child.pid());
                System.out.println("READY");
                System.out.flush();
                Thread.sleep(30000);
                return;
            }
            if (args[0].equals("brief.tex")) {
                System.out.println("READY");
                System.out.flush();
                Thread.sleep(1500);
            }
            if (args[0].equals("no-pdf.tex")) return;
            Path output = Files.createDirectories(Path.of("build"));
            Files.writeString(output.resolve(args[0].replace(".tex", ".pdf")), "%PDF-fixture");
            if (args[0].equals("missing-glyph.tex")) {
                System.out.println("Missing character: There is no ệ in font t5-lmr10!");
            }
            System.out.println("fixture finished");
        }
    }
}

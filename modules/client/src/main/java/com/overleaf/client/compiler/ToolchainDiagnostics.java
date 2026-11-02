package com.overleaf.client.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Checks the actual executables without installing packages or changing the system PATH. */
public final class ToolchainDiagnostics {
    public record Tool(String name, String executable, boolean available, String detail) {}

    private ToolchainDiagnostics() {}

    public static List<Tool> inspect(LatexCompilerService.Options options) {
        List<Tool> tools = new ArrayList<>();
        tools.add(probe("latexmk", options.executable(), "-v", options));
        Path parent = Path.of(options.executable()).getParent();
        for (String engine : List.of("pdflatex", "xelatex", "lualatex")) {
            String executable =
                    parent == null
                            ? engine
                            : parent.resolve(
                                            engine
                                                    + (System.getProperty("os.name")
                                                                    .toLowerCase(Locale.ROOT)
                                                                    .contains("win")
                                                            ? ".exe"
                                                            : ""))
                                    .toString();
            tools.add(probe(engine, executable, "--version", options));
        }
        return List.copyOf(tools);
    }

    private static Tool probe(
            String name, String executable, String flag, LatexCompilerService.Options options) {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("overleaf-tool-", ".log");
            ProcessBuilder builder =
                    new ProcessBuilder(executable, flag)
                            .redirectErrorStream(true)
                            .redirectOutput(output.toFile());
            LatexCompilerService.useToolDirectory(builder, options);
            process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                return new Tool(name, executable, false, "Version check timed out (8s).");
            }
            String text;
            try (var input = Files.newInputStream(output)) {
                text = new String(input.readNBytes(8192), StandardCharsets.UTF_8).strip();
            }
            String first =
                    text.lines()
                            .filter(s ->
                                    s.contains("Version")
                                            || s.contains("TeX Live")
                                            || s.startsWith("Latexmk,"))
                            .findFirst()
                            .orElseGet(() ->
                                    text.lines()
                                            .filter(s -> !s.isBlank())
                                            .findFirst()
                                            .orElse("No version output."));
            return new Tool(
                    name,
                    executable,
                    process.exitValue() == 0,
                    first
                            + (process.exitValue() == 0
                                    ? ""
                                    : " (exit " + process.exitValue() + ")"));
        } catch (IOException | RuntimeException failure) {
            return new Tool(name, executable, false, failure.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Tool(name, executable, false, "Version check interrupted.");
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try {
                    process.waitFor(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                }
            }
        }
    }
}

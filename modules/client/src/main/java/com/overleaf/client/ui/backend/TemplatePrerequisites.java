package com.overleaf.client.ui.backend;

import com.overleaf.client.compiler.LatexToolchain;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Checks the selected TeX installation; package installation requires explicit UI consent. */
public final class TemplatePrerequisites {
    public record Result(int code, String output) {}

    @FunctionalInterface
    public interface Runner {
        Result run(List<String> command, int timeout) throws IOException, InterruptedException;
    }

    public record Missing(String name, String probe, String installPackage) {}

    public record Report(List<Missing> missing, boolean bundled, Path bin) {
        public boolean ready() {
            return missing.isEmpty();
        }

        public String message(boolean vi) {
            String names = String.join(", ", missing.stream().map(Missing::name).toList());
            return (vi ? "Thiếu gói/công cụ TeX: " : "Missing TeX packages/tools: ")
                    + names
                    + ". "
                    + (bundled
                            ? vi
                                    ? "Có thể cài vào TinyTeX đi kèm sau khi bạn xác nhận."
                                    : "These can be installed into bundled TinyTeX after your confirmation."
                            : vi
                                    ? "Hãy cài bằng trình quản lý của bản TeX đang chọn hoặc chọn lại latexmk trong Cài đặt. App không tự sửa TeX hệ thống."
                                    : "Install them with your selected TeX distribution or select latexmk in Settings. System TeX is never modified automatically.");
        }
    }

    public static final class MissingDependencies extends IllegalStateException {
        private final Report report;

        public MissingDependencies(Report report) {
            super(report.message(false));
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    private final Path bin, workspace;
    private final boolean windows;
    private final Runner runner;

    public TemplatePrerequisites(String configured) {
        this(
                Path.of(LatexToolchain.resolve(configured))
                        .toAbsolutePath()
                        .normalize()
                        .getParent(),
                Path.of("").toAbsolutePath(),
                System.getProperty("os.name").startsWith("Windows"),
                null);
    }

    public TemplatePrerequisites(Path bin, Path workspace, boolean windows, Runner runner) {
        this.bin = bin;
        this.workspace = workspace;
        this.windows = windows;
        this.runner = runner == null ? this::run : runner;
    }

    private Path tool(String name) {
        return bin.resolve(name + (windows ? ".exe" : ""));
    }

    public Report inspect(ProjectTemplates.Template template)
            throws IOException, InterruptedException {
        List<Missing> missing = new ArrayList<>();
        for (String pkg : template.requiredPackages()) {
            ProjectFiles.checkCancelled();
            String probe = probe(pkg);
            Result result;
            try {
                result = runner.run(List.of(tool("kpsewhich").toString(), probe), 15);
            } catch (IOException absent) {
                result = new Result(-1, "");
            }
            if (result.code() != 0 || result.output().isBlank())
                missing.add(new Missing(pkg, probe, installPackage(pkg)));
        }
        for (String name :
                template.needsBiber()
                        ? List.of(template.engine(), "latexmk", "biber")
                        : List.of(template.engine(), "latexmk")) {
            Result result;
            try {
                result =
                        runner.run(
                                List.of(
                                        tool(name).toString(),
                                        name.equals("latexmk") ? "-v" : "--version"),
                                15);
            } catch (IOException absent) {
                result = new Result(-1, "");
            }
            if (result.code() != 0)
                missing.add(
                        new Missing(
                                name,
                                name,
                                name.equals("pdflatex")
                                        ? "latex"
                                        : name.equals("xelatex")
                                                ? "xetex"
                                                : name.equals("lualatex") ? "luatex" : name));
        }
        return new Report(List.copyOf(missing), bundled(), bin);
    }

    private static String probe(String pkg) {
        return switch (pkg) {
            case "vntex" -> "t5enc.def";
            case "babel-vietnamese" -> "vietnamese.ldf";
            case "beamer" -> "beamer.cls";
            default -> pkg + ".sty";
        };
    }

    private static String installPackage(String pkg) {
        return switch (pkg) {
            case "lmodern" -> "lm";
            case "amssymb" -> "amsfonts";
            case "amsthm" -> "amscls";
            case "graphicx" -> "graphics";
            case "inputenc", "fontenc" -> "latex";
            case "tikz" -> "pgf";
            default -> pkg;
        };
    }

    public void install(Report report, Consumer<String> progress)
            throws IOException, InterruptedException {
        if (!report.bundled() || !report.bin().equals(bin) || !bundled())
            throw new IOException("Only bundled TinyTeX can be modified");
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (Missing dependency : report.missing()) {
            if (!dependency.installPackage().matches("[a-z0-9-]+"))
                throw new IOException("Invalid package name");
            packages.add(dependency.installPackage());
        }
        for (String pkg : packages) {
            ProjectFiles.checkCancelled();
            progress.accept(pkg);
            List<String> command = new ArrayList<>();
            if (windows) {
                Path root = bin.getParent().getParent();
                command.add(root.resolve("tlpkg/tlperl/bin/perl.exe").toString());
                command.add(root.resolve("texmf-dist/scripts/texlive/tlmgr.pl").toString());
            } else command.add(bin.resolve("tlmgr").toString());
            command.add("install");
            command.add(pkg);
            Result result = runner.run(command, 600);
            if (result.code() != 0)
                throw new IOException("tlmgr install " + pkg + " failed: " + result.output());
        }
    }

    private boolean bundled() {
        try {
            Path real = bin.toRealPath();
            for (Path ancestor = workspace.toAbsolutePath().normalize();
                    ancestor != null;
                    ancestor = ancestor.getParent())
                for (String relative : List.of("target/TinyTeX", "TinyTeX", "tools/TinyTeX")) {
                    Path root = ancestor.resolve(relative);
                    if (Files.isDirectory(root.resolve("texmf-dist"))
                            && real.startsWith(root.toRealPath().resolve("bin"))
                            && Files.isRegularFile(bin.resolve(windows ? "tlmgr.bat" : "tlmgr")))
                        return true;
                }
        } catch (IOException ignored) {
        }
        return false;
    }

    private Result run(List<String> command, int timeout) throws IOException, InterruptedException {
        Path log = Files.createTempFile("overleaf-tex-check-", ".log");
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true).redirectOutput(log.toFile());
            String pathKey =
                    builder.environment().keySet().stream()
                            .filter(k -> k.equalsIgnoreCase("PATH"))
                            .findFirst()
                            .orElse("PATH");
            builder.environment()
                    .put(
                            pathKey,
                            bin
                                    + File.pathSeparator
                                    + builder.environment().getOrDefault(pathKey, ""));
            builder.environment().put("LC_ALL", "C");
            if (windows)
                builder.environment()
                        .put(
                                "PERL5LIB",
                                bin.getParent().getParent().resolve("tlpkg/tlperl/lib").toString());
            process = builder.start();
            process.getOutputStream().close();
            if (!process.waitFor(timeout, TimeUnit.SECONDS))
                throw new IOException("TeX tool timed out");
            try (var input = Files.newInputStream(log)) {
                return new Result(
                        process.exitValue(),
                        new String(input.readNBytes(16 * 1024), StandardCharsets.UTF_8));
            }
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            Files.deleteIfExists(log);
        }
    }
}

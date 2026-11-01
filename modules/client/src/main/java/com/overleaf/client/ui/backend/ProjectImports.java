package com.overleaf.client.ui.backend;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

/** Import into an unpublished staging folder; no archive/repository content is executed. */
public final class ProjectImports {
    static final long MAX_BYTES = 500L * 1024 * 1024;
    static final int MAX_FILES = 10_000;

    public void populate(Path directory, NewProjectRequest request, String affiliation)
            throws IOException {
        ProjectFiles.checkCancelled();
        switch (request.kind()) {
            case BLANK, TEMPLATE, EXAMPLE -> {
                String template =
                        request.kind() == NewProjectRequest.Kind.EXAMPLE
                                ? "example"
                                : request.kind() == NewProjectRequest.Kind.BLANK
                                        ? "blank"
                                        : request.template();
                for (var file :
                        ProjectTemplates.files(
                                        template, request.name(), request.vietnamese(), affiliation)
                                .entrySet()) {
                    Path path = directory.resolve(file.getKey());
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
                }
            }
            case ZIP -> {
                extract(request.source(), directory);
                flattenWrapper(directory);
                ensureMain(directory);
            }
            case GITHUB -> {
                cloneRepository(request.repository(), directory);
                ensureMain(directory);
            }
            case WORD, MARKDOWN -> convert(request, directory);
        }
        checkSize(directory);
        ProjectFiles.checkCancelled();
    }

    static void extract(Path source, Path directory) throws IOException {
        if (source == null || !Files.isRegularFile(source) || Files.size(source) > MAX_BYTES) {
            throw new ProjectFailure(ProjectFailure.Code.ZIP);
        }
        Set<String> seen = new HashSet<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream archive =
                new ZipInputStream(Files.newInputStream(source), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                ProjectFiles.checkCancelled();
                if (++count > MAX_FILES) throw new ProjectFailure(ProjectFailure.Code.LIMIT);
                String name = entry.getName().replace('\\', '/');
                Path output = safeEntry(directory, name);
                String key = output.toString().toLowerCase(Locale.ROOT);
                if (!seen.add(key)) throw new ProjectFailure(ProjectFailure.Code.ZIP);
                if (entry.isDirectory()) Files.createDirectories(output);
                else {
                    Files.createDirectories(output.getParent());
                    try (OutputStream out =
                            Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                        byte[] bytes = new byte[65536];
                        int n;
                        while ((n = archive.read(bytes)) != -1) {
                            ProjectFiles.checkCancelled();
                            total += n;
                            if (total > MAX_BYTES)
                                throw new ProjectFailure(ProjectFailure.Code.LIMIT);
                            out.write(bytes, 0, n);
                        }
                    }
                }
                archive.closeEntry();
            }
        } catch (java.util.zip.ZipException invalid) {
            throw new ProjectFailure(ProjectFailure.Code.ZIP, invalid);
        }
        if (count == 0) throw new ProjectFailure(ProjectFailure.Code.ZIP);
    }

    static Path safeEntry(Path directory, String name) {
        if (name.isBlank() || name.startsWith("/") || name.contains(":"))
            throw new ProjectFailure(ProjectFailure.Code.ZIP);
        for (String segment : name.split("/")) {
            if (segment.equals("..")
                    || segment.equals(".")
                    || segment.endsWith(".")
                    || segment.endsWith(" ")
                    || segment.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?")) {
                throw new ProjectFailure(ProjectFailure.Code.ZIP);
            }
        }
        Path root = directory.toAbsolutePath().normalize();
        Path output = root.resolve(name).normalize();
        if (!output.startsWith(root) || output.equals(root))
            throw new ProjectFailure(ProjectFailure.Code.ZIP);
        return output;
    }

    private static void flattenWrapper(Path directory) throws IOException {
        List<Path> children;
        try (var files = Files.list(directory)) {
            children = files.filter(p -> !p.getFileName().toString().equals("__MACOSX")).toList();
        }
        if (children.size() != 1 || !Files.isDirectory(children.get(0))) return;
        Path wrapper = children.get(0);
        try (var files = Files.list(wrapper)) {
            for (Path file : files.toList())
                Files.move(file, directory.resolve(file.getFileName()));
        }
        Files.delete(wrapper); // Empty archive-owned wrapper only.
    }

    private static void ensureMain(Path directory) throws IOException {
        Path main = directory.resolve("main.tex");
        if (Files.isRegularFile(main)) return;
        List<Path> candidates;
        try (var paths = Files.list(directory)) {
            candidates =
                    paths.filter(p ->
                                    p.getFileName()
                                            .toString()
                                            .toLowerCase(Locale.ROOT)
                                            .endsWith(".tex"))
                            .filter(Files::isRegularFile)
                            .sorted()
                            .toList();
        }
        for (Path candidate : candidates) {
            if (Files.size(candidate) <= 8 * 1024 * 1024
                    && Files.readString(candidate).contains("\\documentclass")) {
                Files.copy(candidate, main);
                return;
            }
        }
        throw new ProjectFailure(ProjectFailure.Code.NO_TEX);
    }

    public static String githubUrl(String value) {
        try {
            URI uri = URI.create(value.strip());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getRawUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || uri.getPort() != -1
                    || !uri.getPath().matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?")) {
                throw new IllegalArgumentException();
            }
            String path = uri.getPath().replaceFirst("/$", "");
            if (path.contains("/../") || path.endsWith("/..")) throw new IllegalArgumentException();
            return "https://github.com" + path;
        } catch (RuntimeException invalid) {
            throw new ProjectFailure(ProjectFailure.Code.REPOSITORY);
        }
    }

    private static void cloneRepository(String url, Path directory) throws IOException {
        String repository = githubUrl(url);
        ProcessBuilder git =
                new ProcessBuilder(
                        "git",
                        "-c",
                        "credential.helper=",
                        "-c",
                        "core.hooksPath=" + directory.resolve(".disabled-hooks"),
                        "-c",
                        "protocol.file.allow=never",
                        "clone",
                        "--depth=1",
                        "--no-recurse-submodules",
                        "--",
                        repository,
                        directory.toString());
        git.environment().put("GIT_TERMINAL_PROMPT", "0");
        git.environment().put("GCM_INTERACTIVE", "Never");
        run(git, ProjectFailure.Code.GIT);
    }

    private static void convert(NewProjectRequest request, Path directory) throws IOException {
        Path source = request.source();
        String extension = request.kind() == NewProjectRequest.Kind.WORD ? ".docx" : ".md";
        if (source == null
                || !Files.isRegularFile(source)
                || Files.size(source) > 50L * 1024 * 1024
                || (extension.equals(".docx")
                        && !source.toString().toLowerCase(Locale.ROOT).endsWith(".docx"))) {
            throw new ProjectFailure(ProjectFailure.Code.CONVERSION);
        }
        Path originals = Files.createDirectory(directory.resolve("source"));
        Path copied = Files.copy(source.toAbsolutePath(), originals.resolve(source.getFileName()));
        List<String> command =
                new ArrayList<>(
                        List.of(
                                pandoc(),
                                "--from="
                                        + (extension.equals(".docx")
                                                ? "docx"
                                                : "markdown-raw_tex-raw_html"),
                                "--to=latex",
                                "--standalone",
                                "--extract-media=media",
                                "--resource-path=" + source.toAbsolutePath().getParent(),
                                "--metadata=title:" + request.name(),
                                "-V",
                                "fontenc:T5",
                                "-V",
                                "mainfont:lmroman10-regular.otf",
                                "--metadata=lang:" + (request.vietnamese() ? "vi" : "en"),
                                "-V",
                                "mainfontoptions:BoldFont=lmroman10-bold.otf,ItalicFont=lmroman10-italic.otf,BoldItalicFont=lmroman10-bolditalic.otf",
                                "--output=main.tex",
                                "--",
                                copied.toString()));
        run(
                new ProcessBuilder(command).directory(directory.toFile()),
                ProjectFailure.Code.CONVERSION);
        ensureMain(directory);
    }

    public static String pandoc() {
        String configured = System.getenv("OVERLEAF_PANDOC");
        if (configured != null && !configured.isBlank()) return configured;
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        String executable = windows ? "pandoc.exe" : "pandoc";
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path portable = root.resolve("target/pandoc").resolve(executable);
            if (Files.isRegularFile(portable)) return portable.toString();
        }
        return executable;
    }

    private static void run(ProcessBuilder builder, ProjectFailure.Code failure)
            throws IOException {
        Path log = Files.createTempFile("overleaf-import-", ".log");
        Process process = null;
        try {
            ProjectFiles.checkCancelled();
            builder.redirectErrorStream(true).redirectOutput(log.toFile());
            try {
                process = builder.start();
            } catch (IOException missing) {
                throw new ProjectFailure(
                        failure == ProjectFailure.Code.CONVERSION
                                ? ProjectFailure.Code.PANDOC
                                : failure,
                        missing);
            }
            process.getOutputStream().close();
            if (!process.waitFor(120, TimeUnit.SECONDS) || process.exitValue() != 0) {
                String detail;
                try (var input = Files.newInputStream(log)) {
                    detail = new String(input.readNBytes(8192), StandardCharsets.UTF_8);
                }
                throw new ProjectFailure(failure, new IOException(detail));
            }
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            throw new ProjectFailure(ProjectFailure.Code.CANCELLED, cancelled);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                try {
                    process.waitFor(3, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                Files.deleteIfExists(log);
            } catch (IOException ignored) {
            }
        }
    }

    private static void checkSize(Path directory) throws IOException {
        long total = 0;
        int count = 0;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                ProjectFiles.checkCancelled();
                if (++count > MAX_FILES || (total += Files.size(path)) > MAX_BYTES)
                    throw new ProjectFailure(ProjectFailure.Code.LIMIT);
            }
        }
    }
}

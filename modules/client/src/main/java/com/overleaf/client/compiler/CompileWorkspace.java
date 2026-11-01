package com.overleaf.client.compiler;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/** Some Windows latexmk builds cannot create/read a non-ANSI working directory. */
final class CompileWorkspace implements AutoCloseable {
    private final Path original;
    private final Path working;

    private CompileWorkspace(Path original, Path working) {
        this.original = original;
        this.working = working;
    }

    Path directory() {
        return working;
    }

    /** TeX does not create parent directories for auxiliary files of nested \\include commands. */
    static void prepareOutputDirectories(Path project) throws IOException {
        Path root = project.toAbsolutePath().normalize();
        Path output = root.resolve("build");
        Files.walkFileTree(
                root,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        if (dir.equals(output)
                                || (!dir.equals(root)
                                        && dir.getFileName().toString().startsWith(".")))
                            return FileVisitResult.SKIP_SUBTREE;
                        if (Thread.currentThread().isInterrupted())
                            throw new java.io.InterruptedIOException("Compile cancelled");
                        Path destination = output.resolve(root.relativize(dir));
                        if (Files.isSymbolicLink(destination))
                            throw new IOException(
                                    "Auxiliary output cannot be a symbolic link: " + destination);
                        Files.createDirectories(destination);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    static CompileWorkspace prepare(Path project) throws IOException {
        Path root = project.toAbsolutePath().normalize();
        if (!System.getProperty("os.name").startsWith("Windows")
                || root.toString().chars().allMatch(c -> c < 128)) {
            return new CompileWorkspace(root, root);
        }
        Path staging = Files.createTempDirectory("overleaf-tex-");
        if (!staging.toString().chars().allMatch(c -> c < 128)) {
            Files.delete(staging);
            throw new IOException(
                    "The Windows TeX working directory must use an ASCII temporary path.");
        }
        CompileWorkspace workspace = new CompileWorkspace(root, staging);
        try {
            Files.walkFileTree(
                    root,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path dir, BasicFileAttributes attrs) throws IOException {
                            if (dir.equals(root.resolve("build"))
                                    || dir.equals(root.resolve(".git")))
                                return FileVisitResult.SKIP_SUBTREE;
                            Files.createDirectories(staging.resolve(root.relativize(dir)));
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                throws IOException {
                            if (Thread.currentThread().isInterrupted())
                                throw new java.io.InterruptedIOException("Compile cancelled");
                            Files.copy(
                                    file,
                                    staging.resolve(root.relativize(file)),
                                    StandardCopyOption.COPY_ATTRIBUTES);
                            return FileVisitResult.CONTINUE;
                        }
                    });
            Files.createDirectories(staging.resolve("build"));
            return workspace;
        } catch (IOException | RuntimeException failure) {
            workspace.close();
            throw failure;
        }
    }

    Path publishPdf(String name) throws IOException {
        Path pdf = working.resolve("build").resolve(name);
        Path target = original.resolve("build").resolve(name);
        if (!working.equals(original)) {
            Path temp = Files.createTempFile(target.getParent(), ".pdf-", ".tmp");
            try {
                Files.copy(pdf, temp, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(
                            temp,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }
        return target;
    }

    @Override
    public void close() {
        if (working.equals(original)) return;
        try {
            Path logs = working.resolve("build");
            if (Files.isDirectory(logs))
                try (var files = Files.list(logs)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".log")).toList()) {
                        Files.copy(
                                file,
                                original.resolve("build").resolve(file.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
        } catch (IOException ignored) {
        }
        if (!working.getFileName().toString().startsWith("overleaf-tex-"))
            throw new IllegalStateException("Not a compile staging directory");
        try {
            Files.walkFileTree(
                    working,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException error)
                                throws IOException {
                            if (error != null) throw error;
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}

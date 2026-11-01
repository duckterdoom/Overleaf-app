package com.overleaf.client.ui.backend;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

final class ProjectFiles {
    private ProjectFiles() { }
    static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new ProjectFailure(ProjectFailure.Code.CANCELLED);
    }
    static void copy(Path from, Path to) throws IOException {
        Path source = from.toAbsolutePath().normalize();
        Path target = to.toAbsolutePath().normalize();
        if (target.startsWith(source)) throw new ProjectFailure(ProjectFailure.Code.DIRECTORY);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                checkCancelled();
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                checkCancelled();
                if (attrs.isSymbolicLink() || !attrs.isRegularFile()) throw new ProjectFailure(ProjectFailure.Code.ZIP);
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                if (Files.mismatch(file, target.resolve(source.relativize(file))) != -1) {
                    throw new IOException("A source file changed during the move: " + source.relativize(file));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
    /** Only removes application-owned temporary staging folders, never a user project. */
    static void removeStaging(Path staging) {
        Path root = staging.toAbsolutePath().normalize();
        if (!root.getFileName().toString().startsWith(".overleaf-staging-")) throw new IllegalArgumentException("Not a staging folder");
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                    if (error != null) throw error;
                    Files.delete(dir); return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) { /* A failed staging folder is hidden and recoverable. */ }
    }
}

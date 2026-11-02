package com.overleaf.client.workspace;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Local text revisions travel with their project; this is not server audit history. */
public final class ProjectHistory {
    public static final String DIRECTORY = ".overleaf-history";
    private static final Object LOCK = new Object();
    private final Path project, store;

    public record Revision(
            String id,
            String file,
            Instant time,
            String author,
            String reason,
            String label,
            long size) {
        @Override
        public String toString() {
            return time + " · " + file + (label.isBlank() ? "" : " · " + label);
        }
    }

    public ProjectHistory(Path project) {
        this.project = project.toAbsolutePath().normalize();
        store = this.project.resolve(DIRECTORY);
    }

    private void checkStore() throws IOException {
        if (Files.isSymbolicLink(store)) throw new IOException("History cannot be a symbolic link");
    }

    private Path revisionPath(String id) throws IOException {
        checkStore();
        if (id == null || !id.matches("[a-f0-9-]{36}"))
            throw new IOException("Invalid revision id");
        Path dir = store.resolve(id);
        if (Files.isSymbolicLink(dir)
                || Files.isSymbolicLink(dir.resolve("metadata.properties"))
                || Files.isSymbolicLink(dir.resolve("content.txt")))
            throw new IOException("Invalid history entry");
        return dir;
    }

    public Path filePath(String name) throws IOException {
        if (name == null || name.isBlank() || name.contains("\\"))
            throw new IOException("Invalid history file path");
        Path relative;
        try {
            relative = Path.of(name);
        } catch (RuntimeException invalid) {
            throw new IOException(invalid);
        }
        if (relative.isAbsolute()) throw new IOException("Invalid history file path");
        for (Path segment : relative)
            if (segment.toString().startsWith(".")
                    || segment.toString().equalsIgnoreCase("build")
                    || segment.toString().contains(":"))
                throw new IOException("Invalid history file path");
        Path result = project.resolve(relative).normalize();
        if (!result.startsWith(project) || result.equals(project))
            throw new IOException("Outside project");
        for (Path path = result; path != null && path.startsWith(project); path = path.getParent())
            if (Files.isSymbolicLink(path)) throw new IOException("Symlink is not supported");
        return result;
    }

    public List<Revision> revisions() throws IOException {
        synchronized (LOCK) {
            checkStore();
            if (!Files.isDirectory(store)) return List.of();
            List<Revision> revisions = new ArrayList<>();
            try (var dirs = Files.list(store)) {
                for (Path dir :
                        dirs.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                                .toList()) {
                    try {
                        String id = dir.getFileName().toString();
                        Path safe = revisionPath(id);
                        Properties data = metadata(safe);
                        String file = data.getProperty("file");
                        filePath(file);
                        revisions.add(
                                new Revision(
                                        id,
                                        file,
                                        Instant.parse(data.getProperty("time")),
                                        data.getProperty("author", "Local"),
                                        data.getProperty("reason", "Edited"),
                                        data.getProperty("label", ""),
                                        Files.size(safe.resolve("content.txt"))));
                    } catch (IOException | RuntimeException malformed) {
                        /* Skip incomplete entries. */
                    }
                }
            }
            revisions.sort(
                    Comparator.comparing(Revision::time).reversed().thenComparing(Revision::id));
            return List.copyOf(revisions);
        }
    }

    public void capture(String file, String text, String author, String reason) throws IOException {
        synchronized (LOCK) {
            filePath(file);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 2 * 1024 * 1024)
                throw new IOException("History supports text revisions up to 2 MiB");
            List<Revision> revisions = revisions();
            Revision previous =
                    revisions.stream().filter(r -> r.file().equals(file)).findFirst().orElse(null);
            if (previous != null && text.equals(read(previous))) return;
            if ("Before edit".equals(reason)) {
                String fingerprint = hash(bytes);
                for (Revision saved : revisions)
                    if (saved.file().equals(file)
                            && fingerprint.equals(
                                    metadata(revisionPath(saved.id())).getProperty("sha256")))
                        return;
            }
            if (revisions.size() >= 1024
                    || revisions.stream().mapToLong(Revision::size).sum() + bytes.length
                            > 128L * 1024 * 1024)
                throw new IOException("History limit reached (1024 revisions / 128 MiB)");
            Files.createDirectories(store);
            String id = UUID.randomUUID().toString();
            Path staging = Files.createDirectory(store.resolve("pending-" + id));
            try {
                Files.write(staging.resolve("content.txt"), bytes);
                Properties data = new Properties();
                data.setProperty("file", file);
                data.setProperty("time", Instant.now().toString());
                data.setProperty("author", author == null ? "Local" : author);
                data.setProperty("reason", reason == null ? "Edited" : reason);
                data.setProperty("sha256", hash(bytes));
                try (var out = Files.newOutputStream(staging.resolve("metadata.properties"))) {
                    data.store(out, "Overleaf Desktop local history");
                }
                Files.move(staging, store.resolve(id));
            } finally {
                if (Files.exists(staging)) {
                    Files.deleteIfExists(staging.resolve("content.txt"));
                    Files.deleteIfExists(staging.resolve("metadata.properties"));
                    Files.deleteIfExists(staging);
                }
            }
        }
    }

    public String read(Revision revision) throws IOException {
        synchronized (LOCK) {
            Path dir = revisionPath(revision.id());
            Properties data = metadata(dir);
            filePath(data.getProperty("file"));
            if (!revision.file().equals(data.getProperty("file")))
                throw new IOException("Revision identity changed");
            byte[] bytes;
            try (var in = Files.newInputStream(dir.resolve("content.txt"))) {
                bytes = in.readNBytes(2 * 1024 * 1024 + 1);
            }
            if (bytes.length > 2 * 1024 * 1024 || !hash(bytes).equals(data.getProperty("sha256")))
                throw new IOException("Revision content changed or incomplete");
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public void label(Revision revision, String label) throws IOException {
        synchronized (LOCK) {
            Path dir = revisionPath(revision.id());
            read(revision);
            Properties data = metadata(dir);
            String value = label == null ? "" : label.strip();
            if (value.length() > 120) throw new IOException("Label exceeds 120 characters");
            data.setProperty("label", value);
            Path temp = Files.createTempFile(dir, "label-", ".tmp");
            try {
                try (var out = Files.newOutputStream(temp)) {
                    data.store(out, "Overleaf Desktop local history");
                }
                Files.move(
                        temp,
                        dir.resolve("metadata.properties"),
                        StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    private Properties metadata(Path dir) throws IOException {
        if (Files.size(dir.resolve("metadata.properties")) > 16 * 1024)
            throw new IOException("Invalid metadata");
        Properties data = new Properties();
        try (var in = Files.newInputStream(dir.resolve("metadata.properties"))) {
            data.load(in);
        }
        return data;
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}

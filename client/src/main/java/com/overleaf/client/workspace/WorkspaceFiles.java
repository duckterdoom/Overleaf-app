package com.overleaf.client.workspace;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/** File additions are staged and never replace an existing project entry. */
public final class WorkspaceFiles {
    public enum Problem { NAME, EXISTS, SOURCE, LIMIT, IO, URL, CANCELLED }
    public static final class Failure extends IOException {
        private final Problem problem;
        public Failure(Problem problem) { super(problem.name()); this.problem = problem; }
        public Failure(Problem problem, Throwable cause) { super(problem.name(), cause); this.problem = problem; }
        public Problem problem() { return problem; }
        public String message(boolean vi) {
            return switch (problem) {
                case NAME -> vi ? "Dùng đường dẫn bên trong dự án; không dùng tên ẩn, build, tên dành riêng hoặc ký tự không hợp lệ." : "Use a path inside the project without hidden, build, reserved or invalid names.";
                case EXISTS -> vi ? "Tên đã tồn tại hoặc bị trùng trong danh sách. Hãy đổi tên; tệp cũ chưa bị ghi đè." : "An entry already exists or appears twice in the selection. Rename it; existing files were not replaced.";
                case SOURCE -> vi ? "Không đọc được nguồn hoặc nguồn chứa liên kết tượng trưng. Hãy chọn tệp/thư mục thông thường." : "The source is unreadable or contains symbolic links. Select regular files or folders.";
                case LIMIT -> vi ? "Mỗi lần nhập tối đa 10.000 mục và 500 MiB; tải URL tối đa 100 MiB." : "An import is limited to 10,000 entries and 500 MiB; URL downloads to 100 MiB.";
                case URL -> vi ? "Không tải được URL. Dùng liên kết HTTPS trực tiếp tới tệp, không chứa thông tin đăng nhập." : "Could not download the URL. Use a direct HTTPS file link without credentials.";
                case CANCELLED -> vi ? "Đã hủy thao tác." : "Operation cancelled.";
                case IO -> vi ? "Không ghi được vào thư mục dự án. Kiểm tra quyền truy cập và dung lượng trống." : "Could not write to the project. Check folder permissions and available space.";
            };
        }
    }
    public record Addition(Path source, String name) { }
    private record Entry(Path source, String name, boolean directory) { }
    private final Path root;
    private static final int MAX_ENTRIES = 10_000;
    private static final long MAX_BYTES = 500L * 1024 * 1024;
    public WorkspaceFiles(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public Path target(String name) throws IOException {
        if (name == null || name.isBlank() || name.contains("\\") || name.startsWith("/")) throw new Failure(Problem.NAME);
        String[] parts = name.split("/", -1);
        for (String part : parts) {
            if (part.isBlank() || part.startsWith(".") || part.endsWith(".") || part.endsWith(" ")
                    || part.matches(".*[\\x00-\\x1f<>:\"|?*].*")
                    || part.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")
                    || part.equalsIgnoreCase("build")) throw new Failure(Problem.NAME);
        }
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new Failure(Problem.NAME);
        for (Path p = path; p != null && p.startsWith(root); p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw new Failure(Problem.NAME);
        }
        return path;
    }
    public String createFile(String name) throws IOException {
        Path file = target(name);
        if (Files.exists(file, NOFOLLOW_LINKS)) throw new Failure(Problem.EXISTS);
        Files.createDirectories(file.getParent()); Files.createFile(file); return name;
    }
    public String createFolder(String name) throws IOException {
        Path folder = target(name);
        if (Files.exists(folder, NOFOLLOW_LINKS)) throw new Failure(Problem.EXISTS);
        Files.createDirectories(folder); return name;
    }
    public List<String> directories() throws IOException {
        List<String> result = new ArrayList<>(); result.add("");
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                if (path.equals(root)) return FileVisitResult.CONTINUE;
                try { target(relative(path)); } catch (Failure ignored) { return FileVisitResult.SKIP_SUBTREE; }
                result.add(relative(path));
                return result.size() >= MAX_ENTRIES ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });
        result.sort(String.CASE_INSENSITIVE_ORDER); return result;
    }
    private String relative(Path path) { return root.relativize(path).toString().replace('\\', '/'); }

    public List<String> importPaths(List<Path> sources, String directory) throws IOException {
        List<Addition> additions = sources.stream().map(p -> new Addition(p, join(directory, p.getFileName().toString()))).toList();
        return importFiles(additions);
    }
    public static String join(String directory, String name) { return directory == null || directory.isBlank() ? name : directory + "/" + name; }

    public List<String> importFiles(List<Addition> additions) throws IOException {
        if (additions.isEmpty()) throw new Failure(Problem.SOURCE);
        List<Entry> entries = new ArrayList<>(); Set<String> names = new HashSet<>(); long[] bytes = {0};
        for (Addition addition : additions) {
            Path source = addition.source().toAbsolutePath().normalize();
            if (!Files.exists(source, NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) throw new Failure(Problem.SOURCE);
            // Importing the containing folder into itself would recursively copy the workspace.
            if (Files.isDirectory(source) && root.toRealPath().startsWith(source.toRealPath())) throw new Failure(Problem.SOURCE);
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                private void add(Path path, boolean directory, long size) throws IOException {
                    cancelled();
                    String suffix = source.relativize(path).toString().replace('\\', '/');
                    String name = suffix.isEmpty() ? addition.name() : join(addition.name(), suffix);
                    Path destination = target(name);
                    if (Files.exists(destination, NOFOLLOW_LINKS) || !names.add(name.toLowerCase(Locale.ROOT))) throw new Failure(Problem.EXISTS);
                    entries.add(new Entry(path, name, directory)); bytes[0] += size;
                    if (entries.size() > MAX_ENTRIES || bytes[0] > MAX_BYTES) throw new Failure(Problem.LIMIT);
                }
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException { add(dir, true, 0); return FileVisitResult.CONTINUE; }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new Failure(Problem.SOURCE);
                    add(file, false, attrs.size()); return FileVisitResult.CONTINUE;
                }
            });
        }
        Path staging = Files.createTempDirectory(root, ".overleaf-upload-");
        List<Path> created = new ArrayList<>(); List<String> imported = new ArrayList<>(); long[] copied = {0};
        try {
            for (Entry entry : entries) {
                cancelled();
                if (entry.directory()) continue;
                Path staged = staging.resolve(entry.name()); Files.createDirectories(staged.getParent());
                try (InputStream in = Files.newInputStream(entry.source()); OutputStream out = Files.newOutputStream(staged, StandardOpenOption.CREATE_NEW)) {
                    transfer(in, out, MAX_BYTES, copied);
                }
            }
            for (Entry entry : entries) {
                cancelled(); Path destination = target(entry.name());
                if (Files.exists(destination, NOFOLLOW_LINKS)) throw new Failure(Problem.EXISTS);
                makeDirectories(entry.directory() ? destination : destination.getParent(), created);
                if (!entry.directory()) {
                    Files.move(staging.resolve(entry.name()), destination);
                    created.add(destination);
                }
                imported.add(entry.name());
            }
            return imported;
        } catch (IOException | RuntimeException failure) {
            Collections.reverse(created);
            for (Path path : created) try { Files.delete(path); } catch (IOException ignored) { }
            throw failure;
        } finally { removeStaging(staging); }
    }
    private void makeDirectories(Path directory, List<Path> created) throws IOException {
        if (directory.equals(root)) return;
        if (Files.exists(directory, NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) throw new Failure(Problem.EXISTS);
            return;
        }
        makeDirectories(directory.getParent(), created); Files.createDirectory(directory); created.add(directory);
    }
    private static void cancelled() throws Failure { if (Thread.currentThread().isInterrupted()) throw new Failure(Problem.CANCELLED); }
    private static void transfer(InputStream in, OutputStream out, long limit, long[] count) throws IOException {
        byte[] buffer = new byte[64 * 1024]; int size;
        while ((size = in.read(buffer)) != -1) { cancelled(); count[0] += size; if (count[0] > limit) throw new Failure(Problem.LIMIT); out.write(buffer, 0, size); }
    }
    private void removeStaging(Path staging) {
        if (!staging.getParent().equals(root) || !staging.getFileName().toString().startsWith(".overleaf-upload-")) throw new IllegalArgumentException();
        try { Files.walkFileTree(staging, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException { Files.delete(dir); return FileVisitResult.CONTINUE; }
        }); } catch (IOException ignored) { }
    }
    public List<String> download(String address, String name) throws IOException {
        target(name);
        Path temporary = Files.createTempFile("overleaf-download-", ".tmp");
        try {
            URI uri = URI.create(address);
            for (int redirects = 0; redirects <= 5; redirects++) {
                cancelled();
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new Failure(Problem.URL);
                var connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setConnectTimeout(15_000); connection.setReadTimeout(15_000); connection.setInstanceFollowRedirects(false);
                try {
                    int code = connection.getResponseCode();
                    if (code >= 300 && code < 400 && connection.getHeaderField("Location") != null) { uri = uri.resolve(connection.getHeaderField("Location")); continue; }
                    if (code != 200) throw new Failure(Problem.URL);
                    long limit = 100L * 1024 * 1024;
                    if (connection.getContentLengthLong() > limit) throw new Failure(Problem.LIMIT);
                    try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(temporary)) { transfer(in, out, limit, new long[]{0}); }
                    return importFiles(List.of(new Addition(temporary, name)));
                } finally { connection.disconnect(); }
            }
            throw new Failure(Problem.URL);
        } catch (Failure failure) { throw failure; }
        catch (IOException | IllegalArgumentException failure) { throw new Failure(Problem.URL, failure); }
        finally { Files.deleteIfExists(temporary); }
    }
}

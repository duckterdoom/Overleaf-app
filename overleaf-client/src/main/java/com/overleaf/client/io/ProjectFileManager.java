package com.overleaf.client.io;



import java.io.IOException;

import java.nio.charset.StandardCharsets;

import java.nio.file.AtomicMoveNotSupportedException;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.StandardCopyOption;

import java.util.ArrayList;

import java.util.Collections;

import java.util.List;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import java.util.stream.Stream;



/**

 Đọc/ghi file trong thư mục project. Mọi đường dẫn đều được kiểm tra để không


 thoát ra ngoài thư mục gốc (chống path traversal).


 */

public class ProjectFileManager {


    /**
     * Thư mục chứa file build (PDF, log, aux) do latexmk sinh ra.
     */

    public static final String BUILD_DIR = "build";


    private final Path projectRoot;


    /**
     * Nội dung cuối cùng do chính ứng dụng ghi, theo tên file (dùng để chống echo từ watcher).
     */

    private final Map<String, String> lastOwnWrite = new ConcurrentHashMap<>();


    public ProjectFileManager(String projectDirPath) {

        this.projectRoot = Path.of(projectDirPath).toAbsolutePath().normalize();

    }


    /**
     * Đọc nội dung file để load lên Editor. Trả về "" nếu file chưa tồn tại.
     */

    public String readTexFile(String fileName) throws IOException {

        Path filePath = resolveSafe(fileName);

        if (Files.isRegularFile(filePath)) {

            return Files.readString(filePath, StandardCharsets.UTF_8);

        }

        return "";

    }


    /**
     * Ghi nội dung từ Editor xuống file cục bộ. Ghi ra file tạm rồi đổi tên nguyên tử
     * <p>
     * <p>
     * để watcher/latexmk không bao giờ thấy file ghi dở.
     */

    public void writeTexFile(String fileName, String content) throws IOException {

        Path filePath = resolveSafe(fileName);

        Path parent = filePath.getParent();

        if (parent != null) {

            Files.createDirectories(parent);

        }


// Ghi nhớ trước khi ghi để lần watcher báo thay đổi sau đó nhận ra đây là việc của mình.

        lastOwnWrite.put(normalizeName(fileName), content);


// Tên tạm không kết thúc bằng .tex nên watcher bỏ qua.

        Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");

        Files.writeString(tmp, content, StandardCharsets.UTF_8);

        try {

            Files.move(tmp, filePath,

                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        } catch (AtomicMoveNotSupportedException e) {

            Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);

        }

    }


    /**
     * Kiểm tra file hiện tại có đúng bằng nội dung mà ứng dụng vừa ghi hay không.
     * <p>
     * <p>
     * Dùng trong callback của watcher để bỏ qua "tiếng vọng" của chính mình.
     */

    public boolean isOwnWrite(String fileName) {

        String expected = lastOwnWrite.get(normalizeName(fileName));

        if (expected == null) {

            return false;

        }

        try {

            return expected.equals(readTexFile(fileName));

        } catch (IOException e) {

            return false;

        }

    }


    /**
     * Tạo file rỗng nếu chưa có.
     */

    public void createFile(String fileName) throws IOException {

        Path filePath = resolveSafe(fileName);

        Path parent = filePath.getParent();

        if (parent != null) {

            Files.createDirectories(parent);

        }

        if (!Files.exists(filePath)) {

            Files.createFile(filePath);

        }

    }


    public void deleteFile(String fileName) throws IOException {

        lastOwnWrite.remove(normalizeName(fileName));

        Files.deleteIfExists(resolveSafe(fileName));

    }


    public void renameFile(String oldName, String newName) throws IOException {

        Path from = resolveSafe(oldName);

        Path to = resolveSafe(newName);

        Path parent = to.getParent();

        if (parent != null) {

            Files.createDirectories(parent);

        }

        lastOwnWrite.remove(normalizeName(oldName));

        Files.move(from, to);

    }


    /**
     * Liệt kê các file .tex (đường dẫn tương đối, dấu "/"), không tính thư mục build.
     */

    public List<String> listTexFiles() throws IOException {

        return listFiles(".tex");

    }


    /**
     * Liệt kê file theo đuôi (ví dụ ".tex", ".bib"); không đuôi nào được đưa vào = tất cả file.
     */

    public List<String> listFiles(String... extensions) throws IOException {

        if (!Files.isDirectory(projectRoot)) {

            return Collections.emptyList();

        }

        List<String> result = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(projectRoot)) {

            walk.filter(Files::isRegularFile)

                    .map(projectRoot::relativize)

                    .filter(p -> !isInIgnoredDir(p))

                    .map(p -> p.toString().replace('\\', '/'))

                    .filter(name -> matches(name, extensions))

                    .sorted()

                    .forEach(result::add);

        }

        return result;

    }


    /**
     * Đường dẫn file PDF tương ứng với file chính, theo quy ước -outdir=build.
     */

    public Path pdfPathFor(String mainTexFileName) {

        String base = Path.of(mainTexFileName).getFileName().toString();

        int dot = base.lastIndexOf('.');

        if (dot > 0) {

            base = base.substring(0, dot);

        }

        return projectRoot.resolve(BUILD_DIR).resolve(base + ".pdf");

    }


    public Path getProjectRoot() {

        return projectRoot;

    }


// ------------------------------------------------------------------ helpers


    private Path resolveSafe(String fileName) throws IOException {

        if (fileName == null || fileName.isBlank()) {

            throw new IOException("File name must not be empty.");

        }

        Path resolved = projectRoot.resolve(fileName).normalize();

        if (!resolved.startsWith(projectRoot) || resolved.equals(projectRoot)) {

            throw new IOException("Path is outside the project directory: " + fileName);

        }

        return resolved;

    }


    private static String normalizeName(String fileName) {

        return fileName.replace('\\', '/');

    }


    private static boolean isInIgnoredDir(Path relative) {

        for (Path part : relative.getParent() == null ? List.<Path>of() : iterable(relative.getParent())) {

            String s = part.toString();

            if (s.equals(BUILD_DIR) || s.startsWith(".")) {

                return true;

            }

        }

        return false;

    }


    private static Iterable<Path> iterable(Path p) {

        return p;

    }


    private static boolean matches(String name, String[] extensions) {

        if (extensions == null || extensions.length == 0) {

            return true;

        }

        String lower = name.toLowerCase();

        for (String ext : extensions) {

            if (lower.endsWith(ext.toLowerCase())) {

                return true;

            }

        }

        return false;

    }
}
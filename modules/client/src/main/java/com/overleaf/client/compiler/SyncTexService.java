package com.overleaf.client.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Immutable index belonging to one successful PDF. Queries never launch external editors. */
public final class SyncTexService {
    public record PdfPoint(int page, double x, double y) {}

    public record SourcePoint(String file, int line, int column) {}

    private final Path root;
    private final String executable;
    private final byte[] index;

    private SyncTexService(Path root, String executable, byte[] index) {
        this.root = root;
        this.executable = executable;
        this.index = index;
    }

    public static SyncTexService load(Path pdf, String latexmk) throws IOException {
        Path root = pdf.toAbsolutePath().normalize().getParent().getParent().toRealPath();
        Path sidecar =
                pdf.resolveSibling(
                        pdf.getFileName().toString().replaceFirst("\\.pdf$", ".synctex.gz"));
        if (!Files.isRegularFile(sidecar))
            throw new IOException(
                    "Recompile to generate SyncTeX data / Biên dịch lại để tạo SyncTeX.");
        byte[] bytes;
        try (var in = Files.newInputStream(sidecar)) {
            bytes = in.readNBytes(32 * 1024 * 1024 + 1);
        }
        if (bytes.length > 32 * 1024 * 1024) throw new IOException("SyncTeX index exceeds 32 MiB.");
        Path compiler = Path.of(LatexToolchain.resolve(latexmk));
        if (compiler.getParent() == null) {
            String path =
                    System.getenv().entrySet().stream()
                            .filter(e -> e.getKey().equalsIgnoreCase("PATH"))
                            .map(Map.Entry::getValue)
                            .findFirst()
                            .orElse("");
            String command = compiler.toString();
            for (String entry :
                    path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
                if (entry.isBlank()) continue;
                Path candidate = Path.of(entry.replace("\"", "")).resolve(command);
                if (Files.isRegularFile(candidate)) {
                    compiler = candidate;
                    break;
                }
                if (System.getProperty("os.name").startsWith("Windows")
                        && Files.isRegularFile(candidate.resolveSibling(command + ".exe"))) {
                    compiler = candidate.resolveSibling(command + ".exe");
                    break;
                }
            }
        }
        compiler = compiler.toAbsolutePath();
        String name =
                System.getProperty("os.name").startsWith("Windows") ? "synctex.exe" : "synctex";
        Path tool = compiler.resolveSibling(name);
        if (!Files.isRegularFile(tool))
            throw new IOException(
                    "SyncTeX tool is missing beside latexmk / Thiếu công cụ synctex cạnh latexmk. Install the synctex package in your TeX distribution.");
        return new SyncTexService(root, tool.toString(), bytes);
    }

    public PdfPoint forward(String file, int line, int column)
            throws IOException, InterruptedException {
        String safe = source(file);
        var fields =
                query(
                        "view",
                        List.of(
                                "-i",
                                Math.max(1, line) + ":" + Math.max(0, column + 1) + ":./" + safe));
        try {
            int page = Integer.parseInt(fields.get("Page"));
            double x = Double.parseDouble(fields.get("x")), y = Double.parseDouble(fields.get("y"));
            if (page < 1 || !Double.isFinite(x) || !Double.isFinite(y))
                throw new NumberFormatException();
            return new PdfPoint(page - 1, x, y);
        } catch (RuntimeException invalid) {
            throw noMatch();
        }
    }

    public SourcePoint inverse(int page, double x, double y)
            throws IOException, InterruptedException {
        if (page < 0 || !Double.isFinite(x) || !Double.isFinite(y)) throw noMatch();
        var fields =
                query(
                        "edit",
                        List.of("-o", String.format(Locale.ROOT, "%d:%.5f:%.5f:", page + 1, x, y)));
        try {
            int line = Integer.parseInt(fields.get("Line"));
            int column = Integer.parseInt(fields.getOrDefault("Column", "0"));
            if (line < 1) throw new NumberFormatException();
            return new SourcePoint(source(fields.get("Input")), line, Math.max(0, column - 1));
        } catch (RuntimeException invalid) {
            throw noMatch();
        }
    }

    private String source(String input) throws IOException {
        if (input == null) throw noMatch();
        Path file = Path.of(input);
        if (!file.isAbsolute()) file = root.resolve(file);
        file = file.normalize();
        if (!file.startsWith(root)
                || !Files.isRegularFile(file)
                || !file.toRealPath().startsWith(root)
                || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tex"))
            throw new IOException(
                    "SyncTeX target is not a project .tex file / Đích không phải tệp .tex trong dự án.");
        return root.relativize(file).toString().replace('\\', '/');
    }

    private Map<String, String> query(String mode, List<String> arguments)
            throws IOException, InterruptedException {
        Path temp = Files.createTempDirectory("overleaf-synctex-");
        Process process = null;
        try {
            if (System.getProperty("os.name").startsWith("Windows")
                    && !temp.toString().chars().allMatch(c -> c < 128))
                throw new IOException("SyncTeX on Windows requires an ASCII temporary path.");
            // The CLI locates the index by PDF basename; it does not read PDF bytes.
            Path pdf = temp.resolve("main.pdf");
            Files.writeString(pdf, "%PDF-1.4\n");
            Files.write(temp.resolve("main.synctex.gz"), index);
            List<String> command = new ArrayList<>(List.of(executable, mode));
            command.addAll(arguments);
            if (mode.equals("view")) command.addAll(List.of("-o", pdf.toString()));
            else command.set(command.size() - 1, command.getLast() + pdf);
            Path output = temp.resolve("result.txt");
            ProcessBuilder builder =
                    new ProcessBuilder(command)
                            .directory(temp.toFile())
                            .redirectErrorStream(true)
                            .redirectOutput(output.toFile());
            builder.environment().remove("SYNCTEX_VIEWER");
            builder.environment().remove("SYNCTEX_EDITOR");
            process = builder.start();
            if (!process.waitFor(10, TimeUnit.SECONDS))
                throw new IOException("SyncTeX query timed out / Hết thời gian tìm SyncTeX.");
            if (process.exitValue() != 0) throw noMatch();
            String text;
            try (var in = Files.newInputStream(output)) {
                text = new String(in.readNBytes(65536), StandardCharsets.UTF_8);
            }
            return parseResult(text);
        } finally {
            if (process != null && process.isAlive()) {
                process.toHandle().descendants().forEach(child -> child.destroyForcibly());
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            try (var files = Files.list(temp)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            }
            Files.deleteIfExists(temp);
        }
    }

    static Map<String, String> parseResult(String output) {
        Map<String, String> fields = new LinkedHashMap<>();
        boolean result = false;
        for (String line : output.lines().toList()) {
            if (line.equals("SyncTeX result begin")) {
                result = true;
                continue;
            }
            if (line.equals("SyncTeX result end")) break;
            int colon = line.indexOf(':');
            if (result && colon > 0)
                fields.putIfAbsent(line.substring(0, colon), line.substring(colon + 1).strip());
        }
        return fields;
    }

    private static IOException noMatch() {
        return new IOException(
                "No matching SyncTeX position. Recompile or choose nearby text / Không tìm thấy vị trí; biên dịch lại hoặc chọn đoạn văn gần đó.");
    }
}

package com.overleaf.client.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chạy latexmk ở luồng nền, không chặn UI. Mọi callback chạy trên luồng nền: nếu cập nhật JavaFX,
 * hãy bọc trong Platform.runLater.
 *
 * <p>Yêu cầu biên dịch đến khi đang có một lần compile chạy sẽ được gộp: chỉ yêu cầu mới nhất được
 * giữ lại, các yêu cầu cũ hơn nhận onSuperseded().
 */
public class LatexCompilerService {

    /** Mã lỗi khi hết thời gian. */
    public static final int EXIT_TIMEOUT = -3;

    /** Mã lỗi khi không chạy được tiến trình (thường do chưa cài latexmk). */
    public static final int EXIT_START_FAILED = -1;

    /** Mã lỗi khi bị ngắt giữa chừng. */
    public static final int EXIT_INTERRUPTED = -4;

    /** TeX may return zero even though characters were dropped from the PDF. */
    public static final int EXIT_MISSING_GLYPHS = -5;

    private static final String OUT_DIR = "build";
    public static final long DEFAULT_TIMEOUT_SECONDS = 120;

    /** Snapshot per build. A zero timeout explicitly means no time limit. */
    public record Options(String executable, long timeoutSeconds) {
        public Options {
            executable =
                    executable == null || executable.isBlank() ? "latexmk" : executable.strip();
            if (executable.indexOf('\n') >= 0
                    || executable.indexOf('\r') >= 0
                    || executable.indexOf('"') >= 0) {
                throw new IllegalArgumentException(
                        "Choose the executable path without quotes or arguments.");
            }
            Path.of(executable); // Reject invalid paths before a worker starts and the UI becomes
            // busy.
            if (timeoutSeconds < 0)
                throw new IllegalArgumentException("Timeout must be zero or positive.");
        }

        public static Options defaults() {
            return new Options("latexmk", DEFAULT_TIMEOUT_SECONDS);
        }
    }

    public enum Engine {
        PDFLATEX("-pdf"),
        XELATEX("-xelatex"),
        LUALATEX("-lualatex");

        private final String flag;

        Engine(String flag) {
            this.flag = flag;
        }

        public String flag() {
            return flag;
        }
    }

    /** Kết quả một lần compile thành công. */
    public record CompileResult(
            Path pdfPath, long durationMillis, String log, String inputFingerprint) {
        public CompileResult(Path pdfPath, long durationMillis, String log) {
            this(pdfPath, durationMillis, log, "");
        }
    }

    /** Một dòng lỗi LaTeX đã tách (từ -file-line-error). */
    public record LatexIssue(String file, int line, String message) {}

    private static final Pattern ERROR_LINE = Pattern.compile("^(.+?\\.\\w+):(\\d+): (.*)$");

    private record Request(
            String workingDir,
            String mainTexFile,
            Engine engine,
            CompileCallback callback,
            Options options,
            AtomicBoolean cancelled) {}

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "latex-compiler");
                t.setDaemon(true);
                return t;
            });

    private final Object lock = new Object();
    private boolean running;
    private Request pending;
    private boolean shutdown;
    private volatile Process currentProcess;
    private Request currentRequest;
    private volatile Options options = Options.defaults();

    @FunctionalInterface
    interface ProcessLauncher {
        Process start(ProcessBuilder builder) throws IOException;
    }

    private final ProcessLauncher launcher;

    public LatexCompilerService() {
        this(ProcessBuilder::start);
    }

    LatexCompilerService(ProcessLauncher launcher) {
        this.launcher = launcher;
    }

    public void configure(Options options) {
        this.options = java.util.Objects.requireNonNull(options);
    }

    public void setTimeoutSeconds(long seconds) {
        configure(new Options(options.executable(), seconds));
    }

    /** Biên dịch bằng pdfLaTeX. */
    public void compileAsync(String workingDir, String mainTexFile, CompileCallback callback) {
        compileAsync(workingDir, mainTexFile, Engine.PDFLATEX, callback);
    }

    public void compileAsync(
            String workingDir, String mainTexFile, Engine engine, CompileCallback callback) {
        Request req =
                new Request(
                        workingDir,
                        mainTexFile,
                        engine == null ? Engine.PDFLATEX : engine,
                        callback,
                        options,
                        new AtomicBoolean());

        synchronized (lock) {
            if (shutdown) {
                safely(() -> callback.onError(EXIT_START_FAILED, "Compiler service is shut down."));
                return;
            }
            if (running) {
                Request old = pending;
                pending = req;
                if (old != null) {
                    safely(old.callback::onSuperseded);
                }
                return;
            }
            running = true;
            currentRequest = req;
        }

        try {
            executor.submit(() -> runLoop(req));
        } catch (RejectedExecutionException e) {
            synchronized (lock) {
                running = false;
                currentRequest = null;
            }
            safely(() -> callback.onError(EXIT_START_FAILED, "Compiler service is shut down."));
        }
    }

    private void runLoop(Request first) {
        Request req = first;
        while (req != null) {
            runOne(req);
            synchronized (lock) {
                req = pending;
                pending = null;
                if (req == null || shutdown) {
                    running = false;
                    req = null;
                }
                currentRequest = req;
            }
        }
    }

    private void runOne(Request req) {
        long start = System.nanoTime();
        CompileCallback cb = req.callback;
        StringBuilder log = new StringBuilder();

        Process process;
        String inputFingerprint;
        CompileWorkspace workspace = null;
        try {
            Files.createDirectories(Path.of(req.workingDir).resolve(OUT_DIR));
            inputFingerprint = ProjectInputs.fingerprint(Path.of(req.workingDir));
            workspace = CompileWorkspace.prepare(Path.of(req.workingDir));
            CompileWorkspace.prepareOutputDirectories(workspace.directory());
            ProcessBuilder pb =
                    new ProcessBuilder(
                            req.options.executable(),
                            req.engine.flag(),
                            "-interaction=nonstopmode",
                            "-file-line-error",
                            "-outdir=" + OUT_DIR,
                            req.mainTexFile);
            Path previousLog =
                    workspace
                            .directory()
                            .resolve(OUT_DIR)
                            .resolve(pdfName(req.mainTexFile).replaceFirst("\\.pdf$", ".log"));
            if (!parseErrors(readTexDiagnostics(previousLog)).isEmpty()) {
                // A previous failure may be cached even after an output folder/package has been
                // fixed.
                pb.command().add(pb.command().size() - 1, "-g");
            }
            pb.directory(workspace.directory().toFile());
            pb.redirectErrorStream(true);
            useToolDirectory(pb, req.options);
            synchronized (lock) {
                if (req.cancelled.get() || shutdown) {
                    workspace.close();
                    safely(() ->
                            cb.onError(EXIT_INTERRUPTED, "[BUILD] Cancelled before starting."));
                    return;
                }
                process = launcher.start(pb);
                currentProcess = process;
            }
        } catch (IOException e) {
            if (workspace != null) workspace.close();
            safely(() ->
                    cb.onError(
                            EXIT_START_FAILED,
                            "Không chạy được "
                                    + req.options.executable()
                                    + ". Chọn đường dẫn latexmk trong Settings > Compilation hoặc cài TeX Live/MiKTeX. "
                                    + e.getMessage()));
            return;
        }
        try {
            // Không có gì để gửi vào stdin; đóng lại để tiến trình không chờ nhập.
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
                // không quan trọng
            }

            // Đọc log ở luồng riêng để waitFor có timeout hoạt động và log được stream từng dòng.
            final Process p = process;
            Thread reader =
                    new Thread(
                            () -> {
                                try (BufferedReader r =
                                        new BufferedReader(
                                                new InputStreamReader(
                                                        p.getInputStream(),
                                                        StandardCharsets.UTF_8))) {
                                    String line;
                                    while ((line = r.readLine()) != null) {
                                        synchronized (log) {
                                            log.append(line).append('\n');
                                        }
                                        final String l = line;
                                        safely(() -> cb.onLogLine(l));
                                    }
                                } catch (IOException ignored) {
                                    // luồng đóng khi tiến trình bị hủy
                                }
                            },
                            "latex-log-reader");
            reader.setDaemon(true);
            reader.start();

            long timeout = req.options.timeoutSeconds();
            boolean finished;
            if (timeout == 0) {
                process.waitFor();
                finished = true;
            } else {
                finished = process.waitFor(timeout, TimeUnit.SECONDS);
            }
            if (req.cancelled.get()) {
                reader.join(2000);
                safely(() ->
                        cb.onError(
                                EXIT_INTERRUPTED, snapshot(log) + "\n[BUILD] Cancelled by user."));
                return;
            }
            if (!finished) {
                destroyTree(process);
                process.waitFor(5, TimeUnit.SECONDS);
                reader.join(2000);
                String text =
                        snapshot(log) + "\n[Hết thời gian chờ " + timeout + "s, đã dừng latexmk]";
                safely(() -> cb.onError(EXIT_TIMEOUT, text));
                return;
            }
            reader.join(2000);

            int exitCode = process.exitValue();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            Path texLog =
                    workspace
                            .directory()
                            .resolve(OUT_DIR)
                            .resolve(pdfName(req.mainTexFile).replaceFirst("\\.pdf$", ".log"));
            String output = snapshot(log);
            String diagnostics =
                    exitCode != 0 && parseErrors(output).isEmpty()
                            ? readTexDiagnostics(texLog)
                            : "";
            String text =
                    diagnostics.isEmpty() || parseErrors(diagnostics).isEmpty()
                            ? output
                            : output + "\n[BUILD] TeX log diagnostics:\n" + diagnostics;

            if (exitCode == 0) {
                String missing = missingGlyphMessage(text, texLog);
                if (missing != null) {
                    safely(() ->
                            cb.onError(
                                    EXIT_MISSING_GLYPHS,
                                    text
                                            + "\n[FONT ERROR] "
                                            + missing
                                            + "\nPDF contains missing characters. Fix the font/engine configuration before exporting."));
                    return;
                }
                Path pdf = workspace.directory().resolve(OUT_DIR).resolve(pdfName(req.mainTexFile));
                if (Files.isRegularFile(pdf)) {
                    Path published = workspace.publishPdf(pdfName(req.mainTexFile));
                    safely(() ->
                            cb.onSuccess(
                                    new CompileResult(
                                            published, durationMs, text, inputFingerprint)));
                } else {
                    safely(() ->
                            cb.onError(
                                    0,
                                    text
                                            + "\n[latexmk báo thành công nhưng không thấy file PDF: "
                                            + pdf
                                            + "]"));
                }
            } else {
                safely(() -> cb.onError(exitCode, text));
            }
        } catch (IOException failure) {
            safely(() ->
                    cb.onError(
                            EXIT_START_FAILED,
                            snapshot(log)
                                    + "\nCannot publish the compiled PDF: "
                                    + failure.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyTree(process);
            safely(() -> cb.onError(EXIT_INTERRUPTED, snapshot(log) + "\n[Bị ngắt]"));
        } finally {
            currentProcess = null;
            workspace.close();
        }
    }

    /** Kiểm tra latexmk có chạy được không (để UI báo rõ nếu người dùng chưa cài LaTeX). */
    public boolean isLatexmkAvailable() {
        try {
            ProcessBuilder builder =
                    new ProcessBuilder(options.executable(), "-v").redirectErrorStream(true);
            useToolDirectory(builder, options);
            Process p = builder.start();
            p.getOutputStream().close();
            // Xả output để tiến trình không bị kẹt bộ đệm.
            Thread drain =
                    new Thread(
                            () -> {
                                try {
                                    p.getInputStream()
                                            .transferTo(java.io.OutputStream.nullOutputStream());
                                } catch (IOException ignored) {
                                    // bỏ qua
                                }
                            },
                            "latexmk-check");
            drain.setDaemon(true);
            drain.start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                destroyTree(p);
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Keeps actual TeX diagnostics, including ! errors, ahead of launcher summary messages. */
    public static List<LatexIssue> parseErrors(String log) {
        List<LatexIssue> issues = new ArrayList<>();
        if (log == null) {
            return issues;
        }
        int pending = -1;
        for (String raw : log.split("\\R")) {
            if (raw.stripLeading().startsWith("!")) {
                String message = raw.stripLeading().substring(1).strip();
                if (message.isEmpty() || (secondaryError(message) && !issues.isEmpty())) continue;
                pending = -1;
                for (int i = 0; i < issues.size(); i++)
                    if (issues.get(i).message().equals(message)) {
                        pending = i;
                        break;
                    }
                if (pending < 0) {
                    pending = issues.size();
                    issues.add(new LatexIssue("", 0, message));
                }
                continue;
            }
            Matcher m = ERROR_LINE.matcher(raw);
            if (m.matches()) {
                String file = m.group(1);
                String message = m.group(3);
                if (file.toLowerCase(java.util.Locale.ROOT).endsWith("runscript.tlu")
                        || message.toLowerCase(java.util.Locale.ROOT)
                                .contains("command failed with exit code")) continue;
                if (file.startsWith("./")) {
                    file = file.substring(2);
                }
                try {
                    int line = Integer.parseInt(m.group(2));
                    if (pending >= 0 && issues.get(pending).file().isEmpty()) {
                        LatexIssue first = issues.get(pending);
                        issues.set(pending, new LatexIssue(file, line, first.message()));
                    }
                    pending = -1;
                    if (!(secondaryError(message) && !issues.isEmpty())) {
                        LatexIssue issue = new LatexIssue(file, line, message);
                        if (!issues.contains(issue)) issues.add(issue);
                    }
                } catch (NumberFormatException ignored) {
                    // bỏ qua dòng không hợp lệ
                }
            }
        }
        return issues;
    }

    private static boolean secondaryError(String message) {
        return message.contains("Emergency stop") || message.contains("Fatal error occurred");
    }

    private static String readTexDiagnostics(Path path) {
        if (!Files.isRegularFile(path)) return "";
        try (var input = Files.newInputStream(path)) {
            return new String(input.readNBytes(16 * 1024 * 1024), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    /** Stops the active process tree and discards queued builds. Safe before process startup. */
    public void cancelCompile() {
        Request discarded;
        synchronized (lock) {
            discarded = pending;
            pending = null;
            if (currentRequest != null) currentRequest.cancelled.set(true);
            if (currentProcess != null) destroyTree(currentProcess);
        }
        if (discarded != null) safely(discarded.callback::onSuperseded);
    }

    /** Tắt Thread Pool khi tắt ứng dụng. */
    public void shutdown() {
        synchronized (lock) {
            shutdown = true;
            Request old = pending;
            pending = null;
            if (old != null) {
                safely(old.callback::onSuperseded);
            }
        }
        cancelCompile();
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ helpers

    static String missingGlyphMessage(String output, Path texLog) {
        String found =
                output.lines()
                        .filter(line -> line.startsWith("Missing character:"))
                        .findFirst()
                        .orElse(null);
        if (found != null) return found;
        // latexmk may reuse a cached PDF without repeating the underlying TeX diagnostics.
        if (Files.isRegularFile(texLog)) {
            try (var input = Files.newInputStream(texLog)) {
                String log = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                return log.lines()
                        .filter(line -> line.startsWith("Missing character:"))
                        .findFirst()
                        .orElse(null);
            } catch (IOException unreadable) {
                return "Cannot verify TeX font diagnostics: " + unreadable.getMessage();
            }
        }
        return null;
    }

    /** Tools invoked by latexmk must come from the selected TeX installation too. */
    static void useToolDirectory(ProcessBuilder builder, Options options) {
        Path executable = Path.of(options.executable());
        if (executable.getParent() == null) return;
        String bin = executable.toAbsolutePath().normalize().getParent().toString();
        String key =
                builder.environment().keySet().stream()
                        .filter(k -> k.equalsIgnoreCase("PATH"))
                        .findFirst()
                        .orElse("PATH");
        builder.environment()
                .put(key, bin + File.pathSeparator + builder.environment().getOrDefault(key, ""));
    }

    private static void destroyTree(Process p) {
        // latexmk sinh tiến trình con (pdflatex...), cần hủy cả cây.
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    private static String pdfName(String mainTexFile) {
        String base = Path.of(mainTexFile).getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base + ".pdf";
    }

    private static String snapshot(StringBuilder log) {
        synchronized (log) {
            return log.toString();
        }
    }

    /** Mỗi callback được bọc riêng: lỗi trong callback không bị nhầm thành lỗi biên dịch. */
    private static void safely(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Giao diện callback để UI nhận kết quả. */
    public interface CompileCallback {
        /** Gọi cho từng dòng log khi latexmk đang chạy. Mặc định bỏ qua. */
        default void onLogLine(String line) {}

        void onSuccess(CompileResult result);

        void onError(int exitCode, String errorLog);

        /** Yêu cầu này bị thay bằng yêu cầu mới hơn trước khi kịp chạy. Mặc định bỏ qua. */
        default void onSuperseded() {}
    }
}

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chạy latexmk ở luồng nền, không chặn UI. Mọi callback chạy trên luồng nền:
 * nếu cập nhật Swing, hãy bọc trong SwingUtilities.invokeLater.
 *
 * Yêu cầu biên dịch đến khi đang có một lần compile chạy sẽ được gộp: chỉ yêu cầu
 * mới nhất được giữ lại, các yêu cầu cũ hơn nhận onSuperseded().
 */
public class LatexCompilerService {

    /** Mã lỗi khi hết thời gian. */
    public static final int EXIT_TIMEOUT = -3;
    /** Mã lỗi khi không chạy được tiến trình (thường do chưa cài latexmk). */
    public static final int EXIT_START_FAILED = -1;
    /** Mã lỗi khi bị ngắt giữa chừng. */
    public static final int EXIT_INTERRUPTED = -4;

    private static final String OUT_DIR = "build";
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

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
    public record CompileResult(Path pdfPath, long durationMillis, String log) {
    }

    /** Một dòng lỗi LaTeX đã tách (từ -file-line-error). */
    public record LatexIssue(String file, int line, String message) {
    }

    private static final Pattern ERROR_LINE = Pattern.compile("^(.+?\\.\\w+):(\\d+): (.*)$");

    private record Request(String workingDir, String mainTexFile, Engine engine, CompileCallback callback) {
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "latex-compiler");
        t.setDaemon(true);
        return t;
    });

    private final Object lock = new Object();
    private boolean running;
    private Request pending;
    private boolean shutdown;
    private volatile Process currentProcess;
    private volatile long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    public void setTimeoutSeconds(long seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException("Timeout must be positive.");
        }
        this.timeoutSeconds = seconds;
    }

    /** Biên dịch bằng pdfLaTeX. */
    public void compileAsync(String workingDir, String mainTexFile, CompileCallback callback) {
        compileAsync(workingDir, mainTexFile, Engine.PDFLATEX, callback);
    }

    public void compileAsync(String workingDir, String mainTexFile, Engine engine, CompileCallback callback) {
        Request req = new Request(workingDir, mainTexFile,
                engine == null ? Engine.PDFLATEX : engine, callback);

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
        }

        try {
            executor.submit(() -> runLoop(req));
        } catch (RejectedExecutionException e) {
            synchronized (lock) {
                running = false;
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
            }
        }
    }

    private void runOne(Request req) {
        long start = System.nanoTime();
        CompileCallback cb = req.callback;
        StringBuilder log = new StringBuilder();

        Process process;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "latexmk",
                    req.engine.flag(),
                    "-interaction=nonstopmode",
                    "-file-line-error",
                    "-outdir=" + OUT_DIR,
                    req.mainTexFile);
            pb.directory(new File(req.workingDir));
            pb.redirectErrorStream(true);
            process = pb.start();
        } catch (IOException e) {
            safely(() -> cb.onError(EXIT_START_FAILED,
                    "Không chạy được latexmk (đã cài TeX Live/MiKTeX và thêm vào PATH chưa?): " + e.getMessage()));
            return;
        }
        currentProcess = process;

        try {
            // Không có gì để gửi vào stdin; đóng lại để tiến trình không chờ nhập.
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
                // không quan trọng
            }

            // Đọc log ở luồng riêng để waitFor có timeout hoạt động và log được stream từng dòng.
            final Process p = process;
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
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
            }, "latex-log-reader");
            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                destroyTree(process);
                process.waitFor(5, TimeUnit.SECONDS);
                reader.join(2000);
                String text = snapshot(log) + "\n[Hết thời gian chờ " + timeoutSeconds + "s, đã dừng latexmk]";
                safely(() -> cb.onError(EXIT_TIMEOUT, text));
                return;
            }
            reader.join(2000);

            int exitCode = process.exitValue();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            String text = snapshot(log);

            if (exitCode == 0) {
                Path pdf = Path.of(req.workingDir).toAbsolutePath().normalize()
                        .resolve(OUT_DIR).resolve(pdfName(req.mainTexFile));
                if (Files.isRegularFile(pdf)) {
                    safely(() -> cb.onSuccess(new CompileResult(pdf, durationMs, text)));
                } else {
                    safely(() -> cb.onError(0, text + "\n[latexmk báo thành công nhưng không thấy file PDF: " + pdf + "]"));
                }
            } else {
                safely(() -> cb.onError(exitCode, text));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyTree(process);
            safely(() -> cb.onError(EXIT_INTERRUPTED, snapshot(log) + "\n[Bị ngắt]"));
        } finally {
            currentProcess = null;
        }
    }

    /** Kiểm tra latexmk có chạy được không (để UI báo rõ nếu người dùng chưa cài LaTeX). */
    public boolean isLatexmkAvailable() {
        try {
            Process p = new ProcessBuilder("latexmk", "-v").redirectErrorStream(true).start();
            p.getOutputStream().close();
            // Xả output để tiến trình không bị kẹt bộ đệm.
            Thread drain = new Thread(() -> {
                try {
                    p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                } catch (IOException ignored) {
                    // bỏ qua
                }
            }, "latexmk-check");
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

    /** Tách các dòng lỗi dạng "file:line: message" trong log để UI đánh dấu dòng lỗi. */
    public static List<LatexIssue> parseErrors(String log) {
        List<LatexIssue> issues = new ArrayList<>();
        if (log == null) {
            return issues;
        }
        for (String raw : log.split("\\R")) {
            Matcher m = ERROR_LINE.matcher(raw);
            if (m.matches()) {
                String file = m.group(1);
                if (file.startsWith("./")) {
                    file = file.substring(2);
                }
                try {
                    issues.add(new LatexIssue(file, Integer.parseInt(m.group(2)), m.group(3)));
                } catch (NumberFormatException ignored) {
                    // bỏ qua dòng không hợp lệ
                }
            }
        }
        return issues;
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
        Process p = currentProcess;
        if (p != null) {
            destroyTree(p);
        }
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ helpers

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
        default void onLogLine(String line) {
        }

        void onSuccess(CompileResult result);

        void onError(int exitCode, String errorLog);

        /** Yêu cầu này bị thay bằng yêu cầu mới hơn trước khi kịp chạy. Mặc định bỏ qua. */
        default void onSuperseded() {
        }
    }
}

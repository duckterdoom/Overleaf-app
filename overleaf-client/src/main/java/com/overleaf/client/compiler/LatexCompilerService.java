package com.overleaf.client.compiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LatexCompilerService {
    // Sử dụng Thread Pool để quản lý tiến trình ngầm, không block UI
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void compileAsync(String workingDir, String mainTexFile, CompileCallback callback) {
        executor.submit(() -> {
            try {
                // Khởi tạo lệnh latexmk
                ProcessBuilder pb = new ProcessBuilder(
                        "latexmk",
                        "-pdf",
                        "-interaction=nonstopmode",
                        "-file-line-error",
                        mainTexFile
                );
                pb.directory(new File(workingDir));
                pb.redirectErrorStream(true); // Gộp luồng lỗi vào luồng đầu ra chuẩn

                Process process = pb.start();

                // Đọc log output từ quá trình compile
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder outputLog = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    outputLog.append(line).append("\n");
                    // Có thể gọi callback streaming từng dòng tại đây nếu UI cần live logs
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    callback.onSuccess(outputLog.toString());
                } else {
                    callback.onError(exitCode, outputLog.toString());
                }
            } catch (Exception e) {
                callback.onError(-1, "Lỗi I/O khi khởi chạy ProcessBuilder: " + e.getMessage());
            }
        });
    }

    // Tắt Thread Pool khi tắt ứng dụng
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    // Giao diện Callbacks để Swing UI nhận kết quả
    public interface CompileCallback {
        void onSuccess(String log);
        void onError(int exitCode, String errorLog);
    }
}
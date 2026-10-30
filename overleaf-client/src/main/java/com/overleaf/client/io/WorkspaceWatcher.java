
package com.overleaf.client.io;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;

public class WorkspaceWatcher {

    private final Path directory;

    private volatile WatchService watchService;

    private ExecutorService watcherThread;
    private ScheduledExecutorService debounceExecutor;

    private ScheduledFuture<?> pendingTask;

    private static final long DEBOUNCE_DELAY = 500;

    public WorkspaceWatcher(String dirPath) {
        this.directory = Paths.get(dirPath)
                .toAbsolutePath()
                .normalize();
    }

    public synchronized void startWatching(FileChangeListener listener)
            throws IOException {

        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }

        if (!Files.isDirectory(directory)) {
            throw new IOException(
                    "Workspace directory does not exist: " + directory
            );
        }

        if (watcherThread != null && !watcherThread.isShutdown()) {
            throw new IllegalStateException("Watcher is already running.");
        }

        watchService = FileSystems.getDefault().newWatchService();

        try {
            directory.register(
                    watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE
            );
        } catch (IOException e) {
            watchService.close();
            watchService = null;
            throw e;
        }

        watcherThread = Executors.newSingleThreadExecutor();

        debounceExecutor = Executors.newSingleThreadScheduledExecutor();

        watcherThread.submit(() -> {

            try {
                while (!Thread.currentThread().isInterrupted()) {

                    WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents()) {

                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        Object context = event.context();

                        if (!(context instanceof Path changedFile)) {
                            continue;
                        }

                        if (changedFile.toString()
                                .toLowerCase()
                                .endsWith(".tex")) {

                            scheduleCallback(
                                    changedFile.toString(),
                                    listener
                            );
                        }
                    }

                    if (!key.reset()) {
                        break;
                    }
                }

            } catch (ClosedWatchServiceException e) {
                // WatchService đã đóng.

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Debounce: chỉ gọi callback sau khi không còn event mới trong 500ms
    private synchronized void scheduleCallback(
            String fileName,
            FileChangeListener listener) {

        if (debounceExecutor == null ||
                debounceExecutor.isShutdown()) {
            return;
        }

        // Hủy callback trước đó nếu vẫn chưa được thực hiện
        if (pendingTask != null) {
            pendingTask.cancel(false);
        }

        // Lên lịch callback mới sau 500ms
        pendingTask = debounceExecutor.schedule(
                () -> listener.onFileChanged(fileName),
                DEBOUNCE_DELAY,
                TimeUnit.MILLISECONDS
        );
    }

    public synchronized void stopWatching() {

        // Hủy callback đang chờ
        if (pendingTask != null) {
            pendingTask.cancel(false);
            pendingTask = null;
        }

        // Đóng WatchService
        try {
            if (watchService != null) {
                watchService.close();
                watchService = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Dừng watcher thread
        if (watcherThread != null) {
            watcherThread.shutdownNow();
            watcherThread = null;
        }

        // Dừng debounce thread
        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
            debounceExecutor = null;
        }
    }

    public interface FileChangeListener {
        void onFileChanged(String fileName);
    }
}

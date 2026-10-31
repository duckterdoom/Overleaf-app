package com.overleaf.client.io;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Theo dõi thay đổi file nguồn LaTeX trong thư mục project (đệ quy).
 * Debounce riêng cho từng file. Callback chạy trên luồng nền: nếu cập nhật Swing,
 * hãy bọc trong SwingUtilities.invokeLater.
 */
public class WorkspaceWatcher {

    private static final Logger LOG = Logger.getLogger(WorkspaceWatcher.class.getName());

    private static final long DEBOUNCE_DELAY = 500;
    private static final Set<String> WATCHED_EXTENSIONS = Set.of(".tex", ".bib", ".sty", ".cls");

    private final Path directory;

    /** Mỗi file có một tác vụ debounce riêng, nên đổi hai file cùng lúc không làm mất sự kiện. */
    private final Map<String, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();
    private final Map<WatchKey, Path> keys = new ConcurrentHashMap<>();

    private volatile WatchService watchService;
    private ExecutorService watcherThread;
    private ScheduledExecutorService debounceExecutor;

    public WorkspaceWatcher(String dirPath) {
        this.directory = Paths.get(dirPath).toAbsolutePath().normalize();
    }

    public synchronized void startWatching(FileChangeListener listener) throws IOException {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null.");
        }
        if (!Files.isDirectory(directory)) {
            throw new IOException("Workspace directory does not exist: " + directory);
        }
        if (watcherThread != null && !watcherThread.isShutdown()) {
            throw new IllegalStateException("Watcher is already running.");
        }

        final WatchService ws = FileSystems.getDefault().newWatchService();
        try {
            registerRecursive(ws, directory);
        } catch (IOException e) {
            keys.clear();
            ws.close();
            throw e;
        }
        watchService = ws;

        final ScheduledExecutorService debounce = Executors.newSingleThreadScheduledExecutor(
                r -> daemon("workspace-debounce", r));
        debounceExecutor = debounce;
        watcherThread = Executors.newSingleThreadExecutor(r -> daemon("workspace-watcher", r));

        watcherThread.submit(() -> runLoop(ws, debounce, listener));
    }

    private void runLoop(WatchService ws, ScheduledExecutorService debounce, FileChangeListener listener) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = ws.take();
                Path dir = keys.get(key);

                if (dir != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            LOG.warning("File system events overflowed; some changes may be missed.");
                            continue;
                        }
                        if (!(event.context() instanceof Path name)) {
                            continue;
                        }
                        Path child = dir.resolve(name);

                        // Thư mục mới tạo: đăng ký theo dõi luôn.
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(child)) {
                            try {
                                registerRecursive(ws, child);
                            } catch (IOException e) {
                                LOG.log(Level.WARNING, "Cannot watch new directory: " + child, e);
                            }
                            continue;
                        }

                        if (isWatchedFile(child)) {
                            String relative = directory.relativize(child).toString().replace('\\', '/');
                            scheduleCallback(relative, listener, debounce);
                        }
                    }
                } else {
                    key.pollEvents();
                }

                if (!key.reset()) {
                    keys.remove(key);
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        } catch (ClosedWatchServiceException e) {
            // WatchService đã đóng.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Workspace watcher stopped unexpectedly", e);
        }
    }

    private void registerRecursive(WatchService ws, Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(directory) && isIgnoredDirectory(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                WatchKey key = dir.register(ws,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                keys.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isIgnoredDirectory(Path dir) {
        String name = dir.getFileName().toString();
        return name.equals(ProjectFileManager.BUILD_DIR) || name.startsWith(".");
    }

    private boolean isWatchedFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String ext : WATCHED_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** Debounce theo từng file: chỉ gọi callback sau khi file đó yên 500ms. */
    private void scheduleCallback(String fileName, FileChangeListener listener, ScheduledExecutorService debounce) {
        if (debounce.isShutdown()) {
            return;
        }
        pendingTasks.compute(fileName, (name, old) -> {
            if (old != null) {
                old.cancel(false);
            }
            try {
                return debounce.schedule(() -> fire(name, listener), DEBOUNCE_DELAY, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                return null;
            }
        });
    }

    private void fire(String fileName, FileChangeListener listener) {
        try {
            if (Files.exists(directory.resolve(fileName))) {
                listener.onFileChanged(fileName);
            } else {
                listener.onFileDeleted(fileName);
            }
        } catch (Throwable t) {
            // Không để lỗi của listener bị ScheduledFuture nuốt im lặng.
            LOG.log(Level.SEVERE, "FileChangeListener failed for " + fileName, t);
        }
    }

    public synchronized void stopWatching() {
        pendingTasks.values().forEach(f -> f.cancel(false));
        pendingTasks.clear();

        WatchService ws = watchService;
        watchService = null;
        if (ws != null) {
            try {
                ws.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to close WatchService", e);
            }
        }
        keys.clear();

        if (watcherThread != null) {
            watcherThread.shutdownNow();
            watcherThread = null;
        }
        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
            debounceExecutor = null;
        }
    }

    private static Thread daemon(String name, Runnable r) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    public interface FileChangeListener {
        /** File được tạo hoặc sửa. fileName là đường dẫn tương đối so với thư mục project. */
        void onFileChanged(String fileName);

        /** File bị xóa. Mặc định không làm gì. */
        default void onFileDeleted(String fileName) {
        }
    }
}
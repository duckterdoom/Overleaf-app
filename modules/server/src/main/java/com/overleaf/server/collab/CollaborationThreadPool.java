package com.overleaf.server.collab;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ========================================================================= M4: QUẢN LÝ THREAD POOL
 * CỘNG TÁC (Collaboration Thread Pool)
 * =========================================================================
 *
 * <p>Lớp quản lý TẤT CẢ các nhóm luồng (Thread Pool) dùng cho việc xử lý đồng bộ và cộng tác trong
 * Server Overleaf Desktop.
 *
 * <p>Kiến trúc Thread Pool:
 *
 * <p>┌─────────────────────────────────────────────────────────────────┐ │ 1. SYNC POOL (Fixed
 * Thread Pool) │ │ - Xử lý các thao tác đồng bộ văn bản (CRDT operations) │ │ - Số luồng cố định =
 * 2 × số lõi CPU (tối ưu cho I/O) │ │ - Mỗi thao tác TEXT_EDIT được giao cho 1 luồng xử lý │
 * ├─────────────────────────────────────────────────────────────────┤ │ 2. BROADCAST POOL (Cached
 * Thread Pool) │ │ - Phát sóng thông điệp đến các client trong phòng │ │ - Luồng được tạo theo nhu
 * cầu và thu hồi khi rảnh │ │ - Không chặn luồng xử lý chính (non-blocking broadcast) │
 * ├─────────────────────────────────────────────────────────────────┤ │ 3. SCHEDULER (Scheduled
 * Thread Pool) │ │ - Heartbeat kiểm tra kết nối định kỳ (mỗi 5 giây) │ │ - Tự động lưu trạng thái
 * tài liệu định kỳ (mỗi 30 giây) │ │ - Dọn dẹp phòng trống định kỳ (mỗi 60 giây) │
 * └─────────────────────────────────────────────────────────────────┘
 */
public class CollaborationThreadPool {

    /** Số luồng cho Sync Pool = 2 × số lõi CPU */
    private static final int SYNC_POOL_SIZE =
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

    /** Số luồng cho Scheduler */
    private static final int SCHEDULER_POOL_SIZE = 3;

    // ---- Các Thread Pool ----

    /** Pool xử lý thao tác đồng bộ CRDT (dùng fixed pool cho ổn định) */
    private final ExecutorService syncPool;

    /** Pool phát sóng thông điệp (dùng cached pool cho linh hoạt) */
    private final ExecutorService broadcastPool;

    /** Pool cho các tác vụ định kỳ: heartbeat, auto-save, cleanup */
    private final ScheduledExecutorService scheduler;

    /** Trạng thái hoạt động của thread pool */
    private volatile boolean isRunning;

    /**
     * Khởi tạo tất cả các thread pool. Tất cả luồng được đặt chế độ daemon = true để không ngăn JVM
     * tắt.
     */
    public CollaborationThreadPool() {
        // 1. Sync Pool: Fixed, dùng cho CRDT
        this.syncPool =
                Executors.newFixedThreadPool(
                        SYNC_POOL_SIZE,
                        runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setName("M4-SyncWorker-" + thread.getId());
                            thread.setDaemon(true);
                            return thread;
                        });

        // 2. Broadcast Pool: Cached, co giãn theo nhu cầu
        this.broadcastPool =
                Executors.newCachedThreadPool(runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("M4-Broadcaster-" + thread.getId());
                    thread.setDaemon(true);
                    return thread;
                });

        // 3. Scheduler: Cho heartbeat và auto-save
        this.scheduler =
                Executors.newScheduledThreadPool(
                        SCHEDULER_POOL_SIZE,
                        runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setName("M4-Scheduler-" + thread.getId());
                            thread.setDaemon(true);
                            return thread;
                        });

        this.isRunning = true;
        System.out.println("[M4-ThreadPool] Đã khởi tạo thành công:");
        System.out.println("  - SyncPool: " + SYNC_POOL_SIZE + " luồng cố định");
        System.out.println("  - BroadcastPool: Cached (co giãn tự động)");
        System.out.println("  - Scheduler: " + SCHEDULER_POOL_SIZE + " luồng định kỳ");
    }

    // =========================================================================
    // GỬI TÁC VỤ VÀO THREAD POOL
    // =========================================================================

    /**
     * Gửi tác vụ đồng bộ CRDT vào Sync Pool. Dùng khi nhận được thao tác TEXT_EDIT từ client.
     *
     * @param task tác vụ xử lý CRDT (Runnable)
     */
    public void submitSyncTask(Runnable task) {
        if (isRunning && !syncPool.isShutdown()) {
            syncPool.submit(task);
        }
    }

    /**
     * Gửi tác vụ phát sóng vào Broadcast Pool. Dùng khi cần gửi thông điệp đến nhiều client trong
     * phòng.
     *
     * @param task tác vụ phát sóng (Runnable)
     */
    public void submitBroadcastTask(Runnable task) {
        if (isRunning && !broadcastPool.isShutdown()) {
            broadcastPool.submit(task);
        }
    }

    /**
     * Đăng ký tác vụ chạy ĐỊNH KỲ (ví dụ: heartbeat, auto-save).
     *
     * @param task tác vụ cần chạy định kỳ
     * @param initialDelay độ trễ ban đầu trước lần chạy đầu tiên (giây)
     * @param period khoảng thời gian giữa các lần chạy (giây)
     */
    public void scheduleAtFixedRate(Runnable task, long initialDelay, long period) {
        if (isRunning && !scheduler.isShutdown()) {
            scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.SECONDS);
        }
    }

    /**
     * Đăng ký tác vụ chạy SAU MỘT KHOẢNG THỜI GIAN (delay).
     *
     * @param task tác vụ cần chạy
     * @param delay thời gian chờ (giây)
     */
    public void scheduleWithDelay(Runnable task, long delay) {
        if (isRunning && !scheduler.isShutdown()) {
            scheduler.schedule(task, delay, TimeUnit.SECONDS);
        }
    }

    // =========================================================================
    // TẮT AN TOÀN (Graceful Shutdown)
    // =========================================================================

    /**
     * Tắt tất cả các thread pool một cách AN TOÀN.
     *
     * <p>Quy trình: 1. Gửi tín hiệu shutdown (không nhận task mới) 2. Chờ tối đa 10 giây cho các
     * task đang chạy hoàn thành 3. Nếu quá thời gian → buộc dừng tất cả (shutdownNow)
     */
    public void shutdown() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        System.out.println("[M4-ThreadPool] Đang tắt tất cả các thread pool...");

        // Gửi tín hiệu shutdown
        syncPool.shutdown();
        broadcastPool.shutdown();
        scheduler.shutdown();

        try {
            // Chờ Sync Pool hoàn thành
            if (!syncPool.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("[M4-ThreadPool] SyncPool không dừng kịp → buộc tắt!");
                syncPool.shutdownNow();
            }

            // Chờ Broadcast Pool hoàn thành
            if (!broadcastPool.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("[M4-ThreadPool] BroadcastPool không dừng kịp → buộc tắt!");
                broadcastPool.shutdownNow();
            }

            // Chờ Scheduler hoàn thành
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("[M4-ThreadPool] Scheduler không dừng kịp → buộc tắt!");
                scheduler.shutdownNow();
            }

            System.out.println("[M4-ThreadPool] Đã tắt tất cả thread pool thành công.");

        } catch (InterruptedException e) {
            System.err.println("[M4-ThreadPool] Bị gián đoạn khi đang chờ tắt → buộc tắt!");
            syncPool.shutdownNow();
            broadcastPool.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ---- Getter ----

    /** Kiểm tra thread pool còn đang hoạt động hay không */
    public boolean isRunning() {
        return isRunning;
    }

    /** Lấy kích thước Sync Pool */
    public int getSyncPoolSize() {
        return SYNC_POOL_SIZE;
    }
}

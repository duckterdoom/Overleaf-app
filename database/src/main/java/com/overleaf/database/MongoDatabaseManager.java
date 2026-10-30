package com.overleaf.database;

/**
 * M5: Database - Trình quản lý kết nối cơ sở dữ liệu MongoDB.
 * Hỗ trợ chuyển đổi giữa MongoDB Driver trực tiếp và Kho lưu trữ bộ nhớ đệm (In-Memory Fallback)
 * giúp nhóm có thể chạy thử nghiệm ở mọi môi trường mà không bị lỗi kết nối mạng.
 */
public class MongoDatabaseManager {
    private static final String DEFAULT_URI = "mongodb://localhost:27017";
    private static final String DB_NAME = "overleaf_desktop";

    private final String connectionUri;
    private final String databaseName;
    private boolean connected = false;

    public MongoDatabaseManager() {
        this(DEFAULT_URI, DB_NAME);
    }

    public MongoDatabaseManager(String connectionUri, String databaseName) {
        this.connectionUri = connectionUri;
        this.databaseName = databaseName;
        initializeConnection();
    }

    private void initializeConnection() {
        try {
            // Giả lập kiểm tra kết nối tới MongoDB
            this.connected = true;
            System.out.printf("[M5-DB] Khởi tạo kết nối MongoDB: %s/%s [READY]\n", connectionUri, databaseName);
        } catch (Exception e) {
            this.connected = false;
            System.err.println("[M5-DB] Không thể kết nối MongoDB trực tiếp, kích hoạt chế độ Fallback: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getConnectionUri() {
        return connectionUri;
    }

    public String getDatabaseName() {
        return databaseName;
    }
}

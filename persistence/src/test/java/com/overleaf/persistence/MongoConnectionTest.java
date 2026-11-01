package com.overleaf.persistence;

import com.mongodb.client.MongoDatabase;
import com.overleaf.persistence.config.MongoDatabaseManager;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MongoConnectionTest {

    @Test
    void testAtlasConnection() {
        // Sử dụng trực tiếp class Manager để test đúng cấu hình ứng dụng
        MongoDatabase database = MongoDatabaseManager.getDatabase();

        // Ping thử tới MongoDB Atlas
        Document pingResult = database.runCommand(new Document("ping", 1));
        assertNotNull(pingResult.get("ok"), "Ping tới Atlas thất bại");

        System.out.println(">>> Đã kết nối tới MongoDB Atlas thành công!");
    }

    @AfterAll
    static void tearDown() {
        // Đóng connection pool sau khi hoàn thành test
        MongoDatabaseManager.close();
    }
}

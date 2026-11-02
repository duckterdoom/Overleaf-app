package com.overleaf.persistence.config;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import java.io.InputStream;
import java.util.Properties;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

public class MongoDatabaseManager {

    private static String connectionString;
    private static String dbName;
    private static MongoClient mongoClient = null;

    static {
        try (InputStream input =
                MongoDatabaseManager.class
                        .getClassLoader()
                        .getResourceAsStream("config.properties")) {

            Properties prop = new Properties();
            if (input == null) {
                throw new RuntimeException(
                        "Không tìm thấy file config.properties trong resources!");
            }
            prop.load(input);

            connectionString = prop.getProperty("mongo.uri");
            dbName = prop.getProperty("mongo.dbname");
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi đọc cấu hình MongoDatabaseManager", e);
        }
    }

    private MongoDatabaseManager() {}

    public static synchronized MongoDatabase getDatabase() {
        if (mongoClient == null) {
            // Đăng ký CodecProvider để tự động convert POJO <-> BSON
            CodecRegistry pojoCodecRegistry =
                    fromRegistries(
                            MongoClientSettings.getDefaultCodecRegistry(),
                            fromProviders(PojoCodecProvider.builder().automatic(true).build()));

            MongoClientSettings settings =
                    MongoClientSettings.builder()
                            .applyConnectionString(new ConnectionString(connectionString))
                            .codecRegistry(pojoCodecRegistry)
                            .build();

            mongoClient = MongoClients.create(settings);
        }
        return mongoClient.getDatabase(dbName);
    }

    public static synchronized void close() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
        }
    }
}

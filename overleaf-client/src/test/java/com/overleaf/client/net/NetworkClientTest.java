package com.overleaf.client.net;

import com.overleaf.common.sync.*;
import com.overleaf.server.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class NetworkClientTest {
    static final String TOKEN = "test-access-token-at-least-24-characters";

    @Test void largeOfflineUploadReassemblesBeforeApplyingAndReplayIsIdempotent() throws Exception {
        ServerConfig config = new ServerConfig("127.0.0.1", 0, 8, 60000, TOKEN, false, 32, 200000);
        try (OverleafServer server = new OverleafServer(config)) {
            server.start();
            try (NetworkClient seed = new NetworkClient("127.0.0.1", server.getPort(), false, TOKEN)) {
                BlockingQueue<SyncMessage> initial = new LinkedBlockingQueue<>();
                seed.start(initial::add, e -> { });
                seed.send(SyncMessage.createJoinRoom("bulk", "seed", "seed"));
                await(initial, MessageType.CHECKPOINT);
            }
            OfflineOperationQueue queue = new OfflineOperationQueue("bulk", "u", "u");
            queue.onConnectionLost();
            String text = "\u0001".repeat(150);
            for (int i = 0; i < 1200; i++) queue.prepareEdit(new CRDTOperation("u" + i,
                    CRDTOperation.OpType.INSERT, i * text.length(), text, "u", i + 1, 1));
            SyncMessage request = queue.onReconnected();
            assertThrows(IOException.class, () -> MessageCodec.encode(request)); // larger than one frame
            BlockingQueue<SyncMessage> inbox = new LinkedBlockingQueue<>();
            try (NetworkClient client = new NetworkClient("127.0.0.1", server.getPort(), false, TOKEN)) {
                client.start(message -> { queue.handleServerMessage(message); inbox.add(message); }, e -> { });
                client.send(request);
                assertEquals(text.repeat(1200), await(inbox, MessageType.FULL_SYNC).getPayload());
                await(inbox, MessageType.CHECKPOINT);
                assertEquals(0, queue.getPendingCount());
                assertFalse(queue.isResyncing());
                client.send(request); // simulate ACKs lost before disconnect
                assertEquals(text.repeat(1200), await(inbox, MessageType.FULL_SYNC).getPayload());
                await(inbox, MessageType.CHECKPOINT);
            }
        }
    }

    private static SyncMessage await(BlockingQueue<SyncMessage> messages, MessageType type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            SyncMessage message = messages.poll(100, TimeUnit.MILLISECONDS);
            if (message != null && message.getMessageType() == type) return message;
        }
        throw new AssertionError("Expected " + type);
    }

    @Test void apiClientsJoinEditAndReceiveDisconnect() throws Exception {
        ServerConfig config = new ServerConfig("127.0.0.1", 0, 8, 60000, TOKEN, false, 32, 100000);
        try (OverleafServer server = new OverleafServer(config)) {
            server.start();
            BlockingQueue<SyncMessage> inbox = new LinkedBlockingQueue<>();
            CountDownLatch disconnected = new CountDownLatch(1);
            try (NetworkClient client = new NetworkClient("127.0.0.1", server.getPort(), false, TOKEN)) {
                client.start(inbox::add, e -> disconnected.countDown());
                client.send(SyncMessage.createJoinRoom("r", "a", "Alice"));
                await(inbox, MessageType.FULL_SYNC); await(inbox, MessageType.CHECKPOINT);
                CRDTOperation op = new CRDTOperation("a1", CRDTOperation.OpType.INSERT, 0, "Hello", "a", 1, 1);
                client.send(SyncMessage.createTextEdit("r", "a", "Alice", OperationCodec.toJson(op)));
                assertEquals("a1", await(inbox, MessageType.ACK).getPayload());
                server.close();
                assertTrue(disconnected.await(3, TimeUnit.SECONDS));
                assertThrows(IOException.class, () -> client.send(SyncMessage.createHeartbeat("r", "a")));
            }
        }
    }

    @Test void tlsAuthenticatesAndVerifiesHostname(@TempDir Path temporary) throws Exception {
        Path keyStorePath = temporary.resolve("test.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
        Process generate = new ProcessBuilder(keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost",
                "-keystore", keyStorePath.toString(), "-storetype", "PKCS12", "-storepass", "test-password", "-noprompt")
                .redirectErrorStream(true).redirectOutput(temporary.resolve("keytool.log").toFile()).start();
        assertTrue(generate.waitFor(15, TimeUnit.SECONDS));
        assertEquals(0, generate.exitValue());
        SSLContext serverContext = TlsContexts.server(java.util.Map.of("OVERLEAF_KEYSTORE", keyStorePath.toString(),
                "OVERLEAF_KEYSTORE_PASSWORD", "test-password"));
        SSLContext clientContext = TlsContexts.client(java.util.Map.of("OVERLEAF_TRUSTSTORE", keyStorePath.toString(),
                "OVERLEAF_TRUSTSTORE_PASSWORD", "test-password"));
        try (OverleafServer server = new OverleafServer(new ServerConfig("127.0.0.1", 0, 8, 60000, TOKEN, true, 32, 100000), serverContext)) {
            server.start();
            assertThrows(IOException.class, () -> new NetworkClient("127.0.0.1", server.getPort(), true, TOKEN, clientContext));
            assertThrows(IOException.class, () -> new NetworkClient("localhost", server.getPort(), true, "wrong", clientContext));
            try (NetworkClient client = new NetworkClient("localhost", server.getPort(), true, TOKEN, clientContext)) {
                BlockingQueue<SyncMessage> inbox = new LinkedBlockingQueue<>();
                client.start(inbox::add, e -> { });
                client.send(SyncMessage.createJoinRoom("tls-room", "a", "Alice"));
                assertEquals("", await(inbox, MessageType.FULL_SYNC).getPayload());
                await(inbox, MessageType.CHECKPOINT);
            }
        }
    }
}

package com.overleaf.server.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.overleaf.common.model.CRDTOperation;
import com.overleaf.common.protocol.MessageCodec;
import com.overleaf.common.protocol.MessageType;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.common.sync.OperationCodec;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(15)
class NetworkingTest {
    static final String TOKEN = "test-access-token-at-least-24-characters";

    static ServerConfig config(int max, int timeout, int size) {
        return new ServerConfig("127.0.0.1", 0, max, timeout, TOKEN, false, 32, size);
    }

    static final class Peer implements AutoCloseable {
        final Socket socket;
        final String id;
        String room = "r";

        Peer(OverleafServer server, String id) throws Exception {
            this.socket = new Socket("127.0.0.1", server.getPort());
            this.id = id;
            socket.setSoTimeout(3000);
            send(new SyncMessage(MessageType.AUTH, "", "", "", TOKEN));
            assertEquals("AUTHENTICATED", expect(MessageType.ACK).getPayload());
        }

        void send(SyncMessage message) throws IOException {
            MessageCodec.write(socket.getOutputStream(), message);
        }

        SyncMessage expect(MessageType type) throws IOException {
            // Presence events are asynchronous relative to other clients' requests.
            for (int i = 0; i < 20; i++) {
                SyncMessage message = MessageCodec.read(socket.getInputStream());
                assertNotNull(message);
                if (message.getMessageType() == MessageType.USER_LIST_UPDATE
                        && type != MessageType.USER_LIST_UPDATE) continue;
                assertEquals(type, message.getMessageType());
                return message;
            }
            throw new AssertionError("Expected " + type);
        }

        String join() throws Exception {
            send(SyncMessage.createJoinRoom(room, id, id));
            String snapshot = expect(MessageType.FULL_SYNC).getPayload();
            expect(MessageType.CHECKPOINT);
            expect(MessageType.USER_LIST_UPDATE);
            return snapshot;
        }

        CRDTOperation op(String operationId, String user, int pos, String text, long time) {
            return new CRDTOperation(
                    operationId, CRDTOperation.OpType.INSERT, pos, text, user, time, 1);
        }

        void edit(CRDTOperation operation) throws Exception {
            send(SyncMessage.createTextEdit(room, id, id, OperationCodec.toJson(operation)));
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    void joinEditSnapshotHeartbeatAndDisconnect() throws Exception {
        try (OverleafServer server = new OverleafServer(config(8, 60000, 100000))) {
            server.start();
            try (Peer a = new Peer(server, "a");
                    Peer b = new Peer(server, "b")) {
                assertEquals("", a.join());
                b.join();
                a.edit(a.op("a-1", "a", 0, "Xin chào 🌏", 1));
                assertEquals("a-1", a.expect(MessageType.ACK).getPayload());
                assertEquals(
                        "Xin chào 🌏",
                        OperationCodec.fromJson(b.expect(MessageType.TEXT_EDIT).getPayload())
                                .getContent());
                b.send(SyncMessage.createHeartbeat("r", "b"));
                assertEquals("pong", b.expect(MessageType.HEARTBEAT).getPayload());
                b.close();
                a.expect(
                        MessageType
                                .USER_LIST_UPDATE); // b's join may still be queued; wait via fresh
                // snapshot below.
                try (Peer late = new Peer(server, "late")) {
                    assertEquals("Xin chào 🌏", late.join());
                }
            }
        }
    }

    @Test
    void preservesPipelinedJoinAndEditOrder() throws Exception {
        try (OverleafServer server = new OverleafServer(config(8, 60000, 100000))) {
            server.start();
            try (Peer a = new Peer(server, "a")) {
                a.send(SyncMessage.createJoinRoom("r", "a", "a"));
                a.edit(a.op("a1", "a", 0, "hello", 1));
                a.edit(a.op("a2", "a", 5, "!", 2));
                a.expect(MessageType.FULL_SYNC);
                a.expect(MessageType.CHECKPOINT);
                assertEquals("a1", a.expect(MessageType.ACK).getPayload());
                assertEquals("a2", a.expect(MessageType.ACK).getPayload());
                try (Peer b = new Peer(server, "b")) {
                    assertEquals("hello!", b.join());
                }
            }
        }
    }

    @Test
    void validatesMembershipIdentityOperationsAndDocumentLimit() throws Exception {
        try (OverleafServer server = new OverleafServer(config(8, 60000, 10))) {
            server.start();
            try (Peer a = new Peer(server, "a");
                    Peer duplicate = new Peer(server, "a")) {
                a.edit(a.op("before", "a", 0, "bad", 1));
                a.expect(MessageType.ERROR);
                a.join();
                duplicate.send(SyncMessage.createJoinRoom("r", "a", "a"));
                duplicate.expect(MessageType.ERROR);
                a.edit(a.op("spoof", "other", 0, "bad", 1));
                a.expect(MessageType.ERROR);
                a.send(SyncMessage.createHeartbeat("wrong-room", "a"));
                a.expect(MessageType.ERROR);
                a.edit(a.op("large", "a", 0, "x".repeat(11), 1));
                a.expect(MessageType.ERROR);
                a.send(SyncMessage.createFullSync("r", "malicious"));
                a.expect(MessageType.ERROR);
                a.send(SyncMessage.createCursorUpdate("r", "a", "a", "-1,2,file"));
                a.expect(MessageType.ERROR);
                a.edit(a.op("valid", "a", 0, "ok", 1));
                a.expect(MessageType.ACK);
            }
        }
    }

    @Test
    void isolatesRoomsAndRelaysCursor() throws Exception {
        try (OverleafServer server = new OverleafServer(config(8, 60000, 100000))) {
            server.start();
            try (Peer a = new Peer(server, "a");
                    Peer b = new Peer(server, "b");
                    Peer c = new Peer(server, "c")) {
                a.join();
                b.join();
                c.room = "other";
                c.join();
                a.send(SyncMessage.createCursorUpdate("r", "a", "forged-name", "2,7,main.tex"));
                SyncMessage cursor = b.expect(MessageType.CURSOR_UPDATE);
                assertEquals("a", cursor.getSenderName());
                assertEquals("2,7,main.tex", cursor.getPayload());
                c.socket.setSoTimeout(200);
                assertThrows(
                        SocketTimeoutException.class,
                        () -> MessageCodec.read(c.socket.getInputStream()));
                a.send(SyncMessage.createLeaveRoom("r", "a", "a"));
                a.send(SyncMessage.createHeartbeat("r", "a"));
                a.expect(MessageType.ERROR);
            }
        }
    }

    @Test
    void offlineResyncUsesRealSocketAndDeduplicatesEdits() throws Exception {
        try (OverleafServer server = new OverleafServer(config(8, 60000, 100000))) {
            server.start();
            try (Peer a = new Peer(server, "a")) {
                a.join();
                CRDTOperation operation = a.op("a1", "a", 0, "Hello", 1);
                a.edit(operation);
                a.expect(MessageType.ACK);
                // Same authenticated session may resync; no JOIN required before RESYNC.
                a.send(
                        new SyncMessage(
                                MessageType.RESYNC,
                                "r",
                                "a",
                                "a",
                                OperationCodec.encodeResync("", List.of(operation))));
                a.expect(MessageType.ACK);
                assertEquals("Hello", a.expect(MessageType.FULL_SYNC).getPayload());
                assertEquals("a1", a.expect(MessageType.CHECKPOINT).getPayload());
                try (Peer offline = new Peer(server, "b")) {
                    CRDTOperation pending = offline.op("b1", "b", 5, "!", 2);
                    offline.send(
                            new SyncMessage(
                                    MessageType.RESYNC,
                                    "r",
                                    "b",
                                    "b",
                                    OperationCodec.encodeResync("a1", List.of(pending))));
                    offline.expect(MessageType.ACK);
                    assertEquals("Hello!", offline.expect(MessageType.FULL_SYNC).getPayload());
                    offline.expect(MessageType.CHECKPOINT);
                }
            }
        }
    }

    @Test
    void rejectsAuthMalformedFrameIdleAndTooManyConnections() throws Exception {
        try (OverleafServer server = new OverleafServer(config(1, 400, 100000))) {
            server.start();
            try (Socket bad = new Socket("127.0.0.1", server.getPort())) {
                bad.setSoTimeout(3000);
                MessageCodec.write(
                        bad.getOutputStream(),
                        new SyncMessage(MessageType.AUTH, "", "", "", "wrong"));
                assertNull(MessageCodec.read(bad.getInputStream()));
            }
            try (Peer a = new Peer(server, "a")) {
                a.join();
                try (Socket excess = new Socket("127.0.0.1", server.getPort())) {
                    excess.setSoTimeout(3000);
                    assertNull(MessageCodec.read(excess.getInputStream()));
                }
                assertNull(MessageCodec.read(a.socket.getInputStream())); // idle timeout
            }
        }
        try (OverleafServer server = new OverleafServer(config(8, 60000, 100000))) {
            server.start();
            try (Peer a = new Peer(server, "a")) {
                DataOutputStream output = new DataOutputStream(a.socket.getOutputStream());
                output.writeInt(MessageCodec.MAX_FRAME_BYTES + 1);
                output.flush();
                assertNull(MessageCodec.read(a.socket.getInputStream()));
            }
            try (Peer healthy = new Peer(server, "healthy")) {
                healthy.join();
                server.close();
                assertNull(MessageCodec.read(healthy.socket.getInputStream()));
                assertEquals(0, server.getSessionCount());
            }
        }
    }

    @Test
    void refusesUnsafeExternalBindAndInvalidConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerConfig("0.0.0.0", 8080, 64, 60000, "", false, 32, 100000));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerConfig("0.0.0.0", 8080, 64, 60000, TOKEN, false, 32, 100000));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerConfig("127.0.0.1", -1, 64, 60000, "", false, 32, 100000));
        assertFalse(config(8, 60000, 100000).toString().contains(TOKEN));
    }

    @Test
    void reconnectsSameUserOverNewSocketAndReplaysAtDocumentLimit() throws Exception {
        try (OverleafServer server = new OverleafServer(config(8, 60000, 5))) {
            server.start();
            CRDTOperation operation;
            try (Peer first = new Peer(server, "a")) {
                first.join();
                operation = first.op("a1", "a", 0, "Hello", 1);
                first.edit(operation);
                first.expect(MessageType.ACK);
            }
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (server.getSessionCount() != 0 && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals(0, server.getSessionCount());
            try (Peer reconnected = new Peer(server, "a")) {
                reconnected.send(
                        new SyncMessage(
                                MessageType.RESYNC,
                                "r",
                                "a",
                                "a",
                                OperationCodec.encodeResync("", List.of(operation))));
                reconnected.expect(MessageType.ACK);
                assertEquals("Hello", reconnected.expect(MessageType.FULL_SYNC).getPayload());
                reconnected.expect(MessageType.CHECKPOINT);
            }
        }
    }

    @Test
    void slowSessionHasBoundedQueue() throws Exception {
        try (OverleafServer server = new OverleafServer(config(8, 60000, 100000));
                Socket socket = new Socket()) {
            ServerSession session = new ServerSession(socket, server, 1);
            session.send(SyncMessage.createFullSync("r", "first"));
            assertThrows(
                    IllegalStateException.class,
                    () -> session.send(SyncMessage.createFullSync("r", "second")));
            assertFalse(session.isOpen());
            assertTrue(socket.isClosed());
        }
    }

    @Test
    void lateJoinClockPreventsDoubleTransformAndUnknownRoomCodeIsStable() throws Exception {
        try (OverleafServer server = new OverleafServer(config(8, 60000, 100000))) {
            server.start();
            try (Peer alice = new Peer(server, "alice");
                    Peer bob = new Peer(server, "bob")) {
                alice.join();
                alice.edit(alice.op("alice1", "alice", 0, "Hello", 1));
                alice.expect(MessageType.ACK);
                bob.send(SyncMessage.createJoinRoom("r", "bob", "bob"));
                SyncMessage snapshot = bob.expect(MessageType.FULL_SYNC);
                SyncMessage checkpoint = bob.expect(MessageType.CHECKPOINT);
                assertTrue(snapshot.getLamportTimestamp() >= 1);
                bob.edit(bob.op("bob1", "bob", 0, ">", checkpoint.getLamportTimestamp() + 1));
                bob.expect(MessageType.ACK);
                try (Peer observer = new Peer(server, "observer")) {
                    assertEquals(">Hello", observer.join());
                }
                bob.send(new SyncMessage(MessageType.RESYNC, "missing", "bob", "bob", ""));
                assertEquals("ROOM_UNKNOWN", bob.expect(MessageType.ERROR).getPayload());
            }
        }
    }
}

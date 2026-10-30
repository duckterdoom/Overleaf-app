package com.overleaf.server;

import com.overleaf.common.collab.*;
import com.overleaf.server.collab.RoomManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class OverleafServerTest {
    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final String name;
        String room;

        Peer(OverleafServer server, String name) throws IOException {
            this.name = name;
            socket = new Socket("127.0.0.1", server.getPort());
            socket.setSoTimeout(2000);
        }

        void send(MessageType type, String content) throws IOException {
            MessageCodec.write(socket.getOutputStream(), new SyncMessage(type, name, room, content));
        }

        SyncMessage read(MessageType expected) throws IOException {
            SyncMessage message = MessageCodec.read(socket.getInputStream());
            assertNotNull(message);
            assertEquals(expected, message.getType());
            return message;
        }

        String join(String room) throws IOException {
            this.room = room;
            send(MessageType.JOIN_ROOM, null);
            read(MessageType.JOIN_ROOM);
            String snapshot = read(MessageType.TEXT_UPDATE).getContent();
            read(MessageType.USER_LIST);
            return snapshot;
        }

        void noMessage() {
            assertThrows(SocketTimeoutException.class, () -> {
                socket.setSoTimeout(200);
                try { MessageCodec.read(socket.getInputStream()); }
                finally { socket.setSoTimeout(2000); }
            });
        }

        @Override public void close() throws IOException { socket.close(); }
    }

    @Test
    void broadcastsSnapshotsAndPresenceOnlyWithinRoom() throws Exception {
        try (OverleafServer server = new OverleafServer(0)) {
            server.start();
            try (Peer a = new Peer(server, "alice"); Peer b = new Peer(server, "bob"); Peer c = new Peer(server, "carol")) {
                assertEquals("", a.join("project"));
                assertEquals("", b.join("project"));
                assertEquals("alice\nbob", a.read(MessageType.USER_LIST).getContent());
                c.join("other");
                a.send(MessageType.TEXT_UPDATE, "\\section{Tiếng Việt 🌏}\nHello");
                assertEquals("alice", a.read(MessageType.TEXT_UPDATE).getSender());
                assertEquals("\\section{Tiếng Việt 🌏}\nHello", b.read(MessageType.TEXT_UPDATE).getContent());
                c.noMessage();
                b.send(MessageType.LEAVE_ROOM, null);
                assertEquals("alice", a.read(MessageType.USER_LIST).getContent());
                b.send(MessageType.TEXT_UPDATE, "forbidden");
                b.read(MessageType.ERROR);
                assertEquals("\\section{Tiếng Việt 🌏}\nHello", b.join("project"));
                a.read(MessageType.USER_LIST);
                b.close();
                assertEquals("alice", a.read(MessageType.USER_LIST).getContent());
            }
        }
    }

    @Test
    void validatesMembershipDuplicateNamesSenderAndCursor() throws Exception {
        try (OverleafServer server = new OverleafServer(0)) {
            server.start();
            try (Peer a = new Peer(server, "alice"); Peer b = new Peer(server, "alice")) {
                a.send(MessageType.TEXT_UPDATE, "not joined");
                a.read(MessageType.ERROR);
                a.join("r");
                b.room = "r";
                b.send(MessageType.JOIN_ROOM, null);
                b.read(MessageType.ERROR);
                // The same name is allowed in another room.
                b.join("elsewhere");
                MessageCodec.write(a.socket.getOutputStream(), new SyncMessage(MessageType.CHAT_MESSAGE, "forged", "r", "hello"));
                assertEquals("alice", a.read(MessageType.CHAT_MESSAGE).getSender());
                MessageCodec.write(a.socket.getOutputStream(), new SyncMessage(MessageType.TEXT_UPDATE, "alice", "elsewhere", "attack"));
                a.read(MessageType.ERROR);
                SyncMessage cursor = new SyncMessage(MessageType.CURSOR_MOVE, "alice", "r", null);
                cursor.setLine(-1);
                MessageCodec.write(a.socket.getOutputStream(), cursor);
                a.read(MessageType.ERROR);
                a.send(MessageType.COMPILE_REQUEST, null);
                a.read(MessageType.ERROR);
                b.noMessage();
            }
        }
    }

    @Test
    void relaysCursorWithoutEchoAndMovesBetweenRooms() throws Exception {
        try (OverleafServer server = new OverleafServer(0)) {
            server.start();
            try (Peer a = new Peer(server, "a"); Peer b = new Peer(server, "b")) {
                a.join("r"); b.join("r"); a.read(MessageType.USER_LIST);
                SyncMessage cursor = new SyncMessage(MessageType.CURSOR_MOVE, "spoof", "r", null);
                cursor.setLine(2); cursor.setColumn(7);
                MessageCodec.write(a.socket.getOutputStream(), cursor);
                SyncMessage received = b.read(MessageType.CURSOR_MOVE);
                assertEquals("a", received.getSender());
                assertEquals(2, received.getLine()); assertEquals(7, received.getColumn());
                a.noMessage();
                a.join("new");
                assertEquals("b", b.read(MessageType.USER_LIST).getContent());
                a.send(MessageType.CHAT_MESSAGE, "private");
                a.read(MessageType.CHAT_MESSAGE);
                b.noMessage();
            }
        }
    }

    @Test
    void concurrentWritersHaveSameBroadcastOrder() throws Exception {
        try (OverleafServer server = new OverleafServer(0)) {
            server.start();
            try (Peer a = new Peer(server, "a"); Peer b = new Peer(server, "b")) {
                a.join("r"); b.join("r"); a.read(MessageType.USER_LIST);
                ExecutorService senders = Executors.newFixedThreadPool(2);
                try {
                    CountDownLatch start = new CountDownLatch(1);
                    List<Future<?>> tasks = new ArrayList<>();
                    for (Peer peer : List.of(a, b)) {
                        tasks.add(senders.submit(() -> {
                            start.await();
                            for (int i = 0; i < 10; i++) peer.send(MessageType.TEXT_UPDATE, peer.name + i);
                            return null;
                        }));
                    }
                    start.countDown();
                    for (Future<?> task : tasks) task.get(3, TimeUnit.SECONDS);
                    List<String> orderA = new ArrayList<>();
                    List<String> orderB = new ArrayList<>();
                    for (int i = 0; i < 20; i++) {
                        orderA.add(a.read(MessageType.TEXT_UPDATE).getContent());
                        orderB.add(b.read(MessageType.TEXT_UPDATE).getContent());
                    }
                    assertEquals(orderA, orderB);
                    try (Peer late = new Peer(server, "late")) {
                        assertEquals(orderA.get(19), late.join("r"));
                    }
                } finally { senders.shutdownNow(); }
            }
        }
    }

    @Test
    void malformedConnectionDoesNotStopServerAndShutdownClosesSockets() throws Exception {
        OverleafServer server = new OverleafServer(0);
        try (server) {
            server.start();
            try (Peer bad = new Peer(server, "bad")) {
                DataOutputStream malformed = new DataOutputStream(bad.socket.getOutputStream());
                malformed.writeInt(MessageCodec.MAX_FRAME_BYTES + 1);
                malformed.flush();
                assertNull(MessageCodec.read(bad.socket.getInputStream()));
            }
            try (Peer good = new Peer(server, "good")) {
                good.join("r");
                server.close();
                assertNull(MessageCodec.read(good.socket.getInputStream()));
                assertEquals(0, server.getSessionCount());
            }
            assertThrows(IllegalStateException.class, server::start);
        }
    }

    @Test
    void enforcesConnectionLimit() throws Exception {
        try (OverleafServer server = new OverleafServer(0, 1, new RoomManager())) {
            server.start();
            try (Peer first = new Peer(server, "first")) {
                first.join("r");
                try (Peer rejected = new Peer(server, "second")) {
                    assertNull(MessageCodec.read(rejected.socket.getInputStream()));
                }
                assertEquals(1, server.getSessionCount());
            }
        }
    }
}

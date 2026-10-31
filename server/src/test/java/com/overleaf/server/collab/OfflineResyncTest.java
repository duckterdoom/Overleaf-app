package com.overleaf.server.collab;

import com.overleaf.common.protocol.MessageType;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.server.collab.SyncBroadcaster.ClientSession;
import com.overleaf.server.collab.M4TestSupport.Env;
import com.overleaf.server.collab.M4TestSupport.TestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.overleaf.server.collab.M4TestSupport.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm tra chức năng sửa offline rồi đồng bộ lại (Offline Operation Queue + RESYNC). */
class OfflineResyncTest {

    private Env env;

    @AfterEach
    void tearDown() {
        if (env != null) env.close();
    }

    private void join(TestClient c) {
        int before = (int) c.received.stream().filter(m -> m.getMessageType() == MessageType.CHECKPOINT).count();
        env.manager.handleMessage(c, SyncMessage.createJoinRoom(c.room, c.id, c.id));
        assertTrue(await(() -> c.received.stream()
                .filter(m -> m.getMessageType() == MessageType.CHECKPOINT).count() > before), c.id + " vào phòng");
    }

    /** Gửi một thao tác online và chờ server áp dụng xong (client nhận ACK). */
    private void sendAndWait(TestClient c, int pos, String text) {
        int acksBefore = (int) c.received.stream().filter(m -> m.getMessageType() == MessageType.ACK).count();
        env.manager.handleMessage(c, c.typeInsert(pos, text));
        assertTrue(await(() -> c.received.stream()
                .filter(m -> m.getMessageType() == MessageType.ACK).count() > acksBefore), c.id + " nhận ACK");
    }

    private void resync(TestClient c) {
        int before = (int) c.received.stream().filter(m -> m.getMessageType() == MessageType.CHECKPOINT
                || m.getMessageType() == MessageType.ERROR).count();
        env.manager.handleMessage(c, c.queue.onReconnected());
        assertTrue(await(() -> c.received.stream().filter(m -> m.getMessageType() == MessageType.CHECKPOINT
                || m.getMessageType() == MessageType.ERROR).count() > before), c.id + " nhận phản hồi RESYNC");
    }

    @Test
    void offlineEditsMergeWithOthersEdits() {
        env = new Env(new OperationHistory());
        TestClient alice = new TestClient("r", "alice"), bob = new TestClient("r", "bob");
        join(alice);
        sendAndWait(alice, 0, "Hello World");
        join(bob);
        assertEquals("Hello World", bob.local.getContent(), "Bob vào phòng thấy Hello World");
        assertFalse(bob.queue.getLastSeenOperationId().isEmpty(), "Bob phải có điểm đã thấy (CHECKPOINT)");

        // Bob mất mạng và gõ hai thay đổi
        env.manager.handleDisconnect("r", bob);
        bob.queue.onConnectionLost();
        assertNull(bob.typeInsert(11, "!"), "Offline: không gửi, giữ lại");
        assertNull(bob.typeInsert(0, ">"), "Offline: thay đổi thứ hai cũng giữ lại");
        assertEquals(2, bob.queue.getPendingCount(), "Hàng đợi có 2 thay đổi");
        assertEquals(">Hello World!", bob.local.getContent(), "Bob vẫn gõ được khi offline");

        // Alice sửa trong lúc Bob offline
        sendAndWait(alice, 6, "Big ");
        sendAndWait(alice, 0, "<<");
        assertEquals("<<Hello Big World", alice.local.getContent());

        // Bob nối lại
        resync(bob);
        String expected = "<<>Hello Big World!";
        assertEquals(expected, env.manager.getDocumentContent("r"), "Văn bản trên server");
        assertTrue(await(() -> expected.equals(bob.local.getContent())), "Bob thấy cùng văn bản với server");
        assertTrue(await(() -> expected.equals(alice.local.getContent())), "Alice thấy cùng văn bản với server");
        assertEquals(0, bob.queue.getPendingCount(), "Hàng đợi của Bob rỗng");
        assertFalse(bob.queue.isResyncing(), "Đồng bộ lại đã xong");
    }

    @Test
    void lostAckDoesNotDuplicateEdit() {
        env = new Env();
        TestClient bob = new TestClient("r2", "bob");
        join(bob);
        bob.dropAcks = true;
        env.manager.handleMessage(bob, bob.typeInsert(0, "abc"));
        assertTrue(await(() -> "abc".equals(env.manager.getDocumentContent("r2"))), "Server đã áp dụng abc");
        assertEquals(1, bob.queue.getPendingCount(), "Chưa nhận ACK nên còn 1 thay đổi chờ");

        bob.queue.onConnectionLost();
        bob.dropAcks = false;
        resync(bob);
        assertEquals("abc", env.manager.getDocumentContent("r2"), "Không được thành abcabc");
        assertTrue(await(() -> bob.queue.getPendingCount() == 0), "Thay đổi được ACK lại");
    }

    @Test
    void unknownRoomKeepsLocalEdits() {
        env = new Env();
        TestClient bob = new TestClient("ghost", "bob");
        bob.queue.onConnectionLost();
        bob.typeInsert(0, "local text");
        resync(bob);
        assertTrue(bob.received.stream().anyMatch(
                m -> m.getMessageType() == MessageType.ERROR && "ROOM_UNKNOWN".equals(m.getPayload())),
                "Phải nhận lỗi ROOM_UNKNOWN");
        assertEquals(1, bob.queue.getPendingCount(), "Thay đổi vẫn còn trong hàng đợi");
        assertEquals("local text", bob.local.getContent(), "Văn bản local không bị mất");
        assertFalse(bob.queue.isOnline() || bob.queue.isResyncing(), "Hàng đợi quay lại trạng thái offline");
    }

    @Test
    void trimmedHistoryFallsBackWithoutLosingEdits() {
        env = new Env(new OperationHistory(3));
        TestClient alice = new TestClient("r4", "alice"), bob = new TestClient("r4", "bob");
        join(alice);
        sendAndWait(alice, 0, "a");
        join(bob);
        env.manager.handleDisconnect("r4", bob);
        bob.queue.onConnectionLost();
        bob.typeInsert(1, "B");
        for (int i = 0; i < 6; i++) sendAndWait(alice, 0, "x");   // vượt quá giới hạn 3 thao tác
        resync(bob);
        String server = env.manager.getDocumentContent("r4");
        assertTrue(server.contains("B"), "Chữ B của Bob phải được áp dụng: " + server);
        assertEquals(6, server.chars().filter(c -> c == 'x').count(), "Không mất chữ x nào của Alice");
        assertTrue(await(() -> server.equals(bob.local.getContent())), "Bob thấy cùng văn bản với server");
        assertEquals(0, bob.queue.getPendingCount(), "Hàng đợi rỗng");
    }

    @Test
    void messagesToOneClientArriveInOrder() {
        env = new Env();
        final List<Integer> got = new CopyOnWriteArrayList<>();
        ClientSession slowSession = new ClientSession() {
            @Override public String getUserId() { return "u"; }
            @Override public boolean isOpen() { return true; }
            @Override public void send(SyncMessage m) {
                try { Thread.sleep(0, 200_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                got.add(Integer.parseInt(m.getPayload()));
            }
        };
        env.broadcaster.register("r5", slowSession);
        for (int i = 0; i < 300; i++) {
            env.broadcaster.broadcastToAll("r5", new SyncMessage(MessageType.TEXT_EDIT, "r5", "x", "x", String.valueOf(i)));
        }
        assertTrue(await(() -> got.size() == 300), "Phải nhận đủ 300 thông điệp, thực tế: " + got.size());
        for (int i = 0; i < 300; i++) {
            assertEquals(i, got.get(i).intValue(), "Thông điệp số " + i + " đến sai thứ tự");
        }
    }
}

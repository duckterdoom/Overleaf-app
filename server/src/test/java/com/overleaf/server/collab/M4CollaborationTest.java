package com.overleaf.server.collab;

import com.overleaf.common.model.CRDTOperation;
import com.overleaf.common.protocol.MessageType;
import com.overleaf.common.sync.OperationCodec;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.server.collab.M4TestSupport.Env;
import com.overleaf.server.collab.M4TestSupport.TestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.overleaf.server.collab.M4TestSupport.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kiểm tra bộ máy cộng tác M4: vào/rời phòng, sửa văn bản, xử lý sửa cùng lúc, nhiều luồng. */
class M4CollaborationTest {

    private Env env;

    @AfterEach
    void tearDown() {
        if (env != null) env.close();
    }

    private void join(TestClient c) {
        env.manager.handleMessage(c, SyncMessage.createJoinRoom(c.room, c.id, c.id));
        assertTrue(await(() -> c.received(MessageType.CHECKPOINT)), c.id + " phải nhận CHECKPOINT sau khi vào phòng");
    }

    private void edit(TestClient c, int pos, String text, long lamport) {
        CRDTOperation op = CRDTOperation.createInsert(pos, text, c.id, lamport);
        env.manager.handleMessage(c, SyncMessage.createTextEdit(c.room, c.id, c.id, OperationCodec.toJson(op)));
    }

    @Test
    void joiningUsersReceiveFullSync() {
        env = new Env();
        TestClient alice = new TestClient("r1", "alice"), bob = new TestClient("r1", "bob");
        join(alice);
        join(bob);
        assertTrue(alice.received(MessageType.FULL_SYNC), "Alice phải nhận FULL_SYNC");
        assertTrue(bob.received(MessageType.FULL_SYNC), "Bob phải nhận FULL_SYNC");
        assertEquals(2, env.manager.getCollaboratorCount("r1"), "Phòng có 2 người");
    }

    @Test
    void operationJsonRoundTripKeepsSpecialCharacters() {
        CRDTOperation original = CRDTOperation.createInsert(0, "a\"b\\c\nd,{}é", "alice", 1);
        CRDTOperation copy = OperationCodec.fromJson(OperationCodec.toJson(original));
        assertEquals(original.getContent(), copy.getContent(), "Nội dung phải giữ nguyên");
        assertEquals(original.getOperationId(), copy.getOperationId(), "Mã thao tác phải giữ nguyên");
        assertEquals(original.getPosition(), copy.getPosition(), "Vị trí phải giữ nguyên");
    }

    @Test
    void insertIsAppliedAcknowledgedAndBroadcast() {
        env = new Env();
        TestClient alice = new TestClient("r2", "alice"), bob = new TestClient("r2", "bob");
        join(alice);
        join(bob);
        edit(alice, 0, "Hello", 1);
        assertTrue(await(() -> "Hello".equals(env.manager.getDocumentContent("r2"))), "Văn bản phải là Hello");
        assertTrue(await(() -> alice.received(MessageType.ACK)), "Alice phải nhận ACK");
        assertTrue(await(() -> bob.received(MessageType.TEXT_EDIT)), "Bob phải nhận thay đổi");
    }

    @Test
    void concurrentInsertsConvergeToSameText() {
        env = new Env();
        TestClient alice = new TestClient("r3", "alice"), bob = new TestClient("r3", "bob");
        join(alice);
        join(bob);
        edit(alice, 0, "Hello", 1);
        assertTrue(await(() -> "Hello".equals(env.manager.getDocumentContent("r3"))));
        // Hai người cùng thấy "Hello" rồi sửa cùng lúc (cùng đồng hồ Lamport = 2)
        edit(alice, 2, "X", 2);
        assertTrue(await(() -> "HeXllo".equals(env.manager.getDocumentContent("r3"))));
        edit(bob, 4, "Y", 2);
        assertTrue(await(() -> "HeXllYo".equals(env.manager.getDocumentContent("r3"))),
                "Kết quả phải là HeXllYo, thực tế: " + env.manager.getDocumentContent("r3"));
    }

    @Test
    void manyThreadsDoNotLoseEdits() throws Exception {
        env = new Env();
        TestClient alice = new TestClient("r4", "alice"), bob = new TestClient("r4", "bob");
        join(alice);
        join(bob);
        final int total = 200;
        Thread[] threads = new Thread[total];
        for (int i = 0; i < total; i++) {
            final TestClient c = i % 2 == 0 ? alice : bob;
            final long ts = 1000 + i;
            threads[i] = new Thread(() -> edit(c, 0, "z", ts));
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        assertTrue(await(() -> env.manager.getDocumentContent("r4").length() == total),
                "Phải có đủ " + total + " ký tự, thực tế: " + env.manager.getDocumentContent("r4").length());
    }

    @Test
    void leavingRoomRemovesCollaborator() {
        env = new Env();
        TestClient alice = new TestClient("r5", "alice"), bob = new TestClient("r5", "bob");
        join(alice);
        join(bob);
        env.manager.handleMessage(bob, SyncMessage.createLeaveRoom("r5", "bob", "Bob"));
        assertTrue(await(() -> env.manager.getCollaboratorCount("r5") == 1), "Chỉ còn 1 người");
    }
}

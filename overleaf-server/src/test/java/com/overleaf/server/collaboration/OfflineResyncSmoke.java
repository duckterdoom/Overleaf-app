package com.overleaf.server.collaboration;

import com.overleaf.common.sync.*;
import com.overleaf.server.collaboration.SyncBroadcaster.ClientSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kiểm tra chức năng "sửa offline rồi tự đồng bộ lại" (M4 - phần A).
 * Chạy trong một tiến trình, không cần mạng thật: mỗi TestClient giả lập một
 * máy khách có văn bản local và một Offline Operation Queue.
 *
 * Chạy: Run 'OfflineResyncSmoke.main()' trong IntelliJ. Cuối cùng phải in "ALL PASSED".
 */
public class OfflineResyncSmoke {

    /** Máy khách giả: nhận thông điệp, cập nhật văn bản local và hàng đợi. */
    static class TestClient implements ClientSession {
        final String id;
        final DocumentState local = new DocumentState("local", "");
        final OfflineOperationQueue queue;
        final List<SyncMessage> received = new CopyOnWriteArrayList<>();
        final AtomicLong clock = new AtomicLong(0);
        volatile boolean open = true;
        volatile boolean dropAcks = false;
        final String room;

        TestClient(String room, String id) {
            this.room = room;
            this.id = id;
            this.queue = new OfflineOperationQueue(room, id, id);
        }

        public String getUserId() { return id; }
        public boolean isOpen() { return open; }

        public synchronized void send(SyncMessage m) {
            received.add(m);
            if (dropAcks && m.getMessageType() == MessageType.ACK) return;
            queue.handleServerMessage(m);
            switch (m.getMessageType()) {
                case FULL_SYNC:
                    local.replaceAll(m.getPayload());
                    break;
                case TEXT_EDIT: {
                    CRDTOperation op = OperationCodec.fromJson(m.getPayload());
                    clock.accumulateAndGet(op.getLamportTimestamp(), Math::max);
                    int len = local.getContentLength();
                    int pos = Math.min(op.getPosition(), len);
                    if (op.isInsert()) local.applyInsert(pos, op.getContent());
                    else local.applyDelete(pos, Math.min(op.getContentLength(), len - pos));
                    break;
                }
                default:
            }
        }

        /** Người dùng gõ chèn một đoạn; trả về thông điệp cần gửi (null nếu offline). */
        SyncMessage typeInsert(int pos, String text) {
            CRDTOperation op = CRDTOperation.createInsert(pos, text, id, clock.incrementAndGet());
            local.applyInsert(pos, text);
            return queue.prepareEdit(op);
        }
    }

    static int failures = 0;
    static void check(boolean ok, String name) {
        System.out.println((ok ? "OK:   " : "FAIL: ") + name);
        if (!ok) failures++;
    }
    static void pause() throws InterruptedException { Thread.sleep(300); }

    static class Env {
        final CollaborationThreadPool pool = new CollaborationThreadPool();
        final SyncBroadcaster bc = new SyncBroadcaster(pool);
        final DocumentSyncManager mgr;
        Env(OperationHistory h) { mgr = new DocumentSyncManager(pool, bc, h); }
    }

    public static void main(String[] args) throws Exception {
        scenarioOfflineMerge();
        scenarioLostAck();
        scenarioUnknownRoom();
        scenarioTrimmedHistory();
        scenarioOrdering();
        System.out.println(failures == 0 ? "ALL PASSED" : ("FAILURES: " + failures));
        System.exit(failures == 0 ? 0 : 1);
    }

    /** 1) Bob sửa offline, Alice sửa online; Bob nối lại → mọi máy giống nhau, không mất chữ nào. */
    static void scenarioOfflineMerge() throws Exception {
        System.out.println("--- 1. Offline rồi ghép với sửa của người khác");
        Env e = new Env(new OperationHistory());
        TestClient alice = new TestClient("r", "alice"), bob = new TestClient("r", "bob");
        e.mgr.handleMessage(alice, SyncMessage.createJoinRoom("r", "alice", "Alice")); pause();
        e.mgr.handleMessage(alice, alice.typeInsert(0, "Hello World")); pause();
        e.mgr.handleMessage(bob, SyncMessage.createJoinRoom("r", "bob", "Bob")); pause();
        check("Hello World".equals(bob.local.getContent()), "Bob vào phòng thấy 'Hello World'");
        check(!bob.queue.getLastSeenOperationId().isEmpty(), "Bob có điểm đã thấy (CHECKPOINT)");

        // Bob mất mạng
        e.mgr.handleDisconnect("r", bob);
        bob.open = false;
        bob.queue.onConnectionLost();

        check(bob.typeInsert(11, "!") == null, "Bob offline: thao tác được giữ lại, không gửi");
        check(bob.typeInsert(0, ">") == null, "Bob offline: thao tác thứ hai cũng giữ lại");
        check(bob.queue.getPendingCount() == 2, "Hàng đợi có 2 thao tác chưa xác nhận");
        check(">Hello World!".equals(bob.local.getContent()), "Bob vẫn gõ được khi offline");

        // Alice sửa trong lúc Bob offline
        e.mgr.handleMessage(alice, alice.typeInsert(6, "Big ")); pause();
        e.mgr.handleMessage(alice, alice.typeInsert(0, "<<")); pause();
        check("<<Hello Big World".equals(alice.local.getContent()), "Alice sửa bình thường: <<Hello Big World");

        // Bob nối lại bằng phiên mới
        bob.open = true;
        bob.queue.onReconnected();            // đặt trạng thái online + resyncing
        SyncMessage resync = new SyncMessage(MessageType.RESYNC, "r", "bob", "bob",
                OperationCodec.encodeResync(bob.queue.getLastSeenOperationId(), bob.queue.getPendingOperations()));
        check(bob.queue.isResyncing(), "Hàng đợi báo đang đồng bộ lại");
        e.mgr.handleMessage(bob, resync); pause();

        String server = e.mgr.getDocumentContent("r");
        System.out.println("      server = " + server);
        check("<<>Hello Big World!".equals(server), "Server ghép đúng: <<>Hello Big World!");
        check(server.equals(bob.local.getContent()), "Bob thấy cùng văn bản với server");
        check(server.equals(alice.local.getContent()), "Alice thấy cùng văn bản với server");
        check(bob.queue.getPendingCount() == 0, "Hàng đợi của Bob đã rỗng (đều được ACK)");
        check(!bob.queue.isResyncing(), "Đồng bộ lại xong (đã nhận CHECKPOINT)");
        e.pool.shutdown();
    }

    /** 2) ACK bị mất: Bob gửi lại cùng thao tác → server không áp dụng hai lần. */
    static void scenarioLostAck() throws Exception {
        System.out.println("--- 2. ACK bị mất, gửi lại không bị trùng");
        Env e = new Env(new OperationHistory());
        TestClient bob = new TestClient("r2", "bob");
        e.mgr.handleMessage(bob, SyncMessage.createJoinRoom("r2", "bob", "Bob")); pause();
        bob.dropAcks = true;
        e.mgr.handleMessage(bob, bob.typeInsert(0, "abc")); pause();
        check("abc".equals(e.mgr.getDocumentContent("r2")), "Server đã áp dụng 'abc'");
        check(bob.queue.getPendingCount() == 1, "Bob chưa nhận ACK nên còn 1 thao tác chờ");

        bob.queue.onConnectionLost();
        bob.dropAcks = false;
        e.mgr.handleMessage(bob, bob.queue.onReconnected()); pause();
        check("abc".equals(e.mgr.getDocumentContent("r2")), "Sau đồng bộ lại vẫn là 'abc' (không thành 'abcabc')");
        check(bob.queue.getPendingCount() == 0, "Thao tác được ACK lại, hàng đợi rỗng");
        e.pool.shutdown();
    }

    /** 3) Phòng không còn: trả lỗi, client giữ nguyên thao tác. */
    static void scenarioUnknownRoom() throws Exception {
        System.out.println("--- 3. Phòng không tồn tại");
        Env e = new Env(new OperationHistory());
        TestClient bob = new TestClient("ghost", "bob");
        bob.queue.onConnectionLost();
        bob.typeInsert(0, "local text");
        e.mgr.handleMessage(bob, bob.queue.onReconnected()); pause();
        check(bob.received.stream().anyMatch(m -> m.getMessageType() == MessageType.ERROR
                && "ROOM_UNKNOWN".equals(m.getPayload())), "Nhận lỗi ROOM_UNKNOWN");
        check(bob.queue.getPendingCount() == 1, "Thao tác vẫn được giữ trong hàng đợi");
        check("local text".equals(bob.local.getContent()), "Văn bản local không bị mất");
        check(!bob.queue.isOnline() && !bob.queue.isResyncing(), "Hàng đợi quay lại trạng thái offline");
        e.pool.shutdown();
    }

    /** 4) Lịch sử bị cắt bớt: vẫn đồng bộ được bằng cách dự phòng, không mất thao tác. */
    static void scenarioTrimmedHistory() throws Exception {
        System.out.println("--- 4. Lịch sử bị xóa bớt (cách dự phòng)");
        Env e = new Env(new OperationHistory(3));
        TestClient alice = new TestClient("r4", "alice"), bob = new TestClient("r4", "bob");
        e.mgr.handleMessage(alice, SyncMessage.createJoinRoom("r4", "alice", "Alice")); pause();
        e.mgr.handleMessage(alice, alice.typeInsert(0, "a")); pause();
        e.mgr.handleMessage(bob, SyncMessage.createJoinRoom("r4", "bob", "Bob")); pause();
        e.mgr.handleDisconnect("r4", bob);
        bob.queue.onConnectionLost();
        bob.typeInsert(1, "B");
        for (int i = 0; i < 6; i++) {   // vượt quá giới hạn 3 thao tác
            e.mgr.handleMessage(alice, alice.typeInsert(0, "x")); pause();
        }
        e.mgr.handleMessage(bob, bob.queue.onReconnected()); pause();
        String server = e.mgr.getDocumentContent("r4");
        System.out.println("      server = " + server);
        check(server.contains("B"), "Chữ B của Bob được áp dụng");
        check(server.chars().filter(c -> c == 'x').count() == 6, "Không mất chữ x nào của Alice");
        check(server.equals(bob.local.getContent()), "Bob thấy cùng văn bản với server");
        check(bob.queue.getPendingCount() == 0, "Hàng đợi rỗng");
        e.pool.shutdown();
    }

    /** 5) Thứ tự gửi: 300 thông điệp liên tiếp phải đến đúng thứ tự. */
    static void scenarioOrdering() throws Exception {
        System.out.println("--- 5. Thứ tự gửi thông điệp");
        Env e = new Env(new OperationHistory());
        final List<Integer> got = new CopyOnWriteArrayList<>();
        ClientSession s = new ClientSession() {
            public String getUserId() { return "u"; }
            public boolean isOpen() { return true; }
            public void send(SyncMessage m) {
                try { Thread.sleep(0, 200_000); } catch (InterruptedException ignored) {}
                got.add(Integer.parseInt(m.getPayload()));
            }
        };
        e.bc.register("r5", s);
        for (int i = 0; i < 300; i++) {
            e.bc.broadcastToAll("r5", new SyncMessage(MessageType.TEXT_EDIT, "r5", "x", "x", String.valueOf(i)));
        }
        Thread.sleep(1500);
        boolean ordered = got.size() == 300;
        for (int i = 0; ordered && i < got.size(); i++) ordered = got.get(i) == i;
        check(ordered, "300 thông điệp đến đủ và đúng thứ tự (nhận " + got.size() + ")");
        e.pool.shutdown();
    }
}

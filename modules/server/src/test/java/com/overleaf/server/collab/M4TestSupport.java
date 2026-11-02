package com.overleaf.server.collab;

import com.overleaf.common.model.CRDTOperation;
import com.overleaf.common.model.DocumentState;
import com.overleaf.common.protocol.MessageType;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.common.sync.OfflineOperationQueue;
import com.overleaf.common.sync.OperationCodec;
import com.overleaf.server.collab.SyncBroadcaster.ClientSession;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** Các lớp hỗ trợ dùng chung cho test của M4 (không phải test). */
final class M4TestSupport {

    private M4TestSupport() {}

    /** Môi trường M4 đầy đủ trong một tiến trình, dùng cho mỗi test. */
    static final class Env implements AutoCloseable {
        final CollaborationThreadPool pool = new CollaborationThreadPool();
        final SyncBroadcaster broadcaster = new SyncBroadcaster(pool);
        final DocumentSyncManager manager;

        Env() {
            this(new OperationHistory());
        }

        Env(OperationHistory history) {
            manager = new DocumentSyncManager(pool, broadcaster, history);
        }

        @Override
        public void close() {
            pool.shutdown();
        }
    }

    /** Máy khách giả: ghi lại thông điệp nhận được, giữ văn bản local và hàng đợi offline. */
    static final class TestClient implements ClientSession {
        final String id;
        final String room;
        final DocumentState local = new DocumentState("local", "");
        final OfflineOperationQueue queue;
        final List<SyncMessage> received = new CopyOnWriteArrayList<>();
        final AtomicLong clock = new AtomicLong(0);
        volatile boolean dropAcks = false;

        TestClient(String room, String id) {
            this.room = room;
            this.id = id;
            this.queue = new OfflineOperationQueue(room, id, id);
        }

        @Override
        public String getUserId() {
            return id;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public synchronized void send(SyncMessage m) {
            received.add(m);
            if (dropAcks && m.getMessageType() == MessageType.ACK) return;
            clock.accumulateAndGet(m.getLamportTimestamp(), Math::max);
            queue.handleServerMessage(m);
            switch (m.getMessageType()) {
                case FULL_SYNC:
                    local.replaceAll(m.getPayload());
                    break;
                case TEXT_EDIT:
                    {
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

        /** Người dùng chèn một đoạn tại pos; trả về thông điệp cần gửi (null nếu đang offline). */
        SyncMessage typeInsert(int pos, String text) {
            CRDTOperation op = CRDTOperation.createInsert(pos, text, id, clock.incrementAndGet());
            local.applyInsert(pos, text);
            return queue.prepareEdit(op);
        }

        boolean received(MessageType type) {
            return received.stream().anyMatch(m -> m.getMessageType() == type);
        }
    }

    /** Chờ tối đa 5 giây cho đến khi điều kiện đúng (thay cho Thread.sleep cố định). */
    static boolean await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}

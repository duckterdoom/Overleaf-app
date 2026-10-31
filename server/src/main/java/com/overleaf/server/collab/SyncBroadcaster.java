package com.overleaf.server.collab;

import com.overleaf.common.protocol.SyncMessage;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * =========================================================================
 * M4: PHÁT SÓNG ĐỒNG BỘ (Sync Broadcaster)
 * =========================================================================
 *
 * Phát {@link SyncMessage} đến tất cả client trong một phòng (có thể loại trừ
 * người gửi). Việc gửi được đẩy sang broadcast pool của
 * {@link CollaborationThreadPool} nên không chặn luồng xử lý chỉnh sửa.
 *
 * Ánh xạ phòng → danh sách client dùng ConcurrentHashMap + tập đồng thời,
 * an toàn khi nhiều luồng cùng đăng ký / hủy đăng ký / phát sóng.
 *
 * LƯU Ý TÍCH HỢP: M3 (TCP Networking) cài đặt {@link ClientSession} cho từng
 * socket (M3 ghi frame qua MessageCodec) rồi đăng ký qua {@link #register}.
 */
public class SyncBroadcaster {

    /**
     * Giao diện trừu tượng của một kết nối client. M3 cài đặt giao diện này
     * trên socket thật; M4 chỉ phụ thuộc vào giao diện, không phụ thuộc socket.
     */
    public interface ClientSession {
        /** ID người dùng của phiên này. */
        String getUserId();

        /** Gửi một thông điệp đến client. Có thể ném RuntimeException nếu lỗi. */
        void send(SyncMessage message);

        /** Phiên còn mở hay không. */
        boolean isOpen();

        /** Transport may close slow clients; default keeps existing in-memory M4 adapters compatible. */
        default void disconnect() { }
    }

    private final ConcurrentHashMap<String, Set<ClientSession>> roomSessions =
            new ConcurrentHashMap<>();

    private final CollaborationThreadPool threadPool;
    private final int outboxCapacity;

    /**
     * Hộp thư đi của từng client. Mỗi client chỉ có MỘT luồng đang gửi tại một
     * thời điểm nên thông điệp luôn đến đúng thứ tự đã đưa vào (nếu để broadcast
     * pool tự chia luồng thì hai thông điệp liên tiếp có thể đến đảo thứ tự).
     */
    private static final class Outbox {
        final BlockingQueue<SyncMessage> queue;
        final AtomicBoolean draining = new AtomicBoolean(false);
        Outbox(int capacity) { queue = new ArrayBlockingQueue<>(capacity); }
    }

    private final ConcurrentHashMap<ClientSession, Outbox> outboxes = new ConcurrentHashMap<>();

    public SyncBroadcaster(CollaborationThreadPool threadPool) {
        this(threadPool, 1024);
    }

    public SyncBroadcaster(CollaborationThreadPool threadPool, int outboxCapacity) {
        if (outboxCapacity < 1) throw new IllegalArgumentException("Invalid outbox capacity");
        this.threadPool = threadPool;
        this.outboxCapacity = outboxCapacity;
    }

    // =========================================================================
    // ĐĂNG KÝ / HỦY ĐĂNG KÝ
    // =========================================================================

    /** Thêm client vào phòng. */
    public void register(String roomId, ClientSession session) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    /** Xóa client khỏi phòng; xóa luôn phòng nếu không còn ai. */
    public void unregister(String roomId, ClientSession session) {
        outboxes.remove(session);
        Set<ClientSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;
        sessions.remove(session);
        if (sessions.isEmpty()) {
            roomSessions.remove(roomId, sessions);
        }
    }

    /** Xóa mọi phiên của một userId trong phòng. */
    public void unregisterUser(String roomId, String userId) {
        Set<ClientSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;
        sessions.removeIf(s -> {
            if (s.getUserId().equals(userId)) {
                outboxes.remove(s);
                return true;
            }
            return false;
        });
        if (sessions.isEmpty()) {
            roomSessions.remove(roomId, sessions);
        }
    }

    // =========================================================================
    // PHÁT SÓNG
    // =========================================================================

    /**
     * Phát thông điệp đến tất cả client trong phòng, trừ người gửi
     * (excludeUserId = null nghĩa là gửi cho tất cả).
     */
    public void broadcast(String roomId, SyncMessage message, String excludeUserId) {
        for (ClientSession session : getSessions(roomId)) {
            if (excludeUserId != null && excludeUserId.equals(session.getUserId())) {
                continue;
            }
            dispatch(roomId, session, message);
        }
    }

    /** Phát đến tất cả client trong phòng (kể cả người gửi). */
    public void broadcastToAll(String roomId, SyncMessage message) {
        broadcast(roomId, message, null);
    }

    /** Gửi thông điệp riêng cho một người dùng trong phòng. */
    public void sendTo(String roomId, String userId, SyncMessage message) {
        for (ClientSession session : getSessions(roomId)) {
            if (session.getUserId().equals(userId)) {
                dispatch(roomId, session, message);
            }
        }
    }

    // =========================================================================
    // TRUY VẤN
    // =========================================================================

    public int getSessionCount(String roomId) {
        return getSessions(roomId).size();
    }

    public Set<String> getActiveRoomIds() {
        return Collections.unmodifiableSet(roomSessions.keySet());
    }

    // =========================================================================
    // NỘI BỘ
    // =========================================================================

    private Set<ClientSession> getSessions(String roomId) {
        Set<ClientSession> sessions = roomSessions.get(roomId);
        return sessions != null ? sessions : Collections.emptySet();
    }

    /** Đưa thông điệp vào hộp thư đi của client; gửi không chặn qua broadcast pool. */
    private void dispatch(String roomId, ClientSession session, SyncMessage message) {
        if (!session.isOpen()) return;
        Outbox outbox = outboxes.computeIfAbsent(session, k -> new Outbox(outboxCapacity));
        if (!outbox.queue.offer(message)) {
            unregister(roomId, session);
            session.disconnect();
            return;
        }
        scheduleDrain(roomId, session, outbox);
    }

    private void scheduleDrain(String roomId, ClientSession session, Outbox outbox) {
        if (!outbox.draining.compareAndSet(false, true)) {
            return; // đã có một luồng đang gửi, nó sẽ lấy luôn thông điệp mới
        }
        threadPool.submitBroadcastTask(() -> {
            try {
                SyncMessage next;
                while ((next = outbox.queue.poll()) != null) {
                    if (!session.isOpen()) {
                        outbox.queue.clear();
                        unregister(roomId, session);
                        return;
                    }
                    session.send(next);
                }
            } catch (RuntimeException e) {
                System.err.println("[Broadcaster] Gửi thất bại tới "
                        + session.getUserId() + " (phòng " + roomId + "): " + e.getMessage());
                outbox.queue.clear();
                unregister(roomId, session);
            } finally {
                outbox.draining.set(false);
                // Có thông điệp vào đúng lúc vừa thoát vòng lặp → gửi tiếp
                if (!outbox.queue.isEmpty()) {
                    scheduleDrain(roomId, session, outbox);
                }
            }
        });
    }
}

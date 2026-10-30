package com.overleaf.server.collaboration;

import com.overleaf.common.sync.SyncMessage;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * socket (ví dụ ghi ra ObjectOutputStream) rồi đăng ký qua {@link #register}.
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
    }

    private final ConcurrentHashMap<String, Set<ClientSession>> roomSessions =
            new ConcurrentHashMap<>();

    private final CollaborationThreadPool threadPool;

    public SyncBroadcaster(CollaborationThreadPool threadPool) {
        this.threadPool = threadPool;
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
        sessions.removeIf(s -> s.getUserId().equals(userId));
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

    /** Gửi không chặn qua broadcast pool; lỗi gửi → loại phiên khỏi phòng. */
    private void dispatch(String roomId, ClientSession session, SyncMessage message) {
        threadPool.submitBroadcastTask(() -> {
            try {
                if (!session.isOpen()) {
                    unregister(roomId, session);
                    return;
                }
                session.send(message);
            } catch (RuntimeException e) {
                System.err.println("[Broadcaster] Gửi thất bại tới "
                        + session.getUserId() + " (phòng " + roomId + "): " + e.getMessage());
                unregister(roomId, session);
            }
        });
    }
}

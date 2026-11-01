package com.overleaf.server.collab;

import com.overleaf.common.model.CRDTOperation;
import com.overleaf.common.model.CollaboratorInfo;
import com.overleaf.common.model.CursorPosition;
import com.overleaf.common.model.DocumentState;
import com.overleaf.common.protocol.MessageType;
import com.overleaf.common.sync.OperationCodec;
import com.overleaf.common.protocol.SyncMessage;
import com.overleaf.server.collab.SyncBroadcaster.ClientSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =========================================================================
 * M4: ĐIỀU PHỐI ĐỒNG BỘ TÀI LIỆU (Document Sync Manager)
 * =========================================================================
 *
 * Điểm vào duy nhất của M4 cho M3 (TCP Networking): M3 nhận SyncMessage từ
 * socket rồi gọi {@link #handleMessage}. Lớp này:
 *  - giữ trạng thái từng phòng trong ConcurrentHashMap (nhiều phòng song song);
 *  - xử lý JOIN / LEAVE / TEXT_EDIT / CURSOR_UPDATE / HEARTBEAT;
 *  - với TEXT_EDIT: biến đổi (OT) theo các thao tác đồng thời trong lịch sử,
 *    áp dụng qua CRDTEngine, lưu lịch sử, ACK người gửi, phát cho người khác;
 *  - chạy mọi việc trên syncPool; mỗi phòng có khóa riêng nên các thao tác
 *    trong CÙNG phòng được tuần tự hóa, còn các phòng khác nhau chạy song song.
 */
public class DocumentSyncManager {

    /** Trạng thái của một phòng cộng tác. */
    private static final class Room {
        final String roomId;
        final DocumentState document;
        final CRDTEngine engine;
        final Map<String, CollaboratorInfo> collaborators = new ConcurrentHashMap<>();
        final Map<String, CursorPosition> cursors = new ConcurrentHashMap<>();

        Room(String roomId, String initialContent) {
            this.roomId = roomId;
            this.document = new DocumentState(roomId, initialContent);
            this.engine = new CRDTEngine(document);
        }
    }

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final CollaborationThreadPool threadPool;
    private final SyncBroadcaster broadcaster;
    private final OperationHistory history;

    public DocumentSyncManager(CollaborationThreadPool threadPool,
                               SyncBroadcaster broadcaster,
                               OperationHistory history) {
        this.threadPool = threadPool;
        this.broadcaster = broadcaster;
        this.history = history;
    }

    // =========================================================================
    // ĐIỀU PHỐI THÔNG ĐIỆP (không chặn luồng gọi)
    // =========================================================================

    /**
     * Nhận một thông điệp từ client. Việc xử lý chạy bất đồng bộ trên sync pool.
     *
     * @param session phiên gửi (dùng để đăng ký vào broadcaster khi JOIN)
     */
    public void handleMessage(ClientSession session, SyncMessage message) {
        threadPool.submitSyncTask(() -> processMessage(session, message));
    }

    /** M3's dedicated socket reader uses this entry point to preserve JOIN/edit/LEAVE order. */
    public void processMessage(ClientSession session, SyncMessage message) {
        try {
            switch (message.getMessageType()) {
                case JOIN_ROOM:     handleJoinRoom(session, message);   break;
                case LEAVE_ROOM:    handleLeaveRoom(session, message);  break;
                case RESYNC:        handleResync(session, message);     break;
                case TEXT_EDIT:     handleEditMessage(message);         break;
                case CURSOR_UPDATE: handleCursorUpdate(message);        break;
                case HEARTBEAT:     handleHeartbeat(message);           break;
                default: /* ACK, FULL_SYNC... là thông điệp server → client */ break;
            }
        } catch (RuntimeException e) {
            System.err.println("[SyncManager] Lỗi xử lý " + message.getMessageType()
                    + ": " + e.getMessage());
            broadcaster.sendTo(message.getRoomId(), message.getSenderId(),
                    SyncMessage.createError(message.getRoomId(), "Lỗi xử lý: " + e.getMessage()));
        }
    }

    // =========================================================================
    // JOIN
    // =========================================================================

    /** Thêm người dùng vào phòng và gửi toàn bộ nội dung hiện tại cho họ. */
    public void handleJoinRoom(ClientSession session, SyncMessage message) {
        String roomId = message.getRoomId();
        Room room = rooms.computeIfAbsent(roomId, id -> new Room(id, ""));

        synchronized (room) {
            CollaboratorInfo info = registerCollaborator(room, session, message);

            // Gửi trạng thái đầy đủ cho người mới (đọc trong khóa → nhất quán với op kế tiếp),
            // kèm CHECKPOINT để client biết mình đã thấy đến thao tác nào.
            broadcaster.sendTo(roomId, info.getUserId(),
                    SyncMessage.createFullSync(roomId, room.document.getContent()).withLamportTimestamp(room.engine.getCurrentLamportTime()));
            broadcaster.sendTo(roomId, info.getUserId(), checkpoint(roomId));
        }
        broadcastUserList(room);
    }

    /**
     * Thêm người dùng vào phòng (người đầu tiên là HOST, sau đó là EDITOR) và
     * đăng ký phiên để nhận phát sóng. Gọi trong khóa của phòng.
     */
    private CollaboratorInfo registerCollaborator(Room room, ClientSession session, SyncMessage message) {
        CollaboratorInfo info = room.collaborators.get(message.getSenderId());
        if (info == null) {
            info = room.collaborators.isEmpty()
                    ? CollaboratorInfo.createHost(message.getSenderId(), message.getSenderName())
                    : CollaboratorInfo.createEditor(message.getSenderId(), message.getSenderName());
            room.collaborators.put(info.getUserId(), info);
        }
        // Bỏ phiên cũ (nếu còn sót lại sau khi mất mạng) rồi đăng ký phiên mới
        broadcaster.unregisterUser(room.roomId, info.getUserId());
        broadcaster.register(room.roomId, session);
        return info;
    }

    private SyncMessage checkpoint(String roomId) {
        return new SyncMessage(MessageType.CHECKPOINT, roomId, "SERVER", "Server",
                history.getLastOperationId(roomId)).withLamportTimestamp(rooms.get(roomId).engine.getCurrentLamportTime());
    }

    // =========================================================================
    // LEAVE
    // =========================================================================

    /** Xóa người dùng khỏi phòng và thông báo cho những người còn lại. */
    public void handleLeaveRoom(ClientSession session, SyncMessage message) {
        removeUser(message.getRoomId(), message.getSenderId(), session);
    }

    /** M3 gọi khi socket bị ngắt đột ngột. */
    public void handleDisconnect(String roomId, ClientSession session) {
        removeUser(roomId, session.getUserId(), session);
    }

    private void removeUser(String roomId, String userId, ClientSession session) {
        Room room = rooms.get(roomId);
        if (room == null) return;
        synchronized (room) {
            room.collaborators.remove(userId);
            room.cursors.remove(userId);
            if (session != null) {
                broadcaster.unregister(roomId, session);
            } else {
                broadcaster.unregisterUser(roomId, userId);
            }
        }
        broadcastUserList(room);
    }

    // =========================================================================
    // TEXT_EDIT
    // =========================================================================

    /**
     * Xử lý chỉnh sửa: giải mã → kiểm tra quyền → OT → áp dụng → lưu lịch sử
     * → ACK người gửi → phát cho người khác. Toàn bộ trong khóa của phòng.
     */
    public void handleEditMessage(SyncMessage message) {
        String roomId = message.getRoomId();
        String userId = message.getSenderId();
        Room room = rooms.get(roomId);
        if (room == null) {
            broadcaster.sendTo(roomId, userId,
                    SyncMessage.createError(roomId, "Phòng không tồn tại: " + roomId));
            return;
        }

        CRDTOperation incoming = operationFromJson(message.getPayload());

        synchronized (room) {
            CollaboratorInfo user = room.collaborators.get(userId);
            if (user == null || !user.canEdit()) {
                broadcaster.sendTo(roomId, userId,
                        SyncMessage.createError(roomId, "Không có quyền chỉnh sửa"));
                return;
            }
            if (history.contains(roomId, incoming.getOperationId())) {
                // Client gửi lại thao tác đã áp dụng (ACK bị mất): chỉ xác nhận lại
                broadcaster.sendTo(roomId, userId,
                        SyncMessage.createAck(roomId, incoming.getOperationId()).withLamportTimestamp(room.engine.getCurrentLamportTime()));
                return;
            }

            // OT: client tăng đồng hồ Lamport TRƯỚC khi tạo op, nên op nào của người khác
            // có timestamp >= timestamp của op này là op client CHƯA thấy (đồng thời).
            CRDTOperation transformed = incoming;
            for (CRDTOperation concurrent : history.getOperationsSince(roomId, incoming.getLamportTimestamp() - 1)) {
                if (!concurrent.getUserId().equals(userId)) {
                    transformed = room.engine.transformOperation(transformed, concurrent);
                }
            }
            applyAndPublish(room, user, message.getSenderName(), transformed, incoming);
        }
    }

    /**
     * Áp dụng thao tác (đã đổi sang hệ tọa độ của server) → lưu lịch sử → ACK
     * người gửi → phát cho người khác. Gọi trong khóa của phòng.
     *
     * @param op       thao tác đã được biến đổi
     * @param original thao tác gốc client gửi (dùng mã của nó để ACK)
     * @return true nếu áp dụng thành công
     */
    private boolean applyAndPublish(Room room, CollaboratorInfo user, String senderName,
                                    CRDTOperation op, CRDTOperation original) {
        return applyAndPublish(room, user, senderName, op, original, null);
    }

    private boolean applyAndPublish(Room room, CollaboratorInfo user, String senderName,
                                    CRDTOperation op, CRDTOperation original, List<String> batchAcks) {
        String roomId = room.roomId;
        long version = room.engine.applyOperation(op);
        if (version < 0) {
            broadcaster.sendTo(roomId, user.getUserId(),
                    SyncMessage.createError(roomId, "Không áp dụng được thao tác"));
            return false;
        }
        history.addOperation(roomId, op);
        user.markEditing();

        // ACK + phát trong khóa để giữ đúng thứ tự áp dụng giữa các client
        if (batchAcks != null) batchAcks.add(original.getOperationId());
        else broadcaster.sendTo(roomId, user.getUserId(),
                    SyncMessage.createAck(roomId, original.getOperationId()).withLamportTimestamp(room.engine.getCurrentLamportTime()));
        broadcaster.broadcast(roomId,
                SyncMessage.createTextEdit(roomId, user.getUserId(), senderName, operationToJson(op)).withLamportTimestamp(room.engine.getCurrentLamportTime()),
                user.getUserId());
        return true;
    }

    // =========================================================================
    // RESYNC (đồng bộ lại sau khi offline)
    // =========================================================================

    /**
     * Xử lý yêu cầu đồng bộ lại của client vừa nối lại mạng.
     *
     * Payload: mã thao tác cuối cùng client đã thấy + các thao tác chưa được xác nhận.
     *
     * Cách làm:
     *  1. Thao tác nào đã có trong lịch sử (đã áp dụng nhưng ACK bị mất) → chỉ ACK lại.
     *  2. "Phần còn thiếu" = các thao tác của người khác nằm sau điểm đã thấy.
     *     Từng thao tác offline được dịch vị trí qua phần còn thiếu (và ngược lại,
     *     để thao tác offline kế tiếp dịch đúng), rồi áp dụng như một thao tác thường.
     *  3. Nếu điểm đã thấy không còn trong lịch sử (bị xóa bớt) thì quay về cách
     *     dùng đồng hồ Lamport như TEXT_EDIT thường (kém chính xác hơn).
     *  4. Cuối cùng gửi FULL_SYNC (văn bản chuẩn đã gồm mọi thứ) và CHECKPOINT.
     *
     * Nếu phòng không còn (server khởi động lại, phòng đã dọn) → trả lỗi
     * ROOM_UNKNOWN và KHÔNG đụng đến dữ liệu; client vẫn giữ thao tác của mình.
     */
    public void handleResync(ClientSession session, SyncMessage message) {
        String roomId = message.getRoomId();
        String userId = message.getSenderId();
        Room room = rooms.get(roomId);
        if (room == null) {
            safeSend(session, SyncMessage.createError(roomId, "ROOM_UNKNOWN"));
            return;
        }

        OperationCodec.ResyncRequest request;
        try {
            request = OperationCodec.decodeResync(message.getPayload());
        } catch (RuntimeException e) {
            safeSend(session, SyncMessage.createError(roomId, "RESYNC_INVALID"));
            return;
        }

        synchronized (room) {
            CollaboratorInfo user = registerCollaborator(room, session, message);

            // Bước 1: bỏ các thao tác đã áp dụng trước đó
            List<String> batchAcks = request.getPending().size() > 64 ? new ArrayList<>() : null;
            List<CRDTOperation> pending = new ArrayList<>();
            for (CRDTOperation op : request.getPending()) {
                if (history.contains(roomId, op.getOperationId())) {
                    if (batchAcks != null) batchAcks.add(op.getOperationId());
                    else broadcaster.sendTo(roomId, userId, SyncMessage.createAck(roomId, op.getOperationId()));
                } else {
                    pending.add(op);
                }
            }

            if (!pending.isEmpty() && !user.canEdit()) {
                broadcaster.sendTo(roomId, userId,
                        SyncMessage.createError(roomId, "Không có quyền chỉnh sửa"));
            } else if (!pending.isEmpty()) {
                List<CRDTOperation> missed = findMissedOperations(roomId, request.getLastSeenOperationId(), userId);
                if (missed == null) {
                    // Bước 3: không xác định được phần còn thiếu → dùng đồng hồ Lamport
                    for (CRDTOperation op : pending) {
                        CRDTOperation transformed = op;
                        for (CRDTOperation concurrent : history.getOperationsSince(roomId, op.getLamportTimestamp() - 1)) {
                            if (!concurrent.getUserId().equals(userId)) {
                                transformed = room.engine.transformOperation(transformed, concurrent);
                            }
                        }
                        applyAndPublish(room, user, message.getSenderName(), transformed, op, batchAcks);
                    }
                } else {
                    // Bước 2: dịch vị trí lần lượt
                    for (CRDTOperation op : pending) {
                        CRDTOperation rebased = op;
                        List<CRDTOperation> nextMissed = new ArrayList<>(missed.size());
                        for (CRDTOperation m : missed) {
                            nextMissed.add(room.engine.transformOperation(m, rebased));
                            rebased = room.engine.transformOperation(rebased, m);
                        }
                        missed = nextMissed;
                        applyAndPublish(room, user, message.getSenderName(), rebased, op, batchAcks);
                    }
                }
            }

            // Bước 4: văn bản chuẩn + điểm đã thấy mới
            if (batchAcks != null) {
                for (int offset = 0; offset < batchAcks.size(); offset += 2048) {
                    broadcaster.sendTo(roomId, userId, new SyncMessage(MessageType.ACK_BATCH, roomId, "SERVER", "Server",
                            String.join("\n", batchAcks.subList(offset, Math.min(offset + 2048, batchAcks.size()))))
                            .withLamportTimestamp(room.engine.getCurrentLamportTime()));
                }
            }
            broadcaster.sendTo(roomId, userId,
                    SyncMessage.createFullSync(roomId, room.document.getContent()).withLamportTimestamp(room.engine.getCurrentLamportTime()));
            broadcaster.sendTo(roomId, userId, checkpoint(roomId));
        }
        broadcastUserList(room);
    }

    /**
     * Các thao tác của NGƯỜI KHÁC mà client chưa thấy, theo thứ tự server áp dụng.
     *
     * @return danh sách, hoặc null nếu không xác định được (lịch sử đã bị xóa bớt)
     */
    private List<CRDTOperation> findMissedOperations(String roomId, String lastSeen, String userId) {
        List<CRDTOperation> after;
        if (lastSeen == null || lastSeen.isEmpty()) {
            if (history.wasTrimmed(roomId)) return null;
            after = history.getFullHistory(roomId);
        } else {
            java.util.Optional<List<CRDTOperation>> found = history.getOperationsAfter(roomId, lastSeen);
            if (found.isEmpty()) return null;
            after = found.get();
        }
        List<CRDTOperation> others = new ArrayList<>();
        for (CRDTOperation op : after) {
            // Thao tác của chính client đã nằm trong bản local của nó nên không tính
            if (!op.getUserId().equals(userId)) others.add(op);
        }
        return others;
    }

    private void safeSend(ClientSession session, SyncMessage message) {
        try {
            session.send(message);
        } catch (RuntimeException e) {
            System.err.println("[SyncManager] Không gửi được tới " + session.getUserId() + ": " + e.getMessage());
        }
    }

    // =========================================================================
    // CURSOR / HEARTBEAT
    // =========================================================================

    /** Payload con trỏ: "line,column,fileName". Lưu rồi chuyển tiếp cho người khác. */
    private void handleCursorUpdate(SyncMessage message) {
        String roomId = message.getRoomId();
        Room room = rooms.get(roomId);
        if (room == null) return;

        String[] parts = message.getPayload().split(",", 3);
        try {
            int line = Integer.parseInt(parts[0].trim());
            int col = Integer.parseInt(parts[1].trim());
            String file = parts.length > 2 ? parts[2] : "";
            room.cursors.put(message.getSenderId(),
                    new CursorPosition(message.getSenderId(), message.getSenderName(), line, col, file));
        } catch (RuntimeException e) {
            return; // payload sai định dạng → bỏ qua
        }
        broadcaster.broadcast(roomId, message, message.getSenderId());
    }

    private void handleHeartbeat(SyncMessage message) {
        Room room = rooms.get(message.getRoomId());
        if (room == null) return;
        CollaboratorInfo info = room.collaborators.get(message.getSenderId());
        if (info != null) info.touch();
    }

    // =========================================================================
    // TÁC VỤ ĐỊNH KỲ
    // =========================================================================

    /** Bật heartbeat-timeout và dọn phòng trống bằng scheduler. */
    public void startMaintenance(long timeoutMs) {
        threadPool.scheduleAtFixedRate(() -> {
            for (Room room : rooms.values()) {
                boolean changed = false;
                synchronized (room) {
                    for (CollaboratorInfo c : new ArrayList<>(room.collaborators.values())) {
                        if (c.isTimedOut(timeoutMs)) {
                            room.collaborators.remove(c.getUserId());
                            room.cursors.remove(c.getUserId());
                            broadcaster.unregisterUser(room.roomId, c.getUserId());
                            changed = true;
                        }
                    }
                }
                if (changed) broadcastUserList(room);
            }
            rooms.entrySet().removeIf(e -> {
                Room r = e.getValue();
                synchronized (r) {
                    if (r.collaborators.isEmpty()) {
                        history.clearHistory(e.getKey());
                        return true;
                    }
                    return false;
                }
            });
        }, 10, 30);
    }

    // =========================================================================
    // TRUY VẤN
    // =========================================================================

    public String getDocumentContent(String roomId) {
        Room room = rooms.get(roomId);
        return room == null ? null : room.document.getContent();
    }

    public int getRoomCount() { return rooms.size(); }

    public int getCollaboratorCount(String roomId) {
        Room room = rooms.get(roomId);
        return room == null ? 0 : room.collaborators.size();
    }

    // =========================================================================
    // DANH SÁCH NGƯỜI DÙNG
    // =========================================================================

    /** Payload: mỗi dòng "userId|displayName|ROLE|STATUS". */
    private void broadcastUserList(Room room) {
        StringBuilder sb = new StringBuilder();
        for (CollaboratorInfo c : room.collaborators.values()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(c.getUserId().replace("|", "/")).append('|')
              .append(c.getDisplayName().replace("|", "/").replace("\n", " ")).append('|')
              .append(c.getRole()).append('|').append(c.getStatus());
        }
        broadcaster.broadcastToAll(room.roomId,
                new SyncMessage(MessageType.USER_LIST_UPDATE,
                        room.roomId, "SERVER", "Server", sb.toString()));
    }

    // =========================================================================
    // JSON (giữ lại để mã cũ vẫn gọi được; việc chuyển đổi nằm trong Operation Codec ở Common)
    // =========================================================================

    /** Chuyển CRDTOperation thành chuỗi JSON. */
    public static String operationToJson(CRDTOperation op) {
        return OperationCodec.toJson(op);
    }

    /** Đọc CRDTOperation từ JSON do {@link #operationToJson} tạo ra. */
    public static CRDTOperation operationFromJson(String json) {
        return OperationCodec.fromJson(json);
    }
}

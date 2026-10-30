package com.overleaf.server.collaboration;

import com.overleaf.common.sync.CRDTOperation;
import com.overleaf.common.sync.CRDTOperation.OpType;
import com.overleaf.common.sync.CollaboratorInfo;
import com.overleaf.common.sync.CursorPosition;
import com.overleaf.common.sync.DocumentState;
import com.overleaf.common.sync.SyncMessage;
import com.overleaf.server.collaboration.SyncBroadcaster.ClientSession;

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
        threadPool.submitSyncTask(() -> {
            try {
                switch (message.getMessageType()) {
                    case JOIN_ROOM:     handleJoinRoom(session, message);   break;
                    case LEAVE_ROOM:    handleLeaveRoom(session, message);  break;
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
        });
    }

    // =========================================================================
    // JOIN
    // =========================================================================

    /** Thêm người dùng vào phòng và gửi toàn bộ nội dung hiện tại cho họ. */
    public void handleJoinRoom(ClientSession session, SyncMessage message) {
        String roomId = message.getRoomId();
        Room room = rooms.computeIfAbsent(roomId, id -> new Room(id, ""));

        synchronized (room) {
            // Người đầu tiên là HOST, những người sau là EDITOR
            CollaboratorInfo info = room.collaborators.isEmpty()
                    ? CollaboratorInfo.createHost(message.getSenderId(), message.getSenderName())
                    : CollaboratorInfo.createEditor(message.getSenderId(), message.getSenderName());
            room.collaborators.put(info.getUserId(), info);
            broadcaster.register(roomId, session);

            // Gửi trạng thái đầy đủ cho người mới (đọc trong khóa → nhất quán với op kế tiếp)
            broadcaster.sendTo(roomId, info.getUserId(),
                    SyncMessage.createFullSync(roomId, room.document.getContent()));
        }
        broadcastUserList(room);
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

        CRDTOperation toBroadcast;
        synchronized (room) {
            CollaboratorInfo user = room.collaborators.get(userId);
            if (user == null || !user.canEdit()) {
                broadcaster.sendTo(roomId, userId,
                        SyncMessage.createError(roomId, "Không có quyền chỉnh sửa"));
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

            long version = room.engine.applyOperation(transformed);
            if (version < 0) {
                broadcaster.sendTo(roomId, userId,
                        SyncMessage.createError(roomId, "Không áp dụng được thao tác"));
                return;
            }
            history.addOperation(roomId, transformed);
            user.markEditing();
            toBroadcast = transformed;

            // ACK + phát trong khóa để giữ đúng thứ tự áp dụng giữa các client
            broadcaster.sendTo(roomId, userId,
                    SyncMessage.createAck(roomId, incoming.getOperationId()));
            broadcaster.broadcast(roomId,
                    SyncMessage.createTextEdit(roomId, userId, message.getSenderName(),
                            operationToJson(toBroadcast)),
                    userId);
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
                new SyncMessage(com.overleaf.common.sync.MessageType.USER_LIST_UPDATE,
                        room.roomId, "SERVER", "Server", sb.toString()));
    }

    // =========================================================================
    // JSON (tối giản, không cần thư viện ngoài)
    // =========================================================================

    /** Chuyển CRDTOperation thành chuỗi JSON. */
    public static String operationToJson(CRDTOperation op) {
        return "{\"id\":" + quote(op.getOperationId())
                + ",\"type\":" + quote(op.getType().name())
                + ",\"pos\":" + op.getPosition()
                + ",\"content\":" + quote(op.getContent())
                + ",\"user\":" + quote(op.getUserId())
                + ",\"lamport\":" + op.getLamportTimestamp()
                + ",\"ts\":" + op.getCreatedAt() + "}";
    }

    /** Đọc CRDTOperation từ JSON do {@link #operationToJson} tạo ra. */
    public static CRDTOperation operationFromJson(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("payload rỗng");
        }
        Map<String, String> f = new java.util.HashMap<>();
        int i = json.indexOf('{') + 1;
        int n = json.length();
        while (i < n) {
            int ks = json.indexOf('"', i);
            if (ks < 0) break;
            int[] end = new int[1];
            String key = readString(json, ks, end);
            int colon = json.indexOf(':', end[0]);
            int vs = colon + 1;
            while (vs < n && json.charAt(vs) == ' ') vs++;
            String value;
            if (json.charAt(vs) == '"') {
                value = readString(json, vs, end);
                i = end[0];
            } else {
                int ve = vs;
                while (ve < n && json.charAt(ve) != ',' && json.charAt(ve) != '}') ve++;
                value = json.substring(vs, ve).trim();
                i = ve;
            }
            f.put(key, value);
        }
        return new CRDTOperation(
                f.get("id"),
                OpType.valueOf(f.get("type")),
                Integer.parseInt(f.get("pos")),
                f.get("content"),
                f.get("user"),
                Long.parseLong(f.get("lamport")),
                Long.parseLong(f.get("ts")));
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Đọc chuỗi JSON bắt đầu tại dấu " ở vị trí start; end[0] = vị trí sau dấu " đóng. */
    private static String readString(String s, int start, int[] end) {
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') { end[0] = i + 1; return sb.toString(); }
            if (c == '\\') {
                char e = s.charAt(++i);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; break;
                    default:  sb.append(e);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        throw new IllegalArgumentException("JSON chuỗi không đóng");
    }
}

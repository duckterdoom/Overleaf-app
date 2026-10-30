package com.overleaf.common.sync;

import java.io.Serializable;
import java.util.Objects;

/**
 * =========================================================================
 * M4: GÓI TIN ĐỒNG BỘ QUA MẠNG (Sync Message)
 * =========================================================================
 *
 * Đây là "phong bì" chuẩn cho MỌI thông điệp được gửi qua TCP Socket
 * giữa Client và Server. Mỗi SyncMessage chứa:
 *
 * - messageType: Loại thông điệp (JOIN, EDIT, CURSOR, v.v.)
 * - roomId:      Mã phòng cộng tác đích
 * - senderId:    ID người gửi
 * - senderName:  Tên hiển thị người gửi
 * - payload:     Dữ liệu đính kèm (chuỗi JSON, tuỳ loại thông điệp)
 * - timestamp:   Thời điểm tạo gói tin
 *
 * Cách dùng: Client tạo SyncMessage → serialize → gửi qua TCP Socket →
 * Server nhận → deserialize → xử lý theo messageType → phát sóng cho
 * các client khác trong cùng phòng.
 */
public class SyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private final MessageType messageType;
    private final String roomId;
    private final String senderId;
    private final String senderName;
    private final String payload;    // Nội dung JSON tuỳ loại thông điệp
    private final long timestamp;

    /**
     * Hàm khởi tạo đầy đủ.
     */
    public SyncMessage(MessageType messageType, String roomId,
                       String senderId, String senderName,
                       String payload) {
        this.messageType = Objects.requireNonNull(messageType, "messageType không được null");
        this.roomId = Objects.requireNonNull(roomId, "roomId không được null");
        this.senderId = Objects.requireNonNull(senderId, "senderId không được null");
        this.senderName = senderName != null ? senderName : senderId;
        this.payload = payload != null ? payload : "";
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Hàm khởi tạo từ dữ liệu nhận qua mạng (đã có timestamp).
     */
    public SyncMessage(MessageType messageType, String roomId,
                       String senderId, String senderName,
                       String payload, long timestamp) {
        this.messageType = Objects.requireNonNull(messageType);
        this.roomId = Objects.requireNonNull(roomId);
        this.senderId = Objects.requireNonNull(senderId);
        this.senderName = senderName != null ? senderName : senderId;
        this.payload = payload != null ? payload : "";
        this.timestamp = timestamp;
    }

    // =========================================================================
    // PHƯƠNG THỨC FACTORY TĨNH — Tạo nhanh các loại thông điệp phổ biến
    // =========================================================================

    /**
     * Tạo thông điệp yêu cầu tham gia phòng.
     *
     * @param roomId     mã phòng
     * @param userId     ID người dùng
     * @param userName   tên hiển thị
     * @return SyncMessage kiểu JOIN_ROOM
     */
    public static SyncMessage createJoinRoom(String roomId, String userId, String userName) {
        return new SyncMessage(MessageType.JOIN_ROOM, roomId, userId, userName, "");
    }

    /**
     * Tạo thông điệp rời phòng.
     */
    public static SyncMessage createLeaveRoom(String roomId, String userId, String userName) {
        return new SyncMessage(MessageType.LEAVE_ROOM, roomId, userId, userName, "");
    }

    /**
     * Tạo thông điệp chỉnh sửa văn bản chứa thao tác CRDT đã serialize.
     *
     * @param roomId          mã phòng
     * @param userId          ID người dùng
     * @param userName        tên hiển thị
     * @param operationJson   chuỗi JSON của CRDTOperation
     * @return SyncMessage kiểu TEXT_EDIT
     */
    public static SyncMessage createTextEdit(String roomId, String userId,
                                              String userName, String operationJson) {
        return new SyncMessage(MessageType.TEXT_EDIT, roomId, userId, userName, operationJson);
    }

    /**
     * Tạo thông điệp cập nhật vị trí con trỏ.
     *
     * @param roomId        mã phòng
     * @param userId        ID người dùng
     * @param userName      tên hiển thị
     * @param cursorJson    chuỗi JSON của CursorPosition
     * @return SyncMessage kiểu CURSOR_UPDATE
     */
    public static SyncMessage createCursorUpdate(String roomId, String userId,
                                                  String userName, String cursorJson) {
        return new SyncMessage(MessageType.CURSOR_UPDATE, roomId, userId, userName, cursorJson);
    }

    /**
     * Tạo thông điệp đồng bộ toàn phần (gửi toàn bộ nội dung tài liệu).
     *
     * @param roomId          mã phòng
     * @param fullContent     toàn bộ nội dung tài liệu
     * @return SyncMessage kiểu FULL_SYNC
     */
    public static SyncMessage createFullSync(String roomId, String fullContent) {
        return new SyncMessage(MessageType.FULL_SYNC, roomId, "SERVER", "Server", fullContent);
    }

    /**
     * Tạo thông điệp xác nhận (ACK) từ Server.
     *
     * @param roomId      mã phòng
     * @param operationId ID thao tác được xác nhận
     * @return SyncMessage kiểu ACK
     */
    public static SyncMessage createAck(String roomId, String operationId) {
        return new SyncMessage(MessageType.ACK, roomId, "SERVER", "Server", operationId);
    }

    /**
     * Tạo thông điệp heartbeat.
     */
    public static SyncMessage createHeartbeat(String roomId, String userId) {
        return new SyncMessage(MessageType.HEARTBEAT, roomId, userId, userId, "ping");
    }

    /**
     * Tạo thông điệp lỗi.
     */
    public static SyncMessage createError(String roomId, String errorMessage) {
        return new SyncMessage(MessageType.ERROR, roomId, "SERVER", "Server", errorMessage);
    }

    // =========================================================================
    // GETTER
    // =========================================================================

    public MessageType getMessageType() { return messageType; }
    public String getRoomId()           { return roomId; }
    public String getSenderId()         { return senderId; }
    public String getSenderName()       { return senderName; }
    public String getPayload()          { return payload; }
    public long getTimestamp()           { return timestamp; }

    @Override
    public String toString() {
        return String.format("SyncMessage{type=%s, room='%s', sender='%s', payloadLen=%d}",
                messageType, roomId, senderName, payload.length());
    }
}

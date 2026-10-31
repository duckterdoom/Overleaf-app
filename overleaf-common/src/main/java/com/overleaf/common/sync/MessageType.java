package com.overleaf.common.sync;

/**
 * =========================================================================
 * M4: KIỂU THÔNG ĐIỆP ĐỒNG BỘ (Sync Message Type Enum)
 * =========================================================================
 *
 * Liệt kê tất cả các loại thông điệp được trao đổi qua giao thức TCP
 * giữa Client và Server trong quá trình cộng tác thời gian thực.
 *
 * Mỗi thông điệp gửi qua mạng đều phải gắn một MessageType để Server
 * biết cách xử lý đúng (ví dụ: chỉnh sửa văn bản, di chuyển con trỏ,
 * tham gia/rời phòng, v.v.).
 */
public enum MessageType {

    /** M3 transport handshake; payload is the configured shared access token. */
    AUTH,
    /** Bounded transport upload of a RESYNC too large for a single frame. */
    RESYNC_BEGIN,
    RESYNC_PART,
    RESYNC_END,

    // ---- Quản lý phòng cộng tác ----

    /** Client gửi yêu cầu tham gia một phòng làm việc nhóm */
    JOIN_ROOM,

    /** Client gửi yêu cầu rời khỏi phòng hiện tại */
    LEAVE_ROOM,

    // ---- Đồng bộ nội dung văn bản ----

    /** Client gửi một thao tác chỉnh sửa văn bản (INSERT hoặc DELETE) */
    TEXT_EDIT,

    /** Server gửi toàn bộ trạng thái tài liệu cho client mới tham gia */
    FULL_SYNC,

    /** Server xác nhận đã nhận và xử lý thành công thao tác của client */
    ACK,
    /** Newline-separated operation IDs acknowledged after a large offline upload. */
    ACK_BATCH,

    // ---- Đồng bộ lại sau khi mất kết nối (chế độ offline) ----

    /**
     * Client gửi sau khi kết nối lại: mã thao tác cuối cùng đã thấy cộng với
     * các thao tác viết khi offline chưa được server xác nhận.
     */
    RESYNC,

    /**
     * Server báo mã thao tác cuối cùng trong lịch sử phòng (có thể rỗng).
     * Client lưu lại để lần đồng bộ lại sau biết mình đã thấy đến đâu.
     */
    CHECKPOINT,

    // ---- Thông tin cộng tác viên ----

    /** Client gửi cập nhật vị trí con trỏ (dòng, cột) để hiển thị trên máy khác */
    CURSOR_UPDATE,

    /** Server phát sóng danh sách người dùng đang trực tuyến trong phòng */
    USER_LIST_UPDATE,

    // ---- Hệ thống ----

    /** Gói tin heartbeat để kiểm tra kết nối còn sống hay không */
    HEARTBEAT,

    /** Thông báo lỗi từ Server gửi về Client */
    ERROR;

    /**
     * Chuyển chuỗi thành MessageType, trả về null nếu không hợp lệ.
     *
     * @param value tên kiểu thông điệp (không phân biệt hoa thường)
     * @return MessageType tương ứng hoặc null
     */
    public static MessageType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MessageType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

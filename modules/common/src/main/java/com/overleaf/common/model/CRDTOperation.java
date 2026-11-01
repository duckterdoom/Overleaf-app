package com.overleaf.common.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * ========================================================================= M4: THAO TÁC CRDT (CRDT
 * Operation) =========================================================================
 *
 * <p>Đại diện cho MỘT thao tác chỉnh sửa văn bản trong thuật toán CRDT (Conflict-free Replicated
 * Data Type).
 *
 * <p>Mỗi khi người dùng gõ một ký tự (INSERT) hoặc xóa một đoạn văn bản (DELETE), hệ thống sẽ tạo
 * ra một CRDTOperation chứa đầy đủ thông tin: - Ai thực hiện (userId) - Thao tác gì (INSERT hay
 * DELETE) - Tại vị trí nào (position) - Nội dung gì (content) - Đồng hồ Lamport (lamportTimestamp)
 * để sắp xếp thứ tự toàn cục
 *
 * <p>Đồng hồ Lamport đảm bảo rằng khi hai người cùng gõ đồng thời tại cùng một vị trí, hệ thống
 * luôn có thể quyết định thao tác nào đến trước một cách NHẤT QUÁN trên tất cả các máy.
 */
public class CRDTOperation implements Serializable, Comparable<CRDTOperation> {

    private static final long serialVersionUID = 1L;

    /** Loại thao tác CRDT */
    public enum OpType {
        /** Chèn nội dung tại vị trí chỉ định */
        INSERT,
        /** Xóa nội dung tại vị trí chỉ định */
        DELETE
    }

    // ---- Các trường dữ liệu ----

    /** ID duy nhất của thao tác (UUID), đảm bảo không trùng lặp trên toàn hệ thống */
    private final String operationId;

    /** Loại thao tác: INSERT hoặc DELETE */
    private final OpType type;

    /** Vị trí ký tự trong văn bản nơi thao tác được áp dụng (bắt đầu từ 0) */
    private final int position;

    /** Nội dung được chèn (INSERT) hoặc nội dung bị xóa (DELETE) */
    private final String content;

    /** ID người dùng thực hiện thao tác */
    private final String userId;

    /**
     * Đồng hồ Lamport — số nguyên tăng dần dùng để xác định thứ tự TOÀN CỤC của các thao tác trên
     * nhiều máy khác nhau. Quy tắc: Khi nhận một thao tác từ xa có lamportTimestamp = T, máy nhận
     * sẽ cập nhật đồng hồ cục bộ = max(cục_bộ, T) + 1.
     */
    private final long lamportTimestamp;

    /** Thời điểm tạo thao tác (mili giây từ Epoch) */
    private final long createdAt;

    /**
     * Hàm khởi tạo đầy đủ.
     *
     * @param type Loại thao tác (INSERT/DELETE)
     * @param position Vị trí áp dụng trong văn bản
     * @param content Nội dung chèn hoặc xóa
     * @param userId ID người thực hiện
     * @param lamportTimestamp Giá trị đồng hồ Lamport
     */
    public CRDTOperation(
            OpType type, int position, String content, String userId, long lamportTimestamp) {
        this.operationId = UUID.randomUUID().toString();
        this.type = Objects.requireNonNull(type, "Loại thao tác không được null");
        this.position = position;
        this.content = content != null ? content : "";
        this.userId = Objects.requireNonNull(userId, "userId không được null");
        this.lamportTimestamp = lamportTimestamp;
        this.createdAt = System.currentTimeMillis();
    }

    /** Hàm khởi tạo từ dữ liệu nhận qua mạng (đã có sẵn operationId). */
    public CRDTOperation(
            String operationId,
            OpType type,
            int position,
            String content,
            String userId,
            long lamportTimestamp,
            long createdAt) {
        this.operationId = Objects.requireNonNull(operationId);
        this.type = Objects.requireNonNull(type);
        this.position = position;
        this.content = content != null ? content : "";
        this.userId = Objects.requireNonNull(userId);
        this.lamportTimestamp = lamportTimestamp;
        this.createdAt = createdAt;
    }

    // ---- Phương thức Factory tiện ích ----

    /**
     * Tạo nhanh thao tác INSERT.
     *
     * @param position Vị trí chèn
     * @param content Nội dung cần chèn
     * @param userId ID người dùng
     * @param lamportTimestamp Đồng hồ Lamport hiện tại
     * @return CRDTOperation mới kiểu INSERT
     */
    public static CRDTOperation createInsert(
            int position, String content, String userId, long lamportTimestamp) {
        return new CRDTOperation(OpType.INSERT, position, content, userId, lamportTimestamp);
    }

    /**
     * Tạo nhanh thao tác DELETE.
     *
     * @param position Vị trí bắt đầu xóa
     * @param content Nội dung bị xóa (để broadcast cho client khác biết)
     * @param userId ID người dùng
     * @param lamportTimestamp Đồng hồ Lamport hiện tại
     * @return CRDTOperation mới kiểu DELETE
     */
    public static CRDTOperation createDelete(
            int position, String content, String userId, long lamportTimestamp) {
        return new CRDTOperation(OpType.DELETE, position, content, userId, lamportTimestamp);
    }

    // ---- So sánh theo đồng hồ Lamport (Comparable) ----

    /**
     * Sắp xếp thao tác theo đồng hồ Lamport tăng dần. Nếu hai thao tác có cùng timestamp, sắp xếp
     * theo userId (đảm bảo thứ tự NHẤT QUÁN trên mọi node trong hệ thống phân tán).
     */
    @Override
    public int compareTo(CRDTOperation other) {
        int cmp = Long.compare(this.lamportTimestamp, other.lamportTimestamp);
        if (cmp != 0) return cmp;
        // Nếu cùng timestamp → dùng userId để phân định (tie-breaking)
        return this.userId.compareTo(other.userId);
    }

    // ---- Getter ----

    public String getOperationId() {
        return operationId;
    }

    public OpType getType() {
        return type;
    }

    public int getPosition() {
        return position;
    }

    public String getContent() {
        return content;
    }

    public String getUserId() {
        return userId;
    }

    public long getLamportTimestamp() {
        return lamportTimestamp;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /** Kiểm tra đây có phải thao tác chèn không */
    public boolean isInsert() {
        return type == OpType.INSERT;
    }

    /** Kiểm tra đây có phải thao tác xóa không */
    public boolean isDelete() {
        return type == OpType.DELETE;
    }

    /** Độ dài ký tự bị ảnh hưởng bởi thao tác */
    public int getContentLength() {
        return content.length();
    }

    // ---- equals / hashCode / toString ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CRDTOperation that = (CRDTOperation) o;
        return operationId.equals(that.operationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationId);
    }

    @Override
    public String toString() {
        return String.format(
                "CRDTOp{id=%.8s, type=%s, pos=%d, len=%d, user=%s, lamport=%d}",
                operationId, type, position, content.length(), userId, lamportTimestamp);
    }
}

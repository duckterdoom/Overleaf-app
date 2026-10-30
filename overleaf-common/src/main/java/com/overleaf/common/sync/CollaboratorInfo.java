package com.overleaf.common.sync;

import java.io.Serializable;
import java.util.Objects;

/**
 * =========================================================================
 * M4: THÔNG TIN CỘNG TÁC VIÊN (Collaborator Info)
 * =========================================================================
 *
 * Lưu trữ thông tin về MỘT người dùng đang tham gia phòng cộng tác.
 * Server duy trì danh sách CollaboratorInfo cho mỗi phòng và phát sóng
 * (broadcast) cập nhật mỗi khi có người tham gia hoặc rời đi.
 *
 * Mỗi cộng tác viên có:
 * - userId / displayName: Nhận dạng người dùng
 * - role:   Vai trò (HOST quản lý phòng, EDITOR được sửa, VIEWER chỉ xem)
 * - status: Trạng thái hiện tại (ACTIVE đang hoạt động, IDLE đang rảnh,
 *           EDITING đang gõ văn bản)
 */
public class CollaboratorInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Vai trò của cộng tác viên trong phòng.
     */
    public enum Role {
        /** Chủ phòng — có quyền quản lý phòng và mời/đuổi người dùng */
        HOST,
        /** Biên tập viên — có quyền chỉnh sửa tài liệu */
        EDITOR,
        /** Người xem — chỉ được đọc, không sửa */
        VIEWER
    }

    /**
     * Trạng thái hoạt động hiện tại.
     */
    public enum Status {
        /** Đang hoạt động bình thường (di chuyển chuột, cuộn trang, v.v.) */
        ACTIVE,
        /** Không thao tác trong một khoảng thời gian (ví dụ > 2 phút) */
        IDLE,
        /** Đang gõ/chỉnh sửa văn bản */
        EDITING
    }

    // ---- Các trường dữ liệu ----

    /** ID duy nhất của người dùng */
    private final String userId;

    /** Tên hiển thị trên giao diện */
    private final String displayName;

    /** Vai trò trong phòng cộng tác */
    private Role role;

    /** Trạng thái hoạt động hiện tại */
    private Status status;

    /** Thời điểm tham gia phòng (mili giây từ Epoch) */
    private final long joinedAt;

    /** Thời điểm hoạt động cuối cùng */
    private volatile long lastActiveAt;

    /**
     * Hàm khởi tạo đầy đủ.
     *
     * @param userId       ID người dùng
     * @param displayName  tên hiển thị
     * @param role         vai trò (HOST, EDITOR, VIEWER)
     */
    public CollaboratorInfo(String userId, String displayName, Role role) {
        this.userId = Objects.requireNonNull(userId, "userId không được null");
        this.displayName = displayName != null ? displayName : userId;
        this.role = Objects.requireNonNull(role, "role không được null");
        this.status = Status.ACTIVE;
        this.joinedAt = System.currentTimeMillis();
        this.lastActiveAt = this.joinedAt;
    }

    /**
     * Tạo nhanh cộng tác viên với vai trò EDITOR (mặc định phổ biến nhất).
     */
    public static CollaboratorInfo createEditor(String userId, String displayName) {
        return new CollaboratorInfo(userId, displayName, Role.EDITOR);
    }

    /**
     * Tạo nhanh chủ phòng (HOST).
     */
    public static CollaboratorInfo createHost(String userId, String displayName) {
        return new CollaboratorInfo(userId, displayName, Role.HOST);
    }

    /**
     * Tạo nhanh người xem (VIEWER).
     */
    public static CollaboratorInfo createViewer(String userId, String displayName) {
        return new CollaboratorInfo(userId, displayName, Role.VIEWER);
    }

    // ---- Cập nhật trạng thái ----

    /**
     * Đánh dấu người dùng đang gõ văn bản.
     */
    public void markEditing() {
        this.status = Status.EDITING;
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * Đánh dấu người dùng đang hoạt động (nhưng không gõ).
     */
    public void markActive() {
        this.status = Status.ACTIVE;
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * Đánh dấu người dùng đang rảnh (không thao tác gì).
     */
    public void markIdle() {
        this.status = Status.IDLE;
    }

    /**
     * Thay đổi vai trò của cộng tác viên.
     *
     * @param newRole vai trò mới
     */
    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole);
    }

    /**
     * Ghi nhận hoạt động cuối cùng (cập nhật lastActiveAt).
     */
    public void touch() {
        this.lastActiveAt = System.currentTimeMillis();
    }

    /**
     * Kiểm tra người dùng có phải chủ phòng không.
     */
    public boolean isHost() {
        return role == Role.HOST;
    }

    /**
     * Kiểm tra người dùng có quyền chỉnh sửa không (HOST hoặc EDITOR).
     */
    public boolean canEdit() {
        return role == Role.HOST || role == Role.EDITOR;
    }

    /**
     * Kiểm tra xem người dùng có đang không hoạt động quá lâu không.
     *
     * @param timeoutMs thời gian tối đa cho phép không hoạt động (mili giây)
     * @return true nếu đã vượt quá thời gian cho phép
     */
    public boolean isTimedOut(long timeoutMs) {
        return (System.currentTimeMillis() - lastActiveAt) > timeoutMs;
    }

    // ---- Getter ----

    public String getUserId()       { return userId; }
    public String getDisplayName()  { return displayName; }
    public Role getRole()           { return role; }
    public Status getStatus()       { return status; }
    public long getJoinedAt()       { return joinedAt; }
    public long getLastActiveAt()   { return lastActiveAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollaboratorInfo that = (CollaboratorInfo) o;
        return userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return String.format("Collaborator{user='%s', name='%s', role=%s, status=%s}",
                userId, displayName, role, status);
    }
}

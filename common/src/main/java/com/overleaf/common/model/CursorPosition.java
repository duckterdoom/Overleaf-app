package com.overleaf.common.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * =========================================================================
 * M4: VỊ TRÍ CON TRỎ CỘNG TÁC VIÊN (Remote Cursor Position)
 * =========================================================================
 *
 * Khi nhiều người cùng soạn thảo một tài liệu, mỗi người cần NHÌN THẤY
 * con trỏ của những người khác đang ở đâu (dòng nào, cột nào, đang sửa
 * file nào). Lớp này lưu trữ thông tin vị trí con trỏ của MỘT cộng tác
 * viên và được gửi qua mạng TCP để các client khác cập nhật giao diện.
 *
 * Ví dụ: Alice đang gõ tại dòng 24, cột 15 trong file main.tex →
 * Bob sẽ thấy con trỏ của Alice nhấp nháy tại vị trí đó trên màn hình.
 */
public class CursorPosition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID người dùng sở hữu con trỏ này */
    private final String userId;

    /** Tên hiển thị của người dùng */
    private final String userName;

    /** Số dòng hiện tại của con trỏ (bắt đầu từ 1) */
    private int line;

    /** Số cột hiện tại của con trỏ (bắt đầu từ 1) */
    private int column;

    /** Tên file đang được chỉnh sửa (ví dụ: "main.tex") */
    private String fileName;

    /** Thời điểm cập nhật cuối cùng (mili giây từ Epoch) */
    private long lastUpdated;

    /**
     * Hàm khởi tạo đầy đủ.
     *
     * @param userId    ID người dùng
     * @param userName  tên hiển thị
     * @param line      dòng hiện tại (≥ 1)
     * @param column    cột hiện tại (≥ 1)
     * @param fileName  tên file đang sửa
     */
    public CursorPosition(String userId, String userName,
                           int line, int column, String fileName) {
        this.userId = Objects.requireNonNull(userId, "userId không được null");
        this.userName = userName != null ? userName : userId;
        this.line = Math.max(1, line);
        this.column = Math.max(1, column);
        this.fileName = fileName != null ? fileName : "";
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Cập nhật vị trí con trỏ mới.
     *
     * @param newLine    dòng mới
     * @param newColumn  cột mới
     * @param newFileName tên file mới (có thể thay đổi khi chuyển tab)
     */
    public void updatePosition(int newLine, int newColumn, String newFileName) {
        this.line = Math.max(1, newLine);
        this.column = Math.max(1, newColumn);
        if (newFileName != null) {
            this.fileName = newFileName;
        }
        this.lastUpdated = System.currentTimeMillis();
    }

    /**
     * Cập nhật vị trí con trỏ mới (giữ nguyên file).
     */
    public void updatePosition(int newLine, int newColumn) {
        updatePosition(newLine, newColumn, this.fileName);
    }

    /**
     * Kiểm tra xem con trỏ có còn "tươi" (active) hay đã lâu không cập nhật.
     *
     * @param timeoutMs thời gian hết hiệu lực tính bằng mili giây
     * @return true nếu con trỏ vẫn còn hoạt động
     */
    public boolean isActive(long timeoutMs) {
        return (System.currentTimeMillis() - lastUpdated) < timeoutMs;
    }

    // ---- Getter ----

    public String getUserId()     { return userId; }
    public String getUserName()   { return userName; }
    public int getLine()          { return line; }
    public int getColumn()        { return column; }
    public String getFileName()   { return fileName; }
    public long getLastUpdated()  { return lastUpdated; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CursorPosition that = (CursorPosition) o;
        return userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return String.format("Cursor{user='%s', line=%d, col=%d, file='%s'}",
                userName, line, column, fileName);
    }
}

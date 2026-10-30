package com.overleaf.common.sync;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * =========================================================================
 * M4: TRẠNG THÁI TÀI LIỆU AN TOÀN ĐA LUỒNG (Thread-Safe Document State)
 * =========================================================================
 *
 * Lớp này lưu giữ NỘI DUNG HIỆN TẠI của một tài liệu LaTeX đang được
 * nhiều người cùng chỉnh sửa đồng thời.
 *
 * Vấn đề cốt lõi: Khi có 3–4 người cùng gõ vào một văn bản cùng lúc,
 * nếu không bảo vệ dữ liệu, các luồng sẽ ghi đè lên nhau gây ra lỗi
 * (Race Condition). Lớp này giải quyết bằng ReentrantReadWriteLock:
 *
 * - ReadLock: Nhiều luồng có thể ĐỌC nội dung cùng lúc (không chặn nhau)
 * - WriteLock: Chỉ MỘT luồng được GHI tại một thời điểm (chặn toàn bộ)
 *
 * Ngoài ra, mỗi lần nội dung thay đổi, version sẽ tăng lên 1 đơn vị
 * (sử dụng AtomicLong), giúp client biết mình đang ở phiên bản nào
 * và có cần đồng bộ lại hay không.
 */
public class DocumentState implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Nội dung tài liệu hiện tại (dùng StringBuilder cho hiệu suất cao) */
    private final StringBuilder content;

    /** ID tài liệu (thường là tên file, ví dụ: "main.tex") */
    private final String documentId;

    /** Phiên bản tài liệu — tăng sau mỗi lần sửa đổi */
    private final AtomicLong version;

    /** Khóa đọc-ghi để bảo vệ nội dung khỏi Race Condition */
    private final transient ReentrantReadWriteLock rwLock;

    /** Thời điểm sửa đổi cuối cùng (mili giây từ Epoch) */
    private volatile long lastModified;

    /**
     * Khởi tạo tài liệu mới với nội dung ban đầu.
     *
     * @param documentId   ID/tên tài liệu (ví dụ: "main.tex")
     * @param initialContent nội dung ban đầu (có thể rỗng)
     */
    public DocumentState(String documentId, String initialContent) {
        this.documentId = documentId;
        this.content = new StringBuilder(initialContent != null ? initialContent : "");
        this.version = new AtomicLong(0);
        this.rwLock = new ReentrantReadWriteLock(true); // fair lock
        this.lastModified = System.currentTimeMillis();
    }

    /**
     * Khởi tạo tài liệu rỗng.
     *
     * @param documentId ID/tên tài liệu
     */
    public DocumentState(String documentId) {
        this(documentId, "");
    }

    // =========================================================================
    // CÁC THAO TÁC GHI (CẦN WriteLock — chỉ 1 luồng tại 1 thời điểm)
    // =========================================================================

    /**
     * Chèn nội dung vào vị trí chỉ định trong tài liệu.
     *
     * Phương thức này AN TOÀN ĐA LUỒNG: dùng WriteLock đảm bảo chỉ có
     * một luồng được chèn tại một thời điểm, tránh nội dung bị xáo trộn.
     *
     * @param position vị trí chèn (index ký tự, bắt đầu từ 0)
     * @param text     chuỗi nội dung cần chèn
     * @return phiên bản mới của tài liệu sau khi chèn
     * @throws IndexOutOfBoundsException nếu vị trí nằm ngoài phạm vi
     */
    public long applyInsert(int position, String text) {
        rwLock.writeLock().lock();
        try {
            // Kiểm tra vị trí hợp lệ
            if (position < 0 || position > content.length()) {
                throw new IndexOutOfBoundsException(
                        String.format("Vị trí chèn %d nằm ngoài phạm vi [0, %d]",
                                position, content.length()));
            }
            content.insert(position, text);
            lastModified = System.currentTimeMillis();
            return version.incrementAndGet();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Xóa một đoạn nội dung từ vị trí bắt đầu đến vị trí kết thúc.
     *
     * @param position vị trí bắt đầu xóa
     * @param length   số ký tự cần xóa
     * @return phiên bản mới của tài liệu sau khi xóa
     * @throws IndexOutOfBoundsException nếu vị trí nằm ngoài phạm vi
     */
    public long applyDelete(int position, int length) {
        rwLock.writeLock().lock();
        try {
            int endPos = position + length;
            if (position < 0 || endPos > content.length()) {
                throw new IndexOutOfBoundsException(
                        String.format("Phạm vi xóa [%d, %d) nằm ngoài [0, %d]",
                                position, endPos, content.length()));
            }
            content.delete(position, endPos);
            lastModified = System.currentTimeMillis();
            return version.incrementAndGet();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Thay thế toàn bộ nội dung tài liệu (dùng khi đồng bộ toàn phần / FULL_SYNC).
     *
     * @param newContent nội dung mới hoàn toàn
     * @return phiên bản mới
     */
    public long replaceAll(String newContent) {
        rwLock.writeLock().lock();
        try {
            content.setLength(0);
            content.append(newContent != null ? newContent : "");
            lastModified = System.currentTimeMillis();
            return version.incrementAndGet();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // =========================================================================
    // CÁC THAO TÁC ĐỌC (CẦN ReadLock — nhiều luồng đọc cùng lúc được)
    // =========================================================================

    /**
     * Lấy toàn bộ nội dung tài liệu hiện tại.
     * Nhiều luồng có thể gọi phương thức này ĐỒNG THỜI mà không bị chặn.
     *
     * @return bản sao nội dung tài liệu
     */
    public String getContent() {
        rwLock.readLock().lock();
        try {
            return content.toString();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Lấy độ dài nội dung tài liệu.
     *
     * @return số ký tự trong tài liệu
     */
    public int getContentLength() {
        rwLock.readLock().lock();
        try {
            return content.length();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ---- Getter đơn giản (không cần lock vì là immutable hoặc atomic) ----

    public String getDocumentId()   { return documentId; }
    public long getVersion()        { return version.get(); }
    public long getLastModified()   { return lastModified; }

    @Override
    public String toString() {
        return String.format("DocumentState{id='%s', version=%d, length=%d}",
                documentId, version.get(), getContentLength());
    }
}

package com.overleaf.server.collab;

import com.overleaf.common.model.CRDTOperation;
import com.overleaf.common.model.DocumentState;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ========================================================================= M4: LÕI BỘ MÁY CRDT
 * (CRDT Engine) =========================================================================
 *
 * <p>Đây là TRÁI TIM của hệ thống đồng bộ văn bản thời gian thực. Triển khai thuật toán RGA
 * (Replicated Growable Array) đơn giản hóa, kết hợp với Chuyển đổi Thao tác (Operational
 * Transformation - OT) để xử lý xung đột khi nhiều người gõ đồng thời.
 *
 * <p>╔══════════════════════════════════════════════════════════════════╗ ║ VÍ DỤ XỬ LÝ XUNG ĐỘT: ║
 * ║ ║ ║ Văn bản gốc: "Hello" ║ ║ Alice chèn "X" tại vị trí 2: "HeXllo" ║ ║ Bob chèn "Y" tại vị trí
 * 4: "HellYo" ║ ║ ║ ║ Nếu không xử lý → mỗi máy ra kết quả khác nhau! ║ ║ OT Transform: Khi áp dụng
 * thao tác của Alice (pos=2) trước, ║ ║ thao tác của Bob cần DỊCH vị trí +1 → pos=5 → "HeXllYo" ║ ║
 * Kết quả: TẤT CẢ các máy đều có cùng nội dung "HeXllYo" ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <p>AN TOÀN ĐA LUỒNG: Toàn bộ các phương thức thay đổi trạng thái đều được đồng bộ bằng khối
 * synchronized, đảm bảo chỉ MỘT thao tác được xử lý tại một thời điểm cho mỗi tài liệu.
 */
public class CRDTEngine {

    /**
     * Đồng hồ Lamport — bộ đếm đơn điệu tăng dùng để xác định thứ tự TOÀN CỤC của các thao tác trên
     * hệ phân tán.
     *
     * <p>Quy tắc Lamport: 1. Trước mỗi sự kiện cục bộ: clock++ 2. Khi gửi thông điệp: gắn kèm clock
     * hiện tại 3. Khi nhận thông điệp: clock = max(local_clock, received_clock) + 1
     */
    private final AtomicLong lamportClock;

    /** Trạng thái tài liệu mà engine này quản lý */
    private final DocumentState documentState;

    /** Đối tượng khóa để đồng bộ hóa các thao tác trên cùng tài liệu */
    private final Object documentLock = new Object();

    /**
     * Khởi tạo CRDT Engine cho một tài liệu.
     *
     * @param documentState trạng thái tài liệu cần quản lý
     */
    public CRDTEngine(DocumentState documentState) {
        this.documentState = documentState;
        this.lamportClock = new AtomicLong(0);
    }

    // =========================================================================
    // TẠO THAO TÁC MỚI (Generate Operation)
    // =========================================================================

    /**
     * Tạo thao tác CHÈN mới với Lamport timestamp tự tăng. Được gọi khi chính server hoặc khi cần
     * tạo op từ phía server.
     *
     * @param position vị trí chèn
     * @param content nội dung cần chèn
     * @param userId ID người thực hiện
     * @return CRDTOperation mới kiểu INSERT
     */
    public CRDTOperation generateInsert(int position, String content, String userId) {
        long timestamp = lamportClock.incrementAndGet();
        return CRDTOperation.createInsert(position, content, userId, timestamp);
    }

    /**
     * Tạo thao tác XÓA mới với Lamport timestamp tự tăng.
     *
     * @param position vị trí bắt đầu xóa
     * @param content nội dung bị xóa
     * @param userId ID người thực hiện
     * @return CRDTOperation mới kiểu DELETE
     */
    public CRDTOperation generateDelete(int position, String content, String userId) {
        long timestamp = lamportClock.incrementAndGet();
        return CRDTOperation.createDelete(position, content, userId, timestamp);
    }

    // =========================================================================
    // ÁP DỤNG THAO TÁC (Apply Operation)
    // =========================================================================

    /**
     * Áp dụng một thao tác CRDT lên tài liệu.
     *
     * <p>Quy trình: 1. Cập nhật đồng hồ Lamport theo quy tắc: max(local, received) + 1 2. Áp dụng
     * thao tác INSERT hoặc DELETE lên DocumentState 3. Trả về phiên bản mới của tài liệu
     *
     * <p>Phương thức này AN TOÀN ĐA LUỒNG nhờ khối synchronized.
     *
     * @param operation thao tác cần áp dụng
     * @return phiên bản mới của tài liệu sau khi áp dụng, hoặc -1 nếu lỗi
     */
    public long applyOperation(CRDTOperation operation) {
        synchronized (documentLock) {
            // Bước 1: Cập nhật đồng hồ Lamport
            updateLamportClock(operation.getLamportTimestamp());

            // Bước 2: Áp dụng thao tác lên tài liệu
            try {
                if (operation.isInsert()) {
                    // Kiểm tra vị trí hợp lệ, clamp nếu cần
                    int safePos =
                            clampPosition(
                                    operation.getPosition(), documentState.getContentLength());
                    return documentState.applyInsert(safePos, operation.getContent());

                } else if (operation.isDelete()) {
                    int safePos =
                            clampPosition(
                                    operation.getPosition(), documentState.getContentLength());
                    int safeLen =
                            Math.min(
                                    operation.getContentLength(),
                                    documentState.getContentLength() - safePos);
                    if (safeLen > 0) {
                        return documentState.applyDelete(safePos, safeLen);
                    }
                    // Không có gì để xóa → trả về version hiện tại
                    return documentState.getVersion();
                }
            } catch (IndexOutOfBoundsException e) {
                System.err.println(
                        "[CRDT] Lỗi áp dụng thao tác: " + e.getMessage() + " | Op: " + operation);
                return -1;
            }
            return documentState.getVersion();
        }
    }

    /**
     * Áp dụng một danh sách thao tác theo thứ tự (dùng khi replay cho client mới tham gia phòng).
     *
     * @param operations danh sách thao tác theo thứ tự Lamport tăng dần
     */
    public void applyOperations(List<CRDTOperation> operations) {
        synchronized (documentLock) {
            for (CRDTOperation op : operations) {
                applyOperation(op);
            }
        }
    }

    // =========================================================================
    // CHUYỂN ĐỔI VỊ TRÍ THAO TÁC (Operational Transformation)
    // =========================================================================

    /**
     * Chuyển đổi vị trí của thao tác hiện tại dựa trên một thao tác đồng thời đã được áp dụng trước
     * đó (OT - Operational Transformation).
     *
     * <p>Đây là phần QUAN TRỌNG NHẤT để đảm bảo TÍNH NHẤT QUÁN: Khi hai người gõ đồng thời, vị trí
     * của thao tác sau cần được ĐIỀU CHỈNH để phản ánh đúng ý định ban đầu của người dùng.
     *
     * @param originalPosition vị trí ban đầu của thao tác
     * @param concurrentOp thao tác đồng thời đã được áp dụng trước
     * @return vị trí đã được chuyển đổi (transformed position)
     */
    public int transformPosition(int originalPosition, CRDTOperation concurrentOp) {
        if (concurrentOp.isInsert()) {
            // Thao tác đồng thời CHÈN tại vị trí concurrentPos:
            // - Nếu concurrentPos <= originalPos → đẩy vị trí hiện tại sang phải
            // - Nếu concurrentPos > originalPos → không ảnh hưởng
            if (concurrentOp.getPosition() <= originalPosition) {
                return originalPosition + concurrentOp.getContentLength();
            }
        } else if (concurrentOp.isDelete()) {
            int delStart = concurrentOp.getPosition();
            int delEnd = delStart + concurrentOp.getContentLength();

            if (delEnd <= originalPosition) {
                // Xóa hoàn toàn TRƯỚC vị trí hiện tại → dịch trái
                return originalPosition - concurrentOp.getContentLength();
            } else if (delStart < originalPosition) {
                // Vùng xóa CHỒNG LẤP vị trí hiện tại → đặt tại delStart
                return delStart;
            }
            // Xóa SAU vị trí hiện tại → không ảnh hưởng
        }
        return originalPosition;
    }

    /**
     * Chuyển đổi thao tác dựa trên thao tác đồng thời. Tạo bản sao mới với vị trí đã được điều
     * chỉnh.
     *
     * @param operation thao tác cần chuyển đổi
     * @param concurrentOp thao tác đồng thời đã được áp dụng trước
     * @return thao tác mới với vị trí đã transform
     */
    public CRDTOperation transformOperation(CRDTOperation operation, CRDTOperation concurrentOp) {
        int newPosition = transformPosition(operation.getPosition(), concurrentOp);
        return new CRDTOperation(
                operation.getOperationId(),
                operation.getType(),
                newPosition,
                operation.getContent(),
                operation.getUserId(),
                operation.getLamportTimestamp(),
                operation.getCreatedAt());
    }

    // =========================================================================
    // PHƯƠNG THỨC HỖ TRỢ
    // =========================================================================

    /**
     * Cập nhật đồng hồ Lamport theo quy tắc: clock = max(clock_cục_bộ, clock_nhận_được) + 1
     *
     * @param receivedTimestamp timestamp từ thao tác nhận được qua mạng
     */
    private void updateLamportClock(long receivedTimestamp) {
        lamportClock.updateAndGet(current -> Math.max(current, receivedTimestamp) + 1);
    }

    /**
     * Giới hạn vị trí trong phạm vi hợp lệ [0, maxPosition]. Tránh IndexOutOfBoundsException khi
     * thao tác bị trễ hoặc lệch đồng bộ.
     *
     * @param position vị trí cần kiểm tra
     * @param maxPosition giới hạn trên (= độ dài tài liệu)
     * @return vị trí đã được giới hạn
     */
    private int clampPosition(int position, int maxPosition) {
        return Math.max(0, Math.min(position, maxPosition));
    }

    // ---- Getter ----

    /** Lấy giá trị đồng hồ Lamport hiện tại */
    public long getCurrentLamportTime() {
        return lamportClock.get();
    }

    /** Lấy trạng thái tài liệu đang quản lý */
    public DocumentState getDocumentState() {
        return documentState;
    }

    /** Lấy nội dung tài liệu hiện tại */
    public String getDocumentContent() {
        return documentState.getContent();
    }

    /** Lấy phiên bản tài liệu hiện tại */
    public long getDocumentVersion() {
        return documentState.getVersion();
    }
}

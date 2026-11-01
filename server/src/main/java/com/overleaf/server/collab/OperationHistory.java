package com.overleaf.server.collab;

import com.overleaf.common.model.CRDTOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * =========================================================================
 * M4: LỊCH SỬ THAO TÁC (Operation History)
 * =========================================================================
 *
 * Bộ nhớ đệm lưu trữ LỊCH SỬ các thao tác CRDT đã được áp dụng
 * thành công cho mỗi phòng cộng tác.
 *
 * Mục đích chính:
 * 1. PHÁT LẠI (Replay): Khi một client mới tham gia phòng giữa chừng,
 *    server gửi lại toàn bộ lịch sử thao tác để client xây dựng lại
 *    trạng thái tài liệu hiện tại.
 *
 * 2. PHÁT HIỆN XUNG ĐỘT: So sánh thao tác mới nhận với các thao tác
 *    gần đây để quyết định có cần transform (OT) hay không.
 *
 * 3. GỠ LỖI: Lưu vết tất cả thao tác để truy vết khi có lỗi đồng bộ.
 *
 * Sử dụng ConcurrentLinkedDeque (hàng đợi hai đầu an toàn đa luồng)
 * với giới hạn dung lượng tối đa để tránh tràn bộ nhớ trên Server.
 */
public class OperationHistory {

    /** Số thao tác tối đa lưu trữ mặc định cho mỗi phòng */
    private static final int DEFAULT_MAX_HISTORY_SIZE = 10_000;

    /**
     * Bản đồ phòng → lịch sử thao tác.
     * ConcurrentHashMap đảm bảo an toàn khi nhiều luồng truy cập đồng thời.
     */
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<CRDTOperation>> roomHistories;

    /** Giới hạn dung lượng tối đa cho mỗi phòng */
    private final int maxHistorySize;

    /** Các phòng đã từng bị xóa bớt thao tác cũ (lịch sử không còn đầy đủ từ đầu) */
    private final java.util.Set<String> trimmedRooms = ConcurrentHashMap.newKeySet();

    /**
     * Khởi tạo với dung lượng mặc định.
     */
    public OperationHistory() {
        this(DEFAULT_MAX_HISTORY_SIZE);
    }

    /**
     * Khởi tạo với dung lượng tuỳ chỉnh.
     *
     * @param maxHistorySize số thao tác tối đa lưu trữ cho mỗi phòng
     */
    public OperationHistory(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
        this.roomHistories = new ConcurrentHashMap<>();
    }

    // =========================================================================
    // THÊM THAO TÁC VÀO LỊCH SỬ
    // =========================================================================

    /**
     * Thêm một thao tác đã xử lý thành công vào lịch sử của phòng.
     * Nếu lịch sử vượt quá giới hạn, thao tác cũ nhất sẽ bị xóa.
     *
     * @param roomId    mã phòng
     * @param operation thao tác cần lưu
     */
    public void addOperation(String roomId, CRDTOperation operation) {
        ConcurrentLinkedDeque<CRDTOperation> history =
                roomHistories.computeIfAbsent(roomId, k -> new ConcurrentLinkedDeque<>());

        history.addLast(operation);

        // Nếu vượt giới hạn → xóa thao tác cũ nhất (đầu hàng đợi)
        while (history.size() > maxHistorySize) {
            history.pollFirst();
            trimmedRooms.add(roomId);
        }
    }

    // =========================================================================
    // TRUY VẤN LỊCH SỬ
    // =========================================================================

    /**
     * Lấy toàn bộ lịch sử thao tác của một phòng.
     * Trả về danh sách bất biến (unmodifiable) để tránh sửa đổi ngoài ý muốn.
     *
     * @param roomId mã phòng
     * @return danh sách thao tác theo thứ tự thời gian, hoặc danh sách rỗng
     */
    public List<CRDTOperation> getFullHistory(String roomId) {
        ConcurrentLinkedDeque<CRDTOperation> history = roomHistories.get(roomId);
        if (history == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * Lấy các thao tác có Lamport timestamp > sinceTimestamp.
     * Dùng khi client bị mất kết nối rồi quay lại, chỉ cần đồng bộ
     * những thao tác đã xảy ra kể từ lần cuối nó nhận được.
     *
     * @param roomId          mã phòng
     * @param sinceTimestamp   Lamport timestamp cuối cùng client đã nhận
     * @return danh sách thao tác sau thời điểm chỉ định
     */
    public List<CRDTOperation> getOperationsSince(String roomId, long sinceTimestamp) {
        ConcurrentLinkedDeque<CRDTOperation> history = roomHistories.get(roomId);
        if (history == null) {
            return Collections.emptyList();
        }
        return history.stream()
                .filter(op -> op.getLamportTimestamp() > sinceTimestamp)
                .collect(Collectors.toList());
    }

    /**
     * Lấy N thao tác GẦN NHẤT của một phòng.
     * Hữu ích để kiểm tra xung đột với các thao tác vừa xảy ra.
     *
     * @param roomId mã phòng
     * @param count  số thao tác cần lấy
     * @return danh sách N thao tác cuối cùng
     */
    public List<CRDTOperation> getRecentOperations(String roomId, int count) {
        ConcurrentLinkedDeque<CRDTOperation> history = roomHistories.get(roomId);
        if (history == null) {
            return Collections.emptyList();
        }

        List<CRDTOperation> allOps = new ArrayList<>(history);
        int fromIndex = Math.max(0, allOps.size() - count);
        return Collections.unmodifiableList(allOps.subList(fromIndex, allOps.size()));
    }

    /**
     * Lấy các thao tác của một người dùng cụ thể trong phòng.
     *
     * @param roomId mã phòng
     * @param userId ID người dùng
     * @return danh sách thao tác của người dùng chỉ định
     */
    public List<CRDTOperation> getOperationsByUser(String roomId, String userId) {
        ConcurrentLinkedDeque<CRDTOperation> history = roomHistories.get(roomId);
        if (history == null) {
            return Collections.emptyList();
        }
        return history.stream()
                .filter(op -> op.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // THỐNG KÊ VÀ QUẢN LÝ
    // =========================================================================

    /**
     * Đếm số thao tác trong lịch sử của một phòng.
     *
     * @param roomId mã phòng
     * @return số thao tác đã lưu
     */
    public int getHistorySize(String roomId) {
        ConcurrentLinkedDeque<CRDTOperation> history = roomHistories.get(roomId);
        return history != null ? history.size() : 0;
    }

    /**
     * Xóa toàn bộ lịch sử của một phòng (khi phòng bị đóng).
     *
     * @param roomId mã phòng cần xóa lịch sử
     */
    public void clearHistory(String roomId) {
        roomHistories.remove(roomId);
        trimmedRooms.remove(roomId);
    }

    // =========================================================================
    // HỖ TRỢ ĐỒNG BỘ LẠI SAU KHI OFFLINE
    // =========================================================================

    /** Phòng này đã từng bị xóa bớt thao tác cũ chưa (lịch sử không còn đầy đủ). */
    public boolean wasTrimmed(String roomId) {
        return trimmedRooms.contains(roomId);
    }

    /** Thao tác có mã này đã nằm trong lịch sử phòng chưa (dùng để chống áp dụng trùng). */
    public boolean contains(String roomId, String operationId) {
        ConcurrentLinkedDeque<CRDTOperation> history = roomHistories.get(roomId);
        if (history == null) return false;
        for (CRDTOperation op : history) {
            if (op.getOperationId().equals(operationId)) return true;
        }
        return false;
    }

    /**
     * Các thao tác nằm SAU thao tác có mã operationId, theo đúng thứ tự server
     * đã áp dụng.
     *
     * @return danh sách (có thể rỗng), hoặc Optional.empty() nếu không tìm thấy
     *         mã đó (đã bị xóa khỏi lịch sử hoặc không tồn tại)
     */
    public java.util.Optional<List<CRDTOperation>> getOperationsAfter(String roomId, String operationId) {
        ConcurrentLinkedDeque<CRDTOperation> history = roomHistories.get(roomId);
        if (history == null) return java.util.Optional.empty();
        List<CRDTOperation> result = new ArrayList<>();
        boolean found = false;
        for (CRDTOperation op : history) {
            if (found) {
                result.add(op);
            } else if (op.getOperationId().equals(operationId)) {
                found = true;
            }
        }
        return found ? java.util.Optional.of(result) : java.util.Optional.empty();
    }

    /** Mã thao tác mới nhất trong lịch sử phòng, hoặc chuỗi rỗng nếu chưa có. */
    public String getLastOperationId(String roomId) {
        ConcurrentLinkedDeque<CRDTOperation> history = roomHistories.get(roomId);
        CRDTOperation last = history == null ? null : history.peekLast();
        return last == null ? "" : last.getOperationId();
    }

    /**
     * Kiểm tra xem phòng có lịch sử thao tác hay không.
     *
     * @param roomId mã phòng
     * @return true nếu phòng có ít nhất 1 thao tác trong lịch sử
     */
    public boolean hasHistory(String roomId) {
        ConcurrentLinkedDeque<CRDTOperation> history = roomHistories.get(roomId);
        return history != null && !history.isEmpty();
    }

    /**
     * Lấy danh sách tất cả các phòng đang có lịch sử.
     *
     * @return tập hợp mã phòng
     */
    public java.util.Set<String> getActiveRoomIds() {
        return Collections.unmodifiableSet(roomHistories.keySet());
    }
}

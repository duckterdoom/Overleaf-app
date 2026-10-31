package com.overleaf.common.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * =========================================================================
 * M4: HÀNG ĐỢI THAO TÁC OFFLINE PHÍA CLIENT (Offline Operation Queue)
 * =========================================================================
 *
 * Giúp client tiếp tục soạn thảo khi mất mạng và đồng bộ lại khi có mạng.
 *
 * Ý tưởng:
 *  - Mỗi thao tác do người dùng tạo ra được giữ trong danh sách "chưa xác nhận"
 *    cho đến khi server gửi ACK. Nhờ vậy thao tác viết lúc offline, hoặc đã gửi
 *    nhưng rớt mạng trước khi nhận ACK, đều không bị mất.
 *  - Client luôn nhớ mã thao tác cuối cùng đã thấy từ server ("điểm đã thấy").
 *  - Khi có mạng lại, {@link #onReconnected()} tạo thông điệp RESYNC chứa điểm đã
 *    thấy và các thao tác chưa xác nhận. Server sẽ tự ghép chúng với những gì
 *    người khác đã sửa trong lúc offline, rồi gửi lại FULL_SYNC (toàn bộ văn bản)
 *    và CHECKPOINT (điểm đã thấy mới).
 *
 * CÁCH DÙNG (cho M1/M3):
 *   1. Mỗi khi người dùng gõ:   msg = queue.prepareEdit(op);  if (msg != null) gửi(msg);
 *   2. Mỗi thông điệp từ server: queue.handleServerMessage(msg);  (rồi xử lý như bình thường)
 *   3. Khi socket đứt:           queue.onConnectionLost();
 *   4. Khi socket nối lại:       gửi(queue.onReconnected());
 *      Trong lúc chờ FULL_SYNC (queue.isResyncing()) nên khóa ô soạn thảo vài trăm
 *      mili giây vì văn bản sẽ bị thay bằng bản từ server.
 *
 * An toàn đa luồng: mọi phương thức đều synchronized (luồng giao diện và
 * luồng mạng cùng gọi).
 */
public class OfflineOperationQueue {

    private final String roomId;
    private final String userId;
    private final String userName;

    /** Thao tác chưa được server xác nhận, theo thứ tự tạo. */
    private final Map<String, CRDTOperation> unacked = new LinkedHashMap<>();

    /** Mã thao tác cuối cùng đã thấy từ server (rỗng = chưa thấy gì). */
    private String lastSeenOperationId = "";

    private boolean online = true;
    private boolean resyncing = false;

    public OfflineOperationQueue(String roomId, String userId, String userName) {
        this.roomId = roomId;
        this.userId = userId;
        this.userName = userName;
    }

    // =========================================================================
    // CLIENT TẠO THAO TÁC
    // =========================================================================

    /**
     * Ghi nhận một thao tác mới của người dùng.
     *
     * @return thông điệp TEXT_EDIT cần gửi ngay, hoặc null nếu đang offline
     *         (thao tác được giữ lại và sẽ gửi khi đồng bộ lại)
     * @throws IllegalStateException nếu đang chờ FULL_SYNC sau khi nối lại
     */
    public synchronized SyncMessage prepareEdit(CRDTOperation op) {
        if (resyncing) {
            throw new IllegalStateException(
                    "Đang đồng bộ lại, cần khóa ô soạn thảo cho đến khi nhận CHECKPOINT");
        }
        unacked.put(op.getOperationId(), op);
        if (!online) {
            return null;
        }
        return SyncMessage.createTextEdit(roomId, userId, userName, OperationCodec.toJson(op));
    }

    // =========================================================================
    // KẾT NỐI
    // =========================================================================

    /** Gọi khi socket bị đứt hoặc không gửi được. */
    public synchronized void onConnectionLost() {
        online = false;
        resyncing = false;
    }

    /**
     * Gọi khi đã nối lại socket. Trả về thông điệp RESYNC cần gửi đầu tiên.
     * (Không cần gửi JOIN_ROOM trước: RESYNC đã bao gồm việc vào lại phòng.)
     */
    public synchronized SyncMessage onReconnected() {
        online = true;
        resyncing = true;
        return new SyncMessage(MessageType.RESYNC, roomId, userId, userName,
                OperationCodec.encodeResync(lastSeenOperationId, unacked.values()));
    }

    // =========================================================================
    // THÔNG ĐIỆP TỪ SERVER
    // =========================================================================

    /** Chuyển mọi thông điệp nhận từ server vào đây để hàng đợi cập nhật trạng thái. */
    public synchronized void handleServerMessage(SyncMessage message) {
        switch (message.getMessageType()) {
            case ACK:
                unacked.remove(message.getPayload());
                lastSeenOperationId = message.getPayload();
                break;
            case TEXT_EDIT:
                // Thao tác của người khác do server chuyển tiếp
                if (!userId.equals(message.getSenderId())) {
                    lastSeenOperationId = OperationCodec.fromJson(message.getPayload()).getOperationId();
                }
                break;
            case CHECKPOINT:
                // Server gửi CHECKPOINT sau khi vào phòng hoặc đồng bộ lại xong => kết nối đang sống
                lastSeenOperationId = message.getPayload();
                online = true;
                resyncing = false;
                online = true;
                break;
            case ERROR:
                // Đồng bộ lại bị từ chối (ví dụ phòng không còn): giữ nguyên thao tác chưa gửi
                if (resyncing) {
                    resyncing = false;
                    online = false;
                }
                break;
            default:
                break;
        }
    }

    // =========================================================================
    // TRUY VẤN
    // =========================================================================

    public synchronized int getPendingCount() { return unacked.size(); }

    public synchronized List<CRDTOperation> getPendingOperations() {
        return new ArrayList<>(unacked.values());
    }

    public synchronized String getLastSeenOperationId() { return lastSeenOperationId; }
    public synchronized boolean isOnline()     { return online; }
    public synchronized boolean isResyncing()  { return resyncing; }
}

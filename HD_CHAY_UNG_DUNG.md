# OVERLEAF DESKTOP - HƯỚNG DẪN KẾT NỐI GUI VỚI MÔ-ĐUN M2 VÀ M4

Nhánh hiện tại: **`feat/m1-gui-client`**  
Mã nguồn GUI JavaFX tại mô-đun **`overleaf-client`** đã được kết nối hoàn chỉnh với:
- **M2 (Core Compiler & File I/O)**: `LatexCompilerService` (ProcessBuilder chạy nền) và `WorkspaceWatcher`.
- **M4 (Collaboration Logic & Resync)**: `OfflineOperationQueue`, `CRDTOperation`, Đồng hồ Lamport, chế độ gõ Offline và cơ chế tự động Resync / Checkpoint khi nối lại mạng.

---

## 1. Nguyên tắc triển khai kết nối theo mục 4 của tài liệu M4

1. **Gõ văn bản (`prepareEdit`)**:
   Khi người dùng gõ vào `codeEditor`, hệ thống tính toán phần sai khác (Diff), tăng đồng hồ Lamport cục bộ và tạo `CRDTOperation` (INSERT hoặc DELETE).
   Sau đó gọi `SyncMessage m = queue.prepareEdit(op);`:
   - Nếu đang Online: Gửi gói tin `TEXT_EDIT` qua socket TCP tới Server.
   - Nếu đang Offline: Thông điệp tự động lưu vào hàng đợi chưa xác nhận (`unacked queue`) để sẵn sàng đồng bộ lại.

2. **Nhận thông điệp (`handleServerMessage`)**:
   Khi có thông điệp đến, luôn gọi `queue.handleServerMessage(msg)` trước:
   - `FULL_SYNC`: Cập nhật toàn bộ nội dung văn bản mới nhất từ server và làm mới bản xem trước PDF.
   - `TEXT_EDIT`: Áp dụng thao tác sửa đổi từ thành viên khác vào đúng vị trí và cập nhật đồng hồ Lamport (`max(local, op.ts)`).
   - `ACK`: Đánh dấu thao tác đã được server xác nhận.
   - `CHECKPOINT`: Cập nhật điểm đã thấy mới nhất và **mở khóa ô soạn thảo** khi `isResyncing()` trở về `false`.
   - `ERROR (ROOM_UNKNOWN)`: Giữ nguyên vẹn văn bản cục bộ, cảnh báo người dùng lưu bản sao trước khi vào lại phòng.

3. **Mất kết nối / Rớt mạng (`onConnectionLost`)**:
   Gọi `queue.onConnectionLost()`. Người dùng vẫn tiếp tục soạn thảo bình thường trên máy tính cá nhân.

4. **Nối lại mạng (`onReconnected`)**:
   Gửi thông điệp `queue.onReconnected()` (chứa loại `RESYNC`, điểm đã thấy `lastSeenOperationId` và toàn bộ thao tác chưa xác nhận). Khóa tạm thời ô soạn thảo cho đến khi nhận `CHECKPOINT`.

---

## 2. Cách chạy thử ứng dụng

### Cách 1: Chạy bằng script tự động
Nhấp đúp chuột vào file **`run-client.bat`** ở thư mục gốc dự án.  
*(Script sẽ tự động biên dịch `overleaf-common` và nạp các thư viện JavaFX 17 có sẵn trong `.m2` để khởi chạy ngay lập tức).*

### Cách 2: Chạy bản Swing dự phòng
Nhấp đúp chuột vào file **`run-client-swing.bat`**.

---

## 3. Các tính năng kiểm thử M4 trực tiếp trên GUI (Menu `Collaboration`)

Trên thanh Menu `Collaboration`, bạn có thể kiểm tra thực tế:
- **`Kết nối phòng (TCP Socket)...`**: Nhập Host, Port, Room ID và Tên hiển thị.
- **`Mất mạng / Chế độ Offline (M4 Simulate Lost)`**: Giả lập tình huống rớt mạng, giao diện chuyển sang trạng thái Offline, cho phép gõ tự do và tích lũy thao tác vào hàng đợi.
- **`Nối lại mạng & Tự động Resync (M4 Reconnect)`**: Giả lập kết nối lại, gửi gói tin RESYNC và đợi CHECKPOINT.
- **`Xem hàng đợi Offline (Pending Operations)`**: Hộp thoại xem chi tiết số lượng thao tác chưa gửi, mã thao tác cuối cùng đã thấy và giá trị đồng hồ Lamport hiện tại.

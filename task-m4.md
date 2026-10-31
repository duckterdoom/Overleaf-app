# M4 Collaboration Logic - Danh sách công việc

## Phase 1: Mô hình dùng chung trong `overleaf-common`
- [x] `CRDTOperation.java`, `DocumentState.java`, `SyncMessage.java`, `MessageType.java`, `CursorPosition.java`, `CollaboratorInfo.java`

## Phase 2: Bộ máy đồng bộ trong `overleaf-server`
- [x] `CRDTEngine.java`, `OperationHistory.java`, `CollaborationThreadPool.java`
- [x] `SyncBroadcaster.java`, `DocumentSyncManager.java` (đã merge qua Pull Request #2)

## Phase 3: Kiểm tra
- [x] Biên dịch toàn bộ bằng `javac --release 17`
- [x] `M4Smoke` chạy đạt (9 kiểm tra)
- [ ] Chạy `mvn clean compile` trên máy của bạn (sandbox không tải được plugin Maven)
- [ ] Chuyển `M4Smoke` và `OfflineResyncSmoke` thành test JUnit 5 để `mvn clean test` chạy được

## Phase 4: Chế độ offline và đồng bộ lại (mục A)
- [x] Thêm 2 loại thông điệp `RESYNC`, `CHECKPOINT`
- [x] `OperationCodec.java` (Common): chuyển JSON dùng chung cho client và server
- [x] `OfflineOperationQueue.java` (Common): hàng đợi thao tác phía client
- [x] `OperationHistory`: thêm `contains`, `getOperationsAfter`, `getLastOperationId`, `wasTrimmed`
- [x] `DocumentSyncManager`: thêm `handleResync`, chống áp dụng trùng, gửi CHECKPOINT khi vào phòng
- [x] `SyncBroadcaster`: sửa lỗi thứ tự gửi (mỗi client một hộp thư đi)
- [x] `OfflineResyncSmoke.java`: 5 tình huống, 26 kiểm tra, tất cả đạt
- [ ] Commit và mở Pull Request cho Phase 4

## Phase 5: Việc chưa làm / phụ thuộc người khác
- [ ] M3 cài `ClientSession` trên socket và gọi `handleMessage`, `handleDisconnect`
- [ ] M1/M2 dùng `OfflineOperationQueue` trong client (xem hướng dẫn trong `walkthrough.md`)
- [ ] Mục B (tua lại lịch sử) và mục C (benchmark): chưa bắt đầu
- [ ] Đưa phần M4 vào báo cáo (đã có bản nháp tiếng Việt)

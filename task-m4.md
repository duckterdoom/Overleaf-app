# M4 Collaboration Logic - Danh sách công việc

## Phase 1: Mô hình dùng chung trong `overleaf-common`
- [x] `CRDTOperation.java`, `DocumentState.java`, `SyncMessage.java`, `MessageType.java`, `CursorPosition.java`, `CollaboratorInfo.java`

## Phase 2: Bộ máy đồng bộ trong `overleaf-server`
- [x] `CRDTEngine.java`, `OperationHistory.java`, `CollaborationThreadPool.java`
- [x] `SyncBroadcaster.java`, `DocumentSyncManager.java` (đã merge qua Pull Request #2)

## Phase 3: Kiểm tra
- [x] Biên dịch toàn bộ bằng `javac --release 17`
- [x] Test JUnit 5: `M4CollaborationTest` (6 test), `OfflineResyncTest` (5 test), `M4TestSupport` (lớp hỗ trợ)
- [ ] Chạy `mvn clean compile` trên máy của bạn (sandbox không tải được plugin Maven)
- [x] Thêm `junit-jupiter` và Surefire 3.2.5 vào `overleaf-server/pom.xml`
- [ ] Chạy `mvn clean test` trên máy của bạn (sandbox không tải được JUnit, chỉ kiểm tra logic bằng bộ giả lập)
- [ ] Xóa 2 file cũ `M4Smoke.java` và `OfflineResyncSmoke.java` (đã được thay bằng test JUnit)

## Phase 4: Chế độ offline và đồng bộ lại (mục A)
- [x] Thêm 2 loại thông điệp `RESYNC`, `CHECKPOINT`
- [x] `OperationCodec.java` (Common): chuyển JSON dùng chung cho client và server
- [x] `OfflineOperationQueue.java` (Common): hàng đợi thao tác phía client
- [x] `OperationHistory`: thêm `contains`, `getOperationsAfter`, `getLastOperationId`, `wasTrimmed`
- [x] `DocumentSyncManager`: thêm `handleResync`, chống áp dụng trùng, gửi CHECKPOINT khi vào phòng
- [x] `SyncBroadcaster`: sửa lỗi thứ tự gửi (mỗi client một hộp thư đi)
- [x] `OfflineResyncTest.java`: 5 tình huống, tất cả đạt
- [ ] Commit và mở Pull Request cho Phase 4

## Phase 5: Benchmark (mục C)
- [x] `BenchmarkM4.java`: 4 phép đo (một phòng, nhiều phòng, phát cho nhiều người nhận, đồng bộ lại sau offline)
- [x] Chạy thử, cả 4 phép đo đều đúng dữ liệu (số liệu mẫu trong `benchmark-results.md`)
- [ ] Chạy lại trên máy của nhóm và thay số liệu trong báo cáo
- [ ] Commit `BenchmarkM4.java` cùng Pull Request của Phase 4

## Phase 6: Việc chưa làm / phụ thuộc người khác
- [ ] M3 cài `ClientSession` trên socket và gọi `handleMessage`, `handleDisconnect`
- [ ] M1/M2 dùng `OfflineOperationQueue` trong client (xem hướng dẫn trong `walkthrough.md`)
- [ ] Mục B (tua lại lịch sử): chưa bắt đầu
- [ ] Đưa phần M4 vào báo cáo (đã có bản nháp tiếng Việt)

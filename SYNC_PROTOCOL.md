# Hợp đồng đồng bộ M3/M4 — wire v2

Cập nhật 01/11/2026. Áp dụng cho Common, TCP server, client console và JavaFX trong workspace hiện tại. Cần build lại và cập nhật **cả client lẫn server** khi triển khai phiên bản này. Không dùng client JAR v1 với server JAR v2.

Trong lần build này, JAR root `dist/overleaf-server.jar` đang được tiến trình cũ sử dụng, nên bản mới được đóng gói tại `dist/v2/overleaf-server.jar` và `dist/v2/overleaf-network-demo.jar`. Sau khi dừng server cũ bằng Ctrl+C, chạy `java -jar dist/v2/overleaf-server.jar 8080`. Khởi động lại JavaFX từ output Maven mới hoặc chạy demo mới bằng `java -jar dist/v2/overleaf-network-demo.jar 127.0.0.1 8080 alice demo`. Script `.bat` cũ vẫn trỏ đến JAR root; không dùng chúng cho lần chuyển phiên bản này trước khi thay artifact root.

Có thể build sang thư mục khác bằng `powershell -NoProfile -ExecutionPolicy Bypass -File .\build-network.ps1 -JavaHome 'C:\path\jdk' -OutputDirectory .\dist\v2`.

## 1. Frame

Big-endian, `[int32 bodyLength][body]`. Body tối đa 1 MiB:

```text
int32 version = 2
string messageType
string roomId
string senderId
string senderName
string payload
int64 timestamp           # thời gian thực, milliseconds
int64 lamportTimestamp    # clock logic của server, không phải timestamp ở trên
```

String là `[int32 UTF-8 byteLength][bytes]`. Codec đọc được v1 bằng cách gán clock bằng 0; khi ghi luôn dùng v2. Khả năng đọc v1 này không thay thế yêu cầu cập nhật cả hai đầu, vì decoder v1 cũ không đọc được response v2.

AUTH(token) vẫn là bước đầu tiên trên TCP/TLS, sau đó JOIN hoặc RESYNC. AUTH ACK chỉ được NetworkClient xử lý nội bộ, không đưa vào offline operation queue.

## 2. Lamport và snapshot

`FULL_SYNC`, `CHECKPOINT`, ACK và các edit được server phát có clock logic của engine. Client JavaFX và demo cập nhật `local = max(local, message.lamportTimestamp)` trước khi xử lý nội dung. Khi tạo operation mới, tăng clock rồi gắn vào operation như quy ước M4 cũ.

Payload FULL_SYNC vẫn là nguyên văn nội dung; payload CHECKPOINT vẫn là operation ID. Không nhét metadata vào văn bản hoặc thay định dạng checkpoint. Nhờ vậy người mới JOIN không transform lại các operation đã có trong snapshot của mình.

## 3. RESYNC lớn

`OfflineOperationQueue.onReconnected()` vẫn trả một SyncMessage RESYNC duy nhất theo định dạng OperationCodec cũ. NetworkClient tự chia payload dài hơn 64 Ki UTF-16 code units thành:

```text
RESYNC_BEGIN  payload = tổng số byte UTF-8 của payload RESYNC
RESYNC_PART   payload = từng phần nguyên văn, không cắt đôi surrogate pair
RESYNC_PART   ...
RESYNC_END    payload = chuỗi rỗng
```

Mỗi phần phải có cùng room/user/name. Mỗi kết nối chỉ có một upload đang ghép; không cho interleave message khác giữa BEGIN và END. Writer gửi toàn bộ upload theo FIFO như một công việc, nên heartbeat không chen vào giữa các phần.

Server chỉ gọi M4 khi đã nhận đủ END và số byte khớp. Upload bị cắt/mất kết nối không áp dụng một phần tài liệu. M4 nhận toàn bộ pending trong **một lần RESYNC**, tránh lỗi transform nếu tự chia pending thành các batch nghiệp vụ độc lập.

Giới hạn: tổng upload 4 MiB UTF-8, tối đa 128 phần và 30.000 operation; document vẫn chịu giới hạn `OVERLEAF_MAX_DOCUMENT_CHARS`. Vượt giới hạn không xóa pending client. Đây là giới hạn tài nguyên có chủ đích, không phải offline queue không giới hạn có thể upload vô hạn.

## 4. ACK cho offline upload

RESYNC không quá 64 operation vẫn dùng ACK từng operation. Với upload lớn, M4 dùng `ACK_BATCH`, payload là các operation ID phân cách newline, tối đa 2048 ID/message. Queue client chỉ xóa các ID đã được xác nhận, và cuối cùng nhận FULL_SYNC/CHECKPOINT như trước.

ACK batch giảm lượng message dồn vào outbox của chính người reconnect. Broadcast edit cho các thành viên khác vẫn dùng cơ chế M4 hiện có; client chậm vẫn có thể bị ngắt bởi backpressure và cần reconnect. ACK vẫn xác nhận trạng thái RAM, chưa phải ghi bền vững xuống MongoDB.

## 5. Phiên GUI và mã lỗi

Mỗi lần kết nối/đổi phòng có generation riêng trong `ConnectionScope`. Callback nhận dữ liệu kiểm tra generation, room ID và instance NetworkClient **khi callback thực sự chạy trên JavaFX thread**. Kết quả connect task cũ cũng bị bỏ và đóng socket thay vì gán đè client mới.

Không tồn tại phòng khi RESYNC: server trả `ERROR` có payload chính xác `ROOM_UNKNOWN`. Queue giữ pending và văn bản local; GUI cho người dùng lưu bản sao. Các validation error khác vẫn có tiền tố `INVALID_REQUEST:`.

## 6. Kiểm tra

```powershell
mvn "-Dtest=MessageCodecTest,ResyncTransferTest,ConnectionScopeTest,NetworkingTest,NetworkClientTest,M4CollaborationTest,OfflineResyncTest,ProjectFileManagerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

42 test đã pass trên JDK 21, compile networking với target Java 17. Bao gồm late JOIN vào tài liệu Hello rồi chèn dấu `>` đúng đầu văn bản, callback cũ không cập nhật phòng mới, 1.200 offline operation với payload trên 1 MiB, replay không trùng, UTF-8 ở ranh giới chunk, upload thiếu/sai/vượt giới hạn, batch ACK và lỗi ROOM_UNKNOWN.

Luồng routing server vẫn dùng khóa chung như trước; thay đổi này chưa tối ưu throughput giữa các phòng. Nội dung server và pending client vẫn nằm trong RAM. Chưa thực hiện triển khai VPS hoặc kiểm thử thao tác GUI thủ công.

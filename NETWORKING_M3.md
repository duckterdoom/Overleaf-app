# Member 3 — Networking Backend

Phần triển khai theo kiến trúc Pure Java/Maven trong `overleaf_desktop_ver2.md` và `overleaf_desktop_pure_java_project_report.md`. Blueprint là tham khảo sản phẩm; phần networking này dùng TCP, không dùng WebSocket/Yjs.

## Phạm vi và mã nguồn

| Thành phần | Vai trò |
| --- | --- |
| `overleaf-common/.../collab/MessageCodec.java` | Mã hóa/giải mã frame nhị phân, UTF-8, kiểm tra kích thước |
| `overleaf-server/.../server/OverleafServer.java` | ServerSocket, giới hạn 64 kết nối, định tuyến phòng, đóng server |
| `overleaf-server/.../server/ServerSession.java` | Reader/writer riêng cho từng socket, hàng đợi gửi tối đa 32 frame, dọn session |
| `overleaf-server/.../server/ServerApp.java` | Main, cổng mặc định 8080, shutdown hook |
| `overleaf-client/.../client/NetworkClient.java` | API transport dùng chung cho GUI/console |
| `overleaf-client/.../client/NetworkDemo.java` | Demo console để kiểm tra phần M3 độc lập GUI |

Tái sử dụng `Room`/`RoomManager` của M4. Khóa tại server giữ thứ tự xử lý membership, snapshot và broadcast; không sửa thuật toán collaboration của M4. Mỗi session có một reader và một writer trong executor tối đa `2 * maxClients` worker. Client chậm làm đầy hàng đợi sẽ bị ngắt để không chặn các phòng khác. Queue lưu frame đã mã hóa để tránh thay đổi POJO sau khi gửi.

## Build và demo trên Windows

Cần JDK 17 trở lên. Đặt `JAVA_HOME` đến thư mục JDK, hoặc thêm `java` và `javac` vào PATH.

```powershell
# Từ thư mục dự án; chỉ build common/server/client, không cần MongoDB hoặc Maven.
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-network.ps1
# Có thể chỉ rõ JDK:
# powershell -NoProfile -ExecutionPolicy Bypass -File .\build-network.ps1 -JavaHome 'C:\path\to\jdk'

# Terminal 1
.\run-server.bat 8080

# Terminal 2
.\run-network-demo.bat 127.0.0.1 8080 alice project-demo

# Terminal 3
.\run-network-demo.bat 127.0.0.1 8080 bob project-demo
```

Nhập `/text Hello LaTeX` ở Alice: cả Alice và Bob nhận `TEXT_UPDATE`. `/text` gửi **toàn bộ nội dung**, thay thế nội dung cũ. Nhập `/cursor 2 10`: Bob nhận vị trí con trỏ, Alice không nhận lại. `/chat Xin chào` gửi tin nhắn đến cả phòng. `/quit` rời phòng. Chạy client thứ ba vào phòng khác để kiểm tra cách ly. Tên người dùng phải khác nhau trong cùng một phòng.

Người mới vào nhận lần lượt `JOIN_ROOM` (content là session ID), `TEXT_UPDATE` (snapshot hiện tại), `USER_LIST`. Thành viên hiện có nhận `USER_LIST` mới. Đóng client cũng xóa presence. Snapshot phòng rỗng được giữ trong RAM cho lần kết nối sau và mất khi server tắt.

## Hợp đồng giao thức cho M1/M4

Frame: `[int32 bodyLength][body]`, big-endian; `bodyLength` phải từ 1 đến 1,048,576 byte. Body v1 gồm:

```text
int32 version (=1)
string type (tên MessageType, không dùng ordinal)
string sender
string roomId
string content
int32 line
int32 column
int64 timestamp (milliseconds)
```

Mỗi string là `[int32 UTF-8 byteLength][bytes]`; `-1` nghĩa là null. Không dùng `ObjectInputStream`/`ObjectOutputStream`. TCP có thể chia/gộp packet; codec đọc đủ frame, từ chối frame bị cắt, version/type sai, UTF-8 lỗi hoặc byte dư. Gói tin sai cấu trúc làm ngắt session đó. Yêu cầu sai nghiệp vụ trả `ERROR`, giữ kết nối.

| Client gửi | Server xử lý |
| --- | --- |
| `JOIN_ROOM` | Xác thực tên/room không trống, tối đa 128 ký tự, không có ký tự điều khiển; chuyển phòng và gửi snapshot |
| `LEAVE_ROOM` | Rời phòng, phát lại danh sách cho người còn lại |
| `TEXT_UPDATE` | Lưu snapshot và broadcast cho cả người gửi theo thứ tự server nhận |
| `CURSOR_MOVE` | Line/column không âm; broadcast cho người khác cùng phòng |
| `CHAT_MESSAGE`, `COMPILE_NOTIFY` | Broadcast cho cả phòng |
| `USER_LIST` | Trả tên thành viên sắp xếp, phân cách bằng newline |
| `COMPILE_REQUEST`, `ERROR` | Trả lỗi vì chưa có handler nghiệp vụ tương ứng |

Trừ JOIN, yêu cầu phải ghi đúng room đang tham gia. Server lấy sender từ session, không tin sender trong payload. Tên chỉ là định danh phiên demo, chưa có xác thực tài khoản/quyền dự án.

Ví dụ tích hợp GUI (khởi tạo và gọi `send` trong worker, không chạy trên EDT):

```java
NetworkClient client = new NetworkClient("127.0.0.1", 8080,
    message -> SwingUtilities.invokeLater(() -> handleRemoteMessage(message)),
    error -> SwingUtilities.invokeLater(() -> showDisconnected(error)));
client.send(new SyncMessage(MessageType.JOIN_ROOM, "alice", "project-demo", null));
// Chờ JOIN_ROOM/snapshot trước khi cho phép chỉnh sửa.
client.send(new SyncMessage(MessageType.TEXT_UPDATE, "alice", "project-demo", fullText));
// Khi đóng cửa sổ:
client.close();
```

Callback chạy trên receiver thread. Khi áp dụng nội dung remote, M1 cần chặn document listener phát lại update để tránh vòng lặp. `NetworkClient` dùng một kết nối cho mỗi instance; muốn kết nối lại thì tạo instance mới. `onDisconnect` nhận null khi peer đóng bình thường; đóng chủ động bằng `close()` không gọi callback.

## Kiểm thử

JUnit 5: codec round trip Unicode/null/text dài, nhiều frame liên tiếp, TCP chia nhỏ dữ liệu, frame lỗi; integration bằng socket loopback thật: snapshot/broadcast/presence, cách ly phòng, giả mạo sender, tên trùng, cursor, chuyển phòng, cập nhật đồng thời, giới hạn kết nối, shutdown.

```powershell
mvn -pl overleaf-common,overleaf-server -am test
```

Lệnh Maven trên cũng chạy test database do server đã phụ thuộc module database trong cấu trúc ban đầu. Test networking không mở MongoDB.

## Ranh giới bàn giao

Đây là transport và đồng bộ snapshot theo thứ tự server nhận. Hai người sửa đồng thời có thể ghi đè nội dung nhau; M4 cần bổ sung revision/OT/CRDT và điều chỉnh message contract nếu muốn gộp chỉnh sửa. M2 phụ trách compile thực tế; `COMPILE_NOTIFY` chỉ là thông báo. M5 phụ trách authentication, authorization và lưu dữ liệu bền vững. GUI gốc và `ClientApp` chưa nối vào transport; dùng console demo để chạy phần M3 ngay.

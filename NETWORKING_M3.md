# M3 — TCP backend tích hợp main

Cập nhật 01/11/2026, dựa trên `main` commit `df06601`. Phần cũ trên nhánh `protocol_networking` dùng model `common.collab`; bản này dùng **`com.overleaf.common.sync` của M4**, không tương thích wire với client M3 cũ.

## Chạy ngay trên máy local

Cần **JDK đầy đủ 17+**, gồm `java`, `javac`, `jar`. JBR của IntelliJ trên máy hiện tại thiếu `jar.exe`; có thể dùng JDK Microsoft đã cài:

```powershell
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-21.0.13.0.2'
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-network.ps1
.\run-server.bat 8080
```

Ở hai terminal khác, đặt `JAVA_HOME` tương tự rồi chạy:

```powershell
.\run-network-demo.bat 127.0.0.1 8080 alice demo
.\run-network-demo.bat 127.0.0.1 8080 bob demo
```

Mỗi dòng trên chạy ở một terminal riêng. Nhập `/insert 0 Hello` ở Alice: Alice nhận `ACK`, Bob nhận `TEXT_EDIT`. Client vào sau nhận `FULL_SYNC` chứa `Hello`. Các lệnh khác: `/delete 0 Hello`, `/cursor 2 7`, `/quit`. Demo hiển thị payload và Lamport clock, không phải editor đầy đủ; người dùng chọn vị trí theo nội dung hiện tại.

Trên Linux/macOS có JDK:

```sh
sh build-network.sh
java -jar dist/overleaf-server.jar
java -jar dist/overleaf-network-demo.jar 127.0.0.1 8080 alice demo
```

Hai JAR độc lập không cần Maven, JavaFX, MongoDB hoặc TeX trên máy server. `build-network.*` chỉ biên dịch Common, Server và client transport/demo. Để biên dịch GUI và database, dùng `mvn compile`; các script build GUI có sẵn vẫn thuộc luồng build của M1.

## Phân lớp

| Lớp | Vai trò |
| --- | --- |
| Common `MessageCodec` | Frame có tiền tố độ dài, UTF-8 chặt chẽ, version và giới hạn kích thước |
| Common `TlsContexts` | Nạp keystore/truststore PKCS12 bên ngoài JAR |
| Server `ServerConfig` | Đọc cấu hình môi trường, kiểm tra giới hạn và external bind |
| Server `ServerApp` | Main, bind, báo lỗi startup, shutdown hook |
| Server `OverleafServer` | Accept socket, AUTH, membership, kiểm tra yêu cầu, gọi M4 |
| Server `ServerSession` | Adapter `SyncBroadcaster.ClientSession`, reader/writer và queue giới hạn |
| Client `net.NetworkClient` | Kết nối, TLS/hostname verification, AUTH, queue gửi và heartbeat |
| Client `net.NetworkDemo` | Demo console/headless kiểm tra server |
| M4 `DocumentSyncManager.processMessage` | Điểm gọi đồng bộ để giữ thứ tự JOIN → edit → LEAVE trên TCP |

Networking tuần tự hóa routing dưới một khóa để membership và giới hạn document nhất quán; đây là lựa chọn đơn giản cho server nhóm nhỏ. M4 vẫn dùng khóa phòng và outbox riêng để broadcast theo thứ tự. `handleMessage` bất đồng bộ cũ vẫn được giữ cho các caller/test hiện có.

## Wire protocol v1

```text
int32 bodyLength                 # 1..1,048,576 bytes
  int32 version                  # 1
  string messageType             # enum name
  string roomId
  string senderId
  string senderName
  string payload
  int64 timestamp                # milliseconds
```

Big-endian. String là `[int32 byteLength][UTF-8 bytes]`, không nullable; model chuẩn hóa name/payload null thành chuỗi mặc định trước khi encode. Codec xử lý TCP chia/gộp packet, từ chối chiều dài sai, version/type lạ, UTF-8 lỗi, byte dư hoặc frame bị cắt. Không dùng Java object deserialization.

**Frame đầu tiên luôn là `AUTH`**, payload chứa token (có thể rỗng trên loopback). Server trả `ACK` với payload `AUTHENTICATED`; sai token hoặc thiếu AUTH sẽ đóng kết nối. Đây là access token chung của nhóm, chưa phải đăng nhập từng tài khoản.

| Message client gửi | Hành vi |
| --- | --- |
| `JOIN_ROOM` | Đặt room/identity; M4 gửi `FULL_SYNC`, `CHECKPOINT`, `USER_LIST_UPDATE` |
| `RESYNC` | Khôi phục membership và áp dụng pending ops qua M4; không cần JOIN trước; phòng không tồn tại trả lỗi chứa `ROOM_UNKNOWN` |
| `TEXT_EDIT` | Payload từ `OperationCodec.toJson`; server kiểm tra user trong op, vị trí không âm, ID và Lamport; M4 biến đổi/áp dụng, ACK và phát cho người khác |
| `CURSOR_UPDATE` | Payload `line,column,fileName`; line/column không âm |
| `HEARTBEAT` | Cập nhật M4, trả `HEARTBEAT` có payload `pong` |
| `LEAVE_ROOM` | Dọn membership/presence; socket vẫn mở để có thể join tiếp |

`ACK`, `FULL_SYNC`, `CHECKPOINT`, `USER_LIST_UPDATE`, `ERROR` là server → client; client gửi các kiểu này bị từ chối. Không còn `TEXT_UPDATE`, `CHAT_MESSAGE`, `COMPILE_NOTIFY` của giao thức M3 cũ.

User ID được cố định sau lần join đầu của một kết nối. ID/room/name không trống, tối đa 128 ký tự, không có ký tự điều khiển hoặc `|`. Không cho hai kết nối cùng user ID vào cùng phòng. Mọi request sau join phải đúng room và sender ID của session; tên hiển thị do server lấy từ session. User ID vẫn là tên tự khai báo bởi người giữ access token, chưa được xác minh với MongoDB.

## Cấu hình

| Biến môi trường | Mặc định / ý nghĩa |
| --- | --- |
| `OVERLEAF_HOST` | `127.0.0.1` |
| `OVERLEAF_PORT` | `8080`; đối số port của main được ưu tiên |
| `OVERLEAF_MAX_CLIENTS` | `64`, hợp lệ 1..1024 |
| `OVERLEAF_IDLE_TIMEOUT_MS` | `60000`; socket read timeout, tính cả AUTH/TLS |
| `OVERLEAF_QUEUE_CAPACITY` | `32` frame/session ở server |
| `OVERLEAF_MAX_DOCUMENT_CHARS` | `100000` UTF-16 code units, tối đa cấu hình 200000 |
| `OVERLEAF_TOKEN` | Rỗng ở loopback; nếu có phải dài ít nhất 24 ký tự |
| `OVERLEAF_TLS` | `false`, chỉ nhận `true` hoặc `false` ở server main |
| `OVERLEAF_KEYSTORE` | Đường dẫn PKCS12 chứa server private key và certificate; bắt buộc cho main khi TLS bật |
| `OVERLEAF_KEYSTORE_PASSWORD` | Mật khẩu keystore |
| `OVERLEAF_TRUSTSTORE` | Phía client: PKCS12 truststore tùy chọn; nếu bỏ trống dùng CA mặc định JVM |
| `OVERLEAF_TRUSTSTORE_PASSWORD` | Mật khẩu truststore client |

Bind ngoài loopback bắt buộc TLS và token. Tối đa 256 phòng trong RAM. RESYNC tối đa 1000 thao tác/request. Giới hạn tài liệu dùng cận trên bảo thủ: tổng insert trong batch được cộng trước, không trừ delete. Một frame tối đa 1 MiB bao gồm metadata; nội dung quá lớn bị từ chối. Client gửi heartbeat mỗi 15 giây; nên giữ timeout server ít nhất 45–60 giây khi dùng client này.

Server ngắt client nếu queue gửi đầy. Outbox M4 trong TCP backend cũng được giới hạn 128 message/session (constructor M4 mặc định dùng 1024 để hỗ trợ burst hiện có). Client có queue 64 frame; khi đầy, GUI chuyển về offline, giữ pending operations trong queue M4. Client gửi theo FIFO; tránh tạo một thread riêng cho mỗi lần gõ như code GUI trước đây. Socket đọc và ghi riêng, network không chạy trên JavaFX Application Thread.

## GUI JavaFX

GUI hiện dùng `net.NetworkClient`, có trường token và checkbox TLS trong hộp thoại kết nối. Nó giữ luồng xử lý M4 cho `FULL_SYNC`/`TEXT_EDIT`/ACK/CHECKPOINT, khóa editor trong lúc join/resync và hiển thị danh sách thành viên từ server. Bản Swing cũ chưa được nối transport này.

JOIN mới lấy bản server thay nội dung editor; lưu file local trước khi đổi phòng hoặc tạo một phiên JOIN mới. Để gửi lại chỉnh sửa đang chờ của cùng phòng, dùng luồng reconnect/RESYNC thay vì JOIN mới.

Khởi tạo client trong worker, gọi `start(onMessage, onDisconnect)` để nhận. `send` đưa frame vào queue nên không chờ socket write. Callback chạy trên receiver/writer thread; GUI chuyển sang `Platform.runLater`. Đóng chủ động không gọi disconnect callback. Sau khi mất kết nối, tạo client mới rồi gửi `operationQueue.onReconnected()` để RESYNC. GUI có thao tác reconnect, chưa có retry tự động.

## Kiểm thử

```powershell
# Maven, không chạy test MongoDB:
mvn "-Dtest=MessageCodecTest,NetworkingTest,NetworkClientTest,M4CollaborationTest,OfflineResyncTest,ProjectFileManagerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test

# Hoặc JUnit 5.10.2 có sẵn trong cache .m2 (không cần tải Maven/JavaFX):
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-network.ps1 -JavaHome 'C:\path\to\jdk'
```

Test script dùng JUnit thật, tạo certificate TLS test tạm và kiểm tra hostname/token. Maven dùng JUnit theo POM dự án (5.10.1). Test MongoDB hiện có cần cấu hình riêng và không nằm trong lệnh chọn lọc này.

## Triển khai

Xem [DEPLOY_SERVER.md](DEPLOY_SERVER.md) để chạy JAR trên VPS qua SSH tunnel hoặc TLS public, và cài service systemd. Đây là TCP service, không phải HTTP website.

**Giới hạn dữ liệu:** server hiện giữ nội dung/lịch sử trong RAM, chưa nối `DatabaseService`. Không bật maintenance M4 vì hàm đó xóa phòng trống và lịch sử; socket timeout M3 đảm nhiệm việc dọn kết nối. Phòng rỗng được giữ đến khi JVM tắt. Restart mất trạng thái server; client RESYNC sau restart nhận `ROOM_UNKNOWN`. Cần lưu file local và bổ sung persistence trước khi dùng làm nơi lưu tài liệu lâu dài. Queue offline của GUI cũng chỉ nằm trong RAM của tiến trình client.

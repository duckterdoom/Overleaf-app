# AI Handoff — Member 3: Networking Backend

Ngày cập nhật: 30/10/2026.

## 1. Yêu cầu và trạng thái

Đã đọc ba tài liệu `overleaf_desktop_ver2.md`, `overleaf_desktop_pure_java_project_report.md` và `overleaf-desktop-blueprint.md`, sau đó triển khai phần **M3: Java TCP Sockets, ServerSession, Broadcast Sync** trong cấu trúc Maven hiện có.

Phần networking có thể build và chạy độc lập bằng demo console. GUI chưa tích hợp networking. Đồng bộ hiện tại dùng snapshot toàn bộ nội dung, chưa có OT/CRDT.

Hướng dẫn kỹ thuật và hợp đồng giao thức chi tiết: [NETWORKING_M3.md](NETWORKING_M3.md).

## 2. Các file đã thêm

| File | Nội dung đã code |
| --- | --- |
| `overleaf-common/src/main/java/com/overleaf/common/collab/MessageCodec.java` | Codec nhị phân có tiền tố độ dài, version 1, chuỗi UTF-8 nullable; kiểm tra kích thước, version, loại message, UTF-8, frame thiếu và byte dư. |
| `overleaf-server/src/main/java/com/overleaf/server/OverleafServer.java` | TCP server, accept loop, giới hạn kết nối, quản lý session, xử lý join/leave, kiểm tra membership và broadcast trong phòng. |
| `overleaf-server/src/main/java/com/overleaf/server/ServerSession.java` | Một reader và một writer cho mỗi socket; queue gửi có giới hạn; đóng socket và dọn presence khi mất kết nối. |
| `overleaf-client/src/main/java/com/overleaf/client/NetworkClient.java` | Kết nối TCP với timeout 5 giây, gửi message, nhận nền qua callback, đóng kết nối. |
| `overleaf-client/src/main/java/com/overleaf/client/NetworkDemo.java` | Demo console hỗ trợ `/text`, `/cursor`, `/chat`, `/quit`. |
| `overleaf-common/src/test/java/com/overleaf/common/collab/MessageCodecTest.java` | 3 test JUnit cho codec và xử lý frame lỗi. |
| `overleaf-server/src/test/java/com/overleaf/server/OverleafServerTest.java` | 6 test JUnit dùng socket loopback thật cho server. |
| `build-network.ps1` | Build common/server/client bằng `javac --release 17`, xuất vào `bin/`, không cần Maven hoặc MongoDB. |
| `run-network-demo.bat` | Chạy demo console với host, port, username, room; tự build nếu thiếu class demo. |
| `NETWORKING_M3.md` | Hướng dẫn build, demo, message contract, tích hợp GUI và ranh giới trách nhiệm. |
| `AI_Handoff.md` | Bản ghi bàn giao này. |

## 3. Các file đã sửa

| File | Thay đổi |
| --- | --- |
| `overleaf-common/src/main/java/com/overleaf/common/collab/SyncMessage.java` | Thêm `setTimestamp(long)` phục vụ decode; cập nhật mô tả transport. Vẫn giữ `Serializable` để tương thích model, nhưng networking không dùng Java object serialization. |
| `overleaf-server/src/main/java/com/overleaf/server/ServerApp.java` | Thay main placeholder bằng khởi chạy server thật, nhận port tùy chọn, shutdown hook và báo lỗi khởi động. |
| `overleaf-common/pom.xml` | Thêm dependency JUnit Jupiter ở test scope. |
| `overleaf-server/pom.xml` | Thêm dependency JUnit Jupiter ở test scope. |
| `pom.xml` | Thêm Maven Surefire Plugin 3.2.5 để chạy JUnit 5. |
| `run-server.bat` | Dùng thư mục script làm working directory, build networking nếu thiếu class, hỗ trợ `JAVA_HOME` và port truyền vào. |

Tái sử dụng `Room` và `RoomManager` có sẵn của M4, không sửa hai lớp này. Không sửa GUI gốc, `ClientApp`, thuật toán collaboration, compiler hoặc database.

## 4. Hành vi đã triển khai

- Server mặc định cổng **8080**, tối đa **64 client**. Có constructor cấu hình giới hạn và truyền `RoomManager`; port `0` dùng để hệ điều hành chọn cổng khi test.
- Một session chỉ tham gia một phòng tại một thời điểm. Join phòng mới sẽ rời phòng cũ.
- Username và room ID phải không trống, dài tối đa 128 ký tự và không chứa ký tự điều khiển. Username không được trùng trong cùng phòng.
- Khi join thành công, client nhận lần lượt `JOIN_ROOM` chứa session ID, `TEXT_UPDATE` chứa snapshot, rồi `USER_LIST`.
- `TEXT_UPDATE` thay toàn bộ nội dung phòng và broadcast cho cả người gửi.
- `CURSOR_MOVE` yêu cầu line/column không âm và chỉ gửi cho các client khác cùng phòng.
- `CHAT_MESSAGE` và `COMPILE_NOTIFY` broadcast cho cả phòng.
- `USER_LIST` trả danh sách username đã sắp xếp, phân cách bằng newline.
- Leave hoặc ngắt kết nối sẽ xóa presence và gửi danh sách thành viên mới cho phòng cũ.
- Sau join, server lấy sender từ session; client không thể đổi sender bằng payload. Yêu cầu phải ghi đúng room đang tham gia.
- Gói tin sai cấu trúc làm đóng session đó; lỗi nghiệp vụ trả `ERROR`. `COMPILE_REQUEST` chưa có handler và trả lỗi.
- Khóa tại server giữ thứ tự xử lý join/snapshot/update/broadcast. Đây chưa phải cơ chế gộp chỉnh sửa đồng thời.
- Mỗi session có queue tối đa **32 frame**. Client bị ngắt nếu queue đầy; việc ghi socket chạy trên writer riêng.
- Nội dung phòng được giữ trong RAM kể cả khi phòng rỗng, mất khi server tắt.

## 5. Quyết định kỹ thuật cần giữ khi tích hợp

Wire protocol là `[int32 bodyLength][body]`, big-endian, tối đa **1 MiB cho body**. Body chứa version, tên enum message, sender, room ID, content, line, column và timestamp. Chuỗi dùng độ dài byte UTF-8; `-1` biểu diễn null. Xem thứ tự trường chính xác trong `NETWORKING_M3.md` và `MessageCodec`.

Hai đầu phải dùng cùng codec. Không thay một đầu sang `ObjectInputStream`/`ObjectOutputStream` vì sẽ không tương thích giao thức hiện tại.

Session ID là số tăng dần trong JVM, chỉ dùng định danh kết nối, không phải token đăng nhập. Ban đầu dùng UUID nhưng lần nhận kết nối đầu tiên chờ nguồn ngẫu nhiên trong môi trường kiểm thử; đã đổi sang `AtomicLong`.

Callback của `NetworkClient` chạy trên receiver thread. GUI phải chuyển cập nhật UI qua `SwingUtilities.invokeLater`; kết nối và gửi dữ liệu cần chạy ngoài EDT. Khi áp dụng nội dung remote, cần chặn listener gửi lại chính nội dung đó để tránh vòng lặp.

Đóng `NetworkClient` chủ động không gọi `onDisconnect`. Nếu peer đóng bình thường, callback nhận null; nếu có lỗi, callback nhận exception. Kết nối lại cần tạo instance mới.

## 6. Kiểm tra đã thực hiện

| Kiểm tra | Kết quả |
| --- | --- |
| Build common/server/client với `javac --release 17 -encoding UTF-8` | Thành công trên JBR/JDK 21.0.6, xuất bytecode tương thích Java 17. Chưa chạy runtime Java 17 riêng. |
| JUnit networking | **9/9 test pass**, chạy qua JUnit Platform Launcher với thư viện JUnit 5.10.2 có sẵn trong máy. |
| Smoke test `NetworkClient` | Hai instance API client kết nối server thật, join cùng phòng và truyền `TEXT_UPDATE` thành công. |
| `git diff --check` | Pass ở lần kiểm tra sau khi triển khai M3. |

Các test bao gồm Unicode/null/nội dung dài, nhiều frame liên tiếp, dữ liệu TCP chia nhỏ, độ dài lỗi, frame bị cắt, version sai, byte dư; snapshot/presence/broadcast, cách ly phòng, username trùng, sender giả, room sai, cursor, chuyển phòng, cập nhật đồng thời có cùng thứ tự broadcast, giới hạn kết nối và shutdown.

**Chưa chạy toàn bộ Maven reactor test.** POM hiện khai báo JUnit 5.10.1; lần kiểm chứng trực tiếp dùng 5.10.2 có sẵn trong cache. Không đổi version dependency của dự án để khớp cache máy.

Các helper kiểm thử cục bộ `target/NetworkingTestRunner.java`, `target/NetworkClientSmoke.java`, thư viện copy trong `target/test-libs/` và class trong `bin/` là artifact bị Git ignore, không phải source bàn giao. Test JUnit chính thức nằm trong `src/test/java` của từng module.

## 7. Cách chạy lại

Cần JDK 17 trở lên. Đặt `JAVA_HOME` hoặc thêm `java`/`javac` vào PATH. Môi trường lúc triển khai chưa có các lệnh Java/Maven trên PATH; đã dùng JDK có sẵn tại `D:\IntelliJ IDEA Community Edition 2024.3.5\jbr`.

```powershell
# Từ thư mục gốc dự án:
# Nếu cần, đặt biến này trong từng terminal chạy demo.
$env:JAVA_HOME = 'D:\IntelliJ IDEA Community Edition 2024.3.5\jbr'

powershell -NoProfile -ExecutionPolicy Bypass -File .\build-network.ps1

# Terminal 1
.\run-server.bat 8080

# Terminal 2
.\run-network-demo.bat 127.0.0.1 8080 alice project-demo

# Terminal 3
.\run-network-demo.bat 127.0.0.1 8080 bob project-demo
```

Ở Alice, nhập `/text Hello LaTeX`, `/cursor 2 10` hoặc `/chat Xin chào`; quan sát log ở Bob. Nhập `/quit` để rời phòng.

Nếu Maven đã được cài và cấu hình:

```powershell
mvn -pl overleaf-common,overleaf-server -am test
```

Lệnh này cũng đưa module database vào reactor vì server đã có dependency database từ cấu trúc ban đầu. Test networking không cần MongoDB.

## 8. Việc còn lại và bàn giao cho thành viên khác

| Phụ trách | Việc cần tiếp tục |
| --- | --- |
| M1 — GUI | Nối `NetworkClient` vào màn hình kết nối và editor; xử lý trạng thái join/error/disconnect, snapshot, danh sách thành viên, cursor; tránh gửi lặp khi nhận remote update. |
| M2 — Compiler | Nối kết quả compile thực tế với `COMPILE_NOTIFY`; thống nhất xử lý `COMPILE_REQUEST` nếu cần. |
| M4 — Collaboration | Thêm revision/OT/CRDT và cơ chế xử lý sửa đồng thời; thống nhất lại payload/codec và test hai đầu khi thay giao thức. |
| M5 — Database | Thêm xác thực, quyền truy cập dự án và lưu/khôi phục nội dung phòng. |
| Networking tiếp theo | Cân nhắc heartbeat/idle timeout, tự kết nối lại và kiểm thử tải/client chậm nếu mở rộng phạm vi. |

Giới hạn hiện tại: hai người sửa cùng lúc có thể ghi đè nhau; chưa có đồng bộ nhiều file, lưu bền vững, tài khoản/quyền phòng, TLS, heartbeat hoặc tự reconnect. Username hiện là tên tự khai báo khi join.

## 9. Trạng thái Git tại thời điểm bàn giao

Các thay đổi M3 vẫn ở working tree, **chưa commit hoặc push** trong phiên làm việc này. Hai tài liệu `overleaf-desktop-blueprint.md` và `overleaf_desktop_pure_java_project_report.md` đã xuất hiện dưới dạng untracked trước khi triển khai; đây không phải file do phần M3 tạo mới. Ba tài liệu yêu cầu ban đầu chỉ được đọc, không chỉnh sửa.

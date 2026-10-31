# AI Handoff — Overleaf Desktop / Networking Backend M3

> **Trạng thái mới nhất — đồng bộ M3/M4 ngày 01/11/2026:** đã sửa bốn điểm trong review; xem mục 13 và [SYNC_PROTOCOL.md](SYNC_PROTOCOL.md). 42/42 test pass. Phần mô tả v1, giới hạn RESYNC 1000 và kết quả 35 test ở các mục cũ là lịch sử trước lần sửa này, không còn là hợp đồng hiện tại.

Cập nhật: **01/11/2026**. Nền mã nguồn được kiểm tra: `main`, commit **`df06601`**. Tài liệu này ghi trạng thái code thực tế sau khi tích hợp M3; thay thế handoff ngày 30/10 vốn mô tả nhánh `protocol_networking`.

## 1. Kết quả và phạm vi

Đã đọc lại báo cáo Pure Java, phần M3 trong `overleaf_desktop_ver2.md`, đối chiếu blueprint và kiểm tra code M1/M2/M4/M5 trên `main`. Kiến trúc được áp dụng là Java TCP theo báo cáo, không chuyển sang WebSocket/Yjs của blueprint.

**M3 hiện có backend chạy được bằng JAR, adapter kết nối M4 và client tương thích.** Có thể triển khai listener trên VPS theo [DEPLOY_SERVER.md](DEPLOY_SERVER.md). Chưa đăng nhập VPS, chưa chạy service trên Linux và chưa publish/push code trong phiên này.

Nội dung/lịch sử phòng vẫn ở RAM; chưa nối persistence MongoDB hoặc đăng nhập từng tài khoản. Do đó triển khai được dịch vụ networking cho nhóm, nhưng chưa phải nơi lưu trữ tài liệu bền vững.

Tài liệu liên quan:

- [README.md](README.md): bản đồ từng thư mục/file, chức năng, member phụ trách và cách chạy.

- [NETWORKING_M3.md](NETWORKING_M3.md): build, API, wire protocol, cấu hình và giới hạn.
- [DEPLOY_SERVER.md](DEPLOY_SERVER.md): chuyển JAR lên VPS, SSH tunnel, TLS public, systemd.
- `target/AI_Handoff.before-20261101.md`: bản sao handoff cũ trên máy này, bị Git ignore.

## 2. Các thành viên khác đã code gì trên main?

Đây là phân tích source hiện có; không coi những tính năng chỉ được viết trong báo cáo là đã triển khai.

| Phần | Code đã có trước lần bổ sung này | Tình trạng tích hợp thực tế |
| --- | --- | --- |
| **M1 — GUI** | `ClientApp` chọn JavaFX mặc định hoặc Swing cũ. `OverleafFxApp` có editor, cây file, menu/toolbar, chọn compiler, console, badge online/offline, thao tác reconnect và xử lý message M4. `OverleafDesktopApp` là bản Swing cũ. | JavaFX đã gọi các lớp M2 và `OfflineOperationQueue`. Trước khi bổ sung M3, socket dùng Java Object Stream nên không tương thích codec nhị phân. Preview là Canvas tự phân tích/vẽ nội dung LaTeX, không phải render trực tiếp PDF đầu ra. |
| **M2 — Compiler** | `LatexCompilerService`: gọi compiler nền bằng ProcessBuilder, chọn engine, timeout, thu log, parse lỗi và callback. | Compile thực hiện ở client, không chuyển lên server TCP. Kiểm chứng compile Java không đồng nghĩa đã kiểm chứng TeX/MiKTeX hoặc bản PDF thật. |
| **M2 — I/O** | `ProjectFileManager`: đọc/ghi, tạo/xóa/đổi tên file, liệt kê file, định vị PDF và theo dõi own-write. `WorkspaceWatcher`: giám sát filesystem. Có 8 test file manager. | Có tích hợp trong GUI; một số luồng compile/watch của GUI vẫn dùng working directory `.`. Không thay đổi logic compiler/workspace trong phần M3 này. |
| **M4 — Common** | `CRDTOperation`, `CollaboratorInfo`, `CursorPosition`, `DocumentState`, `MessageType`, `SyncMessage`, `OperationCodec`, `OfflineOperationQueue`. | Payload thao tác đã dùng JSON và có cơ chế giữ pending ops, checkpoint và RESYNC. Queue offline ở RAM, chưa ghi xuống đĩa. |
| **M4 — Server collaboration** | `CRDTEngine`, `OperationHistory`, `CollaborationThreadPool`, `DocumentSyncManager`, `SyncBroadcaster`. Có insert/delete, OT transform, ACK, full sync, cursor, presence, chống áp dụng trùng, replay offline, lịch sử giới hạn và benchmark. | Đây là engine xử lý, trước đó chưa có listener/session TCP thật trên main. Không gọi đó là CRDT đã chứng minh hội tụ cho mọi tình huống; chỉ xác nhận hành vi qua các test đã chạy. |
| **M5 — Database** | Package `com.overleaf.db`: model User/Project/Document, repository interfaces/implementations, cấu hình MongoDB, `DatabaseService`, các test repository/kết nối. | `DatabaseService` có `authenticate`, `loadInitialDocumentContent`, `saveDocumentContent`. Chưa được nối với đường đi M3/M4; authenticate hiện so sánh chuỗi password hash đã truyền vào. Chưa kiểm chứng MongoDB trong lần này. |

`task-m4.md` và `walkthrough-m4.md` là ghi chú lịch sử M4. Một số dòng “chưa commit/chưa có M3/chưa chạy JUnit thật” trong các ghi chú cũ không còn phản ánh trạng thái sau lần tích hợp này; không tự sửa tài liệu của thành viên khác.

## 3. Vì sao không copy nguyên nhánh protocol_networking?

Code M3 cũ nằm ở commit **`14b7d27`**, dùng `com.overleaf.common.collab`, `RoomManager`, `TEXT_UPDATE` và snapshot toàn văn bản. Trên main hiện tại, M4 dùng **`com.overleaf.common.sync`**, `TEXT_EDIT`, `FULL_SYNC`, `CHECKPOINT`, `RESYNC` và `DocumentSyncManager`.

Lần này triển khai transport phù hợp với model M4 đang có. Không đưa lại package `common.collab` hoặc mô hình RoomManager cũ. Frame v1 mới cũng khác layout của frame v1 cũ, nên phải dùng client/server mới cùng nhau.

## 4. M3 đã bổ sung những file nào?

### Common

| File tương đối với `overleaf-common/src/main/java/com/overleaf/common/sync/` | Nội dung |
| --- | --- |
| `MessageCodec.java` | Frame nhị phân length-prefixed, version, UTF-8, năm trường string và timestamp. Giới hạn body 1 MiB; kiểm tra dữ liệu sai, bị cắt hoặc dư. Không deserialize Java object. |
| `TlsContexts.java` | Nạp private key/trust từ PKCS12 bên ngoài JAR qua biến môi trường. Client vẫn kiểm tra hostname. |
| `MessageType.java` | Thêm `AUTH` cho bước xác thực transport. |
| `OfflineOperationQueue.java` | Khi nhận CHECKPOINT, chuyển queue sang online. Sửa lỗi JOIN thành công nhưng queue còn offline nên thao tác gõ không được gửi. |

### Server

Đường dẫn: `overleaf-server/src/main/java/com/overleaf/server/`.

| File | Nội dung |
| --- | --- |
| `ServerConfig.java` | Đọc env/port, kiểm tra cấu hình. Mặc định loopback; bind bên ngoài bắt buộc TLS và token ít nhất 24 ký tự. `toString` không in token. |
| `ServerApp.java` | Main chạy headless, nạp TLS keystore khi cần, bind, startup log và shutdown hook; lỗi startup trả exit code 1. |
| `OverleafServer.java` | Accept TCP, giới hạn client/phòng, AUTH, quản lý identity/membership, kiểm tra request và gọi M4 đồng bộ. |
| `ServerSession.java` | Cài `SyncBroadcaster.ClientSession`: socket reader/writer riêng, queue frame giới hạn, dọn kết nối và presence. |

Sửa hai lớp M4 để transport tích hợp đúng:

- `collaboration/DocumentSyncManager.java`: thêm `processMessage(session, message)` chạy đồng bộ. `handleMessage` bất đồng bộ cũ vẫn giữ nguyên API và gọi lại hàm này. Reader M3 dùng entry point đồng bộ để JOIN/EDIT/LEAVE không vượt nhau.
- `collaboration/SyncBroadcaster.java`: giới hạn outbox; bổ sung constructor nhận capacity và `ClientSession.disconnect()` mặc định để giữ tương thích adapter cũ. Outbox trong TCP backend là 128 message; constructor mặc định M4 là 1024, vẫn hỗ trợ test burst 300 message theo thứ tự. Session TCP đóng khi outbox đầy.

### Client và GUI

| File | Nội dung |
| --- | --- |
| `overleaf-client/.../client/net/NetworkClient.java` | Connect timeout 5 giây, TLS và hostname verification, AUTH trước nhận dữ liệu, FIFO writer queue 64 frame, receiver callback, heartbeat mỗi 15 giây, đóng kết nối. |
| `overleaf-client/.../client/net/NetworkDemo.java` | Console chạy độc lập: join, insert/delete, cursor; in snapshot, operation và ACK để kiểm tra server. |
| `overleaf-client/.../client/gui/OverleafFxApp.java` | Thay Object Stream bằng NetworkClient dùng chung; thêm token/TLS vào hộp thoại; bỏ thread gửi riêng cho mỗi message; hiển thị presence thật thay Alice/Bob giả; khóa editor khi chờ JOIN/RESYNC và mở lại khi CHECKPOINT/ngắt kết nối/lỗi. Sửa việc nhận biết đổi room để tạo queue đúng phòng. |

GUI được compile bằng Maven. Chưa mở GUI để kiểm tra thủ công tương tác hai cửa sổ trong phiên này. Bản Swing cũ không được nối networking mới.

### Build, test và deploy

- `build-network.ps1`, `build-network.sh`: build Java 17 và đóng gói server/demo độc lập, không kéo JavaFX hoặc MongoDB vào JAR triển khai.
- `run-server.bat`, `run-network-demo.bat`: chạy JAR; tự build nếu thiếu artifact.
- `test-network.ps1`, `tools/NetworkingTestLauncher.java`: chạy JUnit Platform thật qua cache JUnit 5.10.2 khi không muốn tải Maven; không dùng assertions giả.
- `deploy/overleaf.service`: mẫu systemd chạy user `overleaf`, restart khi lỗi, shutdown timeout 30 giây.
- `deploy/overleaf.env.example`: loopback phù hợp SSH tunnel.
- `deploy/overleaf-public.env.example`: mẫu TLS/token/keystore cho public TCP.
- POM common thêm test dependency JUnit; POM root thêm Surefire 3.2.5; POM client thêm server dependency **test scope** để test integration hai đầu.
- `.gitignore`: thêm `dist/`, `.env`, `*.p12`, `*.jks`.

## 5. Luồng kết nối thực tế

```text
Client TCP/TLS
  -> AUTH(token)
  <- ACK(AUTHENTICATED)
  -> JOIN_ROOM(room, userId, displayName)
  <- FULL_SYNC + CHECKPOINT + USER_LIST_UPDATE
  -> TEXT_EDIT(OperationCodec JSON)
  -> M3 kiểm tra membership, identity và giới hạn
  -> M4 áp dụng/biến đổi và lưu lịch sử RAM
  <- ACK(operationId) về người gửi
  -> TEXT_EDIT cho các thành viên khác cùng phòng
```

Reconnect: tạo kết nối mới, AUTH, rồi gửi **RESYNC trực tiếp**, không cần JOIN trước. Nếu room còn trong RAM, M4 xử lý pending và gửi snapshot/checkpoint mới. Nếu server đã restart hoặc room không tồn tại, lỗi chứa `ROOM_UNKNOWN`; pending client không bị xóa.

Token là khóa chung cho nhóm, không ánh xạ tài khoản MongoDB. User ID được giữ cố định trên một kết nối sau join đầu; không được có hai session cùng ID trong cùng phòng. Server kiểm tra cả sender trong envelope và user trong CRDT operation, rồi dùng display name đã gắn với session.

## 6. Cấu hình và artifact triển khai

Mặc định: `127.0.0.1:8080`, TLS tắt, token rỗng, 64 client, read timeout 60 giây, queue server 32 frame, giới hạn tài liệu 100000 UTF-16 code units. Tối đa 256 phòng và 1000 pending operations trong một RESYNC request. Phòng rỗng vẫn giữ trong RAM đến khi JVM tắt.

Socket có reader/writer riêng; routing M3 dùng một khóa để giữ thứ tự xử lý/membership và kiểm tra giới hạn. Đây là cấu hình cho server nhóm nhỏ, chưa benchmark throughput networking trên VPS.

Không bật scheduler maintenance của M4 trong main server vì hiện nó xóa phòng trống và lịch sử. Socket timeout của M3 đóng kết nối và dọn presence. Client tự gửi heartbeat mỗi 15 giây; nên giữ timeout server 45–60 giây trở lên.

Artifact hiện có tại:

- `dist/overleaf-server.jar`: main `com.overleaf.server.ServerApp`.
- `dist/overleaf-network-demo.jar`: main `com.overleaf.client.net.NetworkDemo`.

JAR không chứa file MongoDB `config.properties`, keystore hoặc credential. Máy triển khai chỉ cần Java runtime 17+ cho backend hiện tại. Cần JDK đầy đủ để build; JBR IntelliJ trên máy hiện tại thiếu `jar.exe`, nên đã dùng `C:\Users\Admin\.jdks\ms-21.0.13.0.2`.

## 7. Kiểm chứng

Đã chạy Maven thật trên toàn bộ reactor để compile cả Common, Database, Server và GUI JavaFX. Đã chạy các test được chọn, không gọi test MongoDB:

| Test class | Số test |
| --- | ---: |
| `MessageCodecTest` | 5 |
| `NetworkingTest` | 9 |
| `NetworkClientTest` | 2 |
| `M4CollaborationTest` có sẵn | 6 |
| `OfflineResyncTest` có sẵn | 5 |
| `ProjectFileManagerTest` có sẵn | 8 |
| **Tổng** | **35** |

Kết quả: **35/35 pass**, không fail/error/skip trong lần chạy Maven chọn lọc. POM dùng JUnit 5.10.1; script cache dùng JUnit 5.10.2. Build networking dùng `--release 17`; runtime kiểm thử là JDK 21, chưa chạy riêng runtime Java 17.

Đã kiểm tra thêm artifact thật: khởi động `java -jar dist/overleaf-server.jar` trong tiến trình con, AUTH, nối hai client, truyền edit và kiểm tra snapshot của client vào sau đều thành công. Helper local `target/DeploymentSmoke.java` bị ignore, không thay thế test JUnit chính thức.

Phạm vi test: Unicode/nội dung dài, frame chia/gộp, EOF/truncation, độ dài/version/type/UTF-8/byte dư sai; socket JOIN rồi EDIT gửi liền nhau, snapshot, ACK, cursor, cách ly room, identity giả, loại message không được phép, duplicate user, tài liệu vượt giới hạn, token sai, idle timeout, client limit, shutdown, queue đầy, nối lại qua socket mới, RESYNC và replay idempotent; TLS với certificate tạm, truststore và hostname verification; test M4/IO hiện có.

Log Maven và report nằm ở `target/maven-network-tests.log` và `*/target/surefire-reports/`. Đây là artifact local bị ignore. Không tuyên bố đã chạy toàn bộ test database, compile LaTeX thật, test GUI bằng thao tác tay hoặc service Linux trên VPS.

Lệnh chạy lại:

```powershell
mvn "-Dtest=MessageCodecTest,NetworkingTest,NetworkClientTest,M4CollaborationTest,OfflineResyncTest,ProjectFileManagerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## 8. Chạy thử nhanh

```powershell
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-21.0.13.0.2'
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-network.ps1
.\run-server.bat 8080
```

Hai terminal riêng, đặt JAVA_HOME tương tự và chạy Alice/Bob:

```powershell
.\run-network-demo.bat 127.0.0.1 8080 alice demo
.\run-network-demo.bat 127.0.0.1 8080 bob demo
```

Nhập `/insert 0 Hello` ở Alice. Để đưa lên VPS, làm theo [DEPLOY_SERVER.md](DEPLOY_SERVER.md): loopback + SSH tunnel hoặc public TLS + token + PKCS12; có mẫu systemd kèm theo.

## 9. Việc tiếp theo và giới hạn còn thật sự tồn tại

| Phụ trách | Việc còn lại |
| --- | --- |
| **M3 + M5 + M4** | Nối tải/lưu tài liệu bền vững; xác định room → project/document và semantics restart. ACK hiện xác nhận áp dụng vào RAM, không phải đã ghi bền vững. |
| **M3 + M5** | Xác thực tài khoản, kiểm tra quyền từng project; token chung hiện chỉ kiểm soát quyền vào server nhóm. |
| **M1 + M4** | Kiểm thử GUI hai cửa sổ, chỉnh sửa đồng thời nhiều tình huống; xử lý UX khi đổi room có pending ops; cân nhắc tự reconnect và lưu offline queue xuống đĩa. |
| **M1 + M2** | Kiểm chứng compile trong đúng workspace, render PDF đầu ra thật nếu đó là yêu cầu cuối cùng. Canvas preview hiện tại không phải PDF renderer. |
| **Nhóm triển khai** | Cài Java trên VPS, chuyển artifact, cấu hình DNS/certificate/token/firewall hoặc SSH tunnel, cài systemd và đo networking trên môi trường thật. |

Không thay đổi compiler hoặc repository MongoDB của các thành viên khác. Một room hiện là một document trong M4; chưa có đồng bộ cây thư mục hoặc lựa chọn document theo file. File local của client vẫn là nơi cần lưu trước khi restart server.

JOIN mới thay editor bằng snapshot server, có thể là rỗng với phòng mới. Lưu file local trước khi đổi phòng; nếu đang phục hồi pending ops của cùng phòng thì dùng reconnect/RESYNC. Queue offline chỉ sống trong RAM tiến trình client, nên đóng ứng dụng chưa phải hành động lưu các pending ops bền vững.

## 10. Git và lưu ý cho phiên AI tiếp theo

Các thay đổi lần này đang ở working tree nhánh `main`, chưa commit/push. Không thực hiện merge/reset nhánh `protocol_networking`. Nếu chuẩn bị PR, tạo nhánh feature từ trạng thái hiện tại và kiểm tra lại diff.

Hai file `HD_CHAY_UNG_DUNG.md` và `overleaf_desktop_ver2.md` đã modified trước lần làm việc này vì người dùng yêu cầu giữ bản local khi cập nhật main. Không đưa chúng vào commit M3 một cách vô tình. Các file báo cáo/blueprint/copy Markdown untracked cũng có sẵn trước đó; không xóa hoặc ghi đè.

Khi tiếp tục, đọc `AI_Handoff.md`, `NETWORKING_M3.md`, `DEPLOY_SERVER.md` và kiểm tra source thực tế. Không dựng lại stack `common.collab` của nhánh cũ, không đổi wire một phía sang Object Stream, không ghi credential vào JAR/Git và không đánh đồng backend đã chạy với persistence/database đã tích hợp.

## 11. Dọn file không còn dùng

Theo yêu cầu dọn dự án, đã xóa `build.bat` cũ vì dùng đường dẫn `src/com/...` không tồn tại trong cấu trúc Maven hiện tại, và 4 file `.gitkeep` trong thư mục Java đã có source.

Đã dọn thư mục `bin/` chứa 20 class cũ, `target/test-libs/` chứa 7 JAR trùng với bộ thư viện test mới, ba helper kiểm thử tạm `NetworkingTestRunner.java`, `NetworkClientSmoke.java`, `DeploymentSmoke.java` trong `target/`, cùng `DeploymentSmoke.class` tạm. Tổng cộng 36 file, khoảng 1 MiB. Kết quả smoke test trước khi dọn vẫn được ghi ở mục 7; helper tạm không còn trong working tree.

Giữ nguyên test chính thức, `tools/NetworkingTestLauncher.java`, `target/network-test-libs/`, cache Maven/JavaFX, output Maven trong từng module, hai JAR `dist/` và `main.tex` của người dùng. Không xóa tài liệu Markdown hoặc các bản sao lưu Markdown.

Build networking bằng `build-network.ps1`/`build-network.sh`; build Maven bằng `mvn compile`. Các script client cũ có thể tự tạo lại `bin/`, nhưng chúng chưa được cập nhật để dùng toàn bộ transport mới; lệnh mở JavaFX đã kiểm chứng dùng output Maven và JavaFX JAR trong `target/maven-cache/` như hướng dẫn trong cuộc trò chuyện.

## 12. Bản đồ folder, phần việc member và vai trò bàn giao

Đã thêm [README.md](README.md) liệt kê từng file source/test/resource/script/tài liệu đang có, chức năng, member phụ trách và lệnh chạy. Các nhóm file tự sinh/dependency được mô tả theo thư mục để không trộn với source nghiệp vụ.

Phân công dưới đây dựa trên báo cáo và chức năng code đã đối chiếu. Không có tên/ID cá nhân đã xác nhận cho M1–M5; đây là trách nhiệm kỹ thuật, không phải kết luận tác giả Git của từng file. Năm vai trò được tổ chức trong bốn Maven module, không phải năm module độc lập.

| Folder/phần việc | Member chính | Đã code gì | Vai trò của member khác / việc bàn giao |
| --- | --- | --- | --- |
| `overleaf-client/.../client/ClientApp.java` | **M1** | Main chọn JavaFX/Swing | M3 dùng luồng JavaFX hiện tại để nối transport; không đổi main |
| `overleaf-client/.../client/gui/` | **M1** | JavaFX editor, file tree, menu, compile controls, log, Canvas preview; Swing dự phòng; xử lý sự kiện M2/M4 | **M3** thay Object Stream bằng NetworkClient, thêm token/TLS, nối presence thật và khóa/mở editor khi đồng bộ. M1 kiểm tra UX, remote edit và các tình huống đổi phòng |
| `overleaf-client/src/main/resources/styles/` | **M1** | Theme JavaFX `overleaf-theme.css` | Không dùng Tailwind; M3 không thay theme |
| `overleaf-client/.../client/compiler/` | **M2** | `LatexCompilerService`: ProcessBuilder, engine, timeout, log và callback | M1 gọi từ GUI; compile local, không chạy trong TCP server |
| `overleaf-client/.../client/io/` | **M2** | `ProjectFileManager`, `WorkspaceWatcher` | M1 nối file tree/editor; cần thống nhất workspace khi compile/watch |
| `overleaf-client/.../client/net/` | **M3** | `NetworkClient`, `NetworkDemo`; TCP/TLS, AUTH, queue FIFO, heartbeat và callback | M1 sử dụng transport; **M4** cung cấp operation/offline queue và semantics RESYNC |
| `overleaf-common/.../common/sync/` — model và operation | **M4** | `CRDTOperation`, `DocumentState`, `CollaboratorInfo`, `CursorPosition`, `SyncMessage`, `MessageType`, `OperationCodec`, `OfflineOperationQueue` | **M3** thêm AUTH trong enum và sửa online sau CHECKPOINT. M1/M3 phải dùng cùng message/payload contract |
| `overleaf-common/.../common/sync/` — transport | **M3** | `MessageCodec`, `TlsContexts` | M4 giữ model nghiệp vụ; M3 giữ wire protocol/TLS tương thích hai đầu |
| `overleaf-server/.../server/` ngoài `collaboration/` | **M3** | `ServerApp`, `ServerConfig`, `OverleafServer`, `ServerSession`; listener, session, AUTH, membership, kiểm tra yêu cầu và shutdown | Gọi engine M4; **M5** chưa được nối cho đăng nhập/persistence |
| `overleaf-server/.../server/collaboration/` | **M4** | Engine, room/document manager, lịch sử, thread pools, broadcaster, offline RESYNC và chống replay trùng | **M3** thêm entry point đồng bộ và giới hạn outbox/disconnect để phù hợp TCP. M3/M4/M5 cùng thiết kế việc lưu bền vững tiếp theo |
| `database/.../db/config/` | **M5** | Quản lý MongoClient/MongoDatabase và POJO codec | Người triển khai cung cấp cấu hình; không đưa credential vào JAR networking |
| `database/.../db/model/` | **M5** | User, Project, Document | M3/M4 cần mapping identity/room sang các ID này khi tích hợp |
| `database/.../db/repository/` và `repository/impl/` | **M5** | Hợp đồng và triển khai truy cập MongoDB cho ba model | Không sửa repository từ lớp GUI/network; gọi qua API/service phù hợp |
| `database/.../db/service/` | **M5** | `DatabaseService`: authenticate, load content, save content và getter repository | **M3 + M4** chưa gọi service trong đường đi TCP. Cần xác định thời điểm load/save, quyền dự án và nghĩa của ACK khi có persistence |
| `database/src/main/resources/` | **M5** | Mẫu/runtime config MongoDB và logging | Mẫu có thể bàn giao; credential local không được đưa vào README/handoff |
| `deploy/`, `build-network.*`, `run-server.bat`, `run-network-demo.bat` | **M3** | Build JAR, chạy local, mẫu env và systemd | Người quản trị cấu hình VPS/DNS/TLS; chưa chạy service Linux thực tế trong phiên code |
| `tools/`, `test-network.ps1` | **M3** | Launcher JUnit thật và script chạy test từ cache | Đây là công cụ kiểm chứng, không phải code runtime server |
| POM root và từng module | **Cả nhóm theo phần phụ thuộc** | Cấu trúc Maven chung; M1 JavaFX, M5 MongoDB/logger, M3 Surefire/common test/client integration test dependency | Đổi dependency phải kiểm tra reactor; dependency Server trong Client hiện chỉ là test scope |
| Markdown gốc và `main.tex` | **Tài liệu nhóm / dữ liệu người dùng** | Báo cáo, blueprint, tiến độ M4, README, handoff và tài liệu local | Không coi là file build rác; giữ theo yêu cầu người dùng |

### 12.1 Test do từng phần quản lý

| Folder test | Member | Phạm vi |
| --- | --- | --- |
| `overleaf-common/src/test/.../sync/` | **M3** | Codec và regression queue online sau CHECKPOINT |
| `overleaf-client/src/test/.../io/` | **M2** | File manager |
| `overleaf-client/src/test/.../net/` | **M3** | API client/server, disconnect và TLS |
| `overleaf-server/src/test/.../server/NetworkingTest.java` | **M3** | Socket thật, membership, giới hạn, reconnect và queue |
| `overleaf-server/src/test/.../collaboration/` | **M4** | Logic collaboration/offline, helper giả lập và benchmark M4 |
| `database/src/test/.../db/` | **M5** | Kết nối MongoDB và repository |

### 12.2 Ranh giới khi tiếp tục sửa code

- **M1** giữ UI và UX, gọi API của M2/M3/M4; chuyển callback network sang JavaFX Application Thread.
- **M2** giữ compile/file I/O; tránh đưa ProcessBuilder hoặc filesystem logic vào socket handler.
- **M3** giữ kết nối, TLS, framing, validation/membership và vận chuyển message; thống nhất wire contract trước khi sửa model chung.
- **M4** giữ semantics operation, transform, checkpoint/ACK và RESYNC; phối hợp M3 để không phá thứ tự nhận/gửi.
- **M5** giữ schema/repository/service; phối hợp M3/M4 để hoàn thiện tài khoản/quyền dự án và lưu nội dung bền vững.

Lần cập nhật này chỉ thêm README và bổ sung bản đồ trách nhiệm vào handoff; không thay đổi code runtime hoặc chạy lại test nghiệp vụ. Kết quả 35/35 ở mục 7 là lần kiểm chứng code trước đó, không phải một lần chạy test mới cho thay đổi tài liệu.

## 13. Đồng bộ code M3/M4 sau review

Đã sửa code theo yêu cầu người dùng, không chỉ cập nhật tài liệu:

| Vấn đề | Sửa đổi |
| --- | --- |
| Late JOIN có clock quá thấp, chèn đầu Hello thành Hello> | SyncMessage thêm metadata Lamport; codec ghi wire v2. M4 gắn clock vào snapshot/checkpoint/ACK/edit. JavaFX/demo cập nhật clock trước khi tạo thao tác kế tiếp. Payload FULL_SYNC và CHECKPOINT không đổi. |
| Callback cũ ghi đè phòng mới | Thêm `ConnectionScope`; kiểm tra generation/room/client khi chạy callback trên FX thread, và đóng kết quả connect task đã cũ thay vì gán đè connection mới. |
| Pending hơn 1000 không RESYNC được | Thêm `ResyncTransfer`, RESYNC_BEGIN/PART/END. Server ghép đủ mới gọi M4 một lần, không tự rebase từng phần riêng. Tối đa upload 4 MiB, 30.000 operation và 128 phần; giới hạn document vẫn áp dụng. |
| ACK dồn khi RESYNC lớn | M4 gửi ACK_BATCH cho upload trên 64 operation; mỗi ACK_BATCH tối đa 2048 ID. Offline queue chỉ xóa các ID đã ACK; replay vẫn idempotent trong lịch sử còn giữ. |
| ROOM_UNKNOWN có tiền tố khác hợp đồng | Trả đúng payload `ROOM_UNKNOWN` qua TCP; validation error khác vẫn giữ tiền tố. |

### 13.1 File và vai trò cập nhật

- **M3/Common:** sửa `SyncMessage.java`, `MessageCodec.java`, `MessageType.java`; thêm `ResyncTransfer.java` và test. Codec đọc được frame v1 nhưng chỉ ghi v2, do đó phải rebuild/deploy cả hai đầu, không dùng client JAR cũ.
- **M3/Client:** thêm `net/ConnectionScope.java`, sửa `NetworkClient` để gửi các frame của một upload liên tiếp, và cập nhật clock trong demo.
- **M1 + M3/GUI:** cập nhật callback nhận dữ liệu, connect task và ACK_BATCH; guard kiểm tra tại thời điểm thực thi callback. Không sửa layout giao diện.
- **M4/Server:** gắn metadata clock và batch ACK trong `DocumentSyncManager`. Entry point đồng bộ và cơ chế khóa chung ở routing vẫn giữ như trước.
- **M4/Common:** queue xử lý ACK_BATCH, bỏ phép gán online trùng; test adapter M4 cũng cập nhật clock theo metadata như client thật.
- **M3/Server:** session ghép upload chỉ sau AUTH, lỗi/truncated upload không giao cho M4; tăng giới hạn operation và chuẩn hóa ROOM_UNKNOWN.

### 13.2 Kiểm chứng lần sửa mới

Maven compile toàn reactor và chạy chọn lọc **42/42 test pass**: MessageCodecTest 6, ResyncTransferTest 3, ConnectionScopeTest 1, NetworkingTest 10, NetworkClientTest 3, M4CollaborationTest 6, OfflineResyncTest 5, ProjectFileManagerTest 8. Log ở `target/sync-integration-tests.log`.

Test hồi quy bao gồm chèn đúng đầu tài liệu sau JOIN, kiểm tra callback trễ, upload 1.200 operation trên 1 MiB qua NetworkClient/ServerSession thật, replay không nhân đôi, batch ACK, chunk Unicode không cắt surrogate pair, upload thiếu/sai/vượt giới hạn, đọc frame v1 và mã lỗi chính xác. Test scope không truy cập MongoDB. Chưa kiểm thử GUI bằng thao tác tay hoặc triển khai VPS.

`task-m4.md` và `walkthrough-m4.md` đã thêm trạng thái tích hợp mới, vẫn giữ phần lịch sử. Không khôi phục các file Markdown đang bị người dùng xóa trong working tree, bao gồm `NETWORKING_M3.md` và các bản report/blueprint. Hợp đồng hiện tại nằm trong `SYNC_PROTOCOL.md`.

Các giới hạn còn lại: dữ liệu và offline queue ở RAM, chưa nối MongoDB; routing server còn khóa chung; client chậm có thể bị ngắt bởi backpressure. Upload vượt 4 MiB/30.000 operation cần người dùng giữ bản local và xử lý phù hợp, không có cam kết đồng bộ vô hạn.

### 13.3 Artifact khi server cũ đang chạy

Không thể ghi đè `dist/overleaf-server.jar` vì Windows báo file đang được tiến trình sử dụng. Không tự dừng tiến trình của người dùng. Đã bổ sung `-OutputDirectory` cho `build-network.ps1` để xuất bản v2 vào `dist/v2/`. Dừng server cũ rồi chạy JAR v2, đồng thời khởi động lại GUI từ output Maven mới hoặc dùng demo v2. Các `.bat` hiện tại vẫn trỏ tới JAR ở root `dist/`, do đó dùng đường dẫn v2 trực tiếp trong lần cập nhật này. Restart server làm mất dữ liệu RAM như giới hạn đã ghi ở trên; lưu file local trước khi chuyển phiên.

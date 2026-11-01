# AI Handoff — Overleaf Desktop / Networking Backend M3

> **Bố cục hiện tại (02/11/2026):** bốn Maven module nằm trong `modules/`, tài liệu trong `docs/`, và LaTeX mẫu trong `examples/`. Các đường dẫn module cũ trong những phần lịch sử dưới đây được giữ nguyên theo ngữ cảnh lịch sử.

> **Mới nhất — PDF cuộn và liên kết (02/11/2026):** preview xếp trang liên tiếp, cuộn qua các trang, bấm liên kết nội bộ đến tiêu đề và quay lại bằng `↶`. Render theo vùng xem với ngân sách ảnh, giữ nguyên export PDF thật. **120/120 JUnit chọn lọc đạt**, smoke PDF 13 trang và regression compile/export 7 trang đạt. Xem **mục 25**; mô tả viewer một trang ở mục 19 là lịch sử.

> **Mới nhất — sửa ZIP nhiều chương (02/11/2026):** tái hiện trực tiếp `D:\download\latex-test-project-v2.zip`: thiếu gói TeX và chưa có thư mục aux cho `\include`. Đã bổ sung TinyTeX, tự tạo output subfolder, ưu tiên lỗi gốc và retry cache lỗi. ZIP nguyên gốc ra **13 trang với cả ba engine**, có tiếng Việt/Biber, nguồn giữ nguyên. **117/117 JUnit chọn lọc đạt**. Xem **mục 24**.

> **Mới nhất — workspace (02/11/2026):** thêm thư mục/tệp con, upload nhiều tệp hoặc thư mục, kéo thả/dán ảnh, sao chép từ dự án khác/tệp đầu ra và tải URL HTTPS. Hộp thoại Anh/Việt theo ảnh, cây hiển thị thư mục rỗng và tài nguyên. **110/110 JUnit chọn lọc đạt**, smoke JavaFX + tải HTTPS thật đạt. Xem **mục 23**.

> **Mới nhất — tạo/nhập dự án và chọn thư mục (02/11/2026):** menu mới đầy đủ Anh/Việt, 9 nhóm template, nhập ZIP/Word/Markdown/GitHub; chọn nơi lưu từng dự án, đổi mặc định và chuyển dự án có bản phục hồi. **98/98 test chọn lọc đạt**, kiểm thử PDF thật và JavaFX đạt. Xem **mục 22**. Các mục trước giữ lại làm lịch sử.

> **Mới nhất — tiếng Việt và Outline (01/11/2026):** Outline có thanh kéo giữa cây file và danh sách đề mục, nhớ tỷ lệ sau khi thả chuột; không còn khóa chiều cao 120–260 px. Thêm kiểm tra PDF mất ký tự và bản preamble tương thích ba engine. Xem **mục 20–21**. Bộ regression **84/84 test đạt**.

> **Trạng thái mới nhất — PDF preview/export thật (01/11/2026):** đã bỏ Canvas mô phỏng, dùng PDFBox đọc PDF từ compiler; Export chép đúng bytes của PDF đang xem. Nguồn đổi/lỗi compile khóa xuất và ghi rõ trạng thái cũ. **81/81 test đạt**, smoke UI trên tài liệu người dùng 7 trang đạt. Xem **mục 19**; các nhận định “preview Canvas” bên dưới là lịch sử.

> **Sửa lỗi toolchain sau kiểm thử người dùng (01/11/2026):** đã bổ sung tự nhận `latexmk` khi Settings trống; **76/76 test đạt** và đã compile thành công bản sao đúng tài liệu test trong ảnh. Xem **mục 18**. Tiến trình app cũ cần được đóng/mở lại để nhận code mới.

> **Cập nhật mới nhất — tiêu chí Compile (01/11/2026):** nhánh `feat/m1-gui-client`, nền `b89dbc5` kèm thay đổi local. Đã thêm Settings → Compilation, timeout tùy chỉnh/không giới hạn, đường dẫn latexmk, kiểm tra toolchain, Stop và tích hợp Error Explainer. **72/72 test chọn lọc đạt**, smoke PDF thật ba engine + TikZ/tiếng Việt và smoke UI đạt. Xem **mục 17**; số 35/42/52 và nhận định chưa compile LaTeX ở các mục trước là lịch sử.

> **Trạng thái mới nhất — đồng bộ M3/M4 ngày 01/11/2026:** đã sửa bốn điểm trong review; xem mục 13 và [README.md §5](../README.md#5-giao-thức-wire-v2). 42/42 test pass. Phần mô tả v1, giới hạn RESYNC 1000 và kết quả 35 test ở các mục cũ là lịch sử trước lần sửa này, không còn là hợp đồng hiện tại.

Cập nhật: **01/11/2026**. Nền mã nguồn được kiểm tra: `main`, commit **`df06601`**. Tài liệu này ghi trạng thái code thực tế sau khi tích hợp M3; thay thế handoff ngày 30/10 vốn mô tả nhánh `protocol_networking`.

## 1. Kết quả và phạm vi

Đã đọc lại báo cáo Pure Java, phần M3 trong `overleaf_desktop_ver2.md`, đối chiếu blueprint và kiểm tra code M1/M2/M4/M5 trên `main`. Kiến trúc được áp dụng là Java TCP theo báo cáo, không chuyển sang WebSocket/Yjs của blueprint.

**M3 hiện có backend chạy được bằng JAR, adapter kết nối M4 và client tương thích.** Có thể triển khai listener trên VPS theo [README.md §6](../README.md#6-triển-khai-lên-vps). Chưa đăng nhập VPS, chưa chạy service trên Linux và chưa publish/push code trong phiên này.

Nội dung/lịch sử phòng vẫn ở RAM; chưa nối persistence MongoDB hoặc đăng nhập từng tài khoản. Do đó triển khai được dịch vụ networking cho nhóm, nhưng chưa phải nơi lưu trữ tài liệu bền vững.

Tài liệu liên quan:

- [README.md](../README.md): cấu trúc, cách chạy, cấu hình server, giao thức wire v2 và triển khai VPS.
- Mục 14 dưới đây: các ghi chú quan trọng gộp từ `SYNC_PROTOCOL.md`, `DEPLOY_SERVER.md`, `HD_CHAY_UNG_DUNG.md`, `task-m4.md`, `walkthrough-m4.md` (đã xóa khi dọn dự án).

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

Nhập `/insert 0 Hello` ở Alice. Để đưa lên VPS, làm theo [README.md §6](../README.md#6-triển-khai-lên-vps): loopback + SSH tunnel hoặc public TLS + token + PKCS12; có mẫu systemd kèm theo.

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

Khi tiếp tục, đọc `AI_Handoff.md` (nhất là mục 14), `README.md` và kiểm tra source thực tế. Không dựng lại stack `common.collab` của nhánh cũ, không đổi wire một phía sang Object Stream, không ghi credential vào JAR/Git và không đánh đồng backend đã chạy với persistence/database đã tích hợp.

## 11. Dọn file không còn dùng

Theo yêu cầu dọn dự án, đã xóa `build.bat` cũ vì dùng đường dẫn `src/com/...` không tồn tại trong cấu trúc Maven hiện tại, và 4 file `.gitkeep` trong thư mục Java đã có source.

Đã dọn thư mục `bin/` chứa 20 class cũ, `target/test-libs/` chứa 7 JAR trùng với bộ thư viện test mới, ba helper kiểm thử tạm `NetworkingTestRunner.java`, `NetworkClientSmoke.java`, `DeploymentSmoke.java` trong `target/`, cùng `DeploymentSmoke.class` tạm. Tổng cộng 36 file, khoảng 1 MiB. Kết quả smoke test trước khi dọn vẫn được ghi ở mục 7; helper tạm không còn trong working tree.

Giữ nguyên test chính thức, `tools/NetworkingTestLauncher.java`, `target/network-test-libs/`, cache Maven/JavaFX, output Maven trong từng module, hai JAR `dist/` và `main.tex` của người dùng. Không xóa tài liệu Markdown hoặc các bản sao lưu Markdown.

Build networking bằng `build-network.ps1`/`build-network.sh`; build Maven bằng `mvn compile`. Các script client cũ có thể tự tạo lại `bin/`, nhưng chúng chưa được cập nhật để dùng toàn bộ transport mới; lệnh mở JavaFX đã kiểm chứng dùng output Maven và JavaFX JAR trong `target/maven-cache/` như hướng dẫn trong cuộc trò chuyện.

## 12. Bản đồ folder, phần việc member và vai trò bàn giao

Đã thêm [README.md](../README.md) liệt kê từng file source/test/resource/script/tài liệu đang có, chức năng, member phụ trách và lệnh chạy. Các nhóm file tự sinh/dependency được mô tả theo thư mục để không trộn với source nghiệp vụ.

Phân công dưới đây dựa trên báo cáo và chức năng code đã đối chiếu. Không có tên/ID cá nhân đã xác nhận cho M1–M5; đây là trách nhiệm kỹ thuật, không phải kết luận tác giả Git của từng file. Năm vai trò được tổ chức trong bốn Maven module, không phải năm module độc lập.

| Folder/phần việc | Member chính | Đã code gì | Vai trò của member khác / việc bàn giao |
| --- | --- | --- | --- |
| `overleaf-client/.../client/ClientApp.java` | **M1** | Main mở JavaFX (đã bỏ Swing, xem mục 14) | M3 dùng luồng JavaFX hiện tại để nối transport; không đổi main |
| `overleaf-client/.../client/gui/` | **M1** | JavaFX editor, file tree, menu, compile controls, log, Canvas preview; xử lý sự kiện M2/M4 | **M3** thay Object Stream bằng NetworkClient, thêm token/TLS, nối presence thật và khóa/mở editor khi đồng bộ. M1 kiểm tra UX, remote edit và các tình huống đổi phòng |
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

`task-m4.md` và `walkthrough-m4.md` đã thêm trạng thái tích hợp mới, vẫn giữ phần lịch sử. Không khôi phục các file Markdown đang bị người dùng xóa trong working tree, bao gồm `NETWORKING_M3.md` và các bản report/blueprint. Hợp đồng hiện tại nằm trong README §5 (`SYNC_PROTOCOL.md` đã gộp vào đó, xem mục 14).

Các giới hạn còn lại: dữ liệu và offline queue ở RAM, chưa nối MongoDB; routing server còn khóa chung; client chậm có thể bị ngắt bởi backpressure. Upload vượt 4 MiB/30.000 operation cần người dùng giữ bản local và xử lý phù hợp, không có cam kết đồng bộ vô hạn.

### 13.3 Artifact khi server cũ đang chạy

Không thể ghi đè `dist/overleaf-server.jar` vì Windows báo file đang được tiến trình sử dụng. Không tự dừng tiến trình của người dùng. Đã bổ sung `-OutputDirectory` cho `build-network.ps1` để xuất bản v2 vào `dist/v2/`. Dừng server cũ rồi chạy JAR v2, đồng thời khởi động lại GUI từ output Maven mới hoặc dùng demo v2. Các `.bat` hiện tại vẫn trỏ tới JAR ở root `dist/`, do đó dùng đường dẫn v2 trực tiếp trong lần cập nhật này. Restart server làm mất dữ liệu RAM như giới hạn đã ghi ở trên; lưu file local trước khi chuyển phiên.

## 14. Dọn dự án và ghi chú gộp từ tài liệu đã xóa (01/11/2026, nhánh `Network`)

### 14.1 Những gì đã dọn

- **Tài liệu:** chỉ còn `README.md` (hướng dẫn sử dụng/kỹ thuật) và file handoff này. Đã xóa `SYNC_PROTOCOL.md`, `DEPLOY_SERVER.md` (nội dung chuyển vào README §5–§6), `HD_CHAY_UNG_DUNG.md` (hướng dẫn M1 cũ, phần còn đúng như phím tắt đã vào README), `task-m4.md`, `walkthrough-m4.md` (phần còn giá trị ở 14.2–14.5).
- **Code chết:** xóa `gui/OverleafDesktopApp.java` (bản Swing cũ, không nối networking/M4) và cờ `--swing` trong `ClientApp`; xóa các import không dùng trong `DatabaseService`, `ProjectRepositoryTest`, `CRDTEngine`.
- **Script:** xóa `run-client-swing.bat`, `test-network.ps1` và `tools/NetworkingTestLauncher.java` (trùng với `mvn test`, chỉ chạy Windows và phụ thuộc cache JUnit 5.10.2). Viết lại `run-client.bat` dùng Maven (bản cũ không compile package `net`) và thêm `run-client.sh`.
- **POM:** bỏ cấu hình compiler plugin và version JUnit/Surefire lặp lại ở module client/server (đã khai báo ở parent). Giữ dependency `server → database` và `database → common` dù chưa gọi, vì đó là điểm tích hợp persistence đã lên kế hoạch.
- **Định dạng:** thêm `.editorconfig` và `.gitattributes` (LF trong repo, CRLF cho `.bat/.ps1`) để tránh lỗi "modified" do khác kiểu xuống dòng; xóa khoảng trắng cuối dòng và thêm newline cuối file. Không chạy formatter toàn bộ để tránh conflict với nhánh đang mở của các member.
- **Bảo mật:** `database/src/main/resources/config.properties` (nay là `persistence/...`) chứa mật khẩu MongoDB Atlas thật đã bị commit. Đã bỏ khỏi Git (`git rm --cached`, file local vẫn giữ) và sửa `.gitignore` thành `**/src/main/resources/config.properties` (pattern cũ chỉ khớp ở thư mục gốc). **Mật khẩu vẫn còn trong lịch sử Git → M5 phải đổi mật khẩu trên Atlas.** Sau khi pull, member khác cần tự tạo lại file từ `config.properties.example`.
- Kiểm chứng sau khi dọn: 42/42 test chọn lọc pass như trước.

### 14.1b Tổ chức lại module/package

Các mục 1–13 dùng đường dẫn cũ. Bảng đối chiếu (chỉ đổi vị trí/tên, không đổi logic; dùng `git mv` nên giữ lịch sử file):

| Cũ | Mới |
| --- | --- |
| module `overleaf-common`, `overleaf-server`, `overleaf-client`, `database` | `common`, `server`, `client`, `persistence` (artifactId trùng tên thư mục) |
| `common.sync`: MessageType, SyncMessage, MessageCodec, ResyncTransfer | `common.protocol` |
| `common.sync`: CRDTOperation, DocumentState, CollaboratorInfo, CursorPosition | `common.model` |
| `common.sync`: OperationCodec, OfflineOperationQueue | `common.sync` (giữ nguyên) |
| `common.sync.TlsContexts` | `common.security.TlsContexts` |
| `server`: OverleafServer, ServerSession, ServerConfig | `server.net` (ServerApp vẫn ở `server`) |
| `server.collaboration` | `server.collab` |
| `client.gui`, `client.io` | `client.ui`, `client.workspace` |
| `com.overleaf.db.*` | `com.overleaf.persistence.*` |
| script ở thư mục gốc | `scripts/` (tự `cd` về thư mục gốc) |

Tên JAR triển khai (`dist/overleaf-server.jar`, `dist/overleaf-network-demo.jar`), main class `com.overleaf.server.ServerApp`/`com.overleaf.client.net.NetworkDemo` và wire protocol không đổi. Member đang có nhánh mở cần rebase/merge sau khi thay đổi này vào `main`; Git nhận diện được phần lớn file đổi tên.

Việc tiếp theo đề xuất: tách `client/.../ui/OverleafFxApp.java` (~1.650 dòng) thành MainWindow, EditorPane, FileTreePane, PreviewPane, ConnectDialog và một controller cho collaboration.

### 14.2 Cơ chế offline/RESYNC của M4 (từ walkthrough-m4)

1. Client giữ mọi thao tác chưa ACK, nên cả thao tác gõ lúc offline lẫn thao tác đã gửi nhưng mất ACK đều không mất.
2. Client nhớ "điểm đã thấy" (operation ID cuối từ server), cập nhật qua CHECKPOINT khi JOIN và sau RESYNC.
3. Server `handleResync`: thao tác đã có trong lịch sử thì chỉ ACK lại (không áp dụng lần hai); các thao tác offline được transform qua phần người khác sửa sau điểm đã thấy rồi áp dụng và broadcast; nếu điểm đã thấy đã bị trim khỏi lịch sử thì dự phòng bằng Lamport. Cuối cùng gửi FULL_SYNC + CHECKPOINT.
4. Ví dụ đã test: "Hello World", Bob offline gõ "!" cuối và ">" đầu, Alice thêm "Big " và "<<" → mọi máy ra `<<>Hello Big World!`.
5. `SyncBroadcaster` dùng outbox riêng cho mỗi client; trước đây pool co giãn làm đảo thứ tự ACK/edit (có test 300 message).

### 14.3 Giới hạn đã biết (cần nói thật trong báo cáo)

- Sau RESYNC, văn bản client bị thay bằng bản server nên con trỏ có thể nhảy; GUI khóa editor khi `isResyncing()`.
- Dự phòng Lamport (lịch sử đã trim) không mất chữ nhưng có thể đặt sai vị trí so với ý người gõ.
- Hai người chèn cùng vị trí: thứ tự theo thứ tự server nhận. Xóa đoạn chồng nhau chưa test kỹ. ACK mất cùng lúc người khác sửa có thể làm lệch nhẹ vị trí thao tác kế tiếp.
- `OperationHistory.getOperationsSince` duyệt toàn bộ lịch sử phòng (≤ 10.000 op) mỗi lần sửa; chi phí tăng theo độ dài lịch sử, chưa đo riêng.
- Số liệu "< 50 ms trên LAN" trong báo cáo cũ **chưa từng được đo**; không dùng.

### 14.4 Benchmark M4 (`BenchmarkM4`)

Đo engine trong một tiến trình Java, không qua mạng; "độ trễ" là gửi → nhận ACK trong RAM. 4 phép đo: B1 một phòng 1–16 người; B2 1–8 phòng × 4 người; B3 một người gõ, 1–50 người nhận; B4 thời gian RESYNC theo số op offline/bỏ lỡ. Mỗi phép đo 1 lần khởi động + 3 lần chính thức, lấy trung vị, có kiểm tra không mất thao tác.

Số mẫu (VM 2 nhân, Java 21; chỉ có ý nghĩa trên máy đó): B1 ~2.100 → ~4.500 op/s, trung vị < 3 ms; B2 ~6.100 → ~12.000 op/s tổng, trung vị 0,3 → 2,5 ms; B3 50 người nhận ~0,3 ms; B4 1000 op offline + 1000 op bỏ lỡ ~65 ms. Cần chạy lại trên máy nhóm trước khi đưa vào báo cáo.

### 14.5 Việc còn mở (bổ sung cho mục 9)

- **M5:** đổi mật khẩu MongoDB Atlas (xem 14.1).
- **M4:** mục B "tua lại lịch sử" chưa bắt đầu; đưa phần M4 (offline, benchmark, giới hạn 14.3) vào báo cáo; chạy lại benchmark trên máy nhóm.
- **Có thể dọn tiếp nếu nhóm đồng ý** (chưa xóa vì là API của member khác): các method public chưa có nơi gọi — `CollaboratorInfo` (`changeRole`, `createViewer`, `isHost`, `markActive`, `markIdle`, `getJoinedAt`, `getLastActiveAt`), getter của `CursorPosition`, `DocumentState.getDocumentId/getLastModified`, `MessageType.fromString`, `CRDTEngine` (`applyOperations`, `generateInsert/Delete`, `getDocumentState/Version`), `CollaborationThreadPool.getSyncPoolSize/scheduleWithDelay`, `DocumentSyncManager.startMaintenance` (cố ý không bật vì xóa phòng trống), `OperationHistory` (`getHistorySize`, `getOperationsByUser`, `getRecentOperations`, `hasHistory`), `LatexCompilerService` (`isLatexmkAvailable`, `parseErrors`, `setTimeoutSeconds`).

## 15. Nối giao diện mới với backend (01/11/2026, nhánh `Network`)

Giao diện mới (LoginView, HomeView, ActivityHeatmap, UiSupport, theme) do DeepSeek dựng chỉ chạy với `DemoBackend` dữ liệu giả; editor luôn đọc/ghi `./main.tex` bất kể mở dự án nào. Đã nối thật:

| Phần | Trước | Sau |
| --- | --- | --- |
| Đăng nhập | Email bất kỳ + mật khẩu ≥ 6 ký tự đều vào; điền sẵn tài khoản demo | `LocalBackend`: tài khoản lưu trên máy, PBKDF2-SHA256 có salt, báo sai mật khẩu/email chưa có/email trùng; "Keep me signed in" tự đăng nhập lần sau; form điền sẵn email gần nhất |
| Dự án | 4 dự án giả ở `~/Overleaf/...` không tồn tại | Mỗi dự án là một thư mục trong `~/OverleafDesktop/projects/` (đổi bằng `OVERLEAF_HOME`); tạo/đổi tên/xóa (vào `.trash/`); thời gian sửa và số file đọc từ ổ đĩa |
| Hoạt động | Số ngẫu nhiên | Đếm số lần lưu thay đổi theo ngày, số lần compile thành công theo năm |
| Editor | Luôn dùng thư mục chạy app | `ProjectFileManager`, `WorkspaceWatcher`, cây file và compile trỏ vào thư mục dự án; bấm file trong cây để mở; tự lưu khi chuyển file/dự án/về Home/thoát; file sửa bên ngoài được nạp lại nếu chưa có thay đổi chưa lưu |
| Compile | Bỏ qua lựa chọn engine; luôn compile `.` | Dùng engine đã chọn (pdfLaTeX/XeLaTeX/LuaLaTeX), báo rõ khi thiếu TeX/timeout/lỗi LaTeX (dòng đầu tiên); "Open compiled PDF", "Clean temporary files" thật sự xóa file tạm trong `build/` |
| Phòng | Queue offline tạo ngay khi đăng nhập nên mọi thao tác gõ bị xếp hàng; JOIN phòng trống xóa trắng editor rồi autosave ghi đè `main.tex` | Queue chỉ tạo khi vào phòng; phòng trống được khởi tạo bằng `main.tex` local; phòng có nội dung khác → giữ bản local thành `main.before-<room>.tex`; đổi dự án thì rời phòng; trong phòng chỉ sửa được `main.tex` |
| Ảnh đại diện | Chỉ có chữ viết tắt | Bấm avatar (Home hoặc sidebar editor) → modal "Profile picture": chọn PNG/JPG/GIF ≤ 5 MB, cắt vuông giữa, hiển thị tròn; xóa ảnh. Lưu theo tài khoản ở `.app/avatars/` qua `ProfileGateway` |
| Hộp thoại | `Dialog`/`Alert` mặc định của JavaFX, lệch theme | `UiModal`: cùng kiểu modal "New project" (scrim, card trắng, ô icon, field, nút xanh/đỏ) cho Connect room, New file, đổi tên/xóa file và dự án, thông báo |

File: `ui/backend/LocalBackend.java` (mới, thay `DemoBackend` đã xóa), `UiModal.java` (mới), sửa `OverleafFxApp`, `LoginView`, `HomeView`, `UiSupport` (thêm icon, bỏ `styleDialog` không còn dùng), gateway interfaces, CSS (`btn-danger`, `modal-body`, `menu-danger`, `modal-icon-tile-danger`).

Kiểm chứng: **52/52 test** (42 cũ + 10 `LocalBackendTest`). `UiSmoke` (main class trong test, cần màn hình — lệnh ở README §3) điều khiển app thật: đăng ký, tạo dự án qua modal, gõ/lưu (kiểm tra ổ đĩa), compile (máy này không có TeX → báo "No LaTeX toolchain found", không treo), watcher, mở/tạo/đổi tên/xóa file, kết nối server thật qua modal Connect với client thứ hai (Bob) nhận/gửi edit và presence, rejoin có backup, đổi tên/xóa dự án, đăng xuất, sai mật khẩu, đăng nhập lại — tất cả PASS. `scripts/run-client.sh` khởi động không lỗi.

Chưa làm / cần nhóm quyết định:

- **M5 (MongoDB) chưa nối vào đăng nhập.** Cố ý: client nối thẳng Atlas thì mọi máy phải giữ credential DB. Đường đúng là client gửi AUTH tài khoản lên server M3, server gọi `DatabaseService`; khi có, viết một `AuthGateway`/`ProjectGateway` mới thay `LocalBackend`, view không phải sửa.
- Chưa compile LaTeX thật trên máy kiểm thử (không có TeX). Preview vẫn là Canvas tự vẽ, không phải PDF thật; PDF thật mở bằng "Open compiled PDF".
- Phòng mới chỉ đồng bộ `main.tex`; nhiều file trong phòng cần mở rộng giao thức (M3/M4).
- `OverleafFxApp` vẫn lớn (~2.600 dòng); đề xuất tách như mục 14.1b.

### 15.1 Gõ tiếng Việt và Windows

- **Lỗi:** gõ tiếng Việt (Telex) trên macOS bị thừa chữ trong mọi ô nhập. Nguyên nhân là JavaFX 17: khi bộ gõ đang ghép chữ (marked text), glass macOS vẫn sinh thêm KeyEvent nên ký tự gốc bị chèn kèm ký tự đã ghép. Đã sửa ở JavaFX 23 ([JDK-8301900](https://bugs.openjdk.org/browse/JDK-8301900)); không backport về 17/21.
- **Sửa:** `javafx.version` 17.0.8 → **23.0.2**. JavaFX 23 cần Java 21, nên chỉ module `client` biên dịch `release 21` (POM cha chuyển sang `<release>${maven.compiler.release}</release>`, mặc định 17). `common`/`server` vẫn Java 17, JAR server và triển khai VPS không đổi. Client có `maven-enforcer-plugin` báo rõ khi build bằng JDK < 21.
- **Đã kiểm:** 52/52 test, `UiSmoke` toàn bộ PASS trên JavaFX 23 (thêm bước tạo dự án tên tiếng Việt, gõ đủ các chữ có dấu, lưu đúng UTF-8, outline hiển thị đúng). Tên tài khoản tiếng Việt lưu/đọc đúng. Template dự án mới hỗ trợ tiếng Việt cho pdfLaTeX/XeLaTeX/LuaLaTeX (chưa compile thử vì máy không có TeX).
- **Windows:** tên thư mục dự án tránh tên thiết bị (`CON`, `NUL`…) và dấu chấm/khoảng trắng cuối; font logic (System/Serif/Monospaced) map sang Segoe UI/Times New Roman/Courier New đều có dấu tiếng Việt; `run-client.bat` đặt `chcp 65001`. **Chưa chạy thử trên máy Windows** — cần một member kiểm tra gõ Telex (Unikey/EVKey hoặc bàn phím Vietnamese của Windows) trên JDK 21+.


### 15.2 Lệnh và chi tiết đã rút khỏi README

README §3 chỉ giữ cách chạy. Chi tiết kỹ thuật ở đây:

- Dữ liệu (`~/OverleafDesktop`, đổi bằng `OVERLEAF_HOME` hoặc `-Doverleaf.home=`): `projects/<tên>/main.tex` (PDF ở `build/main.pdf`), `.app/accounts.properties` (PBKDF2-SHA256 + salt), `.app/settings.properties`, `.app/activity.properties`, `.app/avatars/`, `.trash/`.
- Phòng: người đầu tiên vào phòng trống đưa `main.tex` của mình lên; phòng đã có nội dung khác → bản local giữ thành `main.before-<room>.tex`; trong phòng không mở được file khác. Token/TLS xem README §4–§6.
- Demo console: `java -jar dist/overleaf-network-demo.jar 127.0.0.1 8080 alice demo` (lệnh `/insert 0 Hello`, `/delete 0 Hello`, `/cursor 2 7`, `/quit`).
- Smoke test GUI (cần màn hình, dữ liệu ở thư mục tạm, lưu ảnh từng màn hình):

  ```bash
  mvn -q -DskipTests install
  mvn -q -pl client dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
  java -cp client/target/classes:client/target/test-classes:$(cat client/target/cp.txt) com.overleaf.client.ui.UiSmoke /tmp/overleaf-shots
  ```

- `mvn test` không chọn lọc chạy cả test `persistence`: cần MongoDB và `persistence/src/main/resources/config.properties` (tạo từ `.example`, không commit). `BenchmarkM4.main()` đo engine M4, ghi `benchmark-results.md`.

## 16. Sửa lỗi khởi động Windows sau khi chuyển nhánh Network

Ngày 01/11/2026, trên nền `Network` commit `190cecb`:

- Kiểm tra còn đầy đủ **66 file source `.java`** trong bốn module mới `common`, `client`, `server`, `persistence` (bao gồm source test). Không có việc tự đổi `.java` thành `.class`.
- Bốn folder tên cũ `overleaf-client`, `overleaf-common`, `overleaf-server`, `database` chỉ chứa output `target/` từ nhánh trước, không còn source hoặc file được Git theo dõi trên nhánh hiện tại. Đã kiểm tra đường dẫn/không có symlink rồi chuyển nguyên trạng vào `target/legacy-build-output-20261101-095848/`, có manifest; không xóa source, `main.tex`, tài liệu hoặc dữ liệu người dùng.
- Tái hiện được lỗi launcher: `mvn is not recognized` và `JAVA_HOME` trống. Sửa `scripts/run-client.bat` để tự tìm JDK và Maven cài sẵn/đi kèm IntelliJ, sử dụng cache workspace và giữ cửa sổ báo lỗi. Launcher Windows dùng batch native, không phụ thuộc PowerShell/profile/execution policy. Thêm tham số `-BuildOnly` cho kiểm tra build mà không mở app.
- Lần compile đầu có lỗi test không nhìn thấy một số class client vừa được compile, trong khi chạy riêng test-compile lại thành công. POM parent bổ sung `fork=true` để tách tiến trình javac giữa module Java 17 và client Java 21.
- Đã chạy `scripts\run-client.bat -BuildOnly`: toàn bộ 5 bước reactor, gồm parent và 4 module, **BUILD SUCCESS** trên Windows với JDK Microsoft 21.0.13 và Maven của IntelliJ. Log: `target/network-client-build-native.log`.
- Đã kiểm tra khởi tạo JavaFX 23 thật: tạo Stage/scene đăng nhập thành công (`UI_STARTUP_OK`) bằng helper tạm, dữ liệu ở `target/ui-startup-check/`. Không dùng tài khoản/dự án thật, không sửa dữ liệu `~/OverleafDesktop`. Helper nằm trong `target/`, không phải source runtime.

Lệnh người dùng chạy từ thư mục gốc: `.\scripts\run-client.bat`. Mở source tại các module tên mới, không mở class trong `target/classes`. Bước kiểm tra Windows này xác nhận build và startup, chưa khẳng định đã kiểm tra thao tác Telex/Unikey thủ công hoặc chạy lại toàn bộ UiSmoke.

## 17. Cải thiện tiêu chí 1 — Compile local và kiểm chứng PDF thật

Ngày **01/11/2026**, nhánh **`feat/m1-gui-client`**, nền **`b89dbc5`**. Các thay đổi vẫn ở working tree, chưa commit/push. Giữ nguyên các thay đổi Windows launcher/POM/tài liệu có từ trước.

### 17.1 Đã triển khai

| Phần / vai trò | File | Thay đổi |
| --- | --- | --- |
| **M2 — Compiler** | `client/.../compiler/LatexCompilerService.java` | `Options` bất biến gồm đường dẫn latexmk và timeout; snapshot theo từng request để chỉnh Settings không đổi build đang chạy/chờ. Timeout mặc định vẫn 120 giây, **0 = không giới hạn**. Đường dẫn được truyền trực tiếp cho ProcessBuilder, không ghép shell command. Thêm thư mục executable vào PATH riêng của process con để dùng đúng engine cùng bộ TeX. |
| **M2 — Compiler** | Cùng file | `cancelCompile()` dừng cả process tree, loại build đang chờ, báo `EXIT_INTERRUPTED`, service có thể dùng lại. Giữ cơ chế chỉ giữ yêu cầu chờ mới nhất; không xóa `build/` mỗi lần compile, để latexmk reuse cache. Thông báo thiếu toolchain chỉ rõ đường dẫn và nơi cấu hình. |
| **M2 — Chẩn đoán** | `compiler/ToolchainDiagnostics.java` (mới) | Chạy `latexmk -v` và `--version` của ba engine, mỗi tool tối đa 8 giây; trả executable/version/lỗi. Không tự cài package hoặc sửa PATH hệ thống. Chạy trong worker nền từ UI. |
| **M1 — Settings** | `ui/SettingsView.java`, `ui/AppSettings.java` | Mục **Compilation/Biên dịch** riêng: chọn engine, nhập/chọn tệp latexmk, nhập timeout (0 hoặc số giây dương), lưu và kiểm tra toolchain đã lưu. Cấu hình lưu local qua backend hiện có. Đường dẫn có khoảng trắng được truyền nguyên một argument. |
| **M1 — Compile UI** | `ui/OverleafFxApp.java` | Nút **Stop** hiện lúc compile; chặn bấm compile trùng từ phím tắt/menu. Log executable/engine/timeout, hiển thị thời gian từ kết quả compile; hết timeout hướng dẫn vào Settings. |
| **M1/M2 — Lỗi dễ hiểu** | `LatexErrorExplainer.java`, `OverleafFxApp.java` | Gọi explainer cho lỗi đã parse và mã lỗi startup/timeout/cancel; ghi `[EXPLAIN]`, nguyên nhân và gợi ý vào Build output theo ngôn ngữ UI. Sửa rule file thiếu để không nhận “File ... already exists” là “not found”. Đây là bộ rule offline, không gọi AI qua mạng và không tự sửa source. |
| **Testing/build** | `LatexErrorExplainerTest.java`, `client/pom.xml` | Chuyển annotation/assertion từ TestNG/JUnit 4 sang JUnit 5; thêm regression file tồn tại; bỏ hai dependency compile-scope TestNG và JUnit 4 `RELEASE` không còn được dùng. Maven phát hiện đầy đủ 9 test explainer. |
| **Testing** | `compiler/LatexCompilerServiceTest.java` (mới) | 8 test, dùng tiến trình Java fixture để kiểm tra lỗi start, đủ ba flag engine, giữ auxiliary files, exit 0 thiếu PDF, timeout snapshot, kill process con, Stop khi không giới hạn, discard/coalesce queue và chẩn đoán thiếu tool. Fixture không phải compiler TeX thật. |
| **Smoke thật** | `compiler/CompilerSmoke.java`, `ui/CompilerUiSmoke.java` (mới, test source) | Hai main chạy tường minh: PDF/cache/ba engine/TikZ/template tiếng Việt; và UI Settings/compile/explainer/Stop. Không chạy tự động bởi Surefire, cần toolchain thật; UI smoke cần desktop. Chỉ dùng thư mục dữ liệu thử mới. |

### 17.2 Toolchain trên máy kiểm tra

Không tìm thấy latexmk/ba engine trên PATH ban đầu. Đã tải **TinyTeX-1 v2026.03.02 Windows** từ [release chính thức](https://github.com/rstudio/tinytex-releases/releases/tag/v2026.03.02), xác nhận SHA-256 archive:

```text
af5fda147cfdccd071f9a8d3dbec3b6c30a1f6d77ff30d8b42e449b4e5d1c147
```

Giải nén vào **`D:\Overleaf-app\target\TinyTeX`** (bị Git ignore). Không sửa PATH hệ thống, registry hoặc dữ liệu thật `~/OverleafDesktop`. Cập nhật tlmgr của bộ portable; thêm `vntex`, `babel-vietnamese`, `babel-english`/`hyphen-english`, `pgf` và dependency cần thiết. Đây là toolchain local phục vụ kiểm tra, không được đóng vào JAR hay cam kết có sẵn trên máy member khác. Không chạy `mvn clean` ở root nếu cần giữ toolchain/cache/backup trong `target/`.

Version đã chạy: latexmk **4.87**, pdfTeX **1.40.29**, XeTeX **0.999998**, LuaHBTeX **1.24.0**, TeX Live **2026**. Bản portable không chứa mọi package TeX; dự án khác có thể cần cài bổ sung.

### 17.3 Kết quả kiểm chứng

**Maven: 72/72 đạt, 0 fail/error/skip** = 55 test hiện có trên nhánh + 8 compiler mới + 9 explainer đã được JUnit phát hiện. `LocalBackendTest` hiện 13 test, nên con số 52 trong README cũ đã lỗi thời. Test MongoDB không được chọn. Log: `target/compile-improvements-tests.log`.

Smoke thật qua chính `LatexCompilerService`:

- Ba engine đều sinh file có header PDF thật; thành công với tài liệu có công thức/tham chiếu, TikZ 100 nét và template của app có chữ tiếng Việt.
- Không sửa nội dung rồi compile lần hai: latexmk báo `Nothing to do`/`up-to-date`, xác nhận reuse cache. Sau sửa nhỏ vẫn compile thành công.
- Kết quả một lần chạy trên máy hiện tại (tài liệu nhỏ, một số bước chạy song song với UI smoke):

| Engine | Lần đầu (ms) | Không sửa (ms) | Sửa nhỏ (ms) |
| --- | ---: | ---: | ---: |
| pdfLaTeX | 486 | 246 | 358 |
| XeLaTeX | 3409 | 202 | 407 |
| LuaLaTeX | 5144 | 208 | 745 |

Đây là thời gian wall-clock end-to-end từ service, **không phải benchmark so với Overleaf**, không phải thống kê p50/p95 hoặc đo CPU/RAM. Chưa kiểm tra hình thức từng glyph/trang PDF bằng mắt. Dữ liệu/log: `target/compile-smoke-02/`, bảng `results.md`, `target/real-compile-smoke.log`.

**JavaFX smoke trên Windows đạt** (`COMPILER_UI_SMOKE_OK`): timeout âm bị từ chối; timeout 0 lưu được; diagnostics chạy nền; compile UI sinh PDF; source cố ý sai `\texbf` hiện `[EXPLAIN] Lệnh không tồn tại` trong Build output; Stop dừng vòng lặp TeX khi timeout không giới hạn và mở lại nút Recompile. Đã xác nhận lại trên bản cuối, dữ liệu riêng ở `target/compiler-ui-smoke-02`, log `target/compiler-ui-smoke.log`. Không chạy lại toàn bộ `UiSmoke` cũ.

Sau bổ sung kiểm tra đường dẫn không hợp lệ (bao gồm NUL) ngay trong `Options`, chạy lại chọn lọc **17/17 compiler/explainer test đạt**; log `target/compile-final-tests.log`. Cấu hình lưu từ UI chuẩn hóa đường dẫn absolute để không phụ thuộc working directory của project.

Sandbox từng gây `AccessDeniedException` khi javac đóng JDK `lib/ct.sym`; chạy kiểm tra ngoài sandbox đã giải quyết, không phải lỗi Java source. Các lần smoke thất bại trước khi thêm đủ package được giữ ở `target/compile-smoke-01` để truy vết, không dùng làm kết quả thành công.

### 17.4 Cách dùng và chạy lại

1. Mở app: `.\scripts\run-client.bat`.
2. Mở Settings → **Compilation / Biên dịch**. Trên máy hiện tại chọn **`D:\Overleaf-app\target\TinyTeX\bin\windows\latexmk.exe`**. Trên máy khác chọn latexmk của TeX Live/MiKTeX, hoặc để trống nếu đã có trên PATH.
3. Nhập timeout, **0** nếu muốn không giới hạn; bấm **Save compiler settings** rồi **Check saved toolchain**. Đổi engine qua Settings hoặc toolbar.
4. Về editor, F5/Ctrl+Enter để compile; bấm **Stop** nếu cần. Xem đường dẫn PDF và thời gian trong Build output/status bar.

Chạy test bằng Maven đã cài:

```powershell
mvn "-Dtest=LatexCompilerServiceTest,LatexErrorExplainerTest,MessageCodecTest,ResyncTransferTest,ConnectionScopeTest,NetworkingTest,NetworkClientTest,M4CollaborationTest,OfflineResyncTest,ProjectFileManagerTest,LocalBackendTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Main smoke cần build test-classes trước (`mvn -DskipTests test`), classpath gồm `client/target/classes`, `client/target/test-classes`, `common/target/classes`; UI thêm `server/target/classes` và JavaFX module path. Truyền `<absolute-latexmk-path> <new-output-directory>` cho `com.overleaf.client.compiler.CompilerSmoke` hoặc `com.overleaf.client.ui.CompilerUiSmoke`. Phải dùng thư mục output mới mỗi lần để không làm sai phép đo cold build. Các `.cmd` trong `target/` chỉ là helper riêng máy này, không phải script phát hành.

### 17.5 Giới hạn còn lại của tiêu chí Compile

- Chưa có Typst, tùy chọn shell-escape trong UI, quản lý nhiều phiên bản TeX tự động hoặc profiling CPU/RAM. Dùng executable path để chủ động chọn bộ TeX đã cài.
- PDF preview trong app vẫn là Canvas mô phỏng; PDF thật mở ngoài app. Cải thiện tiêu chí preview/SyncTeX là phần việc riêng, chưa làm trong mục này.
- Thời gian thực tế tùy máy/package/document. Không tuyên bố app nhanh hơn Overleaf, compile vô hạn theo mặc định hoặc tận dụng toàn bộ CPU.
- Error Explainer dựa vào rule và lỗi dạng file:line; lỗi log không có định dạng này có thể chỉ nhận giải thích chung theo exit code. Không phải AI chẩn đoán mọi lỗi.

## 18. Sửa lỗi “Không chạy được latexmk” từ ảnh kiểm thử

Ngày **01/11/2026**, cùng nhánh `feat/m1-gui-client` và working tree mục 17.

- Ảnh hiển thị thông báo startup cũ, không có dòng log executable/timeout và giải thích mới. Đây là lỗi khởi động compiler, không phải lỗi công thức LaTeX. Không tự kill các tiến trình Java đang chạy của người dùng; phải lưu file rồi khởi động lại app sau khi build.
- Thiếu sót trong bản mục 17: khi Settings để trống, chỉ gọi `latexmk` trên PATH; bộ portable trong `target/TinyTeX` không tự được tìm thấy. Các smoke trước đã truyền đường dẫn tường minh nên chưa bao phủ trường hợp này.
- **M2:** thêm `compiler/LatexToolchain.java`: ưu tiên đường dẫn đã cấu hình → `OVERLEAF_LATEXMK` → executable trên PATH → portable trong workspace/ancestor (hỗ trợ chạy từ module client) → các thư mục TinyTeX/MiKTeX phổ biến. Không ghi cấu hình cá nhân, không tải package tự động hoặc sửa PATH hệ thống.
- **M1:** `AppSettings.compileOptions()` dùng resolver; Settings cập nhật mô tả “để trống để tự tìm”. **M3/build:** launcher Windows thêm bin portable vào PATH của riêng tiến trình nếu có.
- Thêm **4 `LatexToolchainTest`** kiểm tra ưu tiên cấu hình/PATH, environment override, không có toolchain và tự tìm portable từ module client. `CompilerUiSmoke` đổi sang **không lưu đường dẫn** và kiểm tra tự nhận, compile UI, giải thích tiếng Việt và Stop. Kết quả **76/76 test đạt**, smoke UI đạt; dữ liệu thử mới `target/compiler-ui-smoke-03`, log `target/compiler-ui-smoke.log`.
- Đã đọc source test của người dùng để tái hiện: chỉ sao chép `Untitled project/main.tex` từ thư mục dự án local sang `target/user-compile-repro/`, không sửa file gốc. Tài liệu dùng nhiều package hơn mẫu nhỏ ở mục 17. Thêm vào TinyTeX portable: `setspace`, `fancyhdr`, `enumitem`, `multirow`, `pgfplots`, `algorithms`, `algorithmicx`, `mathtools`, `caption`, `listings`, `microtype` (các package phổ biến khác đã có).
- Bản sao nguyên nội dung trong thư mục mới **`target/user-compile-repro-02`** đã compile thành công bằng **pdfLaTeX**, qua `LatexCompilerService` và resolver với cấu hình trống. Log `target/user-compile-replay.log`: `USER_DOCUMENT_PDF_OK`, **6741 ms**; PDF `target/user-compile-repro-02/build/main.pdf`. Đây là kết quả trên chính tài liệu người dùng, không chỉ mẫu smoke nhỏ. Chưa kiểm tra hình thức từng trang bằng mắt.
- Khi vừa cài package sau một lần lỗi, latexmk có thể giữ kết quả lỗi cũ trong cache dù báo up-to-date. Lần tái hiện dùng thư mục copy mới để tách cache; nếu gặp trong app, dùng menu **Clean temporary files** rồi compile lại. Không tự xóa output/dữ liệu của người dùng.

Cách dùng trên máy hiện tại: lưu file, đóng cửa sổ app cũ, chạy `.\scripts\run-client.bat`. Có thể để đường dẫn latexmk trong Settings trống, app tự nhận `D:\Overleaf-app\target\TinyTeX\bin\windows\latexmk.exe`. Kiểm tra Build output phải có `[BUILD] latexmk: ...` và timeout. Nội dung preview Canvas vẫn là mô phỏng; xem PDF compile thật bằng menu mở PDF hoặc file `build/main.pdf`.

## 19. Preview đúng PDF và export cùng kết quả compiler

Ngày **01/11/2026**, theo yêu cầu tránh làm người dùng nhầm preview sai với lỗi LaTeX. Cùng nhánh `feat/m1-gui-client`, chưa commit/push. Mục này thay thế giới hạn Canvas ở các mục trước.

### 19.1 Hành vi sản phẩm

- **Preview đọc PDF thật từ lần compile thành công**, giữ nguyên nội dung/trang/kích thước/rotation/font trong PDF; bỏ hoàn toàn lớp parser/vẽ LaTeX `PdfCanvasPane` (~700 dòng) khỏi `OverleafFxApp`.
- Dùng [Apache PDFBox 3.0.8](https://pdfbox.apache.org/3.0/getting-started.html), chỉ thêm dependency vào client. Render ở worker nền, một trang mỗi lần, có bộ đếm trang, trước/sau, zoom và Fit. Không còn giới hạn 50 trang của bản Canvas; không nạp ảnh của toàn bộ tài liệu cùng lúc.
- **Export PDF…** trên preview hoặc menu `⋯` chép nguyên bytes của PDF thành công đang xem; không chụp màn hình, không tái dựng PDF từ ảnh hoặc parser. File xuất giữ chất lượng/vector/font của kết quả compiler.
- Thông báo **xanh**: PDF đã biên dịch thành công, có thể xuất. **Vàng**: nguồn đã đổi/đang compile/bản cũ. **Đỏ**: compile hoặc render thất bại. Nếu còn PDF thành công trước đó thì tiếp tục cho xem, nhưng ghi rõ là bản cũ và khóa xuất.
- Mới mở/đổi dự án phải compile để xác nhận PDF; không mặc nhiên tin file `build/main.pdf` cũ. Sửa bất kỳ file editor, đổi engine, sửa từ mạng hoặc thay dependency trên đĩa đều làm preview cũ. Thành công của compile thuộc workspace trước không được gán sang workspace mới.
- Lỗi compile không được đưa PDF tạm/partial vào preview hoặc Export. Menu mở PDF và đường mở `build/main.pdf` trong cây file cũng dùng kiểm tra phiên bản hiện tại; PDF đầu vào khác trong cây file vẫn mở bình thường.

### 19.2 File và trách nhiệm

| Vai trò | File | Nội dung |
| --- | --- | --- |
| **M2 — artifact PDF** | `client/.../compiler/PdfSnapshot.java` (mới) | Giữ snapshot byte bất biến, đọc/validate bằng PDFBox, render đúng page crop box/rotation, xuất bytes bằng temp + atomic move khi filesystem hỗ trợ. Compile sau ghi đè file build không làm đổi snapshot đang xem. |
| **M2 — tính mới của nguồn** | `compiler/ProjectInputs.java` (mới), `LatexCompilerService.java` | SHA-256 tên/nội dung file input trong project, loại `build/` và `.git/`. Compiler gắn fingerprint trước compile vào `CompileResult`; UI so lại khi nhận PDF. Không chỉ dựa vào timestamp/kích thước. I/O/hash chạy nền. |
| **M1 — viewer** | `ui/PdfPreviewPane.java` (mới) | Viewer PDF thật, điều khiển trang/zoom, trạng thái và nút Export. Render queue giới hạn, generation bỏ kết quả render cũ; render lỗi hiển thị lỗi và khóa xuất. |
| **M1 — điều phối** | `ui/OverleafFxApp.java` | Bỏ nested Canvas/parser, nối compile thành công → snapshot → viewer; revision + workspace generation; stale/error state; save dialog Export PDF, kiểm tra fingerprint và FX state trước xuất; mở external viewer từ snapshot tạm thay vì file build có thể bị ghi đè. |
| **M2 — watcher** | `workspace/WorkspaceWatcher.java` | Theo dõi thêm hình/font/PDF dữ liệu đầu vào, `.csv/.dat`, style bibliography, `.lua/.tikz`…; vẫn bỏ qua thư mục build/ẩn. Tạo/đổi tên/xóa file từ UI cũng invalidates PDF. |
| **M1/build** | `client/pom.xml` | PDFBox 3.0.8, transitively pdfbox-io/fontbox/commons-logging. Không thêm dependency PDFBox vào common/server hoặc wire protocol. |
| **Testing** | `compiler/PdfSnapshotTest.java` (mới) | 5 test: kích thước/trang quay/pixel thật; export byte-identical dù compiler ghi đè output; PDF/page/zoom sai; fingerprint bỏ qua build nhưng nhận sửa dependency cùng size/mtime; rename/delete binary asset. |
| **Testing UI** | `ui/PdfPreviewUiSmoke.java` (mới) | Main cần desktop + TeX thật, dùng bản sao source và home mới. So mẫu pixel của các trang preview với PDF renderer; file xuất giống byte-for-byte, stale/error recovery, đổi project. Không tự chạy bằng Surefire. |

Export kiểm tra lại input trên đĩa và revision trên FX thread trước ghi. Lỗi ghi file (quyền/disk full) báo riêng, không tự kết luận nguồn sai. Bộ kiểm tra input chỉ bao phủ file trong project; không theo dõi tự động thay đổi TeX distribution/font hệ thống hoặc dependency nằm ngoài project. Nếu thay các thành phần đó thì compile lại.

### 19.3 Kiểm chứng

- Maven chọn lọc **81/81 đạt** = 76 test mục 18 + 5 `PdfSnapshotTest`; không gọi MongoDB. Log `target/pdf-preview-tests.log`.
- Smoke JavaFX trên **bản sao đúng tài liệu test người dùng**, compile bằng pdfLaTeX ra **7 trang**. Mẫu pixel của trang 1–4 khớp với render PDF; ảnh minh chứng **`target/pdf-preview-ui-02/preview.png`** là trang 4 có công thức/bảng đúng layout.
- File **`target/pdf-preview-ui-02/exported.pdf`** giống byte-for-byte với output compiler được preview; có kiểm tra `Files.mismatch(...) == -1`.
- Chỉnh source khóa xuất ngay; cố xuất stale PDF không sinh file; compile lỗi giữ snapshot thành công cũ, không nhận PDF partial; sửa lỗi và compile lại thay snapshot; đổi project xóa snapshot. Kết quả `PDF_PREVIEW_UI_SMOKE_OK`, log `target/pdf-preview-ui.log`.
- Đã xem ảnh chụp UI trang có công thức/bảng. Không tuyên bố chứng minh mọi PDF/font hoặc mọi lỗi nội dung LaTeX: viewer thể hiện kết quả compiler; không kiểm tra đúng/sai về mặt khoa học của công thức. Các cảnh báo LaTeX vẫn cần đọc trong Build output.
- Source/dữ liệu gốc của người dùng không bị sửa; helper, ảnh, PDF thử và cache đều ở `target/` bị ignore. Chưa chạy toàn bộ `UiSmoke` cũ; UI PDF có smoke riêng như trên.

### 19.4 Cách sử dụng và chạy lại

Lưu file và đóng app cũ, chạy `.\scripts\run-client.bat`, mở dự án rồi F5/Ctrl+Enter. Chờ trạng thái preview xanh, dùng `‹`/`›` chuyển trang, zoom/Fit; **Export PDF…** để chọn file đích `.pdf`. Sau sửa source, compile lại trước khi xuất. Build output/preview phân biệt thất bại với bản đã compile thành công.

Chạy test: lệnh ở README có thêm `PdfSnapshotTest`. Chạy smoke: build test-classes, classpath gồm client/main + client/test + common + server + dependency PDFBox và module-path JavaFX; main `com.overleaf.client.ui.PdfPreviewUiSmoke <source.tex> <new-home>`. Helper riêng máy này: `target/run-pdf-preview-ui.cmd`; phải đổi home mới mỗi lần.

Giới hạn hiện tại: preview raster cho việc hiển thị (PDF export vẫn nguyên bản vector), một trang mỗi lần; ảnh render giới hạn khoảng 12 triệu pixel và scale tối đa 3 để tránh hết bộ nhớ; snapshot PDF tối đa 128 MiB, vượt giới hạn báo lỗi rõ ràng. Chưa có chọn/copy text trên PDF, annotation hoặc SyncTeX hai chiều; đó là phần phát triển tiếp, không dùng parser Canvas thay thế.

## 20. Sửa nguyên nhân chữ tiếng Việt lỗi khi đổi engine

Ngày 01/11/2026. Tái hiện trên bản sao tài liệu người dùng: pdfLaTeX sinh PDF đúng; XeLaTeX và LuaLaTeX đều trả exit 0 nhưng có hàng loạt `Missing character` cho `ệ`, `đ`, `ư`… vì tài liệu dùng cố định `inputenc`, `fontenc[T5]`, `lmodern` của luồng pdfLaTeX. Xe/Lua đọc UTF-8 trực tiếp; cần nhánh Unicode với fontspec. Nguồn: [LaTeX Project về inputenc](https://www.latex-project.org/news/latex2e-news/ltnews.pdf), [fontspec](https://ctan.org/pkg/fontspec).

- Không tự sửa tài liệu gốc trong `~/OverleafDesktop`. Bản sao đã sửa nằm ở **`target/vietnamese-fixed/main.tex`**: `iftex` tách pdfLaTeX (UTF-8/T5/lmodern) với Xe/Lua (`fontspec`, Latin Modern Roman OpenType cùng font bold/italic). Chọn font theo **tệp `.otf`** để portable Windows không phụ thuộc font đã đăng ký trên hệ điều hành. `literate` trong listings có `{ạ}{{a}}1` làm mất dấu do chính mapping; bản sao đổi thành `\d{a}`.
- **M1/M2:** `LocalBackend.template()` sinh preamble nhánh engine, bổ sung chọn font OpenType theo tệp. Chỉ dự án mới nhận template mới; tài liệu cũ cần sửa khai báo riêng.
- **M2:** `LatexCompilerService` thêm `EXIT_MISSING_GLYPHS = -5`; dù exit 0, nếu console hoặc file TeX `.log` có `Missing character:` thì không gọi onSuccess. Kiểm tra `.log` cả khi latexmk dùng cache. Các cảnh báo thường như hyphenation không bị coi là mất ký tự.
- **M1:** status báo PDF thiếu ký tự; đường onError giữ preview cũ có cảnh báo và khóa Export. `LatexErrorExplainer.explainExit` thêm hướng dẫn T5/fontspec theo engine, tiếng Việt/Anh.
- Thêm 2 compiler regression và 1 explainer regression: tổng chọn lọc từ 81 → **84/84 đạt**; log `target/pdf-preview-tests.log`. Không tính smoke thật vào số JUnit.
- Thêm main test **`compiler/VietnamesePdfSmoke.java`**: compile bản sao qua ba engine, kiểm tra không mất glyph, trích xuất PDF rồi chuẩn hóa NFC, kiểm tra các chuỗi `Tài liệu`, `Định lý`, `Tiếng Việt`; render trang 1 để xem.
- Trong quá trình kiểm tra có crash native XeTeX Windows khi chạy nền. Các thử nghiệm đổi launcher/wrapper không giải quyết ổn định nên **đã bỏ khỏi source runtime**. Bộ TinyTeX portable được cập nhật `xetex` cùng các dependency LaTeX/kernel, chạy lại font cache; regenerate format LuaLaTeX/pdfLaTeX để tránh `Mismatched LaTeX support files`. Chỉ sửa bộ TeX trong `target/`, không đổi cài đặt toàn hệ thống. Log cập nhật `target/xetex-update.log`, format `target/portable-formats.log`.
- **Kiểm chứng cuối cùng sau cập nhật:** `VietnamesePdfSmoke` chạy qua chính service, cả pdfLaTeX/XeLaTeX/LuaLaTeX đều đạt: **7 trang, 0 missing glyph, đủ 3 chuỗi tiếng Việt có dấu**. Kết quả `target/vietnamese-fixed-check.log`; PDF, text trích xuất và ảnh trang 1 nằm trong `target/vietnamese-fixed-check-09/{PDFLATEX,XELATEX,LUALATEX}/`. Đây là kiểm chứng bản source đã sửa; bản gốc dùng T5 cố định vẫn cần đổi preamble.

Nếu thấy lỗi tiếng Việt ở tài liệu cũ, sửa preamble theo bản sao tương thích; việc đổi compiler trong toolbar không tự chuyển đổi source LaTeX. Không đánh đồng “có file PDF” hoặc exit 0 với “không rơi ký tự”.

## 21. Outline có thể kéo lên/xuống thay vì chiều cao cố định

Ngày 01/11/2026, theo yêu cầu mới của người dùng.

- **M1:** `OverleafFxApp.createLeftPanel()` đặt cây file và Outline trong **SplitPane dọc** (`#file-outline-split`). Kéo thanh ngang lên để mở rộng Outline, kéo xuống để thu lại; cây file và Outline cuộn riêng. Bỏ `prefHeight=180`, `minHeight=120`, `maxHeight=260` cũ; giữ min vùng Outline khoảng 80 px và vùng file khoảng 100 px để tránh mất hẳn nội dung.
- **M1 — UI:** thêm ký hiệu `↕`, tooltip hướng dẫn kéo; CSS `.sidebar-split` có thanh kéo đủ dày và highlight khi hover.
- **M1 — Settings:** `AppSettings.OUTLINE_SPLIT` lưu tỷ lệ sau **mouse release**, không ghi file mỗi pixel khi drag. Khôi phục khi tạo sidebar, clamp giá trị và bỏ NaN. Listener không gọi lại applyEditorSettings khi lưu divider, nên không reset engine/cỡ chữ do thao tác resize.
- **UI smoke thật:** `ui/OutlineUiSmoke.java` dùng home riêng, mở app JavaFX, thay divider và kiểm tra chiều cao tăng/giảm + preference lưu + engine không bị đổi. Kết quả `OUTLINE_UI_SMOKE_OK`, số đo cửa sổ kiểm tra: **241 → 422 → 133 px**, saved ratio ~0.3002. Log `target/outline-ui.log`, dữ liệu `target/outline-ui-02`.
- Kiểm chứng dựa trên layout/control JavaFX thật và sự kiện thả chuột; chưa phải test Robot kéo bằng tọa độ màn hình. Nút và giới hạn chiều cao đều do SplitPane chuẩn xử lý.

Sử dụng: lưu file, đóng app cũ, chạy `.\scripts\run-client.bat`; ở tab **Files**, kéo thanh ngang ngay trên tiêu đề **OUTLINE**. Tỷ lệ được lưu local để dùng lại.

## 22. Menu tạo dự án, nhập tài liệu và thư mục lưu tùy chọn

Ngày **02/11/2026**, nhánh `feat/m1-gui-client`, nền `b89dbc5` cùng các thay đổi local ở mục 17–21. Chưa commit/push. Đây là chức năng desktop local; không phải API lưu dự án lên MongoDB hoặc server cộng tác.

### 22.1 Đã triển khai và cách dùng

- **Dự án mới / New project** mở menu: dự án trống, nhập ZIP, Word `.docx`, Markdown, kho GitHub công khai, dự án ví dụ, thêm mẫu và thêm đơn vị. Các nhãn, hướng dẫn, lỗi trong luồng mới có tiếng Anh/Việt theo ngôn ngữ UI.
- **Thêm mẫu dự án** có bài báo, sách, thư trang trọng, bài tập, poster, thuyết trình, báo cáo, CV và luận văn; **Xem tất cả** mở bộ chọn. Các mẫu tạo file LaTeX thật với class/bố cục phù hợp, hỗ trợ tiếng Việt và ba engine. Dự án ví dụ có `main.tex`, `sections/introduction.tex`, `references.bib` và citation thật.
- Hộp thoại tạo/nhập cho sửa trực tiếp đường dẫn hoặc dùng DirectoryChooser. Đường dẫn là **thư mục cha**; app tạo thư mục con theo tên dự án, tự thêm hậu tố khi trùng tên, không ghi đè dự án cũ. Có thể cuộn nội dung hộp thoại khi cửa sổ thấp.
- **Cài đặt → Dự án** hoặc **Dự án mới → Thư mục dự án mặc định…** đổi vị trí dùng cho lần tạo tiếp theo. Các dự án cũ không bị chuyển theo và vẫn có trong danh sách.
- Trang chủ → nhấp phải dự án → **Đổi thư mục lưu…** chuyển dự án hiện có. Sao chép qua staging ở đích, đối chiếu bytes từng file, rồi giữ thư mục nguồn trong `<thư mục cha cũ>/.overleaf-moved-backups/<tên>-<UUID>`. Cách này không cần rename trực tiếp giữa hai ổ đĩa; kiểm thử hiện tại chưa xác nhận bằng hai ổ vật lý khác nhau. Không tự dọn bản phục hồi.
- **Thêm đơn vị…** lưu tên trường/cơ quan local, điền vào tác giả của template mới, escape ký tự LaTeX. Không liên kết tài khoản với tổ chức trên Overleaf web.

### 22.2 File, folder và vai trò bàn giao

| Nhóm / member phụ trách | File và phần việc mới |
| --- | --- |
| M1 — `client/.../ui/` | `HomeView`: menu, form, template gallery, chọn/chuyển thư mục, affiliation, task nền có hủy và lỗi song ngữ; `SettingsView`: trang Dự án; `AppSettings`: truy cập ProjectGateway; `UiModal`: tooltip đóng bản dịch; CSS menu và vùng cuộn |
| M1 — tích hợp workspace | `OverleafFxApp`: nhớ dự án bằng đường dẫn đầy đủ thay vì chỉ tên; cập nhật watcher/file manager khi đổi tên/chuyển, bỏ PDF cũ của vị trí trước |
| M1/M2 — `ui/backend/` local | `NewProjectRequest`: loại tạo/nhập và vị trí; `ProjectGateway`: contract mới; `LocalBackend`: catalog vị trí ngoài mặc định, tạo staging, đổi mặc định, rename/delete/move tại vị trí thật; `ProjectFiles`: copy, xác minh, dọn staging sở hữu bởi app |
| M2 — nhập/template | `ProjectImports`: ZIP, Git clone, Pandoc và giới hạn; `ProjectTemplates`: 9 nhóm mẫu + ví dụ; `ProjectFailure`: mã lỗi và thông điệp Anh/Việt |
| M2 — `compiler/` | `CompileWorkspace` + `LatexCompilerService`: tránh lỗi latexmk/Perl trên Windows khi đường dẫn dự án chứa ký tự Unicode bằng bản sao tạm ASCII; PDF thành công và log trả về `build/` thật, fingerprint vẫn lấy nguồn gốc |
| M5 — kiểm thử / cả nhóm đọc | `ProjectWorkflowTest` (12 test), `CompileWorkspaceTest` (2 test); `ProjectImportSmoke`, `NewProjectUiSmoke`; cập nhật `UiSmoke` để chọn mục Dự án trống sau khi mở menu mới |

Phân công trên là phạm vi module cần bàn giao, không khẳng định member đã tự viết các thay đổi trong phiên AI này. Không sửa giao thức M3/M4 hay schema MongoDB M5.

### 22.3 Dữ liệu và công cụ

- Catalog `~/OverleafDesktop/.app/projects.properties` lưu đường dẫn dự án; `.app/settings.properties` lưu thư mục mặc định, last-project bằng đường dẫn và affiliation. Catalog giữ được hai dự án cùng tên ở hai nơi. Không nên chuyển bằng Explorer vì app chưa tự dò vị trí bị đổi bên ngoài.
- Nhập ZIP hỗ trợ bọc ngoài một folder, giữ tài nguyên; cần `main.tex` ở root hoặc root `.tex` có `\documentclass` để tạo bản `main.tex`. Chặn traversal, đường dẫn tuyệt đối, Windows ADS/device names, mục trùng không phân biệt hoa/thường; tối đa **10.000 mục / 500 MiB giải nén**. Thất bại không công bố dự án dở.
- Word chỉ `.docx`, Markdown UTF-8; nguồn tối đa **50 MiB**, giữ bản gốc trong `source/`. Pandoc xuất LaTeX standalone, trích media, font T5/Unicode; chuyển đổi có thể mất định dạng phức tạp. Không hứa PDF giống từng pixel với Word. Tham khảo [Pandoc Manual](https://pandoc.org/MANUAL.html).
- Pandoc tìm theo `OVERLEAF_PANDOC`, `target/pandoc/pandoc.exe`, rồi PATH. Đã chuẩn bị bản portable **3.13** từ [release chính thức](https://github.com/jgm/pandoc/releases/tag/3.13); SHA-256 ZIP `23e1f2e978a3df80db8bf65c9c59534f41a23140480cfb4f783a882ab7dc6a4e`. Portable ở `target/` chỉ phục vụ máy hiện tại, không được commit.
- GitHub cần Git trên PATH; chỉ nhận URL HTTPS `github.com/owner/repo[.git]`, không credential/query/fragment, shallow clone, không recurse submodule, tắt credential prompt/hooks cục bộ. Chưa hỗ trợ kho private hay URL trỏ vào nhánh/subfolder. Không chạy script của kho sau nhập.
- Tiến trình nhập có timeout **120 giây**, xử lý nền, hủy sẽ dừng process tree; không tự compile dự án vừa tải. Tài liệu/engine có thể cần cài thêm package TeX vào môi trường người dùng.
- Windows với đường dẫn Unicode dùng workspace tạm để tránh lỗi `File::Path Invalid argument` của Perl/latexmk. Build tạm này chưa giữ cache giữa các lần compile; thư mục temp của Java cần đường dẫn ASCII. Đường dẫn ASCII bình thường vẫn compile tại chỗ và giữ cache. PDF lỗi không thay bản PDF tốt trước đó.

### 22.4 Kiểm chứng thực tế

- **98/98 JUnit chọn lọc đạt**, không skip/failure/error: common 9 + server 21 + client 68. Log `target/new-projects-tests.log`; lệnh README đã gồm `ProjectWorkflowTest`, `CompileWorkspaceTest` và regression compiler/workspace/network trước đó.
- **PDF thật:** `ProjectImportSmoke` tạo và compile thành công cả **9 template tiếng Việt** trong đường dẫn có dấu, dự án ví dụ có BibTeX, chuyển và compile Word/Markdown có tiếng Việt/công thức; PDFBox trích được `Tiếng Việt`, bản nguồn không đổi bytes. Clone được kho công khai `https://github.com/gcstinchfield/latex-paper-template`, có `main.tex` và `.git`. Log `target/project-import-smoke.log`, kết quả `PROJECT_IMPORT_SMOKE_OK`; dữ liệu `target/project-import-smoke-04`.
- **JavaFX thật:** `NewProjectUiSmoke` kiểm menu Anh/Việt và đủ nhóm mẫu, tạo dự án ở thư mục riêng, đổi mặc định vẫn thấy dự án cũ, chuyển dự án và lưu thư mục qua Settings. Kết quả `NEW_PROJECT_UI_SMOKE_OK`; log `target/new-project-ui-smoke.log`, dữ liệu `target/new-project-ui-smoke-03`. Dùng control/event thật với home cô lập, chưa phải Robot bấm bằng tọa độ. Không chạy lại toàn bộ `UiSmoke` cũ trong lần này.
- Chạy smoke bằng main class tương ứng trong `client/src/test/java`, classpath client test/main + common + PDFBox; smoke UI thêm JavaFX module path. Luôn truyền thư mục test home **chưa tồn tại**; GitHub smoke tùy chọn URL repo công khai. Các `.cmd` thử nghiệm dưới `target/` là helper local, không phải script phân phối.

Sau cập nhật, đóng app cũ rồi chạy `.\scripts\run-client.bat`. Không cần chuyển hoặc xóa dự án người dùng đang có để bật tính năng.

## 23. Workspace: thêm thư mục, tệp, upload và lấy tệp từ nguồn khác

Ngày **02/11/2026**, cùng working tree `feat/m1-gui-client`; chưa commit/push. Hoàn thành yêu cầu hộp thoại Add files theo ảnh và thao tác trong thư mục workspace. Giữ các thay đổi compiler/PDF/project từ trước.

### 23.1 Hành vi và sử dụng

- Sidebar có **Thêm tệp / Add files**, **Thư mục mới / New folder**, **Tải lên / Upload**. Nhấp phải thư mục cũng có ba thao tác này. Thư mục đang chọn (hoặc thư mục cha của tệp đang chọn) được điền sẵn làm đích; có thể chọn/nhập thư mục khác, để trống dùng gốc.
- Hộp thoại rộng có menu bên trái: **Tệp mới**, **Thư mục mới**, **Tải lên**, **Từ dự án khác**, **Từ URL bên ngoài**, **Thư viện tài liệu (.bib)**; tên/hướng dẫn/lỗi/thao tác mới có Anh/Việt. Đổi ngôn ngữ cập nhật cả các nút workspace mới và menu thêm tệp. Thẻ nội dung có vùng cuộn.
- Tạo tệp trống; nếu tên cuối chưa có đuôi, thêm `.tex`. Chấp nhận đường dẫn con như `chapters/intro.tex`, tự tạo thư mục cha. Tạo thư mục rỗng vẫn hiện ngay trong cây và có thể nhấp phải để thêm tệp.
- Upload nhiều tệp hoặc cả thư mục qua FileChooser/DirectoryChooser; kéo thả từ hệ điều hành hoặc **Dán tệp / ảnh**. `Ctrl+V` khi vùng thả có focus cũng nhận clipboard. Danh sách chờ cho bỏ mục trước khi xác nhận upload. Upload folder giữ tên folder, cấu trúc và folder rỗng; file nhị phân giữ nguyên bytes. Ảnh clipboard mã hóa PNG với tên riêng.
- **Từ dự án khác:** chọn trong các dự án local đã đăng ký, chọn tệp, đổi tên tại dự án đích. Bật **Chọn từ tệp đầu ra (build)** để lấy PDF/log/tệp build. Sao chép độc lập, không tạo liên kết hay tự cập nhật khi nguồn đổi.
- **URL:** nhập HTTPS trực tiếp và tên đích; theo tối đa 5 redirect HTTPS, không nhận credentials trong URL, yêu cầu HTTP 200; tối đa 100 MiB, connect/read timeout 15 giây. Đây là tải tệp, không xử lý trang chia sẻ/đăng nhập cloud, không thực thi tệp tải về. Hủy dừng khi vòng đọc kiểm tra ngắt; một read đang chờ có thể cần đến timeout.
- **Thư viện tài liệu:** tải file `.bib` đã xuất từ ReadCube/Zotero/Mendeley. Không có OAuth hay đồng bộ tài khoản các dịch vụ đó; không hiển thị các kết nối giả. Các folder/tài nguyên mới lưu local, không mở rộng phòng cộng tác vốn chỉ đồng bộ `main.tex`.

### 23.2 File và vai trò

| Phần / member phụ trách bàn giao | Thay đổi |
| --- | --- |
| M1 — `client/.../ui/AddFilesDialog.java` | Hộp thoại dùng chung các điểm vào, menu trái, thư mục đích, các form, queue upload, drag/drop, clipboard PNG, chọn dự án/đầu ra, URL, `.bib`; Task nền cho copy/download, lỗi dịch và hủy |
| M1 — `OverleafFxApp.java` | Nút workspace và context menu thư mục; dùng selected directory; hiển thị thư mục rỗng, tệp ảnh/font/tài nguyên, tới 32 cấp; bỏ symlink khỏi cây; file nhị phân không đưa vào text editor, mở ngoài bằng nhấp đúp; thêm tệp làm PDF cũ không còn hợp lệ |
| M1 — `UiModal.java`, theme CSS | `width()` responsive cho hộp thoại rộng, menu trái/vùng thả/vùng cuộn theo style app |
| M2 — `client/.../workspace/WorkspaceFiles.java` | Kiểm đường dẫn, tạo folder/file, lập kế hoạch nhập, staging, không ghi đè, rollback các mục mới khi lỗi, giới hạn bytes/số mục, download HTTPS |
| M5 — kiểm thử / cả nhóm đọc | `WorkspaceFilesTest` (12 test), `WorkspaceFilesUiSmoke`; cập nhật control mới trong `UiSmoke` để test cũ gọi đúng tên field/nút |

Đây là phạm vi bàn giao theo module, không xác nhận member đã tự viết thay đổi trong phiên AI. Không đổi protocol M3/M4 hay database.

### 23.3 Tính toàn vẹn và giới hạn

- Đường dẫn nằm trong dự án; từ chối traversal, tên rỗng/ẩn, device name Windows, ADS, segment `build`, ký tự cấm và symlink đích. Upload không theo symlink nguồn. Root `build/` được dành cho compiler; muốn lấy output từ dự án khác cần lưu với tên ngoài build.
- Không ghi đè file/folder đã có; từ chối tên trùng trong cùng lô kể cả khác hoa/thường. Kiểm tra toàn bộ kế hoạch trước khi ghi; dữ liệu copy vào staging ẩn `.overleaf-upload-*` trước khi công bố. Nếu công bố lỗi, chỉ rollback các file/folder vừa tạo, giữ mục cũ. Staging lỗi còn sót bị ẩn khỏi cây; không xóa dữ liệu nguồn.
- Mỗi lô tối đa **10.000 mục / 500 MiB**, thư mục tính vào số mục; ảnh dán giới hạn **25 triệu pixel** trước khi mã hóa. Chưa có luồng ghi đè có xác nhận; gặp trùng cần đổi tên hoặc bỏ mục trong queue.
- Không tự thêm `\input`/`\includegraphics`/bibliography vào `main.tex`, không tự compile sau upload. Người dùng thêm lệnh sử dụng tài nguyên rồi biên dịch như bình thường.
- Build output chỉ hiện PDF trong cây chính; chọn output để sao chép có thể lấy các file khác. File không thuộc nhóm text hỗ trợ được mở bằng ứng dụng hệ điều hành khi nhấp đúp; app không tự mở binary vừa upload.

### 23.4 Kiểm chứng

- **110/110 JUnit chọn lọc đạt**, không failure/error/skip: common 9 + server 21 + client 80; `target/workspace-files-tests.log`. Test mới kiểm Unicode/thư mục rỗng, path traversal/tên cấm, không ghi đè, giữ hierarchy/bytes nhị phân, conflict cả lô, trùng hoa/thường, copy output đổi tên, chặn copy folder chứa project, rollback publish lỗi, giới hạn số mục, URL sai và hủy.
- `WorkspaceFilesUiSmoke` trên JavaFX thật: tạo folder và file đúng folder đang chọn, upload folder có ảnh và folder rỗng, đối chiếu bytes ảnh, encode ảnh dán PNG và đối chiếu pixel, sao chép `.tex` tiếng Việt và PDF output, từ chối trùng tên, bản dịch VI/EN và cập nhật sidebar. Đã tải HTTPS thật README Pandoc 3.13 vào dự án thử và kiểm nội dung. Log `target/workspace-files-ui-smoke.log`: `HTTPS_DOWNLOAD_OK`, `WORKSPACE_FILES_UI_SMOKE_OK`.
- Home cô lập `target/workspace-files-ui-smoke-04`, ảnh riêng scene ứng dụng: `upload-dialog.png`, `copy-dialog.png`, `new-file-dialog.png`; đã kiểm tra bố cục. Smoke gọi control/event và handler với nguồn test, không thao tác clipboard thật của người dùng, chưa dùng Robot kéo thả hoặc tự bấm native file chooser. Không chạy lại toàn bộ `UiSmoke` cũ.
- Chạy main `WorkspaceFilesUiSmoke <home-chưa-tồn-tại> [url-test]` với JavaFX module path và client/test/common/server/PDFBox classpath như smoke mục 22. URL test tùy chọn đang kiểm nội dung có `pandoc`; dùng `https://raw.githubusercontent.com/jgm/pandoc/3.13/README.md`. Script helper local `target/run-workspace-files-ui-smoke.cmd` không được Git theo dõi.

Đóng tiến trình app cũ, chạy `.\scripts\run-client.bat` để nhận code mới. Các dự án đang có không cần nhập lại.

## 24. Sửa biên dịch ZIP nhiều chương và thông báo exit code 12

Ngày **02/11/2026**, theo báo lỗi khi nhập `D:\download\latex-test-project-v2.zip`, cùng working tree chưa commit/push. Chỉ đọc ZIP gốc, nhập/compile các bản sao dưới `target/`, không sửa tệp gốc hoặc dự án đang dùng.

### 24.1 Nguyên nhân đã tái hiện

1. TinyTeX portable ban đầu thiếu `csquotes.sty`; log có `! LaTeX Error: File 'csquotes.sty' not found` trước `preamble.tex:15: Emergency stop`. ZIP còn yêu cầu titlesec, siunitx, biblatex/Biber, cleveref… Đây là phụ thuộc môi trường TeX, không phải hỏng ZIP/UTF-8 khi giải nén.
2. Sau bổ sung gói, `main.tex:42` lỗi `I can't write on file 'chapters/01-introduction.aux'`. Compiler dùng `-outdir=build` nhưng chỉ tạo `build/`, chưa tạo `build/chapters`, `build/appendix`; TeX không tự tạo các thư mục con cho `\include`.
3. Parser cũ chỉ đọc `file:line: message`, bỏ mất lỗi bắt đầu bằng `!`, nhưng nhận cả `runscript.tlu:933: command failed with exit code 12`. UI giải thích thông báo tổng kết của launcher như lỗi tài liệu nên gợi ý ngoặc nhọn không phù hợp.

### 24.2 Thay đổi và vai trò

- **M2 — `CompileWorkspace.prepareOutputDirectories`:** tạo cây thư mục đầu ra tương ứng các thư mục nguồn trước khi khởi chạy TeX; bỏ qua `build/`, thư mục ẩn và không đi theo symlink. Dùng cho cả build tại chỗ và bản staging ASCII của đường dẫn Unicode. Không thay đổi `\include`/tệp nguồn.
- **M2 — `LatexCompilerService`:** gọi chuẩn bị output; nếu TeX log cũ có lỗi thì thêm `-g` cho lần retry để tránh latexmk giữ thất bại sau khi thư mục/gói đã được sửa. Build bình thường không lỗi vẫn dùng cache. Khi output thất bại không có lỗi phân tích được, bổ sung chẩn đoán từ TeX log (đọc tối đa 16 MiB).
- **M2/M1 — parser và UI:** nhận cả lỗi `!`, gắn vị trí từ dòng chẩn đoán tiếp theo nếu có; bỏ lỗi launcher `runscript.tlu` và thông báo `command failed with exit code`, bỏ fatal/emergency phụ khi đã có nguyên nhân, gộp lỗi lặp. UI hỗ trợ lỗi chưa biết file/số dòng, không in vị trí giả `:0`.
- **M1 — `LatexErrorExplainer`:** giải thích lỗi không ghi được `.aux`; nếu nhận riêng thông báo wrapper thì nói rõ exit code chỉ là tổng kết và hướng dẫn tìm lỗi đầu tiên, không đoán thiếu ngoặc.
- **M5 — kiểm thử:** thêm 7 regression (4 compiler/parser/retry, 1 output-directory, 2 explainer); `ImportedZipCompileSmoke` nhận ZIP gốc và home thử mới, chạy ba engine, kiểm PDFBox và đối chiếu bytes toàn bộ tệp nhập.
- **Môi trường local:** bổ sung vào `target/TinyTeX` các gói còn thiếu `csquotes`, `titlesec`, `siunitx`, `biblatex`, `biber` (kèm binary Windows), `cleveref`, `logreq`; xác nhận pgfplots/listings/enumitem/setspace/fancyhdr/mathtools/caption/booktabs/tools đã có. `xspace.sty` thuộc gói `tools`, không phải tên package tlmgr riêng. Không đổi PATH hệ thống hoặc tự tải gói khi người dùng bấm compile; portable không được Git theo dõi.

### 24.3 Kiểm chứng và bằng chứng

- Tái hiện thiếu gói: `target/user-zip-v2-repro-01/PDFLATEX.log`; tái hiện lỗi aux sau bổ sung gói: `target/user-zip-v2-repro-02/PDFLATEX.log`.
- ZIP nguyên gốc nhập qua `LocalBackend`/`ProjectImports`, compile bằng service thật: **pdfLaTeX, XeLaTeX, LuaLaTeX đều thành công, mỗi PDF 13 trang**. PDFBox tìm được `Dự án kiểm thử`, `Giới thiệu`, `Dữ liệu thô`, `Tài liệu tham khảo`, không có citation `[?]`; `build/main.bbl` có entry `knuth1984` của Biber. Các tệp nguồn/ảnh/CSV sau import và compile vẫn đúng bytes trong ZIP.
- Log `target/imported-zip-smoke.log`: `IMPORTED_ZIP_COMPILE_SMOKE_OK`; PDF/text/log từng engine ở `target/user-zip-v2-verified-01/evidence/` (`PDFLATEX.pdf`, `XELATEX.pdf`, `LUALATEX.pdf`). Smoke này kiểm tài liệu thật, không tính vào số JUnit.
- **117/117 JUnit chọn lọc đạt**, common 9 + server 21 + client 87, không failure/error/skip; log `target/workspace-files-tests.log`. Lệnh README giữ nguyên nhóm test vì các regression mới nằm trong class đã có.
- Main smoke có thể chạy `ImportedZipCompileSmoke <zip> <home-chưa-tồn-tại>` với classpath client/main/test + common + PDFBox; đây là fixture riêng cho ZIP nhiều chương người dùng, kiểm các cụm tiếng Việt và BibLaTeX nêu trên, không phải validator chung cho mọi ZIP.

Cách dùng sau sửa: đóng app cũ, chạy `.\scripts\run-client.bat`, mở lại dự án đã nhập và **Recompile**. Không cần sửa nguồn hoặc nhập lại ZIP. Các phụ thuộc portable chỉ được cài trên máy kiểm thử; máy mới cần cài TeX và các package tương ứng.

## 25. Preview PDF cuộn liên tục và liên kết nội bộ

Ngày **02/11/2026**, theo ảnh và yêu cầu cuộn qua các trang, bấm mục lục/tham chiếu để tới tiêu đề. Cùng working tree `feat/m1-gui-client`, chưa commit/push.

### 25.1 Sử dụng và hành vi

- Sau Recompile thành công, preview xếp các trang thật theo chiều dọc, có khoảng cách và bóng giữa trang. Cuộn chuột/thanh cuộn để xem trang tiếp theo, không cần nhấn nút chuyển từng trang. Bộ đếm cập nhật theo trang chiếm nhiều vùng nhìn nhất. Vẫn giữ `‹`/`›`, zoom và Fit.
- Bấm liên kết mục lục, tham chiếu chương/hình/bảng trong PDF để tới đúng trang và tọa độ dọc của tiêu đề/đích liên kết. `↶` quay lại vị trí trước khi bấm liên kết; lịch sử tối đa 100 vị trí, reset khi đổi PDF/dự án.
- Lấy link annotation/đích thật từ PDF: direct destination và GoTo, kể cả named destinations của `hyperref`. Không suy ra đích từ màu chữ hay đoán tiêu đề. Tài liệu cần tạo link sẵn trong PDF; thường dùng `\usepackage[colorlinks=true]{hyperref}` và compile đủ các lượt.
- Chưa mở URI website, tệp ngoài hoặc chạy action/script của PDF. Các destination không có tọa độ heading được đưa về đầu trang; không phải SyncTeX giữa source và PDF, không bổ sung text selection/search.
- Export tiếp tục chép đúng bytes của snapshot PDF; nguồn đổi hoặc compile lỗi vẫn đánh dấu bản cũ và khóa xuất. Cuộn/zoom/bấm liên kết không thay PDF hay mở khóa xuất cho bản cũ.

### 25.2 File và vai trò

| Member / phần bàn giao | Thay đổi |
| --- | --- |
| M2 — `compiler/PdfSnapshot.java` | Metadata immutable cho kích thước trang và links; resolve named destination/GoTo; chuyển rectangle và tọa độ PDF sang hệ tọa độ crop box đã xoay; bỏ annotation hỏng/không hỗ trợ, không thực thi actions |
| M1 — `ui/PdfPreviewPane.java` | Các khung trang liên tục; giữ hình dạng trang trước khi render; tính vùng nhìn, render tuần tự nền; overlay Hyperlink đúng vị trí; jump/Back, zoom/Fit và generation guard khi đổi PDF/reset |
| M1 — theme | CSS trang trắng/bóng và vùng hover/focus nhẹ cho liên kết, không thay chữ/công thức trong ảnh PDF |
| M5 — kiểm thử | Thêm 3 `PdfSnapshotTest`: crop/rotation, named links + bỏ URI action, link đích sai không làm hỏng export; `PdfContinuousUiSmoke` mới; chạy lại `PdfPreviewUiSmoke` với tài liệu thật |

Phân công là trách nhiệm module cần bàn giao; không khẳng định từng member tự viết các thay đổi trong phiên AI này. Không đổi compiler command/protocol/database cho tính năng này.

### 25.3 Bộ nhớ và giới hạn kỹ thuật

- Tạo khung trang từ metadata, chỉ render trang đang thấy và các trang lân cận. Những ảnh xa vùng nhìn được bỏ để tránh giữ tất cả trang; một worker render, không tạo hàng đợi vô hạn khi cuộn nhanh.
- Ngân sách lựa chọn ảnh khoảng **24 triệu pixel**, một trang vẫn bị giới hạn **12 triệu pixel** và scale tối đa 3 như trước. Đây là giới hạn ảnh trang được giữ, không phải tổng heap: PDF bytes, metadata, buffer render/chuyển ảnh còn cần thêm bộ nhớ. PDF vẫn giới hạn 128 MiB.
- Khi nhảy nhanh, trang có thể hiện placeholder ngắn lúc chờ render. Kích thước khung không đổi khi ảnh tải xong nên tránh cuộn giật. Không tự kéo về đầu mỗi khi một trang render xong.
- Generation guard bỏ kết quả render cũ sau reset/compile PDF mới. Render lỗi làm trạng thái thất bại và khóa export; một link hỏng được bỏ qua mà không chặn trang còn hợp lệ.

### 25.4 Kiểm chứng

- **120/120 JUnit chọn lọc đạt**, common 9 + server 21 + client 90, không failure/error/skip; log `target/workspace-files-tests.log`.
- `PdfContinuousUiSmoke` dùng PDF **13 trang** từ ZIP người dùng ở mục 24: cuộn cập nhật trang, pixels đúng PDFBox, cuộn tới cuối và không giữ mọi ảnh; named link từ trang vật lý 3 đến trang 6, overlay khớp tọa độ, tiêu đề đích nằm đúng mép trên viewport, Back trở về; zoom/Fit, khóa xuất stale, export byte-identical và không nhận render cũ sau reset đều đạt. Log `target/pdf-continuous-ui.log`: `PDF_CONTINUOUS_UI_SMOKE_OK`.
- Ảnh scene riêng đã xem: `target/pdf-continuous-ui-02/continuous-preview.png`, thấy cuối trang mục lục và đầu trang danh sách hình liên tiếp. Smoke gọi scroll/control thật và `Hyperlink.fire`, chưa dùng Robot bấm/kéo theo tọa độ hệ điều hành.
- Chạy lại **`PdfPreviewUiSmoke` toàn luồng JavaFX app** với `target/vietnamese-fixed/main.tex` **7 trang**: compile thật, pixels các trang, export đúng bytes, sửa nguồn chặn xuất, compile lỗi vẫn giữ bản tốt, compile lại phục hồi và đổi dự án reset preview đều đạt. Log `target/pdf-continuous-regression.log`: `PDF_PREVIEW_UI_SMOKE_OK`; home riêng `target/pdf-continuous-regression-01`.
- Chạy smoke mới: main `PdfContinuousUiSmoke <pdf-multi-page-có-hyperref> <output-folder-chưa-tồn-tại>` với JavaFX module path + client/test/common/server/PDFBox classpath. Fixture cần có link nội bộ đến heading ở phần trên trang; helper local `target/run-pdf-continuous-smoke.cmd` không được Git theo dõi.

Đóng app cũ rồi chạy `.\scripts\run-client.bat`; mở dự án và Recompile để dùng preview mới.

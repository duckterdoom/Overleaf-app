# Overleaf Desktop — Pure Java

Trình soạn thảo LaTeX desktop cộng tác thời gian thực: client JavaFX compile LaTeX trên máy người dùng, server Java TCP/TLS đồng bộ nội dung giữa nhiều người, module MongoDB riêng cho dữ liệu. Đồ án môn **Java Software Development**, 5 member, 4 Maven module.

Trạng thái, quyết định kỹ thuật, giới hạn và việc còn lại: xem [AI_Handoff.md](AI_Handoff.md).

## 1. Phân công

| Member | Vai trò | Phụ trách chính |
| --- | --- | --- |
| **M1** | GUI & Client | `client/.../ui/`, `ClientApp`, theme JavaFX |
| **M2** | Compiler & I/O | `client/.../compiler/`, `client/.../workspace/` |
| **M3** | Networking | `server/.../net/`, `ServerApp`, `client/.../net/`, `common/.../protocol/`, `common/.../security/`, `deploy/`, `scripts/` |
| **M4** | Collaboration | `server/.../collab/`, `common/.../model/`, `common/.../sync/` |
| **M5** | Database & Testing | `persistence/` |

## 2. Cấu trúc

Maven multi-module; package gốc `com.overleaf`, mỗi module một package con cùng tên.

```text
Overleaf-app/
├── pom.xml              # Maven parent: Java 17 (client 21), JavaFX 23.0.2, MongoDB 4.11.1, JUnit 5.10.1
├── common/              # Dùng chung client/server — com.overleaf.common
│   ├── protocol/        #   MessageType, SyncMessage, MessageCodec (wire v2), ResyncTransfer
│   ├── model/           #   CRDTOperation, DocumentState, CollaboratorInfo, CursorPosition
│   ├── sync/            #   OperationCodec, OfflineOperationQueue
│   └── security/        #   TlsContexts
├── server/              # Backend — com.overleaf.server (ServerApp)
│   ├── net/             #   OverleafServer, ServerSession, ServerConfig (M3)
│   └── collab/          #   DocumentSyncManager, CRDTEngine, OperationHistory, SyncBroadcaster, CollaborationThreadPool (M4)
├── client/              # Desktop — com.overleaf.client (ClientApp)
│   ├── ui/              #   OverleafFxApp, LoginView, HomeView, UiModal + styles/overleaf-theme.css (M1)
│   │   └── backend/     #   AuthGateway/ProjectGateway/ActivityGateway + LocalBackend (lưu trên máy)
│   ├── compiler/        #   LatexCompilerService (M2)
│   ├── workspace/       #   ProjectFileManager, WorkspaceWatcher (M2)
│   └── net/             #   NetworkClient, ConnectionScope, NetworkDemo (M3)
├── persistence/         # MongoDB — com.overleaf.persistence: config/, model/, repository/(impl/), service/ (M5)
├── scripts/             # run-client.sh|.bat, build-network.sh|.ps1, run-server.bat, run-network-demo.bat
├── deploy/              # Mẫu systemd + env cho VPS
└── main.tex             # Tài liệu mẫu; client mở/ghi main.tex ở thư mục chạy
```

Test nằm cùng package với code được test, trong `src/test/java` của từng module.

## 3. Chạy

### Cần cài

- **JDK 21+** và **Maven**
- *(Tùy chọn)* TeX Live / MacTeX / MiKTeX để compile ra PDF thật

### Mở ứng dụng

```bash
./scripts/run-client.sh     # macOS / Linux
scripts\run-client.bat      # Windows
```

Lần đầu: **tạo tài khoản → mở dự án → viết**. Dữ liệu lưu ở `~/OverleafDesktop`.

**Windows:** `scripts\run-client.bat` tự tìm JDK trong `JAVA_HOME` hoặc các thư mục cài đặt thường gặp (`~/.jdks`, Program Files), và tìm Maven trên PATH hoặc Maven đi kèm IntelliJ IDEA. Không cần đổi cấu hình hệ thống nếu máy đã có JDK 21+ và IntelliJ. Có thể kiểm tra chỉ build bằng `scripts\run-client.bat -BuildOnly`; nếu chạy bình thường bị lỗi, cửa sổ giữ thông báo để đọc nguyên nhân.

Nếu thấy file `.class` thay vì source: trên nhánh `Network`, mở code trong `client/src/main/java`, `common/src/main/java`, `server/src/main/java`, `persistence/src/main/java`. `.class` trong `target/classes` là output compiler, không phải source bị đổi đuôi; không đổi tên chúng thành `.java`. Các folder tên cũ như `overleaf-client/` chỉ còn output từ nhánh trước, đã chuyển vào `target/legacy-build-output-20261101-095848/` để giữ bản sao. Lệnh chạy ở các phiên hướng dẫn trước dùng tên module cũ không áp dụng cho nhánh này.

| Thao tác | Cách làm |
| --- | --- |
| Lưu / Compile | `Ctrl+S` / `F5` hoặc `Ctrl+Enter` |
| Tạo file, mở PDF, vào phòng | Menu `⋯` góc trên bên phải |
| Đổi tên / xóa file, dự án | Chuột phải vào file hoặc dự án |
| Đổi ảnh đại diện | Bấm vào avatar |
| Chỉnh chiều cao Outline | Kéo thanh ngang giữa cây file và Outline lên/xuống; tỷ lệ được nhớ sau khi thả chuột |
| Tạo / nhập dự án | **Dự án mới** → dự án trống, ZIP, Word `.docx`, Markdown, kho GitHub, dự án ví dụ hoặc 9 nhóm template |
| Chọn nơi lưu dự án mới | Trong hộp thoại tạo/nhập, sửa ô thư mục lưu hoặc bấm **Chọn…**; app tạo thư mục con mang tên dự án |
| Đổi nơi lưu mặc định | **Cài đặt → Dự án** hoặc **Dự án mới → Thư mục dự án mặc định…**; dự án cũ vẫn giữ nguyên vị trí và được liệt kê |
| Chuyển dự án có sẵn | Trang chủ → nhấp phải dự án → **Đổi thư mục lưu…**; chọn thư mục cha mới |
| Thêm thư mục / tệp trong workspace | **Thư mục mới**, **Thêm tệp**, hoặc nhấp phải thư mục; thư mục đang chọn được dùng làm đích |
| Upload tài nguyên | **Tải lên** → chọn nhiều tệp/thư mục, kéo thả hoặc dán tệp/ảnh; kiểm tra danh sách rồi bấm **Tải lên** |
| Lấy tệp từ nơi khác | **Thêm tệp → Từ dự án khác** (có tùy chọn tệp đầu ra), **Từ URL bên ngoài** hoặc **Thư viện tài liệu (.bib)** |
| Xem / xuất PDF | F5 → chờ preview xanh → cuộn liên tục qua các trang, bấm liên kết mục lục/tham chiếu để tới mục; `↶` quay lại, vẫn có `‹`/`›`, zoom/Fit; **Export PDF…** lưu đúng PDF đang xem |

Preview hiển thị **PDF thật do compiler tạo** bằng PDFBox. Sửa nguồn hoặc compile lỗi sẽ báo rõ bản cũ và khóa xuất; chỉ xuất khi đã compile thành công nội dung hiện tại. File Export giữ nguyên PDF, gồm font, công thức, bảng và hình. Xem chi tiết ở [AI_Handoff.md §19](AI_Handoff.md#19-preview-đúng-pdf-và-export-cùng-kết-quả-compiler).

**Cài đặt compile:** Settings → **Compilation / Biên dịch** cho phép chọn tệp `latexmk`, timeout (mặc định 120 giây, **0 = không giới hạn**) và kiểm tra phiên bản ba engine. Để trống đường dẫn để tự tìm trên PATH, bộ portable `target/TinyTeX` hoặc nơi cài phổ biến; đường dẫn bạn chọn luôn được ưu tiên. Bấm **Save compiler settings** để lưu; nút **Stop** xuất hiện khi compile. Lỗi có giải thích offline trong Build output bằng tiếng Việt/Anh. Kết quả PDF/cache/TikZ/tiếng Việt đã kiểm chứng và cách dùng TinyTeX portable trên máy hiện tại: [AI_Handoff.md §17](AI_Handoff.md#17-cải-thiện-tiêu-chí-1--compile-local-và-kiểm-chứng-pdf-thật). Sau cập nhật code phải đóng/mở lại app; tiến trình đang chạy vẫn dùng class cũ.

Gõ tiếng Việt (Telex/VNI) dùng được bình thường.

Menu tạo dự án, nhóm template, hộp thoại chọn nơi lưu và lỗi nhập dự án có cả tiếng Anh/Việt theo ngôn ngữ giao diện. Có các mẫu bài báo, sách, thư, bài tập, poster, thuyết trình, báo cáo, CV và luận văn. **Thêm đơn vị…** lưu tên trường/cơ quan để điền tác giả vào template mới.

Nhập Word/Markdown cần **Pandoc** trên PATH, biến `OVERLEAF_PANDOC` trỏ tới executable hoặc bản portable `target/pandoc/pandoc.exe`. Máy hiện tại có bản portable 3.13; thư mục `target/` không được Git theo dõi nên máy khác cần cài riêng. Bản nguồn được giữ trong `source/`; Word chỉ hỗ trợ `.docx` và định dạng phức tạp cần kiểm tra sau chuyển đổi ([Pandoc Manual](https://pandoc.org/MANUAL.html)). GitHub cần Git trên PATH và URL HTTPS kho công khai dạng `https://github.com/owner/repo`; chưa có luồng đăng nhập kho riêng. ZIP/kho Git cần `main.tex` ở gốc hoặc một file `.tex` gốc chứa `\documentclass` để app tạo bản `main.tex`.

Chuyển dự án sao chép và đối chiếu nội dung trước khi đổi vị trí, giữ bản cũ trong `.overleaf-moved-backups/` bên cạnh thư mục cũ. Không tự xóa các bản phục hồi này. Danh sách vị trí được lưu trong `~/OverleafDesktop/.app/projects.properties`; nên chuyển qua ứng dụng để danh sách cập nhật. Xem [AI_Handoff.md §22](AI_Handoff.md#22-menu-tạo-dự-án-nhập-tài-liệu-và-thư-mục-lưu-tùy-chọn).

**Workspace:** hộp thoại thêm tệp có ô chọn/sửa thư mục đích; để trống để thêm ở gốc. Tạo tệp không có đuôi tự thêm `.tex`; có thể dùng `chapters/intro.tex` để tạo đường dẫn con. Upload thư mục giữ cả cấu trúc và thư mục rỗng, ảnh/PDF/font được chép nguyên bytes; ảnh từ clipboard được lưu PNG. Tệp trùng tên bị từ chối, không ghi đè. Giới hạn mỗi lô 10.000 mục / 500 MiB; URL cần HTTPS trực tiếp, tối đa 100 MiB. Nút **Hủy** hủy tác vụ nền; đọc URL đang chờ có thể cần tối đa 15 giây để hết timeout.

Tệp từ dự án khác là bản sao độc lập. Với Zotero/Mendeley/ReadCube, hãy xuất `.bib` rồi nhập qua **Thư viện tài liệu (.bib)**; chưa kết nối tài khoản dịch vụ. Các tài nguyên workspace lưu trên máy; phòng cộng tác hiện chỉ đồng bộ `main.tex`. Xem [AI_Handoff.md §23](AI_Handoff.md#23-workspace-thêm-thư-mục-tệp-upload-và-lấy-tệp-từ-nguồn-khác).

### Làm việc nhóm (tùy chọn)

1. Chạy server:

   ```bash
   sh scripts/build-network.sh && java -jar dist/overleaf-server.jar 8080   # macOS / Linux
   scripts\run-server.bat 8080                                              # Windows (tự build)
   ```

2. Trong app: `⋯` → **Connect room…** → host `127.0.0.1`, port `8080`, cùng một tên phòng.

Phòng đồng bộ `main.tex` của dự án đang mở. Nối từ máy khác (VPS, SSH tunnel): xem [§6](#6-triển-khai-lên-vps).

### Chạy test

```bash
mvn test -Dtest='MessageCodecTest,ResyncTransferTest,ConnectionScopeTest,NetworkingTest,NetworkClientTest,M4CollaborationTest,OfflineResyncTest,ProjectFileManagerTest,WorkspaceFilesTest,LocalBackendTest,ProjectWorkflowTest,CompileWorkspaceTest,LatexCompilerServiceTest,LatexToolchainTest,LatexErrorExplainerTest,PdfSnapshotTest' -Dsurefire.failIfNoSpecifiedTests=false
```

120 test chọn lọc, không cần MongoDB hoặc TeX thật (compiler unit test dùng process fixture). Smoke với TeX thật (`CompilerSmoke`, `CompilerUiSmoke`, `PdfPreviewUiSmoke`, `VietnamesePdfSmoke`): xem [AI_Handoff.md](AI_Handoff.md) §17–20; Outline có `OutlineUiSmoke` (§21); tạo/nhập/chuyển dự án có `ProjectImportSmoke`, `NewProjectUiSmoke` (§22); thêm/upload tệp có `WorkspaceFilesUiSmoke` (§23); ZIP nhiều chương + Biber có `ImportedZipCompileSmoke` (§24); PDF cuộn/liên kết có `PdfContinuousUiSmoke` (§25). Test GUI đầy đủ (`UiSmoke`): §15.

Preview PDF xếp các trang liên tiếp và chỉ render vùng đang xem cùng các trang gần đó. Các liên kết nội bộ cần được tạo sẵn trong PDF, thường bằng gói `hyperref`; màu chữ xanh tự nó không tạo liên kết. Chưa mở liên kết website hoặc chạy action/script PDF trong preview. Xem [AI_Handoff.md §25](AI_Handoff.md#25-preview-pdf-cuộn-liên-tục-và-liên-kết-nội-bộ).

Dự án dùng `\include{chapters/...}` được tự tạo thư mục phụ trợ tương ứng trong `build/`. Nếu thiếu gói TeX, Build output ưu tiên lỗi gốc như `File 'csquotes.sty' not found`, thay vì báo `runscript.tlu ... exit code 12` là lỗi cú pháp. Máy kiểm thử đã bổ sung csquotes, titlesec, siunitx, biblatex, Biber, cleveref vào TinyTeX portable; máy khác phải cài các phụ thuộc mà tài liệu của mình yêu cầu. Kiểm chứng ZIP người dùng: [AI_Handoff.md §24](AI_Handoff.md#24-sửa-biên-dịch-zip-nhiều-chương-và-thông-báo-exit-code-12).

## 4. Cấu hình server

Biến môi trường; tham số dòng lệnh duy nhất là port.

| Biến | Mặc định | Ý nghĩa |
| --- | --- | --- |
| `OVERLEAF_HOST` | `127.0.0.1` | Địa chỉ bind. Bind ngoài loopback bắt buộc TLS + token ≥ 24 ký tự |
| `OVERLEAF_PORT` | `8080` | Port |
| `OVERLEAF_MAX_CLIENTS` | `64` | Số kết nối tối đa |
| `OVERLEAF_IDLE_TIMEOUT_MS` | `60000` | Đóng kết nối im lặng (client heartbeat mỗi 15 s) |
| `OVERLEAF_TOKEN` | rỗng | Token chung cho AUTH |
| `OVERLEAF_TLS` | `false` | Bật TLS |
| `OVERLEAF_KEYSTORE`, `OVERLEAF_KEYSTORE_PASSWORD` | — | PKCS12 của server khi bật TLS |
| `OVERLEAF_QUEUE_CAPACITY` | `32` | Hàng đợi frame gửi mỗi session |
| `OVERLEAF_MAX_DOCUMENT_CHARS` | `100000` | Giới hạn kích thước tài liệu (UTF-16 code units) |

Client (demo/GUI) đọc `OVERLEAF_TLS`, `OVERLEAF_TOKEN`, `OVERLEAF_TRUSTSTORE`, `OVERLEAF_TRUSTSTORE_PASSWORD`. GUI có ô token và checkbox TLS; truststore lấy từ môi trường lúc khởi chạy.

## 5. Giao thức wire v2

Frame big-endian `[int32 bodyLength][body]`, body tối đa 1 MiB:

```text
int32  version = 2
string messageType        # tên enum MessageType
string roomId
string senderId
string senderName
string payload
int64  timestamp          # thời gian thực, ms
int64  lamportTimestamp   # clock logic của server
```

String = `[int32 số byte UTF-8][bytes]`. Codec đọc được v1 (clock = 0) nhưng luôn ghi v2, nên **client và server phải cùng phiên bản**. Không dùng `ObjectInputStream`/`ObjectOutputStream`. Frame sai (cắt, sai version/type, UTF-8 lỗi, byte dư) → đóng session; lỗi nghiệp vụ → `ERROR` với tiền tố `INVALID_REQUEST:`.

```text
-> AUTH(token)                 <- ACK(AUTHENTICATED)
-> JOIN_ROOM(room, user, name) <- FULL_SYNC + CHECKPOINT + USER_LIST_UPDATE
-> TEXT_EDIT(OperationCodec JSON)
   server kiểm tra membership/identity/giới hạn -> M4 áp dụng vào RAM
<- ACK(operationId) cho người gửi, TEXT_EDIT cho người khác cùng phòng
```

- **Lamport:** client đặt `local = max(local, msg.lamportTimestamp)` trước khi xử lý; tạo operation thì tăng clock rồi gắn vào. Payload `FULL_SYNC` là nguyên văn, `CHECKPOINT` là operation ID.
- **Reconnect:** kết nối mới → AUTH → gửi **RESYNC trực tiếp** (không JOIN) gồm điểm đã thấy + thao tác chưa ACK. Phòng không còn (server restart) → `ERROR` với payload đúng `ROOM_UNKNOWN`; client giữ pending và văn bản local.
- **RESYNC lớn:** `NetworkClient` tự chia payload > 64 Ki ký tự thành `RESYNC_BEGIN` (tổng byte) → `RESYNC_PART`… (không cắt surrogate pair) → `RESYNC_END`, gửi liền như một khối FIFO. Server chỉ giao cho M4 khi ghép đủ. Giới hạn 4 MiB, 128 phần, 30.000 operation.
- **ACK_BATCH:** RESYNC > 64 operation được xác nhận bằng `ACK_BATCH` (ID cách nhau bởi newline, ≤ 2048 ID/message).
- **Định danh:** sau JOIN, server lấy sender từ session; mỗi user ID chỉ một session trong một phòng. Token là khóa chung, chưa phải tài khoản.
- **GUI:** mỗi kết nối/đổi phòng có generation trong `ConnectionScope`; callback cũ bị bỏ khi chạy trên FX thread.

## 6. Triển khai lên VPS

Chỉ cần copy `dist/overleaf-server.jar`; JAR không chứa cấu hình MongoDB hay credential. VPS cần Java runtime 17+.

**Cách 1 — SSH tunnel (khuyên dùng):** server bind loopback, mỗi người mở tunnel.

```sh
scp dist/overleaf-server.jar user@server:~/
ssh user@server 'java -jar ~/overleaf-server.jar 8080'
ssh -N -L 8080:127.0.0.1:8080 user@server   # trên máy mỗi người; client kết nối 127.0.0.1:8080
```

**Chạy nền bằng systemd** (mẫu trong `deploy/`, bind loopback):

```sh
sudo useradd --system --home /opt/overleaf --shell /usr/sbin/nologin overleaf
sudo install -d -o root -g overleaf -m 750 /opt/overleaf /etc/overleaf
sudo install -o root -g overleaf -m 640 ~/overleaf-server.jar /opt/overleaf/overleaf-server.jar
sudo install -o root -g root -m 600 deploy/overleaf.env.example /etc/overleaf/overleaf.env
sudo install -o root -g root -m 644 deploy/overleaf.service /etc/systemd/system/overleaf.service
sudo systemctl daemon-reload && sudo systemctl enable --now overleaf
sudo journalctl -u overleaf -f
```

**Cách 2 — TLS public:** cần DNS trỏ về VPS và PKCS12 đúng tên miền.

```sh
keytool -genkeypair -alias overleaf -keyalg RSA -keysize 3072 -validity 365 \
  -dname "CN=latex.example.com" -ext "SAN=dns:latex.example.com" -keystore server.p12 -storetype PKCS12
keytool -exportcert -rfc -alias overleaf -keystore server.p12 -file server.crt
```

Đặt `server.p12` ở `/etc/overleaf/` (`root:overleaf`, mode 640), dùng `deploy/overleaf-public.env.example` làm `/etc/overleaf/overleaf.env` với token ngẫu nhiên ≥ 24 ký tự, mở TCP 8080 rồi restart service. Với certificate tự ký, mỗi client import **certificate công khai** sau khi đối chiếu fingerprint:

```sh
keytool -importcert -alias overleaf -file server.crt -keystore client-trust.p12 -storetype PKCS12
# rồi đặt OVERLEAF_TLS=true, OVERLEAF_TOKEN, OVERLEAF_TRUSTSTORE, OVERLEAF_TRUSTSTORE_PASSWORD
```

Không tắt hostname verification để vượt lỗi certificate; sửa DNS/SAN/truststore. Server chưa lưu bền vững: restart là mất phòng, nên lưu file ở client trước khi bảo trì.

## 7. Quy ước

- Nhánh `feat/`, `fix/`, `docs/`; không commit thẳng vào `main`; chạy test ở mục 3 trước khi mở PR.
- Không commit `config.properties`, `.env`, `*.p12`, `*.jks`.
- Định dạng: `.editorconfig` (UTF-8, LF, 4 dấu cách) và `.gitattributes` (LF trong repo, CRLF cho `.bat`/`.ps1`).
- Remote: `https://github.com/duckterdoom/Overleaf-app.git`

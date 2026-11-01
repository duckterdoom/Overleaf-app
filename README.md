# Overleaf Desktop

Trình soạn thảo LaTeX desktop bằng Java, hỗ trợ compile PDF trên máy và cộng tác trực tuyến qua server TCP/TLS.

Đồ án môn **Java Software Development**, gồm 5 thành viên và 4 Maven module. Chi tiết kỹ thuật và trạng thái phát triển nằm trong [AI_Handoff.md](docs/AI_Handoff.md).

## Tính năng chính

- Soạn thảo LaTeX, quản lý file và xem PDF trực tiếp trong ứng dụng.
- Tạo dự án từ template hoặc nhập từ ZIP, Word, Markdown và GitHub.
- Lưu dự án trên máy, hỗ trợ tiếng Việt và giao diện Anh/Việt.
- Cộng tác qua phòng online, có reconnect và đồng bộ thao tác chưa gửi.

## Cấu trúc dự án

```text
Overleaf-app/
├── modules/
│   ├── client/        # JavaFX, compiler, workspace và network client
│   ├── common/        # Model, protocol và sync dùng chung
│   ├── server/        # TCP/TLS server và collaboration engine
│   └── persistence/   # MongoDB và repository
├── docs/              # Tài liệu kỹ thuật và đánh giá
├── examples/          # Tài liệu LaTeX mẫu
├── scripts/           # Script chạy và build
└── deploy/            # Mẫu cấu hình VPS
```

Source code nằm trong `modules/*/src/main/java`. Các thư mục `target/` và `dist/` là output build, không phải source.

## Bắt đầu nhanh

### 1. Cài đặt

- **JDK 21+** và **Maven**.
- **TeX Live**, **MacTeX** hoặc **MiKTeX** nếu muốn compile PDF.
- **Pandoc** nếu muốn nhập Word hoặc Markdown.

### 2. Mở ứng dụng

macOS / Linux:

```bash
./scripts/run-client.sh
```

Windows:

```bat
scripts\run-client.bat
```

Sau khi mở app: **tạo tài khoản → tạo hoặc mở dự án → bắt đầu viết**. Dữ liệu local được lưu trong `~/OverleafDesktop`.

Trên Windows, launcher tự tìm JDK và Maven trong các vị trí cài đặt thông dụng. Để chỉ kiểm tra build, chạy `scripts\run-client.bat -BuildOnly`.

## Sử dụng

| Thao tác | Cách thực hiện |
| --- | --- |
| Lưu file | `Ctrl+S` |
| Compile PDF | `F5` hoặc `Ctrl+Enter` |
| Tạo hoặc nhập dự án | Chọn **Dự án mới**, sau đó chọn template hoặc nguồn nhập |
| Thêm file hoặc thư mục | Dùng **Thêm tệp**, **Thư mục mới** hoặc menu chuột phải |
| Upload tài nguyên | Chọn **Tải lên**, kéo thả hoặc dán file/ảnh |
| Xem và xuất PDF | Compile, xem preview, sau đó chọn **Export PDF…** |
| Đổi tên hoặc xóa | Nhấp chuột phải vào file hoặc dự án |
| Kết nối phòng | Menu `⋯` → **Connect room…** |

### Compile và PDF

Preview hiển thị PDF thật do compiler tạo. Sau khi sửa source, cần compile thành công trước khi xem hoặc xuất bản mới.

Trong **Settings → Compilation / Biên dịch**, bạn có thể chọn `latexmk`, đổi timeout và kiểm tra toolchain. Để trống đường dẫn nếu muốn app tự tìm compiler.

### Nhập và di chuyển dự án

- Word/Markdown cần Pandoc; GitHub cần Git và URL HTTPS của repository công khai.
- ZIP hoặc repository cần có `main.tex`, hoặc một file `.tex` chứa `\documentclass` để app tạo file chính.
- Nên di chuyển dự án bằng menu **Đổi thư mục lưu…** để app cập nhật danh sách dự án và giữ bản khôi phục.

Các giới hạn import, upload và workspace được ghi trong [AI_Handoff.md §22–23](docs/AI_Handoff.md#22-menu-tạo-dự-án-nhập-tài-liệu-và-thư-mục-lưu-tùy-chọn).

## Cộng tác trực tuyến

Khởi động server trên macOS / Linux:

```bash
sh scripts/build-network.sh
java -jar dist/overleaf-server.jar 8080
```

Trên Windows:

```bat
scripts\run-server.bat 8080
```

Sau đó, trong app chọn **Connect room…** và nhập:

- Host: `127.0.0.1`
- Port: `8080`
- Room ID phải giống nhau giữa các thành viên

Hiện phòng cộng tác đồng bộ `main.tex`. Server lưu trạng thái phòng trong RAM, vì vậy cần lưu project trước khi restart server.

## Chạy test

```bash
mvn test -Dtest='MessageCodecTest,ResyncTransferTest,ConnectionScopeTest,NetworkingTest,NetworkClientTest,M4CollaborationTest,OfflineResyncTest,ProjectFileManagerTest,WorkspaceFilesTest,LocalBackendTest,ProjectWorkflowTest,CompileWorkspaceTest,LatexCompilerServiceTest,LatexToolchainTest,LatexErrorExplainerTest,PdfSnapshotTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Bộ test chọn lọc gồm **120 test**, không cần MongoDB hoặc TeX thật. Hướng dẫn smoke test cho GUI, compiler và PDF nằm trong [AI_Handoff.md](docs/AI_Handoff.md).

## Server và triển khai

Cấu hình thường dùng:

| Biến môi trường | Mặc định | Mục đích |
| --- | --- | --- |
| `OVERLEAF_HOST` | `127.0.0.1` | Địa chỉ server |
| `OVERLEAF_PORT` | `8080` | Port server |
| `OVERLEAF_TOKEN` | rỗng | Token dùng chung khi bật xác thực |
| `OVERLEAF_TLS` | `false` | Bật TLS |

Các cấu hình đầy đủ có trong `deploy/overleaf.env.example` và `deploy/overleaf-public.env.example`.

Cách triển khai đơn giản qua SSH tunnel:

```bash
scp dist/overleaf-server.jar user@server:~/
ssh user@server 'java -jar ~/overleaf-server.jar 8080'
ssh -N -L 8080:127.0.0.1:8080 user@server
```

`deploy/` cũng có mẫu systemd để chạy server nền. Khi mở server ra public network, bắt buộc dùng TLS và token ít nhất 24 ký tự; không tắt hostname verification.

Giao thức wire v2 và các giới hạn networking được mô tả trong [AI_Handoff.md §5](docs/AI_Handoff.md#5-giao-thức-wire-v2).

## Phát triển

Format Java trước khi tạo pull request:

```bash
mvn spotless:apply
mvn spotless:check
```

Quy ước chung:

- Dùng nhánh `feat/`, `fix/` hoặc `docs/`; không commit trực tiếp vào `main`.
- Chạy test trước khi mở pull request.
- Không commit `config.properties`, `.env`, `*.p12` hoặc `*.jks`.
- Giữ UTF-8 và newline theo `.editorconfig` / `.gitattributes`.

## Phân công

| Member | Phạm vi chính |
| --- | --- |
| **M1** | JavaFX UI và client |
| **M2** | Compiler và workspace I/O |
| **M3** | Networking, scripts và deployment |
| **M4** | Collaboration và sync |
| **M5** | MongoDB, persistence và testing |

Repository: <https://github.com/duckterdoom/Overleaf-app.git>

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
| Auto Compile | Mũi tên cạnh Recompile → **Tự động biên dịch**; chờ 1 giây ngừng gõ rồi compile. Chọn **Biên dịch thủ công** để tắt |
| Lịch sử sửa | **Lịch sử / History** → chọn tệp và phiên bản, so sánh với hiện tại, gắn nhãn hoặc khôi phục |
| Đổi bố cục | **Bố cục / Layout** → chia đôi, chỉ editor, chỉ PDF, PDF cửa sổ riêng hoặc chế độ tập trung (`Ctrl+Shift+M`) |
| Tạo hoặc nhập dự án | Chọn **Dự án mới**, sau đó chọn template hoặc nguồn nhập |
| Thêm file hoặc thư mục | Dùng **Thêm tệp**, **Thư mục mới** hoặc menu chuột phải |
| Upload tài nguyên | Chọn **Tải lên**, kéo thả hoặc dán file/ảnh |
| Xem và xuất PDF | Compile, xem preview, sau đó chọn **Export PDF…** |
| Đổi tên hoặc xóa | Nhấp chuột phải vào file hoặc dự án |
| Kết nối phòng | Menu `⋯` → **Connect room…** |

Toolbar editor có dãy icon ngay sau **Italic**, theo thứ tự: **Math → Link → Comment → Cross reference → Citation → Figure → Table → List → Decrease indent → Increase indent**. Di chuột lên icon để xem chú thích Việt/Anh; nếu editor hẹp, cuộn ngang toolbar để thấy các nút còn lại.

Math và List mở menu chọn dạng chèn. Comment bật/tắt chú thích LaTeX `%` cho dòng đang chọn, không phải luồng nhận xét review. Tham chiếu đọc `\label` từ tệp `.tex`, citation đọc khóa từ `.bib`; ảnh chọn từ các PNG/JPG/PDF đã upload. Thụt lề dùng 2 dấu cách, giảm lề xử lý cả tab. Link cần `hyperref`, Figure cần `graphicx`; hộp thoại có hướng dẫn package, không tự sửa preamble. Xem [AI_Handoff §26](docs/AI_Handoff.md#26-toolbar-editor-chèn-latex-và-thụt-lề).

### Compile và PDF

Preview hiển thị PDF thật do compiler tạo. Sau khi sửa source, cần compile thành công trước khi xem hoặc xuất bản mới.

Trong **Settings → Compilation / Biên dịch**, bạn có thể chọn `latexmk`, đổi timeout và kiểm tra toolchain. Để trống đường dẫn nếu muốn app tự tìm compiler.

Auto Compile mặc định tắt, nhớ lựa chọn cho lần mở sau; vẫn có thể bấm nút compile ngay. Khi một lượt đang chạy, thay đổi tiếp theo được gộp để compile sau đó; **Stop** dừng lượt hiện tại và lượt tự động đang chờ.

History lưu phiên bản văn bản local trong `.overleaf-history/` của từng dự án khi mở/lưu tệp và sau 3 giây ngừng gõ. Các checkpoint chưa Save không ghi đè tệp làm việc. Chỉ có lịch sử từ khi bật bản ứng dụng này; đây là lịch sử trên máy, chưa đồng bộ qua server. Khôi phục một tệp có xác nhận và giữ nội dung cũ trước khi thay thế; cần rời phòng cộng tác để khôi phục. Giới hạn 2 MiB/phiên bản, 1.024 phiên bản/128 MiB mỗi dự án; chạm giới hạn sẽ dừng ghi thêm history, không xóa các mốc cũ và vẫn cho lưu source. Xem [AI_Handoff §27](docs/AI_Handoff.md#27-auto-compile-history-và-layout-workspace).

### Nhập và di chuyển dự án

**Thư viện mẫu:** Dự án mới → Thêm mẫu dự án → chọn một trong 9 nhóm hoặc Xem tất cả. Có **29 mẫu nhiều tệp, Anh/Việt**, tìm kiếm có/không dấu, lọc nhóm/tag, mô tả và engine mặc định. Gồm bài báo, sách, đơn hành chính, bài tập, poster, Beamer, báo cáo thực tập, CV và hai mẫu luận văn đủ 5 chương/BibLaTeX. Kiểm package/Biber trước khi tạo; chỉ cài gói vào TinyTeX đi kèm sau xác nhận, không tự sửa TeX hệ thống. Xem [danh mục và cách thêm mẫu](docs/TEMPLATE_LIBRARY.md), [giấy phép](LICENSE-TEMPLATES.md).

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
mvn test -Dtest='MessageCodecTest,ResyncTransferTest,ConnectionScopeTest,NetworkingTest,NetworkClientTest,M4CollaborationTest,OfflineResyncTest,ProjectFileManagerTest,ProjectHistoryTest,WorkspaceFilesTest,LocalBackendTest,ProjectWorkflowTest,ProjectTemplatesTest,TemplatePrerequisitesTest,CompileWorkspaceTest,LatexCompilerServiceTest,LatexToolchainTest,LatexErrorExplainerTest,PdfSnapshotTest,EditorInsertionsTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Bộ test chọn lọc gồm **143 test**, không cần MongoDB hoặc TeX thật. Hướng dẫn smoke test nằm trong [AI_Handoff.md](docs/AI_Handoff.md); thư viện mẫu có ma trận **174 build thật** và smoke UI mở rộng (§28), bên cạnh toolbar/history/layout (§26–27).

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

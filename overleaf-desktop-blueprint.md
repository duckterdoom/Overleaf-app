# Blueprint xây dựng ứng dụng LaTeX desktop (Windows + macOS)

Mục tiêu: một ứng dụng kiểu Overleaf chạy native, giải quyết các điểm yếu của bản web (compile trên server, phụ thuộc internet, giới hạn gói, tùy biến kém, riêng tư).

> Lưu ý: tên thư viện và phiên bản dưới đây dựa trên kiến thức đến giữa 2026. Hãy kiểm tra phiên bản và giấy phép mới nhất trước khi chốt.

---

## 1. Tầm nhìn và nguyên tắc

1. **Local-first:** dữ liệu là thư mục thường trên máy, mở được bằng bất kỳ công cụ nào. Không khóa người dùng.
2. **Compile cục bộ:** không timeout, không xếp hàng, kiểm soát được bản TeX và gói.
3. **Cộng tác là tùy chọn:** hoạt động tốt khi offline, đồng bộ khi có mạng.
4. **Mở rộng được:** plugin, theme, keybinding, LSP.
5. **Trải nghiệm quen thuộc:** bố cục 3 cột (file tree, editor, PDF) giống Overleaf để người dùng chuyển sang dễ.

## 2. Ánh xạ điểm yếu của Overleaf sang giải pháp

| Điểm yếu trên web | Giải pháp trong app |
|---|---|
| Timeout, hàng đợi compile | Compile local bằng latexmk, dùng toàn bộ CPU |
| Không chọn được TeX Live, gói, shell-escape | Cho chọn engine, bản TeX, bật shell-escape theo dự án |
| Compile lại từ đầu | Incremental build, giữ thư mục build, cache |
| Cần internet | Offline-first, sync nền |
| Git/Dropbox bị khóa gói trả phí | Git tích hợp sẵn, thư mục dự án có thể nằm trong Dropbox/OneDrive |
| Editor khó tùy biến | CodeMirror 6, plugin API, LSP (texlab) |
| Tìm kiếm/outline yếu | Tìm toàn dự án, outline, đi tới định nghĩa label/cite |
| Lịch sử tuyến tính | Lịch sử dựa trên Git, hỗ trợ branch/merge |
| Dữ liệu trên cloud | Lưu local, mã hóa tùy chọn, server tự host |
| Gói free giới hạn cộng tác viên | Cộng tác qua relay tự host hoặc P2P |

## 3. Phạm vi MVP

**Bắt buộc có:**
- Mở/tạo dự án từ thư mục, file tree (thêm, xóa, đổi tên, kéo thả ảnh).
- Editor LaTeX: tô màu cú pháp, autocomplete lệnh/label/cite, gấp đoạn, tìm kiếm toàn dự án.
- Compile bằng latexmk (pdfLaTeX, XeLaTeX, LuaLaTeX), tự compile khi lưu (tùy chọn).
- Preview PDF cạnh editor, SyncTeX hai chiều (click PDF nhảy tới code và ngược lại).
- Panel log lỗi đã được phân tích: lỗi/cảnh báo, bấm vào nhảy tới dòng.
- Phát hiện TeX Live/MiKTeX có sẵn, hướng dẫn cài nếu thiếu.
- Hỗ trợ BibTeX/Biber.
- Cài đặt: theme sáng/tối, font, keybinding Vim/Emacs/mặc định.

**Để sau MVP:** cộng tác thời gian thực, comment/track changes, visual editor, plugin store, AI, template gallery.

## 4. Kiến trúc đề xuất

### 4.1 Lựa chọn nền tảng

| Phương án | Ưu điểm | Nhược điểm |
|---|---|---|
| **Tauri (Rust + WebView)** | Nhẹ (vài chục MB), bảo mật tốt, nhanh | WebView khác nhau giữa Windows (WebView2) và macOS (WKWebView), cần test kỹ |
| **Electron** | Chromium đồng nhất, hệ sinh thái lớn, nhiều người biết | Nặng (100MB+), tốn RAM |

Gợi ý: nếu đội mạnh web/TypeScript và cần tiến nhanh, chọn **Electron**. Nếu ưu tiên nhẹ và có người biết Rust, chọn **Tauri**. Phần UI dùng chung (React/Svelte + TypeScript) nên chuyển đổi sau này không quá đau.

### 4.2 Sơ đồ thành phần

```
┌─────────────────────────── UI (TypeScript) ───────────────────────────┐
│ File tree │ Editor (CodeMirror 6) │ PDF viewer (PDF.js) │ Log panel   │
└──────────────┬───────────────────────────────────────┬────────────────┘
               │ IPC                                   │
┌──────────────▼───────────── Core (Rust/Node) ────────▼────────────────┐
│ Project manager │ Compile service │ LSP host │ Git service │ Sync     │
└───────┬──────────────────┬──────────────┬───────────────┬─────────────┘
        │                  │              │               │
   Thư mục dự án    latexmk + TeX     texlab (LSP)    libgit2 / git CLI
```

### 4.3 Thành phần chính

**Editor**
- CodeMirror 6 (nhẹ, module hóa, hỗ trợ collab qua `y-codemirror.next`). Monaco là lựa chọn khác nhưng nặng hơn.
- Ngôn ngữ LaTeX: dùng `texlab` qua LSP cho autocomplete, diagnostics, go-to-definition, outline.
- Vim/Emacs mode qua extension có sẵn của CodeMirror.

**Compile service**
- Gọi `latexmk` với tham số: `-interaction=nonstopmode -file-line-error -synctex=1 -outdir=<build>`.
- Chạy trong tiến trình con, stream log về UI, có nút hủy.
- Thư mục build riêng (`.build/`) để giữ cache và không làm bẩn dự án.
- Phân tích log: viết parser cho `-file-line-error` (hoặc dùng thư viện parse log LaTeX có sẵn).
- Tùy chọn engine thay thế nhanh: **Tectonic** (tự tải gói theo nhu cầu, gọn) hoặc Typst cho người muốn thử.

**Phân phối TeX**
- Windows: ưu tiên phát hiện MiKTeX/TeX Live; nếu không có, gợi ý cài MiKTeX (cài gói theo nhu cầu, nhẹ hơn).
- macOS: phát hiện MacTeX/BasicTeX; hướng dẫn cài qua Homebrew hoặc trình cài đặt chính thức.
- Phương án nâng cao: đóng gói TeX riêng hoặc dùng Tectonic để người dùng không phải cài gì.
- Nếu đóng gói TeX Live, cần kiểm tra điều khoản phân phối từng gói.

**PDF preview**
- PDF.js render trong UI. Giữ vị trí cuộn khi compile lại.
- SyncTeX: dùng binary `synctex` để đổi qua lại giữa vị trí PDF và dòng nguồn.

**Quản lý dự án và phiên bản**
- Dự án = thư mục + file cấu hình `.latexapp.json` (engine, file chính, tùy chọn compile).
- Git tích hợp: libgit2 (`git2-rs`/`nodegit`) hoặc gọi `git` CLI. Giao diện commit, diff, branch, đơn giản như VS Code.
- Lịch sử cục bộ tự động (snapshot) phòng khi người dùng không dùng Git.

**Cộng tác (giai đoạn 2)**
- CRDT: **Yjs** (hoặc Automerge). Mỗi file là một `Y.Text`, cấu trúc thư mục là `Y.Map`.
- Truyền tải: WebSocket qua relay server nhỏ (tự host được), hoặc WebRTC P2P cho nhóm nhỏ.
- Presence: con trỏ, tên, màu của từng người.
- Offline: Yjs gộp thay đổi khi kết nối lại; lưu cập nhật vào local (IndexedDB hoặc file).
- Quyền: link mời có token, vai trò xem/sửa.
- Comment và track changes: lưu như metadata đi kèm tài liệu (hoặc chú thích trong file), cần thiết kế riêng, đừng nhồi vào MVP.

**Plugin**
- API đơn giản: đăng ký lệnh, panel, snippet, hook trước/sau compile.
- Chạy plugin trong sandbox (worker/iframe) và có hệ thống quyền.

## 5. Stack gợi ý

| Lớp | Lựa chọn |
|---|---|
| Shell | Tauri hoặc Electron |
| UI | TypeScript + React (hoặc Svelte) |
| Editor | CodeMirror 6 |
| LSP | texlab |
| PDF | PDF.js + synctex |
| Compile | latexmk (TeX Live/MiKTeX), tùy chọn Tectonic |
| Git | libgit2 hoặc git CLI |
| Collab | Yjs + y-websocket / y-webrtc |
| Lưu trữ cài đặt | JSON/TOML trong thư mục config của OS |
| Đóng gói | Windows: MSI/NSIS; macOS: DMG, ký và notarize |
| CI | GitHub Actions, build song song Windows + macOS |

## 6. Lộ trình gợi ý

| Giai đoạn | Thời gian ước tính | Kết quả |
|---|---|---|
| 0. Nghiên cứu, prototype | 2–3 tuần | Mở thư mục, editor, compile latexmk, hiện PDF |
| 1. MVP | 2–3 tháng | Đủ tính năng mục 3, bản beta nội bộ trên Win + Mac |
| 2. Hoàn thiện editor | 1–2 tháng | LSP đầy đủ, outline, tìm kiếm, log đẹp, template |
| 3. Git và lịch sử | 1 tháng | Commit/diff/branch trong app |
| 4. Cộng tác | 2–3 tháng | Yjs, relay server, presence, comment |
| 5. Hệ sinh thái | liên tục | Plugin, AI, visual editor, đồng bộ cloud tùy chọn |

Thời gian chỉ mang tính tham khảo cho nhóm nhỏ 2–4 người; điều chỉnh theo năng lực thực tế.

## 7. Rủi ro và cách giảm thiểu

| Rủi ro | Giảm thiểu |
|---|---|
| Người dùng chưa cài TeX, onboarding khó | Trình hướng dẫn cài đặt, kiểm tra môi trường, tùy chọn Tectonic |
| Khác biệt Windows/macOS (đường dẫn, WebView, ký ứng dụng) | CI build cả hai từ sớm, test trên máy thật |
| macOS yêu cầu ký và notarize | Đăng ký Apple Developer từ đầu, tự động hóa trong CI |
| Windows SmartScreen cảnh báo | Chứng chỉ ký code (EV nếu có ngân sách) |
| Cộng tác phức tạp, dễ lỗi đồng bộ | Làm sau MVP, dùng Yjs đã được kiểm chứng, test xung đột kỹ |
| Cạnh tranh với TeXstudio, VS Code + LaTeX Workshop | Tập trung vào trải nghiệm kiểu Overleaf + offline + cộng tác dễ |
| Bảo mật plugin | Sandbox, quyền rõ ràng, chưa mở store sớm |
| Giấy phép | Xem mục 8 |

## 8. Giấy phép và pháp lý

- Overleaf Community Edition dùng **AGPL-3.0**. Tái sử dụng mã nguồn thì sản phẩm phải theo AGPL (kể cả khi cung cấp qua mạng). Làm lại từ đầu thì không bị ràng buộc, nhưng **tránh sao chép mã và tài sản thương hiệu** (tên, logo, giao diện đặc trưng).
- Kiểm tra giấy phép từng thư viện: CodeMirror (MIT), PDF.js (Apache-2.0), Yjs (MIT), texlab (GPL-3.0, chạy như tiến trình riêng nên cần xem xét kỹ cách tích hợp), Tectonic (MIT). Hãy xác minh lại; đây không phải tư vấn pháp lý.
- Template trong gallery cần có giấy phép phân phối rõ ràng.
- Nếu dùng tên có chữ "Overleaf" hoặc "LaTeX", kiểm tra vấn đề nhãn hiệu. Nên đặt tên sản phẩm riêng.

## 9. Kiểm thử và chất lượng

- Bộ dự án mẫu: bài báo, luận văn nhiều chương, beamer, TikZ nặng, tiếng Việt/CJK (XeLaTeX/LuaLaTeX), bibliography Biber.
- Kiểm thử compile trên Windows và macOS (đường dẫn có dấu cách, ký tự Unicode).
- Test hiệu năng: dự án 100+ file, PDF 300+ trang.
- Telemetry tùy chọn (opt-in) để thu lỗi compile và crash.
- Cập nhật tự động (Tauri updater / electron-updater) với kênh stable/beta.

## 10. Việc cần làm ngay (tuần đầu)

1. Chốt nền tảng: Tauri hay Electron.
2. Dựng prototype: mở thư mục, CodeMirror, nút Compile gọi latexmk, hiện PDF bằng PDF.js.
3. Tích hợp texlab qua LSP và thử autocomplete.
4. Thiết lập CI build Windows + macOS.
5. Viết 5–10 dự án LaTeX mẫu để làm bộ test.
6. Xác định nhóm người dùng mục tiêu (sinh viên, nhà nghiên cứu, doanh nghiệp) để chốt thứ tự ưu tiên.

---

*Tài liệu này là bản khởi đầu; hãy điều chỉnh theo nguồn lực và phản hồi từ người dùng thử.*

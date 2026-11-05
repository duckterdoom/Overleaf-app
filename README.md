# Overleaf Desktop — Python + Next.js

Ứng dụng desktop soạn thảo LaTeX, biên dịch và xem PDF trên máy, có cộng tác qua TCP/TLS. Giao diện Next.js được build thành file tĩnh và mở trong cửa sổ **pywebview**; backend **Python/FastAPI** chạy local. Bản hiện tại không cần JVM, JavaFX hoặc Maven để chạy.

## Công nghệ và luồng hoạt động

| Thành phần | Công nghệ / vai trò |
| --- | --- |
| Cửa sổ desktop | Python 3.12+, pywebview, hộp thoại file/thư mục native |
| Giao diện | Next.js static export, React, TypeScript, Tailwind CSS, Lucide icons |
| Editor | CodeMirror: highlight, folding, tìm/thay thế, chèn LaTeX |
| Backend local | FastAPI/Uvicorn, API loopback có token phiên |
| Compile | latexmk với pdfLaTeX, XeLaTeX hoặc LuaLaTeX; TeX cài trên máy hoặc TinyTeX đi kèm môi trường local |
| PDF | PDF.js, pypdf và SyncTeX; PDF thật, export cùng bytes với preview |
| Cộng tác | Server Python riêng: TCP/TLS, wire v1/v2, OT/Lamport, ACK và RESYNC |
| Dữ liệu | File local, JSON, history; MongoDB/pymongo là module tùy chọn chưa nối UI/room |

Luồng chạy: **pywebview → UI tĩnh → API Python local → file/TeX/PDF**. Khi bật cộng tác, client kết nối thêm server TCP/TLS. Sau khi build UI, không cần chạy Next.js server; Node.js dùng cho build/phát triển.

## Chạy từ source

Chuẩn bị Python **3.12+**, Node.js **22.13+** để build, native WebView và bộ TeX có `latexmk`. Windows dùng WebView2; macOS dùng WebKit; Linux cần GTK/WebKit hoặc Qt phù hợp với pywebview. Mac/Linux chưa được kiểm thử native trong lần nghiệm thu Windows hiện tại.

Windows, chạy tại root repository:

```bat
scripts\run-python-desktop.bat --build
scripts\run-python-desktop.bat
```

macOS/Linux:

```sh
sh scripts/run-python-desktop.sh --build
sh scripts/run-python-desktop.sh
```

`--build` chuẩn bị dependency và build UI rồi thoát; lệnh tiếp theo mở ứng dụng. Launcher tự tạo `apps/desktop/.venv` nếu thiếu. Sau khi sửa frontend, chạy lại `--build`. macOS cũng có launcher `Overleaf Python Desktop.command`.

Windows kiểm tra cả khả năng chạy Python, không chỉ sự tồn tại của `.venv`. Nếu runtime do uv quản lý bị mất, `scripts/ensure-python.ps1` khôi phục Python vào `target/python-runtimes` khi có uv và kết nối mạng; môi trường cần tạo lại sẽ được đổi tên để giữ bản dự phòng. Không xóa thư mục runtime này khi đang dùng `.venv` tương ứng.

Compile cần TeX Live/MacTeX/MiKTeX hoặc TinyTeX phù hợp hệ điều hành. Windows hiện có thể dùng `target/TinyTeX/bin/windows/latexmk.exe`; thư mục này là công cụ local, **không được Git đóng gói sẵn cho máy khác**. Chọn `latexmk` trong **Cài đặt → Biên dịch** nếu app chưa tìm thấy. Không chép TinyTeX Windows hay `.venv` Windows sang macOS.

Git cần cho import GitHub public; Pandoc cần cho Word/Markdown. Lần tải dependency/package/import online cần mạng; soạn thảo và compile local hoạt động offline khi đã chuẩn bị đủ công cụ và gói TeX. Đây là bản chạy từ source, chưa có bộ cài độc lập `.exe`/`.app`/`.dmg` đã ký.

## Chức năng hiện có

- Tài khoản local, đăng nhập/remember/offline, hồ sơ/avatar, cài đặt Anh/Việt, theme và hoạt động sử dụng.
- Quản lý dự án: tìm kiếm, icon/màu, đổi tên/thùng rác, thư mục lưu tùy chọn và chuyển vị trí có backup; 29 template gallery cùng blank/example, nguồn Anh/Việt.
- Import ZIP/thư mục/Word/Markdown/GitHub public HTTPS; tạo file/thư mục lồng nhau, upload nhiều file/thư mục, kéo thả/dán ảnh, copy từ dự án khác hoặc build output, tải từ HTTPS và nhập `.bib`.
- Editor có outline kéo đổi chiều cao, toolbar Math/Link/Comment/Reference/Citation/Figure/Table/List/Indent, autosave, lịch sử phiên bản/nhãn/so sánh/khôi phục và bố cục split/editor/PDF/focus.
- Compile thủ công hoặc Auto Compile, Stop, timeout tùy chỉnh, log và Error Explainer Anh/Việt; kiểm tra toolchain/package. Cài package trong app chỉ áp dụng cho TinyTeX đi kèm và cần người dùng xác nhận.
- Preview PDF cuộn nhiều trang, mục lục/link, zoom, cửa sổ PDF riêng và SyncTeX hai chiều. Export dùng đúng snapshot bytes đang preview; nguồn thay đổi hoặc compile lỗi sẽ chặn export cho đến khi compile thành công.
- Cộng tác `main.tex`: TCP/TLS, presence/cursor, offline queue, reconnect và RESYNC. Chưa đồng bộ toàn bộ cây file; room/queue nằm trong RAM, chưa có CRDT đầy đủ hoặc phân quyền theo tài khoản.

Nút Comment mở Review: nhận xét gắn đoạn chọn/dòng, trả lời, giải quyết/mở lại và lọc theo file/tổng quan. Nhận xét lưu local trong `.overleaf-review.json` của dự án, không chèn `%` vào source và chưa đồng bộ qua phòng TCP. Zotero/Mendeley/ReadCube dùng nhập `.bib`, chưa có OAuth. GitHub import chưa phải Git branch/merge.

Thanh icon ngoài cùng bên trái workspace chuyển giữa **Tệp/Outline**, **Review** và **Chat cộng tác**; chỉ một panel hiện mỗi lần, bấm lại icon đang chọn hoặc nút đóng để ẩn. Panel kéo đổi chiều rộng được. Chat hiện là giao diện chờ tích hợp nhiều người dùng, chưa gửi/nhận tin nhắn.

**Chế độ tập trung:** vào từ Bố cục hoặc `Ctrl/Cmd+Shift+M`. Nhấn **Esc** để thoát; thanh trên cùng vẫn có **Bố cục → Thoát chế độ tập trung** và nút **Thoát tập trung · Esc**. Thoát sẽ giữ bố cục editor/PDF đã chọn.

### Chỉnh sửa Word trong workspace và export DOCX

Cạnh **PDF preview** có tab **Word preview**. Mặc định **Word phân trang** mở trực tiếp DOCX từ bộ chuyển đổi gốc của bạn bằng SuperDoc, có trang, cột, hình, caption và header/footer. Có thể sửa trực tiếp, dùng toolbar định dạng, tự lưu sau khi ngừng gõ hoặc **Lưu Word** / `Ctrl/Cmd+S`. Nếu chưa có PDF, mở tab sẽ yêu cầu compile và hiển thị trạng thái chuyển đổi/phân trang.

Nút tải xuống mở menu **Export PDF (.pdf)** / **Export Word (.docx)**. DOCX ban đầu dùng nguyên bộ chuyển đổi từ `D:\ilovepdf-python` đã đóng gói trong app: builder bài mẫu, Universal mặc định và patched pdf2docx fallback. Khi đã sửa Word, download lấy đúng bytes DOCX đã lưu từ trình soạn thảo. Export vẫn yêu cầu PDF/source hiện hành; nếu nguồn thay đổi trong lúc Word có sửa, app giữ draft và chặn export thay vì âm thầm ghi đè. **Tạo lại từ PDF** thay draft sau xác nhận, giữ revision trước đó.

Mã nghiên cứu nằm trong [integrations/pdf2docx-research](integrations/pdf2docx-research/README.md); runtime trong `apps/desktop/src/overleaf_desktop/pdf_export`. Không cần thư mục nguồn cũ để chạy app. Chi tiết ở [PDF_TO_WORD_EXPORT.md](docs/PDF_TO_WORD_EXPORT.md).

`.overleaf-docx/` giữ DOCX hiện tại, revision trước và metadata/checksum, không tham gia compile. Đổi tab, xem lịch sử, đổi bố cục hoặc quay về danh sách dự án sẽ lưu trước. Sửa Word phân trang được giữ trong DOCX, không tự sửa mã LaTeX.

Nút **Đồng bộ chữ LaTeX** giữ chế độ cũ khi cần sửa chữ qua ánh xạ Word ↔ LaTeX; cần **Pandoc** (`PATH`, `OVERLEAF_PANDOC` hoặc `target/pandoc/pandoc.exe`). Chỉ chữ trong vùng ánh xạ duy nhất được ghi về nguồn; công thức, citation, macro và cấu trúc được bảo vệ. Chế độ này dùng bản HTML riêng `.overleaf-word.json`, không phải bản DOCX phân trang và không cung cấp bytes download. Chưa đồng bộ chỉnh sửa DOCX đầy đủ hai chiều với LaTeX.

Đã kiểm bài journal mẫu: Word phân trang **6 trang, 8 ảnh**, sửa chữ → tự lưu → đổi tab → mở lại vẫn giữ nội dung; download trùng bytes DOCX đã sửa. Đây là render DOCX thực, không phải engine TeX; ngắt dòng/trang có thể khác PDF hoặc Microsoft Word tùy font và cấu trúc tài liệu.

SuperDoc và worker được bundle local, tắt telemetry, không cần dịch vụ chỉnh Word trên cloud. Build tự chép worker qua `scripts/copy-worker.mjs`. SuperDoc dùng AGPL-3.0; dependency `@superdoc/docx-engine` có giấy phép riêng trong package. Giữ các thông báo giấy phép khi đóng gói/phân phối.

Chất lượng chuyển PDF → DOCX phụ thuộc chính bộ converter gốc; không cam kết mọi tài liệu giống PDF tuyệt đối. Preview không tự thay bằng ảnh nguyên trang để giả lập khả năng chỉnh sửa.


## Cấu trúc source

```text
Overleaf-app/
├── apps/
│   ├── desktop/
│   │   ├── pyproject.toml / uv.lock     # dependency Python và lock
│   │   ├── src/overleaf_desktop/        # backend và cửa sổ desktop
│   │   │   └── resources/              # template và rule giải thích lỗi độc lập Java
│   │   └── tests/                      # unit/integration test và native smoke helpers
│   └── frontend/
│       ├── app/                        # trang dashboard/workspace, layout, CSS
│       ├── components/                 # editor, PDF, history, settings, upload...
│       ├── lib/                        # API client, state, i18n, snippet, PDF geometry
│       ├── public/                     # tài nguyên tĩnh, PDF worker khi build
│       ├── scripts/                    # bundle worker và test trình duyệt
│       ├── package.json / package-lock.json
│       └── next.config.ts              # static export → out/
├── scripts/run-python-*                # launcher desktop/server/console .bat và .sh
├── deploy/python/                      # Docker/Compose mẫu và hướng dẫn TLS
├── docs/                               # bàn giao, đối chiếu, kế hoạch dọn Java
├── examples/                           # nguồn LaTeX mẫu dùng chung
├── LICENSE-TEMPLATES.md                 # giấy phép template cần giữ
└── modules/, pom.xml, launcher Java     # mã cũ chờ dọn theo kế hoạch riêng
```

Các file backend chính:

| File trong `apps/desktop/src/overleaf_desktop/` | Chức năng |
| --- | --- |
| `main.py` | Cửa sổ pywebview, native dialogs, export, lưu buffer khi đóng |
| `api.py`, `parity_api.py` | API local, phiên, route thao tác, upload, theo dõi thay đổi |
| `store.py` | Project/catalog, đường dẫn, template, fingerprint nguồn |
| `state.py` | Account/profile/avatar/settings/activity, đọc dữ liệu Java cũ |
| `files.py` | Import/upload/copy/HTTPS, staging/rollback, nhập dữ liệu Java |
| `history.py` | Revision, nhãn, checksum, quota và restore có backup |
| `compiler.py` | Tiến trình TeX, timeout/Stop, cache, snapshot PDF và chặn stale |
| `toolchain.py` | Tìm công cụ, preflight package/Biber và cài TinyTeX package |
| `explainer.py` | Phân tích lỗi gốc, ngữ cảnh và hướng sửa Anh/Việt |
| `synctex.py` | Điều hướng source ↔ PDF theo snapshot |
| `network.py`, `console.py` | Protocol, server/client TCP/TLS, OT/RESYNC, console demo |
| `persistence.py` | Repository/service MongoDB tùy chọn |

`apps/frontend/.next`, `out`, `node_modules`, `.venv` và `target` là output/công cụ local, không phải source. Riêng `target` hiện chứa cả TinyTeX, runtime và bằng chứng kiểm thử: không xóa toàn bộ để dọn Java.

## Dữ liệu và chuyển từ Java

Home mặc định mới: `~/OverleafDesktopPython` (`C:\Users\<user>\OverleafDesktopPython` trên Windows). Có thể đổi bằng `OVERLEAF_PYTHON_HOME` hoặc tùy chọn `--home` khi chạy module. Dự án cũng có thể nằm ở thư mục ngoài do người dùng chọn.

Trong **Cài đặt → Dự án → Nhập dữ liệu từ bản Java**, chọn home cũ `~/OverleafDesktop`. App sao chép dự án, history và dữ liệu tài khoản/cài đặt hỗ trợ; giữ bản gốc và ghi ledger tránh nhập trùng. Không cần Java runtime để đọc dữ liệu này. Kiểm tra đường dẫn lưu mặc định sau nhập.

Không commit tài khoản, mật khẩu, `.env`, token, chứng chỉ/private key hoặc dữ liệu dự án cá nhân. Xem [kế hoạch dọn Java](docs/JAVA_CLEANUP_PLAN.md) trước khi xóa mã cũ.

## Server cộng tác

Sau khi chuẩn bị venv/dependency bằng launcher desktop, chạy server local:

```bat
scripts\run-python-server.bat --host 127.0.0.1 --port 8080
scripts\run-python-console.bat --host 127.0.0.1 --port 8080 --room DEMO
```

macOS/Linux dùng các file `.sh` cùng tên qua `sh`. Client mở **Cộng tác**, nhập host/port/room; các thành viên dùng cùng room. Server độc lập không cần frontend, pywebview hoặc TeX; có thể cài riêng package Python backend.

Kết nối ngoài localhost yêu cầu TLS và token ít nhất 24 ký tự; client xác minh CA/hostname. Hướng dẫn biến môi trường, PEM và Compose tại [deploy/python/README.md](deploy/python/README.md). Compose là mẫu chưa được chạy Docker trong lần nghiệm thu này.

## Kiểm thử và phát triển

Cài môi trường có test (từ root, nếu đã cài uv):

```sh
uv sync --project apps/desktop --extra desktop --extra dev --extra mongo --frozen
uv run --project apps/desktop --no-sync python -m pytest apps/desktop/tests -q
```

Hoặc dùng Python trong venv để cài `pip install -e "apps/desktop[desktop,dev,mongo]"` rồi chạy pytest bằng chính Python đó. MongoDB là optional; test repository sử dụng mongomock.

Kiểm frontend:

```sh
cd apps/frontend
npm ci
npm run build
npm run typecheck
node scripts/test-snippets.mjs
node scripts/test-pdf-geometry.mjs
```

Test trình duyệt/native cần thêm công cụ tương ứng và home test riêng; xem [hướng dẫn migration](docs/PYTHON_NEXTJS_MIGRATION.md). Kết quả đã ghi nhận: **124 pytest**, 186 build template PDF thật, kiểm SyncTeX/preview/export và benchmark fixture 248 trang trên Windows. Đây là kết quả các lần kiểm trước, không phải kiểm thử được chạy lại chỉ vì cập nhật README; không suy rộng thành nhanh hơn Overleaf web trên mọi tài liệu.

## Tài liệu và trách nhiệm

- [AI_Handoff.md](docs/AI_Handoff.md): lịch sử bàn giao; §33 là triển khai Python/Next.js, §34 là sửa ZIP thiếu package/font và cache lỗi.
- [PYTHON_NEXTJS_MIGRATION.md](docs/PYTHON_NEXTJS_MIGRATION.md): chạy, migration, kiểm chứng và giới hạn.
- [JAVA_PYTHON_PARITY.md](docs/JAVA_PYTHON_PARITY.md): đối chiếu tính năng hai nền tảng.
- [JAVA_CLEANUP_PLAN.md](docs/JAVA_CLEANUP_PLAN.md): danh sách chuẩn bị xóa Java, điều kiện và tài nguyên phải giữ.

Phân chia trách nhiệm để nhóm tiếp tục phát triển: **M1** UI/editor/window; **M2** compile/TeX/PDF/SyncTeX; **M3** TCP/TLS/protocol/deploy; **M4** OT/ACK/RESYNC/offline; **M5** account/local data/history/migration/Mongo và kiểm thử. Đây là phạm vi phụ trách, không khẳng định tác giả từng thay đổi.

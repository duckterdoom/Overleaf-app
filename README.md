# Latex — Python + Next.js

Compile/PDF → Word đã có cache hợp lệ, gộp request chuyển Word trùng và giới hạn job CPU chung giữa các workspace. Xem [hiệu năng và cấu hình cho server](docs/COMPILE_WORD_PERFORMANCE.md); tối ưu cache không thay thuật toán converter gốc.

**Backend web dùng MongoDB làm nguồn dữ liệu chính.** Điền kết nối Atlas/server trong `config/mongodb.local.json` (file riêng, gitignore), rồi chuyển dữ liệu SQLite/JSON bằng `scripts\migrate-mongodb.bat --dry-run` / `--apply` trước khi chạy lại web. Xem [MONGODB_PRIMARY.md](docs/MONGODB_PRIMARY.md). File TeX/ảnh/PDF/Word vẫn ở server volume; app không tự fallback SQLite khi MongoDB chưa sẵn sàng.

Web có **chia sẻ riêng tư theo lời mời** và **chia sẻ bằng liên kết**, quyền Viewer/Editor, quản lý/thu hồi quyền và danh sách Shared with you thật. Editor đồng bộ nội dung trực tiếp qua WebSocket bằng CodeMirror OT, kèm con trỏ có tên và avatar; thay đổi được lưu server trước khi xác nhận. Người nhận cần đăng nhập; lời mời hiện được owner tự copy/gửi, chưa có gửi email tự động. Xem [WEB_PROJECT_SHARING.md](docs/WEB_PROJECT_SHARING.md).

Đăng ký/đăng nhập web đã có bước chọn **Tutor hoặc Student**, phiên riêng và vùng dự án riêng theo tài khoản. Google sign-in được tích hợp nhưng cần cấu hình OAuth client của bạn trước khi sử dụng. Xem [luồng tài khoản và cấu hình Google](docs/WEB_AUTH_ONBOARDING.md). Dự án chung cũ được giữ nguyên, không tự gán cho tài khoản mới.

Thiết kế hiện tại tập trung vào **IELTS Writing**: [thiết kế IELTS](docs/IELTS_WRITING_DESIGN.md) và [prototype tương tác](docs/prototypes/writing/index.html). Task 1/Task 2, đề bài, vùng viết và chấm bốn tiêu chí nằm trong khung workspace. Đây là bản mẫu độc lập, chưa tích hợp nghiệp vụ vào ứng dụng. [Thiết kế Writing tổng quát trước](docs/WRITING_PRODUCT_DESIGN.md) được giữ để tham khảo phần mở rộng về sau.

Bản web lưu dự án, source, lịch sử và Word draft **trên server chạy Python**. Trình duyệt upload dữ liệu và tải bản sao về; người dùng không chọn đường dẫn lưu vật lý của server. Cấu trúc backend và cấu hình dữ liệu: [WEB_BACKEND_ARCHITECTURE.md](docs/WEB_BACKEND_ARCHITECTURE.md).

Ứng dụng soạn thảo LaTeX chạy trong trình duyệt, backend **Python/FastAPI** chạy trên máy local và phục vụ cả giao diện **Next.js** lẫn API tại cùng địa chỉ. Có thể dùng editor, tab nhiều tài liệu, PDF/Word preview, compile và upload mà không cài pywebview/WebView2. Web tách tài khoản/dự án cá nhân, dùng MongoDB cho identity/session/ACL/metadata; chưa phải dịch vụ public Internet hoặc hệ thống quản lý lớp hoàn chỉnh. Đường chạy desktop cũ được giữ để tương thích.

## Chạy local web

Cần Python **3.12+**, Node.js **22.13+** để build và bộ TeX có `latexmk` để compile. Đóng app desktop cũ trước khi dùng cùng thư mục dữ liệu.

Windows, tại root repository:

```powershell
.\scripts\run-python-web.bat --build
.\scripts\run-python-web.bat
```

Mở **http://127.0.0.1:8000/**; launcher tự mở trình duyệt. Dừng bằng **Ctrl+C** ở terminal. Nếu cổng đang bận, dùng `scripts\run-python-web.bat --port 8001`. Để chỉ chạy server: thêm `--no-browser`.

**Máy Windows vừa clone:** không cần lệnh `py` nếu `python` đã chạy được phiên bản 3.12+. Script tự thử `python` → `python3` → `py -3`, dùng Python đang cài để tạo môi trường và cài thư viện. Không copy `.venv`, `target` hay `node_modules` từ máy khác. Chỉ khi không tìm thấy Python tương thích mới thử tải runtime qua `uv` nếu có. Có thể chỉ rõ Python đã cài bằng `$env:LATEX_PYTHON = 'C:\duong-dan\python.exe'` trước khi chạy hai lệnh trên. Xem [xử lý lỗi Python khi clone](docs/WINDOWS_PYTHON_STARTUP.md).

MongoDB và công cụ TeX cần cấu hình riêng trên máy mới: nếu chưa có `config/mongodb.local.json`, copy từ `config/mongodb.local.example.json` rồi điền URI riêng theo [MONGODB_PRIMARY.md](docs/MONGODB_PRIMARY.md). Không commit URI/mật khẩu. `--build` chỉ chuẩn bị Python/UI rồi thoát; lệnh không có `--build` mới chạy backend và kết nối database. `run-python-server.bat` là server TCP cũ, không phải lệnh chạy web.

macOS/Linux:

```sh
sh scripts/run-python-web.sh --build
sh scripts/run-python-web.sh
```

Không cần cài thư viện GUI cho bản web. Lần đầu cần mạng để cài dependency; các lần sau có thể dùng lại thư viện, UI build và gói TeX đã chuẩn bị. Launcher ưu tiên `uv.lock` khi có uv, fallback pip nếu không có. Dữ liệu mặc định tiếp tục ở `~/OverleafDesktopPython` để giữ dự án cũ; đổi bằng `--home` hoặc `OVERLEAF_WEB_HOME`.

Đã kiểm thử local web trên Windows trong môi trường Python sạch **không có pywebview**. Xem [LOCAL_WEB.md](docs/LOCAL_WEB.md) cho cấu hình, phiên truy cập, phạm vi MongoDB và kết quả kiểm thử. Sau khi sửa UI cần build lại; không cần chạy một Next.js server riêng cho chế độ này.

## Công nghệ và luồng hoạt động

| Thành phần | Công nghệ / vai trò |
| --- | --- |
| Local web | Trình duyệt + FastAPI/Uvicorn cùng origin; cookie HttpOnly/SameSite cho phiên local |
| Desktop cũ (tùy chọn) | Python 3.12+, pywebview, hộp thoại file/thư mục native |
| Giao diện | Next.js static export, React, TypeScript, Tailwind CSS, Lucide icons |
| Editor | CodeMirror: highlight, folding, tìm/thay thế, chèn LaTeX |
| Backend local | FastAPI/Uvicorn; cookie ở web, bearer token ở desktop; bind loopback |
| Compile | latexmk với pdfLaTeX, XeLaTeX hoặc LuaLaTeX; TeX cài trên máy hoặc TinyTeX đi kèm môi trường local |
| PDF | PDF.js, pypdf và SyncTeX; PDF thật, export cùng bytes với preview |
| Cộng tác | Server Python riêng: TCP/TLS, wire v1/v2, OT/Lamport, ACK và RESYNC |
| Dữ liệu web | MongoDB/PyMongo cho identity/session/ACL/metadata/settings/history/review; source/binary/artifact trong server volume |

Luồng local web: **trình duyệt → UI Next.js đã build → API/WebSocket Python cùng origin → MongoDB + file/TeX/PDF**. Desktop cũ dùng pywebview thay trình duyệt và còn module cộng tác TCP/TLS cũ. Web dùng quyền chia sẻ tài khoản và WebSocket; luồng OT hiện chạy một backend worker. Node.js dùng cho build; Python phục vụ UI/API/WebSocket khi chạy.

## Chạy desktop cũ từ source (tùy chọn)

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

`--build` chuẩn bị dependency và build UI rồi thoát; lệnh tiếp theo mở ứng dụng. Launcher tự tạo `apps/backend/.venv` nếu thiếu. Sau khi sửa frontend, chạy lại `--build`. macOS cũng có launcher `Overleaf Python Desktop.command`.

Windows kiểm tra cả khả năng chạy Python, không chỉ sự tồn tại của `.venv`. Nếu môi trường hỏng, `scripts/ensure-python.ps1` ưu tiên Python đã cài trên máy; nếu không có mới dùng uv để chuẩn bị Python tại `target/python-runtimes`. Môi trường cần tạo lại được đổi tên thành `.venv.backup-...` để giữ bản dự phòng. Không xóa thư mục runtime khi `.venv` còn tham chiếu đến nó.

Compile cần TeX Live/MacTeX/MiKTeX hoặc TinyTeX phù hợp hệ điều hành. Windows hiện có thể dùng `target/TinyTeX/bin/windows/latexmk.exe`; thư mục này là công cụ local, **không được Git đóng gói sẵn cho máy khác**. Chọn `latexmk` trong **Cài đặt → Biên dịch** nếu app chưa tìm thấy. Không chép TinyTeX Windows hay `.venv` Windows sang macOS.

Mẫu ZIP **Universal manuscript template for Optica Publishing Group journals** cần thêm các gói và font ngoài TinyTeX tối thiểu. Trên Windows với TinyTeX local của dự án, chạy `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install-optica-tex.ps1`, rồi bấm **Recompile**. Script cài `colortbl`, `newtx`, `silence`, `lastpage`, `cite`, font Courier và các dependency của mẫu; kiểm tra cả `.sty` lẫn font metrics sau khi cài. Cần mạng khi tải gói. Nếu dùng TeX hệ thống khác, cài các gói tương ứng bằng trình quản lý TeX đó.

Git cần cho import GitHub public; Pandoc cần cho Word/Markdown. Lần tải dependency/package/import online cần mạng; soạn thảo và compile local hoạt động offline khi đã chuẩn bị đủ công cụ và gói TeX. Đây là bản chạy từ source, chưa có bộ cài độc lập `.exe`/`.app`/`.dmg` đã ký.

## Chức năng hiện có

- Tài khoản local, đăng nhập/remember/offline, hồ sơ/avatar, cài đặt Anh/Việt, theme và hoạt động sử dụng.
- Quản lý dự án: tìm kiếm, icon/màu, đổi tên/thùng rác, thư mục lưu tùy chọn và chuyển vị trí có backup; 29 template gallery cùng blank/example, nguồn Anh/Việt.
- Import ZIP/thư mục/Word/Markdown/GitHub public HTTPS; tạo file/thư mục lồng nhau, upload nhiều file/thư mục, kéo thả/dán ảnh, copy từ dự án khác hoặc build output, tải từ HTTPS và nhập `.bib`.
- Editor có outline kéo đổi chiều cao, toolbar Math/Link/Comment/Reference/Citation/Figure/Table/List/Indent, autosave, lịch sử phiên bản/nhãn/so sánh/khôi phục và bố cục split/editor/PDF/focus.
- Compile thủ công hoặc Auto Compile, Stop, timeout tùy chỉnh, log và Error Explainer Anh/Việt; kiểm tra toolchain/package. Cài package trong app chỉ áp dụng cho TinyTeX đi kèm và cần người dùng xác nhận.
- **Nhiều tài liệu trong một dự án:** chọn `.tex` ở ô **Tệp biên dịch** (ví dụ `ielts_report.tex`) rồi Recompile/F5/Ctrl+Enter. Mở file có `\documentclass` sẽ tự chọn file đó; mở file nội dung được include giữ nguyên file gốc đang chọn. Không đổi `main` mặc định của dự án. Hai dãy tab cho nguồn và các bản `tên-file.pdf`/`tên-file.docx`, có nút đóng; đổi tab lưu trước và giữ bản preview riêng. Word phân trang chỉnh sửa/lưu theo từng file; đồng bộ chữ LaTeX cũ vẫn chỉ áp dụng cho tệp chính. Tab/PDF cache nằm trong phiên làm việc, DOCX đã lưu được giữ trên đĩa. Báo cáo IELTS cần các gói `tcolorbox`, `environ`, `trimspaces` cùng bộ TeX thông dụng.
- Preview PDF cuộn nhiều trang, mục lục/link, zoom, cửa sổ PDF riêng và SyncTeX hai chiều. Export dùng đúng snapshot bytes đang preview; nguồn thay đổi hoặc compile lỗi sẽ chặn export cho đến khi compile thành công.
- Cộng tác `main.tex`: TCP/TLS, presence/cursor, offline queue, reconnect và RESYNC. Chưa đồng bộ toàn bộ cây file; room/queue nằm trong RAM, chưa có CRDT đầy đủ hoặc phân quyền theo tài khoản.

Nút Comment mở Review: nhận xét gắn đoạn chọn/dòng, trả lời, giải quyết/mở lại và lọc theo file/tổng quan. Nhận xét lưu local trong `.overleaf-review.json` của dự án, không chèn `%` vào source và chưa đồng bộ qua phòng TCP. Zotero/Mendeley/ReadCube dùng nhập `.bib`, chưa có OAuth. GitHub import chưa phải Git branch/merge.

Thanh icon ngoài cùng bên trái workspace chuyển giữa **Tệp/Outline**, **Review** và **Chat cộng tác**; chỉ một panel hiện mỗi lần, bấm lại icon đang chọn hoặc nút đóng để ẩn. Panel kéo đổi chiều rộng được. Chat hiện là giao diện chờ tích hợp nhiều người dùng, chưa gửi/nhận tin nhắn.

**Chế độ tập trung:** vào từ Bố cục hoặc `Ctrl/Cmd+Shift+M`. Nhấn **Esc** để thoát; thanh trên cùng vẫn có **Bố cục → Thoát chế độ tập trung** và nút **Thoát tập trung · Esc**. Thoát sẽ giữ bố cục editor/PDF đã chọn.

### Chỉnh sửa Word trong workspace và export DOCX

Cạnh **PDF preview** có tab **Word preview**. Mặc định **Word phân trang** mở trực tiếp DOCX từ bộ chuyển đổi gốc của bạn bằng SuperDoc, có trang, cột, hình, caption và header/footer. Có thể sửa trực tiếp, dùng toolbar định dạng, tự lưu sau khi ngừng gõ hoặc **Lưu Word** / `Ctrl/Cmd+S`. Nếu chưa có PDF, mở tab sẽ yêu cầu compile và hiển thị trạng thái chuyển đổi/phân trang.

Nút tải xuống mở menu **Export PDF (.pdf)** / **Export Word (.docx)**. DOCX ban đầu dùng nguyên bộ chuyển đổi từ `D:\ilovepdf-python` đã đóng gói trong app: builder bài mẫu, Universal mặc định và patched pdf2docx fallback. Khi đã sửa Word, download lấy đúng bytes DOCX đã lưu từ trình soạn thảo. Export vẫn yêu cầu PDF/source hiện hành; nếu nguồn thay đổi trong lúc Word có sửa, app giữ draft và chặn export thay vì âm thầm ghi đè. **Tạo lại từ PDF** thay draft sau xác nhận, giữ revision trước đó.

Mã nghiên cứu nằm trong [integrations/pdf2docx-research](integrations/pdf2docx-research/README.md); runtime trong `apps/backend/src/latex_backend/pdf_export`. Không cần thư mục nguồn cũ để chạy app. Chi tiết ở [PDF_TO_WORD_EXPORT.md](docs/PDF_TO_WORD_EXPORT.md).

`.overleaf-docx/` giữ DOCX hiện tại, revision trước và metadata/checksum, không tham gia compile. Đổi tab, xem lịch sử, đổi bố cục hoặc quay về danh sách dự án sẽ lưu trước. Sửa Word phân trang được giữ trong DOCX, không tự sửa mã LaTeX.

Nút **Đồng bộ chữ LaTeX** giữ chế độ cũ khi cần sửa chữ qua ánh xạ Word ↔ LaTeX; cần **Pandoc** (`PATH`, `OVERLEAF_PANDOC` hoặc `target/pandoc/pandoc.exe`). Chỉ chữ trong vùng ánh xạ duy nhất được ghi về nguồn; công thức, citation, macro và cấu trúc được bảo vệ. Chế độ này dùng bản HTML riêng `.overleaf-word.json`, không phải bản DOCX phân trang và không cung cấp bytes download. Chưa đồng bộ chỉnh sửa DOCX đầy đủ hai chiều với LaTeX.

Đã kiểm bài journal mẫu: Word phân trang **6 trang, 8 ảnh**, sửa chữ → tự lưu → đổi tab → mở lại vẫn giữ nội dung; download trùng bytes DOCX đã sửa. Đây là render DOCX thực, không phải engine TeX; ngắt dòng/trang có thể khác PDF hoặc Microsoft Word tùy font và cấu trúc tài liệu.

SuperDoc và worker được bundle local, tắt telemetry, không cần dịch vụ chỉnh Word trên cloud. Build tự chép worker qua `scripts/copy-worker.mjs`. SuperDoc dùng AGPL-3.0; dependency `@superdoc/docx-engine` có giấy phép riêng trong package. Giữ các thông báo giấy phép khi đóng gói/phân phối.

Chất lượng chuyển PDF → DOCX phụ thuộc chính bộ converter gốc; không cam kết mọi tài liệu giống PDF tuyệt đối. Preview không tự thay bằng ảnh nguyên trang để giả lập khả năng chỉnh sửa.


## Cấu trúc source

```text
Overleaf-app/
├── apps/
│   ├── backend/
│   │   ├── pyproject.toml / uv.lock     # dependency Python và lock
│   │   ├── src/latex_backend/           # package backend Python
│   │   │   ├── entrypoints/            # CLI web, desktop tùy chọn và console
│   │   │   ├── core/                   # cấu hình và application factory
│   │   │   ├── http/                   # API/middleware và adapter HTTP
│   │   │   ├── storage/                # project/file/state/history/repository
│   │   │   ├── compilation/            # TeX, toolchain, SyncTeX, explainer
│   │   │   ├── documents/              # Word/DOCX và báo cáo
│   │   │   ├── collaboration/          # networking cũ và comment/review
│   │   │   ├── pdf_export/             # converter gốc PDF → DOCX
│   │   │   └── resources/              # template và rule lỗi độc lập Java
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

Không còn file alias `api.py`, `store.py`, `compiler.py`... ở root package. Triển khai nằm trực tiếp trong các nhóm dưới đây; launcher đã trỏ sang `apps/backend`. Backend web không cần thư viện GUI.

| File trong `apps/backend/src/latex_backend/` | Chức năng |
| --- | --- |
| `entrypoints/desktop.py` | Cửa sổ pywebview tùy chọn, native dialogs, export, lưu buffer khi đóng |
| `core/config.py`, `core/application.py`, `entrypoints/web.py` | Đường dẫn dữ liệu, khởi tạo ứng dụng và CLI web |
| `http/api.py`, `http/parity_api.py` | Phiên, route thao tác, upload, theo dõi thay đổi |
| `storage/store.py` | Project/catalog, đường dẫn, server-managed storage, fingerprint |
| `storage/state.py` | Account/profile/avatar/settings/activity, đọc dữ liệu Java cũ |
| `storage/files.py` | Import/upload/copy/HTTPS, staging/rollback |
| `storage/history.py` | Revision, nhãn, checksum, quota và restore có backup |
| `compilation/compiler.py` | Tiến trình TeX, timeout/Stop, cache, snapshot PDF và chặn stale |
| `compilation/toolchain.py` | Công cụ, preflight package/Biber; cài gói do quản trị server thực hiện |
| `compilation/explainer.py`, `compilation/synctex.py` | Giải thích lỗi và điều hướng source ↔ PDF |
| `documents/` | Word text-sync cũ, native DOCX workspace, báo cáo IELTS |
| `collaboration/network.py`, `collaboration/review.py`, `entrypoints/console.py` | TCP/TLS/OT cũ, comment/reply/resolve, console demo |
| `storage/mongo.py`, `storage/mongo_store.py` | Kết nối/indexes và Store/State/History MongoDB chính cho web |
| `storage/migration.py` | Chuyển SQLite/JSON, backup/đối chiếu/resume/rebind volume |
| `storage/persistence.py` | Prototype M5 cũ, không phải đường persistence web chính |

`apps/frontend/.next`, `out`, `node_modules`, `.venv` và `target` là output/công cụ local, không phải source. Riêng `target` hiện chứa cả TinyTeX, runtime và bằng chứng kiểm thử: không xóa toàn bộ để dọn Java.

## Dữ liệu và chuyển từ Java

**Web:** người vận hành cấu hình `LATEX_DATA_DIR` hoặc `--home`; dự án theo tài khoản nằm ở `<data-dir>/users/<user-id>/projects/<id>`. MongoDB quản lý ownership/metadata; source/binary vẫn trên volume. Không chọn/chuyển thư mục server trong giao diện. Các dự án desktop ngoài vùng này cần nén ZIP rồi upload; bản gốc không bị sửa. Cơ chế chọn thư mục/nhập Java qua đường dẫn dưới đây chỉ còn ở desktop cũ. Xem hướng dẫn MongoDB/migration trước khi khởi động web.

```powershell
$env:LATEX_DATA_DIR = 'D:\Latex-data'
.\scripts\run-python-web.bat --no-browser
```

Khi publish, data-dir phải nằm trên ổ/volume bền vững và có backup. Bản hiện tại vẫn chỉ dành cho một workspace trên loopback; cần thêm session theo người dùng, phân quyền project và worker TeX cách ly trước khi public. Xem [cấu trúc và lộ trình backend](docs/WEB_BACKEND_ARCHITECTURE.md).

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
uv sync --project apps/backend --extra desktop --extra dev --extra mongo --frozen
uv run --project apps/backend --no-sync python -m pytest apps/backend/tests -q
```

Hoặc dùng Python trong venv để cài `pip install -e "apps/backend[web-local,dev]"` rồi chạy pytest bằng chính Python đó. Web production yêu cầu MongoDB; unit tests inject mongomock riêng và không tự kết nối database sản xuất.

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

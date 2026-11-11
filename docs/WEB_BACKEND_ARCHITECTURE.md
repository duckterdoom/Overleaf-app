# Backend web và lưu trữ trên server

**Cập nhật 11/11/2026:** nhiều backend có home riêng có thể dùng chung database. Module `storage/shared_files.py` lưu source/ảnh/history/Word draft theo manifest CAS và chunk bất biến trong MongoDB; volume local là working copy để compile. Đăng ký storage theo node thay vì ép một đường dẫn toàn database. Xem [SHARED_MONGODB_FILES.md](SHARED_MONGODB_FILES.md). Các mô tả file chỉ ở volume và một storage binding độc quyền phía dưới là lịch sử trước cập nhật này. Identity/ACL/session đã dùng MongoDB, không còn SQLite ở runtime web.

Cập nhật nguồn dữ liệu chính 10/11/2026: identity/session/ACL, project metadata, settings/activity, history metadata và review/IELTS dùng MongoDB; source/binary/artifact ở volume. Thêm `storage/mongo.py`, `mongo_store.py`, `migration.py`; xem [MONGODB_PRIMARY.md](MONGODB_PRIMARY.md) cho cấu hình Atlas và migration có backup. Các mốc SQLite/prototype dưới đây là lịch sử trước chuyển đổi này.

Cập nhật chia sẻ 10/11/2026: `collaboration/sharing.py` lưu project ACL/link/invitation trong SQLite identity; `http/sharing_api.py` quản lý share và danh sách own/shared; gateway định tuyến request project về workspace owner sau kiểm quyền. Actor dùng ContextVar, không tráo session giữa user. Xem [WEB_PROJECT_SHARING.md](WEB_PROJECT_SHARING.md).

Cập nhật 10/11/2026: thêm `auth/accounts.py` (identity/session SQLite), `http/web_accounts.py` (gateway và workspace theo tài khoản). Web không dùng `State.session` chung làm danh tính browser nữa. Dữ liệu mới nằm trong `<home>/users/<user-id>/`, identity/session ở `<home>/.auth/accounts.sqlite3`. Xem [WEB_AUTH_ONBOARDING.md](WEB_AUTH_ONBOARDING.md); các mô tả workspace dùng chung bên dưới là mốc kiến trúc trước thay đổi này. MongoDB chưa được nối vào repository tài khoản.

Cập nhật ngày 07/11/2026. Chọn **modular monolith**: một backend FastAPI, chia module theo chức năng. Với quy mô hiện tại, cách này giữ luồng compile/PDF/Word dễ kiểm thử mà không cần quản lý nhiều dịch vụ. Thư mục dữ liệu tách khỏi source; trình duyệt chỉ gửi nội dung qua API.

## Cấu trúc đã áp dụng

```text
apps/
  frontend/                           # Next.js, React, CodeMirror, PDF.js, Word UI
  backend/                            # Backend Python độc lập với frontend
    pyproject.toml / uv.lock
    src/latex_backend/                # Package backend mới
      auth/
        accounts.py                   # Identity, password/Google, role và browser sessions
      core/
        config.py                     # Đường dẫn tài nguyên và data dir từ môi trường
        application.py                # Khởi tạo Store, Compiler và FastAPI cho web
      http/
        web_accounts.py               # Google OAuth/onboarding, tách Store/Compiler theo user
        api.py                        # HTTP middleware, project/file/compile/PDF
        parity_api.py                 # HTTP cho upload, history, review, Word, profile
      storage/
        store.py                      # Project, path validation, managed storage
        files.py                      # Upload/import/copy, staging và rollback
        state.py                      # Account/settings/avatar/activity hiện tại
        history.py                    # Revision/checksum/restore
        persistence.py                # Prototype MongoDB, chưa nối Store/UI
      compilation/
        compiler.py                   # latexmk, job, timeout, PDF snapshot
        toolchain.py                  # Kiểm tra/cài công cụ TeX
        explainer.py                  # Giải thích lỗi
        synctex.py                    # Source ↔ PDF
      documents/
        word.py                       # Chế độ Word text-sync cũ
        docx_workspace.py             # DOCX chỉnh trực tiếp, revision và export
        ielts.py                      # Báo cáo IELTS
      collaboration/
        network.py                    # TCP/TLS/OT cũ; chưa phải WebSocket room
        review.py                     # Comment/reply/resolve
      pdf_export/                     # Converter PDF → DOCX và bản gốc của người dùng
      resources/                      # Templates và rule lỗi
      entrypoints/
        web.py                        # CLI web, không import pywebview
        desktop.py                    # Adapter desktop tùy chọn
        console.py                    # CLI networking cũ
    tests/
docs/
scripts/                              # Lệnh chạy/build/kiểm thử
```

Code import trực tiếp từ các package chức năng, ví dụ `latex_backend.storage.store`. Đã chuyển vật lý `apps/desktop` thành `apps/backend`, đổi package `overleaf_desktop` thành `latex_backend`, bỏ toàn bộ file alias ở root và chuyển các điểm khởi động vào `entrypoints`. Root package chỉ còn `__init__.py` cùng các thư mục chức năng. Launcher, import, test, subprocess converter và Dockerfile networking đã cập nhật. Các lệnh `.bat`/`.sh` quen thuộc vẫn dùng được.

Hướng phụ thuộc: `core/application → http → service chức năng → storage`. `core/config` cung cấp cấu hình đường dẫn cho các lớp. Các service vẫn có những phụ thuộc thực tế giữa compile và document export; không tạo một lớp repository/service trống cho từng file. Bundle converter gốc trong `pdf_export/original` không bị sửa.

## Dữ liệu nằm ở đâu?

```text
LATEX_DATA_DIR/                        # Server volume, không commit vào Git
  .app/                               # State, avatar, registry
  projects/
    <project-id>/
      .project.json
      main.tex, chapters/, figures/    # Tệp người dùng
      .overleaf-history/              # Lịch sử
      .overleaf-review.json           # Comment (tên thực tế do service quản lý)
      .overleaf-docx*/                 # Word draft theo root tài liệu
      build/                          # PDF và auxiliary output
  .import-tmp/                        # Tạm nhận import, dọn sau xử lý
  .trash/                             # Dự án đã xóa
```

Web bật `Store(managed=True)`. Tạo/import dự án luôn vào `projects/<id>`; người dùng không nhập ổ D:, đường dẫn Linux hoặc thư mục vật lý trên server. API từ chối `parent` khác rỗng; chặn move, import-path, migrate-java, đổi `projectsDirectory` và `latexmkPath`. Cài gói TeX qua browser cũng bị chặn: đây là việc của quản trị server. Metadata project trả `storage: "server"`, không trả đường dẫn vật lý trong `folder`.

Chọn file hoặc thư mục trong browser là **chọn dữ liệu để upload**, không chọn nơi lưu trên server. Lưu editor/Word ghi về API; download là lấy bản sao PDF/DOCX về máy người dùng. Đóng tab không xóa dữ liệu đã lưu. Dữ liệu nằm trong filesystem của backend, không phải localStorage/IndexedDB của trình duyệt.

## Cấu hình cho người vận hành

Windows, tại root repo:

```powershell
$env:LATEX_DATA_DIR = 'D:\Latex-data'
.\scripts\run-python-web.bat --build
.\scripts\run-python-web.bat --no-browser
```

Hoặc `scripts\run-python-web.bat --home D:\Latex-data --no-browser`. `--home` ưu tiên hơn biến môi trường. Thứ tự mặc định: `LATEX_DATA_DIR` → `OVERLEAF_WEB_HOME` → `OVERLEAF_PYTHON_HOME` → `~/OverleafDesktopPython` để giữ dữ liệu cũ.

Trên Linux có thể đặt `LATEX_DATA_DIR=/var/lib/latex` khi chạy launcher `.sh`. Nếu đóng gói container sau này, mount volume bền vững vào đường dẫn này và backup toàn bộ home, không chỉ `build/*.pdf`. Không chạy hai process ghi cùng home; bản hiện tại cần một process/worker cho một workspace.

Dự án desktop đã nằm trong `<home>/projects/<id>` tiếp tục mở được. Những dự án đã đặt ở thư mục ngoài không được web đọc hoặc di chuyển tự động; bản gốc vẫn nguyên vẹn. Nén source thành ZIP và upload để tạo bản sao trong vùng server. Tính năng chọn thư mục native vẫn còn khi chạy desktop cũ.

## Phạm vi trước khi publish

Đây là nền tảng **lưu trên server cho một workspace**. Launcher vẫn chỉ bind loopback. Đăng nhập hiện dùng state/session chung của process; cookie bootstrap không phải đăng nhập độc lập của nhiều người. Chưa thể dùng nó làm web public nhiều tài khoản.

Các bước tiếp theo trước public: session theo từng browser/user; owner/membership và kiểm quyền trên mọi project/file/history/Word; nối MongoDB cho metadata thay JSON chung; hàng đợi và worker TeX chạy cách ly với quota; HTTPS/reverse proxy, kiểm soát outbound URL/Git và giới hạn tài nguyên. Tệp LaTeX/ảnh/PDF/DOCX có thể tiếp tục ở server volume hoặc object storage; không cần nhét toàn bộ binary vào document MongoDB. Module Mongo hiện tại vẫn là prototype, không tự migrate dữ liệu.

Không sửa `deploy/python/compose.yaml` trong đợt này vì file đang có thay đổi riêng của người dùng. Compose cũ là cấu hình networking TCP/TLS, không phải cấu hình publish workspace web.

## Kiểm chứng

`test_server_storage.py` kiểm tra từ chối đường dẫn do browser cung cấp, bỏ qua default folder cũ, chặn đổi executable/cài gói qua browser, lưu và mở lại sau khởi tạo backend mới, upload ZIP vào vùng managed, kiểm tên file và không phục vụ dự án ngoài vùng. Bộ test cũ kiểm tra tương thích các tính năng đã có. Browser test chạy server production và kiểm PDF/Word/upload/import thật trong môi trường không có pywebview.

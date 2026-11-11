# Latex Local Web — bước chuyển đầu tiên (06/11/2026)

Cập nhật MongoDB 10/11/2026: web hiện yêu cầu MongoDB/Atlas cho identity/session/ACL và metadata. Cấu hình `config/mongodb.local.json`, dừng web cũ và migrate dữ liệu trước khi chạy lại. Xem [MONGODB_PRIMARY.md](MONGODB_PRIMARY.md). Không fallback SQLite/JSON; source/binary vẫn ở server volume.

Cập nhật 10/11/2026: web yêu cầu đăng nhập và chọn Tutor/Student; không còn truy cập không đăng nhập vào workspace chung. Phiên và vùng dữ liệu tách theo tài khoản. Google cần OAuth client cấu hình ở backend. Xem [WEB_AUTH_ONBOARDING.md](WEB_AUTH_ONBOARDING.md), bao gồm lưu ý giữ/chuyển dữ liệu chung cũ. Các đoạn mô tả phiên chung phía dưới là lịch sử trước thay đổi tài khoản này.

Cập nhật 09/11/2026: nếu Python venv cũ bị Windows App Control chặn và máy đã có Python chính thức (đã kiểm tra 3.15.2), dùng `scripts/setup-installed-python-web.ps1` một lần, sau đó chạy `scripts/run-python-web.bat` bình thường. Launcher ưu tiên `target/official-python-web-env` khi có. Không cần cài thêm phiên bản Python; nhóm dependency `web-local` bỏ các tùy chọn YAML/server reload không dùng. Xem [WINDOWS_PYTHON_STARTUP.md](WINDOWS_PYTHON_STARTUP.md).

Cập nhật 07/11/2026: web đã chuyển sang **server-managed storage**, không còn prompt chọn thư mục server. Backend được chia thành `core`, `http`, `storage`, `compilation`, `documents`, `collaboration`; xem [WEB_BACKEND_ARCHITECTURE.md](WEB_BACKEND_ARCHITECTURE.md).

Đường chạy chính mới là trình duyệt tại `http://127.0.0.1:8000/`. FastAPI phục vụ UI Next.js static export và API cùng origin, nên chưa cần một Node server thứ hai hoặc CORS wildcard. Python/TeX vẫn chạy trên máy làm server, không chạy trong trình duyệt.

## Khởi động

Windows:

```powershell
.\scripts\run-python-web.bat --build
.\scripts\run-python-web.bat
# Hoặc chọn cổng và không tự mở browser:
.\scripts\run-python-web.bat --port 8001 --no-browser
```

Mac/Linux:

```sh
sh scripts/run-python-web.sh --build
sh scripts/run-python-web.sh
```

`--build` chỉ cài/check dependency và build giao diện, không khởi động server. Không có `--build` sẽ chạy server; nếu thiếu UI build thì tự build trước. Chạy lần đầu cần mạng/Python 3.12+/Node 22.13+. Compile cần TeX, Word text-sync cũ cần Pandoc như trước. Không cần pywebview, GTK/Qt, WebView2 cho đường chạy web. Windows đã chạy thực; shell script Mac/Linux đã kiểm cú pháp, chưa được nghiệm thu runtime trên máy Mac/Linux. Khi Ctrl+C ở Windows hỏi “Terminate batch job (Y/N)?”, chọn Y.

CLI tương đương sau khi cài package: `python -m latex_backend.entrypoints.web`, hoặc console entry `latex-web` (`overleaf-web` còn giữ làm tên lệnh tương thích). Backend đã chuyển vật lý sang `apps/backend/src/latex_backend`; không còn thư mục `apps/desktop` hoặc file alias ở root package. Sau khi đổi cấu trúc, chạy lại launcher để cập nhật editable install từ lock.

Các tham số: `--home <folder>`, `--frontend <out-folder>`, `--latexmk <path>`, `--host 127.0.0.1|localhost`, `--port <number>`, `--no-browser`. Mặc định home lấy LATEX_DATA_DIR, rồi OVERLEAF_WEB_HOME, rồi OVERLEAF_PYTHON_HOME, rồi ~/OverleafDesktopPython. Đây là đường dẫn do người vận hành chọn; trình duyệt không được thay đổi. Không mở đồng thời hai process desktop/web ghi cùng home; dùng home khác khi kiểm thử. Ctrl+C dừng server và các job qua lifespan cleanup.

## Phiên và chức năng

- Truy cập URL thông thường, không cần token trong fragment/link. Frontend gọi POST `/api/web/session`, nhận cookie HttpOnly, SameSite=Strict, path=/api; API chặn origin khác, request cross-site và Host ngoài loopback. Reload sẽ khởi tạo lại phiên local nếu cần. Bearer bootstrap cũ vẫn hoạt động ở chế độ desktop/test.
- Phiên local này là quyền truy cập backend của **một workspace dùng trên máy local**. Nó chưa phải đăng nhập/phân quyền theo từng browser. State đăng nhập/profile hiện được dùng chung bởi cùng process; không triển khai qua reverse proxy Internet hoặc chia sẻ cho người không tin cậy ở bước này.
- Editor, history, outline, tab nhiều root, compile/Stop/Auto Compile, PDF thật, DOCX converter gốc và Word edit/export được giữ. Download dùng browser thay dialog native.
- Open PDF in separate window dùng tab trình duyệt, cùng cookie và root; SyncTeX từ tab PDF gửi message về tab editor với kiểm origin/project. Ảnh/PDF của cây file mở trong tab mới qua API đọc file có kiểm đường dẫn/kích thước. SVG được tải xuống, không chạy như trang HTML/SVG của app.
- Upload file/folder, ảnh đại diện và import ZIP/Word/Markdown dùng input trình duyệt. Browser folder upload không bảo đảm giữ các thư mục rỗng; đường upload native cũ vẫn dành cho desktop. Dữ liệu được ghi vào vùng lưu của server, không nhận đường dẫn vật lý từ browser. Move/import-path/migrate-java và cài TeX từ browser bị chặn; metadata project không lộ folder vật lý. Mặc định thư mục desktop cũ không còn điều khiển nơi lưu web.
- Browser cảnh báo khi đóng/reload nếu nguồn hoặc Word còn chưa lưu. Đây không thay cho Ctrl+S/tự lưu trước khi đóng.

## MongoDB và cộng tác

PyMongo hiện là dependency chính. `storage/mongo.py`, `mongo_store.py`, `auth/accounts.py` và `collaboration/sharing.py` nối MongoDB vào luồng web thực tế; `persistence.py` chỉ còn là prototype M5 cũ. Khi có source SQLite/JSON cũ, phải chạy migration có backup/verify; URI tự nó không chuyển dữ liệu.

Web đã có session riêng, owner/editor/viewer, private invitation và public link sau đăng nhập. Đồng bộ gõ realtime/CRDT/OT, hàng đợi compile cách ly và deployment public vẫn là công việc tiếp theo; không dùng hộp thoại phòng TCP/TLS ở web.

## Thư viện

- Next.js 16.4.4 → **16.4.5**; CodeMirror view 6.38.0 → **6.43.15**; Playwright 1.64.1 → **1.65.0**.
- FastAPI tối thiểu **0.162**, Uvicorn tối thiểu **0.54**; group web dùng `uvicorn[standard]` và PyMongo **4.19+**.
- Lock cập nhật Pydantic 2.14.3, pydantic-core 2.50.4, dnspython 2.9.2, typing-inspection 0.4.5; thêm dependency standard server (httptools, websockets, watchfiles, dotenv, yaml; uvloop có marker nền tảng).
- Giữ pdf2docx/PyMuPDF và source converter gốc để không thay đổi output đã đối chiếu. Không nâng major TypeScript/Lucide/Pillow chỉ vì registry có bản mới; các bản đang dùng vẫn cần tương thích ứng dụng.
- `npm ci` và `uv sync --frozen --extra web` tái tạo môi trường từ lock. Launcher dùng `--inexact` khi tái sử dụng venv cũ để không xóa các extra desktop/dev đang có. Pip fallback cài nhóm web theo ràng buộc pyproject, không bảo đảm khớp lock hoàn toàn như uv.

## Kiểm chứng

Cập nhật server storage 07/11/2026: **152 backend tests đạt**, production build/TypeScript đạt. Browser `target/server-storage-web-02` đạt các luồng bên dưới và kiểm thêm không còn trường chọn thư mục ở new/settings/manage. Kiểm persistence qua khởi tạo backend mới ở `test_server_storage.py`. Các kết quả 148 test bên dưới là mốc trước đợt server-managed storage.

- `scripts/run-python-web.bat --build` chạy thật: npm ci + Next static production build thành công, TypeScript đạt; npm audit không có advisory được báo.
- Môi trường riêng `target/local-web-clean-env`: cài 33 package bằng uv frozen, không cài/import được pywebview nhưng chạy được server web và toàn bộ browser test.
- `scripts/test-local-web.mjs <python> <home-mới>` chạy CLI production `--port 0 --no-browser`, không dùng server test/bearer link. Kiểm cookie HttpOnly, không lộ token trong URL/sessionStorage, reload, create/save, PDF 2 trang/export cùng bytes, tab PDF riêng, Word sửa/export cùng draft bytes, upload ảnh, mở ảnh, avatar, ZIP import/compile. Bằng chứng `target/local-web-ui-02/`.
- Backend có 4 test mới cho bootstrap/cookie/origin/cross-site/Host, desktop isolation, binary paths/SVG và CLI không bind public. Full suite: **148 pytest đạt**.
- Build cuối kiểm lại `local-web-ui-03` đạt trong venv sạch; `local-web-documents-regression-01` đạt nhiều root/PDF/Word; `local-web-legacy-regression-02` đạt chức năng cũ kể cả reload English/profile. Đã sửa fallback bootstrap desktop reload nhận 404 hoặc 405 của static server, không chỉ 404.
- Launcher Windows chạy thật với `--port 0 --no-browser --home target/local-web-launcher-01`: lấy cookie, gọi health trả local-web và dừng bằng Ctrl+C. `uv lock --check` và `git diff --check` đạt.

Tài liệu nền: [Next static export](https://nextjs.org/docs/app/guides/static-exports), [FastAPI cookies](https://fastapi.tiangolo.com/advanced/response-cookies/).

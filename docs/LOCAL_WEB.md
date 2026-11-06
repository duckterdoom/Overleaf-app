# Latex Local Web — bước chuyển đầu tiên (06/11/2026)

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

CLI tương đương sau khi cài package: `python -m overleaf_desktop.web`, hoặc console entry `overleaf-web`. Thư mục `apps/desktop` và tên package cũ tạm giữ để không phá import/dữ liệu; chúng hiện chứa backend dùng chung, không có nghĩa bản web cần GUI.

Các tham số: `--home <folder>`, `--frontend <out-folder>`, `--latexmk <path>`, `--host 127.0.0.1|localhost`, `--port <number>`, `--no-browser`. Mặc định home lấy OVERLEAF_WEB_HOME, rồi OVERLEAF_PYTHON_HOME, rồi ~/OverleafDesktopPython. Không mở đồng thời hai process desktop/web ghi cùng home; dùng home khác khi kiểm thử. Ctrl+C dừng server và các job qua lifespan cleanup.

## Phiên và chức năng

- Truy cập URL thông thường, không cần token trong fragment/link. Frontend gọi POST `/api/web/session`, nhận cookie HttpOnly, SameSite=Strict, path=/api; API chặn origin khác, request cross-site và Host ngoài loopback. Reload sẽ khởi tạo lại phiên local nếu cần. Bearer bootstrap cũ vẫn hoạt động ở chế độ desktop/test.
- Phiên local này là quyền truy cập backend của **một workspace dùng trên máy local**. Nó chưa phải đăng nhập/phân quyền theo từng browser. State đăng nhập/profile hiện được dùng chung bởi cùng process; không triển khai qua reverse proxy Internet hoặc chia sẻ cho người không tin cậy ở bước này.
- Editor, history, outline, tab nhiều root, compile/Stop/Auto Compile, PDF thật, DOCX converter gốc và Word edit/export được giữ. Download dùng browser thay dialog native.
- Open PDF in separate window dùng tab trình duyệt, cùng cookie và root; SyncTeX từ tab PDF gửi message về tab editor với kiểm origin/project. Ảnh/PDF của cây file mở trong tab mới qua API đọc file có kiểm đường dẫn/kích thước. SVG được tải xuống, không chạy như trang HTML/SVG của app.
- Upload file/folder, ảnh đại diện và import ZIP/Word/Markdown dùng input trình duyệt. Browser folder upload không bảo đảm giữ các thư mục rỗng; đường upload native cũ vẫn dành cho desktop. Thư mục lưu dự án của local server chọn bằng nhập đường dẫn vì browser không cung cấp absolute path qua bộ chọn folder upload.
- Browser cảnh báo khi đóng/reload nếu nguồn hoặc Word còn chưa lưu. Đây không thay cho Ctrl+S/tự lưu trước khi đóng.

## MongoDB và cộng tác

Đã đưa PyMongo vào nhóm dependency `web`, giữ module `persistence.py` và group `mongo`. Tuy nhiên repository hiện có chỉ là prototype; UI/Store vẫn lưu file + JSON/history local. Bật OVERLEAF_MONGO_URI chưa tự chuyển dữ liệu UI sang MongoDB. Không ghi/migrate vào Mongo ở bước này, không yêu cầu Mongo đang chạy để khởi động local web.

Chưa thêm Yjs/pycrdt/Redis/Celery hoặc phòng WebSocket mới khi chúng chưa được nối vào luồng thực tế. Uvicorn standard có transport WebSocket để chuẩn bị bước sau; networking hiện tại vẫn là TCP/TLS cũ qua backend. Bước tiếp theo cần thiết kế session theo người dùng, project membership/permissions, nối Mongo làm storage thực và dịch vụ đồng bộ tài liệu; sau đó tách compile worker trước khi public Internet.

## Thư viện

- Next.js 16.4.4 → **16.4.5**; CodeMirror view 6.38.0 → **6.43.15**; Playwright 1.64.1 → **1.65.0**.
- FastAPI tối thiểu **0.162**, Uvicorn tối thiểu **0.54**; group web dùng `uvicorn[standard]` và PyMongo **4.19+**.
- Lock cập nhật Pydantic 2.14.3, pydantic-core 2.50.4, dnspython 2.9.2, typing-inspection 0.4.5; thêm dependency standard server (httptools, websockets, watchfiles, dotenv, yaml; uvloop có marker nền tảng).
- Giữ pdf2docx/PyMuPDF và source converter gốc để không thay đổi output đã đối chiếu. Không nâng major TypeScript/Lucide/Pillow chỉ vì registry có bản mới; các bản đang dùng vẫn cần tương thích ứng dụng.
- `npm ci` và `uv sync --frozen --extra web` tái tạo môi trường từ lock. Launcher dùng `--inexact` khi tái sử dụng venv cũ để không xóa các extra desktop/dev đang có. Pip fallback cài nhóm web theo ràng buộc pyproject, không bảo đảm khớp lock hoàn toàn như uv.

## Kiểm chứng

- `scripts/run-python-web.bat --build` chạy thật: npm ci + Next static production build thành công, TypeScript đạt; npm audit không có advisory được báo.
- Môi trường riêng `target/local-web-clean-env`: cài 33 package bằng uv frozen, không cài/import được pywebview nhưng chạy được server web và toàn bộ browser test.
- `scripts/test-local-web.mjs <python> <home-mới>` chạy CLI production `--port 0 --no-browser`, không dùng server test/bearer link. Kiểm cookie HttpOnly, không lộ token trong URL/sessionStorage, reload, create/save, PDF 2 trang/export cùng bytes, tab PDF riêng, Word sửa/export cùng draft bytes, upload ảnh, mở ảnh, avatar, ZIP import/compile. Bằng chứng `target/local-web-ui-02/`.
- Backend có 4 test mới cho bootstrap/cookie/origin/cross-site/Host, desktop isolation, binary paths/SVG và CLI không bind public. Full suite: **148 pytest đạt**.
- Build cuối kiểm lại `local-web-ui-03` đạt trong venv sạch; `local-web-documents-regression-01` đạt nhiều root/PDF/Word; `local-web-legacy-regression-02` đạt chức năng cũ kể cả reload English/profile. Đã sửa fallback bootstrap desktop reload nhận 404 hoặc 405 của static server, không chỉ 404.
- Launcher Windows chạy thật với `--port 0 --no-browser --home target/local-web-launcher-01`: lấy cookie, gọi health trả local-web và dừng bằng Ctrl+C. `uv lock --check` và `git diff --check` đạt.

Tài liệu nền: [Next static export](https://nextjs.org/docs/app/guides/static-exports), [FastAPI cookies](https://fastapi.tiangolo.com/advanced/response-cookies/).

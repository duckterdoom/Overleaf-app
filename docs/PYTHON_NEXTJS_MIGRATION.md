# Python + Next.js desktop

Bản mới mở **cửa sổ desktop riêng bằng pywebview**, giao diện Next.js/React/TypeScript/Tailwind, backend Python/FastAPI. Các nhóm chức năng đang có ở Java đã được triển khai và nối vào bản mới; bảng đối chiếu chi tiết ở [JAVA_PYTHON_PARITY.md](JAVA_PYTHON_PARITY.md). Mục 32 của AI_Handoff ghi lại bản nền ban đầu; **mục 33 là trạng thái cập nhật**.

## Chạy ứng dụng

Windows, từ root repository:

```bat
scripts\run-python-desktop.bat --build
scripts\run-python-desktop.bat
```

macOS/Linux:

```sh
sh scripts/run-python-desktop.sh --build
sh scripts/run-python-desktop.sh
```

Mac có thể cấp quyền `chmod +x "Overleaf Python Desktop.command"` rồi nhấp đúp file đó. Máy cần Python 3.12+, Node.js 22.13+ để build UI, native WebView và TeX có latexmk. macOS dùng MacTeX và /Library/TeX/texbin; Windows tìm PATH hoặc target/TinyTeX/bin/windows/latexmk.exe. Git dùng cho GitHub import, Pandoc dùng cho DOCX/Markdown import. Chọn lại đường dẫn latexmk và timeout trong **Cài đặt → Biên dịch** nếu cần.

Launcher tạo venv/cài dependency nếu thiếu; `--build` cài frontend từ package-lock và build static export. Chạy lần sau không cần Node server hoặc JVM. Sau khi chuẩn bị đủ dependency/TeX/package, chức năng local chạy ngoại tuyến. Chưa tạo bộ cài .exe/.app/.dmg; không chép .venv hay TinyTeX Windows sang Mac.

Cài môi trường kiểm thử đúng Python lock:

```sh
uv sync --project apps/backend --extra desktop --extra dev --extra mongo --frozen
```

Nếu dùng pip thay uv: `python -m pip install -e "apps/backend[desktop,dev,mongo]"`. Launcher thông thường chỉ cài desktop, MongoDB là tùy chọn.

## Chức năng đã chuyển

| Nhóm | Bản Python/Next.js |
| --- | --- |
| Tài khoản | Đăng ký/đăng nhập, PBKDF2-SHA256, email trùng/sai mật khẩu, remember, offline, logout; hồ sơ tên/email/password và avatar PNG/JPG/GIF crop/remove |
| Dashboard | Tìm dự án, icon/màu, đổi tên/trash, tạo ở thư mục tùy chọn, thư mục mặc định, chuyển dự án bằng bản sao kiểm checksum và giữ backup nguồn; affiliation |
| Mẫu | 31 cấu hình: 29 mẫu trong gallery + blank + example; 9 nhóm, tags/search/filter, nguồn Việt/Anh; toàn bộ asset nằm trong package Python, không đọc từ Java lúc chạy |
| Import | ZIP kiểm traversal/symlink/giới hạn và tìm root .tex; folder; Word/Markdown qua Pandoc; GitHub public HTTPS; staging, hủy và cleanup tiến trình con |
| Files | File/folder lồng nhau, chọn thư mục đích, rename/trash/context menu, mở ảnh/PDF bằng app ngoài; upload nhiều tệp/thư mục/thư mục rỗng, kéo-thả/dán ảnh, queue và rollback khi lỗi |
| Nguồn thêm tệp | Copy từ project khác hoặc output build, HTTPS download giới hạn redirect/size, nhập .bib từ ReadCube/Zotero/Mendeley |
| Editor | CodeMirror, highlight/folding/find-replace, font/size/wrap/autoclose/autosave; full toolbar đúng thứ tự Math/Link/Comment/Ref/Citation/Figure/Table/List/Indent, dialog đọc label/.bib/ảnh trong project và chống chèn vào buffer đã đổi |
| Workspace | Outline cuộn/đổi chiều cao, divider editor/PDF, layout split/editor/PDF, PDF cửa sổ riêng, focus Ctrl/Cmd+Shift+M, lưu khi đổi file/đóng và phát hiện thay đổi ngoài app |
| History | Preimage, checkpoint 3 giây, nhãn, lọc file/label, đọc revision, compare, khôi phục có backup; checksum và quota 1.024 revision/128 MiB, tối đa 2 MiB nội dung/revision |
| Compile | latexmk và pdfLaTeX/XeLaTeX/LuaLaTeX, log stream, Stop cả process tree, timeout 120 giây tùy chỉnh/0 = không giới hạn, Auto Compile debounce, cache/clean, staging ASCII cho root Unicode trên Windows, chặn PDF thiếu glyph |
| Error Explainer | Đủ 28 rule Việt/Anh từ Java được chuyển thành dữ liệu JSON, thay placeholder tên lệnh/tệp/ngữ cảnh; phân biệt wrapper exit code với lỗi gốc; giải thích start/timeout/stop/glyph |
| TeX preflight | Probe package/Biber/tools; cài package sau khi người dùng đồng ý, chỉ sửa TinyTeX đi kèm; hủy cài và dừng tiến trình khi đóng |
| PDF/SyncTeX | PDF thật, lazy canvas/cuộn liên tục/link mục lục/Back/zoom; snapshot bytes bất biến, export cùng bytes preview, chặn stale/dirty/compile lỗi; SyncTeX 2 chiều, tệp con, Unicode root, CropBox/MediaBox/rotation |
| Networking M3/M4 | TCP/TLS frame v1/v2, AUTH, hostname/CA, heartbeat, bounded outbox/client/document/history, presence/cursor, OT/Lamport, ACK/ACK_BATCH, pending offline, reconnect/atomic RESYNC chunk/rebase và ROOM_UNKNOWN |
| Persistence M5 | Module MongoDB repository/service tương ứng Java, tùy chọn pymongo; prototype chưa nối UI/room, giống phạm vi Java hiện có |

Comment hiện mở Review: nhận xét gắn đoạn chọn/dòng, tô sáng đoạn, trả lời, giải quyết/mở lại, xóa có xác nhận và lọc tệp hiện tại/tổng quan. Dữ liệu lưu local trong `.overleaf-review.json`, không sửa source và chưa đồng bộ qua TCP; khác với track changes. Reference manager dùng xuất/nhập .bib, không có OAuth. GitHub import là clone project public, không phải Git branch/merge. Cộng tác vẫn **main.tex-only** và engine OT đơn giản như Java, không thay tên nó thành CRDT hoàn chỉnh.

## Cấu trúc và bàn giao

```text
apps/backend/src/latex_backend/
  main.py                 cửa sổ, native dialogs, buffer khi đóng, PDF detach/export
  api.py, parity_api.py   API local, session token, watch, action routes, upload/install
  store.py                project/catalog/path/template/file/fingerprint
  state.py                account/profile/avatar/settings/activity và Java properties
  files.py                staged upload, import/copy/HTTPS, hủy process, Java migration
  history.py              revision/preimage/checksum/label/restore/quota
  compiler.py             TeX process, timeout/Stop, PDF/index snapshot, glyph guard
  toolchain.py            diagnostics/preflight/TinyTeX install
  explainer.py             rules/context/exit explanation offline
  synctex.py               forward/inverse trên index gắn với PDF snapshot
  network.py               TCP/TLS server, protocol, OT/history, desktop replica
  console.py               console demo networking
  persistence.py           optional MongoDB M5 repositories/service
  resources/templates/     438 file asset/template độc lập Java
  resources/error-rules.json
apps/frontend/
  app/page.tsx             dashboard và workspace orchestration
  components/              Auth/Profile/Settings/History/Toolbar/AddFiles/Network/
                           ProjectMenu/ProjectCard/NewProjectExtras/PdfViewer
  lib/                     API, useParity, i18n, snippet và mapping PDF geometry
  scripts/                 worker bundle, browser regressions và geometry/snippet tests
scripts/
  run-python-desktop.bat/.sh
  run-python-server.bat/.sh
  run-python-console.bat/.sh
deploy/python/              server deploy mẫu và hướng dẫn TCP/TLS
```

Phân công tương ứng: **M1** giao diện/editor/window; **M2** compile/TeX/PDF/SyncTeX; **M3** protocol/TCP/TLS/session/deploy; **M4** OT/Lamport/ACK/RESYNC/offline/presence; **M5** account/local data/history/migration/Mongo prototype và kiểm thử. Đây là phân chia trách nhiệm để nhóm tiếp tục, không gán tác giả các thay đổi trong phiên AI cho từng người.

## Dữ liệu Java cũ

Mặc định mới: **~/OverleafDesktopPython**; Java: ~/OverleafDesktop. Có thể chọn home bằng OVERLEAF_PYTHON_HOME hoặc --home khi chạy module. Metadata Python dùng .project.json và project-locations.json, không đổi .tex/.bib/ảnh.

**Cài đặt → Dự án → Nhập dữ liệu từ bản Java**: chọn thư mục home Java. Import tạo bản sao của project ở projects/, các đường dẫn đăng ký trong .app/projects.properties và thư mục lưu tùy chọn; giữ source/history/appearance, nhập accounts PBKDF2/settings/avatar/activity. Có ledger chống nhập trùng. Thao tác nhập không sửa/xóa project Java gốc. Kiểm tra thư mục mặc định sau nhập nếu muốn tách nơi tạo project mới khỏi đường dẫn đã lưu ở Java.

## Server cộng tác

```bat
scripts\run-python-server.bat --host 127.0.0.1 --port 8080
scripts\run-python-console.bat --host 127.0.0.1 --port 8080 --room DEMO
```

Deploy ngoài máy dùng TLS PEM + OVERLEAF_TOKEN ≥24 ký tự; [deploy/python/README.md](../deploy/python/README.md) mô tả config/env/certs/Compose. API HTTP local chỉ bind loopback với token phiên; server TCP là tiến trình riêng.

Room và offline queue ở RAM như Java: chưa lưu bền vững qua restart, chưa có ACL theo tài khoản hoặc đồng bộ cả cây file. ROOM_UNKNOWN giữ queue/local để người dùng xử lý, không âm thầm bỏ thao tác. Optional MongoDB chưa trở thành database đang dùng của UI.

## Kiểm chứng

- **122 pytest đạt**: bao gồm bộ test cũ và tài khoản/profile/avatar/settings, history/quota/checksum/restore, file/upload/ZIP/Java import, protocol, TCP thật/offline/RESYNC, TLS CA/hostname, API guard và M5 qua mongomock. Một deprecation warning TestClient/httpx, không test fail.
- **186 build PDF thật đạt**, 31 × VI/EN × 3 engine; report target/python-parity-matrix-01/matrix.json. Bài script sau matrix ban đầu phát hiện fixture benchmark tràn thành 496 trang; đã sửa fixture và chạy benchmark riêng đúng 248 trang.
- **6 SyncTeX native đạt**, 3 engine × ASCII/Unicode, forward/inverse vào chapters/body.tex; guard stale đạt. **12 viewport PDF.js thật** kiểm CropBox/MediaBox offset, rotation 0/90/180/270 và 3 mức zoom.
- **Pandoc Markdown/DOCX và HTTPS import thật đạt** ở target/python-parity-imports-01. ZIP/upload/migration được kiểm bằng fixture cô lập; GitHub validation/process cancellation có trong backend, chưa clone mọi loại repo công khai.
- **Python client → Java server thật đạt**: AUTH, UTF-16, ACK, presence, offline/rebase/RESYNC; target/python-java-interop-01/java-server.log.
- **UI Edge headless đạt**: account/register/remember/profile, toolbar, history label/diff, upload/nested files, outline divider, layout/F5/Auto Compile, settings/English/activity; lượt bổ sung kiểm đổi tên buffer chưa lưu, system dark và đúng template example; target/python-parity-features-05/features.png (Edge) và target/python-parity-features-10/features.png (Chromium). Luồng PDF 3 trang/link/export byte-identical/stale/error đạt ở target/python-parity-ui-03.
- **248 trang**: compile cold 1.766 ms, warm 594 ms trên fixture/máy này; preview đầu gồm recompile/render ~1.619 ms trong lần chạy song song, tối đa 3 canvas/~4 MB pixels. Reports target/python-parity-large-01/benchmark.json và viewer-benchmark.json. Đây là tài liệu tổng hợp nhỏ, không suy rộng sang luận văn nhiều ảnh/TikZ hoặc tốc độ so với Overleaf web.
- **Cửa sổ native WebView2 mở/đóng thật đạt**; buffer khi đóng kiểm riêng và không gọi evaluate_js trong closing callback (tránh deadlock). Native export dialog được unit mock kiểm bytes/đổi nguồn trong lúc dialog; chưa tự bấm mọi dialog OS.
- Static build/TypeScript, Ruff F/E9, snippet và dependency audit đạt; PDF.js đã nâng 6.2.108. Không lấy JUnit/benchmark Java làm kết quả Python.
- Máy nghiệm thu hiện tại là Windows. Launcher source Mac/Linux có nhưng **chưa kiểm native trên Mac/Linux**, chưa build/sign/notarize installer; Compose là mẫu chưa chạy Docker. MongoDB test dùng mock, chưa kiểm Atlas thật.

Lệnh test:

```sh
python -m pytest apps/backend/tests -q
cd apps/frontend
npm run typecheck
node scripts/test-snippets.mjs
node scripts/test-workspace.mjs <venv-python-absolute> <fresh-test-home>
node scripts/test-features.mjs <venv-python-absolute> <fresh-test-home>
```

Native helpers trong apps/backend/tests: smoke_compile.py, smoke_cancel.py, smoke_parity.py (--matrix), smoke_imports.py, smoke_java_interop.py. Các helper dùng home test mới, không dùng dữ liệu thật. Xem tên script trước khi chạy: cần TeX, Pandoc, OpenSSL/Git hoặc Java server classes tương ứng.

Nguồn tích hợp: [Next.js static export](https://nextjs.org/docs/app/guides/static-exports), [pywebview](https://pywebview.flowrl.com/), [FastAPI](https://fastapi.tiangolo.com/), [PDF.js](https://mozilla.github.io/pdf.js/). Bản wheel backend cũng đã build và kiểm đủ 438 template asset/28 error rules; đây chưa phải installer có UI/runtime. Source Java được giữ để đối chiếu; bản mới không gọi JVM để thực hiện chức năng.


Test UI bổ sung hỗ trợ biến OVERLEAF_TEST_BROWSER=chromium trong scripts/test-features.mjs; cần cài browser bằng npx playwright install chromium. Hai lần Edge bổ sung bị kẹt trước khi đăng nhập; đã dọn đúng process thử nghiệm, chạy lại Chromium đạt. Kết quả Edge trước đó vẫn được giữ, không coi lần khởi động bị kẹt là test đạt.

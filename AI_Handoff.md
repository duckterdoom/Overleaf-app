# AI Handoff — Overleaf Desktop / Networking Backend M3

## 87. Google OAuth dev: import riêng một lần, tự nhận ở các clone sau (11/11/2026)

User muốn dev clone về test Google mà không phải nhập lại credential bằng `$env:...`. Thêm `auth/google_config.py`: đọc cặp biến môi trường đầy đủ trước; nếu không có thì file chỉ định LATEX_GOOGLE_CONFIG, file repo `config/google.local.json`, rồi file theo tài khoản máy. Không ghép ID/secret từ nguồn khác nhau. File có thể là JSON Web OAuth tải từ Google Cloud (`web.client_id/client_secret`) hoặc JSON dạng phẳng. Blank template không chặn fallback user file. Desktop/installed client bị từ chối. Public URL có thể để trống để dùng host/port hiện tại; Google vẫn cần đăng ký đúng callback một lần, cấu hình app không tự chỉnh Google Console.

`scripts/setup-google-auth.bat PRIVATE_JSON` import **một lần cho tài khoản Windows** vào `%APPDATA%/Latex/google.local.json`; mọi clone sau tự nhận. macOS dùng Library/Application Support/Latex; Linux XDG_CONFIG_HOME hoặc ~/.config/latex. `--project` lưu riêng trong clone, `--replace` mới được ghi đè config đã có, `--check` chỉ báo tình trạng/source/base không in credential. Người quản lý đã có biến Google trong terminal dùng `--from-env --project` để tạo file private cấp cho nhóm, không cần nhập lại secret. CLI chỉ dùng stdlib và PYTHONPATH vào backend source, không cần cài dependency ứng dụng/MongoDB trước khi import. Script nhận fix dò Python chung ở mục 86.

`WebAccounts.google_config()` và callback_url dùng loader mới; auth config API chỉ trả boolean, không lộ file/ID/secret. Validate file/schema/size <=64 KiB, URL HTTPS/HTTP loopback không có userinfo/query/fragment. Lỗi được báo đã sanitize, không echo JSON hoặc parser exception. Khi lưu dùng file tạm, fsync + replace; mode 0600 trên POSIX, Windows dùng quyền user profile kế thừa. Credential field bị loại khỏi dataclass repr. Không commit/file mẫu chứa credential thật; gitignore config/google.local.json và client_secret*.json. Không copy/ghi cấu hình Google thật, không thay đổi MongoDB và chưa xác thực Google bằng tài khoản thật trong lần sửa này.

README, docs/WEB_AUTH_ONBOARDING.md và config/google.local.example.json ghi cách cấp file qua kênh riêng. Máy hoàn toàn mới vẫn cần nhận credential riêng một lần; Git clone không tự mang secret. Không hứa zero config trên máy chưa được cấp file và không hardcode credential của nhóm. Thay đổi hiện local, chưa push remote.

Kiểm chứng: **208 tests backend và 8 tests scripts Windows đạt**, gồm file/env precedence, JSON Google chuẩn, fallback qua user file khi đổi clone, auto callback port, không trộn credential, invalid schema/JSON/URL/size không lộ secret, import/check/from-env và không ghi đè ngầm. TestClient OAuth tạo tài khoản/onboarding bằng file config không cần env, Google exchange mô phỏng. Fixture toàn bộ unit tests cách ly file/env Google thật của dev. Smoke Windows `target/google-private-config-smoke-20261111/report.json` đạt import + check, dùng credential giả trong user profile tạm và xác nhận không in secret. Bộ scripts Windows bổ sung case clone mới/venv mới chỉ có source module stdlib, import Google config được trước khi cài dependency.

## 86. Windows clone mới: dò Python và bootstrap không phụ thuộc py launcher (11/11/2026)

User báo máy clone có `python` 3.13 nhưng `py` không tìm runtime; launcher dừng ở `Python setup failed: File "<string>", line 1`. Đã tái hiện trực tiếp probe cũ `py -3 -c '... else ""'` trên Windows PowerShell 5.1: dấu ngoặc kép truyền sai gây `SyntaxError: unterminated string literal`, không phải bằng chứng thiếu Python. Ngoài ra native stderr khi py hỏng có thể làm `$ErrorActionPreference=Stop` thoát trước fallback.

Thêm `scripts/python-runtime.ps1`, chung cho ensure-python/setup-installed-python-web: probe không có quote lồng; kiểm tra version >=3.12 và lấy sys.executable; ưu tiên Python đã cài qua python → python3 → py -3; candidate hỏng thông thường thì ghi chẩn đoán và thử tiếp. `LATEX_PYTHON` cho phép chỉ định executable đã cài khi cần tạo môi trường mới. Không tải interpreter khác chỉ vì py hỏng. Chỉ khi không tìm thấy installed Python mới fallback uv; .venv cần phục hồi được giữ thành backup, không xóa. Application Control vẫn dừng với thông báo riêng; không thay đổi/bỏ qua chính sách.

`run-python-web.bat` dùng extra web-local (không bắt buộc httptools/PyYAML/reload tooling). Với uv, bắt buộc truyền `--python "%WEB_PYTHON%"`: lượt smoke phát hiện nếu thiếu tham số, `.python-version=3.12` khiến uv tự thay môi trường 3.15 vừa tạo sang 3.12. Sau sửa giữ đúng interpreter đã được kiểm tra. Script server TCP và console cũng gọi ensure-python và tự cài backend khi thiếu, không chỉ kiểm tra file python.exe; đây vẫn là launcher legacy, web dùng run-python-web.bat. Desktop hiện đã dùng ensure-python nên nhận fix chung.

README và docs/WINDOWS_PYTHON_STARTUP.md cập nhật hướng dẫn clone Windows, build rồi chạy, chọn executable, không copy venv/target/node_modules, cấu hình MongoDB riêng và yêu cầu TeX. File URI thật không đổi/không được sao chép vào test.

Kiểm chứng bootstrap: `scripts/tests/test_windows_python_setup.py` đạt **7 tests**, chạy Windows PowerShell thật, PATH shims và venv thật trong thư mục có dấu cách: Python dùng được khi py/uv hỏng; fallback python3/py; giữ/reuse/backup môi trường; thiếu runtime báo rõ; explicit executable; Application Control dừng đúng; batch truyền chính xác interpreter cho uv và build calls. Dùng CALL cho uv để hỗ trợ cả executable và shim .cmd mà vẫn quay lại batch chạy build, đã kiểm bằng shim trong test. Smoke tạo bản sao backend sạch, không reuse .venv: `target/clone bootstrap final 20261111/report.json` đạt exitCode=0, Python=3.15.2 đúng bản chọn, pipCheck=0; log ghi cài 41 package và chạy CLI web --help thành công; kiểm tra lại launcher cuối sau sửa CALL cũng đạt. Bản sao đầy đủ khác không có node_modules/.next/out lúc bắt đầu, chạy npm ci + Next.js/TypeScript build đạt: `target/clone startup smoke 20261111/report.json` exitCode=0/frontendBuilt=true/venvCreated=true; chính lượt này phát hiện uv chọn lại 3.12 trước khi thêm --python, đã được khắc phục và xác nhận bằng smoke backend cuối. Chưa có Python 3.13 riêng để nghiệm thu runtime 3.13 trên máy này; probe tương thích >=3.12, test thực dùng Python 3.15.2. Không đụng database/tài khoản/dự án thật và chưa push thay đổi lên remote.

## 85. Word phân trang đồng bộ chữ về LaTeX và PDF (11/11/2026)

Nguyên nhân: `WordDocxPreview.persist()` chỉ PUT DOCX, còn `DocxWorkspace.save()` chỉ commit bản Word độc lập; không cập nhật nguồn hoặc gọi onSynced. Đã nối pipeline Word native → kiểm tra baseline/fingerprint/revision → ánh xạ thay đổi chữ về nguồn → lưu DOCX → OT broadcast → editor → compile PDF. Bộ chuyển đổi PDF→DOCX gốc và tài nguyên ảnh không thay đổi.

Module mới `documents/docx_sync.py` đọc w:p/w:t từ DOCX, chuẩn hóa khoảng trắng/ligature và diff theo token; tìm đoạn nguồn duy nhất bằng ngữ cảnh, sửa từ cuối lên đầu và escape ký tự đặc biệt. Chỉ quét root đang compile cùng các tệp input/include, không áp sửa sang root khác. Bỏ qua comment, công thức/math environment, literal blocks và đối số tham chiếu/ảnh/package; không đoán vị trí trùng hoặc thay đổi vượt qua lệnh LaTeX. Đồng bộ **chữ**, không phải chuyển đổi mọi định dạng Word thành LaTeX; thay đổi bố cục/định dạng Word vẫn riêng trong DOCX. Nếu không ánh xạ được, trả 409, giữ nguồn, giữ draft tại `.overleaf-docx[-root-hash]/conflict.docx`, trình duyệt giữ bản chưa lưu và có nút tải Word đang sửa. Revision/baseline cũ hoặc LaTeX đã đổi thì chặn ghi đè. Bản Word độc lập của phiên bản cũ có `edited=true` nhưng thiếu `text_baseline` cần tải giữ bản rồi tạo lại từ PDF trước khi dùng sync; không được báo đã đồng bộ cả bản cũ.

`LiveDocuments.external_edit()` serialize với live source updates qua edit_lock, commit trước rồi phát ChangeSet UTF-16 cho từng file, giữ epoch/version để các client rebase phần gõ đang chờ. Native DOCX và chế độ Word HTML cũ đều qua authority. API chạy thao tác Word trong worker thread, không chặn event loop bằng Mongo/filesystem I/O. Source fingerprint sau sửa được ghi vào Word metadata; PDF cũ bị verified chặn cho tới khi compile thành công nguồn mới. Phạm vi vẫn single API worker như kiến trúc OT hiện tại.

Frontend gọi onSynced sau lưu chữ, dùng OT khi kết nối web thay vì ghi lại toàn bộ source qua React. Compile chờ flush Word trước khi lưu/biên dịch. Auto compile bật sẽ compile sau khi đồng bộ; tắt thì người dùng nhấn Compile. Hiển thị “Đã đồng bộ LaTeX” riêng với “Đã lưu Word”, có ghi rõ giới hạn format/math/layout. Không lặp autosave vô hạn cùng một bản sửa lỗi; người dùng vẫn có thể tải draft hoặc thử lưu lại.

Kiểm chứng: production build/TypeScript đạt; bộ backend đầy đủ 190 tests đạt trên bản cuối, cùng các nhóm Word/OT đạt. `test_docx_sync.py` kiểm tra thay/xóa chữ, escape &, giữ lệnh/ảnh/math, input/root phụ, từ chối mơ hồ và stale, giữ draft lỗi, baseline phiên bản cũ, OT giữ epoch/UTF-16, shared editor cập nhật nguồn owner và cả hai socket. Browser `test-local-web.mjs` bổ sung sửa Word → bấm Compile ngay trước debounce → đối chiếu editor/nguồn thật và trích chữ từ PDF thật; thêm auto-save + Auto compile rồi kiểm tra PDF, export DOCX giữ bytes chỉnh sửa. Đạt ở `target/native-word-sync-20261111-final`. Cùng kịch bản đạt Atlas thật trên namespace tách riêng `target/presence-atlas-f0233da398b80d1a/atlas-report.json` (`passed=true`, `cleaned=true`); không sửa tài khoản/dự án thật của user. Sau cập nhật cần restart backend bằng terminal hiện có rồi Ctrl+F5; không chỉ refresh frontend.

## 84. Outline dạng cây theo cấp tiêu đề LaTeX (11/11/2026)

Đã thay danh sách outline phẳng bằng `components/DocumentOutline.tsx`, giao diện bám ảnh user: chevron thu/mở nhánh, cấp con thụt vào với đường dọc, mục đang đọc nền xanh/chữ trắng, tên dài ellipsis và tooltip đầy đủ. Có thể thu/mở toàn bộ panel bằng header Cấu trúc tài liệu / File outline. Giữ divider kéo chia chiều cao file tree/outline và thanh đổi độ rộng sidebar hiện có.

Parser mới `lib/outline.ts` nhận part/chapter/section/subsection/subsubsection/paragraph/subparagraph, gồm heading dấu *, short title và ngoặc nhọn lồng; bỏ comment và block verbatim/lstlisting/minted. Cây dùng cấp gần nhất có level thấp hơn, không tạo cha giả khi thiếu cấp. ID theo đường tiêu đề/loại/occurrence (encode title), không dùng offset để giữ collapse khi thêm chữ trước heading. Offset UTF-16 trỏ đúng command cho CodeMirror. Đây là parser outline theo lệnh có trong file đang mở, không phải bộ thực thi macro TeX đầy đủ.

Click tên nhảy/focus editor tới đúng heading; click chevron chỉ thu/mở. Theo dõi active heading bằng cursor sink ref, cập nhật state trong component riêng khi đổi mục, không bắt parent page rerender mỗi lần rê caret. Component memo hóa source/editor/language; không reconfigure CodeMirror. Hỗ trợ ARIA tree/group/treeitem và bàn phím Up/Down/Left/Right/Home/End/Enter/Space; focus chuyển đồng bộ với node đã có để bấm Enter nhanh vẫn đúng mục. Root cây giới hạn min-width/overflow để tên dài không đẩy tràn sidebar.

Kiểm chứng: TypeScript/production build và diff check đạt. `node --experimental-strip-types scripts/test-outline-parser.mjs` đạt hierarchy, title lồng, duplicate IDs, comments/literal, offset và missing level. Node có warning module type detection cho file TS, không phải lỗi app. Browser Edge `target/outline-tree-20261111-final/report.json` đạt tree 3 cấp, collapse/expand, keyboard, nhảy source sau khi offset đổi, active selection, giữ collapse khi gõ, toggle panel và resize divider; đã xem ảnh `outline-tree.png` và `outline-tree-dark.png`. Regression kéo bôi cục bộ + caret-only + comment shared vẫn đạt `target/outline-selection-regression-20261111`.

Không đổi API/backend, không sửa dự án/tài khoản thật và không commit/push. UI đã build sẵn; Ctrl+F5 trên trang đang mở để nhận bản mới. Không cần migrate dữ liệu hoặc restart backend chỉ cho thay đổi UI này.

## 83. Vùng bôi của chính mình phải nhìn thấy; bỏ vùng bôi remote (11/11/2026)

User xác nhận selection đã tác dụng nhưng chính người kéo không thấy màu bôi, trong khi tài khoản kia thấy vùng bôi remote; yêu cầu bỏ remote range. Đã tái hiện và chụp ảnh trước sửa: đúng range BRAVO nhưng không có nền selection nhìn thấy. Nguyên nhân CSS cm-activeLine có background đục/important nằm trên selection layer CodeMirror; remote mark nằm trong lớp chữ nên lại thấy. Mục 82 sửa state/reselect nhưng chưa kiểm pixel hiển thị nên lọt lỗi này.

- `page.tsx`: extension editorAttributes.compute theo selection đặt data-local-selection cục bộ, không phụ thuộc socket hay tài khoản khác.
- CSS: khi có local range, active-line background transparent; selection layer có màu rõ cho light/dark/focus/blur. Giữ xử lý chọn chữ/multi-range gốc của CodeMirror. Bỏ CSS remote-selection.
- `usePresence.ts`: chỉ vẽ remote caret/name, không tạo Decoration.mark remote range; gửi anchor=head. `presence.py` cũng thu gọn anchor=head với mọi client (kể cả client cũ), giữ identity/ACL/cursor và live text sync. Comment và highlight quote đã lưu vẫn chia sẻ như mục 82; đó không phải vùng kéo chọn live.

Kiểm chứng bằng **ảnh render thực**, không chỉ state: `target/local-selection-before-20261111/local-selection-line.png` so với `target/local-selection-visible-20261111-01/local-selection-line.png`, pixel xanh theo predicate của test từ 2 lên 390. Đã xem ảnh trước/sau. Browser test thêm assert local attribute, remote-selection count=0 và pixel check bằng Pillow; backend test xác nhận range gửi lên bị thu gọn. Production build/TypeScript, **182 backend tests** và diff check đạt.

Browser shared-review selection và caret/live-sync regression local đạt. Atlas thật `target/presence-atlas-74febb64b5457bc4/atlas-report.json` passed=true/cleaned=true, test drag/replace/delete và comment/reply/resolve/delete/viewer vẫn đạt. Dữ liệu test đã dọn, không đổi project/account thật. UI đã build; cần restart backend và Ctrl+F5 cả hai browser. Không commit/push hoặc tự dừng server thật.

## 82. Sửa kéo chọn chữ và đồng bộ nhận xét giữa tài khoản (11/11/2026)

User báo kéo bôi một đoạn khó, vùng chọn bị thành cả dòng/ảnh hưởng xóa và comment; editor khác tạo comment nhưng owner không thấy. Đã xác định `ReviewPanel` luôn mounted dù bị hidden, effect điều hướng phụ thuộc hàm translator mới ở mỗi render nên tự chọn lại quote cũ khi presence/poll/typing làm giao diện render. Panel cũng chỉ fetch review một lần theo route nên không nhận thay đổi mới.

- `ReviewPanel.tsx`: memo translator; click vị trí thread là yêu cầu điều hướng **một lần**, tiêu thụ active rồi không chọn lại khi rerender. Giữ draft/reply khi refresh; chặn GET cũ ghi đè POST mới qua mutation generation. Mở panel/focus/reconnect sẽ tải review mới; không poll panel ẩn, nhưng notification vẫn refresh để cập nhật highlights. Panel mở fallback 5 giây editor / 1,5 giây viewer. Sửa dòng mô tả cũ “lưu local/chưa sync” cho web. Viewer read-only không có composer/reply và không thực hiện resolve/delete.
- `page.tsx`: memo extensions/basicSetup và callback CodeMirror qua handler ref. Presence/comment render không còn reconfigure mouse selection liên tục. Vẫn lấy quote từ selection thật để thêm nhận xét; callback dùng state mới nhất và giữ OT/legacy cursor.
- Backend `parity_api.update_review`: update qua thread giữ ContextVar principal và khóa Review; sau persist mới gọi notification. `WebAccounts.workspace` gắn callback vào tenant app; `Presence.review_changed` chỉ phát invalidation project qua queue, không phát comment text không qua ACL. `usePresence` nhận event và phát sự kiện theo project; reconnect cũng refresh review. GET/POST vẫn giữ quyền owner/editor/viewer hiện tại.

Kiểm chứng: **182 backend tests passed**, production Next.js build/TypeScript và diff check đạt. `test_shared_review.py` kiểm tên thật, owner/editor thấy chung, notification, reply và viewer không ghi được. Browser mới `test-shared-review-selection.mjs` dùng Edge/Chrome thật, click thread cũ rồi kéo một phần từ trong lúc có remote OT update, thay/xóa đúng đoạn, comment đúng quote DELTA, reply/resolve/delete ở máy còn lại, viewer thấy comment mới sau khi panel đã mở và bị chặn ghi. Local đạt `target/shared-review-selection-20261111-01`; Atlas thật đạt `target/presence-atlas-a0f7491b9e3e52c7/atlas-report.json` passed/cleaned=true. Đã xem screenshot `shared-comment-reply.png`; dữ liệu test tách namespace/guard và đã dọn, không đổi project/account/comment thật.

Regression sync gõ liên tục/offline/undo đạt `target/review-fix-live-regression-20261111`. Regression PDF 2 trang/export đúng byte, Word edit/export, upload/avatar và ZIP compile đạt `target/review-fix-documents-20261111-02`. Có điều chỉnh test-local-web dùng Ctrl+S + chờ disabled thay vì click nút Save đang bị disabled đúng do OT đã lưu quá nhanh; đây là sửa race của test, không đổi cơ chế lưu. Test so source DOM phải bỏ nhãn remote cursor để không nhầm tên người dùng là source text.

Không cần migrate hoặc tạo lại comment đã lưu; mở lại panel sẽ đọc dữ liệu chung. UI đã build. User lưu bài → Ctrl+C server cũ trong terminal có Google env → `scripts\run-python-web.bat` → Ctrl+F5 cả Edge/Chrome. Không tự kill/restart server thật, không commit/push. Vẫn giới hạn một web worker như OT hiện tại.

## 81. Tối ưu compile và PDF → Word, chuẩn bị điều phối job cho server (11/11/2026)

Theo yêu cầu tăng tốc compile/convert khi sau này lên server, đã đọc pipeline thật và đối chiếu latexmk recorder/pdf2docx docs. Không sửa bundle converter gốc, không đổi engine/chế độ layout hoặc bật multiprocessing ép vào universal engine.

- `compilation/compiler.py`: dùng lại snapshot PDF/SyncTeX/geometry/log khi source fingerprint, engine, executable và input recorder .fls không đổi; yêu cầu có recorder, không marker lỗi/dirty. Nguồn/dependency sửa/xóa, clean, đổi engine hoặc force bỏ qua fast cache. Replay log để giữ cảnh báo. `CompileBody.force` gọi latexmk -g. Giữ aux/fdb cho lượt incremental có sửa. Gộp các lookup/fingerprint trùng; ghi activity qua thread.
- `pdf_export/service.py`: cache Word theo SHA256 PDF bytes + fingerprint bundle/worker/phiên bản thư viện, không phụ thuộc snapshot ID. RAM cache và cache riêng từng project `.overleaf-pdf-word-cache` có checksum/ZIP validation; giữ tối đa 4 file và 256 MiB mỗi project. Hỏng cache tự convert lại; không ghi đè Word draft đã edit. Gộp request trùng bằng lock có timeout; recheck verified PDF trước/sau conversion và cache hit. Không tạo worker khi có cache hợp lệ. Converter cập nhật cần restart backend để refresh signature.
- `core/work_limits.py`: giới hạn toàn process mặc định 2 compile và 1 Word, cấu hình LATEX_MAX_COMPILE_JOBS/LATEX_MAX_WORD_JOBS (1–16). Compile đợi slot hủy được; Word đợi slot có timeout, releases slot khi lỗi/timeout. Vẫn một active compile/account và trạng thái RAM một API worker; không tự scale đa worker/Redis.
- Tests mới `test_build_performance.py`: source/package đổi/xóa, force/clean invalidation, log replay, duplicate Word chỉ chạy worker một lần, cache disk khi snapshot ID mới, corrupt DOCX/metadata và slot cancellation. Sửa fixture ZIP trong `test_multidocument.py` dùng timestamp cố định để tránh so byte flake khi qua mốc 2 giây ZIP.

Benchmark cuối dùng TeX/converter thật, fixture **4 trang** text/math/table/PNG, Store local không có RTT Atlas: compile đầu 572ms; chạy latexmk no-op như đường cũ 234ms; unchanged snapshot cache `[8,8,8]`ms; có sửa nguồn incremental 365ms. Word cold 626ms, RAM cache 3ms, disk cache exporter mới 4ms; DOCX cache giống byte và stale export bị chặn. Report `target/build-word-performance-20261111-final/benchmark.json`. Đây là cold/hit trên một fixture, không khẳng định cold compile/convert tài liệu lớn đã nhanh hơn hoặc tốc độ server tương đương.

Kiểm chứng: toàn bộ **181 backend tests** đạt ở lượt tổng, sau đó chạy lại tests compile/Word cho các thay đổi nhỏ cuối; browser regression `target/build-word-browser-20261111` đạt lưu source, PDF 2 trang/export byte-identical, PDF tab, Word edit/export, uploads/avatar, ZIP import/compile. Tests manifest xác nhận 11 file converter gốc/figures giữ hash đúng. Hướng dẫn mới `docs/COMPILE_WORD_PERFORMANCE.md`, README có link.

Chưa triển khai public server hoặc sửa deploy/python/compose.yaml (có nội dung user riêng). Launcher/host guard vẫn loopback. Khi lên Internet cần worker compile/convert cách ly CPU/RAM/filesystem/secrets và queue/artifact chung nếu scale; không chỉ tăng Uvicorn --workers vì OT/snapshot còn RAM. Không commit/push hoặc dừng server thật. Để dùng backend mới: user lưu bài, Ctrl+C server cũ, chạy `scripts\run-python-web.bat`; không cần build frontend vì lần này không đổi UI.

## 80. Kiểm tra lại sau khi tiếp tục phiên làm việc (11/11/2026)

User yêu cầu rà soát kỹ sau khi tạm dừng hôm trước. Đã kiểm lại file/code, startup script và trạng thái process; không reset Git, không sửa account/ACL/source dự án thật và không dừng server của user.

- **Toàn bộ backend: 178 tests passed** (33,17 giây); chỉ có deprecation warnings thư viện. TypeScript, production Next.js build và git diff --check đều đạt.
- Browser Edge–Chrome tách process: `target/resume-audit-20261111-presence` đạt lại cursor/avatar, xóa chữ, sửa đồng thời, undo/redo, offline pending, viewer, reload/disconnect. Bổ sung regression gõ liên tục từng phím (khác insertText một transaction) vào `test-presence.mjs`; lượt `target/resume-audit-20261111-burst` đạt, 128 ký tự burst hội tụ giữa hai editor.
- Regression tài liệu `target/resume-audit-20261111-documents` đạt bootstrap/reload, lưu dự án, PDF thật 2 trang và export giống byte, PDF tab, Word edit/export, upload/binary preview/avatar, ZIP import/compile.
- Đo Atlas thật lại bằng 1 warmup + 8 mẫu Edge–Chrome: **median 396 ms, P95 thực nghiệm/max 446 ms**, samples `[436,368,446,340,423,354,445,340]`. Report `target/presence-atlas-42f15ec88a422dc8/speed.json`; Atlas report passed/cleaned=true, collections test ngẫu nhiên có guard đã dọn. Số liệu mẫu nhỏ, không phải SLA hoặc benchmark nhiều người/tài liệu lớn. Không dùng số cũ 387 ms như thể là kết quả đo lần này.

Phát hiện vận hành: **cổng 8000 đang có Python PID 71840**, process khởi động trước lần cập nhật `collaboration/live_documents.py` (đã đối chiếu LastWriteTime > StartTime). Python launcher không hot reload, nên server đang giữ code được import trước lần cập nhật. Không kill/restart từ tool vì user có thể còn bài chưa lưu và Google env ở terminal riêng. Để áp dụng: lưu bài → Ctrl+C trong terminal đang chạy server → `scripts\run-python-web.bat` → Ctrl+F5 cả Edge/Chrome. UI đã được build lại trong lượt này; --build chỉ build rồi thoát nếu user tự dùng. Qua các kiểm thử trên chưa phát hiện lỗi mới cần đổi implementation; thay đổi lần này chỉ mở rộng regression và ghi handoff.

## 79. Tăng tốc đồng bộ: đo trước/sau với Edge–Chrome và Atlas (11/11/2026)

Đã tối ưu theo yêu cầu sync chậm, giữ source nguyên tử + fsync, metadata/activity MongoDB trước ack và vẫn kiểm phiên/quyền mỗi push.

- `liveSync.ts`: thay trailing debounce 60ms bằng batch 16ms không bị dời vô hạn khi gõ liên tục; dọn timer đúng khi disconnect/reconnect.
- `useParity.ts`: khi OT đang hoạt động, bỏ HTTP editing/checkpoint trùng cho từng lần gõ; không GET toàn bộ source mà OT đã quản lý. Poll cây file/PDF mỗi 3 giây cho live editor, viewer vẫn 750ms. Nội dung vẫn truyền bằng WebSocket, không chờ poll.
- `presence.py` / `web_accounts.py`: dùng workspace đã gắn với socket và kiểm session/ACL/project trên từng push, tránh định tuyến lại workspace owner liên tục. Các MongoDB lookup của HTTP gateway chạy bằng thread, không chặn event loop WebSocket.
- `MongoStore.save_live`: chỉ ghi source, cập nhật metadata.updated và tăng activity; bỏ đọc lại toàn bộ lịch sử/settings cho mỗi phím. `LiveDocuments` gom history tối đa một checkpoint mới nhất mỗi tác giả/file trong khoảng 2 giây, giữ thứ tự cập nhật, flush khi rời và dừng server bình thường. Source đã ack không phụ thuộc checkpoint lịch sử đang chờ; hard-kill có thể mất checkpoint chưa ghi, không mất source đã xác nhận. Lỗi/quota history có cảnh báo sạch, không in URI.
- Persistence chạy trong thread dưới khóa authority async tuần tự và kiểm lại source dưới store.lock để không đè thay đổi HTTP/external. Nếu socket ngắt khi đang ghi, hoàn tất commit/version/broadcast trước cleanup; test cancellation mới kiểm tra trường hợp này.

Đo **cùng máy, localhost + Atlas, hai browser process Edge/Chrome**, 1 warmup và 8 mẫu luân phiên hai chiều, từ thao tác chèn đến DOM phía kia hiển thị:

| Chỉ số | Trước | Sau |
| --- | --- | --- |
| Median | 1.740 ms | 387 ms |
| P95 thực nghiệm (8 mẫu, tức mẫu lớn nhất) | 2.337 ms | 718 ms |

Median giảm khoảng 78%, tương đương 4,5 lần nhanh hơn trong lượt đo; đây là mẫu nhỏ trên môi trường hiện tại, không phải SLA hoặc benchmark tải nhiều người. Dữ liệu gốc: `target/presence-atlas-0653258d7f5b7585/speed.json` và `target/presence-atlas-c198eb14fa9f0648/speed.json`. Report Atlas cuối passed/cleaned=true; collections test có prefix/guard đã dọn, không đổi account/dự án thật. Benchmark mới `apps/frontend/scripts/test-sync-speed.mjs`.

Kiểm chứng: production build/TypeScript và diff check đạt; **26 backend tests** auth/sharing/Mongo/presence/live đạt; Edge–Chrome regression `target/sync-speed-regression-final` đạt cursor/avatar, xóa chữ, sửa đồng thời, undo/redo riêng người, offline pending + remote edits, viewer, reload và disconnect. Chưa benchmark nhiều người hoặc file lớn, vẫn một worker. UI đã build; user lưu bài, Ctrl+C server cũ, chạy `scripts\run-python-web.bat`, rồi Ctrl+F5 cả hai browser. --build vẫn chỉ build rồi thoát.

## 78. Đồng bộ nội dung source trực tiếp bằng CodeMirror OT/WebSocket (10/11/2026)

User thử xóa chữ n ở tài khoản editor nhưng owner không đổi. Nguyên nhân là các mục 76–77 chỉ có presence/cursor; nội dung vẫn phụ thuộc lưu/polling. Mục này **thay thế giới hạn chưa OT/realtime** của các mục cũ.

Đã thêm `@codemirror/collab` và lockfile frontend. `lib/liveSync.ts` quản lý Compartment theo project/file/view/account, sendableUpdates/receiveUpdates, rebase thao tác local chưa xác nhận, undo/redo và chờ ack. `usePresence.ts` dùng cùng WebSocket cho sync-open/sync-push/sync-snapshot/sync-updates; heartbeat/cursor vẫn có. `page.tsx` đưa collab vào CodeMirror; nút Lưu/compile web chờ flush live, không PUT toàn file ghi đè. Callback cập nhật savedText/dirty/stale và không phát lại remote change thành local edit. `useParity.ts` không nạp toàn bộ source qua polling khi live authority đang quản lý editor.

Backend `collaboration/live_documents.py`: authority version/epoch một worker, áp dụng ChangeSet JSON theo UTF-16 giống JavaScript (có tiếng Việt/emoji/multiline). Kiểm session/ACL mỗi push; clientID bị ràng buộc với user đăng nhập, history lấy tác giả từ server. Bản version cũ được trả các updates đã chấp nhận để CodeMirror rebase và thử lại, không overwrite bản mới. Lưu source + metadata/history trước khi broadcast/ack. Queue document đáng tin cậy 128 phần tử; presence không được xóa document message. Overflow ngắt để reconnect/catch up. Cache log giới hạn 4.096 thao tác/8 MiB mỗi file; giải phóng file không còn editor theo dõi. Bỏ query root lặp trong cùng edit sau khi gateway đã kiểm project.

Kiểm chứng: 25 tests auth/sharing/Mongo/presence/live đạt; test mới `test_live_documents.py` kiểm xóa n, persistence, broadcast đúng client, stale push không overwrite, retry version mới, reopen và Unicode/malformed ChangeSet. Browser Edge+Chrome riêng đạt xóa chữ không nhấn Lưu, hai bên chèn đồng thời hội tụ, file switch, cursor/avatar, viewer, reload, undo/redo. Production build + TypeScript đạt. Regression tài liệu tại `target/live-sync-documents-01` đạt PDF 2 trang/export đúng byte, Word edit/export, upload/ảnh/avatar và ZIP compile. Kiểm live Atlas tách collections có guard tại `target/presence-atlas-75f8e6fdf10b0fb8` passed/cleaned=true; dữ liệu test đã dọn, không sửa hai account hay dự án thật. Test so sánh source DOM phải loại bỏ nhãn remote cursor, tránh so tên hai người khác nhau như thể là nội dung source.

Lượt cuối sau tối ưu/context và bảo vệ chuyển room: `target/live-sync-offline-final` đạt cả pending khi ngắt socket/khôi phục mạng trong lúc phía kia sửa, undo/redo và convergence source (không tính nhãn cursor). Atlas thật lần cuối `target/presence-atlas-6dfc8092ff3852b7/atlas-report.json` passed=true, cleaned=true; đã dọn collections test. Đã xem screenshot `live-edits-synchronized.png`. Frontend production build và diff check đạt.

Giới hạn: source tự lưu khi live đang kết nối dù tùy chọn autoSave cũ tắt; không cần bấm Lưu để truyền chữ. Viewer vẫn xem bản đã lưu bằng polling, không quảng bá presence. OT chưa áp dụng lên Word/DOCX/review/chat/cây file. Chưa nhiều worker/server: authority và replay log trong RAM, nội dung đã ack trên volume + metadata Mongo. Reconnect cùng epoch giữ/rebase pending; nếu server restart/ngoại lực đổi source hoặc không còn replay window để rebase an toàn thì giữ bản local và báo để sao chép/đối chiếu, không tự ghi đè. Chưa có offline queue bền qua reload/đóng browser.

UI đã build; user cần lưu bài, Ctrl+C server cũ, chạy `scripts\run-python-web.bat` trong terminal giữ Google env rồi Ctrl+F5 cả Edge/Chrome. Nếu tự build lại: --build chỉ build rồi thoát, chạy thêm lần không --build. Không tự dừng/restart process thật từ tool.

## 77. Sửa presence Edge/Chrome trên localhost (10/11/2026)

User xác nhận cùng URL/cổng/dự án nhưng không thấy con trỏ, có lúc nhóm cộng tác chỉ hiện chính mình. Đọc Atlas xác nhận cùng shared project, quyền owner/editor hợp lệ; không thay account/session/ACL thật.

- Backend `presence.py`: lấy room và thêm peer **sau await ws.accept()**. Code cũ lấy room trước handshake; peer cuối rời trong lúc accept chờ có thể xóa room, khiến peer mới nằm trong dict mồ côi. Test ASGI mới tái hiện chính xác race này. Publish snapshot copy từng member. Cache path đã validate để tránh query MongoDB mỗi lần di chuyển cursor.
- Giữ cursor người khác khi chuyển cửa sổ: bỏ điều kiện peer.active khỏi bộ lọc vẽ. Focus vẫn là thông tin trạng thái. Bỏ timeout ứng dụng 45 giây khi chờ message, vì timer JS bị throttled trong tab nền; Uvicorn transport ping/pong phát hiện socket chết. Watchdog vẫn kiểm session/ACL mỗi 5 giây.
- Nhóm avatar cạnh Lịch sử chỉ hiện **người khác**, không lặp lại avatar bản thân ở góc phải. Khi socket nối nhưng chưa có ai khác, báo “Chỉ bạn đang mở dự án”. Hover avatar báo nếu nội dung chưa khớp SHA-256; không vẽ offset sai vào bản khác nội dung.

Kiểm chứng: 5 presence tests đạt, production Next.js build/TypeScript đạt. Browser regression dùng **hai tiến trình riêng Edge owner + Chrome editor**, con trỏ hai chiều, giữ qua window blur, ảnh profile thật, đổi file, buffer khác, viewer, reload/reconnect và disconnect. Mongo double đạt `target/presence-edge-chrome-fix-01`; Atlas thật đạt `target/presence-atlas-be8806f0511792b7`, `atlas-report.json` passed/cleaned=true. Collections test prefix ngẫu nhiên/guard đã dọn, không thay dự án thật. `test-presence.mjs` nhận factory Atlas qua LATEX_TEST_WEB_FACTORY.

Giới hạn giữ nguyên: chưa CRDT/OT/live text merge; cần cùng file và nội dung khớp mới vẽ cursor. Nếu buffer khác do chưa lưu, lưu nguồn và để phía kia tải thay đổi. Presence vẫn một worker. **Python server đang chạy không tự reload:** user lưu bài → Ctrl+C terminal hiện tại → `scripts\run-python-web.bat` → Ctrl+F5 cả hai trình duyệt. UI đã build trong lần sửa này. `--build` chỉ build rồi thoát, cần chạy launcher thêm lần không có --build. Không dừng server thật/khởi động thay từ tool để giữ Google env ở terminal user.

## 76. WebSocket: tên thật, avatar và con trỏ người đang chỉnh sửa (10/11/2026)

Đã triển khai yêu cầu nhận biết người được chia sẻ đang sửa ở đâu trong **editor LaTeX**. Header hiển thị avatar cạnh trái Lịch sử; ảnh profile nếu có, fallback chữ đầu tên nếu không có. Hover cho biết tên tài khoản, file và trạng thái focus. Mỗi user một avatar dù mở nhiều tab; mỗi kết nối có con trỏ riêng. Trong cùng file và cùng nội dung, hiển thị caret màu + nhãn tên thật và nền vùng chọn. Đổi file, mất focus, đóng tab hoặc mất socket sẽ cập nhật/xóa dấu tương ứng. Người có quyền viewer không mở socket, không xuất hiện trong danh sách và không phát con trỏ. Không dùng tên ẩn danh.

- Backend: `collaboration/presence.py`, gateway `http/web_accounts.py`; endpoint WebSocket `/api/projects/{id}/presence`; ảnh qua `/api/projects/{id}/sharing/avatar/{user}` chỉ cấp sau kiểm tra quyền dự án và user đang hiện diện. Identity/color do server tạo từ session đăng nhập, bỏ qua dữ liệu giả tên/id/color từ client. Kiểm tra Origin/Host, role, owner/editor; kiểm tra lại session/ACL mỗi 5 giây, ngắt khi logout/revoke/downgrade. Chặn path traversal/payload sai, giới hạn số kết nối/kích thước/tần suất, queue giữ snapshot mới nhất, heartbeat/reconnect và cleanup.
- Frontend: `lib/usePresence.ts` dùng WebSocket và CodeMirror decorations, `components/PresenceAvatars.tsx`, tích hợp page/CSS. Throttle 80ms, heartbeat 15 giây, backoff reconnect tối đa 15 giây; SHA-256 nội dung khớp mới vẽ con trỏ, tránh hiển thị vị trí sai khi hai buffer chưa đồng bộ. Không gửi nội dung source hoặc email qua presence.
- Kiểm chứng: frontend production build + TypeScript đạt; backend presence 4 tests đạt, nhóm auth/sharing/Mongo trước đó 18 tests đạt. Browser Edge 3 phiên độc lập đạt ở `target/presence-browser-04`: tên thật, ảnh profile decode được, caret/selection, đổi file, buffer khác thì ẩn, viewer không quảng bá, reload/reconnect, đóng tab cleanup, không pageerror. Đã xem ảnh `named-cursor-selection.png`. Test mới `tests/test_presence.py`, `scripts/test-presence.mjs`; dùng Mongo double/test home riêng, không đổi dữ liệu Atlas thật.

Giới hạn rõ ràng: presence hiện trong RAM của **một web worker**; MongoDB vẫn là nguồn tài khoản/ACL, không lưu trạng thái online vào DB. Nhiều worker/server cần Redis pub/sub hoặc backend chia sẻ presence tương đương. Đây chưa phải CRDT/OT hoặc live text merge: nội dung tiếp tục dùng save/polling + expected/409, giữ bản sửa chưa lưu khi xung đột. Không vẽ cursor lên Word/PDF. Launcher/host guard vẫn loopback như trước. Muốn xem bản mới: lưu bài, dừng server cũ, chạy lại `scripts\run-python-web.bat --build` trong terminal có Google env; thử hai browser profile bằng hai tài khoản có quyền sửa.

## 75. Atlas kết nối, migration dữ liệu thật và kiểm thử live đã hoàn tất (10/11/2026)

**Thay thế trạng thái pending ở mục 74:** user đã điền URI vào config/mongodb.local.json và yêu cầu kiểm tra/kiểm thử. Kết nối Atlas tới database `latex` thành công, không in URI/password. Xác nhận cổng 8000 không có server đang chạy rồi thực hiện migration --apply; kết quả complete, source fingerprint vẫn `2f1d67df974160787430d1077b324633de6dbcc2f0af23a8ce9dd794cf59fb5a`, SourceFilesModified=false.

**Dữ liệu thật:** users 3, sessions 1, shared_projects 1, invitations 0, link_visitors 0, user_state 3, projects 6, history 28, project_records 1. Backup tại `C:\Users\Admin\OverleafDesktopPython\.migration-backups\mongo-20261110-081302-95cea4ba`. Script đã đối chiếu từng document khi import, sau đó kiểm main source tồn tại, đọc/kiểm checksum đủ 28 revision payload, indexes email unique/session TTL và HTTP health trả database=mongodb qua factory chính. Source SQLite/JSON gốc không thay đổi. Root metadata/settings/ownership hiện đọc MongoDB; dữ liệu unassigned legacy giữ nguyên như trước, không gán nhầm user.

**Live browser tests:** tài khoản Atlas không được dùng database khác ngoài `latex` (lần thử database tạm riêng bị từ chối trước khi tạo dữ liệu). Không tăng quyền hoặc sửa quyền Atlas. Thay bằng namespace collection tạm có prefix/nonce guard trong đúng database được cấp phép; production collection names/users/projects không đổi. Test helper `tests/atlas_namespace.py` và `run_atlas_namespace_web.py` dùng PyMongo/Atlas thật, không mongomock; chỉ chạy khi Node test harness được chỉ rõ factory và prefix/guard. Không có cờ test/fallback này trong launcher sản phẩm.

Kết quả tại `target/atlas-scoped-live-20261110-3df89fbc/report.json`: accounts PASS (signup/onboarding/reload/student/tutor/private/log-out/in/Google config/error UI); sharing PASS (private invitation qua signup/role, viewer/editor permissions, shared list, link public/revoke, mobile/Escape); documents PASS (source/save, 2-page PDF byte-identical export, separate PDF tab, Word edit/export, upload/binary preview/avatar, ZIP import/compile). Ba namespace tạm đều cleaned=true bằng đúng prefix và guard đã tạo; số users/projects thật và fingerprint source giữ nguyên. Google OAuth thật không được chạy lại trong test tự động, nhưng Google sub/role/session cũ đã được migration giữ nguyên; các biến Google trong terminal của user không thay đổi.

**Chạy lại app:** không tự khởi động persistent server từ tool vì môi trường tool không có Google client secret của terminal user. User chạy `scripts\run-python-web.bat` trong cùng PowerShell đã cấu hình Google để bật app trên 8000, tránh lại trùng cổng. URI Atlas đọc từ file private đã gitignore. Code main MongoDB và dữ liệu thật đều sẵn sàng; không còn chờ URI/migration. Không commit/push.

---

## 74. MongoDB là persistence chính của code web; cutover dữ liệu thật chờ URI Atlas (10/11/2026)

**Trạng thái hiện tại:** user chọn hướng theo dõi trên MongoDB Atlas và đã xác nhận dừng web. User có cluster và sẽ điền `config/mongodb.local.json`. File local đã tạo và gitignore nhưng lần kiểm cuối `uri` vẫn rỗng. Vì vậy **chưa kết nối/migrate dữ liệu thật lên Atlas, chưa khởi động lại web**, không được báo đã cutover xong. Không khởi động Docker hoặc cài MongoDB local sau khi user chọn Atlas; không in URI/secret ra chat/log. Khi file được điền: chạy migrate --check, --dry-run rồi --apply, đối chiếu complete trước khi cho user chạy lại web trong terminal có Google env.

**Code chính:** `storage/mongo.py` đọc LATEX_MONGO_URI/DB hoặc file private JSON, ping, indexes unique/partial/TTL và storage-home binding; lỗi thiếu cấu hình/mạng/migration pending dừng rõ ràng, không fallback SQLite. `auth/accounts.py` đã thay SQL bằng Mongo users/sessions, giữ id/email/Google sub/role/password hash/cookie contract, auth_version thu hồi token khi đổi mật khẩu. `collaboration/sharing.py` dùng Mongo policy/invitation/link visitors với grant/revoke/check quyền như trước. PyMongo chuyển thành dependency base; uv.lock cập nhật.

`storage/mongo_store.py`: MongoStore/MongoState/MongoHistory/MongoRecords cho metadata projects, settings/activity, history labels/checksum, review và IELTS data. Source/binary/PDF/DOCX/revision payload vẫn filesystem volume; JSON manifest/sidecar không phải nguồn runtime canonical. Store được thêm injection state/locations và hook write_info để giữ desktop tương thích; WebAccounts/create_web_app compose MongoStore và repository mới. Desktop Store cũ vẫn file/JSON; M5 persistence.py cũ không phải main web path. MongoDB lỗi trong gateway/API trả 503 không lộ chi tiết secret. Actor và project authorization dùng ContextVar; không query Mongo lặp theo từng file trong cùng request compile/fingerprint, nhưng request mới vẫn kiểm quyền.

**Migration:** `storage/migration.py`, CLI `entrypoints/migrate_mongodb.py`, wrapper `scripts/migrate-mongodb.bat`. Dry-run chỉ đọc SQLite/JSON; apply yêu cầu destination rỗng hoặc migration đúng fingerprint đang dở, snapshot SQLite bằng backup API, copy JSON/metadata/manifest vào `<home>/.migration-backups/mongo-*`, giữ source nguyên trạng. Giữ user ID/Google/roles/session digest/expiry/ACL/link token/invite token và metadata. Đối chiếu từng document + history checksum + fingerprint source không đổi trước marker complete; gián đoạn để in-progress (runtime không vào dữ liệu chuyển dở), có resume/idempotency; không ghi đè database khác. Root legacy projects không tự gán user. Rebind-home CLI hỗ trợ backend chuyển server nhưng giữ Atlas, yêu cầu volume đã copy và main/history được kiểm trước đổi binding.

**Dữ liệu thật đã dry-run:** 3 users, 1 session, 1 shared policy, 0 invitations, 0 link visitors, 3 user_state, 6 projects, 28 history, 1 project_records. SourceFilesModified=false. Chưa apply vì thiếu URI. Tài khoản/project/history thực không được tạo giả trong Atlas để test.

**Kiểm chứng:** 170 backend tests đạt (mongomock inject rõ bằng fixture, production không có mock/fallback flag). Test mới `test_mongodb_primary.py` kiểm fixture SQL/JSON thật, preserve credential/Google/cookie/role/ACL/history/review, source không đổi/backup, resume/idempotency, nonempty target refused, Mongo thắng JSON bị hỏng, require migration kể cả project không có state.json, outage503/redaction, rebind volume và metadata/counters thực ghi collections. Browser fixture riêng `tests/run_mongo_web.py` + Node harness LATEX_TEST_WEB_FACTORY: sharing đạt `target/mongodb-sharing-final`, PDF byte-identical export/Word edit/upload/ZIP đạt `target/mongodb-documents-final`. Đây là integration mock-backed, không phải Atlas live. Lock/diff check và pip check đạt. Không sửa frontend behavior mới ngoài harness.

Docs: MONGODB_PRIMARY.md có hướng dẫn Atlas, config, migrate/check, collections, nguồn file volume và chuyển server; README/backend README và các docs auth/sharing/architecture/local web cập nhật override các mốc SQLite trước. Không chỉnh nội dung user có sẵn ở deploy/python/compose.yaml. Chưa commit/push.

---

## 73. Chia sẻ web Private/Public và Shared with you thật (10/11/2026)

Thay hộp thoại cộng tác TCP/TLS trong **web** bằng `SharePanel.tsx`: tiêu đề project, mời một/nhiều email với quyền Viewer/Editor, danh sách owner/người được mời (pending/accepted), đổi quyền/thu hồi, copy invitation link; general access Riêng tư hoặc Bất kỳ ai có liên kết, chọn quyền, copy/reset link, trạng thái lỗi/thành công, song ngữ, mobile, focus trap và Escape. Desktop giữ NetworkPanel cũ; web chặn các action TCP connect/reconnect/edit/cursor. Nút Share mở với file hiện tại, không ép main.tex.

Chia sẻ public ở đây là **người có link đăng nhập Latex rồi mở**, không anonymous PDF hoặc project công khai để tìm kiếm. Private invite phải có token và đăng nhập đúng email; chưa tích hợp SMTP/dịch vụ gửi mail, nên UI ghi rõ owner tự copy/gửi link lời mời. Không có tool gửi email thật trong đợt này.

Backend thêm `collaboration/sharing.py`, `http/sharing_api.py`; dùng SQLite identity hiện có, bổ sung shared_projects/invitations/link_visitors. Mặc định private/Viewer. Owner quản lý ACL; editor không quản lý share hay xóa/đổi tên project owner; viewer chỉ đọc và có thể compile PDF, không ghi source/file/Word/review/history checkpoint. Shared request định tuyến về Store/Compiler owner sau khi kiểm quyền, chặn action đọc chéo project (copy-file) nếu chưa có kiểm ACL hai phía. `auth/context.py` và property State.session mang actor theo request/worker thread để history ghi đúng người sửa mà không ghi đè session owner toàn cục.

Link reset/off làm mất quyền dựa trên link ở request sau; explicit invitation còn hiệu lực không bị xóa theo link. Revoke member xóa invitation/membership nhưng không triệt tiêu public link đang hoạt động nếu người đó cũng có link. Token sharing được lưu ở DB server để owner copy lại; không trả secret/membership list cho non-owner. Cookie/session hiện có không thay đổi.

Frontend bootstrap giữ #share token trong sessionStorage qua login/Google/onboarding rồi POST accept và mở project. `/api/projects` trả own + shared đã được ACL cho phép; mục Shared with you của mục 72 **không còn placeholder**. ProjectCard hiển thị owner/quyền, ẩn quản lý project cho non-owner. Readonly facet của CodeMirror, add/upload controls và Word tab Viewer bị vô hiệu hóa. Permission header qua watch cập nhật UI khi owner đổi quyền. Viewer vẫn compile/xem PDF; đọc Word draft qua editor UI chưa hỗ trợ, dùng PDF. Server vẫn kiểm ghi dữ liệu độc lập với UI.

Thêm expected source khi frontend save; backend so sánh dưới Store.lock và trả 409 nếu file đã đổi, giữ nội dung local chưa lưu để đối chiếu. Chưa phải CRDT/OT, chưa merge tự động, chưa bảo đảm concurrency cho toàn bộ DOCX/file APIs. Polling có sẵn tải external saved changes khi client không dirty. **Chưa triển khai đồng bộ gõ realtime/presence/chat WebSocket mới**; không coi Share dialog là hoàn thành realtime collaboration.

Kiểm chứng: full backend **162 tests đạt**; sharing tests kiểm wrong-email, viewer/editor/owner, cross-project access, revoke/rotate/private/downgrade, ACL persistence và conflict409; bổ sung assert history actor Editor cũng đạt. Next production build + TypeScript đạt. Browser `test-web-sharing.mjs` đạt cuối ở `target/web-sharing-04`: owner/member/visitor, private invite qua đăng ký/chọn role, clipboard, viewer không gõ được/compile PDF được, nâng Editor lưu cùng source, shared list, public link + turn-off (private member còn quyền), mobile không chồng form và Escape. Đã xem screenshot private/public/mobile, sửa flex-shrink của form ở dialog mobile. Regression PDF/Word/upload/ZIP đạt ở `target/web-sharing-documents-01`. Docs: WEB_PROJECT_SHARING.md, README, auth/architecture cập nhật. diff check đạt.

Backend người dùng đang chạy từ terminal đã cấu hình Google được giữ nguyên, không tự dừng để tránh mất ngữ cảnh. Cần lưu bài, Ctrl+C và chạy lại scripts/run-python-web.bat trong cùng terminal đó, rồi reload frontend để nạp API mới. Localhost link chỉ thử được trên cùng máy; chưa đổi loopback/public hosting/proxy isolation. Chưa commit/push.

---

## 72. Mục Được chia sẻ với bạn / Shared with you (10/11/2026)

Thêm nút icon UsersRound vào thanh ngang trang chính, giữa Tất cả dự án và Thư viện mẫu, có active state/aria-current và bản dịch Anh/Việt. Chuyển mục xóa tìm kiếm cũ. Mục Shared hiện là khu vực chuẩn bị, số lượng 0, không hiển thị lẫn các dự án cá nhân; empty state nói rõ lời mời/cộng tác sẽ được bổ sung sau. Quay lại Tất cả dự án vẫn có danh sách, tìm kiếm và grid/list hiện có.

Chỉ thay frontend page.tsx; chưa thêm API gửi lời mời, membership/ACL hay đồng bộ nhiều người. Khi triển khai backend phải lấy danh sách từ endpoint đã kiểm quyền thành viên, không đoán shared từ danh sách cá nhân ở client. Không thay quyền sở hữu hay vùng dữ liệu theo tài khoản. Production build/TypeScript đạt. Browser kiểm chuyển Shared/All, active state, không lẫn tài liệu cá nhân, tìm kiếm/grid-list/template, dark/mobile và editor tree/outline đạt tại `target/home-layout-1794284895356`.

---

## 71. Log nhiều cảnh báo bị ép thành đường viền, không đọc được (10/11/2026)

Nguyên nhân: `.logs-list-container` là flex-column có chiều cao giới hạn, trong khi `.log-card` và `.raw-logs-section` dùng flex-shrink mặc định và overflow hidden. Khi có vài chục log, các card bị ép chiều cao gần bằng border; header/nội dung bị cắt mặc dù dữ liệu vẫn có.

Sửa `globals.css`: card/raw section dùng flex 0 0 auto để cuộn theo chiều dọc; header tối thiểu 62px, nội dung dài xuống dòng, tăng font tiêu đề/message/raw log, filter tabs không co và có cuộn ngang. `LogsView.tsx`: header là button có aria-expanded/aria-controls, thao tác Enter/Space; luôn hiển thị message gốc khi mở, kể cả message trùng title. Thêm thanh số thông báo, Mở tất cả/Thu gọn chi tiết theo filter, Xem nhật ký thô trực tiếp và cuộn tới raw section. Raw header dùng button, giữ sao chép/tải .log và bộ lọc hiện có. Không thay parser, backend compile hoặc xóa log.

Next production build + TypeScript đạt; diff check đạt. Test browser mới `apps/frontend/scripts/test-many-logs.mjs` tạo tài khoản/home riêng, compile tài liệu LaTeX thật có 120 PackageWarningNoLine khác nhau (tổng 123 gồm 3 cảnh báo locale môi trường). Kiểm card min-height/readable, container cuộn, mở cảnh báo đầu/cuối, Enter đóng/mở, mở tất cả/thu gọn, filter, mở raw giữ Warning 120, tải .log đủ nội dung, sáng/tối và không JS page error. Test đạt tại `target/many-logs-regression-02`; đã xem screenshot light. Frontend output đã build lại; người dùng lưu bài rồi reload browser để tải UI mới, không cần restart backend Google. Chưa commit/push.

---

## 70. Trang chính bỏ sidebar, điều hướng dự án nằm ngang (10/11/2026)

Theo yêu cầu bố cục trang chính: bỏ hẳn JSX `.home-sidebar` (mục dự án, note và footer dư ở cạnh trái) và tab Dự án `.nav-current` cạnh logo ở home. Header giữ logo, trạng thái workspace, settings và profile. Không thay cấu trúc editor/file-sidebar/outline/activity-bar.

`app/page.tsx` chuyển mục Tất cả dự án có số lượng sang nav ngang `.project-navigation` ngay trên danh sách, cạnh ô tìm kiếm và kiểu xem. Nút Tất cả dự án xóa bộ lọc tìm kiếm; Thư viện mẫu mở dialog mẫu/tạo dự án hiện có. Không thêm các mục chia sẻ/lưu trữ không có nghiệp vụ. Nút đổi kiểu xem danh sách/lưới hoạt động thật, có aria-pressed, mặc định danh sách ngang gọn dùng lại ProjectCard, giữ onOpen/onManage. Banner và ActivityHeatmap vẫn giữ phong cách cũ.

CSS mới scope vào dashboard-home/home-project-tools/project-list; phần nội dung sử dụng chiều ngang trước đây bị sidebar chiếm. Thanh công cụ xuống dòng trên mobile; style dùng biến theme hiện tại. Không sửa backend hoặc dữ liệu tài khoản/dự án.

Kiểm chứng: Next production build + TypeScript đạt, diff check đạt. Browser kiểm độc lập bằng home tạm `target/home-layout-1794283415213`: không có sidebar/header nav cũ, nav nằm trên project list, tìm kiếm/xóa lọc, mở thư viện mẫu, list/grid, sáng/tối/mobile không tràn ngang; vào editor vẫn có file-sidebar/outline/sidebar-divider và thu gọn/mở cây file được. Không có JS page error. Đã xem screenshot home desktop/mobile. Frontend static output đã build lại, server đang chạy sẽ phục vụ giao diện mới khi tải lại trang; không cần khởi động thêm server hoặc thay OAuth config. Chưa commit/push.

---

> **Cập nhật 10/11/2026 — Đăng nhập web, Google và chọn Tutor/Student:** luồng này đã tích hợp vào app thật, không chỉ prototype. Xem mục 69 ngay dưới và [WEB_AUTH_ONBOARDING.md](docs/WEB_AUTH_ONBOARDING.md). Các mục cũ mô tả phiên web dùng chung được thay thế bởi gateway tài khoản mới.

## 69. Web account onboarding và Google sign-in (10/11/2026)

**Đã code:** đăng ký email/mật khẩu, đăng nhập, hoàn thiện tên và chọn Student/Tutor sau đăng ký. Khi chưa chọn, API chặn vào workspace; reload/đăng nhập lại tiếp tục bước còn thiếu. Sau khi lưu, vai trò là thuộc tính tài khoản và mở trang dự án theo Student/Tutor; không tự chọn vai trò và không cho tự cấp Admin. Vai trò chính chưa đổi sau khi hoàn tất, chưa hỗ trợ một tài khoản chuyển nhiều vai trò.

**Backend:** thêm `auth/accounts.py` dùng SQLite có sẵn trong Python cho identity, hash mật khẩu, Google sub, role và session token băm; không cần cài thêm DB server/Python. Đây chưa phải MongoDB integration. Session có expiry 12 giờ hoặc 30 ngày, revoke khi logout, rotate khi login; đổi mật khẩu thu hồi các phiên trước. `http/web_accounts.py` là ASGI gateway trước API hiện có: xác định user theo HttpOnly cookie, tạo Store/Compiler riêng ở `<home>/users/<id>`, kiểm Host/Origin, giới hạn auth body, rate limit. Bootstrap local cookie không cấp quyền người dùng. Lifespan dọn toàn bộ compiler/upload/network theo từng workspace khi tắt server. `core/application.py` nối gateway cho web; desktop giữ cơ chế cũ.

**Google:** authorization code + PKCE S256 + cookie-bound state + nonce; google-auth kiểm ID token, audience/issuer/expiry và email_verified. Định danh bằng sub, không tự ghép tài khoản trùng email. Không lưu Google access/refresh token hoặc xin quyền Drive/Gmail. Callback lỗi/hủy về màn đăng nhập. Cần người vận hành cấu hình LATEX_GOOGLE_CLIENT_ID/SECRET và LATEX_PUBLIC_URL, đăng ký callback đúng; chưa có credential thật nên chỉ kiểm mô phỏng và kiểm Google-disabled UI, chưa nghiệm thu login Google thật. Google button bị disable có giải thích khi chưa cấu hình.

**Frontend:** `RoleSelection.tsx` (thẻ radio Tutor/Student, tên, nút tiếp tục/đăng xuất, loading/error và bàn phím/mobile), `AuthView.tsx` (Google, password tối thiểu 8 khi đăng ký, lỗi OAuth, bỏ guest trên web), `Session` type và hook/page gate onboarding. Student/Tutor có tiêu đề không gian riêng và dùng các tính năng editor/PDF/Word đang có. Chưa xây backend quản lý lớp, giao/nộp/chấm bài theo lớp hoặc chia sẻ dự án nhiều người; không coi tiêu đề theo role là LMS hoàn chỉnh. Phần IELTS chi tiết trước vẫn là prototype độc lập.

**Dữ liệu cũ:** tự import credential từ `.app/state.json` một lần, không dùng remembered session chung. Dự án cũ ở `<home>/projects` giữ nguyên và không tự gán cho tài khoản đầu tiên; operator cần xác định chủ sở hữu, nhập ZIP qua tài khoản đúng. Danh sách cá nhân mới có thể trống; không xóa dữ liệu cũ. Không migrate âm thầm vào MongoDB.

**Dependency/docs:** thêm web/local dependency google-auth, requests, httpx; cập nhật uv.lock. Launcher Windows/shell kiểm các import này trước khi bỏ qua setup. README/backend README và kiến trúc/local web docs trỏ đến WEB_AUTH_ONBOARDING.md. Bản web vẫn bind loopback, chưa phải triển khai public/multi-worker; cần phần hạ tầng, quyền chia sẻ và compile isolation trước khi publish.

**Kiểm chứng:** Next production build + TypeScript đạt. Full backend suite đạt 158 tests trước khi bổ sung thêm test Google token exchange/nonce; bộ auth cuối đạt 7 tests. Browser `test-web-accounts.mjs` đạt ở `target/web-account-onboarding-02`: đăng ký thật, reload pending, Student/Tutor hai browser context, giữ role khi login lại, private-project 404, mobile, Google disabled/error, không JS page error. Đã xem screenshot desktop/mobile và sửa role-card flex/mobile width. Regression `test-local-web.mjs` cập nhật đăng ký/chọn role và đọc vùng user, đạt ở `target/web-account-documents-01`: compile 2 trang, PDF export đúng bytes, PDF tab, Word edit/export, upload/image/avatar và ZIP import/compile. `pip check` đạt. Chưa commit/push.

---

> **Mới nhất — Khôi phục và nâng cấp icon mũi tên thoát dự án trong thanh Header (`ArrowLeft`) (06/11/2026):** sửa lỗi icon mũi tên quay lại bị đẩy lệch và ẩn mất khi tên dự án dài (do thuộc tính căn giữa mặc định của thẻ button); tách cấu trúc thành nút icon thoát chuyên dụng `.project-breadcrumb-back` với `flex-shrink: 0` cố định 24x24px, bo góc và hiệu ứng chuyển động trực quan khi hover, đi kèm phần tên dự án tự động thu gọn `text-overflow: ellipsis` từ đầu đến cuối; cho phép người dùng click thoát ra màn hình danh sách dự án dễ dàng và rõ ràng. Xem **mục 52**.

> **Mới nhất — Sửa lỗi viền/nền và làm sáng icon thanh công cụ Word Preview ở chế độ tối (Dark Mode) (06/11/2026):** khắc phục triệt để lỗi phân tách nền trắng - tối trên thanh công cụ SuperDoc bằng cách cấu hình `min-width: 100%; width: max-content; background: var(--panel) !important;`; đồng bộ toàn bộ biến theme SuperDoc (`--sd-ui-*`) sang màu dark panel; làm sáng các icon, nhãn, thanh phân cách và ô chọn phông chữ sang tông màu bạc sáng (`#d8e2ec` / `#e2e8f0`) giúp hiển thị rõ nét, tương phản cao và mượt mà khi người dùng bật Dark Mode. Xem **mục 51**.

> **Mới nhất — Thu gọn thanh công cụ Word Preview tối đa hóa diện tích đọc văn bản (06/11/2026):** nén thanh thao tác `.native-word-actions` xuống chiều cao 30px (nút 22px tinh gọn), chuyển dòng ghi chú giải thích sang tooltip hover (`ℹ`) thay vì chiếm riêng một hàng 30px, giúp giải phóng toàn bộ không gian dọc cho vùng hiển thị và chỉnh sửa văn bản Word bên dưới. Xem **mục 50**.

> **Mới nhất — Thu gọn thanh Header trong trang Editor (06/11/2026):** giảm chiều cao thanh tiêu đề `app-header` từ 76px xuống 42px khi đang mở dự án; tinh chỉnh kích thước logo, tên dự án, nút thao tác (`History`, `Collaborate`, `Layout`, `Settings`) và avatar gọn gàng chuẩn Overleaf, giải phóng thêm 34px chiều dọc cho không gian soạn thảo mã nguồn và khung xem trước tài liệu bên dưới. Xem **mục 49**.

> **Mới nhất — Tối ưu thanh công cụ: Hợp nhất tab PDF/Word cùng hàng với Recompile & Logs (06/11/2026):** chuyển các nút chuyển đổi chế độ xem `PDF preview` và `Word preview` lên cùng một hàng (`compile-toolbar`) ngay cạnh nút Recompile và nút Logs; loại bỏ hoàn toàn thanh tab riêng biệt thứ 2 (`.preview-tabs`) chiếm dụng chiều cao; thiết kế dạng nút chuyển segmented/pill gọn gàng, tinh tế, tiết kiệm tối đa diện tích dọc cho màn hình xem trước tài liệu. Xem **mục 48**.

> **Mới nhất — Chấm điểm IELTS Writing trực tiếp trên Editor (Task 1 & Task 2) và Xuất Báo Cáo PDF (05/11/2026):** tích hợp icon IELTS (`GraduationCap`) trên thanh công cụ bên trái theo Ảnh 4; bảng chấm điểm đầy đủ 4 tiêu chí chuẩn quốc tế cho Task 1 (TA, CC, LR, GRA) và Task 2 (TR, CC, LR, GRA) theo Ảnh 1 & 2; hiển thị chi tiết Band Descriptors chính thức, ô nhập lời phê của Tutor; tính điểm làm tròn theo chuẩn IELTS; lưu trữ bền vững và sinh báo cáo LaTeX/PDF theo kiến trúc Ảnh 3. Xem **mục 47**.

> **Mới nhất — Giao diện Editor toàn màn hình và Bảng Logs & Errors chuẩn Overleaf (04/11/2026):** chuyển toàn bộ build output và bảng error khỏi cột soạn thảo LaTeX để editor chiếm 100% chiều cao; bổ sung nút icon tài liệu kèm huy hiệu đếm lỗi/cảnh báo cạnh nút Recompile; chế độ xem Logs & Errors chuyên dụng ở cột phải với các tab All/Errors/Warnings/Info, thẻ lỗi mở rộng được, nút "Đi tới dòng", mục Raw logs thu gọn, thanh đáy "Clear cached files" và nút "Back to PDF" để quay lại xem PDF. Xem **mục 46**.

> **Mới nhất — Kéo thả tệp/thư mục khi nhập dự án (ZIP/Word/Markdown/Folder) (04/11/2026):** bổ sung vùng kéo thả (Drag & Drop Zone) trực quan trong hộp thoại tạo dự án mới; tự động nhận diện định dạng (.zip, .docx, .md, thư mục hoặc danh sách tệp), tự điền tên dự án và giải phóng thao tác phải mở cửa sổ duyệt file path; hỗ trợ cả native path và upload stream nhị phân độc lập. Xem **mục 45**.

> **Mới nhất — Sửa lỗi phông chữ tiếng Việt trên Next.js (04/11/2026):** loại bỏ hoàn toàn phông `Georgia` vốn thiếu dải ký tự tiếng Việt Unicode (U+1E00–U+1EFF) trên Windows, thay thế bằng bộ phông serif chuẩn mực hỗ trợ đầy đủ tiếng Việt (`Charter`, `Sitka Text`, `Sitka Heading`, `Cambria`, `Times New Roman`, `serif`); nới lỏng `letter-spacing` tránh va chạm/rơi dấu. Xem **mục 40**.

> **Mới nhất — Giao diện Hoạt động, trò chơi Snake và loại bỏ thanh cuộn ngang (04/11/2026):** khôi phục thẻ Hoạt động theo bản Java, bổ sung mini-game Snake và màn hình kết thúc pixel art; tính toán kích cỡ lưới chính xác và loại bỏ triệt để thanh cuộn ngang. Xem **mục 35**.

> **Mới nhất — Python + Next.js desktop (03/11/2026):** đã dựng bản đầu chạy thật bằng pywebview/FastAPI/Next.js, có editor/template/compile/PDF. **Chưa port đủ history, SyncTeX, import và cộng tác**. Xem **mục 32** và [PYTHON_NEXTJS_MIGRATION.md](PYTHON_NEXTJS_MIGRATION.md). Bản Java được giữ nguyên để đối chiếu.

> **Mới nhất — chạy trên Mac (03/11/2026):** bổ sung launcher kiểm JDK 21/Maven, tự tìm Homebrew JDK/MacTeX, `--check`/`--build-only`, file nhấp đúp `.command`. Xem **mục 31** và [MACOS.md](MACOS.md). Chưa xác nhận native trên máy Mac.

> **Mới nhất — PDF/hiệu năng (02/11/2026):** SyncTeX hai chiều, renderer tái sử dụng document, benchmark **248 trang**; **148/148 JUnit**, 6 native SyncTeX build và smoke UI/hồi quy PDF đạt. Xem **mục 30** và [PDF_PERFORMANCE.md](PDF_PERFORMANCE.md).

> **Mới nhất — thư viện mẫu resource (02/11/2026):** triển khai yêu cầu trong `yêu cầu.md.md`: **29 mẫu gốc Anh/Việt**, gallery tìm kiếm/lọc, metadata JSON, preflight package/Biber và cài TinyTeX sau xác nhận. **143/143 JUnit chọn lọc**, **174/174 build thật**, UI/import smoke mở rộng đạt. Xem **mục 28**, [hướng dẫn thư viện](TEMPLATE_LIBRARY.md) và [ma trận kết quả](TEMPLATE_VALIDATION.md).

> **Mới nhất — Auto Compile / History / Layout (02/11/2026):** thêm menu chế độ compile, tự chạy sau 1 giây ngừng gõ; History local có checkpoint, so sánh, nhãn và khôi phục; Layout chia đôi/editor/PDF/cửa sổ riêng/focus. **137/137 JUnit chọn lọc đạt**, smoke JavaFX + compile thật đạt. Xem **mục 27**.

> **Mới nhất — toolbar editor (02/11/2026):** thêm 10 icon ngay sau Italic đúng thứ tự yêu cầu, gồm math/link/comment/reference/citation/figure/table/list/giảm và tăng lề. Có form chèn, đọc nhãn/khóa từ project, giữ vùng chọn khi indent và chặn chèn vào tài liệu đã đổi/chỉ đọc. **128/128 test chọn lọc đạt**, smoke JavaFX và compile snippet thật đạt. Xem **mục 26**.

> **Bố cục hiện tại (02/11/2026):** bốn Maven module nằm trong `modules/`, tài liệu trong `docs/`, và LaTeX mẫu trong `examples/`. Các đường dẫn module cũ trong những phần lịch sử dưới đây được giữ nguyên theo ngữ cảnh lịch sử.

> **Mới nhất — PDF cuộn và liên kết (02/11/2026):** preview xếp trang liên tiếp, cuộn qua các trang, bấm liên kết nội bộ đến tiêu đề và quay lại bằng `↶`. Render theo vùng xem với ngân sách ảnh, giữ nguyên export PDF thật. **120/120 JUnit chọn lọc đạt**, smoke PDF 13 trang và regression compile/export 7 trang đạt. Xem **mục 25**; mô tả viewer một trang ở mục 19 là lịch sử.

> **Mới nhất — sửa ZIP nhiều chương (02/11/2026):** tái hiện trực tiếp `D:\download\latex-test-project-v2.zip`: thiếu gói TeX và chưa có thư mục aux cho `\include`. Đã bổ sung TinyTeX, tự tạo output subfolder, ưu tiên lỗi gốc và retry cache lỗi. ZIP nguyên gốc ra **13 trang với cả ba engine**, có tiếng Việt/Biber, nguồn giữ nguyên. **117/117 JUnit chọn lọc đạt**. Xem **mục 24**.

> **Mới nhất — workspace (02/11/2026):** thêm thư mục/tệp con, upload nhiều tệp hoặc thư mục, kéo thả/dán ảnh, sao chép từ dự án khác/tệp đầu ra và tải URL HTTPS. Hộp thoại Anh/Việt theo ảnh, cây hiển thị thư mục rỗng và tài nguyên. **110/110 JUnit chọn lọc đạt**, smoke JavaFX + tải HTTPS thật đạt. Xem **mục 23**.

> **Mới nhất — tạo/nhập dự án và chọn thư mục (02/11/2026):** menu mới đầy đủ Anh/Việt, 9 nhóm template, nhập ZIP/Word/Markdown/GitHub; chọn nơi lưu từng dự án, đổi mặc định và chuyển dự án có bản phục hồi. **98/98 test chọn lọc đạt**, kiểm thử PDF thật và JavaFX đạt. Xem **mục 22**. Các mục trước giữ lại làm lịch sử.

> **Mới nhất — tiếng Việt và Outline (01/11/2026):** Outline có thanh kéo giữa cây file và danh sách đề mục, nhớ tỷ lệ sau khi thả chuột; không còn khóa chiều cao 120–260 px. Thêm kiểm tra PDF mất ký tự và bản preamble tương thích ba engine. Xem **mục 20–21**. Bộ regression **84/84 test đạt**.

> **Trạng thái mới nhất — PDF preview/export thật (01/11/2026):** đã bỏ Canvas mô phỏng, dùng PDFBox đọc PDF từ compiler; Export chép đúng bytes của PDF đang xem. Nguồn đổi/lỗi compile khóa xuất và ghi rõ trạng thái cũ. **81/81 test đạt**, smoke UI trên tài liệu người dùng 7 trang đạt. Xem **mục 19**; các nhận định “preview Canvas” bên dưới là lịch sử.

> **Sửa lỗi toolchain sau kiểm thử người dùng (01/11/2026):** đã bổ sung tự nhận `latexmk` khi Settings trống; **76/76 test đạt** và đã compile thành công bản sao đúng tài liệu test trong ảnh. Xem **mục 18**. Tiến trình app cũ cần được đóng/mở lại để nhận code mới.

> **Cập nhật mới nhất — tiêu chí Compile (01/11/2026):** nhánh `feat/m1-gui-client`, nền `b89dbc5` kèm thay đổi local. Đã thêm Settings → Compilation, timeout tùy chỉnh/không giới hạn, đường dẫn latexmk, kiểm tra toolchain, Stop và tích hợp Error Explainer. **72/72 test chọn lọc đạt**, smoke PDF thật ba engine + TikZ/tiếng Việt và smoke UI đạt. Xem **mục 17**; số 35/42/52 và nhận định chưa compile LaTeX ở các mục trước là lịch sử.

> **Trạng thái mới nhất — đồng bộ M3/M4 ngày 01/11/2026:** đã sửa bốn điểm trong review; xem mục 13 và [README.md §5](../README.md#5-giao-thức-wire-v2). 42/42 test pass. Phần mô tả v1, giới hạn RESYNC 1000 và kết quả 35 test ở các mục cũ là lịch sử trước lần sửa này, không còn là hợp đồng hiện tại.

Cập nhật: **01/11/2026**. Nền mã nguồn được kiểm tra: `main`, commit **`df06601`**. Tài liệu này ghi trạng thái code thực tế sau khi tích hợp M3; thay thế handoff ngày 30/10 vốn mô tả nhánh `protocol_networking`.

## 1. Kết quả và phạm vi

Đã đọc lại báo cáo Pure Java, phần M3 trong `overleaf_desktop_ver2.md`, đối chiếu blueprint và kiểm tra code M1/M2/M4/M5 trên `main`. Kiến trúc được áp dụng là Java TCP theo báo cáo, không chuyển sang WebSocket/Yjs của blueprint.

**M3 hiện có backend chạy được bằng JAR, adapter kết nối M4 và client tương thích.** Có thể triển khai listener trên VPS theo [README.md §6](../README.md#6-triển-khai-lên-vps). Chưa đăng nhập VPS, chưa chạy service trên Linux và chưa publish/push code trong phiên này.

Nội dung/lịch sử phòng vẫn ở RAM; chưa nối persistence MongoDB hoặc đăng nhập từng tài khoản. Do đó triển khai được dịch vụ networking cho nhóm, nhưng chưa phải nơi lưu trữ tài liệu bền vững.

Tài liệu liên quan:

- [README.md](../README.md): cấu trúc, cách chạy, cấu hình server, giao thức wire v2 và triển khai VPS.
- Mục 14 dưới đây: các ghi chú quan trọng gộp từ `SYNC_PROTOCOL.md`, `DEPLOY_SERVER.md`, `HD_CHAY_UNG_DUNG.md`, `task-m4.md`, `walkthrough-m4.md` (đã xóa khi dọn dự án).

## 2. Các thành viên khác đã code gì trên main?

Đây là phân tích source hiện có; không coi những tính năng chỉ được viết trong báo cáo là đã triển khai.

| Phần | Code đã có trước lần bổ sung này | Tình trạng tích hợp thực tế |
| --- | --- | --- |
| **M1 — GUI** | `ClientApp` chọn JavaFX mặc định hoặc Swing cũ. `OverleafFxApp` có editor, cây file, menu/toolbar, chọn compiler, console, badge online/offline, thao tác reconnect và xử lý message M4. `OverleafDesktopApp` là bản Swing cũ. | JavaFX đã gọi các lớp M2 và `OfflineOperationQueue`. Trước khi bổ sung M3, socket dùng Java Object Stream nên không tương thích codec nhị phân. Preview là Canvas tự phân tích/vẽ nội dung LaTeX, không phải render trực tiếp PDF đầu ra. |
| **M2 — Compiler** | `LatexCompilerService`: gọi compiler nền bằng ProcessBuilder, chọn engine, timeout, thu log, parse lỗi và callback. | Compile thực hiện ở client, không chuyển lên server TCP. Kiểm chứng compile Java không đồng nghĩa đã kiểm chứng TeX/MiKTeX hoặc bản PDF thật. |
| **M2 — I/O** | `ProjectFileManager`: đọc/ghi, tạo/xóa/đổi tên file, liệt kê file, định vị PDF và theo dõi own-write. `WorkspaceWatcher`: giám sát filesystem. Có 8 test file manager. | Có tích hợp trong GUI; một số luồng compile/watch của GUI vẫn dùng working directory `.`. Không thay đổi logic compiler/workspace trong phần M3 này. |
| **M4 — Common** | `CRDTOperation`, `CollaboratorInfo`, `CursorPosition`, `DocumentState`, `MessageType`, `SyncMessage`, `OperationCodec`, `OfflineOperationQueue`. | Payload thao tác đã dùng JSON và có cơ chế giữ pending ops, checkpoint và RESYNC. Queue offline ở RAM, chưa ghi xuống đĩa. |
| **M4 — Server collaboration** | `CRDTEngine`, `OperationHistory`, `CollaborationThreadPool`, `DocumentSyncManager`, `SyncBroadcaster`. Có insert/delete, OT transform, ACK, full sync, cursor, presence, chống áp dụng trùng, replay offline, lịch sử giới hạn và benchmark. | Đây là engine xử lý, trước đó chưa có listener/session TCP thật trên main. Không gọi đó là CRDT đã chứng minh hội tụ cho mọi tình huống; chỉ xác nhận hành vi qua các test đã chạy. |
| **M5 — Database** | Package `com.overleaf.db`: model User/Project/Document, repository interfaces/implementations, cấu hình MongoDB, `DatabaseService`, các test repository/kết nối. | `DatabaseService` có `authenticate`, `loadInitialDocumentContent`, `saveDocumentContent`. Chưa được nối với đường đi M3/M4; authenticate hiện so sánh chuỗi password hash đã truyền vào. Chưa kiểm chứng MongoDB trong lần này. |

`task-m4.md` và `walkthrough-m4.md` là ghi chú lịch sử M4. Một số dòng “chưa commit/chưa có M3/chưa chạy JUnit thật” trong các ghi chú cũ không còn phản ánh trạng thái sau lần tích hợp này; không tự sửa tài liệu của thành viên khác.

## 3. Vì sao không copy nguyên nhánh protocol_networking?

Code M3 cũ nằm ở commit **`14b7d27`**, dùng `com.overleaf.common.collab`, `RoomManager`, `TEXT_UPDATE` và snapshot toàn văn bản. Trên main hiện tại, M4 dùng **`com.overleaf.common.sync`**, `TEXT_EDIT`, `FULL_SYNC`, `CHECKPOINT`, `RESYNC` và `DocumentSyncManager`.

Lần này triển khai transport phù hợp với model M4 đang có. Không đưa lại package `common.collab` hoặc mô hình RoomManager cũ. Frame v1 mới cũng khác layout của frame v1 cũ, nên phải dùng client/server mới cùng nhau.

## 4. M3 đã bổ sung những file nào?

### Common

| File tương đối với `overleaf-common/src/main/java/com/overleaf/common/sync/` | Nội dung |
| --- | --- |
| `MessageCodec.java` | Frame nhị phân length-prefixed, version, UTF-8, năm trường string và timestamp. Giới hạn body 1 MiB; kiểm tra dữ liệu sai, bị cắt hoặc dư. Không deserialize Java object. |
| `TlsContexts.java` | Nạp private key/trust từ PKCS12 bên ngoài JAR qua biến môi trường. Client vẫn kiểm tra hostname. |
| `MessageType.java` | Thêm `AUTH` cho bước xác thực transport. |
| `OfflineOperationQueue.java` | Khi nhận CHECKPOINT, chuyển queue sang online. Sửa lỗi JOIN thành công nhưng queue còn offline nên thao tác gõ không được gửi. |

### Server

Đường dẫn: `overleaf-server/src/main/java/com/overleaf/server/`.

| File | Nội dung |
| --- | --- |
| `ServerConfig.java` | Đọc env/port, kiểm tra cấu hình. Mặc định loopback; bind bên ngoài bắt buộc TLS và token ít nhất 24 ký tự. `toString` không in token. |
| `ServerApp.java` | Main chạy headless, nạp TLS keystore khi cần, bind, startup log và shutdown hook; lỗi startup trả exit code 1. |
| `OverleafServer.java` | Accept TCP, giới hạn client/phòng, AUTH, quản lý identity/membership, kiểm tra request và gọi M4 đồng bộ. |
| `ServerSession.java` | Cài `SyncBroadcaster.ClientSession`: socket reader/writer riêng, queue frame giới hạn, dọn kết nối và presence. |

Sửa hai lớp M4 để transport tích hợp đúng:

- `collaboration/DocumentSyncManager.java`: thêm `processMessage(session, message)` chạy đồng bộ. `handleMessage` bất đồng bộ cũ vẫn giữ nguyên API và gọi lại hàm này. Reader M3 dùng entry point đồng bộ để JOIN/EDIT/LEAVE không vượt nhau.
- `collaboration/SyncBroadcaster.java`: giới hạn outbox; bổ sung constructor nhận capacity và `ClientSession.disconnect()` mặc định để giữ tương thích adapter cũ. Outbox trong TCP backend là 128 message; constructor mặc định M4 là 1024, vẫn hỗ trợ test burst 300 message theo thứ tự. Session TCP đóng khi outbox đầy.

### Client và GUI

| File | Nội dung |
| --- | --- |
| `overleaf-client/.../client/net/NetworkClient.java` | Connect timeout 5 giây, TLS và hostname verification, AUTH trước nhận dữ liệu, FIFO writer queue 64 frame, receiver callback, heartbeat mỗi 15 giây, đóng kết nối. |
| `overleaf-client/.../client/net/NetworkDemo.java` | Console chạy độc lập: join, insert/delete, cursor; in snapshot, operation và ACK để kiểm tra server. |
| `overleaf-client/.../client/gui/OverleafFxApp.java` | Thay Object Stream bằng NetworkClient dùng chung; thêm token/TLS vào hộp thoại; bỏ thread gửi riêng cho mỗi message; hiển thị presence thật thay Alice/Bob giả; khóa editor khi chờ JOIN/RESYNC và mở lại khi CHECKPOINT/ngắt kết nối/lỗi. Sửa việc nhận biết đổi room để tạo queue đúng phòng. |

GUI được compile bằng Maven. Chưa mở GUI để kiểm tra thủ công tương tác hai cửa sổ trong phiên này. Bản Swing cũ không được nối networking mới.

### Build, test và deploy

- `build-network.ps1`, `build-network.sh`: build Java 17 và đóng gói server/demo độc lập, không kéo JavaFX hoặc MongoDB vào JAR triển khai.
- `run-server.bat`, `run-network-demo.bat`: chạy JAR; tự build nếu thiếu artifact.
- `test-network.ps1`, `tools/NetworkingTestLauncher.java`: chạy JUnit Platform thật qua cache JUnit 5.10.2 khi không muốn tải Maven; không dùng assertions giả.
- `deploy/overleaf.service`: mẫu systemd chạy user `overleaf`, restart khi lỗi, shutdown timeout 30 giây.
- `deploy/overleaf.env.example`: loopback phù hợp SSH tunnel.
- `deploy/overleaf-public.env.example`: mẫu TLS/token/keystore cho public TCP.
- POM common thêm test dependency JUnit; POM root thêm Surefire 3.2.5; POM client thêm server dependency **test scope** để test integration hai đầu.
- `.gitignore`: thêm `dist/`, `.env`, `*.p12`, `*.jks`.

## 5. Luồng kết nối thực tế

```text
Client TCP/TLS
  -> AUTH(token)
  <- ACK(AUTHENTICATED)
  -> JOIN_ROOM(room, userId, displayName)
  <- FULL_SYNC + CHECKPOINT + USER_LIST_UPDATE
  -> TEXT_EDIT(OperationCodec JSON)
  -> M3 kiểm tra membership, identity và giới hạn
  -> M4 áp dụng/biến đổi và lưu lịch sử RAM
  <- ACK(operationId) về người gửi
  -> TEXT_EDIT cho các thành viên khác cùng phòng
```

Reconnect: tạo kết nối mới, AUTH, rồi gửi **RESYNC trực tiếp**, không cần JOIN trước. Nếu room còn trong RAM, M4 xử lý pending và gửi snapshot/checkpoint mới. Nếu server đã restart hoặc room không tồn tại, lỗi chứa `ROOM_UNKNOWN`; pending client không bị xóa.

Token là khóa chung cho nhóm, không ánh xạ tài khoản MongoDB. User ID được giữ cố định trên một kết nối sau join đầu; không được có hai session cùng ID trong cùng phòng. Server kiểm tra cả sender trong envelope và user trong CRDT operation, rồi dùng display name đã gắn với session.

## 6. Cấu hình và artifact triển khai

Mặc định: `127.0.0.1:8080`, TLS tắt, token rỗng, 64 client, read timeout 60 giây, queue server 32 frame, giới hạn tài liệu 100000 UTF-16 code units. Tối đa 256 phòng và 1000 pending operations trong một RESYNC request. Phòng rỗng vẫn giữ trong RAM đến khi JVM tắt.

Socket có reader/writer riêng; routing M3 dùng một khóa để giữ thứ tự xử lý/membership và kiểm tra giới hạn. Đây là cấu hình cho server nhóm nhỏ, chưa benchmark throughput networking trên VPS.

Không bật scheduler maintenance của M4 trong main server vì hiện nó xóa phòng trống và lịch sử. Socket timeout của M3 đóng kết nối và dọn presence. Client tự gửi heartbeat mỗi 15 giây; nên giữ timeout server 45–60 giây trở lên.

Artifact hiện có tại:

- `dist/overleaf-server.jar`: main `com.overleaf.server.ServerApp`.
- `dist/overleaf-network-demo.jar`: main `com.overleaf.client.net.NetworkDemo`.

JAR không chứa file MongoDB `config.properties`, keystore hoặc credential. Máy triển khai chỉ cần Java runtime 17+ cho backend hiện tại. Cần JDK đầy đủ để build; JBR IntelliJ trên máy hiện tại thiếu `jar.exe`, nên đã dùng `C:\Users\Admin\.jdks\ms-21.0.13.0.2`.

## 7. Kiểm chứng

Đã chạy Maven thật trên toàn bộ reactor để compile cả Common, Database, Server và GUI JavaFX. Đã chạy các test được chọn, không gọi test MongoDB:

| Test class | Số test |
| --- | ---: |
| `MessageCodecTest` | 5 |
| `NetworkingTest` | 9 |
| `NetworkClientTest` | 2 |
| `M4CollaborationTest` có sẵn | 6 |
| `OfflineResyncTest` có sẵn | 5 |
| `ProjectFileManagerTest` có sẵn | 8 |
| **Tổng** | **35** |

Kết quả: **35/35 pass**, không fail/error/skip trong lần chạy Maven chọn lọc. POM dùng JUnit 5.10.1; script cache dùng JUnit 5.10.2. Build networking dùng `--release 17`; runtime kiểm thử là JDK 21, chưa chạy riêng runtime Java 17.

Đã kiểm tra thêm artifact thật: khởi động `java -jar dist/overleaf-server.jar` trong tiến trình con, AUTH, nối hai client, truyền edit và kiểm tra snapshot của client vào sau đều thành công. Helper local `target/DeploymentSmoke.java` bị ignore, không thay thế test JUnit chính thức.

Phạm vi test: Unicode/nội dung dài, frame chia/gộp, EOF/truncation, độ dài/version/type/UTF-8/byte dư sai; socket JOIN rồi EDIT gửi liền nhau, snapshot, ACK, cursor, cách ly room, identity giả, loại message không được phép, duplicate user, tài liệu vượt giới hạn, token sai, idle timeout, client limit, shutdown, queue đầy, nối lại qua socket mới, RESYNC và replay idempotent; TLS với certificate tạm, truststore và hostname verification; test M4/IO hiện có.

Log Maven và report nằm ở `target/maven-network-tests.log` và `*/target/surefire-reports/`. Đây là artifact local bị ignore. Không tuyên bố đã chạy toàn bộ test database, compile LaTeX thật, test GUI bằng thao tác tay hoặc service Linux trên VPS.

Lệnh chạy lại:

```powershell
mvn "-Dtest=MessageCodecTest,NetworkingTest,NetworkClientTest,M4CollaborationTest,OfflineResyncTest,ProjectFileManagerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

## 8. Chạy thử nhanh

```powershell
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-21.0.13.0.2'
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-network.ps1
.\run-server.bat 8080
```

Hai terminal riêng, đặt JAVA_HOME tương tự và chạy Alice/Bob:

```powershell
.\run-network-demo.bat 127.0.0.1 8080 alice demo
.\run-network-demo.bat 127.0.0.1 8080 bob demo
```

Nhập `/insert 0 Hello` ở Alice. Để đưa lên VPS, làm theo [README.md §6](../README.md#6-triển-khai-lên-vps): loopback + SSH tunnel hoặc public TLS + token + PKCS12; có mẫu systemd kèm theo.

## 9. Việc tiếp theo và giới hạn còn thật sự tồn tại

| Phụ trách | Việc còn lại |
| --- | --- |
| **M3 + M5 + M4** | Nối tải/lưu tài liệu bền vững; xác định room → project/document và semantics restart. ACK hiện xác nhận áp dụng vào RAM, không phải đã ghi bền vững. |
| **M3 + M5** | Xác thực tài khoản, kiểm tra quyền từng project; token chung hiện chỉ kiểm soát quyền vào server nhóm. |
| **M1 + M4** | Kiểm thử GUI hai cửa sổ, chỉnh sửa đồng thời nhiều tình huống; xử lý UX khi đổi room có pending ops; cân nhắc tự reconnect và lưu offline queue xuống đĩa. |
| **M1 + M2** | Kiểm chứng compile trong đúng workspace, render PDF đầu ra thật nếu đó là yêu cầu cuối cùng. Canvas preview hiện tại không phải PDF renderer. |
| **Nhóm triển khai** | Cài Java trên VPS, chuyển artifact, cấu hình DNS/certificate/token/firewall hoặc SSH tunnel, cài systemd và đo networking trên môi trường thật. |

Không thay đổi compiler hoặc repository MongoDB của các thành viên khác. Một room hiện là một document trong M4; chưa có đồng bộ cây thư mục hoặc lựa chọn document theo file. File local của client vẫn là nơi cần lưu trước khi restart server.

JOIN mới thay editor bằng snapshot server, có thể là rỗng với phòng mới. Lưu file local trước khi đổi phòng; nếu đang phục hồi pending ops của cùng phòng thì dùng reconnect/RESYNC. Queue offline chỉ sống trong RAM tiến trình client, nên đóng ứng dụng chưa phải hành động lưu các pending ops bền vững.

## 10. Git và lưu ý cho phiên AI tiếp theo

Các thay đổi lần này đang ở working tree nhánh `main`, chưa commit/push. Không thực hiện merge/reset nhánh `protocol_networking`. Nếu chuẩn bị PR, tạo nhánh feature từ trạng thái hiện tại và kiểm tra lại diff.

Hai file `HD_CHAY_UNG_DUNG.md` và `overleaf_desktop_ver2.md` đã modified trước lần làm việc này vì người dùng yêu cầu giữ bản local khi cập nhật main. Không đưa chúng vào commit M3 một cách vô tình. Các file báo cáo/blueprint/copy Markdown untracked cũng có sẵn trước đó; không xóa hoặc ghi đè.

Khi tiếp tục, đọc `AI_Handoff.md` (nhất là mục 14), `README.md` và kiểm tra source thực tế. Không dựng lại stack `common.collab` của nhánh cũ, không đổi wire một phía sang Object Stream, không ghi credential vào JAR/Git và không đánh đồng backend đã chạy với persistence/database đã tích hợp.

## 11. Dọn file không còn dùng

Theo yêu cầu dọn dự án, đã xóa `build.bat` cũ vì dùng đường dẫn `src/com/...` không tồn tại trong cấu trúc Maven hiện tại, và 4 file `.gitkeep` trong thư mục Java đã có source.

Đã dọn thư mục `bin/` chứa 20 class cũ, `target/test-libs/` chứa 7 JAR trùng với bộ thư viện test mới, ba helper kiểm thử tạm `NetworkingTestRunner.java`, `NetworkClientSmoke.java`, `DeploymentSmoke.java` trong `target/`, cùng `DeploymentSmoke.class` tạm. Tổng cộng 36 file, khoảng 1 MiB. Kết quả smoke test trước khi dọn vẫn được ghi ở mục 7; helper tạm không còn trong working tree.

Giữ nguyên test chính thức, `tools/NetworkingTestLauncher.java`, `target/network-test-libs/`, cache Maven/JavaFX, output Maven trong từng module, hai JAR `dist/` và `main.tex` của người dùng. Không xóa tài liệu Markdown hoặc các bản sao lưu Markdown.

Build networking bằng `build-network.ps1`/`build-network.sh`; build Maven bằng `mvn compile`. Các script client cũ có thể tự tạo lại `bin/`, nhưng chúng chưa được cập nhật để dùng toàn bộ transport mới; lệnh mở JavaFX đã kiểm chứng dùng output Maven và JavaFX JAR trong `target/maven-cache/` như hướng dẫn trong cuộc trò chuyện.

## 12. Bản đồ folder, phần việc member và vai trò bàn giao

Đã thêm [README.md](../README.md) liệt kê từng file source/test/resource/script/tài liệu đang có, chức năng, member phụ trách và lệnh chạy. Các nhóm file tự sinh/dependency được mô tả theo thư mục để không trộn với source nghiệp vụ.

Phân công dưới đây dựa trên báo cáo và chức năng code đã đối chiếu. Không có tên/ID cá nhân đã xác nhận cho M1–M5; đây là trách nhiệm kỹ thuật, không phải kết luận tác giả Git của từng file. Năm vai trò được tổ chức trong bốn Maven module, không phải năm module độc lập.

| Folder/phần việc | Member chính | Đã code gì | Vai trò của member khác / việc bàn giao |
| --- | --- | --- | --- |
| `overleaf-client/.../client/ClientApp.java` | **M1** | Main mở JavaFX (đã bỏ Swing, xem mục 14) | M3 dùng luồng JavaFX hiện tại để nối transport; không đổi main |
| `overleaf-client/.../client/gui/` | **M1** | JavaFX editor, file tree, menu, compile controls, log, Canvas preview; xử lý sự kiện M2/M4 | **M3** thay Object Stream bằng NetworkClient, thêm token/TLS, nối presence thật và khóa/mở editor khi đồng bộ. M1 kiểm tra UX, remote edit và các tình huống đổi phòng |
| `overleaf-client/src/main/resources/styles/` | **M1** | Theme JavaFX `overleaf-theme.css` | Không dùng Tailwind; M3 không thay theme |
| `overleaf-client/.../client/compiler/` | **M2** | `LatexCompilerService`: ProcessBuilder, engine, timeout, log và callback | M1 gọi từ GUI; compile local, không chạy trong TCP server |
| `overleaf-client/.../client/io/` | **M2** | `ProjectFileManager`, `WorkspaceWatcher` | M1 nối file tree/editor; cần thống nhất workspace khi compile/watch |
| `overleaf-client/.../client/net/` | **M3** | `NetworkClient`, `NetworkDemo`; TCP/TLS, AUTH, queue FIFO, heartbeat và callback | M1 sử dụng transport; **M4** cung cấp operation/offline queue và semantics RESYNC |
| `overleaf-common/.../common/sync/` — model và operation | **M4** | `CRDTOperation`, `DocumentState`, `CollaboratorInfo`, `CursorPosition`, `SyncMessage`, `MessageType`, `OperationCodec`, `OfflineOperationQueue` | **M3** thêm AUTH trong enum và sửa online sau CHECKPOINT. M1/M3 phải dùng cùng message/payload contract |
| `overleaf-common/.../common/sync/` — transport | **M3** | `MessageCodec`, `TlsContexts` | M4 giữ model nghiệp vụ; M3 giữ wire protocol/TLS tương thích hai đầu |
| `overleaf-server/.../server/` ngoài `collaboration/` | **M3** | `ServerApp`, `ServerConfig`, `OverleafServer`, `ServerSession`; listener, session, AUTH, membership, kiểm tra yêu cầu và shutdown | Gọi engine M4; **M5** chưa được nối cho đăng nhập/persistence |
| `overleaf-server/.../server/collaboration/` | **M4** | Engine, room/document manager, lịch sử, thread pools, broadcaster, offline RESYNC và chống replay trùng | **M3** thêm entry point đồng bộ và giới hạn outbox/disconnect để phù hợp TCP. M3/M4/M5 cùng thiết kế việc lưu bền vững tiếp theo |
| `database/.../db/config/` | **M5** | Quản lý MongoClient/MongoDatabase và POJO codec | Người triển khai cung cấp cấu hình; không đưa credential vào JAR networking |
| `database/.../db/model/` | **M5** | User, Project, Document | M3/M4 cần mapping identity/room sang các ID này khi tích hợp |
| `database/.../db/repository/` và `repository/impl/` | **M5** | Hợp đồng và triển khai truy cập MongoDB cho ba model | Không sửa repository từ lớp GUI/network; gọi qua API/service phù hợp |
| `database/.../db/service/` | **M5** | `DatabaseService`: authenticate, load content, save content và getter repository | **M3 + M4** chưa gọi service trong đường đi TCP. Cần xác định thời điểm load/save, quyền dự án và nghĩa của ACK khi có persistence |
| `database/src/main/resources/` | **M5** | Mẫu/runtime config MongoDB và logging | Mẫu có thể bàn giao; credential local không được đưa vào README/handoff |
| `deploy/`, `build-network.*`, `run-server.bat`, `run-network-demo.bat` | **M3** | Build JAR, chạy local, mẫu env và systemd | Người quản trị cấu hình VPS/DNS/TLS; chưa chạy service Linux thực tế trong phiên code |
| POM root và từng module | **Cả nhóm theo phần phụ thuộc** | Cấu trúc Maven chung; M1 JavaFX, M5 MongoDB/logger, M3 Surefire/common test/client integration test dependency | Đổi dependency phải kiểm tra reactor; dependency Server trong Client hiện chỉ là test scope |
| Markdown gốc và `main.tex` | **Tài liệu nhóm / dữ liệu người dùng** | Báo cáo, blueprint, tiến độ M4, README, handoff và tài liệu local | Không coi là file build rác; giữ theo yêu cầu người dùng |

### 12.1 Test do từng phần quản lý

| Folder test | Member | Phạm vi |
| --- | --- | --- |
| `overleaf-common/src/test/.../sync/` | **M3** | Codec và regression queue online sau CHECKPOINT |
| `overleaf-client/src/test/.../io/` | **M2** | File manager |
| `overleaf-client/src/test/.../net/` | **M3** | API client/server, disconnect và TLS |
| `overleaf-server/src/test/.../server/NetworkingTest.java` | **M3** | Socket thật, membership, giới hạn, reconnect và queue |
| `overleaf-server/src/test/.../collaboration/` | **M4** | Logic collaboration/offline, helper giả lập và benchmark M4 |
| `database/src/test/.../db/` | **M5** | Kết nối MongoDB và repository |

### 12.2 Ranh giới khi tiếp tục sửa code

- **M1** giữ UI và UX, gọi API của M2/M3/M4; chuyển callback network sang JavaFX Application Thread.
- **M2** giữ compile/file I/O; tránh đưa ProcessBuilder hoặc filesystem logic vào socket handler.
- **M3** giữ kết nối, TLS, framing, validation/membership và vận chuyển message; thống nhất wire contract trước khi sửa model chung.
- **M4** giữ semantics operation, transform, checkpoint/ACK và RESYNC; phối hợp M3 để không phá thứ tự nhận/gửi.
- **M5** giữ schema/repository/service; phối hợp M3/M4 để hoàn thiện tài khoản/quyền dự án và lưu nội dung bền vững.

Lần cập nhật này chỉ thêm README và bổ sung bản đồ trách nhiệm vào handoff; không thay đổi code runtime hoặc chạy lại test nghiệp vụ. Kết quả 35/35 ở mục 7 là lần kiểm chứng code trước đó, không phải một lần chạy test mới cho thay đổi tài liệu.

## 13. Đồng bộ code M3/M4 sau review

Đã sửa code theo yêu cầu người dùng, không chỉ cập nhật tài liệu:

| Vấn đề | Sửa đổi |
| --- | --- |
| Late JOIN có clock quá thấp, chèn đầu Hello thành Hello> | SyncMessage thêm metadata Lamport; codec ghi wire v2. M4 gắn clock vào snapshot/checkpoint/ACK/edit. JavaFX/demo cập nhật clock trước khi tạo thao tác kế tiếp. Payload FULL_SYNC và CHECKPOINT không đổi. |
| Callback cũ ghi đè phòng mới | Thêm `ConnectionScope`; kiểm tra generation/room/client khi chạy callback trên FX thread, và đóng kết quả connect task đã cũ thay vì gán đè connection mới. |
| Pending hơn 1000 không RESYNC được | Thêm `ResyncTransfer`, RESYNC_BEGIN/PART/END. Server ghép đủ mới gọi M4 một lần, không tự rebase từng phần riêng. Tối đa upload 4 MiB, 30.000 operation và 128 phần; giới hạn document vẫn áp dụng. |
| ACK dồn khi RESYNC lớn | M4 gửi ACK_BATCH cho upload trên 64 operation; mỗi ACK_BATCH tối đa 2048 ID. Offline queue chỉ xóa các ID đã ACK; replay vẫn idempotent trong lịch sử còn giữ. |
| ROOM_UNKNOWN có tiền tố khác hợp đồng | Trả đúng payload `ROOM_UNKNOWN` qua TCP; validation error khác vẫn giữ tiền tố. |

### 13.1 File và vai trò cập nhật

- **M3/Common:** sửa `SyncMessage.java`, `MessageCodec.java`, `MessageType.java`; thêm `ResyncTransfer.java` và test. Codec đọc được frame v1 nhưng chỉ ghi v2, do đó phải rebuild/deploy cả hai đầu, không dùng client JAR cũ.
- **M3/Client:** thêm `net/ConnectionScope.java`, sửa `NetworkClient` để gửi các frame của một upload liên tiếp, và cập nhật clock trong demo.
- **M1 + M3/GUI:** cập nhật callback nhận dữ liệu, connect task và ACK_BATCH; guard kiểm tra tại thời điểm thực thi callback. Không sửa layout giao diện.
- **M4/Server:** gắn metadata clock và batch ACK trong `DocumentSyncManager`. Entry point đồng bộ và cơ chế khóa chung ở routing vẫn giữ như trước.
- **M4/Common:** queue xử lý ACK_BATCH, bỏ phép gán online trùng; test adapter M4 cũng cập nhật clock theo metadata như client thật.
- **M3/Server:** session ghép upload chỉ sau AUTH, lỗi/truncated upload không giao cho M4; tăng giới hạn operation và chuẩn hóa ROOM_UNKNOWN.

### 13.2 Kiểm chứng lần sửa mới

Maven compile toàn reactor và chạy chọn lọc **42/42 test pass**: MessageCodecTest 6, ResyncTransferTest 3, ConnectionScopeTest 1, NetworkingTest 10, NetworkClientTest 3, M4CollaborationTest 6, OfflineResyncTest 5, ProjectFileManagerTest 8. Log ở `target/sync-integration-tests.log`.

Test hồi quy bao gồm chèn đúng đầu tài liệu sau JOIN, kiểm tra callback trễ, upload 1.200 operation trên 1 MiB qua NetworkClient/ServerSession thật, replay không nhân đôi, batch ACK, chunk Unicode không cắt surrogate pair, upload thiếu/sai/vượt giới hạn, đọc frame v1 và mã lỗi chính xác. Test scope không truy cập MongoDB. Chưa kiểm thử GUI bằng thao tác tay hoặc triển khai VPS.

`task-m4.md` và `walkthrough-m4.md` đã thêm trạng thái tích hợp mới, vẫn giữ phần lịch sử. Không khôi phục các file Markdown đang bị người dùng xóa trong working tree, bao gồm `NETWORKING_M3.md` và các bản report/blueprint. Hợp đồng hiện tại nằm trong README §5 (`SYNC_PROTOCOL.md` đã gộp vào đó, xem mục 14).

Các giới hạn còn lại: dữ liệu và offline queue ở RAM, chưa nối MongoDB; routing server còn khóa chung; client chậm có thể bị ngắt bởi backpressure. Upload vượt 4 MiB/30.000 operation cần người dùng giữ bản local và xử lý phù hợp, không có cam kết đồng bộ vô hạn.

### 13.3 Artifact khi server cũ đang chạy

Không thể ghi đè `dist/overleaf-server.jar` vì Windows báo file đang được tiến trình sử dụng. Không tự dừng tiến trình của người dùng. Đã bổ sung `-OutputDirectory` cho `build-network.ps1` để xuất bản v2 vào `dist/v2/`. Dừng server cũ rồi chạy JAR v2, đồng thời khởi động lại GUI từ output Maven mới hoặc dùng demo v2. Các `.bat` hiện tại vẫn trỏ tới JAR ở root `dist/`, do đó dùng đường dẫn v2 trực tiếp trong lần cập nhật này. Restart server làm mất dữ liệu RAM như giới hạn đã ghi ở trên; lưu file local trước khi chuyển phiên.

## 14. Dọn dự án và ghi chú gộp từ tài liệu đã xóa (01/11/2026, nhánh `Network`)

### 14.1 Những gì đã dọn

- **Tài liệu:** chỉ còn `README.md` (hướng dẫn sử dụng/kỹ thuật) và file handoff này. Đã xóa `SYNC_PROTOCOL.md`, `DEPLOY_SERVER.md` (nội dung chuyển vào README §5–§6), `HD_CHAY_UNG_DUNG.md` (hướng dẫn M1 cũ, phần còn đúng như phím tắt đã vào README), `task-m4.md`, `walkthrough-m4.md` (phần còn giá trị ở 14.2–14.5).
- **Code chết:** xóa `gui/OverleafDesktopApp.java` (bản Swing cũ, không nối networking/M4) và cờ `--swing` trong `ClientApp`; xóa các import không dùng trong `DatabaseService`, `ProjectRepositoryTest`, `CRDTEngine`.
- **Script:** xóa `run-client-swing.bat`, `test-network.ps1` và `tools/NetworkingTestLauncher.java` (trùng với `mvn test`, chỉ chạy Windows và phụ thuộc cache JUnit 5.10.2). Viết lại `run-client.bat` dùng Maven (bản cũ không compile package `net`) và thêm `run-client.sh`.
- **POM:** bỏ cấu hình compiler plugin và version JUnit/Surefire lặp lại ở module client/server (đã khai báo ở parent). Giữ dependency `server → database` và `database → common` dù chưa gọi, vì đó là điểm tích hợp persistence đã lên kế hoạch.
- **Định dạng:** thêm `.editorconfig` và `.gitattributes` (LF trong repo, CRLF cho `.bat/.ps1`) để tránh lỗi "modified" do khác kiểu xuống dòng; xóa khoảng trắng cuối dòng và thêm newline cuối file. Không chạy formatter toàn bộ để tránh conflict với nhánh đang mở của các member.
- **Bảo mật:** `database/src/main/resources/config.properties` (nay là `persistence/...`) chứa mật khẩu MongoDB Atlas thật đã bị commit. Đã bỏ khỏi Git (`git rm --cached`, file local vẫn giữ) và sửa `.gitignore` thành `**/src/main/resources/config.properties` (pattern cũ chỉ khớp ở thư mục gốc). **Mật khẩu vẫn còn trong lịch sử Git → M5 phải đổi mật khẩu trên Atlas.** Sau khi pull, member khác cần tự tạo lại file từ `config.properties.example`.
- Kiểm chứng sau khi dọn: 42/42 test chọn lọc pass như trước.

### 14.1b Tổ chức lại module/package

Các mục 1–13 dùng đường dẫn cũ. Bảng đối chiếu (chỉ đổi vị trí/tên, không đổi logic; dùng `git mv` nên giữ lịch sử file):

| Cũ | Mới |
| --- | --- |
| module `overleaf-common`, `overleaf-server`, `overleaf-client`, `database` | `common`, `server`, `client`, `persistence` (artifactId trùng tên thư mục) |
| `common.sync`: MessageType, SyncMessage, MessageCodec, ResyncTransfer | `common.protocol` |
| `common.sync`: CRDTOperation, DocumentState, CollaboratorInfo, CursorPosition | `common.model` |
| `common.sync`: OperationCodec, OfflineOperationQueue | `common.sync` (giữ nguyên) |
| `common.sync.TlsContexts` | `common.security.TlsContexts` |
| `server`: OverleafServer, ServerSession, ServerConfig | `server.net` (ServerApp vẫn ở `server`) |
| `server.collaboration` | `server.collab` |
| `client.gui`, `client.io` | `client.ui`, `client.workspace` |
| `com.overleaf.db.*` | `com.overleaf.persistence.*` |
| script ở thư mục gốc | `scripts/` (tự `cd` về thư mục gốc) |

Tên JAR triển khai (`dist/overleaf-server.jar`, `dist/overleaf-network-demo.jar`), main class `com.overleaf.server.ServerApp`/`com.overleaf.client.net.NetworkDemo` và wire protocol không đổi. Member đang có nhánh mở cần rebase/merge sau khi thay đổi này vào `main`; Git nhận diện được phần lớn file đổi tên.

Việc tiếp theo đề xuất: tách `client/.../ui/OverleafFxApp.java` (~1.650 dòng) thành MainWindow, EditorPane, FileTreePane, PreviewPane, ConnectDialog và một controller cho collaboration.

### 14.2 Cơ chế offline/RESYNC của M4 (từ walkthrough-m4)

1. Client giữ mọi thao tác chưa ACK, nên cả thao tác gõ lúc offline lẫn thao tác đã gửi nhưng mất ACK đều không mất.
2. Client nhớ "điểm đã thấy" (operation ID cuối từ server), cập nhật qua CHECKPOINT khi JOIN và sau RESYNC.
3. Server `handleResync`: thao tác đã có trong lịch sử thì chỉ ACK lại (không áp dụng lần hai); các thao tác offline được transform qua phần người khác sửa sau điểm đã thấy rồi áp dụng và broadcast; nếu điểm đã thấy đã bị trim khỏi lịch sử thì dự phòng bằng Lamport. Cuối cùng gửi FULL_SYNC + CHECKPOINT.
4. Ví dụ đã test: "Hello World", Bob offline gõ "!" cuối và ">" đầu, Alice thêm "Big " và "<<" → mọi máy ra `<<>Hello Big World!`.
5. `SyncBroadcaster` dùng outbox riêng cho mỗi client; trước đây pool co giãn làm đảo thứ tự ACK/edit (có test 300 message).

### 14.3 Giới hạn đã biết (cần nói thật trong báo cáo)

- Sau RESYNC, văn bản client bị thay bằng bản server nên con trỏ có thể nhảy; GUI khóa editor khi `isResyncing()`.
- Dự phòng Lamport (lịch sử đã trim) không mất chữ nhưng có thể đặt sai vị trí so với ý người gõ.
- Hai người chèn cùng vị trí: thứ tự theo thứ tự server nhận. Xóa đoạn chồng nhau chưa test kỹ. ACK mất cùng lúc người khác sửa có thể làm lệch nhẹ vị trí thao tác kế tiếp.
- `OperationHistory.getOperationsSince` duyệt toàn bộ lịch sử phòng (≤ 10.000 op) mỗi lần sửa; chi phí tăng theo độ dài lịch sử, chưa đo riêng.
- Số liệu "< 50 ms trên LAN" trong báo cáo cũ **chưa từng được đo**; không dùng.

### 14.4 Benchmark M4 (`BenchmarkM4`)

Đo engine trong một tiến trình Java, không qua mạng; "độ trễ" là gửi → nhận ACK trong RAM. 4 phép đo: B1 một phòng 1–16 người; B2 1–8 phòng × 4 người; B3 một người gõ, 1–50 người nhận; B4 thời gian RESYNC theo số op offline/bỏ lỡ. Mỗi phép đo 1 lần khởi động + 3 lần chính thức, lấy trung vị, có kiểm tra không mất thao tác.

Số mẫu (VM 2 nhân, Java 21; chỉ có ý nghĩa trên máy đó): B1 ~2.100 → ~4.500 op/s, trung vị < 3 ms; B2 ~6.100 → ~12.000 op/s tổng, trung vị 0,3 → 2,5 ms; B3 50 người nhận ~0,3 ms; B4 1000 op offline + 1000 op bỏ lỡ ~65 ms. Cần chạy lại trên máy nhóm trước khi đưa vào báo cáo.

### 14.5 Việc còn mở (bổ sung cho mục 9)

- **M5:** đổi mật khẩu MongoDB Atlas (xem 14.1).
- **M4:** mục B "tua lại lịch sử" chưa bắt đầu; đưa phần M4 (offline, benchmark, giới hạn 14.3) vào báo cáo; chạy lại benchmark trên máy nhóm.
- **Có thể dọn tiếp nếu nhóm đồng ý** (chưa xóa vì là API của member khác): các method public chưa có nơi gọi — `CollaboratorInfo` (`changeRole`, `createViewer`, `isHost`, `markActive`, `markIdle`, `getJoinedAt`, `getLastActiveAt`), getter của `CursorPosition`, `DocumentState.getDocumentId/getLastModified`, `MessageType.fromString`, `CRDTEngine` (`applyOperations`, `generateInsert/Delete`, `getDocumentState/Version`), `CollaborationThreadPool.getSyncPoolSize/scheduleWithDelay`, `DocumentSyncManager.startMaintenance` (cố ý không bật vì xóa phòng trống), `OperationHistory` (`getHistorySize`, `getOperationsByUser`, `getRecentOperations`, `hasHistory`), `LatexCompilerService` (`isLatexmkAvailable`, `parseErrors`, `setTimeoutSeconds`).

## 15. Nối giao diện mới với backend (01/11/2026, nhánh `Network`)

Giao diện mới (LoginView, HomeView, ActivityHeatmap, UiSupport, theme) do DeepSeek dựng chỉ chạy với `DemoBackend` dữ liệu giả; editor luôn đọc/ghi `./main.tex` bất kể mở dự án nào. Đã nối thật:

| Phần | Trước | Sau |
| --- | --- | --- |
| Đăng nhập | Email bất kỳ + mật khẩu ≥ 6 ký tự đều vào; điền sẵn tài khoản demo | `LocalBackend`: tài khoản lưu trên máy, PBKDF2-SHA256 có salt, báo sai mật khẩu/email chưa có/email trùng; "Keep me signed in" tự đăng nhập lần sau; form điền sẵn email gần nhất |
| Dự án | 4 dự án giả ở `~/Overleaf/...` không tồn tại | Mỗi dự án là một thư mục trong `~/OverleafDesktop/projects/` (đổi bằng `OVERLEAF_HOME`); tạo/đổi tên/xóa (vào `.trash/`); thời gian sửa và số file đọc từ ổ đĩa |
| Hoạt động | Số ngẫu nhiên | Đếm số lần lưu thay đổi theo ngày, số lần compile thành công theo năm |
| Editor | Luôn dùng thư mục chạy app | `ProjectFileManager`, `WorkspaceWatcher`, cây file và compile trỏ vào thư mục dự án; bấm file trong cây để mở; tự lưu khi chuyển file/dự án/về Home/thoát; file sửa bên ngoài được nạp lại nếu chưa có thay đổi chưa lưu |
| Compile | Bỏ qua lựa chọn engine; luôn compile `.` | Dùng engine đã chọn (pdfLaTeX/XeLaTeX/LuaLaTeX), báo rõ khi thiếu TeX/timeout/lỗi LaTeX (dòng đầu tiên); "Open compiled PDF", "Clean temporary files" thật sự xóa file tạm trong `build/` |
| Phòng | Queue offline tạo ngay khi đăng nhập nên mọi thao tác gõ bị xếp hàng; JOIN phòng trống xóa trắng editor rồi autosave ghi đè `main.tex` | Queue chỉ tạo khi vào phòng; phòng trống được khởi tạo bằng `main.tex` local; phòng có nội dung khác → giữ bản local thành `main.before-<room>.tex`; đổi dự án thì rời phòng; trong phòng chỉ sửa được `main.tex` |
| Ảnh đại diện | Chỉ có chữ viết tắt | Bấm avatar (Home hoặc sidebar editor) → modal "Profile picture": chọn PNG/JPG/GIF ≤ 5 MB, cắt vuông giữa, hiển thị tròn; xóa ảnh. Lưu theo tài khoản ở `.app/avatars/` qua `ProfileGateway` |
| Hộp thoại | `Dialog`/`Alert` mặc định của JavaFX, lệch theme | `UiModal`: cùng kiểu modal "New project" (scrim, card trắng, ô icon, field, nút xanh/đỏ) cho Connect room, New file, đổi tên/xóa file và dự án, thông báo |

File: `ui/backend/LocalBackend.java` (mới, thay `DemoBackend` đã xóa), `UiModal.java` (mới), sửa `OverleafFxApp`, `LoginView`, `HomeView`, `UiSupport` (thêm icon, bỏ `styleDialog` không còn dùng), gateway interfaces, CSS (`btn-danger`, `modal-body`, `menu-danger`, `modal-icon-tile-danger`).

Kiểm chứng: **52/52 test** (42 cũ + 10 `LocalBackendTest`). `UiSmoke` (main class trong test, cần màn hình — lệnh ở README §3) điều khiển app thật: đăng ký, tạo dự án qua modal, gõ/lưu (kiểm tra ổ đĩa), compile (máy này không có TeX → báo "No LaTeX toolchain found", không treo), watcher, mở/tạo/đổi tên/xóa file, kết nối server thật qua modal Connect với client thứ hai (Bob) nhận/gửi edit và presence, rejoin có backup, đổi tên/xóa dự án, đăng xuất, sai mật khẩu, đăng nhập lại — tất cả PASS. `scripts/run-client.sh` khởi động không lỗi.

Chưa làm / cần nhóm quyết định:

- **M5 (MongoDB) chưa nối vào đăng nhập.** Cố ý: client nối thẳng Atlas thì mọi máy phải giữ credential DB. Đường đúng là client gửi AUTH tài khoản lên server M3, server gọi `DatabaseService`; khi có, viết một `AuthGateway`/`ProjectGateway` mới thay `LocalBackend`, view không phải sửa.
- Chưa compile LaTeX thật trên máy kiểm thử (không có TeX). Preview vẫn là Canvas tự vẽ, không phải PDF thật; PDF thật mở bằng "Open compiled PDF".
- Phòng mới chỉ đồng bộ `main.tex`; nhiều file trong phòng cần mở rộng giao thức (M3/M4).
- `OverleafFxApp` vẫn lớn (~2.600 dòng); đề xuất tách như mục 14.1b.

### 15.1 Gõ tiếng Việt và Windows

- **Lỗi:** gõ tiếng Việt (Telex) trên macOS bị thừa chữ trong mọi ô nhập. Nguyên nhân là JavaFX 17: khi bộ gõ đang ghép chữ (marked text), glass macOS vẫn sinh thêm KeyEvent nên ký tự gốc bị chèn kèm ký tự đã ghép. Đã sửa ở JavaFX 23 ([JDK-8301900](https://bugs.openjdk.org/browse/JDK-8301900)); không backport về 17/21.
- **Sửa:** `javafx.version` 17.0.8 → **23.0.2**. JavaFX 23 cần Java 21, nên chỉ module `client` biên dịch `release 21` (POM cha chuyển sang `<release>${maven.compiler.release}</release>`, mặc định 17). `common`/`server` vẫn Java 17, JAR server và triển khai VPS không đổi. Client có `maven-enforcer-plugin` báo rõ khi build bằng JDK < 21.
- **Đã kiểm:** 52/52 test, `UiSmoke` toàn bộ PASS trên JavaFX 23 (thêm bước tạo dự án tên tiếng Việt, gõ đủ các chữ có dấu, lưu đúng UTF-8, outline hiển thị đúng). Tên tài khoản tiếng Việt lưu/đọc đúng. Template dự án mới hỗ trợ tiếng Việt cho pdfLaTeX/XeLaTeX/LuaLaTeX (chưa compile thử vì máy không có TeX).
- **Windows:** tên thư mục dự án tránh tên thiết bị (`CON`, `NUL`…) và dấu chấm/khoảng trắng cuối; font logic (System/Serif/Monospaced) map sang Segoe UI/Times New Roman/Courier New đều có dấu tiếng Việt; `run-client.bat` đặt `chcp 65001`. **Chưa chạy thử trên máy Windows** — cần một member kiểm tra gõ Telex (Unikey/EVKey hoặc bàn phím Vietnamese của Windows) trên JDK 21+.


### 15.2 Lệnh và chi tiết đã rút khỏi README

README §3 chỉ giữ cách chạy. Chi tiết kỹ thuật ở đây:

- Dữ liệu (`~/OverleafDesktop`, đổi bằng `OVERLEAF_HOME` hoặc `-Doverleaf.home=`): `projects/<tên>/main.tex` (PDF ở `build/main.pdf`), `.app/accounts.properties` (PBKDF2-SHA256 + salt), `.app/settings.properties`, `.app/activity.properties`, `.app/avatars/`, `.trash/`.
- Phòng: người đầu tiên vào phòng trống đưa `main.tex` của mình lên; phòng đã có nội dung khác → bản local giữ thành `main.before-<room>.tex`; trong phòng không mở được file khác. Token/TLS xem README §4–§6.
- Demo console: `java -jar dist/overleaf-network-demo.jar 127.0.0.1 8080 alice demo` (lệnh `/insert 0 Hello`, `/delete 0 Hello`, `/cursor 2 7`, `/quit`).
- Smoke test GUI (cần màn hình, dữ liệu ở thư mục tạm, lưu ảnh từng màn hình):

  ```bash
  mvn -q -DskipTests install
  mvn -q -pl client dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
  java -cp client/target/classes:client/target/test-classes:$(cat client/target/cp.txt) com.overleaf.client.ui.UiSmoke /tmp/overleaf-shots
  ```

- `mvn test` không chọn lọc chạy cả test `persistence`: cần MongoDB và `persistence/src/main/resources/config.properties` (tạo từ `.example`, không commit). `BenchmarkM4.main()` đo engine M4, ghi `benchmark-results.md`.

## 16. Sửa lỗi khởi động Windows sau khi chuyển nhánh Network

Ngày 01/11/2026, trên nền `Network` commit `190cecb`:

- Kiểm tra còn đầy đủ **66 file source `.java`** trong bốn module mới `common`, `client`, `server`, `persistence` (bao gồm source test). Không có việc tự đổi `.java` thành `.class`.
- Bốn folder tên cũ `overleaf-client`, `overleaf-common`, `overleaf-server`, `database` chỉ chứa output `target/` từ nhánh trước, không còn source hoặc file được Git theo dõi trên nhánh hiện tại. Đã kiểm tra đường dẫn/không có symlink rồi chuyển nguyên trạng vào `target/legacy-build-output-20261101-095848/`, có manifest; không xóa source, `main.tex`, tài liệu hoặc dữ liệu người dùng.
- Tái hiện được lỗi launcher: `mvn is not recognized` và `JAVA_HOME` trống. Sửa `scripts/run-client.bat` để tự tìm JDK và Maven cài sẵn/đi kèm IntelliJ, sử dụng cache workspace và giữ cửa sổ báo lỗi. Launcher Windows dùng batch native, không phụ thuộc PowerShell/profile/execution policy. Thêm tham số `-BuildOnly` cho kiểm tra build mà không mở app.
- Lần compile đầu có lỗi test không nhìn thấy một số class client vừa được compile, trong khi chạy riêng test-compile lại thành công. POM parent bổ sung `fork=true` để tách tiến trình javac giữa module Java 17 và client Java 21.
- Đã chạy `scripts\run-client.bat -BuildOnly`: toàn bộ 5 bước reactor, gồm parent và 4 module, **BUILD SUCCESS** trên Windows với JDK Microsoft 21.0.13 và Maven của IntelliJ. Log: `target/network-client-build-native.log`.
- Đã kiểm tra khởi tạo JavaFX 23 thật: tạo Stage/scene đăng nhập thành công (`UI_STARTUP_OK`) bằng helper tạm, dữ liệu ở `target/ui-startup-check/`. Không dùng tài khoản/dự án thật, không sửa dữ liệu `~/OverleafDesktop`. Helper nằm trong `target/`, không phải source runtime.

Lệnh người dùng chạy từ thư mục gốc: `.\scripts\run-client.bat`. Mở source tại các module tên mới, không mở class trong `target/classes`. Bước kiểm tra Windows này xác nhận build và startup, chưa khẳng định đã kiểm tra thao tác Telex/Unikey thủ công hoặc chạy lại toàn bộ UiSmoke.

## 17. Cải thiện tiêu chí 1 — Compile local và kiểm chứng PDF thật

Ngày **01/11/2026**, nhánh **`feat/m1-gui-client`**, nền **`b89dbc5`**. Các thay đổi vẫn ở working tree, chưa commit/push. Giữ nguyên các thay đổi Windows launcher/POM/tài liệu có từ trước.

### 17.1 Đã triển khai

| Phần / vai trò | File | Thay đổi |
| --- | --- | --- |
| **M2 — Compiler** | `client/.../compiler/LatexCompilerService.java` | `Options` bất biến gồm đường dẫn latexmk và timeout; snapshot theo từng request để chỉnh Settings không đổi build đang chạy/chờ. Timeout mặc định vẫn 120 giây, **0 = không giới hạn**. Đường dẫn được truyền trực tiếp cho ProcessBuilder, không ghép shell command. Thêm thư mục executable vào PATH riêng của process con để dùng đúng engine cùng bộ TeX. |
| **M2 — Compiler** | Cùng file | `cancelCompile()` dừng cả process tree, loại build đang chờ, báo `EXIT_INTERRUPTED`, service có thể dùng lại. Giữ cơ chế chỉ giữ yêu cầu chờ mới nhất; không xóa `build/` mỗi lần compile, để latexmk reuse cache. Thông báo thiếu toolchain chỉ rõ đường dẫn và nơi cấu hình. |
| **M2 — Chẩn đoán** | `compiler/ToolchainDiagnostics.java` (mới) | Chạy `latexmk -v` và `--version` của ba engine, mỗi tool tối đa 8 giây; trả executable/version/lỗi. Không tự cài package hoặc sửa PATH hệ thống. Chạy trong worker nền từ UI. |
| **M1 — Settings** | `ui/SettingsView.java`, `ui/AppSettings.java` | Mục **Compilation/Biên dịch** riêng: chọn engine, nhập/chọn tệp latexmk, nhập timeout (0 hoặc số giây dương), lưu và kiểm tra toolchain đã lưu. Cấu hình lưu local qua backend hiện có. Đường dẫn có khoảng trắng được truyền nguyên một argument. |
| **M1 — Compile UI** | `ui/OverleafFxApp.java` | Nút **Stop** hiện lúc compile; chặn bấm compile trùng từ phím tắt/menu. Log executable/engine/timeout, hiển thị thời gian từ kết quả compile; hết timeout hướng dẫn vào Settings. |
| **M1/M2 — Lỗi dễ hiểu** | `LatexErrorExplainer.java`, `OverleafFxApp.java` | Gọi explainer cho lỗi đã parse và mã lỗi startup/timeout/cancel; ghi `[EXPLAIN]`, nguyên nhân và gợi ý vào Build output theo ngôn ngữ UI. Sửa rule file thiếu để không nhận “File ... already exists” là “not found”. Đây là bộ rule offline, không gọi AI qua mạng và không tự sửa source. |
| **Testing/build** | `LatexErrorExplainerTest.java`, `client/pom.xml` | Chuyển annotation/assertion từ TestNG/JUnit 4 sang JUnit 5; thêm regression file tồn tại; bỏ hai dependency compile-scope TestNG và JUnit 4 `RELEASE` không còn được dùng. Maven phát hiện đầy đủ 9 test explainer. |
| **Testing** | `compiler/LatexCompilerServiceTest.java` (mới) | 8 test, dùng tiến trình Java fixture để kiểm tra lỗi start, đủ ba flag engine, giữ auxiliary files, exit 0 thiếu PDF, timeout snapshot, kill process con, Stop khi không giới hạn, discard/coalesce queue và chẩn đoán thiếu tool. Fixture không phải compiler TeX thật. |
| **Smoke thật** | `compiler/CompilerSmoke.java`, `ui/CompilerUiSmoke.java` (mới, test source) | Hai main chạy tường minh: PDF/cache/ba engine/TikZ/template tiếng Việt; và UI Settings/compile/explainer/Stop. Không chạy tự động bởi Surefire, cần toolchain thật; UI smoke cần desktop. Chỉ dùng thư mục dữ liệu thử mới. |

### 17.2 Toolchain trên máy kiểm tra

Không tìm thấy latexmk/ba engine trên PATH ban đầu. Đã tải **TinyTeX-1 v2026.03.02 Windows** từ [release chính thức](https://github.com/rstudio/tinytex-releases/releases/tag/v2026.03.02), xác nhận SHA-256 archive:

```text
af5fda147cfdccd071f9a8d3dbec3b6c30a1f6d77ff30d8b42e449b4e5d1c147
```

Giải nén vào **`D:\Overleaf-app\target\TinyTeX`** (bị Git ignore). Không sửa PATH hệ thống, registry hoặc dữ liệu thật `~/OverleafDesktop`. Cập nhật tlmgr của bộ portable; thêm `vntex`, `babel-vietnamese`, `babel-english`/`hyphen-english`, `pgf` và dependency cần thiết. Đây là toolchain local phục vụ kiểm tra, không được đóng vào JAR hay cam kết có sẵn trên máy member khác. Không chạy `mvn clean` ở root nếu cần giữ toolchain/cache/backup trong `target/`.

Version đã chạy: latexmk **4.87**, pdfTeX **1.40.29**, XeTeX **0.999998**, LuaHBTeX **1.24.0**, TeX Live **2026**. Bản portable không chứa mọi package TeX; dự án khác có thể cần cài bổ sung.

### 17.3 Kết quả kiểm chứng

**Maven: 72/72 đạt, 0 fail/error/skip** = 55 test hiện có trên nhánh + 8 compiler mới + 9 explainer đã được JUnit phát hiện. `LocalBackendTest` hiện 13 test, nên con số 52 trong README cũ đã lỗi thời. Test MongoDB không được chọn. Log: `target/compile-improvements-tests.log`.

Smoke thật qua chính `LatexCompilerService`:

- Ba engine đều sinh file có header PDF thật; thành công với tài liệu có công thức/tham chiếu, TikZ 100 nét và template của app có chữ tiếng Việt.
- Không sửa nội dung rồi compile lần hai: latexmk báo `Nothing to do`/`up-to-date`, xác nhận reuse cache. Sau sửa nhỏ vẫn compile thành công.
- Kết quả một lần chạy trên máy hiện tại (tài liệu nhỏ, một số bước chạy song song với UI smoke):

| Engine | Lần đầu (ms) | Không sửa (ms) | Sửa nhỏ (ms) |
| --- | ---: | ---: | ---: |
| pdfLaTeX | 486 | 246 | 358 |
| XeLaTeX | 3409 | 202 | 407 |
| LuaLaTeX | 5144 | 208 | 745 |

Đây là thời gian wall-clock end-to-end từ service, **không phải benchmark so với Overleaf**, không phải thống kê p50/p95 hoặc đo CPU/RAM. Chưa kiểm tra hình thức từng glyph/trang PDF bằng mắt. Dữ liệu/log: `target/compile-smoke-02/`, bảng `results.md`, `target/real-compile-smoke.log`.

**JavaFX smoke trên Windows đạt** (`COMPILER_UI_SMOKE_OK`): timeout âm bị từ chối; timeout 0 lưu được; diagnostics chạy nền; compile UI sinh PDF; source cố ý sai `\texbf` hiện `[EXPLAIN] Lệnh không tồn tại` trong Build output; Stop dừng vòng lặp TeX khi timeout không giới hạn và mở lại nút Recompile. Đã xác nhận lại trên bản cuối, dữ liệu riêng ở `target/compiler-ui-smoke-02`, log `target/compiler-ui-smoke.log`. Không chạy lại toàn bộ `UiSmoke` cũ.

Sau bổ sung kiểm tra đường dẫn không hợp lệ (bao gồm NUL) ngay trong `Options`, chạy lại chọn lọc **17/17 compiler/explainer test đạt**; log `target/compile-final-tests.log`. Cấu hình lưu từ UI chuẩn hóa đường dẫn absolute để không phụ thuộc working directory của project.

Sandbox từng gây `AccessDeniedException` khi javac đóng JDK `lib/ct.sym`; chạy kiểm tra ngoài sandbox đã giải quyết, không phải lỗi Java source. Các lần smoke thất bại trước khi thêm đủ package được giữ ở `target/compile-smoke-01` để truy vết, không dùng làm kết quả thành công.

### 17.4 Cách dùng và chạy lại

1. Mở app: `.\scripts\run-client.bat`.
2. Mở Settings → **Compilation / Biên dịch**. Trên máy hiện tại chọn **`D:\Overleaf-app\target\TinyTeX\bin\windows\latexmk.exe`**. Trên máy khác chọn latexmk của TeX Live/MiKTeX, hoặc để trống nếu đã có trên PATH.
3. Nhập timeout, **0** nếu muốn không giới hạn; bấm **Save compiler settings** rồi **Check saved toolchain**. Đổi engine qua Settings hoặc toolbar.
4. Về editor, F5/Ctrl+Enter để compile; bấm **Stop** nếu cần. Xem đường dẫn PDF và thời gian trong Build output/status bar.

Chạy test bằng Maven đã cài:

```powershell
mvn "-Dtest=LatexCompilerServiceTest,LatexErrorExplainerTest,MessageCodecTest,ResyncTransferTest,ConnectionScopeTest,NetworkingTest,NetworkClientTest,M4CollaborationTest,OfflineResyncTest,ProjectFileManagerTest,LocalBackendTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Main smoke cần build test-classes trước (`mvn -DskipTests test`), classpath gồm `client/target/classes`, `client/target/test-classes`, `common/target/classes`; UI thêm `server/target/classes` và JavaFX module path. Truyền `<absolute-latexmk-path> <new-output-directory>` cho `com.overleaf.client.compiler.CompilerSmoke` hoặc `com.overleaf.client.ui.CompilerUiSmoke`. Phải dùng thư mục output mới mỗi lần để không làm sai phép đo cold build. Các `.cmd` trong `target/` chỉ là helper riêng máy này, không phải script phát hành.

### 17.5 Giới hạn còn lại của tiêu chí Compile

- Chưa có Typst, tùy chọn shell-escape trong UI, quản lý nhiều phiên bản TeX tự động hoặc profiling CPU/RAM. Dùng executable path để chủ động chọn bộ TeX đã cài.
- PDF preview trong app vẫn là Canvas mô phỏng; PDF thật mở ngoài app. Cải thiện tiêu chí preview/SyncTeX là phần việc riêng, chưa làm trong mục này.
- Thời gian thực tế tùy máy/package/document. Không tuyên bố app nhanh hơn Overleaf, compile vô hạn theo mặc định hoặc tận dụng toàn bộ CPU.
- Error Explainer dựa vào rule và lỗi dạng file:line; lỗi log không có định dạng này có thể chỉ nhận giải thích chung theo exit code. Không phải AI chẩn đoán mọi lỗi.

## 18. Sửa lỗi “Không chạy được latexmk” từ ảnh kiểm thử

Ngày **01/11/2026**, cùng nhánh `feat/m1-gui-client` và working tree mục 17.

- Ảnh hiển thị thông báo startup cũ, không có dòng log executable/timeout và giải thích mới. Đây là lỗi khởi động compiler, không phải lỗi công thức LaTeX. Không tự kill các tiến trình Java đang chạy của người dùng; phải lưu file rồi khởi động lại app sau khi build.
- Thiếu sót trong bản mục 17: khi Settings để trống, chỉ gọi `latexmk` trên PATH; bộ portable trong `target/TinyTeX` không tự được tìm thấy. Các smoke trước đã truyền đường dẫn tường minh nên chưa bao phủ trường hợp này.
- **M2:** thêm `compiler/LatexToolchain.java`: ưu tiên đường dẫn đã cấu hình → `OVERLEAF_LATEXMK` → executable trên PATH → portable trong workspace/ancestor (hỗ trợ chạy từ module client) → các thư mục TinyTeX/MiKTeX phổ biến. Không ghi cấu hình cá nhân, không tải package tự động hoặc sửa PATH hệ thống.
- **M1:** `AppSettings.compileOptions()` dùng resolver; Settings cập nhật mô tả “để trống để tự tìm”. **M3/build:** launcher Windows thêm bin portable vào PATH của riêng tiến trình nếu có.
- Thêm **4 `LatexToolchainTest`** kiểm tra ưu tiên cấu hình/PATH, environment override, không có toolchain và tự tìm portable từ module client. `CompilerUiSmoke` đổi sang **không lưu đường dẫn** và kiểm tra tự nhận, compile UI, giải thích tiếng Việt và Stop. Kết quả **76/76 test đạt**, smoke UI đạt; dữ liệu thử mới `target/compiler-ui-smoke-03`, log `target/compiler-ui-smoke.log`.
- Đã đọc source test của người dùng để tái hiện: chỉ sao chép `Untitled project/main.tex` từ thư mục dự án local sang `target/user-compile-repro/`, không sửa file gốc. Tài liệu dùng nhiều package hơn mẫu nhỏ ở mục 17. Thêm vào TinyTeX portable: `setspace`, `fancyhdr`, `enumitem`, `multirow`, `pgfplots`, `algorithms`, `algorithmicx`, `mathtools`, `caption`, `listings`, `microtype` (các package phổ biến khác đã có).
- Bản sao nguyên nội dung trong thư mục mới **`target/user-compile-repro-02`** đã compile thành công bằng **pdfLaTeX**, qua `LatexCompilerService` và resolver với cấu hình trống. Log `target/user-compile-replay.log`: `USER_DOCUMENT_PDF_OK`, **6741 ms**; PDF `target/user-compile-repro-02/build/main.pdf`. Đây là kết quả trên chính tài liệu người dùng, không chỉ mẫu smoke nhỏ. Chưa kiểm tra hình thức từng trang bằng mắt.
- Khi vừa cài package sau một lần lỗi, latexmk có thể giữ kết quả lỗi cũ trong cache dù báo up-to-date. Lần tái hiện dùng thư mục copy mới để tách cache; nếu gặp trong app, dùng menu **Clean temporary files** rồi compile lại. Không tự xóa output/dữ liệu của người dùng.

Cách dùng trên máy hiện tại: lưu file, đóng cửa sổ app cũ, chạy `.\scripts\run-client.bat`. Có thể để đường dẫn latexmk trong Settings trống, app tự nhận `D:\Overleaf-app\target\TinyTeX\bin\windows\latexmk.exe`. Kiểm tra Build output phải có `[BUILD] latexmk: ...` và timeout. Nội dung preview Canvas vẫn là mô phỏng; xem PDF compile thật bằng menu mở PDF hoặc file `build/main.pdf`.

## 19. Preview đúng PDF và export cùng kết quả compiler

Ngày **01/11/2026**, theo yêu cầu tránh làm người dùng nhầm preview sai với lỗi LaTeX. Cùng nhánh `feat/m1-gui-client`, chưa commit/push. Mục này thay thế giới hạn Canvas ở các mục trước.

### 19.1 Hành vi sản phẩm

- **Preview đọc PDF thật từ lần compile thành công**, giữ nguyên nội dung/trang/kích thước/rotation/font trong PDF; bỏ hoàn toàn lớp parser/vẽ LaTeX `PdfCanvasPane` (~700 dòng) khỏi `OverleafFxApp`.
- Dùng [Apache PDFBox 3.0.8](https://pdfbox.apache.org/3.0/getting-started.html), chỉ thêm dependency vào client. Render ở worker nền, một trang mỗi lần, có bộ đếm trang, trước/sau, zoom và Fit. Không còn giới hạn 50 trang của bản Canvas; không nạp ảnh của toàn bộ tài liệu cùng lúc.
- **Export PDF…** trên preview hoặc menu `⋯` chép nguyên bytes của PDF thành công đang xem; không chụp màn hình, không tái dựng PDF từ ảnh hoặc parser. File xuất giữ chất lượng/vector/font của kết quả compiler.
- Thông báo **xanh**: PDF đã biên dịch thành công, có thể xuất. **Vàng**: nguồn đã đổi/đang compile/bản cũ. **Đỏ**: compile hoặc render thất bại. Nếu còn PDF thành công trước đó thì tiếp tục cho xem, nhưng ghi rõ là bản cũ và khóa xuất.
- Mới mở/đổi dự án phải compile để xác nhận PDF; không mặc nhiên tin file `build/main.pdf` cũ. Sửa bất kỳ file editor, đổi engine, sửa từ mạng hoặc thay dependency trên đĩa đều làm preview cũ. Thành công của compile thuộc workspace trước không được gán sang workspace mới.
- Lỗi compile không được đưa PDF tạm/partial vào preview hoặc Export. Menu mở PDF và đường mở `build/main.pdf` trong cây file cũng dùng kiểm tra phiên bản hiện tại; PDF đầu vào khác trong cây file vẫn mở bình thường.

### 19.2 File và trách nhiệm

| Vai trò | File | Nội dung |
| --- | --- | --- |
| **M2 — artifact PDF** | `client/.../compiler/PdfSnapshot.java` (mới) | Giữ snapshot byte bất biến, đọc/validate bằng PDFBox, render đúng page crop box/rotation, xuất bytes bằng temp + atomic move khi filesystem hỗ trợ. Compile sau ghi đè file build không làm đổi snapshot đang xem. |
| **M2 — tính mới của nguồn** | `compiler/ProjectInputs.java` (mới), `LatexCompilerService.java` | SHA-256 tên/nội dung file input trong project, loại `build/` và `.git/`. Compiler gắn fingerprint trước compile vào `CompileResult`; UI so lại khi nhận PDF. Không chỉ dựa vào timestamp/kích thước. I/O/hash chạy nền. |
| **M1 — viewer** | `ui/PdfPreviewPane.java` (mới) | Viewer PDF thật, điều khiển trang/zoom, trạng thái và nút Export. Render queue giới hạn, generation bỏ kết quả render cũ; render lỗi hiển thị lỗi và khóa xuất. |
| **M1 — điều phối** | `ui/OverleafFxApp.java` | Bỏ nested Canvas/parser, nối compile thành công → snapshot → viewer; revision + workspace generation; stale/error state; save dialog Export PDF, kiểm tra fingerprint và FX state trước xuất; mở external viewer từ snapshot tạm thay vì file build có thể bị ghi đè. |
| **M2 — watcher** | `workspace/WorkspaceWatcher.java` | Theo dõi thêm hình/font/PDF dữ liệu đầu vào, `.csv/.dat`, style bibliography, `.lua/.tikz`…; vẫn bỏ qua thư mục build/ẩn. Tạo/đổi tên/xóa file từ UI cũng invalidates PDF. |
| **M1/build** | `client/pom.xml` | PDFBox 3.0.8, transitively pdfbox-io/fontbox/commons-logging. Không thêm dependency PDFBox vào common/server hoặc wire protocol. |
| **Testing** | `compiler/PdfSnapshotTest.java` (mới) | 5 test: kích thước/trang quay/pixel thật; export byte-identical dù compiler ghi đè output; PDF/page/zoom sai; fingerprint bỏ qua build nhưng nhận sửa dependency cùng size/mtime; rename/delete binary asset. |
| **Testing UI** | `ui/PdfPreviewUiSmoke.java` (mới) | Main cần desktop + TeX thật, dùng bản sao source và home mới. So mẫu pixel của các trang preview với PDF renderer; file xuất giống byte-for-byte, stale/error recovery, đổi project. Không tự chạy bằng Surefire. |

Export kiểm tra lại input trên đĩa và revision trên FX thread trước ghi. Lỗi ghi file (quyền/disk full) báo riêng, không tự kết luận nguồn sai. Bộ kiểm tra input chỉ bao phủ file trong project; không theo dõi tự động thay đổi TeX distribution/font hệ thống hoặc dependency nằm ngoài project. Nếu thay các thành phần đó thì compile lại.

### 19.3 Kiểm chứng

- Maven chọn lọc **81/81 đạt** = 76 test mục 18 + 5 `PdfSnapshotTest`; không gọi MongoDB. Log `target/pdf-preview-tests.log`.
- Smoke JavaFX trên **bản sao đúng tài liệu test người dùng**, compile bằng pdfLaTeX ra **7 trang**. Mẫu pixel của trang 1–4 khớp với render PDF; ảnh minh chứng **`target/pdf-preview-ui-02/preview.png`** là trang 4 có công thức/bảng đúng layout.
- File **`target/pdf-preview-ui-02/exported.pdf`** giống byte-for-byte với output compiler được preview; có kiểm tra `Files.mismatch(...) == -1`.
- Chỉnh source khóa xuất ngay; cố xuất stale PDF không sinh file; compile lỗi giữ snapshot thành công cũ, không nhận PDF partial; sửa lỗi và compile lại thay snapshot; đổi project xóa snapshot. Kết quả `PDF_PREVIEW_UI_SMOKE_OK`, log `target/pdf-preview-ui.log`.
- Đã xem ảnh chụp UI trang có công thức/bảng. Không tuyên bố chứng minh mọi PDF/font hoặc mọi lỗi nội dung LaTeX: viewer thể hiện kết quả compiler; không kiểm tra đúng/sai về mặt khoa học của công thức. Các cảnh báo LaTeX vẫn cần đọc trong Build output.
- Source/dữ liệu gốc của người dùng không bị sửa; helper, ảnh, PDF thử và cache đều ở `target/` bị ignore. Chưa chạy toàn bộ `UiSmoke` cũ; UI PDF có smoke riêng như trên.

### 19.4 Cách sử dụng và chạy lại

Lưu file và đóng app cũ, chạy `.\scripts\run-client.bat`, mở dự án rồi F5/Ctrl+Enter. Chờ trạng thái preview xanh, dùng `‹`/`›` chuyển trang, zoom/Fit; **Export PDF…** để chọn file đích `.pdf`. Sau sửa source, compile lại trước khi xuất. Build output/preview phân biệt thất bại với bản đã compile thành công.

Chạy test: lệnh ở README có thêm `PdfSnapshotTest`. Chạy smoke: build test-classes, classpath gồm client/main + client/test + common + server + dependency PDFBox và module-path JavaFX; main `com.overleaf.client.ui.PdfPreviewUiSmoke <source.tex> <new-home>`. Helper riêng máy này: `target/run-pdf-preview-ui.cmd`; phải đổi home mới mỗi lần.

Giới hạn hiện tại: preview raster cho việc hiển thị (PDF export vẫn nguyên bản vector), một trang mỗi lần; ảnh render giới hạn khoảng 12 triệu pixel và scale tối đa 3 để tránh hết bộ nhớ; snapshot PDF tối đa 128 MiB, vượt giới hạn báo lỗi rõ ràng. Chưa có chọn/copy text trên PDF, annotation hoặc SyncTeX hai chiều; đó là phần phát triển tiếp, không dùng parser Canvas thay thế.

## 20. Sửa nguyên nhân chữ tiếng Việt lỗi khi đổi engine

Ngày 01/11/2026. Tái hiện trên bản sao tài liệu người dùng: pdfLaTeX sinh PDF đúng; XeLaTeX và LuaLaTeX đều trả exit 0 nhưng có hàng loạt `Missing character` cho `ệ`, `đ`, `ư`… vì tài liệu dùng cố định `inputenc`, `fontenc[T5]`, `lmodern` của luồng pdfLaTeX. Xe/Lua đọc UTF-8 trực tiếp; cần nhánh Unicode với fontspec. Nguồn: [LaTeX Project về inputenc](https://www.latex-project.org/news/latex2e-news/ltnews.pdf), [fontspec](https://ctan.org/pkg/fontspec).

- Không tự sửa tài liệu gốc trong `~/OverleafDesktop`. Bản sao đã sửa nằm ở **`target/vietnamese-fixed/main.tex`**: `iftex` tách pdfLaTeX (UTF-8/T5/lmodern) với Xe/Lua (`fontspec`, Latin Modern Roman OpenType cùng font bold/italic). Chọn font theo **tệp `.otf`** để portable Windows không phụ thuộc font đã đăng ký trên hệ điều hành. `literate` trong listings có `{ạ}{{a}}1` làm mất dấu do chính mapping; bản sao đổi thành `\d{a}`.
- **M1/M2:** `LocalBackend.template()` sinh preamble nhánh engine, bổ sung chọn font OpenType theo tệp. Chỉ dự án mới nhận template mới; tài liệu cũ cần sửa khai báo riêng.
- **M2:** `LatexCompilerService` thêm `EXIT_MISSING_GLYPHS = -5`; dù exit 0, nếu console hoặc file TeX `.log` có `Missing character:` thì không gọi onSuccess. Kiểm tra `.log` cả khi latexmk dùng cache. Các cảnh báo thường như hyphenation không bị coi là mất ký tự.
- **M1:** status báo PDF thiếu ký tự; đường onError giữ preview cũ có cảnh báo và khóa Export. `LatexErrorExplainer.explainExit` thêm hướng dẫn T5/fontspec theo engine, tiếng Việt/Anh.
- Thêm 2 compiler regression và 1 explainer regression: tổng chọn lọc từ 81 → **84/84 đạt**; log `target/pdf-preview-tests.log`. Không tính smoke thật vào số JUnit.
- Thêm main test **`compiler/VietnamesePdfSmoke.java`**: compile bản sao qua ba engine, kiểm tra không mất glyph, trích xuất PDF rồi chuẩn hóa NFC, kiểm tra các chuỗi `Tài liệu`, `Định lý`, `Tiếng Việt`; render trang 1 để xem.
- Trong quá trình kiểm tra có crash native XeTeX Windows khi chạy nền. Các thử nghiệm đổi launcher/wrapper không giải quyết ổn định nên **đã bỏ khỏi source runtime**. Bộ TinyTeX portable được cập nhật `xetex` cùng các dependency LaTeX/kernel, chạy lại font cache; regenerate format LuaLaTeX/pdfLaTeX để tránh `Mismatched LaTeX support files`. Chỉ sửa bộ TeX trong `target/`, không đổi cài đặt toàn hệ thống. Log cập nhật `target/xetex-update.log`, format `target/portable-formats.log`.
- **Kiểm chứng cuối cùng sau cập nhật:** `VietnamesePdfSmoke` chạy qua chính service, cả pdfLaTeX/XeLaTeX/LuaLaTeX đều đạt: **7 trang, 0 missing glyph, đủ 3 chuỗi tiếng Việt có dấu**. Kết quả `target/vietnamese-fixed-check.log`; PDF, text trích xuất và ảnh trang 1 nằm trong `target/vietnamese-fixed-check-09/{PDFLATEX,XELATEX,LUALATEX}/`. Đây là kiểm chứng bản source đã sửa; bản gốc dùng T5 cố định vẫn cần đổi preamble.

Nếu thấy lỗi tiếng Việt ở tài liệu cũ, sửa preamble theo bản sao tương thích; việc đổi compiler trong toolbar không tự chuyển đổi source LaTeX. Không đánh đồng “có file PDF” hoặc exit 0 với “không rơi ký tự”.

## 21. Outline có thể kéo lên/xuống thay vì chiều cao cố định

Ngày 01/11/2026, theo yêu cầu mới của người dùng.

- **M1:** `OverleafFxApp.createLeftPanel()` đặt cây file và Outline trong **SplitPane dọc** (`#file-outline-split`). Kéo thanh ngang lên để mở rộng Outline, kéo xuống để thu lại; cây file và Outline cuộn riêng. Bỏ `prefHeight=180`, `minHeight=120`, `maxHeight=260` cũ; giữ min vùng Outline khoảng 80 px và vùng file khoảng 100 px để tránh mất hẳn nội dung.
- **M1 — UI:** thêm ký hiệu `↕`, tooltip hướng dẫn kéo; CSS `.sidebar-split` có thanh kéo đủ dày và highlight khi hover.
- **M1 — Settings:** `AppSettings.OUTLINE_SPLIT` lưu tỷ lệ sau **mouse release**, không ghi file mỗi pixel khi drag. Khôi phục khi tạo sidebar, clamp giá trị và bỏ NaN. Listener không gọi lại applyEditorSettings khi lưu divider, nên không reset engine/cỡ chữ do thao tác resize.
- **UI smoke thật:** `ui/OutlineUiSmoke.java` dùng home riêng, mở app JavaFX, thay divider và kiểm tra chiều cao tăng/giảm + preference lưu + engine không bị đổi. Kết quả `OUTLINE_UI_SMOKE_OK`, số đo cửa sổ kiểm tra: **241 → 422 → 133 px**, saved ratio ~0.3002. Log `target/outline-ui.log`, dữ liệu `target/outline-ui-02`.
- Kiểm chứng dựa trên layout/control JavaFX thật và sự kiện thả chuột; chưa phải test Robot kéo bằng tọa độ màn hình. Nút và giới hạn chiều cao đều do SplitPane chuẩn xử lý.

Sử dụng: lưu file, đóng app cũ, chạy `.\scripts\run-client.bat`; ở tab **Files**, kéo thanh ngang ngay trên tiêu đề **OUTLINE**. Tỷ lệ được lưu local để dùng lại.

## 22. Menu tạo dự án, nhập tài liệu và thư mục lưu tùy chọn

Ngày **02/11/2026**, nhánh `feat/m1-gui-client`, nền `b89dbc5` cùng các thay đổi local ở mục 17–21. Chưa commit/push. Đây là chức năng desktop local; không phải API lưu dự án lên MongoDB hoặc server cộng tác.

### 22.1 Đã triển khai và cách dùng

- **Dự án mới / New project** mở menu: dự án trống, nhập ZIP, Word `.docx`, Markdown, kho GitHub công khai, dự án ví dụ, thêm mẫu và thêm đơn vị. Các nhãn, hướng dẫn, lỗi trong luồng mới có tiếng Anh/Việt theo ngôn ngữ UI.
- **Thêm mẫu dự án** có bài báo, sách, thư trang trọng, bài tập, poster, thuyết trình, báo cáo, CV và luận văn; **Xem tất cả** mở bộ chọn. Các mẫu tạo file LaTeX thật với class/bố cục phù hợp, hỗ trợ tiếng Việt và ba engine. Dự án ví dụ có `main.tex`, `sections/introduction.tex`, `references.bib` và citation thật.
- Hộp thoại tạo/nhập cho sửa trực tiếp đường dẫn hoặc dùng DirectoryChooser. Đường dẫn là **thư mục cha**; app tạo thư mục con theo tên dự án, tự thêm hậu tố khi trùng tên, không ghi đè dự án cũ. Có thể cuộn nội dung hộp thoại khi cửa sổ thấp.
- **Cài đặt → Dự án** hoặc **Dự án mới → Thư mục dự án mặc định…** đổi vị trí dùng cho lần tạo tiếp theo. Các dự án cũ không bị chuyển theo và vẫn có trong danh sách.
- Trang chủ → nhấp phải dự án → **Đổi thư mục lưu…** chuyển dự án hiện có. Sao chép qua staging ở đích, đối chiếu bytes từng file, rồi giữ thư mục nguồn trong `<thư mục cha cũ>/.overleaf-moved-backups/<tên>-<UUID>`. Cách này không cần rename trực tiếp giữa hai ổ đĩa; kiểm thử hiện tại chưa xác nhận bằng hai ổ vật lý khác nhau. Không tự dọn bản phục hồi.
- **Thêm đơn vị…** lưu tên trường/cơ quan local, điền vào tác giả của template mới, escape ký tự LaTeX. Không liên kết tài khoản với tổ chức trên Overleaf web.

### 22.2 File, folder và vai trò bàn giao

| Nhóm / member phụ trách | File và phần việc mới |
| --- | --- |
| M1 — `client/.../ui/` | `HomeView`: menu, form, template gallery, chọn/chuyển thư mục, affiliation, task nền có hủy và lỗi song ngữ; `SettingsView`: trang Dự án; `AppSettings`: truy cập ProjectGateway; `UiModal`: tooltip đóng bản dịch; CSS menu và vùng cuộn |
| M1 — tích hợp workspace | `OverleafFxApp`: nhớ dự án bằng đường dẫn đầy đủ thay vì chỉ tên; cập nhật watcher/file manager khi đổi tên/chuyển, bỏ PDF cũ của vị trí trước |
| M1/M2 — `ui/backend/` local | `NewProjectRequest`: loại tạo/nhập và vị trí; `ProjectGateway`: contract mới; `LocalBackend`: catalog vị trí ngoài mặc định, tạo staging, đổi mặc định, rename/delete/move tại vị trí thật; `ProjectFiles`: copy, xác minh, dọn staging sở hữu bởi app |
| M2 — nhập/template | `ProjectImports`: ZIP, Git clone, Pandoc và giới hạn; `ProjectTemplates`: 9 nhóm mẫu + ví dụ; `ProjectFailure`: mã lỗi và thông điệp Anh/Việt |
| M2 — `compiler/` | `CompileWorkspace` + `LatexCompilerService`: tránh lỗi latexmk/Perl trên Windows khi đường dẫn dự án chứa ký tự Unicode bằng bản sao tạm ASCII; PDF thành công và log trả về `build/` thật, fingerprint vẫn lấy nguồn gốc |
| M5 — kiểm thử / cả nhóm đọc | `ProjectWorkflowTest` (12 test), `CompileWorkspaceTest` (2 test); `ProjectImportSmoke`, `NewProjectUiSmoke`; cập nhật `UiSmoke` để chọn mục Dự án trống sau khi mở menu mới |

Phân công trên là phạm vi module cần bàn giao, không khẳng định member đã tự viết các thay đổi trong phiên AI này. Không sửa giao thức M3/M4 hay schema MongoDB M5.

### 22.3 Dữ liệu và công cụ

- Catalog `~/OverleafDesktop/.app/projects.properties` lưu đường dẫn dự án; `.app/settings.properties` lưu thư mục mặc định, last-project bằng đường dẫn và affiliation. Catalog giữ được hai dự án cùng tên ở hai nơi. Không nên chuyển bằng Explorer vì app chưa tự dò vị trí bị đổi bên ngoài.
- Nhập ZIP hỗ trợ bọc ngoài một folder, giữ tài nguyên; cần `main.tex` ở root hoặc root `.tex` có `\documentclass` để tạo bản `main.tex`. Chặn traversal, đường dẫn tuyệt đối, Windows ADS/device names, mục trùng không phân biệt hoa/thường; tối đa **10.000 mục / 500 MiB giải nén**. Thất bại không công bố dự án dở.
- Word chỉ `.docx`, Markdown UTF-8; nguồn tối đa **50 MiB**, giữ bản gốc trong `source/`. Pandoc xuất LaTeX standalone, trích media, font T5/Unicode; chuyển đổi có thể mất định dạng phức tạp. Không hứa PDF giống từng pixel với Word. Tham khảo [Pandoc Manual](https://pandoc.org/MANUAL.html).
- Pandoc tìm theo `OVERLEAF_PANDOC`, `target/pandoc/pandoc.exe`, rồi PATH. Đã chuẩn bị bản portable **3.13** từ [release chính thức](https://github.com/jgm/pandoc/releases/tag/3.13); SHA-256 ZIP `23e1f2e978a3df80db8bf65c9c59534f41a23140480cfb4f783a882ab7dc6a4e`. Portable ở `target/` chỉ phục vụ máy hiện tại, không được commit.
- GitHub cần Git trên PATH; chỉ nhận URL HTTPS `github.com/owner/repo[.git]`, không credential/query/fragment, shallow clone, không recurse submodule, tắt credential prompt/hooks cục bộ. Chưa hỗ trợ kho private hay URL trỏ vào nhánh/subfolder. Không chạy script của kho sau nhập.
- Tiến trình nhập có timeout **120 giây**, xử lý nền, hủy sẽ dừng process tree; không tự compile dự án vừa tải. Tài liệu/engine có thể cần cài thêm package TeX vào môi trường người dùng.
- Windows với đường dẫn Unicode dùng workspace tạm để tránh lỗi `File::Path Invalid argument` của Perl/latexmk. Build tạm này chưa giữ cache giữa các lần compile; thư mục temp của Java cần đường dẫn ASCII. Đường dẫn ASCII bình thường vẫn compile tại chỗ và giữ cache. PDF lỗi không thay bản PDF tốt trước đó.

### 22.4 Kiểm chứng thực tế

- **98/98 JUnit chọn lọc đạt**, không skip/failure/error: common 9 + server 21 + client 68. Log `target/new-projects-tests.log`; lệnh README đã gồm `ProjectWorkflowTest`, `CompileWorkspaceTest` và regression compiler/workspace/network trước đó.
- **PDF thật:** `ProjectImportSmoke` tạo và compile thành công cả **9 template tiếng Việt** trong đường dẫn có dấu, dự án ví dụ có BibTeX, chuyển và compile Word/Markdown có tiếng Việt/công thức; PDFBox trích được `Tiếng Việt`, bản nguồn không đổi bytes. Clone được kho công khai `https://github.com/gcstinchfield/latex-paper-template`, có `main.tex` và `.git`. Log `target/project-import-smoke.log`, kết quả `PROJECT_IMPORT_SMOKE_OK`; dữ liệu `target/project-import-smoke-04`.
- **JavaFX thật:** `NewProjectUiSmoke` kiểm menu Anh/Việt và đủ nhóm mẫu, tạo dự án ở thư mục riêng, đổi mặc định vẫn thấy dự án cũ, chuyển dự án và lưu thư mục qua Settings. Kết quả `NEW_PROJECT_UI_SMOKE_OK`; log `target/new-project-ui-smoke.log`, dữ liệu `target/new-project-ui-smoke-03`. Dùng control/event thật với home cô lập, chưa phải Robot bấm bằng tọa độ. Không chạy lại toàn bộ `UiSmoke` cũ trong lần này.
- Chạy smoke bằng main class tương ứng trong `client/src/test/java`, classpath client test/main + common + PDFBox; smoke UI thêm JavaFX module path. Luôn truyền thư mục test home **chưa tồn tại**; GitHub smoke tùy chọn URL repo công khai. Các `.cmd` thử nghiệm dưới `target/` là helper local, không phải script phân phối.

Sau cập nhật, đóng app cũ rồi chạy `.\scripts\run-client.bat`. Không cần chuyển hoặc xóa dự án người dùng đang có để bật tính năng.

## 23. Workspace: thêm thư mục, tệp, upload và lấy tệp từ nguồn khác

Ngày **02/11/2026**, cùng working tree `feat/m1-gui-client`; chưa commit/push. Hoàn thành yêu cầu hộp thoại Add files theo ảnh và thao tác trong thư mục workspace. Giữ các thay đổi compiler/PDF/project từ trước.

### 23.1 Hành vi và sử dụng

- Sidebar có **Thêm tệp / Add files**, **Thư mục mới / New folder**, **Tải lên / Upload**. Nhấp phải thư mục cũng có ba thao tác này. Thư mục đang chọn (hoặc thư mục cha của tệp đang chọn) được điền sẵn làm đích; có thể chọn/nhập thư mục khác, để trống dùng gốc.
- Hộp thoại rộng có menu bên trái: **Tệp mới**, **Thư mục mới**, **Tải lên**, **Từ dự án khác**, **Từ URL bên ngoài**, **Thư viện tài liệu (.bib)**; tên/hướng dẫn/lỗi/thao tác mới có Anh/Việt. Đổi ngôn ngữ cập nhật cả các nút workspace mới và menu thêm tệp. Thẻ nội dung có vùng cuộn.
- Tạo tệp trống; nếu tên cuối chưa có đuôi, thêm `.tex`. Chấp nhận đường dẫn con như `chapters/intro.tex`, tự tạo thư mục cha. Tạo thư mục rỗng vẫn hiện ngay trong cây và có thể nhấp phải để thêm tệp.
- Upload nhiều tệp hoặc cả thư mục qua FileChooser/DirectoryChooser; kéo thả từ hệ điều hành hoặc **Dán tệp / ảnh**. `Ctrl+V` khi vùng thả có focus cũng nhận clipboard. Danh sách chờ cho bỏ mục trước khi xác nhận upload. Upload folder giữ tên folder, cấu trúc và folder rỗng; file nhị phân giữ nguyên bytes. Ảnh clipboard mã hóa PNG với tên riêng.
- **Từ dự án khác:** chọn trong các dự án local đã đăng ký, chọn tệp, đổi tên tại dự án đích. Bật **Chọn từ tệp đầu ra (build)** để lấy PDF/log/tệp build. Sao chép độc lập, không tạo liên kết hay tự cập nhật khi nguồn đổi.
- **URL:** nhập HTTPS trực tiếp và tên đích; theo tối đa 5 redirect HTTPS, không nhận credentials trong URL, yêu cầu HTTP 200; tối đa 100 MiB, connect/read timeout 15 giây. Đây là tải tệp, không xử lý trang chia sẻ/đăng nhập cloud, không thực thi tệp tải về. Hủy dừng khi vòng đọc kiểm tra ngắt; một read đang chờ có thể cần đến timeout.
- **Thư viện tài liệu:** tải file `.bib` đã xuất từ ReadCube/Zotero/Mendeley. Không có OAuth hay đồng bộ tài khoản các dịch vụ đó; không hiển thị các kết nối giả. Các folder/tài nguyên mới lưu local, không mở rộng phòng cộng tác vốn chỉ đồng bộ `main.tex`.

### 23.2 File và vai trò

| Phần / member phụ trách bàn giao | Thay đổi |
| --- | --- |
| M1 — `client/.../ui/AddFilesDialog.java` | Hộp thoại dùng chung các điểm vào, menu trái, thư mục đích, các form, queue upload, drag/drop, clipboard PNG, chọn dự án/đầu ra, URL, `.bib`; Task nền cho copy/download, lỗi dịch và hủy |
| M1 — `OverleafFxApp.java` | Nút workspace và context menu thư mục; dùng selected directory; hiển thị thư mục rỗng, tệp ảnh/font/tài nguyên, tới 32 cấp; bỏ symlink khỏi cây; file nhị phân không đưa vào text editor, mở ngoài bằng nhấp đúp; thêm tệp làm PDF cũ không còn hợp lệ |
| M1 — `UiModal.java`, theme CSS | `width()` responsive cho hộp thoại rộng, menu trái/vùng thả/vùng cuộn theo style app |
| M2 — `client/.../workspace/WorkspaceFiles.java` | Kiểm đường dẫn, tạo folder/file, lập kế hoạch nhập, staging, không ghi đè, rollback các mục mới khi lỗi, giới hạn bytes/số mục, download HTTPS |
| M5 — kiểm thử / cả nhóm đọc | `WorkspaceFilesTest` (12 test), `WorkspaceFilesUiSmoke`; cập nhật control mới trong `UiSmoke` để test cũ gọi đúng tên field/nút |

Đây là phạm vi bàn giao theo module, không xác nhận member đã tự viết thay đổi trong phiên AI. Không đổi protocol M3/M4 hay database.

### 23.3 Tính toàn vẹn và giới hạn

- Đường dẫn nằm trong dự án; từ chối traversal, tên rỗng/ẩn, device name Windows, ADS, segment `build`, ký tự cấm và symlink đích. Upload không theo symlink nguồn. Root `build/` được dành cho compiler; muốn lấy output từ dự án khác cần lưu với tên ngoài build.
- Không ghi đè file/folder đã có; từ chối tên trùng trong cùng lô kể cả khác hoa/thường. Kiểm tra toàn bộ kế hoạch trước khi ghi; dữ liệu copy vào staging ẩn `.overleaf-upload-*` trước khi công bố. Nếu công bố lỗi, chỉ rollback các file/folder vừa tạo, giữ mục cũ. Staging lỗi còn sót bị ẩn khỏi cây; không xóa dữ liệu nguồn.
- Mỗi lô tối đa **10.000 mục / 500 MiB**, thư mục tính vào số mục; ảnh dán giới hạn **25 triệu pixel** trước khi mã hóa. Chưa có luồng ghi đè có xác nhận; gặp trùng cần đổi tên hoặc bỏ mục trong queue.
- Không tự thêm `\input`/`\includegraphics`/bibliography vào `main.tex`, không tự compile sau upload. Người dùng thêm lệnh sử dụng tài nguyên rồi biên dịch như bình thường.
- Build output chỉ hiện PDF trong cây chính; chọn output để sao chép có thể lấy các file khác. File không thuộc nhóm text hỗ trợ được mở bằng ứng dụng hệ điều hành khi nhấp đúp; app không tự mở binary vừa upload.

### 23.4 Kiểm chứng

- **110/110 JUnit chọn lọc đạt**, không failure/error/skip: common 9 + server 21 + client 80; `target/workspace-files-tests.log`. Test mới kiểm Unicode/thư mục rỗng, path traversal/tên cấm, không ghi đè, giữ hierarchy/bytes nhị phân, conflict cả lô, trùng hoa/thường, copy output đổi tên, chặn copy folder chứa project, rollback publish lỗi, giới hạn số mục, URL sai và hủy.
- `WorkspaceFilesUiSmoke` trên JavaFX thật: tạo folder và file đúng folder đang chọn, upload folder có ảnh và folder rỗng, đối chiếu bytes ảnh, encode ảnh dán PNG và đối chiếu pixel, sao chép `.tex` tiếng Việt và PDF output, từ chối trùng tên, bản dịch VI/EN và cập nhật sidebar. Đã tải HTTPS thật README Pandoc 3.13 vào dự án thử và kiểm nội dung. Log `target/workspace-files-ui-smoke.log`: `HTTPS_DOWNLOAD_OK`, `WORKSPACE_FILES_UI_SMOKE_OK`.
- Home cô lập `target/workspace-files-ui-smoke-04`, ảnh riêng scene ứng dụng: `upload-dialog.png`, `copy-dialog.png`, `new-file-dialog.png`; đã kiểm tra bố cục. Smoke gọi control/event và handler với nguồn test, không thao tác clipboard thật của người dùng, chưa dùng Robot kéo thả hoặc tự bấm native file chooser. Không chạy lại toàn bộ `UiSmoke` cũ.
- Chạy main `WorkspaceFilesUiSmoke <home-chưa-tồn-tại> [url-test]` với JavaFX module path và client/test/common/server/PDFBox classpath như smoke mục 22. URL test tùy chọn đang kiểm nội dung có `pandoc`; dùng `https://raw.githubusercontent.com/jgm/pandoc/3.13/README.md`. Script helper local `target/run-workspace-files-ui-smoke.cmd` không được Git theo dõi.

Đóng tiến trình app cũ, chạy `.\scripts\run-client.bat` để nhận code mới. Các dự án đang có không cần nhập lại.

## 24. Sửa biên dịch ZIP nhiều chương và thông báo exit code 12

Ngày **02/11/2026**, theo báo lỗi khi nhập `D:\download\latex-test-project-v2.zip`, cùng working tree chưa commit/push. Chỉ đọc ZIP gốc, nhập/compile các bản sao dưới `target/`, không sửa tệp gốc hoặc dự án đang dùng.

### 24.1 Nguyên nhân đã tái hiện

1. TinyTeX portable ban đầu thiếu `csquotes.sty`; log có `! LaTeX Error: File 'csquotes.sty' not found` trước `preamble.tex:15: Emergency stop`. ZIP còn yêu cầu titlesec, siunitx, biblatex/Biber, cleveref… Đây là phụ thuộc môi trường TeX, không phải hỏng ZIP/UTF-8 khi giải nén.
2. Sau bổ sung gói, `main.tex:42` lỗi `I can't write on file 'chapters/01-introduction.aux'`. Compiler dùng `-outdir=build` nhưng chỉ tạo `build/`, chưa tạo `build/chapters`, `build/appendix`; TeX không tự tạo các thư mục con cho `\include`.
3. Parser cũ chỉ đọc `file:line: message`, bỏ mất lỗi bắt đầu bằng `!`, nhưng nhận cả `runscript.tlu:933: command failed with exit code 12`. UI giải thích thông báo tổng kết của launcher như lỗi tài liệu nên gợi ý ngoặc nhọn không phù hợp.

### 24.2 Thay đổi và vai trò

- **M2 — `CompileWorkspace.prepareOutputDirectories`:** tạo cây thư mục đầu ra tương ứng các thư mục nguồn trước khi khởi chạy TeX; bỏ qua `build/`, thư mục ẩn và không đi theo symlink. Dùng cho cả build tại chỗ và bản staging ASCII của đường dẫn Unicode. Không thay đổi `\include`/tệp nguồn.
- **M2 — `LatexCompilerService`:** gọi chuẩn bị output; nếu TeX log cũ có lỗi thì thêm `-g` cho lần retry để tránh latexmk giữ thất bại sau khi thư mục/gói đã được sửa. Build bình thường không lỗi vẫn dùng cache. Khi output thất bại không có lỗi phân tích được, bổ sung chẩn đoán từ TeX log (đọc tối đa 16 MiB).
- **M2/M1 — parser và UI:** nhận cả lỗi `!`, gắn vị trí từ dòng chẩn đoán tiếp theo nếu có; bỏ lỗi launcher `runscript.tlu` và thông báo `command failed with exit code`, bỏ fatal/emergency phụ khi đã có nguyên nhân, gộp lỗi lặp. UI hỗ trợ lỗi chưa biết file/số dòng, không in vị trí giả `:0`.
- **M1 — `LatexErrorExplainer`:** giải thích lỗi không ghi được `.aux`; nếu nhận riêng thông báo wrapper thì nói rõ exit code chỉ là tổng kết và hướng dẫn tìm lỗi đầu tiên, không đoán thiếu ngoặc.
- **M5 — kiểm thử:** thêm 7 regression (4 compiler/parser/retry, 1 output-directory, 2 explainer); `ImportedZipCompileSmoke` nhận ZIP gốc và home thử mới, chạy ba engine, kiểm PDFBox và đối chiếu bytes toàn bộ tệp nhập.
- **Môi trường local:** bổ sung vào `target/TinyTeX` các gói còn thiếu `csquotes`, `titlesec`, `siunitx`, `biblatex`, `biber` (kèm binary Windows), `cleveref`, `logreq`; xác nhận pgfplots/listings/enumitem/setspace/fancyhdr/mathtools/caption/booktabs/tools đã có. `xspace.sty` thuộc gói `tools`, không phải tên package tlmgr riêng. Không đổi PATH hệ thống hoặc tự tải gói khi người dùng bấm compile; portable không được Git theo dõi.

### 24.3 Kiểm chứng và bằng chứng

- Tái hiện thiếu gói: `target/user-zip-v2-repro-01/PDFLATEX.log`; tái hiện lỗi aux sau bổ sung gói: `target/user-zip-v2-repro-02/PDFLATEX.log`.
- ZIP nguyên gốc nhập qua `LocalBackend`/`ProjectImports`, compile bằng service thật: **pdfLaTeX, XeLaTeX, LuaLaTeX đều thành công, mỗi PDF 13 trang**. PDFBox tìm được `Dự án kiểm thử`, `Giới thiệu`, `Dữ liệu thô`, `Tài liệu tham khảo`, không có citation `[?]`; `build/main.bbl` có entry `knuth1984` của Biber. Các tệp nguồn/ảnh/CSV sau import và compile vẫn đúng bytes trong ZIP.
- Log `target/imported-zip-smoke.log`: `IMPORTED_ZIP_COMPILE_SMOKE_OK`; PDF/text/log từng engine ở `target/user-zip-v2-verified-01/evidence/` (`PDFLATEX.pdf`, `XELATEX.pdf`, `LUALATEX.pdf`). Smoke này kiểm tài liệu thật, không tính vào số JUnit.
- **117/117 JUnit chọn lọc đạt**, common 9 + server 21 + client 87, không failure/error/skip; log `target/workspace-files-tests.log`. Lệnh README giữ nguyên nhóm test vì các regression mới nằm trong class đã có.
- Main smoke có thể chạy `ImportedZipCompileSmoke <zip> <home-chưa-tồn-tại>` với classpath client/main/test + common + PDFBox; đây là fixture riêng cho ZIP nhiều chương người dùng, kiểm các cụm tiếng Việt và BibLaTeX nêu trên, không phải validator chung cho mọi ZIP.

Cách dùng sau sửa: đóng app cũ, chạy `.\scripts\run-client.bat`, mở lại dự án đã nhập và **Recompile**. Không cần sửa nguồn hoặc nhập lại ZIP. Các phụ thuộc portable chỉ được cài trên máy kiểm thử; máy mới cần cài TeX và các package tương ứng.

## 25. Preview PDF cuộn liên tục và liên kết nội bộ

Ngày **02/11/2026**, theo ảnh và yêu cầu cuộn qua các trang, bấm mục lục/tham chiếu để tới tiêu đề. Cùng working tree `feat/m1-gui-client`, chưa commit/push.

### 25.1 Sử dụng và hành vi

- Sau Recompile thành công, preview xếp các trang thật theo chiều dọc, có khoảng cách và bóng giữa trang. Cuộn chuột/thanh cuộn để xem trang tiếp theo, không cần nhấn nút chuyển từng trang. Bộ đếm cập nhật theo trang chiếm nhiều vùng nhìn nhất. Vẫn giữ `‹`/`›`, zoom và Fit.
- Bấm liên kết mục lục, tham chiếu chương/hình/bảng trong PDF để tới đúng trang và tọa độ dọc của tiêu đề/đích liên kết. `↶` quay lại vị trí trước khi bấm liên kết; lịch sử tối đa 100 vị trí, reset khi đổi PDF/dự án.
- Lấy link annotation/đích thật từ PDF: direct destination và GoTo, kể cả named destinations của `hyperref`. Không suy ra đích từ màu chữ hay đoán tiêu đề. Tài liệu cần tạo link sẵn trong PDF; thường dùng `\usepackage[colorlinks=true]{hyperref}` và compile đủ các lượt.
- Chưa mở URI website, tệp ngoài hoặc chạy action/script của PDF. Các destination không có tọa độ heading được đưa về đầu trang; không phải SyncTeX giữa source và PDF, không bổ sung text selection/search.
- Export tiếp tục chép đúng bytes của snapshot PDF; nguồn đổi hoặc compile lỗi vẫn đánh dấu bản cũ và khóa xuất. Cuộn/zoom/bấm liên kết không thay PDF hay mở khóa xuất cho bản cũ.

### 25.2 File và vai trò

| Member / phần bàn giao | Thay đổi |
| --- | --- |
| M2 — `compiler/PdfSnapshot.java` | Metadata immutable cho kích thước trang và links; resolve named destination/GoTo; chuyển rectangle và tọa độ PDF sang hệ tọa độ crop box đã xoay; bỏ annotation hỏng/không hỗ trợ, không thực thi actions |
| M1 — `ui/PdfPreviewPane.java` | Các khung trang liên tục; giữ hình dạng trang trước khi render; tính vùng nhìn, render tuần tự nền; overlay Hyperlink đúng vị trí; jump/Back, zoom/Fit và generation guard khi đổi PDF/reset |
| M1 — theme | CSS trang trắng/bóng và vùng hover/focus nhẹ cho liên kết, không thay chữ/công thức trong ảnh PDF |
| M5 — kiểm thử | Thêm 3 `PdfSnapshotTest`: crop/rotation, named links + bỏ URI action, link đích sai không làm hỏng export; `PdfContinuousUiSmoke` mới; chạy lại `PdfPreviewUiSmoke` với tài liệu thật |

Phân công là trách nhiệm module cần bàn giao; không khẳng định từng member tự viết các thay đổi trong phiên AI này. Không đổi compiler command/protocol/database cho tính năng này.

### 25.3 Bộ nhớ và giới hạn kỹ thuật

- Tạo khung trang từ metadata, chỉ render trang đang thấy và các trang lân cận. Những ảnh xa vùng nhìn được bỏ để tránh giữ tất cả trang; một worker render, không tạo hàng đợi vô hạn khi cuộn nhanh.
- Ngân sách lựa chọn ảnh khoảng **24 triệu pixel**, một trang vẫn bị giới hạn **12 triệu pixel** và scale tối đa 3 như trước. Đây là giới hạn ảnh trang được giữ, không phải tổng heap: PDF bytes, metadata, buffer render/chuyển ảnh còn cần thêm bộ nhớ. PDF vẫn giới hạn 128 MiB.
- Khi nhảy nhanh, trang có thể hiện placeholder ngắn lúc chờ render. Kích thước khung không đổi khi ảnh tải xong nên tránh cuộn giật. Không tự kéo về đầu mỗi khi một trang render xong.
- Generation guard bỏ kết quả render cũ sau reset/compile PDF mới. Render lỗi làm trạng thái thất bại và khóa export; một link hỏng được bỏ qua mà không chặn trang còn hợp lệ.

### 25.4 Kiểm chứng

- **120/120 JUnit chọn lọc đạt**, common 9 + server 21 + client 90, không failure/error/skip; log `target/workspace-files-tests.log`.
- `PdfContinuousUiSmoke` dùng PDF **13 trang** từ ZIP người dùng ở mục 24: cuộn cập nhật trang, pixels đúng PDFBox, cuộn tới cuối và không giữ mọi ảnh; named link từ trang vật lý 3 đến trang 6, overlay khớp tọa độ, tiêu đề đích nằm đúng mép trên viewport, Back trở về; zoom/Fit, khóa xuất stale, export byte-identical và không nhận render cũ sau reset đều đạt. Log `target/pdf-continuous-ui.log`: `PDF_CONTINUOUS_UI_SMOKE_OK`.
- Ảnh scene riêng đã xem: `target/pdf-continuous-ui-02/continuous-preview.png`, thấy cuối trang mục lục và đầu trang danh sách hình liên tiếp. Smoke gọi scroll/control thật và `Hyperlink.fire`, chưa dùng Robot bấm/kéo theo tọa độ hệ điều hành.
- Chạy lại **`PdfPreviewUiSmoke` toàn luồng JavaFX app** với `target/vietnamese-fixed/main.tex` **7 trang**: compile thật, pixels các trang, export đúng bytes, sửa nguồn chặn xuất, compile lỗi vẫn giữ bản tốt, compile lại phục hồi và đổi dự án reset preview đều đạt. Log `target/pdf-continuous-regression.log`: `PDF_PREVIEW_UI_SMOKE_OK`; home riêng `target/pdf-continuous-regression-01`.
- Chạy smoke mới: main `PdfContinuousUiSmoke <pdf-multi-page-có-hyperref> <output-folder-chưa-tồn-tại>` với JavaFX module path + client/test/common/server/PDFBox classpath. Fixture cần có link nội bộ đến heading ở phần trên trang; helper local `target/run-pdf-continuous-smoke.cmd` không được Git theo dõi.

Đóng app cũ rồi chạy `.\scripts\run-client.bat`; mở dự án và Recompile để dùng preview mới.

## 26. Toolbar editor: chèn LaTeX và thụt lề

Ngày **02/11/2026**, nhánh `Network`, nền **`e9424cf`** với cấu trúc `modules/` và `docs/`. Các thay đổi của mục này ở working tree, chưa commit/push. Không sửa các commit đã đẩy trước đó.

### 26.1 Dãy icon và chức năng

Ngay sau **Italic**: **Insert math → Insert link → Add comment → Insert cross reference → Insert citation → Insert figure → Insert table → Insert list → Decrease indent → Increase indent**. Icon dùng SVGPath của hệ icon hiện có, tooltip có Việt/Anh, không cần ảnh bitmap hay thư viện icon ngoài. Toolbar cuộn ngang khi vùng editor hẹp, không ép chiều rộng workspace.

| Nút | Hành vi |
| --- | --- |
| Math | Menu công thức trong dòng `$...$`, riêng dòng `\[...\]` hoặc `equation` đánh số; bọc đoạn đang chọn, hoặc đặt con trỏ vào giữa |
| Link | Form URL và chữ hiển thị, chèn `\href`; hỗ trợ http/https/mailto, escape ký tự LaTeX trong URL/chữ; cần `hyperref` |
| Comment | Dùng lại thao tác bật/tắt `%` trên các dòng được chọn, giữ shortcut Ctrl/Cmd+/; không phải comment thread/review annotation |
| Cross reference | Gợi ý nhãn từ `.tex`, kể cả nội dung chưa lưu của file hiện tại; chọn `ref`, `eqref`, `pageref`, `autoref` hoặc tự nhập nhãn |
| Citation | Gợi ý khóa từ `.bib`, bỏ entry comment/string/preamble; cho nhập nhiều khóa cách nhau dấu phẩy, chèn `\cite`; không tự cấu hình bibliography |
| Figure | Chọn PNG/JPG/PDF đã có trong project, độ rộng 0.05–1 lần linewidth, caption và label; tạo figure/centering/includegraphics/caption/label; cần `graphicx` |
| Table | Số dòng dữ liệu 1–50, cột 1–20, căn lề l/c/r, caption/label; sinh table + tabular + hline, không cần booktabs |
| List | Dùng lại menu itemize/enumerate; chuyển các dòng đang chọn thành item hoặc tạo mẫu tại con trỏ |
| Giảm / tăng lề | Áp dụng cho các dòng chứa vùng chọn, bỏ qua dòng tiếp theo nếu vùng chọn kết thúc đúng đầu dòng; thêm 2 space, giảm tối đa 2 space hoặc 1 tab; giữ hướng selection/caret |

Các nút sửa nội dung bị disable khi editor chỉ đọc. Các hàm chèn trực tiếp cũng kiểm tra editability. Form chụp file/project/text/range lúc mở và từ chối chèn nếu tài liệu đổi trong lúc form đang mở; tránh dùng sai vị trí khi nhận cập nhật từ người khác.

### 26.2 File và trách nhiệm

- **M1 — `modules/client/.../ui/OverleafFxApp.java`:** lắp dãy icon, menu math/list, dùng lại wrapSelection/toggleComment/insertList, dispatch form và indent; toolbar ScrollPane. Thay đổi qua TextArea thông thường để vẫn kích hoạt dirty/undo/sync/PDF stale như các thao tác gõ.
- **M1 — `ui/UiSupport.java`, CSS:** bổ sung path icon vector math/comment/reference/citation/figure/table/list/indent, style vùng cuộn phù hợp toolbar tối.
- **M1/M2 — `ui/EditorInsertDialog.java`:** form song ngữ, kiểm tra nguồn chưa đổi, quét nhãn/khóa và chọn ảnh. Quét regex có bỏ comment `%`, đọc tối đa 2 MiB/file; đây là gợi ý nhanh, không phải parser TeX/BibTeX đầy đủ, có thể nhập khóa thủ công. Không tự sửa file preamble hoặc tải tài nguyên.
- **M1 — `ui/EditorInsertions.java`:** snippet LaTeX, escape text/URL, kiểm khóa/path/width/kích thước bảng; phép chỉnh lề thuần Java trả về replacement và selection mới. Width được chuẩn hóa số thập phân để tránh sinh cú pháp dimension sai khi nhập dạng `8e-1`.
- **M5 — kiểm thử:** `EditorInsertionsTest` (8 JUnit), `EditorToolbarUiSmoke` (explicit JavaFX + TeX). Đây là phạm vi bàn giao theo module, không xác nhận các member tự viết thay đổi trong phiên AI.

### 26.3 Kiểm chứng và sử dụng

- **128/128 JUnit chọn lọc đạt**, common 9 + server 21 + client 98, không failure/error/skip; log `target/editor-tools-tests.log`. Test mới kiểm key/citation, URL/escape, figure width/path, table syntax và các biên selection/caret/tab/indent.
- **JavaFX thật:** `EditorToolbarUiSmoke` xác nhận thứ tự icon, các thao tác math/link/comment/reference/citation/figure/table/list/indent, đọc nhãn chưa lưu và khóa BibTeX, khóa thao tác chỉ đọc, từ chối form có văn bản đã đổi. Ghép snippet vừa chèn thành tài liệu có hyperref/graphicx/amsmath + ảnh có dấu cách trong tên + BibTeX, compile qua app và xác nhận preview cho phép export. Log `target/editor-tools-ui.log`: `EDITOR_TOOLBAR_UI_SMOKE_OK`.
- Home thử nghiệm cô lập `target/editor-tools-ui-02`, ảnh scene `insert-table.png` và `editor-toolbar.png`; đã kiểm tra layout form và toolbar. Smoke dùng control/event JavaFX, chưa phải Robot theo tọa độ màn hình.
- **Spotless theo cấu hình repo đạt** cho sáu file Java sửa/thêm, log `target/editor-tools-format.log`; không format lại các file ngoài phạm vi. Helper `.cmd` dưới `target/` là công cụ local, không được Git theo dõi.
- Chạy smoke main `com.overleaf.client.ui.EditorToolbarUiSmoke <home-chưa-tồn-tại>` với classpath `modules/client/target/classes`, test-classes, common/server và PDFBox; module path JavaFX như các smoke trước, cần TeX thật để chạy phần compile.

Đóng app cũ và chạy `.\scripts\run-client.bat`. Ảnh cần upload trước khi chèn Figure; cấu hình `hyperref`, `graphicx`, bibliography trong tài liệu theo hướng dẫn của form. Các thao tác chèn không tự compile hay sửa package của dự án.

## 27. Auto Compile, History và Layout workspace

Ngày **02/11/2026**, nhánh `Network`, nền `e9424cf` cùng các thay đổi toolbar mục 26 đang ở working tree. Chưa commit/push các thay đổi mới này.

### 27.1 Chế độ biên dịch

- Mũi tên cạnh nút compile mở radio menu **Biên dịch thủ công / Recompile manually** và **Tự động biên dịch / Auto Compile**. Mặc định thủ công; `AppSettings.AUTO_COMPILE` lưu lựa chọn local. Chọn auto làm nút chính hiển thị Auto Compile; nút chính/F5/Ctrl+Enter vẫn chạy ngay.
- Auto dùng PauseTransition **1 giây** ngừng chỉnh sửa, save nội dung hiện tại rồi dùng compiler service cũ. Khi đang build, chỉ giữ cờ cần chạy tiếp, không tạo một process cho mỗi phím. Có thể tự compile khi source/tài nguyên hoặc engine thay đổi; không tự retry vô hạn nếu build lỗi mà nguồn không thay đổi.
- **Stop** bỏ timer/pending và hủy process hiện tại. Rời editor, vào History, đổi dự án hoặc thoát sẽ hủy lịch tự động; quay lại editor trong chế độ auto sẽ đặt lịch lại. Không tự biên dịch khi editor đang chỉ đọc.

### 27.2 History: lưu và giao diện

- Nút **History / Lịch sử** mở màn hình ba cột: trái danh sách tệp; giữa source của revision (có số dòng) và tab so sánh hai bên với file hiện tại; phải timeline thời gian, file, tác giả local/lý do và nhãn. Có **Tất cả lịch sử / Nhãn**, thêm/sửa nhãn, refresh và Back to editor. Phần khác nhau trong hai văn bản được chọn từ điểm khác đầu đến điểm khác cuối; đây không phải diff từng hunk kiểu Git.
- **`workspace/ProjectHistory.java`** lưu trong `<project>/.overleaf-history/<UUID>/`, metadata properties + nội dung UTF-8. SHA-256 kiểm tra nội dung trước khi đọc/khôi phục; staging trước khi công bố; bỏ qua metadata hỏng/đang ghi, không cho path traversal hay symlink trong history. History đi theo project khi app di chuyển thư mục.
- **`ProjectFileManager`** lưu preimage trước khi ghi và revision sau khi ghi thành công. App capture bản khi mở, thay đổi bên ngoài được watcher đọc, và checkpoint **3 giây ngừng gõ** kể cả chưa Save. Checkpoint không ghi đè tệp source đang làm việc. Các snapshot giống nhau không tạo bản ghi lặp; preimage đã có không làm timeline xuất hiện rollback giả khi Save sau checkpoint.
- Lịch sử bắt đầu từ phiên bản app này; không tái tạo sửa đổi trước đó. Đây là **local text history**, không phải lịch sử Git hoặc audit đầy đủ của mọi cộng tác viên. Snapshot do networking/ghi chung có thể có tác giả “Local capture”; không cam kết danh tính người sửa từ server.
- Chọn revision → **Khôi phục tệp này** → xác nhận. Tệp hiện tại được capture trước khi thay, sau đó viết bản cũ qua FileManager và đánh dấu PDF stale. Chỉ thay một tệp, không phục hồi ảnh/thư mục hoặc cả project. Khi đang có kết nối/phòng cộng tác, khôi phục bị khóa; cần rời phòng trước.
- Giới hạn **2 MiB/revision**, **1.024 revisions hoặc 128 MiB/project**; không tự xóa mốc cũ. Vượt giới hạn/quyền ghi history lỗi không chặn lưu source; cảnh báo `[HISTORY]` được ghi ra log. Chưa có UI dọn/đổi quota history. Crash trước checkpoint/Save vẫn có thể mất phần chưa được ghi.
- `ProjectInputs` loại `.overleaf-history` khỏi fingerprint, `CompileWorkspace` không copy nó sang workspace compile tạm; label/checkpoint không làm thay nội dung PDF cần export. Watcher/tree đã bỏ qua thư mục ẩn.

### 27.3 Layout theo ảnh

- **Split view / Chia đôi:** cây file + editor/build output + PDF, giữ divider của split trước đó.
- **Editor only / Chỉ editor:** cây file và editor, giữ nguyên source/selection.
- **PDF only / Chỉ PDF:** toàn vùng workspace cho PDF.
- **Open PDF in separate window:** viewer thật được chuyển sang Stage riêng, vẫn cập nhật khi compile; không tạo một bản preview khác. Đóng cửa sổ sẽ gắn lại PDF và về split. Vào Home/History hoặc đổi project cũng đóng/detach an toàn. Đây là cửa sổ desktop, không phải tab trình duyệt.
- **Focus mode:** ẩn cây file và build output, giữ các pane theo mode; topbar còn menu để thoát. Shortcut **Ctrl/Cmd+Shift+M**. Focus không lưu qua lần mở app; `AppSettings.LAYOUT` lưu split/editor/pdf. Menu, nút History/Layout và History có nhãn Việt/Anh.

### 27.4 File, vai trò và kiểm chứng

| Phụ trách bàn giao | File / nội dung |
| --- | --- |
| M1 — UI | `modules/client/.../ui/OverleafFxApp.java`: debounce compile/checkpoint, menu/topbar, layout/detach, vào/ra history và restore; `HistoryView.java`: browser read-only, label/filter/compare/confirm; `AppSettings.java`, theme CSS |
| M2 — workspace/compiler | `workspace/ProjectHistory.java`, `ProjectFileManager.java`: lưu text revisions; `compiler/ProjectInputs.java`, `CompileWorkspace.java`: loại history khỏi inputs/build tạm |
| M5 — kiểm thử | `ProjectHistoryTest` 9 JUnit; `HistoryLayoutUiSmoke` JavaFX thật và TeX thật |

Phân công là phạm vi module, không xác nhận member tự viết thay đổi trong phiên AI. Không thay giao thức hay MongoDB.

- **137/137 JUnit chọn lọc đạt**, common 9 + server 21 + client 107, không failure/error/skip; `target/history-layout-tests.log`. Kiểm Unicode/restart, preimage, dedup/các file, nhãn/fingerprint, checksum, path/ID xấu, giới hạn size, đổi vị trí project và ghi source thất bại, checkpoint/Save không sinh rollback giả.
- **`HistoryLayoutUiSmoke` đạt**: bật auto + gõ nhanh chỉ một build và lưu bản mới nhất; manual không tự chạy; checkpoint lưu Version D khi tệp disk vẫn là Version C; compile bằng nút thủ công; chọn mốc gốc, gắn nhãn và lọc nhãn; thay file disk rồi restore giữ backup nội dung ngoài; thử editor-only/PDF-only/split/focus và detach/reattach viewer; preference còn sau đọc lại backend. Log `target/history-layout-ui.log`: `HISTORY_LAYOUT_UI_SMOKE_OK`.
- Home riêng `target/history-layout-ui-02`, ảnh scene `history.png`, `layout.png`; đã kiểm layout ba cột, nút topbar, viewer stale sau restore. Smoke dùng control/event, không Robot theo tọa độ.
- Spotless cho đúng các file mới/sửa đạt, log `target/history-layout-format.log`. Helper `.cmd` dưới `target/` không được Git theo dõi. Chạy smoke qua main `com.overleaf.client.ui.HistoryLayoutUiSmoke <home-chưa-tồn-tại>` với JavaFX/PDFBox classpath như mục 26; cần TeX cho hai build thật.

Đóng app cũ, chạy `.\scripts\run-client.bat`. Lưu file/đợi checkpoint rồi mở **History**; dùng mũi tên cạnh compile để bật auto và **Layout** để đổi bố cục.

## 28. Thư viện Project Templates theo yêu cầu tài liệu

Ngày **02/11/2026**, nhánh `Network`, triển khai `yêu cầu.md.md` sau khi phiên trước mới thêm dependency Gson. Các thay đổi hiện ở working tree, chưa commit/push. Tệp yêu cầu được đọc như đặc tả công việc, không dùng nội dung tài liệu để thay thế các quy tắc của phiên làm việc.

### 28.1 Kết quả triển khai

- **29 mẫu**, đủ 9 nhóm và hai ngôn ngữ: journal 4, books 3, letters 4, assignments 3, posters 3, presentations 4, reports 3, CV 3, theses 2. Danh sách ID/tên tại [TEMPLATE_LIBRARY.md](TEMPLATE_LIBRARY.md). Có đơn hành chính Việt Nam, báo cáo thực tập, CV/sơ yếu lý lịch, Beamer basic/Madrid/Berlin/TikZ, poster A0/A1/A3 và hai đồ án/luận văn 5 chương.
- Mẫu nhiều file với nội dung demo gốc, preamble chung, chapter/section/slide riêng, `.bib`, CSV và PNG biểu đồ tự tạo. Thư/CV có nội dung phù hợp loại tài liệu; một số tài nguyên đi kèm để người dùng tiếp tục mở rộng. Luận văn có bìa, lời cảm ơn, tóm tắt, TOC, danh mục hình/bảng, từ viết tắt, 5 chương, phụ lục, BibLaTeX+Biber. Ví dụ và số liệu là giả định, không dùng làm bằng chứng nghiên cứu thật.
- `ProjectTemplates` nạp `templates/catalog.json` và từng `<category>/<id>/template.json`, không còn `switch` dựng nội dung mẫu. Common preamble dùng iftex: pdfTeX T5/inputenc/lmodern; Xe/Lua dùng fontspec và font Latin Modern theo tên file để portable. Babel chọn tiếng chính theo UI.
- Giữ `all()`, `files(id,title,vi,affiliation)`, `escape()`; record Template mở rộng metadata. `categories()` dành cho 9 nhóm. ID category cũ được map tới mẫu đầu nhóm cho caller cũ. `blank`/`example` là resource nội bộ, không tính vào 29 mẫu gallery. `LocalBackend.template()` dựng bản blank standalone từ cùng resource, giữ API compiler/caller legacy.
- Giữ 9 `template-<category>` và `project-templates` trong menu. Bấm nhóm mở gallery lọc nhóm; Xem tất cả mở cả 29. Gallery thẻ có tên/mô tả song ngữ từ JSON, tag, engine, icon theo nhóm, ô tìm có/không dấu, bộ lọc category/tag và nút tạo. Chưa render thumbnail PDF; dùng icon như phương án tùy chọn của đặc tả.
- Tạo dự án vẫn dùng form tên/thư mục/icon/color cũ, ghi trên Task nền và staging, không sửa các dự án cũ. Binary được copy nguyên bytes; `.overleaf-template.properties` lưu ID, default engine và mainFile. Các mẫu hiện đều dùng `main.tex`; app áp dụng engine mặc định khi mở root mới, Auto Compile vẫn theo lựa chọn người dùng.

### 28.2 Kiểm package và quyền cài

- `TemplatePrerequisites` dùng kpsewhich trong thư mục bin của latexmk đã chọn, kiểm `requiredPackages` với ánh xạ style/class/font tiếng Việt, engine mặc định, latexmk và Biber nếu `needsBiber`.
- Thiếu gói trên **TinyTeX đi kèm**: báo danh sách Anh/Việt, hiện modal xác nhận **Cài gói**. Chưa xác nhận hoặc bấm Hủy không chạy installer. Sau xác nhận, Task gọi tlmgr với argv/package đã kiểm tra, hiện package đang cài, có hủy và kill process tree; xong sẽ kiểm lại rồi mới thử tạo dự án. Windows dùng Perl riêng của TinyTeX để chạy tlmgr.pl, không ghép shell command từ metadata.
- Bản bundled được xác định bằng đường dẫn thật dưới app `target/TinyTeX`, `TinyTeX`, `tools/TinyTeX`, có texmf-dist và tlmgr. System TeX/MiKTeX hoặc TinyTeX ở vị trí khác chỉ báo và hướng dẫn cài/chọn toolchain, không được auto-install. Môi trường chưa có TeX không bị app tự ý sửa.
- UI preflight áp dụng khi tạo TEMPLATE; luồng blank/import/offline hiện có vẫn dùng được. Backend API trực tiếp dùng trong test không tự tải gói. Cài package cần mạng; lỗi tlmgr/khác năm TeX Live được báo, không tự update toàn bộ distribution.
- Gson **2.13.2** là dependency mới nhẹ để parse JSON vì client chưa có JSON parser tổng quát; không thêm thư viện UI/image lớn.

### 28.3 File và vai trò bàn giao

| Phạm vi member | File / trách nhiệm |
| --- | --- |
| M1 — Home/gallery | `ui/TemplateGallery.java`, `TemplateInstallDialog.java`, `HomeView.java`, CSS: cards/filter/search, chọn mẫu, preflight nền và consent UI |
| M1 — tích hợp workspace | `OverleafFxApp.java`: đọc engine mặc định của mẫu mới khi mở dự án |
| M1/M2 — catalog/import | `ui/backend/ProjectTemplates.java`, `ProjectImports.java`, `LocalBackend.java`, `ProjectGateway.java`: metadata/resources, text/binary, placeholder, legacy compatibility và compiler setting seam |
| M2 — toolchain | `TemplatePrerequisites.java`, `ProjectFailure.java`: kiểm gói/Biber, thông báo, installer có hủy/không sửa system TeX |
| M2 — compiler | `CompileWorkspace.publishPdf`: công bố `.log` cùng PDF trước callback thành công; smoke Unicode phát hiện race khi callback trả về trước lúc close() copy log |
| M5 — tests | `ProjectTemplatesTest`, `TemplatePrerequisitesTest`, `TemplateCatalogSmoke`, mở rộng `ProjectImportSmoke`, `NewProjectUiSmoke`, thêm regression log publication trong `CompileWorkspaceTest` |
| Cả nhóm — nội dung | `modules/client/src/main/resources/templates/` có catalog, metadata và en/vi nguồn; `LICENSE-TEMPLATES.md` MIT cho nội dung gốc; docs thư viện và ma trận validation |

Phân công là trách nhiệm module khi bàn giao, không xác nhận member tự viết thay đổi trong phiên AI này. Không thay protocol hoặc MongoDB. Không đưa TinyTeX/Pandoc/cache/bằng chứng test trong target vào commit.

### 28.4 Kiểm chứng và phạm vi

- **143/143 JUnit chọn lọc đạt**, common 9 + server 21 + client 113, không failure/error/skip; `target/template-library-tests.log`. Metadata có ID duy nhất, mainFile/resource tồn tại, đủ trường/ngôn ngữ; thay hết placeholder, copy PNG đúng header; kiểm alias/tìm kiếm; missing package/Biber, system không được cài, fake tlmgr + mapping/recheck, interruption; hồi quy cũ và log publication trước close đều đạt.
- **174/174 build thật đạt:** `TemplateCatalogSmoke` tạo mỗi mẫu với EN/VI trong đường dẫn chứa tiếng Việt, compile engine mặc định trước rồi hai engine còn lại. Kiểm PDF có trang; VI trích được chữ Việt; **final** TeX log không có `!`, undefined reference/citation hoặc yêu cầu chạy lại Biber. Không dùng toàn bộ console để kết luận unresolved vì lượt TeX đầu thường có warning rồi latexmk giải quyết ở các lượt sau.
- `ProjectImportSmoke` mở rộng chạy ma trận trên, sau đó example, Word, Markdown và GitHub công khai đều đạt. Log `target/template-import-smoke.log`: `TEMPLATE_CATALOG_SMOKE_OK builds=174`, `PROJECT_IMPORT_SMOKE_OK`. Home `target/template-import-smoke-03`, CSV/log từng lượt trong `template-matrix/evidence/`. [TEMPLATE_VALIDATION.md](TEMPLATE_VALIDATION.md) ghi đủ kết quả từng mẫu/engine/ngôn ngữ.
- `NewProjectUiSmoke` mở rộng đạt menu 9 nhóm Việt/Anh, category được chọn khi mở gallery, tìm “luận văn”/“thesis”, lọc tag, tạo luận văn đúng file chương 5 và mở main.tex/XeLaTeX. Các kiểm thử storage/move cũ vẫn đạt. Mock UI không cài trước xác nhận/khi Cancel; xác nhận gọi đúng installer rồi recheck và retry. Log `target/template-library-ui.log`, ảnh scene `target/template-library-ui-02/template-gallery.png` đã xem. Không thực hiện download gói thật chỉ để test missing; dùng mock tlmgr theo đặc tả.
- Spotless các file Java sửa/thêm đạt, log `target/template-library-format.log`. Nền kiểm chứng Windows/TinyTeX hiện có; chưa chạy native matrix macOS/Linux. Helper/generator dưới `target/` là công cụ tạo dữ liệu của phiên, không cần để nạp/thêm mẫu và không được Git theo dõi.
- Build cuối qua `scripts/run-client.bat -BuildOnly` đạt `BUILD SUCCESS`; log `target/template-library-launcher-build.log`. Đã kiểm tra JAR client chứa catalog và đủ 29 metadata mẫu cùng hai starter nội bộ.
- Cách thêm mẫu mới và schema/manifest/placeholder/package mapping được ghi trong [TEMPLATE_LIBRARY.md](TEMPLATE_LIBRARY.md). Nội dung mẫu tự viết, không lấy từ Overleaf web; ví dụ citation/CV/thư phải thay và kiểm tra trước sử dụng thực tế.

Chạy `.\scripts\run-client.bat` sau khi đóng app cũ, vào **Dự án mới → Thêm mẫu dự án → Xem tất cả**. Không cần chuyển đổi hay tạo lại dự án cũ.

## 29. Kiểm thử lại và đối chiếu 8 tiêu chí với Overleaf web

Ngày **02/11/2026**, nhánh Network, HEAD runtime **2310f6c**. Đây là lượt review/test và cập nhật tài liệu; không sửa runtime hay dự án thật. Báo cáo cũ ngày 01/11 đã được thay bằng [đánh giá hiện tại](DANH_GIA_SO_SANH_OVERLEAF.md), đủ 8 tiêu chí, nguồn chính thức, giới hạn và việc cần làm tiếp.

- Chạy lại **143/143 JUnit chọn lọc đạt**, 0 failure/error/skip; common 9, server 21, client 113. Không chạy integration MongoDB. Log target/template-library-tests.log.
- Chạy lại **15 build native** trên ba engine: first/unchanged/edit/TikZ/template. Cache reuse được assert từ log, không chỉ dựa vào thời gian. Kết quả first/unchanged/edit (ms): pdfLaTeX **526/199/403**, XeLaTeX **915/239/461**, LuaLaTeX **2340/277/1075**. Một lượt/tình huống, tài liệu nhỏ, không phải benchmark đối chứng Overleaf.
- Năm smoke UI chạy lại đều đạt: **history/layout/auto**, **PDF liên tục + hyperlink**, **preview/export**, **toolbar**, **gallery/tạo dự án**. Log target/comparison-20261102-{history,pdf,export,editor,gallery}.log; home riêng cùng prefix. Preview/export PDF 7 trang khớp bytes, stale/error chặn export; PDF link dùng tài liệu 13 trang.
- Ma trận **174/174 compile mẫu** và import Word/Markdown/GitHub là bằng chứng đã chạy cùng ngày ở §28; không tuyên bố đã rerun toàn bộ trong lượt này.
- Đối chiếu website chính thức: timeout **10s Free / 240s Premium**; web có cache, compiler/TeX Live selection, Vim/Emacs, project search, SyncTeX và Error Assist. Free có 1 collaborator và basic AI allowance; history Free 24 giờ cùng bản có label. URL hỗ trợ từng nhận định ở báo cáo.
- Lợi thế đã có: chủ động toolchain/timeout, luồng dữ liệu local, giải thích lỗi Việt bằng rule offline, history local không paywall trong code, mẫu Việt/Anh dùng offline khi package đã sẵn. PDF thật/toolbar/gallery/layout là cải thiện so với app trước; không tự động chứng minh vượt web.
- Khoảng thiếu: SyncTeX/texlab/plugin/multi-cursor/Git branch-merge; cộng tác mới main.tex, pending queue/server room còn RAM; chưa ACL account/project đầy đủ; file/history chưa mã hóa trên ổ đĩa. History giới hạn **1.024 revision / 128 MiB/project**, mỗi bản text 2 MiB. Server mặc định 64 kết nối không phải đã stress-test 64 người.
- Chưa ngắt internet toàn máy, benchmark app-vs-web, thử máy sạch hoặc native macOS/Linux. TLS có test token/hostname nhưng chưa security audit. Không suy diễn Maven offline là máy không mạng. Installer missing-package dùng mock, không download để thử.
- Helper chạy lại nằm target/comparison-{compile,history,pdf,export,editor,gallery}.cmd, đều Git ignore. Không commit/push ở lượt này.

## 30. Cải thiện tiêu chí 4: SyncTeX hai chiều và PDF lớn

Ngày **02/11/2026**, theo yêu cầu cải thiện PDF/hiệu năng. Thay đổi đang ở working tree; không commit/push. Bản trước đã có PDF thật/scroll/link/export cùng bytes; phiên này bổ sung đồng bộ nguồn/PDF và tối ưu renderer, không thay giao thức hay MongoDB.

### 30.1 Chức năng và file bàn giao

- **M1 — giao diện workspace:** OverleafFxApp nối nút **Đến PDF / Show in PDF** và nhấp đúp PDF. Forward lấy file/dòng/cột ở con trỏ, inverse mở đúng file chương/dòng; PDF only tự về split, cửa sổ editor được đưa lên trước. Task chạy nền và kiểm project/file/revision/snapshot trước callback; fingerprint nguồn trên đĩa được kiểm trước query. Giữ guard stale/error/export.
- **M1 — viewer:** PdfPreviewPane hiển thị vị trí bằng marker vàng; Back quay lại; link một lần vẫn riêng. Actions dùng FlowPane để có thể xuống hàng. Sync bị khóa khi PDF không current; thiếu tool/index chỉ báo cần recompile/kiểm toolchain, không phá preview/export.
- **M2 — compiler:** LatexCompilerService thêm -synctex=1. CompileWorkspace publish .synctex.gz trước callback, remap Input về đường dẫn tương đối và LF để index không phụ thuộc staging ASCII đã bị xóa. Bỏ index cũ khi build thành công không có index mới.
- **M2 — SyncTeX:** SyncTexService nạp index bất biến theo PDF, gọi CLI view/edit bằng argv, timeout 10s; scratch chỉ chứa index và PDF placeholder, không copy hàng chục MB PDF mỗi click. Không chạy command editor/viewer từ output hoặc SYNCTEX_EDITOR/VIEWER; chỉ cho nguồn .tex trong real project root. Có kiểm path/symlink, cap index 32 MiB và output đọc 64 KiB.
- **M2 — render:** PdfSnapshot.RenderSession tái sử dụng PDDocument/PDFRenderer trên luồng render của viewer. Đóng session khi reset/đổi PDF/close; generation guard cũ vẫn giữ. Giới hạn 24 triệu pixel ảnh giữ, 12 triệu/trang vẫn áp dụng, không phải giới hạn tổng RAM. Chuyển tọa độ SyncTeX có crop/rotation/MediaBox lệch gốc.
- **M5 — kiểm thử:** thêm SyncTexServiceTest, SyncTexSmoke, SyncTexUiSmoke, PdfPerformanceSmoke; mở rộng CompileWorkspaceTest, LatexCompilerServiceTest, PdfSnapshotTest.
- Phân công là phạm vi trách nhiệm module khi bàn giao, không phải xác nhận member tự viết code của phiên AI.

### 30.2 Kết quả xác nhận

- **148/148 JUnit chọn lọc đạt** (common 9 + server 21 + client 118), không failure/error/skip; target/pdf-performance-tests.log. Không chạy MongoDB integration.
- **6/6 native SyncTeX build đạt**: 3 engine × root ASCII/tiếng Việt, file chương có dấu cách, forward/inverse đúng file/dòng, chặn đi ngoài project. target/synctex-native-03; log target/synctex-native.log.
- **SyncTexUiSmoke đạt**: nút source → PDF, double-click PDF → file chương/dòng 4, từ PDF only hiện lại split, stale chặn. target/synctex-ui-03, log target/synctex-ui.log; đã xem ảnh synctex-preview.png.
- Hồi quy **PdfPreviewUiSmoke đạt**: PDF 7 trang, pixels, export đúng bytes, stale/error/recovery/isolation; target/pdf-sync-export-01.log.
- Hồi quy **PdfContinuousUiSmoke đạt**: PDF 13 trang, scroll/link/Back/zoom/reset/bounded images; target/pdf-sync-continuous-01.log.
- **Benchmark 248 trang đạt**, 120 file chương + 12 PNG + TikZ/bảng/hyperref; PDF 11.708.355 byte. Native 11 compile (cold 1, warm 5, small edit 5), so 24 mẫu render mỗi chiến lược, 18 lượt điều hướng UI, kiểm pixel lấy mẫu/export và SyncTeX chương cuối.
- Cold 7.855 ms (n=1), warm p50/p95 267/289 ms, small edit 4.794/4.938 ms. Render mở lại document mỗi trang p50/p95 **26/139 ms**, reuse **12/85 ms**. UI navigation **22/68 ms**; metadata read 364 ms, first visible sau nạp snapshot 368 ms. Viewer mở document 1 lần; max 3 ảnh / 3.271.752 pixel sau đợi renderer ổn định. target/pdf-performance-02/results.md và target/pdf-performance.log.
- Chỉ kết luận cải thiện trong workload local đã đo, chưa app-vs-Overleaf benchmark; chưa đo RSS/native/GPU, frame-time liên tục, nghìn trang hay macOS/Linux. p95 từ mẫu nhỏ không phải cam kết. SyncTeX phụ thuộc TeX và có thể chỉ chính xác tới dòng/macro/float gần nhất.
- Spotless các file Java sửa/thêm đạt; target/pdf-performance-format.log. Helper/generator/binary/evidence trong target Git ignore.
- Build cuối bằng scripts/run-client.bat -BuildOnly đạt BUILD SUCCESS; target/pdf-performance-launcher-build.log.

### 30.3 Sử dụng và điều kiện

Đóng app cũ rồi chạy scripts/run-client.bat; mở dự án và Recompile một lần để có index mới. Đặt con trỏ và bấm Đến PDF, hoặc nhấp đúp đoạn văn trên PDF. Cần synctex cạnh latexmk đã chọn. Phiên này cài package synctex vào target/TinyTeX riêng của workspace để test; không sửa TeX hệ thống và không tự cài trên máy khác. Các file binary target không đi kèm commit code.

Hướng dẫn/benchmark/tái lập và giới hạn ở [PDF_PERFORMANCE.md](PDF_PERFORMANCE.md); README thêm cách điều hướng và test selector mới. Báo cáo [so sánh Overleaf](DANH_GIA_SO_SANH_OVERLEAF.md) đã cập nhật tiêu chí 4; các nhận định cũ “chưa SyncTeX” ở mục lịch sử handoff không còn là trạng thái hiện tại.

## 31. Chuẩn bị chạy source trên macOS

Ngày **03/11/2026**, theo yêu cầu cài/chạy trên Mac. Không có máy Mac trong phiên; không khẳng định JavaFX/compile/SyncTeX native macOS đã được test.

- **M1 — launcher:** scripts/run-client.sh kiểm full JDK 21+/Maven, tìm JDK Homebrew hoặc java_home trên Darwin khi JAVA_HOME chưa hợp lệ, thêm Homebrew bin/MacTeX vào PATH cho Finder. Giữ JAVA_HOME hợp lệ do người dùng chọn; JDK cũ báo lỗi rõ. Build Maven dùng cache target/maven-cache và resolve JavaFX native của máy đang build.
- Thêm --check (chỉ báo JDK/Maven và optional TeX tools), --build-only/-BuildOnly; lỗi flag không được bỏ qua. Không tự cài brew/JDK/TeX hoặc sửa shell profile/system.
- **Overleaf Desktop.command** ở root để mở Terminal rồi chạy launcher bằng nhấp đúp; cần chmod +x trên Mac, tài liệu có lệnh. Chưa phải .app/.dmg độc lập, chưa bundle JRE/TeX/sign/notarize.
- **docs/MACOS.md**: cài Homebrew JDK 21/Maven/MacTeX no GUI, JAVA_HOME dùng brew prefix (không hardcode Intel/ARM), kiểm tool, build/chạy, Finder, Settings latexmk=/Library/TeX/texbin/latexmk, SyncTeX, optional Pandoc/Git và lỗi thường gặp. README đã link hướng dẫn.
- Không chép binary/cache Windows sang Mac; cần source hiện tại. Các thay đổi local chưa được push nên clone remote có thể chưa chứa các tính năng mới. Local editing không cần MongoDB/server; cộng tác cần server riêng.
- Đối chiếu lệnh cài với trang chính thức Homebrew/MacTeX ngày 03/11/2026; không cam kết mọi macOS cũ đều được hỗ trợ, cần xem yêu cầu của bản JDK/TeX/Homebrew đang dùng.
- **Kiểm tra đạt:** POSIX sh -n cho hai entry point và mock Darwin/arm64, Homebrew JDK, đường dẫn project/JDK có dấu cách, check/build/run, JDK 17, thiếu Maven, flag sai. Helper target/check-macos-launcher.sh; evidence target/macos-launcher.Cn8NhF (các lượt sau tạo thư mục mới), kết quả MACOS_LAUNCHER_MOCK_OK. Không chạy Homebrew thật hoặc cài phần mềm hệ thống.
- .gitattributes hiện đã buộc LF mặc định nên shell/.command giữ newline phù hợp; Git mode launcher 100644, dùng sh hoặc chmod theo docs.
- Không thay runtime Java ở lượt này; không lặp lại 148 JUnit của §30 chỉ vì thay launcher/docs. Cần nghiệm thu trực tiếp trên Mac bằng --check, mở app, bộ gõ Việt, ba engine/PDF/export/SyncTeX. Không commit/push.

## 32. Bắt đầu chuyển sang Python + Next.js desktop

Ngày **03/11/2026**. Người dùng muốn bỏ Java và đã chọn **ứng dụng desktop có cửa sổ riêng**. Đã tạo bản đầu trong apps/desktop và apps/frontend; **chưa hoàn tất chuyển toàn bộ tính năng**. Bản Java và launcher cũ vẫn giữ để đối chiếu, không xóa dự án/tài khoản thật. Xem [kiến trúc và trạng thái](PYTHON_NEXTJS_MIGRATION.md).

### 32.1 Stack và phạm vi đã làm

- Python 3.12+ / FastAPI / Uvicorn xử lý local project/file/template/compile; pywebview mở cửa sổ desktop. Frontend Next.js 16.4.4 + React/TypeScript + Tailwind, CodeMirror và PDF.js. Next static export được Python phục vụ trên loopback/cổng ngẫu nhiên; không chạy Java hoặc Node server khi UI đã build.
- apps/desktop/src/overleaf_desktop: store.py (metadata JSON, template, file/fingerprint), compiler.py (async subprocess, log stream/Stop/timeout, PDF snapshot), api.py (Authorization/Origin/Host, route/static), main.py (cửa sổ và dialog export).
- apps/frontend: dashboard tìm/tạo dự án, gallery 29 mẫu/9 nhóm/nguồn en-vi, file tree, tạo folder/file, chỉnh sửa/lưu Cmd/Ctrl+S, outline và toolbar cơ bản; compile engine chọn được, log stream, PDF thật/scroll/link/zoom/export và stale/error guard.
- Source template/assets dùng lại dưới dạng dữ liệu từ modules/client/src/main/resources/templates; không gọi Java. Chưa copy tài nguyên vào gói phát hành độc lập. .tex/.bib/PNG vẫn cùng định dạng, metadata/account/history Java chưa tự chuyển.
- Mặc định dữ liệu mới **~/OverleafDesktopPython**, có OVERLEAF_PYTHON_HOME hoặc --home khi chạy module. Không chạm ~/OverleafDesktop. Metadata mới .project.json/ID UUID, không đọc account Java.
- scripts/run-python-desktop.bat/.sh tạo venv/cài desktop dependencies nếu thiếu, build frontend khi chưa có out (hoặc --build), sau đó mở cửa sổ. Lệnh Java cũ vẫn chạy Java; không đổi mặc định ngầm.
- Dependency có package-lock.json và uv.lock; uv sync --frozen cho đúng Python lock. Launcher pip có thể resolve trong dải pyproject. Binary/.venv/node_modules/out/.next/cache/worker/evidence bị Git ignore.
- Host loopback và token theo phiên không thay thế auth/ACL của server nhiều người. Chưa có WebSocket cộng tác hoặc tương thích TCP Java. Không triển khai API local này như server multiuser chưa bổ sung các lớp cần thiết.
- M1 bàn giao frontend/window; M2 compiler/PDF/store; M3 API/session và giao thức cộng tác tiếp theo; M4 engine đồng bộ chưa port; M5 dữ liệu/test/installer. Đây là phạm vi trách nhiệm, không xác nhận member tự viết code trong phiên AI.

### 32.2 Kiểm chứng

- **71 pytest đạt**, 29 mẫu × vi/en materialize, assets/escape, save/reopen/fingerprint/path, auth/origin/host, stale PDF, native export mock kiểm bytes và source đổi khi save dialog mở. Có deprecation warning TestClient/httpx, không failure. target/python-next-tests.log.
- **Next static export + TypeScript đạt**; launcher --build đạt, target/python-next-build.log và target/python-launcher-build.log.
- **6 PDF build thật đạt** qua Python: 3 engine × root ASCII/tiếng Việt, research article nhiều file. Chưa chạy lại toàn bộ 174 build bằng backend mới. target/python-native-02, log target/python-native.log.
- **Native Stop/timeout/recovery đạt**, TeX loop bị dừng rồi compile lại thành công; target/python-cancel-01, log target/python-cancel.log.
- **Cửa sổ pywebview/WebView2 thật** ở chế độ ẩn đã load UI và API thành công; target/python-desktop-ui-02, log target/python-desktop-ui.log. Launcher --smoke cũng đạt với home riêng target/python-launcher-ui-01, log target/python-launcher-ui.log.
- **UI regression Edge headless đạt**: tạo dự án, sửa/lưu, folder, compile, PDF 3 trang/link, export byte-identical, source stale/compile lỗi vẫn giữ bản PDF trước và khóa export. target/python-web-ui-01, log target/python-web-ui.log; đã xem dashboard.png/workspace.png. Browser test dùng download fallback; dialog hệ điều hành chưa được tự bấm, native export kiểm qua unit mock.
- Ruff format/check F,E9 đạt; POSIX shell syntax đạt. Máy hiện tại Windows, chưa native Mac/Linux hoặc build installer. Test Java/số đo 248 trang không phải test coverage/benchmark của Python mới.

### 32.3 Việc cần tiếp tục để thay hoàn toàn Java

History/Auto Compile/settings/i18n và toàn bộ toolbar; SyncTeX; upload/ZIP/Word/Markdown/GitHub/storage/import dự án cũ; TeX preflight và Error Explainer; server/cộng tác/queue bền vững/quyền; tài khoản/database; benchmark Python/PDF.js lớn; đóng gói/sign/notarize .exe/.app/.dmg. Chi tiết ở bảng trạng thái migration. Bản đầu chưa có tính năng thì không tạo nút giả hoặc tuyên bố đã port xong.

Chạy Windows: scripts/run-python-desktop.bat. Mac: sh scripts/run-python-desktop.sh sau khi có Python 3.12+/Node để build/MacTeX và native pywebview dependencies. UI đã build không cần Node server; bộ TeX vẫn là dependency riêng. Không commit/push trong lượt này.

## 33. Đồng bộ chức năng Java sang Python + Next.js desktop

Cập nhật theo yêu cầu chuyển **đầy đủ các tính năng Java thực sự có trong dự án**. Mục 32 là lịch sử bản scaffold; các phần “chưa có history/SyncTeX/import/account/network” của mục đó đã được triển khai ở lượt này. Xem [bảng đối chiếu](JAVA_PYTHON_PARITY.md), [hướng dẫn chạy/bằng chứng](PYTHON_NEXTJS_MIGRATION.md).

### 33.1 Đã code và nối UI

- **Account/profile/settings/activity:** state.py lưu PBKDF2-SHA256, remember/offline/logout, name/email/password, avatar crop/remove, theme/font/word wrap/autoclose/autosave/Auto Compile/lang/compiler/timeout/storage/reset. AuthView, ProfilePanel, SettingsPanel và heatmap dùng dữ liệu thật.
- **Project/file/import:** store.py + files.py quản lý location catalog, custom parent/default storage, rename/icon/color/trash, move kiểm bytes/giữ backup; tạo file/folder/nested target, context menu, binary open, staged upload/drag/drop/paste/empty folder/queue/cancel/rollback, copy project/build, HTTPS và .bib. ZIP/folder/Pandoc DOCX/Markdown/GitHub public HTTPS có staging và dừng process tree khi hủy.
- **Java migration:** đọc đúng Java properties, cả catalog dùng **đường dẫn làm key**, projects ở default/custom parent, .overleaf-project icon/color, account hash/settings/avatar/activity, .overleaf-history metadata/checksum. Tạo bản sao và ledger chống nhập trùng, không sửa project gốc. 438 asset/template đã vào package Python, không phụ thuộc thư mục Java lúc chạy.
- **Editor/workspace:** CodeMirror và toolbar đầy đủ theo thứ tự Math/Link/Comment/Reference/Citation/Figure/Table/List/Decrease/Increase indent; dialog quét label/.bib/images, chống stale insertion, escaping/snippet validation. Outline cuộn/resize, editor/PDF divider, layout split/editor/PDF/detach/focus, keyboard save/compile, watcher, save-on-switch/close.
- **History:** history.py preimage/checkpoint/dedup/label/filter/diff/restore/checksum/quota; HistoryPanel ba cột. Restore bị khóa trong phòng; giữ nội dung hiện tại trước khi ghi đè.
- **Compile/TeX:** compiler.py stream log, 3 engines, timeout tùy chọn/0 unlimited, Stop process tree, clean cache, Unicode ASCII staging, missing-glyph guard. toolchain.py diagnostics/package/Biber preflight, cài bundled TinyTeX sau consent, cancel/shutdown cleanup.
- **Error Explainer:** chuyển **đủ 28 rule gốc Java** thành resources/error-rules.json; explainer.py thay placeholder lệnh/tệp/context, why/fix Việt/Anh và exit/start/timeout/stop/glyph. Wrapper “exit code 12” không thay cho chẩn đoán lỗi gốc.
- **PDF/SyncTeX:** PDF.js thật, canvas quanh viewport, scroll/link/Back/zoom, immutable bytes và source fingerprint/dirty/export guard; native save dialog kiểm trước/sau. synctex.py giữ index theo snapshot, forward/inverse cho tệp con/Unicode; pdfGeometry.ts dùng MediaBox/CropBox/rotation/zoom. Closing callback dùng staged buffer trong Python, không gọi evaluate_js để tránh WebView2 deadlock.
- **Networking M3/M4:** network.py port TCP/TLS wire v1/v2, AUTH/identity/limits/bounded outbox/heartbeat, room presence/cursor, OT/Lamport/history, ACK/ACK_BATCH, offline pending/reconnect/atomic chunk RESYNC/rebase/ROOM_UNKNOWN. NetworkPanel và useParity nối vào editor; main.tex-only, shared token và RAM queue đúng phạm vi Java.
- **M5:** persistence.py port User/Project/Document repositories và DatabaseService bằng pymongo tùy chọn; giữ trạng thái prototype chưa gắn UI/room, không tạo kết nối DB giả. Có test mongomock.
- **Launcher/deploy:** run-python-desktop/server/console .bat/.sh; thêm Overleaf Python Desktop.command. deploy/python có Dockerfile/Compose/hướng dẫn env/TLS PEM. API HTTP local không dùng làm server đa người.

### 33.2 Vai trò bàn giao

| Vai trò | Folder/file Python/Next.js tiếp quản |
| --- | --- |
| M1 GUI/client | apps/frontend/app, components, lib/useParity, desktop/main.py native window/dialogs |
| M2 compile/PDF | compiler.py, toolchain.py, explainer.py, synctex.py, PdfViewer/pdfGeometry |
| M3 networking | network.py framing/TCP/TLS/session/client, api/parity_api, scripts server/console, deploy/python |
| M4 synchronization | network.py operation/transform/history/ACK/pending/RESYNC/presence/cursor |
| M5 data/test | store.py, state.py, files.py, history.py, persistence.py, packaged resources, tests |

Đây là phân công tiếp quản chức năng; không khẳng định từng thành viên tự viết các thay đổi trong phiên AI này.

### 33.3 Kiểm chứng và giới hạn

- 122 pytest đạt; gồm data/API/history/upload/import/protocol/TCP/TLS và native bridge, M5 mock. Có một deprecation warning TestClient/httpx.
- 186 build PDF thật đạt: 31 cấu hình (29 gallery + blank/example) × vi/en × 3 engines, target/python-parity-matrix-01/matrix.json. Script matrix ban đầu phát hiện fixture benchmark 496 trang; đã sửa fixture và chạy benchmark riêng đúng 248 trang.
- 6 native SyncTeX forward/inverse vào tệp con ở ASCII/Unicode đạt; 12 PDF.js geometry combinations crop/media offset/rotation/zoom đạt.
- Pandoc DOCX/Markdown và HTTPS thật đạt; Python clients tương tác Java server thật qua AUTH/UTF16/ACK/offline/RESYNC đạt.
- UI Edge headless: account/remember/profile, toolbar/history label/diff/upload/nested file/outline/layout/F5/Auto Compile/settings/English/activity đạt; PDF 3 trang/link/byte export/stale/error đạt. Evidence target/python-parity-features-05 và python-parity-ui-03.
- Fixture 248 trang: native cold 1766 ms/warm 594 ms; viewer lần kiểm song song ~1619 ms đến trang đầu, tối đa 3 canvas/~4 MB pixels. Report target/python-parity-large-01. Không so sánh tốc độ với Overleaf web hoặc suy rộng sang sách nhiều hình/TikZ.
- Native WebView2 window load/close đạt; dialog export/migration/upload được kiểm unit và logic backend, chưa tự bấm mọi hộp thoại OS. Mac/Linux chưa native nghiệm thu; chưa installer signed/notarized, Docker Compose chưa chạy trên máy hiện tại, Mongo Atlas chưa kiểm thật.
- Room vẫn main.tex-only, shared token, OT đơn giản, room/pending RAM; không tuyên bố full CRDT/multi-file ACL/bền vững khi restart. Unicode root compile đúng nhưng cache staging chưa tối ưu bằng ASCII root. Các giới hạn này được ghi rõ, không thay bằng nút giả.

Windows build/chạy: `scripts\run-python-desktop.bat --build`, rồi `scripts\run-python-desktop.bat`. Mac/Linux: `sh scripts/run-python-desktop.sh --build`, rồi `sh scripts/run-python-desktop.sh`. Dữ liệu mặc định ~/OverleafDesktopPython; Java giữ nguyên để đối chiếu. Không commit/push trong lượt này.

Bổ sung nghiệm thu mục 33: UI Chromium tại target/python-parity-features-10 đạt cả rename tệp còn chưa lưu, system dark và đúng template example. Đã thêm trạng thái tải ban đầu để static HTML không render heatmap theo ngày build; heatmap có streak/ngày hoạt động như Java. Hai lần Edge bổ sung kẹt ở startup đã được dọn đúng process; không tính là test đạt. Wheel backend kiểm đủ 438 asset và 28 error rule. Build/TypeScript và 122 pytest vẫn đạt; không tạo installer hoặc commit/push.

## 34. Sửa compile ZIP LTJournalArticle: thiếu package/font và cache lỗi

Đã tái hiện ZIP `_Article__Hands_on_Training_for_Mitigating_vulnerabilites__Toan_.zip` trên Python. TinyTeX đi kèm thiếu `footmisc`, `abstract`, `titling`; class dùng mathpazo còn cần font `palatino`, `mathpazo` (tlmgr tự cài dependency `fpl`). Đã cài vào **target/TinyTeX**, không đổi TeX hệ thống hoặc nội dung nguồn của bài. Các package cài local không vào Git; máy khác dùng cùng class cần chuẩn bị các package này.

Ngoài dependency, latexmk giữ lần chạy lỗi trong .fdb_latexmk nên cài package xong vẫn có thể trả lại lỗi cũ dù source không đổi. compiler.py dùng marker `build/.overleaf-build-failed` và log fatal để thêm `-g` cho lượt phục hồi, xóa marker chỉ sau khi xác minh PDF thành công. Warm build bình thường giữ cache. Cách dùng `-g`: [latexmk manual](https://tug.ctan.org/support/latexmk/latexmk.pdf).

Error Explainer phân biệt thiếu TFM (`ppl*` → palatino, `zpl*` → mathpazo) với chọn sai engine; không hướng dẫn đổi fontspec trong trường hợp thiếu metric. UI không in `:0` khi lỗi chưa xác định file/dòng. Log bài còn cảnh báo fancyhdr headheight nhỏ và biblatex không có driver cho kiểu `masterthesis`; đây là cảnh báo định dạng nguồn, không chặn build. Chưa sửa class/bibliography của tác giả.

Kiểm chứng: 124 pytest đạt, có regression cache qua restart/legacy fatal/warm và font metrics; Next.js build/TypeScript đạt. Native trên bản nhập cô lập thành công 6 trang. Đã biên dịch cả project người dùng hiện có: 6 trang, 1.051.412 bytes; lượt phục hồi 7.985 ms, lượt warm 297 ms, cùng SHA-256 `792c8fd916e2b9fb579f4b5d46a0098dec1faefa747dfdeae9183cd6f59f4ca2`. Fingerprint source trước/sau không đổi. PDF xác minh: target/article-zip-repro-01/verified-article.pdf; report user-project-verification.json. Không sửa ZIP gốc, không commit/push.

## 35. Khôi phục giao diện Hoạt động và trò chơi Snake trên Python + Next.js

Ngày **04/11/2026**. Cập nhật thành phần `ActivityHeatmap.tsx` để đồng bộ giao diện theo bản Java gốc (Ảnh 1) và bổ sung trò chơi Snake (Ảnh 3, Ảnh 4):

- **Giao diện thẻ Hoạt động (Ảnh 1):**
  - Tiêu đề `Hoạt động` và nhãn phạm vi `52 tuần qua`.
  - 3 khối số liệu thống kê rõ ràng: tổng số `chỉnh sửa` (tính từ `activity.edits`), số lần `biên dịch` (từ `activity.compiles`), và `chuỗi ngày` liên tiếp (streak).
  - Lưới đóng góp 52 tuần × 7 ngày được vẽ trên Canvas với kích thước, khoảng cách ô, bo góc chuẩn mực; nhãn tháng bên dưới (`thg 11` ... `thg 10` cho tiếng Việt, `Nov` ... `Oct` cho tiếng Anh).
  - Dải chú thích cấp độ (Ít → Nhiều) và dòng hướng dẫn "Nhấp đúp vào lưới · hoặc bấm Chơi Snake".
  - Nút bấm `▷ Chơi Snake` (xanh nhạt bo góc) kèm chip hiển thị kỷ lục `BEST <điểm>`.

- **Trò chơi Snake (Ảnh 3):**
  - Chuyển chế độ bằng nút `▷ Chơi Snake` hoặc nhấp đúp vào lưới.
  - Nút chuyển thành `← Quay lại lưới` để thoát bất kỳ lúc nào.
  - Rắn di chuyển trên lưới 52×7 bằng phím mũi tên hoặc WASD với tốc độ 180ms/bước; đầu rắn màu xanh đậm, thân xanh lục; mồi hình quả tròn màu hổ phách có bóng phản chiếu. Thuật toán chọn mồi ưu tiên các ô ngày có hoạt động đóng góp thực tế.
  - Thanh trạng thái game dưới lưới: `SNAKE    SCORE <điểm>    BEST <kỷ lục>` bên trái và `Esc để thoát` bên phải.

- **Màn hình Kết thúc game (Ảnh 4):**
  - Lưới tối màu với lớp phủ mờ `rgba(20, 27, 23, 0.45)`.
  - Hộp thoại pixel art cổ điển với viền pixel đôi, bóng đổ 4px, icon đầu lâu (hoặc cúp vàng khi thắng) vẽ từ ma trận pixel thật.
  - Tiêu đề `KẾT THÚC` (hoặc `THẮNG RỒI!`), dòng điểm số và kỷ lục, đường phân cách, cùng hướng dẫn điều khiển: `SPACE HOẶC NHẤP ĐÚP ĐỂ CHƠI LẠI` và `ESC HOẶC ← QUAY LẠI LƯỚI`.

- **Tương thích:**
  - Bảo tồn 364 thẻ `span` ẩn để các bài kiểm thử Playwright (`test-features.mjs`) tiếp tục chạy chính xác.
  - Build static Next.js (`npm run build`) và TypeScript typecheck đạt 0 lỗi.

## 35. Phục hồi launcher Windows khi mất Python runtime (03/11/2026)

Lỗi `uv trampoline failed to spawn Python child process` xuất hiện vì `.venv/pyvenv.cfg` vẫn trỏ tới `target/python-runtimes/cpython-3.12-windows-x86_64-none` nhưng runtime đã mất. Đã khôi phục CPython 3.12.16 bằng uv có sẵn trong repository; dependency trong venv cũ tiếp tục dùng được, không thay đổi dữ liệu dự án.

Thêm `scripts/ensure-python.ps1`, gọi trước khi launcher desktop kiểm dependency: kiểm Python thực sự chạy được và đủ phiên bản, khôi phục runtime bằng uv khi có sẵn; nếu cần tạo lại venv thì giữ bản cũ dưới tên `.venv.backup-<id>` (được Git ignore). Không có uv thì thử Python đã cài, bỏ Windows Store alias và báo hướng dẫn khi thiếu. README giải thích thư mục runtime cần giữ. Server/console vẫn dùng cùng venv; có thể chạy launcher desktop để phục hồi trước.

Kiểm chứng lần này: tình huống runtime mất được phục hồi thành công; `run-python-desktop.bat --build` đạt, Next.js static build/TypeScript đạt và npm audit không có lỗ hổng. `run-python-desktop.bat --smoke` trên home cô lập `target/launcher-repair-smoke` trả `PYTHON_DESKTOP_WINDOW_SMOKE_OK`, thoát mã 0. Không chạy lại toàn bộ pytest vì thay đổi chỉ ở launcher/tài liệu.

## 36. Sửa cuộn editor workspace (04/11/2026)

Nguyên nhân: CSS `.editor-area > .cm-theme` không khớp wrapper `.cm-theme-light`/`.cm-theme-dark` do UIW tạo khi truyền theme dạng chuỗi. Wrapper thiếu chiều cao giới hạn, CodeMirror nở theo nội dung rồi bị `overflow:hidden` của editor-area cắt, gây không cuộn được đầy đủ.

Thêm class ổn định `source-editor` vào CodeMirror trong `app/page.tsx`, áp dụng height 100%/min-height 0 cho wrapper và editor; scroller giữ overflow auto, giới hạn overscroll trong editor. Không phụ thuộc tên lớp theme. Đã build lại UI static để lần mở app tiếp theo dùng bản sửa.

Kiểm chứng: Next.js production build/TypeScript đạt. Bổ sung browser regression vào `scripts/test-features.mjs` với 240 dòng dài: wheel dọc thực sự đổi scrollTop, wheel ngang đổi scrollLeft, chiều cao scroller không vượt vùng editor. Chạy toàn bộ script trên Edge headless đạt cả kiểm cuộn và các luồng UI sẵn có; bằng chứng trong `target/editor-scroll-01/features.png`. Không thay đổi source dự án của người dùng.

## 37. Cây thư mục theo nhánh và sidebar kéo ngang (04/11/2026)

Thêm `lib/fileTree.ts`: sắp xếp theo từng cấp cha/con, thư mục trước file trong cùng cấp, tên có natural numeric order. Trước đây danh sách phẳng từ backend đặt thư mục trước toàn cục khiến con nằm cách xa cha. Workspace dùng thứ tự mới, thụt mỗi cấp 22px, mũi tên đóng/mở và tô sáng thư mục đích. Sau tạo/upload, mở nhánh thư mục đích để không giấu file mới sau nhánh đã thu gọn.

Thêm separator dọc giữa sidebar và editor: kéo ngang thay đổi đồng thời chiều rộng file tree/outline, mặc định 270px, lưu `sidebarWidth` vào settings, giới hạn 200–600px và chừa vùng editor khi kéo. Separator bị ẩn cùng sidebar trong focus/detached PDF. Cỡ chữ tên file và mục outline tăng lên 14px, tiêu đề outline 11px, đường dẫn đích 12px.

Kiểm chứng: 124 pytest đạt (một deprecation warning đã có); Next.js production build/TypeScript đạt. Browser Edge headless `test-features.mjs` đạt, bổ sung kiểm thứ tự cha/con trước root file, đóng/mở nhánh và kéo sidebar rộng thêm thực tế. Screenshot `target/sidebar-tree-01/features.png`. Đã build UI mới; người dùng đóng/mở lại app để nhận thay đổi. Không di chuyển hoặc đổi cấu trúc file vật lý của dự án.

## 38. Icon Comment mở Review thay vì chèn phần trăm (04/11/2026)

`EditorToolbar` chuyển `editor-add-comment` sang callback Review, không gọi `lineEdit(comment)`. Chọn đoạn rồi bấm icon sẽ mở khung bên trái editor với ô nhận xét; không chọn đoạn thì gắn theo vị trí/dòng. `ReviewPanel.tsx` có tên người dùng/thời gian, trích đoạn, trả lời, giải quyết/mở lại, xóa có xác nhận, lọc tệp hiện tại/tổng quan và hiện đã giải quyết. Click vị trí nhận xét mở tệp và chọn đoạn nguồn. `reviewHighlights.ts` dùng CodeMirror Decoration/StateField tô sáng đoạn chưa giải quyết, map qua thay đổi; tìm lại quote khi cần, không đoán vị trí nếu quote mất hoặc trùng nhiều nơi.

Backend `review.py` lưu atomic/lock trong `.overleaf-review.json` của từng dự án; route GET/POST `/api/projects/{project}/review` dùng session guard API sẵn có. Tên tác giả lấy từ session backend; giới hạn 2.000 thread, 200 message/thread, 10.000 ký tự/message, 20.000 ký tự quote, 8 MiB/project; kiểm path/symlink. File ẩn không vào cây source/fingerprint compile. Source và PDF không bị chèn nhận xét. Lưu bền qua đóng/mở app nhưng **chưa đồng bộ room TCP, chưa track changes/ACL review**. File đổi tên/xóa có thể làm thread giữ đường dẫn cũ; không tự gắn sang file khác.

Kiểm chứng: 126 pytest đạt, gồm test review persistence/reply/resolve/delete, không đổi source/fingerprint, path traversal/validation. Next.js production build/TypeScript đạt. Edge browser regression toàn luồng đạt ở `target/review-comments-03`: đăng/reply/resolve/reopen, đóng/mở panel còn thread, Decoration hiện và nội dung editor trước/sau giữ nguyên. Screenshot `review.png` đã kiểm trực quan. Lần browser đầu chỉ vướng assertion trailing newline DOM, đã sửa test so sánh đúng before/after; không coi lượt lỗi là pass. README/migration/parity cập nhật ngữ nghĩa Comment mới; đã build UI để mở lại app nhận thay đổi.

## 39. Thanh icon trái và panel Tệp/Review/Chat độc lập (04/11/2026)

Workspace có activity bar 46px ngoài cùng bên trái, chỉ ba icon theo phạm vi yêu cầu: tệp, nhận xét (tương ứng icon 4 ảnh tham khảo), chat cộng tác (icon 5). State `sidePanel` chọn files/review/chat hoặc đóng; một panel hiện mỗi lần, icon active có dấu chỉ báo, tooltip/aria-label Anh/Việt. Bấm lại icon hoặc nút đóng sẽ ẩn panel và trả không gian cho editor/PDF.

ReviewPanel chuyển khỏi editor-column sang cột sidebar cạnh activity bar, cùng vị trí với cây tệp/outline. Icon Review chỉ mở danh sách, icon Add comment trong toolbar tiếp tục bắt đoạn chọn và mở ô viết. Giữ panel mounted khi đổi tab để không mất nội dung nhận xét/trả lời đang nhập. Panel đều dùng chiều rộng lưu trong sidebarWidth; sửa phép kéo trừ bề rộng activity bar. Focus/detached PDF ẩn rail/panel phù hợp.

Chat có tiêu đề, nút đóng, empty state và textarea disabled với thông báo chờ tích hợp nhiều người dùng; không giả lập gửi/nhận, chưa có protocol/storage chat. Không thêm các icon search/code/AI ngoài yêu cầu.

Kiểm chứng: production static build/TypeScript đạt; browser Edge toàn luồng đạt ở `target/activity-sidebar-01`. Bổ sung test đúng ba icon, chuyển files/review/chat, review không nằm trong editor-column, đóng/mở panel và separator, chat textbox disabled; các test review/scroll/tree/resize/history/upload/compile trước đó vẫn đạt. Đã xem ảnh `chat-panel.png` trực quan, ảnh `review.png` cũng được lưu; đã build UI mới, cần mở lại app. Không thay đổi backend/network trong mục này.

## 40. Khắc phục lỗi hiển thị phông chữ tiếng Việt trên giao diện Next.js (04/11/2026)

- **Nguyên nhân cốt lõi**:
  - Giao diện Next.js trước đó khai báo phông `Georgia, serif` tại nhiều nơi: tiêu đề dự án `.page-heading h1` ("Dự án của bạn."), thẻ chào mừng `.welcome-card h2` ("Từ dòng đầu tiên đến bản PDF hoàn chỉnh."), ghi chú sidebar `.sidebar-note strong` ("Tập trung vào điều bạn viết."), modal header, tên thương hiệu và `.empty-projects`.
  - Trên Windows, phông `Georgia` (`georgia.ttf`, ~219 KB) chỉ hỗ trợ bộ ký tự Latin cơ bản (Latin-1/Latin-2) và thiếu toàn bộ khối Unicode *Latin Extended Additional* (U+1E00–U+1EFF) – nơi định nghĩa các ký tự ghép dấu đặc thù của tiếng Việt (`ề`, `ế`, `ầ`, `ệ`, `ộ`, `ử`, `ừ`...).
  - Khi DirectWrite / Chromium hiển thị các từ tiếng Việt như "điều", "viết", "đầu", "đến", hệ thống phân tách ký tự thành chữ cái gốc (`ê`, `â` - vốn có trong Georgia) và dấu thanh kết hợp (combining accents như U+0300, U+0301). Do `Georgia` không có bảng neo GPOS mark-to-base cho các dấu thanh này, dấu kết hợp bị vẽ lệch thành một ký tự độc lập (dấu huyền hoặc sắc đứng rời rạc cạnh chữ cái: `điề`u`, `viế`t`, `đâ`u`, `đế`n`).
  - Ngoài ra, khoảng cách chữ âm quá lớn (`letter-spacing: -1.2px` và `-0.5px`) khiến các dấu rơi va chạm, đè lấn hoặc chồng chéo lên ký tự tiếp theo.

- **Giải pháp xử lý**:
  1. Định nghĩa biến CSS chuẩn hóa `--font-serif` và `--font-sans` trong `:root` ([globals.css](file:///d:/Overleaf-app/apps/frontend/app/globals.css)):
     ```css
     --font-serif: Charter, "Sitka Text", "Sitka Heading", Cambria, "Times New Roman", serif;
     --font-sans: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
     font-family: var(--font-sans);
     ```
     - Trên Windows: Ưu tiên `"Sitka Text"`, `"Sitka Heading"` (do Matthew Carter thiết kế với đầy đủ dấu tiếng Việt chuẩn và optical sizing) cùng `Cambria` (phông ClearType chất lượng cao của Microsoft hỗ trợ 100% tiếng Việt) và `Times New Roman`.
     - Trên macOS: Sử dụng `Charter` (phông thanh lịch được Overleaf web tin dùng).
     - Loại bỏ triệt để `Georgia` khỏi mọi font stack để tránh cơ chế fallback chắp vá ký tự.
  2. Cập nhật `:root[data-font="serif"]` sử dụng `var(--font-serif)` để đảm bảo khi người dùng chuyển sang phông Serif trong Settings, toàn bộ ứng dụng vẫn hiển thị tiếng Việt hoàn hảo.
  3. Cập nhật tất cả các selector tiêu đề (`.page-heading h1`, `.welcome-card h2`, `.sidebar-note strong`, `.auth-intro h1`, `.auth-card h2`, `.brand strong`, `.modal h2`, `.empty-projects h3`, `.empty-preview h3`, `.paper-tag`, `.paper-formula`) sang `var(--font-serif)`.
  4. Nới lỏng khoảng cách ký tự (`letter-spacing`) từ mức âm sâu `-1.2px` / `-0.5px` về mức tự nhiên (`-0.3px` đến `normal`) để các thanh dấu tiếng Việt có không gian hiển thị thông thoáng, không bị va chạm.

- **Kiểm chứng**:
  - TypeScript typecheck (`npm run typecheck`) đạt 0 lỗi.
  - Next.js static build (`npm run build`) tạo trang tĩnh thành công 100%. Code đã được đồng bộ để khi khởi chạy lại app, giao diện tự động áp dụng bản sửa lỗi.

## 41. Soạn thảo Word trực tiếp trong workspace và export DOCX (04/11/2026)

Cạnh PDF preview có tab Word preview, với trình soạn thảo rich text ngay trong workspace. `WordPreview.tsx` cho gõ/sửa, bold/italic/underline, heading/paragraph, list/table, undo/redo; auto-save sau 700ms, lưu khi blur, nút Save Word và Ctrl/Cmd+S trong vùng Word. Giữ component mounted khi đổi PDF/Word, không mất bản chỉnh sửa do chuyển tab. Menu download chọn Export PDF (.pdf) hoặc Export Word (.docx); Word export đợi lưu nội dung mới nhất, dùng dialog native trên desktop hoặc download ở browser. Không yêu cầu compile PDF trước khi chuyển Word. Các kiểm tra stale/error của PDF vẫn giữ.

`word.py` sử dụng Pandoc: LaTeX → AST JSON → HTML/MathML có lọc → bản sửa HTML → DOCX thật. Hỗ trợ input/include dạng dấu ngoặc qua Store path guard và giới hạn độ sâu/kích thước; ảnh PNG/JPEG/GIF trong project chuyển thành data URI, không fetch ảnh ngoài; công thức MathML thành Word OMML. Pandoc chạy sandbox, timeout 60s, không shell. Draft lưu atomic riêng `.overleaf-word.json`, revision chống ghi đè giữa cửa sổ và fingerprint nguồn; Word không sửa LaTeX, không làm stale PDF. Tạo lại từ LaTeX có xác nhận thay thế chỉnh sửa Word. File tải xuống lấy từ revision Word, bridge kiểm lại revision sau dialog.

API `/api/projects/{project}/word` GET/POST, `/word/generate` POST, `/word/docx?revision=...` GET. `DesktopBridge.export_docx` chỉ lưu đuôi docx. HTML lọc script/event handler/style/iframe/object/SVG và ảnh file/http; paste vào editor lấy plain text. Manifest Python/JS không thêm dependency; cần Pandoc riêng (PATH/OVERLEAF_PANDOC/target/pandoc/pandoc.exe). Bản portable Windows đã chuẩn bị local trong target, không được Git đóng gói.

Giới hạn: editor Word cơ bản, không phải Microsoft Word đầy đủ, chưa mở/sửa mọi DOCX nhập từ ngoài; chưa đồng bộ Word trong room. Nhiều cột, class/macro tùy biến, TikZ, ảnh PDF và bibliography/citation có thể khác/thiếu; không cam kết DOCX giống từng trang PDF. Thông báo trong tab giải thích khác biệt và giữ Word cũ khi nguồn LaTeX đổi. Bản HTML là mô hình chỉnh sửa; DOCX được tạo khi export, không chỉnh trực tiếp XML ZIP trên từng phím gõ.

Kiểm chứng: toàn bộ pytest trước test native mới đạt 128; sau bổ sung native DOCX dialog test, nhóm Word/workspace đạt 74 và test Word riêng đạt 3. Native Pandoc test kiểm tiếng Việt, include, ảnh PNG trong word/media, công thức OMML, sửa nội dung rồi đọc document.xml, persistence/revision, source/fingerprint không đổi và path traversal. Next.js static build/TypeScript đạt. PDF browser regression 3 trang/link/export byte-identical/stale/error đạt ở `target/word-pdf-guard-01`. Word browser đã tạo/sửa trực tiếp, lưu, tải `.docx`, kiểm ZIP/document.xml chứa “Word direct editing – Tiếng Việt”; ảnh `target/word-workspace-01/word-preview.png` đã xem. Các lượt toàn UI đầu dừng ở selector Export PDF cũ sau khi menu thay đổi; đã cập nhật selector cho menu mới, không tính lượt lỗi là pass.

Đã chuyển bài LTJournalArticle trong home kiểm thử cô lập `target/article-zip-repro-01` thành `verified-article.docx` (15.446 bytes), fingerprint LaTeX giữ nguyên; đây là kiểm tạo DOCX, không xác nhận giữ bố cục bài báo nhiều cột. README hướng dẫn tab Word/menu download và giới hạn Pandoc.

Kiểm cuối: toàn bộ browser feature regression đạt trên Edge ở `target/word-workspace-03` (mã thoát 0), gồm Word edit/export và các chức năng trước đó. Ruff F/E9 và git diff --check đạt; UI đã build lại với phím Ctrl/Cmd+S dành cho Word. Mở lại app để tải bản mới.

## 42. Word tự nạp, đồng bộ chữ hai chiều và bố cục journal (04/11/2026)

Mục này thay thế thiết kế Word tách nguồn ở mục 41. Tab Word tự tạo khi mở lần đầu hoặc chuẩn bị khi có PDF compile thành công, hiện trạng thái chuyển đổi thay vì tờ giấy trắng. Khi LaTeX đổi và không có Word đang sửa, sau debounce app lưu nguồn và tự cập nhật Word. Word cũ không có mapping được nâng cấp khi mở; giữ `.overleaf-word.previous.json` trước khi thay thế.

`SyncHTML` ánh xạ text leaf về vị trí nguồn duy nhất trong main hoặc file input/include. Chỉnh chữ trên Word cập nhật đúng range LaTeX, escape ký tự đặc biệt, giữ preamble/class/macro/label/input và phần nguồn không chỉnh; cập nhật editor đang mở, đánh dấu PDF stale. Các lần sửa tiếp theo cập nhật offset mapping. Store.save tạo history như sửa nguồn bình thường; nhiều file được kiểm trước và rollback nếu ghi lỗi. Fingerprint/revision khác sẽ chặn ghi đè; xung đột nguồn giữ bản HTML đang sửa trong `.overleaf-word-conflict.html`. Không ghi từ Word khi đang trong room TCP.

Giới hạn rõ: đồng bộ **nội dung chữ có ánh xạ**, không round-trip mọi LaTeX. Công thức, citation/section number, đoạn trùng hoặc không xác định nguồn bị bảo vệ. Trình soạn thảo cha readonly, chỉ các span có data-word-id được sửa. Định dạng/cấu trúc thực hiện trong editor LaTeX; toolbar rich-text và Ctrl+B/I/U bị khóa trong chế độ đồng bộ để tránh tạo thay đổi Word chưa có trong nguồn. Đổi toàn trang hoặc mất ID bị từ chối. Không tự chuyển toàn thân bài thành LaTeX mới, không phá cấu trúc include/macro.

Bố cục: nhận diện một/hai cột từ nguồn và class local; đọc A4/Letter/lề top/bottom/left/right thông dụng. Word preview có kích thước giấy, lề và zoom/fit-width, header/abstract toàn chiều rộng trước thân hai cột. Khôi phục abstract nằm trong maketitle hook của LTJournalArticle (Pandoc không chạy hook). DOCX sửa section geometry/columns và section break liên tục cho phần đầu một cột/thân hai cột, giữ namespace XML gốc. Word preview là nội dung liên tục, chưa phân trang bằng TeX, chưa bảo đảm cùng dòng/trang với PDF; macro/TikZ/bibliography phức tạp vẫn có thể khác/thiếu.

Kiểm chứng: 130 pytest đạt; test Word riêng 4 đạt sau bổ sung layout/section, include-file sync nhiều lần, escape phần trăm/ampersand, giữ macro/label, chặn sửa toàn trang và xung đột có backup. Next.js production build/TypeScript, Ruff F/E9 và diff check đạt. Browser Edge feature test có kiểm Word tự hiện (không bấm Create), Word→LaTeX, LaTeX→Word và export đạt ở `target/word-sync-03`. PDF browser 3 trang/link/export byte-identical/stale/error đạt ở `target/word-sync-pdf-02`.

Đã thử trên bản sao article: `target/word-layout-01/article-word.png` kiểm tự hiện title/authors/abstract và data-columns=2; ảnh đã xem trực quan. `target/article-zip-repro-01/verified-article.docx` tạo thành công sau cải thiện (16.038 bytes), source fingerprint không đổi khi chỉ generate/export. Không sửa bản project thật của người dùng trong kiểm thử; mở lại app để dùng UI mới. README cập nhật ngữ nghĩa đồng bộ và các giới hạn.

Regression cuối phát hiện race trong file watcher: poll signature cũ có thể setStale(true) sau compile mới đã hoàn tất. `useParity` đối chiếu snapshot ID đang hiển thị với snapshot backend, current/fingerprint và dirty; chỉ hiệu chỉnh stale khi không có buffer dirty/build đang chạy. Không mở export chỉ dựa vào trạng thái UI; backend vẫn verified snapshot như trước. Lượt feature `word-sync-04` phát hiện lỗi, đã sửa và chạy lại **word-sync-05 đạt toàn luồng**; **word-sync-pdf-03 đạt** PDF link/byte-identical export/stale/error. Static build/TypeScript và diff check cuối đạt. Khi Word đổi nội dung file đang mở, tăng revision để Auto Compile nhận thay đổi nếu đã bật.

## 43. Khôi phục ảnh và typography khi chuyển Word (04/11/2026)

Tái hiện đúng bài journal: PDF có 8 figure nhưng AST Pandoc ban đầu không có Image vì `\\fbox{\\includegraphics...}` bị reader bỏ qua. Tên ảnh có dấu ngoặc kép, nằm dưới Figures/ theo graphicspath khai báo trong LTJournalArticle.cls; pipeline cũ chỉ tìm root. DOCX trước đây chỉ 16 KB và 0 ảnh là kết quả thiếu nội dung, không phải xác minh fidelity thành công.

`word.py` nay đọc class local được documentclass chọn, lấy graphicspath, tìm ảnh có/không đuôi, xử lý tên quote và chuẩn hóa fbox/framebox/mbox chứa includegraphics trước khi Pandoc đọc. Chỉ sửa bản chuyển đổi trong bộ nhớ; nguyên bản LaTeX giữ nguyên. Đường dẫn vẫn đi qua Store.path, không đọc ngoài project. Quy đổi width theo textwidth/linewidth/columnwidth sang mm, giữ style width/height với bộ lọc CSS riêng cho img, không cho URL/CSS tùy ý. Figure/figcaption được giữ, thêm số thứ tự và caption; cảnh báo số ảnh chuyển được nếu chưa đủ, không âm thầm coi ảnh bị mất là thành công.

Nhận diện mathpazo/palatino để dùng Palatino Linotype và cỡ documentclass 10/11/12pt trong Word preview và DOCX styles (Normal/Body/Heading/Title/Caption), không thay font công thức/code. Heading căn giữa thay vì bị justify giãn chữ; caption nhỏ/nghiêng, figure tránh tách khối. Font thực tế vẫn phụ thuộc máy mở Word; chưa embed TeX font hoặc giữ toàn bộ typography/floats/page breaks của PDF.

Thêm converterVersion=3/needsRefresh: cache preview cũ tự tái tạo khi mở tab/export, có backup trước đó. Không cần nhập lại ZIP. Bổ sung alt ngắn cho ảnh và loại data URI khỏi mô tả picture XML, tránh nhân đôi ảnh base64 trong document.xml. DOCX vẫn nhúng dữ liệu ảnh trong word/media.

Kiểm chứng: **131 pytest đạt**, Ruff F/E9, static build/TypeScript và diff check đạt. Test mới bao gồm framed figures, class graphicspath, quote/không đuôi, kích thước 76.5mm, font Palatino 12pt, sanitizer và DOCX có drawing/media thật. Browser Edge `target/word-images-layout-01`: nâng cache cũ, đủ 8/8 ảnh decode naturalWidth>0, 8 caption; ảnh OWASP đầu tiên nằm sau section 3 và trước section 4 trong thứ tự nội dung. Đã xem screenshot `article-word.png`. Toàn bộ feature browser regression đạt ở `target/word-images-features-01`, gồm đồng bộ Word/LaTeX, export, editor/history/upload/Auto Compile.

Trên bản sao bài người dùng: `target/article-zip-repro-01/verified-article.docx` hiện 1.070.279 bytes; kiểm 8 drawing và 8 word/media, SHA-256 tập ảnh nhúng khớp chính xác 8 file gốc (owaspyii2, cyprom_overview, advtemplateproject, actionscenario, 1, criteria, 2, 3). Source fingerprint không đổi khi generate/export. Giữ giới hạn: preview Word là continuous flow hai cột, chưa tái hiện vị trí float/phân trang giống TeX; bibliography/custom macro chưa được round-trip đầy đủ.

## 44. Thoát chế độ tập trung bằng Esc và Bố cục (04/11/2026)

Nguyên nhân bị kẹt: focus CSS ẩn cả app-header chứa nút Bố cục, chỉ còn gợi ý phím Ctrl/Cmd+Shift+M. Thay bằng header gọn 44px giữ Bố cục và nút Thoát tập trung · Esc. Mục Focus mode trong menu đổi thành Thoát chế độ tập trung khi đang bật; giữ nguyên layout split/editor/pdf khi thoát.

`useParity` thêm listener Escape ở capture phase để hoạt động cả khi CodeMirror đang focus, bỏ qua sự kiện IME composition và cleanup listener khi thoát. Đóng layout menu sau khi rời focus. Phím Ctrl/Cmd+Shift+M vẫn dùng được.

Kiểm chứng: Next.js production build/TypeScript và git diff --check đạt. Browser Edge toàn feature regression đạt ở `target/focus-exit-01`; bổ sung ba đường thoát: Esc khi con trỏ trong editor, Bố cục → Thoát chế độ tập trung, nút thoát trực tiếp. Kiểm sidebar trở lại và layout editor-only giữ nguyên. Screenshot `focus-mode.png` đã xem trực quan. README cập nhật cách dùng; UI đã build để mở lại app nhận thay đổi. Không thay đổi luồng export PDF/Word trong mục này.

## 45. Bổ sung hỗ trợ Kéo & Thả (Drag & Drop) khi nhập dự án (04/11/2026)

- **Ngữ cảnh & Vấn đề**:
  - Khi tạo dự án mới từ tài nguyên có sẵn (ZIP, Word `.docx`, Markdown, hoặc thư mục dự án), trước đây người dùng bắt buộc phải bấm vào từng nút tương ứng (`ZIP`, `Word (.docx)`, `Markdown`, `Thư mục có sẵn`), kích hoạt hộp thoại chọn file của hệ điều hành (`create_file_dialog`) và phải tự duyệt tìm đường dẫn tệp trên ổ đĩa.
  - Thao tác này gây gián đoạn và bất tiện khi người dùng đã có sẵn thư mục chứa tệp trong Windows Explorer hoặc muốn kéo thả nhanh chóng.

- **Giải pháp triển khai**:
  1. **Vùng kéo thả trực quan (`NewProjectExtras.tsx`, `globals.css`)**:
     - Bổ sung khối `.import-dropzone` tích hợp trực tiếp vào hộp thoại "Tạo dự án mới" (Create new project), tương thích cả giao diện sáng (Light) và tối (Dark mode).
     - Phản hồi thị giác thời gian thực: khi kéo tệp hoặc thư mục vào vùng dropzone, viền nét đứt chuyển sang màu xanh lá chủ đạo (`var(--green)`), nền chuyển màu sáng/tối nhẹ kèm thông báo *"Thả tệp hoặc thư mục vào đây để tạo dự án ngay!"*.
     - Tự động nhận diện định dạng:
       - Tệp `.zip`: tự động nhận diện và nhập dưới dạng dự án nén LaTeX.
       - Tệp `.docx`: tự động chuyển đổi từ Word sang LaTeX thông qua Pandoc.
       - Tệp `.md` hoặc `.markdown`: tự động chuyển đổi từ Markdown sang LaTeX qua Pandoc.
       - Thư mục hoặc nhiều tệp: tự động nhận diện cấu trúc dự án, tìm `main.tex` hoặc tệp gốc có `\documentclass`.
       - Tệp `.tex` đơn lẻ: tự động đóng gói thành dự án hoàn chỉnh.
     - Tự động gợi ý tên dự án từ tên tệp/thư mục thả vào nếu người dùng chưa nhập tên.
     - Trạng thái tiến trình tải lên và nút hủy: hiển thị icon xoay và tên tệp đang xử lý, kèm nút "Hủy nhập" an toàn (`io-cancel`).
     - Giữ nguyên các nút bấm (`[Dự án ví dụ]`, `[↑ ZIP]`, `[↑ Word]`, `[↑ Markdown]`, `[↑ Thư mục]`) bên trong dropzone để người dùng vẫn có thể click chọn tệp thủ công qua hộp thoại hệ thống nếu muốn.
  2. **Hạ tầng truyền nhận dữ liệu (`parity_api.py`, `api.py`, `main.py`, `zip.ts`)**:
     - Hỗ trợ đường dẫn gốc hệ điều hành (`import-path` / `native("import_path")`): trong môi trường WebView2/desktop, khi có `file.path`, ứng dụng gọi trực tiếp `action("import-path")` hoặc `native("import_path")` với hiệu năng tức thời, không qua sao chép trung gian.
     - Hỗ trợ luồng nhị phân streaming (`POST /api/projects/import-file`): đối với môi trường trình duyệt hoặc khi `file.path` không được lộ vì lý do bảo mật, ứng dụng truyền luồng nhị phân trực tiếp đến FastAPI backend (bỏ qua giới hạn JSON 12MB của middleware và hỗ trợ tệp tới 50 MiB).
     - Module phát sinh tệp nén thuần TypeScript (`lib/zip.ts`): cho phép đóng gói đệ quy các thư mục hoặc danh sách tệp được kéo thả trong trình duyệt thành một tệp ZIP chuẩn mực (không phụ thuộc thư viện ngoài) rồi chuyển đến backend.

- **Kiểm chứng**:
  - 49/49 bài kiểm thử `pytest` trong `test_parity.py` đạt 100%, bao gồm cả 2 luồng `POST /api/projects/import-file` và `POST /api/actions/import-path`.
  - TypeScript typecheck (`npm run typecheck`) đạt 0 lỗi.
  - Next.js static production build (`npm run build`) hoàn tất thành công. Code đã sẵn sàng phục vụ cho lần mở ứng dụng tiếp theo.

## 46. Tối ưu không gian Editor toàn màn hình và Tích hợp Bảng Logs & Errors chuẩn Overleaf (04/11/2026)

- **Ngữ cảnh & Vấn đề**:
  - Ở giao diện cũ, cột trái (editor column) vừa phải chứa trình soạn thảo mã nguồn LaTeX (CodeMirror), vừa phải gánh thêm cửa sổ terminal "Build output" và danh sách giải thích lỗi LaTeX ("error explanations") ở bên dưới.
  - Điều này làm khung soạn thảo LaTeX bị bóp nghẹt chỉ còn chiếm một phần nhỏ chiều cao (khoảng 240px), gây khó khăn khi viết tài liệu dài và không giống với trải nghiệm chuẩn mực của Overleaf.
  - Người dùng muốn trình soạn thảo LaTeX ở cột trái chiếm trọn 100% chiều cao (toàn bộ diện tích khả dụng). Phần nhật ký biên dịch (build logs) và bảng lỗi/cảnh báo được chuyển hoàn toàn sang cột bên phải (nơi hiển thị PDF preview).

- **Giải pháp triển khai theo thiết kế Overleaf (Ảnh 2, 3 và 4)**:
  1. **Giải phóng 100% diện tích cho LaTeX Editor (Ảnh 2)**:
     - Gỡ bỏ hoàn toàn khối terminal `.build-heading`, nút `.cache-clean`, khung terminal `<pre className="build-output">` và danh sách `.error-explanations` khỏi `.editor-column`.
     - Bộ soạn thảo CodeMirror trong `.editor-area` nay tự động giãn nở đạt 100% chiều cao của cột trái, mang lại không gian viết rộng rãi, liền mạch, không còn bị chia cắt.
  2. **Nút biểu tượng Nhật ký & Lỗi với Huy hiệu đếm (Badge Icon) cạnh nút Recompile (Ảnh 3)**:
     - Khi đang ở chế độ xem PDF/Word (`previewTab !== "logs"`), một nút biểu tượng trang tài liệu chuẩn Overleaf (`.logs-toggle-btn`) được đặt ngay cạnh nút `Recompile` trên thanh công cụ biên dịch (`.compile-toolbar`).
     - Biểu tượng tài liệu SVG có góc gập tinh tế, hiển thị số lượng lỗi hoặc cảnh báo ngay trên mặt trang tài liệu:
       - Nếu có lỗi biên dịch (`errors > 0`): số huy hiệu hiển thị màu đỏ rực (`#dc2626`).
       - Nếu có cảnh báo (`warnings > 0` và không có lỗi): số huy hiệu hiển thị màu vàng kim/hổ phách đặc trưng (`#d97706`, giống hệt số `2` trong Ảnh 3).
       - Nếu không có lỗi hay cảnh báo: hiển thị số `0` với tông xám nhạt tinh tế.
     - Nhấp chuột vào nút này sẽ lập tức chuyển đổi cột bên phải sang giao diện "Logs & Errors" chuyên dụng.
  3. **Nút "Quay lại PDF" (Back to PDF) (Ảnh 4)**:
     - Khi chuyển sang giao diện Logs (`previewTab === "logs"`), nút biểu tượng huy hiệu được thay thế bằng nút pill bo tròn `[Quay lại PDF]` (`Back to PDF`) ngay cạnh `Recompile`.
     - Nhấp chuột vào nút này sẽ đưa giao diện xem trước cột phải trở lại ngay lập tức với tài liệu PDF đã biên dịch.
  4. **Giao diện Nhật ký & Lỗi chuyên dụng (`LogsView.tsx`, Ảnh 4)**:
     - **Thanh lọc phân loại (Filter Tabs)**:
       - `Tất cả log <số lượng>` (All logs)
       - `Lỗi <số lượng>` (Errors)
       - `Cảnh báo <số lượng>` (Warnings)
       - `Thông tin <số lượng>` (Info)
       - Thẻ được chọn có đường viền gạch chân xanh lá Overleaf (`#00a877`).
     - **Thẻ lỗi/cảnh báo tương tác (Interactive Issue Cards)**:
       - Tiêu đề được phân màu trực quan: Đỏ cho lỗi, Vàng kim cho cảnh báo, Xanh dương cho thông tin.
       - Hiển thị vị trí tệp và dòng cụ thể (ví dụ: `/main.tex:45`).
       - Biểu tượng hỗ trợ/trợ lý (Sparkles) ở góc phải.
       - Cho phép mở rộng / thu gọn để xem chi tiết thông điệp thô, nguyên nhân (Why), và giải pháp khắc phục đề xuất (Fix).
       - Bổ sung nút **"Đi tới dòng {line}" (Jump to line)**: khi nhấn vào, con trỏ của CodeMirror sẽ tự động cuộn và đặt đúng vào dòng mã nguồn tương ứng trong tệp đang mở.
     - **Bộ bóc tách nhật ký toàn diện (`parseAllLogs`)**:
       - Tự động nhận diện cấu trúc cảnh báo LaTeX từ cả backend `explainer` lẫn dòng raw output: nhận diện cảnh báo font (`Font shape ... undefined`, `Some font shapes were not available, defaults substituted.`), thiếu nhãn tham chiếu chéo (`Reference undefined`), thiếu trích dẫn (`Citation undefined`), tràn lề (`Overfull / Underfull \hbox`), v.v.
     - **Mục "Nhật ký thô" có thể gấp gọn (› Raw logs)**:
       - Thu gọn mặc định để giữ giao diện sạch sẽ, có thể bấm mở để xem toàn bộ luồng xuất của `latexmk`.
       - Đi kèm nút "Sao chép nhật ký" (Copy logs) và "Xóa" (Clear).
     - **Thanh công cụ cố định phía dưới (Bottom Action Bar)**:
       - Nút đỏ nổi bật: `[🗑 Xóa bộ nhớ đệm]` (Clear cached files) để dọn dẹp các tệp tạm, cache, aux của LaTeX.
       - Nút `[Nhật ký & tệp khác ▴]` (Other logs and files): mở menu tải xuống tệp nhật ký `.log`, sao chép toàn bộ nhật ký hoặc mở nhanh PDF.
  5. **Hỗ trợ toàn diện Giao diện Sáng (Light Mode) và Tối (Dark Mode)**:
     - Tương thích màu sắc chuẩn mực với hệ thống CSS biến giao diện hiện tại của ứng dụng.

- **Kiểm chứng**:
  - TypeScript typecheck (`tsc --noEmit`): 0 lỗi.
  - Next.js static production build (`npm run build`): Biên dịch tĩnh thành công 100%.
  - Pytest (`test_parity.py`): 49/49 bài kiểm thử đạt kết quả tốt.

## 47. Chấm điểm IELTS Writing trực tiếp trên Editor (Task 1 & Task 2) và Xuất Báo Cáo PDF (05/11/2026)

- **Ngữ cảnh & Ý tưởng**:
  - Tích hợp một hệ thống chấm và đánh giá bài viết IELTS Writing (cả Task 1 Báo cáo và Task 2 Bài luận) trực tiếp trên giao diện soạn thảo Overleaf Desktop.
  - Giúp Tutor (giáo viên chấm bài) và Student (học viên) có một quy trình làm việc khép kín theo đúng sơ đồ kiến trúc tại **Ảnh 3**:
    `Student (Visual Writing Editor) + Tutor (Review & Scoring) -> Document & Review Engine -> Database (.overleaf-ielts.json) + LaTeX Engine (ielts_report.tex) -> PDF REPORT`.

- **Chi tiết triển khai**:
  1. **Nút biểu tượng IELTS Writing trên thanh công cụ bên trái (Ảnh 4)**:
     - Bổ sung nút biểu tượng `GraduationCap` trên thanh `workspace-activity-bar` nằm ngay dưới icon Chat cộng tác.
     - Khi nhấp vào, mở bảng điều khiển chấm điểm IELTS Writing chuyên dụng ở thanh bên trái (`sidePanel === "ielts"`).
  2. **Tiêu chí chấm điểm Task 1 & Task 2 (Ảnh 1 & Ảnh 2)**:
     - **Task 1 (Report/Báo cáo - tối thiểu 150 từ, trọng số 1/3)**:
       - `Task Achievement (TA)`: Mức độ hoàn thành đề bài và nêu tổng quan xu hướng.
       - `Coherence and Cohesion (CC)`: Tính mạch lạc, phân đoạn và liên kết ý.
       - `Lexical Resource (LR)`: Vốn từ vựng, độ tự nhiên và kết hợp từ (collocations).
       - `Grammatical Range and Accuracy (GRA)`: Đa dạng cấu trúc câu và độ chuẩn xác ngữ pháp.
     - **Task 2 (Discursive Essay/Nghị luận - tối thiểu 250 từ, trọng số 2/3)**:
       - `Task Response (TR)`: Khả năng phản hồi toàn diện đề bài, lập trường và phát triển luận điểm.
       - `Coherence and Cohesion (CC)`
       - `Lexical Resource (LR)`
       - `Grammatical Range and Accuracy (GRA)`
     - Mỗi tiêu chí tích hợp bộ chọn điểm nhanh từ **0.0 đến 9.0** (bước nhảy 0.5 band).
     - Tự động hiển thị tóm tắt **Official Band Descriptors** (chuẩn Cambridge/British Council) tương ứng với mức Band đang chọn để Tutor đối chiếu trực tiếp.
     - Ô nhập nhận xét / lời phê cụ thể của Tutor cho từng tiêu chí riêng biệt.
  3. **Đếm số từ tự động (Live Word Count)**:
     - Tự động tính số từ của bài viết đang soạn thảo trong CodeMirror (loại bỏ comment và macro TeX).
     - Đưa ra huy hiệu cảnh báo nếu chưa đủ số từ (150 từ cho Task 1 / 250 từ cho Task 2) hoặc thông báo đạt tiêu chuẩn độ dài.
  4. **Tính điểm Band tổng quát (IELTS Band Calculator)**:
     - Điểm Task 1: Trung bình 4 tiêu chí $(TA + CC + LR + GRA) / 4$.
     - Điểm Task 2: Trung bình 4 tiêu chí $(TR + CC + LR + GRA) / 4$.
     - Điểm Overall IELTS Writing: Weighted Band $\text{round}((Task1 \times 1 + Task2 \times 2) / 3)$ tuân thủ tuyệt đối quy tắc làm tròn chuẩn IELTS (ví dụ .25 lên .5; .75 lên 1.0).
     - Huy hiệu điểm số lớn, trực quan với phân màu theo dải điểm (Vàng kim cho 8.0-9.0, Ngọc lục bảo cho 7.0-7.5, Xanh dương cho 6.0-6.5, Cam cho 5.0-5.5).
  5. **Chế độ Tutor Mode và Student View**:
     - Cho phép chuyển đổi nhanh: Tutor có quyền chấm và chỉnh sửa lời phê; Student ở chế độ xem đọc điểm, xem nhận xét và kế hoạch cải thiện.
  6. **Lưu trữ dữ liệu & Tự động sinh Báo Cáo PDF qua LaTeX (Kiến trúc Ảnh 3)**:
     - Backend API:
       - `GET /api/projects/{project}/ielts`: Tải dữ liệu đánh giá đã lưu từ `.overleaf-ielts.json`.
       - `POST /api/projects/{project}/ielts`: Lưu trữ trạng thái đánh giá an toàn, nguyên tử.
       - `POST /api/projects/{project}/ielts/report`: Tự động sinh tệp mã nguồn LaTeX `ielts_report.tex` với bố cục báo cáo thẩm định IELTS chuyên nghiệp (bảng điểm, band badge, nhận xét và danh sách khuyến nghị của giáo viên).
     - Khi sinh báo cáo, tệp `ielts_report.tex` được tạo ngay trong thư mục dự án, cho phép Tutor và Học viên có thể lập tức biên dịch ra bản PDF Report chất lượng cao để in ấn hoặc gửi lưu trữ.

- **Kiểm chứng**:
  - Backend pytest: 139/139 tests đạt 100% (`test_parity.py`, `test_ielts.py`, `test_word.py`, `test_workspace.py`,...).
  - Frontend TypeScript typecheck (`npm run typecheck`): 0 lỗi.
  - Next.js production build (`npm run build`): Biên dịch tĩnh thành công 100%.

## 48. Tối ưu thanh công cụ biên dịch: Hợp nhất tab xem trước PDF/Word cùng hàng với Recompile và nút Logs (06/11/2026)

- **Ngữ cảnh & Vấn đề**:
  - Trước đây, khung xem trước tài liệu bị chia thành nhiều thanh công cụ xếp chồng lên nhau theo chiều dọc:
    - Hàng 1: Thanh biên dịch `compile-toolbar` (Engine, nút Recompile, nút Logs, Auto compile, SyncTeX, Download).
    - Hàng 2: Thanh tab chọn định dạng xem trước `preview-tabs` (chiếm riêng ~40px chiều cao chỉ để chứa 2 tab "PDF preview" và "Word preview").
    - Hàng 3: Thanh công cụ điều khiển của chính người xem (PDF viewer toolbar / Word actions toolbar).
  - Điều này làm giao diện bị chiếm dụng diện tích dọc quá nhiều, các nút và khung trông to và thừa thãi, làm giảm không gian hiển thị của tài liệu thực tế.
- **Giải pháp & Chi tiết triển khai**:
  1. **Hợp nhất `PDF preview` & `Word preview` lên cùng hàng với `Recompile` & nút Logs**:
     - Chuyển 2 nút `PDF preview` và `Word preview` vào cùng thanh `compile-toolbar` ngay bên cạnh nút Logs toggle.
     - Sử dụng thanh phân cách mảnh `preview-toolbar-divider` để phân tách trực quan nhóm thao tác biên dịch với nhóm chế độ xem.
     - Thiết kế nhóm tab dạng segmented/pill control (`.preview-mode-switch` & `.preview-mode-tab`):
       - Chiều cao thu gọn 24px, bo góc tinh tế, nền mờ hiện đại.
       - Thẻ kích hoạt được làm nổi bật với chữ sắc nét, độ tương phản cao ở cả Light Mode lẫn Dark Mode.
       - Giữ nguyên thuộc tính chuẩn Accessibility (`role="tab"`, `aria-selected`, tablist) đảm bảo tương thích 100% với các bài test tự động.
  2. **Tối ưu kích thước nhỏ gọn và dễ nhìn**:
     - Cố định chiều cao `compile-toolbar` ở mức **38px** (thay vì 43px+ và bị wrap nhiều dòng trước đó).
     - Điều chỉnh padding, kích thước nút, font size và icon hài hòa, sắc nét.
     - Nút `logs-toggle-btn` hiển thị icon tài liệu và huy hiệu đếm số lỗi/cảnh báo ngay cạnh nút Recompile; khi nhấp vào có thể chuyển đổi mượt mà giữa chế độ xem Logs và xem tài liệu.
     - Khi ở tab Logs, nút "Quay lại PDF" (`back-to-pdf-btn`) được thiết kế nhỏ gọn, hoặc người dùng có thể nhấp trực tiếp vào tab "PDF preview" để quay về xem văn bản.
     - Giảm chiều cao thanh công cụ `preview-toolbar` bên trong `PdfViewer` xuống **30px** (từ 36px), loại bỏ chữ thừa giúp tối đa hóa không gian đọc trang PDF.
  3. **Loại bỏ hoàn toàn hàng `.preview-tabs` thừa**:
     - Giảm tải DOM và giải phóng ngay lập tức hơn 40px chiều cao cho vùng hiển thị tài liệu.
- **Kiểm chứng**:
  - TypeScript typecheck (`npm run typecheck`): 0 lỗi.
  - Next.js build (`npm run build`): Đạt 100%.

## 49. Thu gọn thanh Header trong trang Editor tăng tối đa góc nhìn (06/11/2026)

- **Ngữ cảnh & Vấn đề**:
  - Trước đây, thanh tiêu đề ứng dụng (`.app-header`) dùng chung chiều cao cố định **76px** với padding lớn (30px) và khoảng cách phần tử rộng (28px).
  - Khi người dùng vào trang editor soạn thảo văn bản, thanh header quá dày này chiếm dụng một diện tích dọc rất lớn, làm thu hẹp góc nhìn và giảm không gian làm việc của trình soạn thảo LaTeX cũng như khung preview bên dưới.
- **Giải pháp & Chi tiết triển khai**:
  1. **Tạo lớp điều chỉnh riêng cho chế độ Editor (`.app-header.in-editor`)**:
     - Khi đang mở một dự án (`project !== null`), thanh header được tự động gắn thêm lớp `in-editor`.
     - Giảm chiều cao thanh từ **76px** xuống **42px** (giải phóng ngay lập tức **34px** chiều dọc cho vùng làm việc bên dưới).
     - Giảm padding ngang từ `30px` xuống `14px`, gap thu gọn từ `28px` xuống `12px`.
  2. **Tối ưu tỷ lệ các thành phần bên trong Header**:
     - **Logo Overleaf**: Thu nhỏ biểu tượng lá cây (`brand-mark`) từ 38px xuống 26px, chữ Overleaf từ 25px xuống 16px, subtitle desktop gọn gàng, cân đối.
     - **Thanh phân cách & Tên dự án**: Thu gọn font size tên dự án xuống 13px, hỗ trợ `text-overflow: ellipsis` (tối đa 340px) tránh vỡ giao diện khi tên file/dự án dài.
     - **Các nút chức năng (`History`, `Collaborate`, `Layout`)**: Đặt chiều cao 28px, padding 0 9px, icon 14px tinh gọn chuẩn giao diện Overleaf hiện đại.
     - **Nút Cài đặt & Avatar**: Căn chỉnh chiều cao 28px, avatar tròn 26px font 11px sắc nét.
     - Menu xổ xuống của Bố cục (`layout-menu`) được tính toán lại vị trí tự động `top: calc(100% + 5px)` luôn khớp hoàn hảo dưới nút.
- **Kiểm chứng**:
  - TypeScript typecheck (`tsc --noEmit`): 0 lỗi.
  - Next.js static build (`npm run build`): Thành công 100%.

## 50. Thu gọn thanh công cụ Word Preview tối đa hóa diện tích đọc văn bản (06/11/2026)

- **Ngữ cảnh & Vấn đề**:
  - Khi xem trước tài liệu Word (.docx), giao diện trước đó có thanh tác vụ `.native-word-actions` với các nút kích thước lớn, kèm theo một đoạn văn bản ghi chú riêng (`.native-word-note`: *"DOCX from the original converter, edited and saved directly..."*) chiếm thêm gần 30px chiều dọc.
  - Phía dưới lại có thanh công cụ định dạng của SuperDoc, dẫn đến việc phần đầu bị chiếm dụng quá nhiều không gian dọc trước khi người dùng nhìn thấy nội dung trang Word thực sự.
- **Giải pháp & Chi tiết triển khai**:
  1. **Nén thanh tác vụ `.native-word-actions` xuống chiều cao 30px**:
     - Thiết lập `height: 30px`, `padding: 0 8px`, `flex-wrap: nowrap`.
     - Các nút bấm (`Paginated Word`, `LaTeX text sync`, `Save Word`, `Regenerate from PDF`, `Fit width`) được tinh chỉnh chiều cao **22px**, padding `0 7px`, font size **11px**, bo góc 4px gọn gàng.
     - Trạng thái kích hoạt (`aria-pressed="true"`) giữ điểm nhấn xanh lá Overleaf với nền bán trong suốt tinh tế.
  2. **Giải phóng hoàn toàn dòng ghi chú thừa (`.native-word-note`)**:
     - Ẩn hoàn toàn thẻ `<p className="native-word-note">` khỏi luồng hiển thị giao diện (`display: none !important`), tiết kiệm ngay gần **30px** chiều dọc.
     - Chuyển toàn bộ nội dung giải thích thành tooltip hover (`title`) trên phần trạng thái tệp (`.native-word-status`) kèm biểu tượng `ℹ` nhỏ gọn. Người dùng khi cần đối chiếu chỉ việc rê chuột vào sẽ đọc được đầy đủ thông tin mà không làm chật chội màn hình.
  3. **Tối ưu thanh định dạng Word (`.native-word-toolbar`) & Warning**:
     - Cố định đường viền phân tách mỏng, cảnh báo `.native-word-warning` thu nhỏ thành dải thông báo 1 dòng kín đáo.
- **Kiểm chứng**:
  - TypeScript typecheck (`tsc --noEmit`): 0 lỗi.
  - Next.js static build (`npm run build`): Thành công 100%.

## 51. Sửa lỗi viền/nền và làm sáng icon thanh công cụ Word Preview ở Dark Mode (06/11/2026)

- **Ngữ cảnh & Vấn đề**:
  - Khi xem trước Word (.docx) ở chế độ giao diện tối (Dark Mode), thanh công cụ định dạng SuperDoc xuất hiện vệt cắt màu kỳ lạ: nửa bên trái có nền trắng chói (`#fff`) trong khi nửa bên phải lộ nền tối (`#202a33`) của container cha, tạo thành một đường viền phân tách sắc cạnh (glitch) gây khó chịu cho mắt người dùng.
  - Nguyên nhân: Thư viện SuperDoc mặc định đặt `width: 100%` và fallback `background: var(--sd-ui-toolbar-bg, var(--sd-ui-bg, #fff))`. Khi thanh công cụ bị tràn chiều ngang và cuộn (`overflow-x: auto`), phần nền trắng 100% chỉ bao phủ viewport ban đầu, còn phần nội dung tràn ra ngoài hiển thị nền của container cha.
  - Đồng thời, các icon công cụ (danh sách, thước kẻ, cọ định dạng, căn lề, v.v.) và nhãn chữ mặc định có màu xám đậm (`#47484a`), khi hiển thị trên nền tối gần như không có độ tương phản, rất khó nhìn và khó thao tác.
- **Giải pháp & Chi tiết triển khai**:
  1. **Đồng nhất màu nền và loại bỏ triệt để lỗi phân tách nền / lỗi viền**:
     - Thiết lập `.native-word-toolbar { background: var(--panel); }`.
     - Buộc thanh công cụ SuperDoc mở rộng phủ kín toàn bộ bề rộng nội dung: `.native-word-toolbar .superdoc-toolbar { min-width: 100%; width: max-content; background: var(--panel) !important; box-sizing: border-box; }`.
     - Nhờ đó, nền thanh công cụ luôn liền mạch một màu duy nhất từ đầu tới cuối khi cuộn ngang, xóa bỏ hoàn toàn đường viền phân chia màu trắng - tối.
  2. **Đồng bộ hệ thống biến CSS theme SuperDoc (`--sd-ui-*`) cho Dark Mode**:
     - Cấu hình các biến chính của SuperDoc dưới `:root[data-mode="dark"]`:
       - `--sd-ui-bg` & `--sd-ui-toolbar-bg`: `var(--panel)` (`#202a33`).
       - `--sd-ui-toolbar-button-text`: `#e2e8f0`.
       - `--sd-ui-border`: `var(--line)` (`#3c4b52`).
       - `--sd-ui-dropdown-bg`: `#202a33`.
       - `--sd-ui-dropdown-border`: `#3c4b52`.
       - `--sd-ui-dropdown-hover-bg`: `#2c3a46`.
  3. **Làm sáng icon và nhãn công cụ với độ tương phản cao**:
     - Đặt màu nút bấm `.sd-toolbar-button`, combobox `.sd-font-combobox`, nhãn chữ `.sd-button-label` và các icon SVG `.sd-toolbar-icon svg` sang tông màu bạc sáng `#d8e2ec` / `#e2e8f0` (độ tương phản đạt trên 10:1 so với nền tối `#202a33`).
     - Tinh chỉnh icon mũi tên caret `.sd-dropdown-caret` sang màu xám sáng `#94a3b8`.
     - Đường phân cách công cụ `.toolbar-separator .separator-inner` sử dụng tông `var(--line)` (`#3c4b52`) thanh thoát.
     - Trạng thái hover có hiệu ứng sáng rõ (`color: #ffffff; background: rgba(255, 255, 255, 0.08)`), trạng thái active/được chọn có nền `rgba(255, 255, 255, 0.16)`.
     - Menu xổ xuống (Dropdown Menu & Font Picker teleported ra `document.body`) cũng được áp dụng giao diện dark mode đồng bộ, viền mỏng và nền tối sang trọng.
- **Kiểm chứng**:
  - TypeScript typecheck (`npm run typecheck`): 0 lỗi.
  - Next.js static build (`npm run build`): Đạt 100%.

## 52. Khôi phục và nâng cấp icon mũi tên thoát dự án trong thanh Header (06/11/2026)

- **Ngữ cảnh & Vấn đề**:
  - Sau khi thu gọn thanh Header ở mục 49, người dùng phản ánh icon mũi tên thoát (`ArrowLeft`) bị biến mất khỏi thanh header khi mở dự án, khiến người dùng không thể bấm quay lại danh sách dự án bên ngoài (`media_1793949642721_6b6b955b.png`).
  - **Nguyên nhân cốt lõi**:
    1. Trong CSS chung, phần tử `<button>` có mặc định `justify-content: center`.
    2. Khi tên dự án dài (ví dụ: `_Article_Hands_on_Training_for_Mitigating_vulnerabilites_Toan_`, ~60 ký tự), nội dung dài hơn `max-width: 340px`. Do thuộc tính `justify-content: center` kết hợp `overflow: hidden`, trình duyệt căn giữa nội dung tràn và đẩy lệch toàn bộ phần đầu sang bên trái ngoài vùng hiển thị.
    3. Hậu quả là biểu tượng `<ArrowLeft />` cùng các ký tự đầu tiên (`_Arti`) bị cắt lẹm và biến mất hoàn toàn khỏi tầm nhìn.
- **Giải pháp & Chi tiết triển khai**:
  1. **Tách biệt cấu trúc icon thoát `.project-breadcrumb-back` và tiêu đề `.project-breadcrumb-title`**:
     - Trong [`page.tsx`](file:///d:/Overleaf-app/apps/frontend/app/page.tsx), gói icon `ArrowLeft` trong `<span className="project-breadcrumb-back">` với `flex-shrink: 0`.
     - Tên dự án được đặt trong `<span className="project-breadcrumb-title">` riêng biệt.
  2. **Cấu hình căn lề trái chuẩn xác (`justify-content: flex-start !important`)**:
     - Thiết lập `.project-breadcrumb` luôn căn trái tuyệt đối (`justify-content: flex-start !important; text-align: left;`).
     - Đặt `min-width: 0`, `max-width: 380px` cho toàn khối breadcrumb.
     - Đặt `overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; flex: 1;` trên `.project-breadcrumb-title`. Nhờ đó, tên dự án luôn hiển thị đầy đủ từ chữ cái đầu tiên và chỉ thu gọn bằng dấu `...` ở phần đuôi nếu vượt quá chiều rộng cho phép.
  3. **Thiết kế nút icon thoát dạng pill trực quan, dễ thao tác**:
     - `.project-breadcrumb-back` có kích thước cố định 24x24px, bo góc 4px với nền nhẹ `rgba(255, 255, 255, 0.08)`.
     - Khi rê chuột vào (hover), nút nổi bật rõ ràng, chuyển màu sang xanh lá Overleaf (`var(--green)`) kèm hiệu ứng đẩy nhẹ sang trái (`transform: translateX(-1px)`), tạo cảm giác phản hồi hành động quay lại rất trực quan.
     - Bổ sung `title` và `aria-label` giải thích rõ ràng ("Thoát về danh sách dự án" / "Thoát ra ngoài").
- **Kiểm chứng**:
  - TypeScript typecheck (`tsc --noEmit`): 0 lỗi.
  - Next.js static build (`npm run build`): Thành công 100%.


## 53. Sửa môi trường compile ZIP Optica (06/11/2026)

Đã nhập đúng `D:\download\Universal_manuscript_template_for_Optica_Publishing_Group_journals.zip` bằng `ProjectIO.import_project` vào home kiểm thử riêng `target/optica-zip-repro-01`. ZIP có class `optica-article.cls`, style, hai ảnh PDF và bibliography đầy đủ. Import tạo main.tex từ Optica-template.tex theo cơ chế hiện tại; không sửa ZIP hoặc mã LaTeX.

Tái hiện đúng ảnh: class gọi `xcolor[table]` nên cần `colortbl.sty`, nhưng TinyTeX tối thiểu chưa có. Sau khi cài gói đầu tiên, các lượt compile tiếp tục phát hiện thiếu silence/newtx/lastpage/cite, dependency xpatch/xstring/fontaxes/mweights, scalefnt và centernot, rồi thiếu font metric Courier và TeX Gyre. Đã xác định scalefnt thuộc package `carlisle`, centernot thuộc `oberdiek` bằng tlmgr search --global --file; không giả định tên .sty luôn bằng tên package.

Đã cài các gói/font vào **target/TinyTeX**. Thêm `scripts/install-optica-tex.ps1` để máy khác có TinyTeX local chuẩn bị cùng bộ dependency: dùng Perl đi kèm, tlmgr install tên package thật, kiểm kpsewhich cho các .sty và font TFM, trả lỗi khi cài/kiểm tra thất bại, khôi phục PATH/PERL5LIB. Chỉ thao tác TinyTeX local trong repo, không đổi TeX hệ thống. README có lệnh PowerShell và yêu cầu mạng; package đã cài trong target không đi theo Git nên máy khác cần chạy script sau khi nhận source.

Không cần thay mã compiler, Error Explainer hoặc tắt kiểm tra lỗi. Compiler hiện có marker failed/-g để retry sau khi cài package mà không phải sửa nguồn hoặc xóa dự án.

Validation: imported ZIP compiled through the application Compiler using pdfLaTeX: 7 pages, 453867 bytes, 3984 ms, verified current snapshot. Evidence: target/optica-zip-repro-01/report.json, events-before.json, events-after.json and preview-page-1.png. No missing characters or undefined references/citations in final log. Remaining non-fatal warning: TS1/phv/b/sc font shape unavailable, default shape substituted by TeX. PowerShell installer syntax and real installation/probes passed; no frontend rebuild needed for this environment-only fix.


## 54. Compile nhiều tệp gốc và tab Editor/PDF/Word trong một dự án (06/11/2026)

**Hành vi:** ô **Tệp biên dịch / Compile file** chọn bất kỳ `.tex` trong dự án, ví dụ `ielts_report.tex`; Recompile, F5, Ctrl/Cmd+Enter và Auto Compile dùng đúng lựa chọn. Mở file có `\documentclass` sẽ tự chọn file đó. Mở đoạn nội dung dùng input/include giữ root đang chọn; fragment không có preamble vẫn cần compile qua tài liệu gốc. Không thay metadata `project.main` của dự án.

**Frontend M1:** `page.tsx` có hai dãy tab nguồn và output (`main.pdf`, `ielts_report.pdf`, `ielts_report.docx`...), nút đóng, cuộn ngang và tooltip đường dẫn đầy đủ. Giữ ít nhất một tab nguồn. Đổi/đóng tab lưu nguồn và flush Word trước; lỗi lưu sẽ giữ tab đang làm. Cache PDF, snapshot, geometry và log theo root trong phiên; chuyển tab khôi phục bản tương ứng, không lấy PDF cuối cùng của cả project. Ghi nhớ cursor/scroll của editor theo file; editor riêng theo key tránh dùng chung undo qua hai file. Chuyển file khóa nhập tạm thời, có token chống response cũ ghi đè lựa chọn mới, chặn compile trong lúc tải file. History restore thêm lại source tab; tab không còn tồn tại được lọc khi đổi file/cập nhật cây. Tab/cache PDF reset khi mở dự án khác; dữ liệu nguồn/DOCX đã lưu vẫn nằm trên đĩa.

`useParity.ts` truyền root vào watch, khóa auto-compile theo project/root/engine/revision, bỏ response watch sai root/file sau khi đổi tab. Cửa sổ PDF tách riêng nhận root đang xem thay vì luôn theo PDF cuối project. Download đặt tên theo root. CSS tab có active indicator, màu theo theme và nút đóng độc lập; không thay bố cục sidebar/Review/IELTS đang có.

**Backend M2:** CompileBody thêm `main` tùy chọn, mặc định vẫn `project.main` để tương thích caller cũ. Compiler.start kiểm `.tex`, đường dẫn và file có thật, Job giữ root cố định. Root mặc định vẫn ghi `build/main.*`; root khác ghi trong `build/targets/<hash-full-path>/`, tránh đè PDF/aux/SyncTeX khi hai thư mục đều có main.tex. Mỗi Snapshot mang main, documents cache theo (project, main); lỗi/recompile chỉ vô hiệu snapshot của root đó, không xóa kết quả root khác. Preview/geometry/export/SyncTeX tìm đúng snapshot theo ID. Watch cache cũng theo root; kiểm nguồn/export vẫn dùng fingerprint toàn dự án, nên sửa một file dùng chung hoặc file khác có thể khiến preview cũ bị đánh dấu stale một cách bảo thủ.

**Word:** `docx_workspace.py` tách draft/revision/checksum theo root; tệp chính giữ `.overleaf-docx/` để không mất draft cũ, root khác dùng `.overleaf-docx-<hash>/`. POST preview nhận snapshot đúng root; GET/PUT revision thêm query main. Native/browser export lấy đúng DOCX đã sửa của root, tên tải xuống theo tệp, native dialog kiểm lại revision tương ứng. Draft có sẵn được xem/sửa tiếp khi nguồn stale, nhưng chuyển đổi mới và download vẫn cần verified PDF. Chế độ Word phân trang hỗ trợ mọi root; chế độ đồng bộ chữ LaTeX/Pandoc cũ chỉ cho project.main, nút có giải thích khi root khác. Không tuyên bố đồng bộ DOCX đầy đủ ngược về LaTeX hoặc tăng fidelity của converter gốc trong thay đổi này.

**Kiểm chứng backend:** full suite **144 pytest đạt**. `test_multidocument.py` kiểm hai root cùng basename, không đè output, giữ preview/API/watch của cả hai, lỗi root B không xóa root A, từ chối traversal/file không phải .tex; kiểm sửa Word report không sửa Word main, revision không dùng chéo và stale chặn download. Sau bổ sung xem draft stale, nhóm tests multi-document + DOCX chạy lại **5 đạt**.

**Compile thật:** `target/multidoc-real-01/report.json` ghi main.tex, ielts_report.tex sinh bởi IeltsManager và nested/main.tex đều compile pdfLaTeX, nội dung PDF khác nhau đúng nguồn và vẫn verified sau khi compile root khác. Query SyncTeX forward của report trả tọa độ hợp lệ. Khi tái hiện IELTS phát hiện TinyTeX local thiếu tcolorbox; đã cài tcolorbox/environ/trimspaces rồi compile lại thành công, không sửa source báo cáo để né dependency. Các gói local này không đi theo Git; README nêu yêu cầu cho máy khác.

Browser integration mới: `apps/frontend/scripts/test-document-tabs.mjs`, chạy với Python executable và một home mới. Kiểm main/report compile riêng, PDF/Word tabs, sửa Word → đổi main → quay lại report vẫn giữ chữ; Word main không có chữ của report; tải report.docx trùng bytes draft report; sửa nguồn/đổi tab được lưu và đóng tab chuyển sang output còn lại. Bằng chứng `target/multidoc-ui-05/document-tabs.png`, `document-tabs-word.png`, `edited-report.docx`. Production build/TypeScript và kiểm whitespace các file sửa đạt. Đã cập nhật selector regression cũ để phân biệt file tree với source tabs có cùng tên/tooltip.

Final browser checks: multidoc-ui-06 passed (including closing source/output tabs and reviewing saved Word after source changes); multidoc-regression-02 passed existing features; multidoc-pdf-regression-01 passed 3-page PDF/link/exact-export/stale/error guards. Logs use the selected compile root when no explicit source filename is present. No commit or push performed.


## 55. Đường chạy Local Web độc lập desktop và cập nhật thư viện (06/11/2026)

**Phạm vi:** bước đầu đưa mã Python + Next.js hiện có lên trình duyệt local; không yêu cầu pywebview/native WebView. Chưa triển khai dịch vụ Internet nhiều người dùng, phòng WebSocket/CRDT hoặc chuyển toàn bộ dữ liệu UI sang MongoDB.

**Khởi động:** thêm `overleaf_desktop/web.py`, console entry `overleaf-web`, `scripts/run-python-web.bat` và `.sh`. Windows chạy --build để npm ci/build, sau đó chạy launcher không tham số để mở http://127.0.0.1:8000/. Có --port, --home, --frontend, --latexmk, --no-browser; bind chỉ loopback. Default home vẫn ~/OverleafDesktopPython để giữ dữ liệu, ưu tiên OVERLEAF_WEB_HOME rồi OVERLEAF_PYTHON_HOME. Không đổi/move tên folder apps/desktop hoặc package cũ để tránh phá import. Desktop entry/launchers cũ vẫn giữ.

**Phiên web:** create_api thêm mode web có POST /api/web/session phát cookie HttpOnly, SameSite Strict, path=/api; token không trả trong JSON/link. Frontend bootstrap idempotent và mọi request chờ bootstrap, dùng cookie cùng origin. Host/origin/cross-site được kiểm trước API. Desktop/test vẫn dùng bearer; reload desktop fallback cả 404 và 405 vì static mount có thể trả 405 cho POST route không tồn tại. Mode web hiện là một workspace/local profile dùng chung trong process, chưa tách phiên người dùng theo browser hoặc phân quyền project; không public qua reverse proxy ở bước này.

**Frontend:** brand/title đổi Local Web khi health trả mode local-web. Download PDF/DOCX dùng luồng browser đã có. Tab PDF riêng dùng window.open cùng root/cookie; SyncTeX gửi message về opener có kiểm origin/project/file/line/column. API binary có whitelist ảnh/PDF, Store path validation, size cap và SVG attachment/sandbox; browser tải SVG xuống thay vì thực thi như trang app. Hộp chọn ảnh đại diện có browser input và POST avatar; upload folder có fallback webkitdirectory. Chọn thư mục lưu ở local server dùng nhập đường dẫn khi không có native picker; không giả vờ browser cung cấp absolute path thư mục upload. Java migration có API action local tương đương và prompt đường dẫn. stage_source/close_pdf_windows là no-op khi không có native bridge. Nguồn/Word chưa lưu sẽ có beforeunload guard.

**Dependency:** Next 16.4.4 →16.4.5 (pin), CodeMirror view 6.38.0 →6.43.15, Playwright 1.64.1 →1.65.0. FastAPI/Uvicorn min nâng theo bản đang kiểm 0.162/0.54. Group [web] gồm uvicorn[standard] + pymongo>=4.19,<5, không kéo pywebview. Lock cập nhật Pydantic 2.14.3/core 2.50.4, dnspython 2.9.2, typing-inspection 0.4.5; thêm httptools/websockets/watchfiles/dotenv/yaml và uvloop theo platform. Không nâng major TypeScript/Lucide/Pillow/PyMuPDF; giữ nguyên converter bundle và pins để không đổi output người dùng. Launcher ưu tiên uv frozen --extra web --inexact (giữ các extra cũ), có fallback pip nếu không có uv.

**MongoDB:** đọc lại persistence.py xác nhận đây là repository prototype chưa UI-wired. Đã cập nhật/cài PyMongo nhưng không tự migrate hoặc ghi dữ liệu Mongo; Store/UI vẫn file+JSON/history. Không yêu cầu Mongo đang chạy để mở local web. README/LOCAL_WEB ghi rõ giới hạn này và bước sau cần user sessions, membership/ACL, Mongo storage thực, room/document sync và compile workers trước khi triển khai public.

**Kiểm chứng:** Windows launcher --build chạy thật npm ci + Next production static build/TypeScript đạt. Launcher không --build với --port 0 --no-browser --home riêng chạy thật, bootstrap cookie/health trả local-web; dừng Ctrl+C (Windows hỏi terminate batch chọn Y). uv lock --check, git diff --check và shell script bash -n đạt; chưa chạy runtime Mac/Linux. Npm audit 0 advisory được báo.

Tạo venv `target/local-web-clean-env` bằng uv sync frozen extra web: 33 package, **không có pywebview**; import module web và CLI production/browser test đều chạy được. Full backend **148 pytest đạt**, thêm 4 test cookie/bootstrap/origin/Host/cross-site, desktop cookie isolation, binary paths/SVG và CLI không bind public. Cảnh báo Starlette/httpx TestClient có sẵn vẫn chưa gây fail.

Browser script mới `scripts/test-local-web.mjs` gọi module web production, không dùng server test hoặc bearer URL. `target/local-web-ui-03` đạt: cookie HttpOnly/no token trong URL/sessionStorage; reload; create/save; compile PDF 2 trang và download exact bytes; tab PDF riêng; Word edit/download trùng draft bytes; upload/mở ảnh; avatar; ZIP browser import rồi compile. Screenshot local-web-word.png/local-web-home.png trong home test. Lượt đầu chọn text-run quá cụ thể không khớp DOCX có nhiều run, đã đổi selector line có chữ LOCAL; không sửa converter để né test.

Regression cũ: `local-web-documents-regression-01` đạt nhiều root/source/PDF/Word và cách ly edit/export; `local-web-legacy-regression-02` đạt toàn bộ features cũ, kể cả reload/profile/English sau khi sửa bootstrap 405. README chuyển quickstart chính sang web; chi tiết ở docs/LOCAL_WEB.md. Chưa commit/push trong lượt này.


## 56. Rename displayed application to Latex (2026-11-06)

Changed the visible app name to Latex in the login screen, header, browser metadata/document title, desktop/PDF window titles and CLI descriptions. Removed the desktop/local-web suffix from the displayed brand. README and local-web guide headings now use Latex. Kept package identifiers, session keys, data folders, migration references and readiness markers for compatibility with existing projects and launch/test tooling. Updated the existing browser title assertion to Latex. Production build/TypeScript checked; no new unit tests for this presentation-only change.

Browser verification: latex-brand-ui-01 passed with the browser title Latex and existing local-web workflows intact.

## 57. Lưu dữ liệu web trên server và chia lại backend (07/11/2026)

**Yêu cầu:** điều chỉnh cơ chế lưu cho web thay cho thao tác filesystem kiểu desktop; sắp xếp backend gọn theo chức năng, chuẩn bị cho publish.

**Đã thực hiện:**

- Web dùng `Store(managed=True)`: tạo và import luôn vào `<data-dir>/projects/<id>`. `Store.destination()` dùng chung cho create/import; không nhận `parent` khác rỗng. Đường dẫn được chỉ định bằng `--home` hoặc `LATEX_DATA_DIR` bởi người vận hành. Fallback biến môi trường/default cũ được giữ để không mất dự án hiện có.
- API web chặn `project-move`, `import-path`, `migrate-java`, đổi `projectsDirectory`/`latexmkPath` và cài gói TeX qua browser. Setting mặc định desktop cũ không còn điều khiển thư mục lưu của web. Project trả `storage=server`, `folder` rỗng. Kiểm tên file import trước ghi temp, từ chối slash/backslash/colon/NUL. Upload ZIP vẫn giữ giới hạn size, kiểm path và dọn temp.
- Giao diện bỏ trường chọn thư mục server ở tạo dự án, cài đặt, quản lý dự án; bỏ chọn executable và Java path migration trên web. Lưu editor/Word vẫn qua API; download vẫn lấy bản sao PDF/DOCX về máy. Nhãn storage/account/save đổi theo mode; nút guest trên web là tiếp tục không đăng nhập. Desktop native giữ chức năng chọn/move thư mục.
- Dự án đã ở `<home>/projects/<id>` được giữ. Dự án desktop đăng ký ở thư mục ngoài không được phục vụ trong web; **không xóa/di chuyển bản gốc**. Nén ZIP rồi upload để đưa bản sao vào vùng managed. Dữ liệu vẫn là file/JSON/history trên server; chưa nối MongoDB UI.

**Cấu trúc thực tế đã chuyển:** `core` (config/composition), `http` (API), `storage` (store/state/files/history/persistence), `compilation` (compiler/toolchain/explainer/synctex), `documents` (word/docx_workspace/ielts), `collaboration` (network/review). `pdf_export` và `resources` giữ riêng. `web.py`, `main.py`, `console.py` là entry/adapter. Các file cũ cùng tên tại root là module alias đến triển khai mới, giữ import và monkeypatch identity; không có hai bản logic. Internal import đã chuyển sang package chức năng. Giữ đường dẫn `apps/desktop` và tên distribution/import để không phá launcher/venv. Converter gốc `pdf_export/original` không thay đổi.

**Kiến trúc lựa chọn:** modular monolith FastAPI. Tách dữ liệu khỏi source và có application factory ở `core/application.py`. Chưa thêm microservice hoặc lớp repository trống; bước sau có thể tách compile worker và nối Mongo metadata theo tenant. Chi tiết từng folder, volume dữ liệu và cấu hình: `docs/WEB_BACKEND_ARCHITECTURE.md`; README và LOCAL_WEB đã cập nhật. Thêm `/var/` vào gitignore để tránh commit data-dir mẫu.

**Kiểm chứng:** full backend **152 pytest đạt** (148 cũ + 4 kiểm thử managed storage). Kiểm lưu và mở lại bằng Store/backend mới; upload ZIP managed; từ chối browser-supplied path/settings/move/import-server-path; project external không bị đọc hoặc xóa; alias tương thích. Next production build/TypeScript đạt. Browser production `target/server-storage-web-02` chạy bằng clean web env không pywebview: xác nhận không còn trường thư mục ở settings/new/manage, tạo/lưu, reload, PDF 2 trang/export cùng bytes, tab PDF riêng, Word edit/export cùng bytes, upload ảnh/binary/avatar và ZIP import/compile. Lượt `server-storage-web-01` cũng đạt luồng cũ trước khi bổ sung kiểm tra field. CLI networking cũ `python -m overleaf_desktop.network --help` vẫn chạy. `git diff --check` đạt, chỉ có thông báo chuẩn hóa CRLF/LF.

**Giới hạn publish:** đây vẫn là một workspace/process trên loopback. Cookie bootstrap và account state chưa phải session riêng theo từng user/browser; chưa có owner/membership/permission cho project. Trước public phải hoàn thiện các phần này, worker TeX cách ly/quota, giới hạn outbound và HTTPS. MongoDB hiện chỉ là repository prototype; không khai báo đã migrate. Không sửa `deploy/python/compose.yaml` vì đã có thay đổi riêng trước lượt này; file đó chưa được dùng để publish web. Chưa commit/push.

## 58. Chuyển vật lý sang apps/backend, bỏ lớp alias (07/11/2026)

Người dùng phản hồi cấu trúc ở mục 57 vẫn rối vì còn `apps/desktop`, package cũ và nhiều alias. Đã tiếp tục chuyển **toàn bộ thư mục Python sang `apps/backend`**, package thành **`src/latex_backend`**, distribution thành `latex-backend`. Root package chỉ còn `__init__.py` và các thư mục `entrypoints`, `core`, `http`, `storage`, `compilation`, `documents`, `collaboration`, `pdf_export`, `resources`. Không còn alias `store.py`, `compiler.py`, `api.py`... ở root. `web.py`, `main.py`, `console.py` chuyển thành `entrypoints/web.py`, `entrypoints/desktop.py`, `entrypoints/console.py`.

Đã cập nhật import và monkeypatch target trong source/test, đường dẫn test server trong các browser scripts, module subprocess PDF→DOCX, package-data, lock, tất cả launcher Python `.bat`/`.sh`, `ensure-python.ps1`, Dockerfile networking và hướng dẫn deploy. Lệnh launcher quen thuộc vẫn dùng được; lệnh Python trực tiếp mới là `python -m latex_backend.entrypoints.web`, console entry mới `latex-web`. Các tên console entry cũ còn giữ như tên lệnh tương thích, không giữ source alias. Runtime/venv đã chuyển cùng thư mục và editable install đã cài lại vào đường dẫn mới. Clean web env đã sync frozen sang distribution mới. Không sửa dữ liệu dự án hoặc source bundle converter gốc.

Cập nhật README, thêm `apps/backend/README.md`, sửa tài liệu kiến trúc, LOCAL_WEB, migration, PDF export và cleanup plan theo đường dẫn mới. Các ghi chú ở mục 55–57 về việc giữ đường dẫn/package/alias là lịch sử và được thay thế bởi mục này. `deploy/python/compose.yaml` có thay đổi riêng của người dùng vẫn không bị sửa.

Kiểm chứng: **152 pytest đạt** từ `apps/backend/tests`; production browser `target/backend-restructure-web-01` đạt tạo/lưu/reload, PDF 2 trang/export exact bytes, tab PDF, Word edit/export, upload/binary/avatar và ZIP import/compile trong clean env không pywebview. Launcher Windows `scripts/run-python-web.bat --help` thực sự sync/import/chạy thành công. `uv lock --check` đạt; các launcher `.sh` đạt bash syntax check. Frontend UI không đổi trong bước này nên dùng lại production build đã đạt ở mục 57. Xác nhận `apps/desktop` không còn tồn tại và root package không còn alias. Chưa commit/push. Giới hạn single workspace/session và yêu cầu trước public ở mục 57 vẫn áp dụng.

## 59. Thiết kế Writing theo Writing.pdf và prototype tương tác (07/11/2026)

Đã đọc `D:\download\Writing.pdf`: 4 trang; trang 1 là yêu cầu nghiệp vụ, trang 2–4 là sơ đồ UI raster. Đã trích text và render/xem cả các trang UI, không chỉ đọc text extraction. Bằng chứng đọc ở `target/writing-design/extracted.txt`, `page-1.png` đến `page-4.png`; không sao chép PDF gốc vào repository.

**Bản thiết kế:** `docs/WRITING_PRODUCT_DESIGN.md`, gồm mapping trang/mã GV01–GV08, SV01–SV08, SV-W01–SV-W04; đề xuất Admin; vai trò/quyền theo resource; assignment/class/group và workspace riêng cho từng assignee; wizard giao bài; rubric phân cấp, checkpoint và các chế độ tính điểm; workflow draft/submission/assessment/publish; thời gian, nộp lại, invitation, review và cộng tác. Chỉ ra nguyên tắc văn bản → xem trước LaTeX → xác nhận → compile, không tự đồng bộ hai chiều và không lấy DOCX từ PDF làm canonical rich-text document. Thiết kế submission bất biến gắn revision/artifact hash/rubric version và chỉ trả điểm đã công bố.

Thiết kế cũng đối chiếu code hiện có (`documents/ielts.py`, review, compile/PDF/Word, Store, Mongo prototype và TCP/TLS), đề xuất module identity/education/assessment/submissions/jobs, schema/index Mongo, API/events, worker/artifact storage, lỗi biên, 20 tiêu chí nghiệm thu và lộ trình 6 giai đoạn. Đã kiểm tài liệu chính thức IELTS, MongoDB PyMongo Async, Yjs bindings và FastAPI WebSocket; link nguồn đặt trong tài liệu. Các folder/library trong phần đề xuất chưa được tạo/cài như tính năng thật.

**Prototype độc lập:** `docs/prototypes/writing/index.html`, `styles.css`, `app.js`, `README.md`. Mở HTML trực tiếp bằng browser; có 21 màn hình theo bản đồ: 8 giảng viên + 8 sinh viên + 4 bước văn bản/LaTeX + 1 Admin đề xuất. Có wizard, lời mời demo, rubric editor, sửa văn bản, góp ý/resolve, chuyển source minh họa, preflight/nộp, chấm điểm, công bố, student results, role switch, reset và lưu localStorage. Role switch chỉ phục vụ duyệt thiết kế; không phải cơ chế phân quyền production.

**Giới hạn rõ trên UI/tài liệu:** không gửi email, không CRDT/cộng tác thật, không compile hoặc xuất PDF/DOCX thật; tờ giấy là HTML có nhãn minh họa. Một số policy/formatting chỉ minh họa, prototype giữ snapshot lần nộp gần nhất; sản phẩm thật phải lưu đủ attempts. Không thay đổi runtime application/API hoặc dữ liệu project của người dùng trong đợt thiết kế này. README đã thêm liên kết tới bản thiết kế và prototype.

**Kiểm chứng:** thêm `apps/frontend/scripts/test-writing-design.mjs`, chạy Edge headless đạt wizard/invite; viết/chuyển LaTeX/nộp; working copy đổi không làm đổi submission snapshot; source stale chặn nộp PDF demo; chưa publish không có kết quả; nhập 3 tiêu chí ra 8/10, đủ điểm; publish rồi sinh viên xem đúng phản hồi; reload; duyệt tất cả 21 màn; viewport 390px không tràn ngang. Đã xem ảnh screenshot desktop màn viết/chấm và mobile. Ảnh ở `target/writing-design/teacher-dashboard.png`, `student-writing.png`, `teacher-grading.png`, `student-feedback.png`, `mobile-writing.png`. Không chạy lại full backend/build vì chỉ thêm tài liệu/prototype độc lập và script kiểm thử, không sửa app runtime. Chưa triển khai nghiệp vụ Writing lên server, chưa commit/push.

## 60. Chỉnh prototype Writing bám giao diện chính (07/11/2026)

Theo phản hồi người dùng, bản mẫu ban đầu trông giống dashboard riêng nên khó hình dung tích hợp. Đã đọc phần header/rail/sidebar/editor/preview/statusbar và tokens sáng/tối trong `apps/frontend/app/globals.css`, đối chiếu cấu trúc `page.tsx` cùng screenshot ứng dụng thật `target/backend-restructure-web-01/local-web-word.png`.

Cập nhật prototype tại `docs/prototypes/writing`: thêm `workspace.css`/`workspace.js` làm lớp trình bày theo workspace hiện có; thay logo tự thiết kế bằng lá + Latex, header 42px, rail icon SVG tối rộng 46px, sidebar chuẩn 270px, statusbar gọn. Giữ vị trí Lịch sử/Cộng tác/Bố cục, tab main.tex/main.pdf/main.docx, toolbar editor tối, source có số dòng, Build output và preview giấy trắng. Bản mẫu mở ở GV01 để thấy editor trước; Writing dùng cùng shell, chuyển menu/nội dung theo vai trò. Bỏ banner lớn phía trên và thẻ thống kê cồng kềnh ở danh sách; giảm padding/bo góc, dùng phông/token theo ứng dụng. Thêm Sáng/Tối; role/map/reset demo chuyển xuống footer. Key localStorage demo v2 độc lập với bản mẫu cũ, không liên quan dữ liệu app.

Không sửa UI production hoặc backend. Khung source/PDF ở màn mở đầu vẫn là minh họa có ghi rõ, không giả compile hoặc Word thật. Các nút header bổ sung giữ vị trí giao diện nhưng mở giải thích prototype; không gọi API ứng dụng. README prototype và phần giao diện trong WRITING_PRODUCT_DESIGN đã cập nhật.

Kiểm thử `test-writing-design.mjs` được mở rộng: kiểm đúng header 42px/rail 46px, khung editor mặc định, các luồng nghiệp vụ demo cũ, tất cả 21 màn, chuyển light/dark, viewport mobile không tràn ngang và nội dung main cuộn được. Screenshot mới ở `target/writing-design/current-workspace.png`, `current-workspace-dark.png`, `teacher-grading-dark.png` cùng các ảnh màn viết/chấm/kết quả. Đã xem trực quan các screenshot và sửa tương phản nút rail/bài nộp khi dark. Chưa commit/push.

## 61. Đính chính thiết kế: IELTS Writing là trung tâm (07/11/2026)

Người dùng cho biết thiết kế vẫn hiểu sai và yêu cầu phù hợp IELTS Writing. Đã chuyển prototype từ luồng Research Introduction/paper sang **luyện và chấm IELTS cá nhân**; giữ shell Latex theo mục 60. Mặc định Học viên → Luyện viết → Task 2, không mở source LaTeX hay Research Introduction.

Đã thay script prototype bằng `ielts.js` và bố trí `ielts.css`; bỏ `app.js`/`workspace.js` nghiên cứu cũ, vẫn dùng styles/workspace.css chung. Chọn Academic/General Training: Task 1 Academic có biểu đồ tự biên soạn, General Training là thư; Task 2 là essay. Đề trái, answer phải, lưu riêng task, word counter, chế độ một task hoặc đủ hai task, countdown demo. Nộp trực tiếp văn bản, không ép chuyển LaTeX/compile PDF. Thiếu từ cảnh báo; task trống không nộp được; snapshot tách working copy.

Giảng viên có Giao bài IELTS, Bài nộp, Tiêu chí IELTS; màn chấm theo học viên, bài nộp chỉ đọc, TA/TR/CC/LR/GRA, band và feedback theo tiêu chí. TA của GT chú trọng thư thay vì ép overview biểu đồ. Điểm ban đầu null, lưu nháp khác công bố. Một task chỉ có task band; đủ cả hai mới có Writing band ước tính, dùng raw task means và trọng số 1:2 trước làm tròn 0.5 theo policy demo. Không tự nhận quy trình chấm chính thức hoặc chấm AI. LaTeX/PDF/Word chuyển thành tùy chọn xuất phiếu, không phải trung tâm IELTS.

Thêm `docs/IELTS_WRITING_DESIGN.md`, cập nhật README/prototype README, đánh dấu WRITING_PRODUCT_DESIGN là thiết kế tổng quát cũ để tham khảo mở rộng. Đã đối chiếu thông tin task/time/criteria/weight với trang IELTS.org chính thức và đặt nguồn trong tài liệu. Không sửa code app production hoặc backend trong đợt thiết kế.

Kiểm thử mới `test-ielts-writing-design.mjs`; lệnh cũ `test-writing-design.mjs` là alias. Edge headless đạt Academic chart, GT letter, Task 2 essay, counter, lưu đổi task, thiếu từ vẫn có thể nộp sau cảnh báo, nộp không LaTeX, immutable demo snapshot, band null/partial guard, draft không hiện ở results, chấm và công bố, task band riêng, Writing 1:2 cho hai task, countdown chung, reload, sáng/tối và mobile cuộn/không tràn. Ảnh tại `target/ielts-writing-design/` gồm student-task1/student-task2/student-answer/teacher-grading/student-feedback/student-dark/student-mobile. Đã xem ảnh màn Task 1, bài essay và chấm; thu gọn feedback từng tiêu chí thành details để bốn band dễ nhìn. Dữ liệu/đồng hồ chỉ ở browser, giữ bản nộp gần nhất; chưa có server, phân quyền thực, export thật hoặc lịch sử đầy đủ. Chưa commit/push.

## 62. Góc nhìn Tutor mở trực tiếp (07/11/2026)

Thêm `docs/prototypes/writing/tutor.html` mở `index.html#role=tutor`, đổi nhãn lựa chọn Giảng viên thành Tutor (Giảng viên). `tutor.js` bổ sung tổng quan Tutor, danh sách học viên/bài viết, số chờ chấm/đã trả lấy từ demo, menu Giao bài IELTS/Bài nộp & chấm/Tiêu chí IELTS. Chọn Tutor ở footer mở tổng quan thay vì vào ngay danh sách nộp. Vẫn giữ kiểu workspace hiện có.

Có nút Mở bài chấm mẫu: nếu chưa có submission thì yêu cầu xác nhận tạo essay snapshot tự biên soạn với band null để xem màn chấm; không thay working copy của học viên. Nếu đã có submission thì mở bản đó, không tạo lại hoặc mất điểm nháp. Tất cả vẫn là prototype localStorage, không tạo quyền role hoặc bài nộp trên server. Cập nhật README prototype và thiết kế IELTS.

Kiểm thử mới `test-tutor-design.mjs` đạt mở shortcut, tổng quan, bài mẫu, giữ draft học viên và điểm khi mở lại, mobile. Kiểm thử IELTS cũ cũng đạt sau cập nhật điều hướng Tutor. Đã xem ảnh `target/ielts-tutor-design/tutor-overview.png`; có ảnh màn chấm `tutor-grading.png`. Không sửa production app/backend, chưa commit/push.

## 63. Kết hợp IELTS với LaTeX trong màn làm/chấm (07/11/2026)

Theo yêu cầu giữ ứng dụng LaTeX, thêm `latex-ielts.js` vào prototype. Màn làm bài và Tutor chấm có tabs Văn bản / LaTeX source / PDF preview; band và nhận xét IELTS vẫn giữ ở cột bên phải. Học viên có bước xác nhận tạo/cập nhật source từ văn bản; source có escape ký tự, có thể chỉnh như nhánh trình bày. Prototype vẫn lấy văn bản làm nội dung chấm chính và không giả đồng bộ hai chiều. Khi nộp, kèm source của snapshot phù hợp nội dung; Tutor đọc source bản nộp, không lấy working copy sau nộp. Preview giấy có nhãn rõ chưa compile thật.

Tutor có Tạo phiếu LaTeX, tải source .tex thật chứa học viên, task, TA/TR/CC/LR/GRA, band/nhận xét và trạng thái nháp/công bố. Preamble XeLaTeX/fontspec/TeX Gyre Termes, hỗ trợ tiếng Việt. Xuất PDF/Word thật qua pipeline app vẫn cần tích hợp API, chưa có trong prototype. Đã cập nhật thiết kế IELTS và prototype README: IELTS là nghiệp vụ chấm, LaTeX là công cụ soạn/trình bày/xuất phiếu; chế độ LaTeX canonical đầy đủ cần source revision, trích nội dung và kiểm freshness riêng, không coi là đã triển khai.

Kiểm thử `test-latex-ielts-design.mjs` đạt văn bản→escaped TeX, source gắn submission, Tutor đổi source/PDF mock giữ cột band và tải phiếu .tex. Bộ test IELTS và Tutor hiện có cũng đạt. Ảnh ở `target/latex-ielts-design/tutor-latex-and-band.png`, `tutor-pdf-and-band.png`. Đã compile **thật** file tải xuống `ielts-feedback.tex` bằng latexmk -xelatex trong TinyTeX: thành công, tạo `target/latex-ielts-design/build/ielts-feedback.pdf` 1 trang; đây là phiếu draft kiểm chứng, không phải PDF preview tích hợp. Không sửa runtime app/backend hoặc publish; chưa commit/push.

## 64. Checklist chấm theo phần và nghiên cứu các dạng IELTS (07/11/2026)

Theo yêu cầu khung giống chat, thêm bố cục màn chấm: bài nộp bên trái → góp ý/checklist theo phần ở giữa → bốn band IELTS ở khung cuối. Đã nghiên cứu nguồn chính IELTS.org (band descriptors PDF, Writing resources, Academic/GT formats, question prompts), British Council assessment, IDP Task 1/Task 2. Tài liệu `docs/IELTS_SECTION_CHECKLISTS.md` ghi nguồn, cấu trúc và checklist đề xuất; phân biệt hướng dẫn sư phạm với tiêu chí/band chính thức, không coi số tick là band và không ép số câu/đoạn.

Catalog prototype `rubric-catalog.js`: 21 hồ sơ gồm 8 Academic Task 1 (line/bar/pie/table/mixed/process/map/diagram), 3 GT (formal/semiformal/informal), 10 Task 2 (opinion/discussion/advantages/outweigh/causes-solutions/problems-solutions/causes-effects/two-part/positive-negative/custom). GT có 8 chức năng thư. Checklist Intro, Overview, Body n, Conclusion hoặc Opening/Closing/Toàn bài; mã liên quan TA/TR/CC/LR/GRA. Không khẳng định taxonomy này là danh sách chính thức khép kín; custom dùng cho đề kết hợp.

`section-review.js`/`.css`: thẻ Tutor dạng góp ý, checkbox và năm trạng thái, notes theo phần, chọn quote, gán nhiều đoạn cho một phần hoặc một đoạn nhiều phần, thêm Body. Phân chia paragraph chỉ là gợi ý; chưa gán không kết luận thiếu phần. Tick không thay band. Dữ liệu scope theo attempt/time/task/profile/catalog version; đổi hồ sơ giữ dữ liệu cũ. Công bố clone góp ý theo phần; học viên chỉ thấy bản đã công bố, sửa draft sau đó không đổi phản hồi cũ. Report LaTeX thêm nhận xét/checklist đã xem. Văn bản/source/PDF tabs giữ lại, không giả annotation PDF/SyncTeX thật.

Đã cập nhật prototype README và thiết kế IELTS. Kiểm thử `test-section-review-design.mjs` đạt cả 21 hồ sơ, 8 chức năng thư, intro/body/overview, tách tick khỏi band, quote, gán đoạn, thêm Body, reload, publish và tính bất biến phản hồi, sáng/tối/mobile/cuộn. Ba regression IELTS/Tutor/LaTeX cũng đạt sau thay đổi. Đã xem screenshot `target/ielts-section-review/tutor-introduction-checklist.png` và `task1-overview-checklist.png`; có dark letter screenshot. Chỉ thay prototype/tài liệu/tests, chưa nối production API/backend, chưa commit/push.

Kiểm thêm xuất phiếu sau công bố: source có notes/checklist đã công bố, không chứa chỉnh sửa PRIVATE NEW DRAFT. File `target/ielts-section-review/section-feedback.tex` đã compile thật bằng latexmk -xelatex thành `build/section-feedback.pdf`. Prototype vẫn không tự compile PDF trong browser; đây là kiểm chứng source tải xuống.

## 65. Thẻ comment bên lề theo đoạn, ưu tiên diện tích bài viết (07/11/2026)

Theo yêu cầu giao diện gọn như comment Google Docs, thêm `inline-comments.js`/`.css` vào prototype. Thay khung checklist giữa cố định bằng thẻ nhỏ nằm cạnh đoạn tương ứng, bài viết rộng hơn. Mặc định thẻ thu gọn; bấm đoạn hoặc heading thẻ để mở một checklist, tô vàng đoạn đang góp ý. Giữ checkbox/status/notes/evidence từ catalog hiện có, không lặp toàn đoạn vào thẻ. Bài và thẻ dùng chung scroll; ResizeObserver tính vị trí theo anchor và đẩy thẻ sau để không chồng lên nhau. Cấu hình profile/phân phần thu vào nút, có thêm Body. Các phần chưa gán đoạn nằm cuối lề, không giả anchor chính xác.

Band IELTS chuyển thành panel mở bằng Tổng kết band, có nút × và Esc, trả focus về nút mở khi đóng. Mặc định panel đóng để không chiếm chiều ngang của bài. Mobile thẻ xếp sau tài liệu, panel band tránh che thanh điều hướng. Văn bản/source/PDF và phiếu LaTeX, publish/frozen feedback vẫn giữ; chưa làm PDF geometry annotation thật. Chỉ đổi prototype, không sửa app runtime/backend.

Kiểm thử mới `test-inline-comments-design.mjs`: vùng tài liệu >780px ở viewport 1700, thẻ gắn đoạn, chỉ một thẻ mở, notes/checklist giữ khi đổi đoạn, không chồng thẻ, mở/đóng band bằng Esc, config/thêm Body, sáng/tối/mobile. Đã xem ảnh `target/ielts-inline-comments/reading-with-margin-comments.png`, `introduction-comment-open.png`; có body/dark/mobile screenshot. Các test IELTS/Tutor/LaTeX và section-review được cập nhật theo luồng mở panel/thẻ và chạy đạt, giữ kiểm 21 hồ sơ, quote, publish và export. README prototype và tài liệu checklist cập nhật bố cục mới, mục 64 là lịch sử bố cục cũ. Chưa commit/push.

## 66. Bài mẫu Task 1 mở trực tiếp cho Tutor (07/11/2026)

Thêm `tutor-task1.html` → mở prototype ở bài chấm mẫu Task 1 Academic, cùng `task1-demo.js`. Có biểu đồ cột Car/Bus/Bicycle năm 2000/2020, bài tự biên soạn 175 từ/4 đoạn đúng số liệu, các thẻ Introduction/Overview/Body 1/Body 2 và TA/CC/LR/GRA ban đầu null. Source LaTeX lấy từ bài mẫu. Đề và chart nằm trong details ở màn chấm để Tutor đối chiếu ngay; có thể thu gọn.

Thêm nút Thử Task 1 · biểu đồ ở tổng quan/danh sách/màn chấm Tutor. Chuyển bài giữ các phiên demo trong key localStorage riêng; Về bài trước khôi phục working copy/submission/điểm trước đó. Mở lại Task 1 giữ góp ý/band của Task 1, không gán điểm Task 2 sang. Không ảnh hưởng app/backend hay dữ liệu server.

`test-task1-demo.mjs` đạt chart/bài mẫu, thẻ Overview và không ép Conclusion, TA null rồi lưu được, giữ band TR Task 2 khi quay lại, shortcut/restored Task 1. Regression Tutor/inline comments/section-review cũng đạt. Ảnh `target/ielts-task1-demo/task1-reading.png`, `task1-overview-comment.png`. Prototype README cập nhật lệnh mở Task 1; chưa commit/push.

## 68. Existing official Python 3.15.2 local web environment (09/11/2026)

User requested no additional Python installation. Verified installed Python 3.15.2 and its venv launcher have valid Authenticode signatures. Created target/official-python-web-env and installed project libraries without altering the blocked apps/backend/.venv or Windows security policy. Added scripts/setup-installed-python-web.ps1 to reproduce setup with existing py -3; run-python-web.bat prefers this environment when present. Added web-local extra with MongoDB client and WebSockets using Uvicorn core/h11; existing web extra remains unchanged. Local app does not use YAML config/server reload. PyYAML lacks a Python 3.15 wheel in the checked index and source build encountered a Cython policy block. Updated uv.lock and startup docs. Pip setup follows pyproject constraints, not exact lock versions; pip check passed. Original converter and PDF/DOCX core pins preserved.

Actual launcher started at http://127.0.0.1:8000/; HTTP 200. Browser test-local-web.mjs passed with separate home target/python315-localweb-check-01: cookie/reload, create/save, 2-page PDF and exact export, PDF tab, Word edit/export, upload/image/avatar, ZIP import/compile. Main server left running. Automatic browser-opening command was rejected by tool policy; user can open the local URL directly. This entry supersedes the startup-blocked status below; no additional interpreter was installed.

## 67. Windows App Control blocks Python startup (09/11/2026)

Confirmed CodeIntegrity/Operational Event 3077 blocks apps/backend/.venv/Scripts/python.exe; Authenticode reports NotSigned. Failure occurs before the application starts. Updated scripts/ensure-python.ps1 to recognize policy-related launch exceptions, report the exact blocked path and stop without changing security settings or replacing the runtime. Added docs/WINDOWS_PYTHON_STARTUP.md with policy review and approved-runtime guidance. Actual launcher verification returns exit code 1 with the new actionable message. Server startup remains blocked until the device administrator approves the required runtime/dependencies; application runtime tests could not run in this environment.

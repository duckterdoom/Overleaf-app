# Đối chiếu Java → Python/Next.js

Cập nhật theo yêu cầu chuyển **các tính năng thực sự đã có trong Java**, lấy code và các mục mới nhất của AI_Handoff làm chuẩn. Bản mới mở cửa sổ desktop riêng; không gọi Java để thực hiện chức năng. [Hướng dẫn chạy và bằng chứng](PYTHON_NEXTJS_MIGRATION.md).

| Chuẩn Java | Backend Python | UI Next.js | Kiểm chứng |
| --- | --- | --- | --- |
| Đăng ký/login/remember/offline/logout | state.py, PBKDF2-SHA256 | AuthView | Pytest + browser register/remember |
| Profile name/email/password/avatar crop/remove | state.py + native bridge | ProfilePanel | Pytest ảnh/account + browser profile |
| Heatmap edits/ngày, compiles/năm | state.py | ActivityHeatmap | State tests + browser 364 ô |
| Theme/font/wrap/autoclose/autosave/auto compile/lang/compiler/timeout | state.py/API, reset defaults | SettingsPanel/useParity | Pytest preference/atomic validation + browser settings/English |
| Rename/trash/icon/color/custom storage/move/affiliation | store.py/catalog | ProjectMenu/NewProjectExtras | Pytest custom path/move/trash + browser create |
| 31 cấu hình × VI/EN, example/gallery/category/tag | packaged resources/templates | Gallery + blank/example | Materialize và 186 PDF native |
| ZIP/Word/Markdown/GitHub/folder | files.py, Pandoc/Git, owned process cancellation | NewProjectExtras/native dialogs | ZIP/import tests, DOCX/Markdown/HTTPS native; GitHub public-only validation |
| Nhập dữ liệu Java/account/avatar/settings/history/project ngoại vi | files.py/state.py/properties | Settings → Projects | Idempotent/source-preserving import + catalog path-key/appearance regression |
| File/folder/nested target/rename/trash/binary open | store.py/bridge | FileActions/AddFiles/tree | Pytest + browser nested file |
| Upload nhiều file/folder/empty folder/drag-drop/paste/queue/cancel | staged upload API/files.py | AddFilesPanel | Transaction/rollback/cancel tests + browser .bib upload |
| Copy source/build, URL HTTPS, import .bib | files.py | AddFilesPanel | Copy/HTTPS/validation; .bib browser |
| File watcher/save-on-switch/save-on-close | watch/dirty/native staged buffer | useParity | API/stale + native close-buffer test, window exit smoke |
| Full toolbar Math/Link/Comment/Ref/Citation/Figure/Table/List/Indent | symbols scanning | EditorToolbar/snippets | Browser order/link + snippet escaping/line boundary checks |
| History preimage/checkpoint/label/compare/restore/checksum/quota | history.py, Java metadata reader | HistoryPanel | Pytest + browser label/compare |
| Compile 3 engines/timeout0/Stop/cache/clean/glyph guard | compiler.py | Controls, log, F5 | 186 native builds, Stop/timeout/recovery helper |
| Error Explainer đầy đủ Java | 28 packaged rules + context/exit cases | Build log + explanations | Root-cause/context/exit unit + failing compile browser |
| Preflight/Biber/install consent/bundled-only/cancel | toolchain.py/API | Create dialog/Settings diagnostics | Consent guard/API cancellation; không cài ngầm TeX hệ thống |
| PDF thật/bytes/scroll/link/zoom/stale/Back | immutable snapshot | PdfViewer/PDF.js | 3-page browser journey + byte export/stale/error guard |
| SyncTeX 2 chiều/tệp con/Unicode/crop/rotation | synctex.py, immutable index | source→PDF, PDF double-click→source | 6 native + 12 PDF.js geometry combinations |
| Layout split/editor/PDF/detach/focus/outline divider | desktop bridge | Layout menu, draggable dividers | Browser layout/F5/outline + native window smoke; dialogs còn unit mock |
| TCP/TLS v1/v2/AUTH/hostname/limits/heartbeat/outbox | network.py | NetworkPanel | Protocol/TCP/TLS tests + actual Java server interop |
| OT/Lamport/ACK/presence/cursor/offline/RESYNC/chunk/rebase/ROOM_UNKNOWN | network.py | useParity/NetworkPanel | Two replicas/ACK/offline merge/unknown-room queue retention |
| Server/client/console/deploy/env/certs | network.py/console.py, launchers | Connect/reconnect/offline/leave | CLI/TCP native; Compose là cấu hình mẫu chưa chạy Docker |
| M5 persistence prototype | persistence.py, optional pymongo | Java chưa nối UI; giữ đúng phạm vi | Repository/service tests bằng mongomock, chưa Atlas thật |
| Windows/Mac/Linux source launcher, benchmark | scripts/run-python-*.bat/.sh | pywebview | Windows native + 248-page PDF.js; native Mac/Linux chưa kiểm |

## Giới hạn giữ đúng phạm vi

- Room vẫn đồng bộ **main.tex**; shared-token auth, OT đơn giản và queue/room trong RAM. Không tuyên bố CRDT đầy đủ, ACL theo tài khoản hoặc đồng bộ toàn project.
- Không có Git branch/merge, Zotero/Mendeley OAuth, LSP/plugin hoặc track changes. Python đã bổ sung Review local: nhận xét theo đoạn/dòng, reply/resolve/reopen, lưu riêng source; chưa đồng bộ nhận xét qua room TCP.
- Snapshot ở RAM; restart cần compile lại. Root Unicode trên Windows dùng staging ASCII; cache giữa các lần staging chưa tối ưu bằng root ASCII.
- Source launcher đã có cho Mac/Linux nhưng chưa có máy native để nghiệm thu, chưa có installer signed/notarized. Mongo M5 và Compose được nêu rõ mức kiểm chứng.
- Việc chuyển code không đồng nghĩa chứng minh mọi edge case không còn lỗi. Bằng chứng kiểm thử và đường dẫn report được ghi trong tài liệu migration; không dùng số liệu Java để thay kết quả Python.


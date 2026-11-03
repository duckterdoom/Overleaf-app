# Đánh giá app desktop so với Overleaf web

> **Cập nhật tiêu chí 4 (02/11/2026):** đã bổ sung SyncTeX hai chiều và tái sử dụng PDF renderer; benchmark local 248 trang đạt. 148 JUnit chọn lọc đạt. Xem [cách dùng và số liệu PDF](PDF_PERFORMANCE.md); kết quả các lượt review trước bên dưới có ghi phạm vi riêng.

Ngày kiểm tra: **02/11/2026**, nhánh **Network**, HEAD trước cập nhật tài liệu: **2310f6c**. Báo cáo này thay thế đánh giá cũ ngày 01/11; trạng thái Canvas, Error Explainer chưa nối UI và chưa có history trong bản cũ không còn đúng.

**Kết luận:** app có lợi thế thực tế về chủ động compile local, làm việc với dữ liệu trên máy, giải thích lỗi tiếng Việt bằng rule offline và history local không có paywall trong code. PDF viewer, toolbar, gallery và layout đã tiến bộ rõ so với bản app trước. Chưa có bằng chứng app nhanh hơn hoặc tốt hơn Overleaf trên mọi mặt; cộng tác, SyncTeX và hệ sinh thái editor còn thiếu.

## 1. Đã kiểm thử gì

Không sửa runtime, không sửa dự án/tài khoản thật. Toàn bộ smoke mới dùng home riêng dưới target/. Không đăng nhập hay upload tài liệu lên Overleaf để đo đối chứng. Không tắt card mạng/firewall của máy người dùng.

| Kiểm chứng | Kết quả và bằng chứng local |
| --- | --- |
| JUnit chọn lọc chạy lại | **143/143 đạt**, common 9 + server 21 + client 113; 0 failure/error/skip. target/template-library-tests.log |
| Compile native chạy lại | **15 build đạt**: 3 engine × lần đầu/không sửa/sửa nhỏ/TikZ/template. target/comparison-20261102-compile.log và comparison-20261102-compile/results.md |
| History/Auto Compile/layout chạy lại | **Đạt**: debounce, manual, label/restore có backup, lưu lại preference, split/editor/PDF/focus/detach. target/comparison-20261102-history.log |
| PDF liên tục/link chạy lại | **Đạt**, PDF 13 trang; scroll đổi trang, kiểm pixel render, link từ trang 2 tới trang 5, Back và giới hạn giữ ảnh trang trong bộ nhớ. target/comparison-20261102-pdf.log |
| Preview/export chạy lại | **Đạt**, PDF 7 trang: pixel khớp renderer của cùng PDF, export cùng bytes, stale/error chặn export, recovery và cách ly dự án. target/comparison-20261102-export.log |
| Toolbar chạy lại | **Đạt**: đúng thứ tự icon, math/link/comment/ref/citation/figure/table/list/indent; read-only/stale guard, snippet compile PDF thật. target/comparison-20261102-editor.log |
| Gallery/tạo dự án chạy lại | **Đạt**: Việt/Anh, tìm luận văn/thesis, category/tag, tạo nhiều file/default engine, custom folder/storage/move và consent installer mock. target/comparison-20261102-gallery.log |
| Ma trận template đã chạy trong phiên triển khai cùng ngày | **174/174 đạt**, 29 mẫu × 2 ngôn ngữ × 3 engine. Không chạy lại toàn bộ ở lượt review này. [Ma trận](TEMPLATE_VALIDATION.md), target/template-import-smoke.log |
| Import đã chạy trong phiên triển khai cùng ngày | Example, Word, Markdown và GitHub public đạt; không chạy lại ở lượt review này. target/template-import-smoke.log |

143 là bộ chọn lọc, không phải toàn bộ test repository: không chạy integration MongoDB. Các bài giao thức dùng socket loopback; Maven offline không đồng nghĩa đã ngắt internet. TLS có test handshake, token và hostname trong NetworkClientTest; không phải penetration test.

Smoke UI điều khiển control/event trong JavaFX với stage ẩn, không phải khảo sát khả năng sử dụng hoặc kiểm thử mobile. Cài package thiếu dùng mock; không tải/cài package thật trong lượt review này.

## 2. Đối chiếu đủ 8 tiêu chí

| Tiêu chí | Bản app hiện tại và bằng chứng | Đánh giá so với Overleaf web |
| --- | --- | --- |
| **1. Compile** | latexmk local; pdfLaTeX/XeLaTeX/LuaLaTeX; cấu hình executable/timeout, Stop, Auto Compile, cache, Error Explainer tích hợp. Mặc định 120 giây; 0 là không giới hạn theo cấu hình. Ba engine, TikZ và cache đã chạy native. | **Có lợi thế chủ động môi trường và timeout**, không phải chờ dịch vụ compile cloud. Không chứng minh nhanh hơn; không khẳng định tận dụng hết CPU. Chưa Typst hoặc điều khiển shell-escape/công cụ ngoài đầy đủ trong UI. |
| **2. Internet** | LocalBackend đọc/ghi tài khoản và dự án trên máy, toolchain/PDF renderer local. Tests tạo/lưu/mở lại đạt; UI không cần đăng nhập cloud. Offline RESYNC có test. | **Có lợi thế làm việc local khi mất internet**, nếu Java/dependency/TeX/package đã chuẩn bị. Lần đầu bootstrap/cài package/import URL/GitHub vẫn cần mạng. Chưa test bằng cách ngắt internet toàn máy; chưa tự reconnect bền vững; pending queue vẫn RAM. |
| **3. Editor** | Highlight, outline có cuộn/divider, theme/font/wrap/autosave, toolbar chèn LaTeX với ref/citation/hình/bảng/list, tìm trong file, layout linh hoạt. Toolbar có UI smoke + compile thật. | **Cải thiện trải nghiệm của app**, có ích cho người dùng Việt. Chưa đủ nói editor vượt web: chưa texlab/LSP, plugin API, multi-cursor, Vim/Emacs, refactoring hoặc tìm/thay thế toàn dự án. Add comment hiện toggle dấu % trong source, chưa phải thread thảo luận/review. |
| **4. Hiệu năng/PDF** | PDFBox render PDF thật, scroll/link/zoom; **SyncTeX hai chiều** tới file chương/dòng nguồn; renderer dùng lại document. Benchmark **248 trang**, render p50 **12 ms** thay vì **26 ms** khi mở lại document từng trang; điều hướng p95 **68 ms** trong 18 lượt. Export cùng bytes và stale/error guard có hồi quy. | **Đã có SyncTeX và benchmark local**, xác nhận cải thiện render trong workload đã đo. Không giữ raster toàn tài liệu; giữ tối đa 3 ảnh ở các điểm đo. Chưa đối chứng Overleaf web, RSS/native/GPU hoặc benchmark macOS/Linux, nên chưa khẳng định nhanh hơn web. [Bằng chứng và giới hạn](PDF_PERFORMANCE.md). |
| **5. Giá và giới hạn** | Không thấy paywall/subscription cho history/template/compile trong code đang chạy. History local có label/restore. | **Có lợi ích chi phí phần mềm tiềm năng khi tự vận hành**, đặc biệt so với history đầy đủ của gói Free web. Không đồng nghĩa miễn mọi chi phí hay đã đủ thay gói Premium: máy/VPS/backup/bảo trì vẫn tốn. Không kết luận pháp lý về license toàn sản phẩm từ việc không có paywall. |
| **6. Riêng tư/dữ liệu** | Dự án mặc định ~/OverleafDesktop, đổi storage/thư mục được. Password local PBKDF2 có salt; server riêng TCP/TLS và hostname verification có code/test. Error Explainer dùng rule local. | **Có lợi thế kiểm soát nơi lưu và chọn server**; compile/explainer không cần gửi nội dung tới cloud trong luồng local. File và history chưa mã hóa trên ổ đĩa; TLS bảo vệ đường truyền khi bật. Shared token chưa phải ACL danh tính/project đầy đủ. Chưa security audit, không khẳng định an toàn hơn cloud tuyệt đối. |
| **7. Phiên bản/cộng tác** | History local checkpoint/label/restore có backup đã chạy UI; transform operation, ACK, Lamport, presence/cursor, RESYNC/chống replay có test. | **History là bước tiến thật và dùng local không cần gói trả phí**. Cộng tác chưa chứng minh tốt hơn web: chỉ main.tex, pending/room/history đồng bộ còn RAM, chưa branch/merge, track changes, review thread hoặc khôi phục trạng thái server sau restart. |
| **8. Template/bibliography/mobile** | **29 mẫu, 9 nhóm, Việt/Anh**, metadata/resource, nhiều file/chương/.bib/PNG/CSV; search/category/tag; preflight package/Biber, cài bundled TinyTeX sau xác nhận. Hỗ trợ nhập .bib và trích dẫn từ key. | **Có lợi thế tiện dụng cho người Việt và mẫu có sẵn offline**, đã xác nhận compile qua 174 lượt. Không nói phong phú hơn gallery Overleaf. Nhập .bib từ Zotero/Mendeley/ReadCube chưa phải đồng bộ tài khoản. Chưa visual editor hoàn chỉnh hoặc mobile; không có UX study màn hình nhỏ. |

## 3. Số đo compile trên máy này

Windows, Microsoft JDK 21.0.13, TinyTeX portable hiện có. Nguồn article nhỏ có công thức và tham chiếu; mỗi tình huống **một lượt/engine**, không lấy trung vị hoặc percentile. Không đo Overleaf trên cùng nguồn. Lúc đo có thể có tác vụ test khác, nên dùng số này làm bằng chứng cache hoạt động, không làm benchmark sản phẩm.

| Engine | Lần đầu (ms) | Không sửa (ms) | Sửa nhỏ (ms) |
| --- | ---: | ---: | ---: |
| pdfLaTeX | 526 | 199 | 403 |
| XeLaTeX | 915 | 239 | 461 |
| LuaLaTeX | 2340 | 277 | 1075 |

Lượt không sửa được assert có log latexmk “Nothing to do” hoặc “up-to-date”; không suy luận cache chỉ từ việc thời gian giảm. Mỗi engine còn qua một build TikZ và một build template. Kiểm PDF header ở smoke này không đủ xác nhận mọi glyph; tiếng Việt của 29 mẫu đã có kiểm trích text ở ma trận riêng.

Không dùng số đo này để tuyên bố app nhanh hơn web, compile vô hạn mặc định, tối ưu toàn bộ CPU, hay bảo đảm mọi tài liệu LaTeX bất kỳ.

## 4. Các giả định về Overleaf cần điều chỉnh

Thông tin dưới đây đã mở tài liệu chính thức khi review, ngày 02/11/2026; giá/gói có thể đổi sau đó.

- **Timeout công bố là 10 giây Free / 240 giây Premium**, không phải khoảng 20 giây. [Plan limits](https://docs.overleaf.com/getting-started/free-and-premium-plans/plan-limits).
- Free cho **1 cộng tác viên được mời/project** và có basic AI allowance. Không nên nói toàn bộ AI đều bị khóa trả phí. Không so giá tiền tuyệt đối vì còn chu kỳ thanh toán, loại plan và hỗ trợ tổ chức. [Plans](https://www.overleaf.com/plans).
- Overleaf **có chọn compiler và TeX Live version**. Lợi thế local là chủ động cài môi trường/công cụ theo máy, không phải web hoàn toàn không chọn được phiên bản. [Compiler settings](https://docs.overleaf.com/getting-started/recompiling-your-project/selecting-a-tex-live-version-and-latex-compiler).
- Overleaf **có cache auxiliary file** và Recompile from scratch. Không đúng khi nói lần nào cũng compile từ đầu. [Cache](https://docs.overleaf.com/troubleshooting-and-support/clearing-the-project-cache).
- Web có **Vim/Emacs emulation**, search toàn dự án và SyncTeX hai chiều. Những phần này không phải lợi thế đã có của app. [Keybindings](https://docs.overleaf.com/navigating-in-the-editor/vim-and-emacs-keybinding), [Search](https://docs.overleaf.com/navigating-in-the-editor/searching-within-a-project), [SyncTeX](https://docs.overleaf.com/navigating-in-the-editor/working-with-the-pdf-viewer/moving-between-the-editor-and-pdf).
- Free history hiển thị **24 giờ gần nhất và bản đã gắn nhãn**; full history là premium. App history khác về nơi lưu/chi phí tính năng, chưa tương đương version control đầy đủ của dịch vụ. [History](https://docs.overleaf.com/writing-and-editing/history-and-versioning).
- Overleaf **có Error Assist** giải thích/gợi ý sửa, dùng shared AI allowance. Điểm khác của app là rule tiếng Việt chạy local, không phải khả năng hiểu mọi lỗi hơn AI. [Error Assist](https://docs.overleaf.com/integrations-and-add-ons/ai-features/error-assist).
- Git integration là premium và cho phép chỉnh sửa local rồi push/pull. Không nên nói không có bất kỳ cách nào làm offline với dự án Overleaf; editor web và quy trình editor local + sync khác nhau. App hiện chỉ import GitHub, chưa là Git client. [Git integration](https://docs.overleaf.com/integrations-and-add-ons/git-integration-and-github-synchronization/git-integration).

## 5. Giới hạn kỹ thuật đáng chú ý của app

- **History local:** text tối đa 2 MiB/bản, 1.024 revision hoặc tổng 128 MiB/project; code báo lỗi khi đạt giới hạn, không có nghĩa lưu lịch sử vô hạn. Đây không phải server audit history.
- **Cộng tác:** main.tex; ServerConfig mặc định 64 kết nối toàn server, tài liệu tối đa 100.000 ký tự (cấu hình đến 200.000), không phải 64 người đã stress-test hoặc giới hạn cộng tác vượt web. Có vai trò trong engine, nhưng transport sử dụng shared token và chưa gắn quyền project với tài khoản local đầy đủ.
- **Độ bền:** lưu nội dung/history local trên đĩa khác với lưu queue offline. Queue pending trong OfflineOperationQueue là LinkedHashMap RAM; DocumentSyncManager/OperationHistory cũng RAM. Chưa bảo đảm replay sau kill/restart hoặc server restart.
- **Compile root:** workspace hiện main.tex, không phải chọn tùy ý main document như web; TinyTeX/package phải có trước để compile offline. UI preflight áp dụng TEMPLATE; không phải mọi tài liệu import được tự sửa/cài dependency.
- **Tương thích:** kiểm chứng Windows hiện tại; chưa chạy native trên macOS/Linux, máy sạch không mạng hoặc stress tài liệu rất lớn. Không chứng minh mọi PDF bất kỳ đúng nội dung chỉ bằng equality với output compiler.

Các điểm code đối chiếu: modules/client/.../compiler/LatexCompilerService.java, ui/OverleafFxApp.java, ui/PdfPreviewPane.java, compiler/PdfSnapshot.java, workspace/ProjectHistory.java, ui/backend/LocalBackend.java, ProjectTemplates.java, TemplatePrerequisites.java; modules/common/.../sync/OfflineOperationQueue.java; modules/server/.../collab/DocumentSyncManager.java và net/ServerConfig.java.

## 6. Thứ tự cải thiện tiếp theo

1. **Chọn compile root** và mở rộng kiểm chứng SyncTeX cho tài liệu đặc thù; SyncTeX hai chiều đã triển khai, giữ kiểm export stale/error hiện có.
2. **Queue offline bền vững + reconnect có backoff + server persistence**, rồi test kill/restart/ACK thất lạc và sửa/xóa chồng nhau.
3. **Đồng bộ nhiều file và ACL theo project/tài khoản**, trước khi công bố dùng cho nhóm nghiên cứu thực tế.
4. **Tìm/thay thế toàn dự án và texlab**, sau đó Git commit/diff/branch/merge nếu nhu cầu thật sự cần.
5. **Benchmark có phương pháp**: cùng nguồn và TeX version trên app/web, lặp nhiều lần, cold/warm/edit, sách có hình/TikZ, p50/p95 latency, CPU/RAM; đánh giá màn hình nhỏ riêng.

Câu có thể dùng khi thuyết trình: “Ứng dụng ưu tiên dữ liệu và toolchain local, hỗ trợ ba engine, preview/export cùng PDF đã kiểm chứng, giải thích lỗi tiếng Việt offline, history local và 29 mẫu song ngữ. Các kiểm thử xác nhận các kịch bản đã thiết kế; chưa chứng minh hiệu năng hoặc cộng tác vượt Overleaf web.”

# Đánh giá app desktop so với Overleaf web

> **Cập nhật preview/export:** đã thay Canvas bằng PDF viewer thật, Export dùng đúng bytes của PDF được preview, có kiểm tra stale/error; 81 test và smoke tài liệu 7 trang đạt. Xem [AI_Handoff.md §19](AI_Handoff.md#19-preview-đúng-pdf-và-export-cùng-kết-quả-compiler). Chưa có SyncTeX, chưa benchmark tốc độ với Overleaf.

> **Cập nhật sau đánh giá:** tiêu chí Compile đã được cải thiện: cấu hình toolchain/timeout, Stop, tích hợp Error Explainer; 72 test đạt và đã kiểm chứng PDF thật qua ba engine, cache, TikZ và template tiếng Việt. Xem [AI_Handoff.md §17](AI_Handoff.md#17-cải-thiện-tiêu-chí-1--compile-local-và-kiểm-chứng-pdf-thật). Các bảng/phát hiện bên dưới ghi trạng thái **trước** lần cải thiện này.

Ngày đối chiếu: **01/11/2026**. Nhánh `feat/m1-gui-client`, HEAD `b89dbc5`, kèm thay đổi local hiện có. Đây là đánh giá mã nguồn và kiểm thử cục bộ; không phải benchmark hai sản phẩm trên cùng tài liệu.

## 1. Kết quả kiểm thử thực tế

Chạy Maven thật bằng JDK Microsoft 21.0.13, chế độ offline Maven dùng dependency đã cache. Không gọi test MongoDB, không sửa tài khoản/dự án người dùng. Offline Maven chỉ có nghĩa không tải dependency; các bài networking vẫn dùng socket loopback.

| Test class | Đạt |
| --- | ---: |
| MessageCodecTest | 6 |
| ResyncTransferTest | 3 |
| ConnectionScopeTest | 1 |
| NetworkingTest | 10 |
| NetworkClientTest | 3 |
| M4CollaborationTest | 6 |
| OfflineResyncTest | 5 |
| ProjectFileManagerTest | 8 |
| LocalBackendTest | 13 |
| **Tổng** | **55** |

Kết quả: **55/55 đạt, 0 lỗi, 0 bỏ qua**. Log local: `target/review-desktop-capabilities.log`; lệnh chạy lại trên máy hiện tại: `target\review-desktop-capabilities.cmd`. Helper/cache/log trong `target/` bị Git ignore.

`LatexErrorExplainerTest` cũng được đưa vào bộ chọn nhưng **không được JUnit Platform phát hiện**, do dùng annotation TestNG. Vì vậy không được cộng 8 test của lớp này vào tổng 55. Trong lần review trước, gọi trực tiếp 8 phương thức qua helper thì 8/8 đạt, nhưng đó không phải kết quả Maven phát hiện test tự động.

README đang ghi 52 test, đã cũ so với 55 test phát hiện ở nhánh này. Tên test `templateCompilesVietnameseWithEveryEngine` dễ gây hiểu nhầm: chỉ assert chuỗi template chứa package, không thực sự chạy ba compiler.

Không tìm thấy `latexmk`, `pdflatex`, `xelatex`, `lualatex` trên PATH của tiến trình kiểm tra. Chưa xác nhận có cài ở nơi khác. Chưa compile PDF/TikZ thật, chưa đo incremental build, CPU/RAM, độ trễ mạng hay thao tác UI ở lần kiểm tra này. Không đăng nhập/chạy tài liệu đối chứng trên Overleaf web.

## 2. Đối chiếu 8 tiêu chí

| Tiêu chí | Code thực tế của nhóm | Kết luận |
| --- | --- | --- |
| 1. Compile | `LatexCompilerService` gọi `latexmk` local, chọn pdfLaTeX/XeLaTeX/LuaLaTeX, thu log nền, gộp yêu cầu chờ, giữ output trong `build/`. Timeout mặc định **120 giây**, có setter nhưng chưa có điều khiển timeout trong UI. | **Có lợi thế về quyền chủ động môi trường compile**, không phụ thuộc hàng đợi dịch vụ cloud. Chưa chứng minh nhanh hơn. Không phải compile vô hạn; mặc định ngắn hơn mức 240 giây của Overleaf Premium. Chưa có Typst, lựa chọn đường dẫn nhiều bản TeX, hoặc điều khiển shell-escape trong UI. Error Explainer chưa nối UI. |
| 2. Internet | `LocalBackend` đăng ký/đăng nhập và quản lý dự án bằng file trên máy; `ProjectFileManager` đọc/ghi thật. Offline queue và RESYNC có test qua socket thật, kể cả replay không nhân đôi. | **Đã có nền tảng làm việc local/offline rõ ràng**. Compile offline cần toolchain/package có sẵn. Reconnect hiện do người dùng chọn; chưa tự nối lại, queue còn RAM, chưa có Git integration trong sản phẩm. |
| 3. Editor | Theme/font/cỡ chữ/wrap/tự đóng ngoặc/autosave, syntax highlight, gutter số dòng, outline và tìm trong file đang mở. | Có tùy biến hữu ích nhưng **chưa chứng minh vượt Overleaf**. Chưa có LSP texlab, plugin API, multi-cursor, Vim/Emacs hoặc tìm/thay thế toàn dự án. Git quản lý source dự án này không đồng nghĩa có Git trong app. |
| 4. Hiệu năng/PDF | `PdfCanvasPane` tự parse một phần LaTeX rồi vẽ Canvas; PDF compile thật mở bằng ứng dụng ngoài. | **Chưa đạt lợi thế preview PDF**. Canvas có thể vẽ ngay cả khi compile lỗi và không đảm bảo giống PDF. Chưa có PDF renderer thật, SyncTeX hai chiều hoặc benchmark dự án lớn. |
| 5. Giá/giới hạn | Không có paywall/subscription trong code hiện tại. Server hỗ trợ nhiều kết nối, cấu hình mặc định 64 client toàn server; đó không phải số người đã stress-test hay cam kết vận hành. | **Có lợi thế tiềm năng về chi phí cho nhóm tự vận hành**, so với giới hạn cộng tác của gói Free. Vẫn có chi phí máy, điện, VPS và bảo trì; chưa có đủ tính năng thay thế gói trả phí như track changes/lịch sử/reference manager. |
| 6. Riêng tư/dữ liệu | Dự án mặc định ở `~/OverleafDesktop`; tài khoản local dùng PBKDF2 có salt. Có TLS, truststore, kiểm tra hostname và server riêng. | **Đã có lợi thế kiểm soát nơi lưu và đường đi dữ liệu** so với dịch vụ cloud. TLS chỉ bảo vệ đường truyền; nội dung file chưa mã hóa lúc lưu. Shared token chưa phải quyền truy cập từng tài khoản/project; server chưa lưu bền vững. |
| 7. Phiên bản/cộng tác | Engine transform operation, ACK, Lamport, presence/cursor, RESYNC, chống replay; các kịch bản đồng thời/offline đã có test. | Cộng tác có hoạt động nhưng **chưa đủ cơ sở nói tốt hơn Overleaf**. Chưa có branch/merge, comment/review/track changes, lịch sử có thể phục hồi qua UI; chỉ đồng bộ `main.tex`, room/history ở RAM. Class mang tên CRDT không phải bằng chứng hội tụ cho mọi kiểu edit. |
| 8. Template/bibliography/mobile | Tạo dự án từ template dựng sẵn, quản lý file local, template có cấu hình tiếng Việt. | **Chưa chứng minh cải thiện**. Chưa có thư viện template phong phú, quản lý citation/Zotero/Mendeley, visual editor đầy đủ hoặc bản mobile. |

## 3. Những nhận định về Overleaf cần sửa

Các nguồn dưới đây là tài liệu chính thức đã truy cập khi đánh giá, không dựa vào giả định trong đề bài:

- Timeout hiện được công bố là **10 giây Free / 240 giây Premium**, không phải khoảng 20 giây. Giới hạn và tính năng xem [Plan limits](https://docs.overleaf.com/getting-started/free-and-premium-plans/plan-limits).
- Gói Free cho **1 cộng tác viên được mời**, ngoài chủ dự án. Một số công cụ AI hiện có allowance cơ bản trên Free; không nên nói tất cả AI đều trả phí. Xem [Plans](https://www.overleaf.com/plans).
- Overleaf có chọn TeX Live version/compiler, đổi theme và font. Xem [Redesigned editor](https://docs.overleaf.com/getting-started/how-do-i-use-overleaf/redesigned-overleaf-editor).
- Overleaf có cache auxiliary file và lựa chọn xóa cache/recompile from scratch; không đúng nếu nói luôn compile lại hoàn toàn và không cache. Xem [Clearing cache](https://docs.overleaf.com/troubleshooting-and-support/clearing-the-project-cache).
- Overleaf có [tìm kiếm toàn dự án và thay thế trong file](https://docs.overleaf.com/navigating-in-the-editor/searching-within-a-project), [Vim/Emacs emulation](https://docs.overleaf.com/navigating-in-the-editor/vim-and-emacs-keybinding), [SyncTeX hai chiều](https://docs.overleaf.com/navigating-in-the-editor/working-with-the-pdf-viewer/moving-between-the-editor-and-pdf).
- Có thể làm offline bằng editor/toolchain local và các tích hợp đồng bộ; trải nghiệm này khác với mở editor web khi mất mạng. [Git integration](https://docs.overleaf.com/integrations-and-add-ons/git-integration-and-github-synchronization/git-integration) là tính năng premium.
- Overleaf có [Error Assist](https://docs.overleaf.com/integrations-and-add-ons/ai-features/error-assist), nên bộ giải thích lỗi của nhóm không phải ý tưởng Overleaf chưa có. Cơ hội khác biệt là giải thích tiếng Việt, offline, không gửi code ra dịch vụ AI; hiện logic local của nhóm chưa nối vào UI.

## 4. Điều có thể khẳng định khi trình bày đồ án

> Dự án ưu tiên dữ liệu trên máy người dùng, chỉnh sửa và quản lý file local, có khả năng gọi toolchain LaTeX local và cộng tác qua server TCP/TLS riêng. Các ca kiểm thử hiện có xác nhận giao thức, thao tác file và những tình huống đồng bộ offline đã thiết kế. Chưa có phép đo chứng minh nhanh hơn Overleaf hoặc tương đương toàn bộ tính năng của Overleaf.

Không dùng các tuyên bố: “compile không timeout”, “tận dụng hết CPU”, “preview PDF chính xác”, “CRDT không xung đột”, “64 người đã được kiểm chứng”, “dữ liệu đã mã hóa trên ổ đĩa” hoặc “55 test chứng minh vượt Overleaf”.

## 5. Thứ tự cải thiện đề xuất

1. Nối Error Explainer vào Build output, chuyển test sang JUnit 5, sửa rule nhận diện file thiếu quá rộng. Đây là thay đổi nhỏ, dễ demo lợi ích tiếng Việt/offline.
2. Cài/định vị toolchain rồi đo PDF thật: lần đầu/lần hai/sửa nhỏ, ba engine, tài liệu có TikZ và nhiều ảnh. Cho cấu hình timeout ở UI.
3. Render PDF thật trong app, sau đó thêm SyncTeX; đây là khoảng thiếu quan trọng nhất của trải nghiệm soạn LaTeX hiện tại.
4. Lưu offline queue xuống đĩa, tự reconnect, lưu document/history trên server và xác thực/quyền project. Kiểm tra lại sau restart, mất mạng, ACK thất lạc và edit/xóa chồng nhau.
5. Thêm tìm/thay thế toàn dự án, Git và texlab theo nhu cầu. Chỉ công bố lợi thế hiệu năng khi có dataset, máy đo và số đo so sánh cụ thể.

## 6. Kịch bản nghiệm thu tiếp theo

| Kịch bản | Điều kiện đạt | Trạng thái lần này |
| --- | --- | --- |
| Tắt internet, mở app, đăng nhập local, tạo/mở/lưu lại dự án | Nội dung còn sau đóng/mở app, không cần cloud | Đã test lớp backend/file; chưa thao tác UI với mạng tắt |
| Compile ba engine, không internet, package có sẵn | Sinh đúng PDF, ghi thời gian/CPU/RAM | Chưa chạy vì không tìm thấy toolchain trên PATH |
| Compile hai lần và sửa một dòng | Đọc log latexmk để xác nhận reuse; đo bằng cùng tài liệu | Chưa đo |
| Hai client; một offline sửa, client kia online sửa; reconnect | Nội dung khớp, không mất/nhân đôi thao tác trong kịch bản | Test engine/socket hiện có đạt |
| Kill client khi có pending rồi mở lại | Pending phục hồi từ đĩa | Chưa có persistence queue |
| Restart server rồi reconnect | Nội dung/history tồn tại | Chưa có persistence đường đồng bộ |
| Ba client cùng phòng, dự án lớn, mạng trễ/mất gói | Đo p50/p95 và kiểm tra hội tụ, không mất chữ | Chưa benchmark thực tế |
| So PDF app với output của toolchain | Trang/công thức/hình/font giống PDF thật | Preview Canvas hiện không đủ để đạt |

Không sửa runtime theo các đề xuất trong báo cáo này.

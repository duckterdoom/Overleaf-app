# IELTS Writing trong web Latex

**Phiếu kết quả trong cây tệp:** khi Tutor công bố điểm, hệ thống tạo `result/<ten-hoc-vien>-task<N>-<dang-bai>-<ma-ban-nop>-v<phien-ban>.tex`. Mỗi Task là một tài liệu liên tục: tên học viên → đề → bài làm → band/lời phê theo tiêu chí/nhận xét theo đoạn → tổng kết. Tài liệu dài phân trang tự nhiên, không ép co toàn bộ bài vào một trang giấy. PDF/ảnh đề được chép vào `result/assets/` để compile lại đúng bản đã giao. Không tạo dự án mới. Công bố lại phiên bản mới tạo tên phiên bản mới; không ghi đè file người dùng đã sửa.

`result/` là tệp chung của dự án: thành viên có quyền đọc dự án có thể đọc phiếu đã công bố. Nháp viết/nháp chấm vẫn giữ riêng trong module. Các bài đã công bố trước cập nhật chưa tự tạo file; Tutor công bố lại cùng phiên bản để tạo phiếu, không thay điểm.

**Luyện cá nhân từ editor:** Học viên vào Bài được giao → Bài luyện cá nhân, chọn Task/dạng bài và lấy tệp LaTeX/PDF/ảnh/TXT/Markdown trong dự án. Chọn phạm vi trang nếu cần, bấm Dùng tệp làm đề rồi Bắt đầu viết. Không nhập lại đề hoặc upload hình ngoài form này. Student chỉ được gắn attachment mình vừa lấy cho bài luyện của chính mình, không giao bài lớp hay sửa bài của Tutor. Màn làm bài giống bài được giao: đề lớn trái, câu trả lời phải, không toolbar chuyển source/PDF.

**Lưu trong dự án và chấm tập trung:** bản nháp, bài nộp và kết quả IELTS gắn projectId. Xuất PDF trong dự án lưu đầu ra vào `writing_artifacts`, dùng workspace tạm để compile rồi dọn, không thêm dự án IELTS vào danh sách. Quyền đọc bài vẫn theo assignment/submission, không ghi bài riêng của học viên vào tệp editor dùng chung. Bản PDF đã tạo có cache và được tải xuống; các dự án trùng sinh từ phiên bản cũ không bị tự xóa. Luồng legacy không có projectId vẫn giữ tương thích.

Người được chia sẻ mở lại dự án từ **Được chia sẻ với bạn** khi chủ sở hữu đăng xuất/đóng trình duyệt, không cần chủ dự án online. Backend/server và MongoDB vẫn phải hoạt động; nếu máy chủ local bị tắt thì không ai truy cập được. Quyền chia sẻ bị thu hồi vẫn chặn truy cập.

Màn Tutor chấm bỏ tiêu đề trang lớn và toolbar Văn bản/LaTeX/PDF, thu gọn thông tin task, mở rộng vùng bài/checklist. Đề PDF mặc định thu trong **Xem đề bài Task…**, mở khi cần đối chiếu.

**Làm bài tập trung và cấu hình dự án (12/11/2026):** màn làm bài dùng hai cột ngang nhau, đề lớn bên trái, câu trả lời bên phải. Bỏ thanh Văn bản/LaTeX/PDF/Mở editor trong màn viết; các chức năng xuất/đọc source khi chấm vẫn giữ. Chế độ đọc thu gọn lề trắng trên ảnh hiển thị để chữ/hình lớn hơn; không sửa PDF gốc hay bỏ nội dung. Bản đề có nhiều trang cuộn trong cột trái, không có nút chuyển trang/zoom PDF; không bỏ trang đề đã chọn. Màn kiểm tra trước khi nộp cũng hiển thị bản đề không có toolbar.

Tutor có quyền chỉnh sửa mở **Tiêu chí IELTS → Tiêu chí chấm của dự án** để thêm/sửa/xóa mục checklist từng phần của mỗi dạng bài và lưu. Cấu hình lưu MongoDB `writing_project_settings` theo projectId, có revision chống ghi đè. Không thuộc userId; đổi vai trò hay đổi Tutor không xóa cấu hình. Bốn tiêu chí band và trọng số IELTS giữ nguyên. Bài tập mới/cập nhật chốt `rubricCatalog`, bản nộp/phiếu chấm dùng chính bản đó; sửa cấu hình không đổi cách đọc checklist đã chấm cũ. Cấu hình thời lượng/hạn nộp vẫn thuộc bài tập trong dự án, không chuyển thành tùy chọn tài khoản.

**Quản lý bài tập (12/11/2026):** form Tutor lấy đề từ tệp editor cho mỗi Task, không phải nhập lại đề hoặc upload lại hình. Danh sách đề có **Xem đề và học viên**, **Sửa bài tập**, **Xóa bài tập** và **Xem bản nộp**. Chi tiết hiển thị PDF/hình đề và trạng thái nộp từng học viên trong lớp. Sửa đề đã giao chỉ cho phép khi chưa có người bắt đầu làm; sau đó tạo bài mới để tránh đổi câu hỏi/đồng hồ của bài đang làm. Xóa mềm khỏi danh sách vẫn giữ bản nộp và kết quả. Bài cũ thiếu tệp đề cần Tutor chọn lại đúng tệp; ứng dụng không tự đoán tệp từ tiêu đề bài.

Chọn **Dự án mới → Đề mẫu IELTS Writing** để tạo bộ đề có sẵn. Xem [24 template cho 21 dạng bài](IELTS_PROJECT_TEMPLATES.md), tệp đề LaTeX riêng và cách Tutor giao đề từ editor.

Tích hợp ngày 11/11/2026, sử dụng session và vai trò thật của tài khoản. Không đổi role bằng nút demo; không đưa tài khoản, bài nộp hoặc điểm mẫu vào database khi khởi động.

## Chạy và mở

```powershell
.\scripts\run-python-web.bat --build
.\scripts\run-python-web.bat
```

Nếu server đang chạy, lưu bài rồi dừng bằng Ctrl+C trước khi chạy lại để nạp bản mới. Mở địa chỉ launcher báo, mở một dự án và nhấn icon mũ tốt nghiệp ở rail trái. IELTS là module nội dung trong workspace, dùng nguyên header dự án (tên, Lịch sử, Chia sẻ, Bố cục, tài khoản), không có nút chữ IELTS hay cặp tab Editor/IELTS trên header. Cả bốn icon Tệp/Nhận xét/Chat/IELTS được giữ nguyên. Bấm Tệp, Nhận xét hoặc Chat để quay về chức năng tương ứng; bài IELTS/bản chấm phải lưu thành công trước khi chuyển. Không đổi hoặc tạo dự án khi chỉ mở module; chỉ thao tác mở bài/phiếu LaTeX trong editor mới tạo dự án riêng theo chức năng vốn có.

## Luồng Tutor và Student

### Vai trò, người tham gia và đề từ editor — cập nhật 12/11/2026

Editor LaTeX tiếp tục cùng quyền chỉnh sửa/cộng tác như trước cho Tutor và Học viên. Riêng IELTS dùng giao diện theo **vai trò trong dự án**: Tutor có lớp/giao bài/hàng đợi chấm; Học viên có bài được giao/làm bài/kết quả của mình. Không dùng role tài khoản làm vai trò mặc định trong lúc chờ API; chưa xác nhận quyền thì hiển thị trạng thái đang kiểm tra, không bật nhầm giao diện Tutor. Phản hồi HTTP cũ không được ghi đè vai trò mới.

Chủ sở hữu cũng có thể đổi vai trò của chính mình trong hộp Chia sẻ, giữ quyền Owner và không đổi role tài khoản. Khi một người có cả lời mời riêng và quyền qua link, danh sách hiện người đó một lần theo quyền hiệu lực. Đổi quyền/vai trò từng người áp dụng cho các grant đang có của người đó, tránh việc chọn Chỉ xem nhưng quyền Editor của link vẫn thắng. Chỉ owner quản lý các lựa chọn này.

IELTS dùng **cùng WebSocket presence của dự án**, không ngắt kết nối khi mở module. Avatar hiển thị tên thật, vai trò và đang ở editor hay màn IELTS/task nào, loại chính tài khoản hiện tại và gộp nhiều tab của một người. Người chỉ xem vẫn không phát presence, giống editor theo lựa chọn trước đây. Phần IELTS không gửi nội dung bản nháp riêng/selection của Học viên vào presence; caret LaTeX chỉ vẽ ở editor cho đúng file/digest. Thay đổi quyền gửi invalidation qua WebSocket để UI tải lại ngay; polling phục hồi nếu lỡ thông báo. Source OT của editor vẫn hoạt động khi người khác ở IELTS và được tải lại đúng khi quay về editor.

Khi giao bài, Tutor dùng **Lấy đề Task … từ tệp trong dự án**:

1. Soạn/lưu tệp ở editor như bình thường rồi mở module IELTS trong đúng dự án.
2. Chọn Task 1/Task 2/cả hai, lớp và dạng bài. Với từng task, chọn .tex, PDF, PNG/JPEG/WebP hoặc TXT/Markdown có sẵn trong cây tệp dự án.
3. Nếu chọn .tex, chọn đúng engine rồi bấm **Dùng tệp làm đề Task …**. Hệ thống compile bằng pipeline editor, kiểm PDF hiện tại khớp tệp nguồn và lưu bản đề. Không dùng PDF cũ hoặc PDF của file khác. Với PDF có sẵn thì dùng chính file đó.
4. Có thể chọn khoảng trang: ví dụ cùng questions.tex gồm hai trang, Task 1 lấy trang 1 và Task 2 lấy trang 2. PDF/ảnh giữ hình và bố cục; TXT/Markdown lấy văn bản UTF-8, không render Markdown thành HTML. Có thể ghi thêm chỉ dẫn hoặc tiếp tục nhập đề dạng văn bản nếu không chọn tệp.
5. Đặt **thời lượng, hạn nộp, cho phép trễ** như cũ rồi giao bài. Không cho lưu/giao khi một tệp đang được lấy hoặc lựa chọn tệp chưa hoàn tất. Chọn lại tệp/engine/khoảng trang cần lấy lại bản đề.
6. Học viên mở bài, thấy PDF/ảnh ngay cạnh câu trả lời. Chuyển task hiển thị đúng bản đề của task đó. Timer vẫn bắt đầu theo server khi mở bài và không đặt lại khi chuyển task/đổi màn/đọc lại.

Bản đề được chốt lúc chọn/giao, gồm bytes và digest trong MongoDB `writing_prompt_files`; đề/bản nộp đã giao không bị thay bằng lần sửa source sau đó. Muốn giao nội dung mới, Tutor lấy lại tệp trong một bài mới. Điều này giữ các học viên cùng đọc bản đề đã được giao. Bản nộp và phiếu LaTeX đem theo PDF/ảnh câu hỏi, không phụ thuộc tệp nguồn còn tồn tại trên máy Tutor. Tệp đề tối đa 12 MiB, PDF tối đa 100 trang và không mật khẩu; đề được kiểm ACL theo dự án và trạng thái giao bài. Học viên không tự nhập tệp làm đề cho lớp hay mở attachment của bài chưa giao qua API IELTS.

**Chia sẻ dự án (12/11/2026):** nút **Chia sẻ** dùng chung một hộp thoại cho editor và IELTS. Chủ sở hữu chọn email, quyền **Chỉ xem / Chỉnh sửa** và vai trò **Tutor / Học viên**; sau đó có thể đổi từng quyền hoặc vai trò trong danh sách thành viên. Chia sẻ bằng liên kết có vai trò mặc định cho người mới; người đã mở liên kết hiện trong danh sách để quản lý riêng. Link bị tắt/đổi hoặc quyền bị thu hồi sẽ chặn cả editor lẫn API IELTS.

Vai trò này chỉ thuộc dự án; không sửa vai trò tài khoản toàn hệ thống. Một tài khoản Student có thể được mời làm Tutor ở một dự án, và ngược lại. Tutor có quyền chỉnh sửa được giao/chấm/công bố bài của dự án (bao gồm nháp chấm của đồng Tutor). Học viên chỉ viết/nộp và xem bản nộp, kết quả của chính mình, không thấy bài người khác hay nháp chấm. Quyền Chỉ xem không cho viết/nộp/chấm, kể cả vai trò Tutor. Chuyển quyền/vai trò được cập nhật khi tải lại dữ liệu hoặc tối đa khoảng 5 giây; mọi API ghi kiểm quyền mới ngay. Nếu đang sửa mà quyền bị hạ, có thể tải bản khôi phục phần chưa lưu.

Dữ liệu IELTS mới gắn với `projectId`. Người được chia sẻ dự án tự thấy lớp và đề đã giao trong dự án, không bắt buộc nhập thêm mã lớp. Mã lớp vẫn dùng được trong cùng dự án; mã không thay thế quyền truy cập dự án. Luyện tập cá nhân của Học viên vẫn riêng tư, không xuất hiện trong hàng đợi chấm chung.

Nếu đã tạo lớp/đề bằng phiên bản IELTS cũ chưa gắn dự án, module hiển thị số lượng và nút **Đưa dữ liệu cũ vào dự án này**. Chỉ bấm sau khi mở đúng dự án và đọc xác nhận: lớp/đề thuộc bạn cùng các bản nộp liên quan sẽ dùng quyền chia sẻ của dự án đích. Cần mời học viên cũ vào dự án để tiếp tục làm bài. Nội dung bản nộp/điểm đã lưu được giữ; không tự chuyển dữ liệu cũ vào dự án đầu tiên mở, không chuyển lần nữa sang dự án khác.

1. Tutor mở dự án, vào **Chia sẻ** để mời Học viên hoặc đồng Tutor. Người nhận mở link và đăng nhập đúng email được mời.
2. Tutor vào **Lớp học**, đặt tên và tạo lớp. Học viên được chia sẻ dự án tự thấy lớp; mã tham gia vẫn có thể dùng trong cùng dự án. Tutor thấy danh sách thành viên; Student không xem danh sách cá nhân của lớp.
3. Tutor vào **Giao bài IELTS**, chọn lớp, Task 1/Task 2/cả hai, Academic hoặc General Training, dạng bài; lấy tệp đề từ editor hoặc nhập văn bản/hình, rồi đặt thời lượng và hạn nộp. Có thể lưu đề nháp hoặc giao cho cả lớp. Đề đã giao giữ nguyên để không đổi yêu cầu của bài đang làm.
4. Student vào **Bài được giao** để viết. Hai task có câu trả lời và LaTeX source riêng; nháp lưu server, có số từ, thời gian và trạng thái lưu. Student cũng có **Luyện viết** cá nhân. Nút **Dùng đề luyện mẫu** chỉ điền đề tự soạn và biểu đồ minh họa khi người dùng chủ động chọn.
5. Student kiểm tra rồi nộp. Server giữ snapshot câu hỏi/câu trả lời/nguồn tại lúc nộp. Sửa nháp sau đó không làm đổi bản Tutor đang chấm. Hết thời gian không tự nộp; bài quá hạn được đánh dấu hoặc bị chặn nộp theo lựa chọn của Tutor, nháp vẫn được giữ. Dưới số từ gợi ý vẫn có thể nộp và được cảnh báo.
6. Tutor mở **Bài nộp học viên**. Mỗi đoạn có thẻ nhận xét ở lề; bấm để mở checklist, đổi chức năng đoạn, chọn phần chữ làm minh chứng và ghi góp ý. Có đánh giá toàn bài. Checklist hỗ trợ quan sát, không tự quy đổi thành band.
7. **Tổng kết band** mở khung bốn tiêu chí TA/TR, CC, LR, GRA; chọn điểm và nhập nhận xét. Student không thấy nháp chấm. Tutor phải chấm đủ mọi task trước khi công bố. Điều chỉnh kết quả đã công bố cần lý do; Student chỉ đọc bản công bố. Task 2 có trọng số gấp đôi khi có đủ hai task, tính trên trung bình chưa làm tròn. Band hiển thị là ước tính luyện tập, không phải kết quả thi chính thức.

Catalog gồm 8 dạng Task 1 Academic, 3 mức trang trọng thư General Training và 10 dạng Task 2; có Introduction/Overview/Body/Conclusion/Closing phù hợp từng loại. Tutor có thể gán chức năng lại cho đoạn thay vì ép tất cả bài có cấu trúc giống nhau.

## Kết hợp LaTeX

Vùng làm bài/chấm có **Văn bản / LaTeX source / PDF preview**. Có thể tạo nguồn LaTeX từ câu trả lời, chỉnh nguồn và compile PDF thật bằng bộ TeX hiện có. Nguồn tùy chỉnh là nhánh trình bày riêng: không có đồng bộ hai chiều tùy ý giữa câu trả lời IELTS và mọi lệnh LaTeX. UI hiển thị rõ việc tạo lại nguồn và trạng thái PDF khi nội dung thay đổi.

Tutor đọc bản nộp chuẩn từ snapshot. PDF của bản nộp được dựng từ câu trả lời đã nộp; không chạy tự động mã LaTeX tùy ý của Student trong tài khoản Tutor. **Mở trong editor LaTeX** tạo dự án thật trong workspace tài khoản hiện tại. Phiếu phản hồi lấy kết quả đã công bố, gồm band, nhận xét, minh chứng và checklist đã ghi nhận. Câu trả lời được escape trước khi đưa vào LaTeX; hình đề được đưa vào dự án tạo ra. PDF cần bộ TeX/latexmk hoạt động trên backend.

## Mã và dữ liệu

| Đường dẫn | Trách nhiệm |
| --- | --- |
| `apps/backend/src/latex_backend/writing/api.py` | Route, xác thực, giới hạn payload, upload ảnh và tạo dự án |
| `writing/service.py` | ACL Student/Tutor, lớp/đề/nháp/nộp/chấm/công bố, CAS và nguồn LaTeX |
| `writing/assets.py` | Biểu đồ luyện mẫu tự tạo |
| `resources/writing/catalog.json` | Checklist hướng dẫn theo dạng bài |
| `apps/frontend/components/writing/` | Workspace, form giao bài, thẻ chấm theo đoạn và tab tài liệu |
| `apps/frontend/lib/writing/` | Kiểu dữ liệu, đếm từ, cấu trúc đoạn và catalog UI |
| `apps/frontend/app/writing.css` | Theme và responsive riêng cho module |

MongoDB tự tạo collections khi có dữ liệu: `writing_classes`, `writing_memberships`, `writing_assignments`, `writing_drafts`, `writing_submissions`, `writing_reviews`, `writing_results`, `writing_publications`, `writing_assets`. Ảnh đề lưu binary riêng trong database, có kiểm tra quyền; không phải đường dẫn ổ đĩa người dùng. Nháp/đề/chấm dùng revision CAS để cửa sổ cũ không ghi đè bản mới. Không migrate hay sửa các dự án LaTeX có sẵn khi bật module.

API cùng origin dưới `/api/writing/`: overview; classes và classes/join; assignments; assignments/{id}/draft và submit; submissions/{id}, review và publish; assets; latex. Mọi route yêu cầu session và kiểm quyền server, không tin role do UI gửi.

## Phạm vi hiện tại

Đã có luồng lớp và giao bài cho cả lớp, chưa giao riêng từng học viên, quản trị thành viên hoặc email/thông báo đẩy. Checklist không phải AI chấm tự động; không kiểm đạo văn/giám thị thi. Chưa thêm bộ chọn mục đích thư độc lập cho General Training. PDF không phải snapshot file PDF do Student tải lên. Module Writing hiện lưu nháp theo revision, chưa đồng biên tập IELTS cùng lúc bằng CRDT; mở nhiều tab sửa cùng nháp có thể nhận xung đột để đối chiếu. Quy trình bật web public vẫn cần cấu hình vận hành/bảo mật riêng của hệ thống.

## Kiểm chứng

- Backend: toàn bộ 227 tests đạt, gồm ACL/role chia sẻ dự án, nháp chấm riêng tư, snapshot nộp, CAS, band, import dữ liệu cũ và export LaTeX.
- Frontend: TypeScript và production build đạt.
- Cập nhật shell 12/11/2026: browser regression đạt header dự án dùng chung, đủ bốn icon, chuyển Tệp/Nhận xét/Chat và lưu lại cả hai task khi rời IELTS. Báo cáo `target/writing-shared-shell-20261112-03/report.json`; luồng chấm/compile/mobile/reload vẫn đạt.
- `apps/frontend/scripts/test-writing-workflow.mjs`: hai phiên Tutor/Student trên web chính, lớp → giao Task 1+2 → nháp/PDF thật → nộp → checklist → công bố → kết quả/phiếu LaTeX compile → reload, mobile và dark theme. Test dùng dữ liệu cách ly; không tạo học viên/bài mẫu trong tài khoản thật.
- Cùng luồng browser đã đạt trên MongoDB Atlas thật trong namespace kiểm thử riêng; collections test đã được dọn có guard. Báo cáo: `target/presence-atlas-b56bdeda0b02c3c6/atlas-report.json` (`passed: true`, `cleaned: true`).
- Chia sẻ/role cập nhật 12/11/2026 đã nghiệm thu lại trên Atlas: `target/presence-atlas-ec350765ab3a0e92/report.json`, `atlas-report.json` đều đạt; đổi role trong IELTS, đổi quyền trong editor, thu hồi quyền, đầy đủ luồng nộp/chấm/compile và không đổi role tài khoản. Dữ liệu test cách ly đã được dọn.

# IELTS Writing trong web Latex

Tích hợp ngày 11/11/2026, sử dụng session và vai trò thật của tài khoản. Không đổi role bằng nút demo; không đưa tài khoản, bài nộp hoặc điểm mẫu vào database khi khởi động.

## Chạy và mở

```powershell
.\scripts\run-python-web.bat --build
.\scripts\run-python-web.bat
```

Nếu server đang chạy, lưu bài rồi dừng bằng Ctrl+C trước khi chạy lại để nạp bản mới. Mở địa chỉ launcher báo, mở một dự án và nhấn icon mũ tốt nghiệp ở rail trái. IELTS là module nội dung trong workspace, dùng nguyên header dự án (tên, Lịch sử, Chia sẻ, Bố cục, tài khoản), không có nút chữ IELTS hay cặp tab Editor/IELTS trên header. Cả bốn icon Tệp/Nhận xét/Chat/IELTS được giữ nguyên. Bấm Tệp, Nhận xét hoặc Chat để quay về chức năng tương ứng; bài IELTS/bản chấm phải lưu thành công trước khi chuyển. Không đổi hoặc tạo dự án khi chỉ mở module; chỉ thao tác mở bài/phiếu LaTeX trong editor mới tạo dự án riêng theo chức năng vốn có.

## Luồng Tutor và Student

**Chia sẻ dự án (12/11/2026):** nút **Chia sẻ** dùng chung một hộp thoại cho editor và IELTS. Chủ sở hữu chọn email, quyền **Chỉ xem / Chỉnh sửa** và vai trò **Tutor / Học viên**; sau đó có thể đổi từng quyền hoặc vai trò trong danh sách thành viên. Chia sẻ bằng liên kết có vai trò mặc định cho người mới; người đã mở liên kết hiện trong danh sách để quản lý riêng. Link bị tắt/đổi hoặc quyền bị thu hồi sẽ chặn cả editor lẫn API IELTS.

Vai trò này chỉ thuộc dự án; không sửa vai trò tài khoản toàn hệ thống. Một tài khoản Student có thể được mời làm Tutor ở một dự án, và ngược lại. Tutor có quyền chỉnh sửa được giao/chấm/công bố bài của dự án (bao gồm nháp chấm của đồng Tutor). Học viên chỉ viết/nộp và xem bản nộp, kết quả của chính mình, không thấy bài người khác hay nháp chấm. Quyền Chỉ xem không cho viết/nộp/chấm, kể cả vai trò Tutor. Chuyển quyền/vai trò được cập nhật khi tải lại dữ liệu hoặc tối đa khoảng 5 giây; mọi API ghi kiểm quyền mới ngay. Nếu đang sửa mà quyền bị hạ, có thể tải bản khôi phục phần chưa lưu.

Dữ liệu IELTS mới gắn với `projectId`. Người được chia sẻ dự án tự thấy lớp và đề đã giao trong dự án, không bắt buộc nhập thêm mã lớp. Mã lớp vẫn dùng được trong cùng dự án; mã không thay thế quyền truy cập dự án. Luyện tập cá nhân của Học viên vẫn riêng tư, không xuất hiện trong hàng đợi chấm chung.

Nếu đã tạo lớp/đề bằng phiên bản IELTS cũ chưa gắn dự án, module hiển thị số lượng và nút **Đưa dữ liệu cũ vào dự án này**. Chỉ bấm sau khi mở đúng dự án và đọc xác nhận: lớp/đề thuộc bạn cùng các bản nộp liên quan sẽ dùng quyền chia sẻ của dự án đích. Cần mời học viên cũ vào dự án để tiếp tục làm bài. Nội dung bản nộp/điểm đã lưu được giữ; không tự chuyển dữ liệu cũ vào dự án đầu tiên mở, không chuyển lần nữa sang dự án khác.

1. Tutor mở dự án, vào **Chia sẻ** để mời Học viên hoặc đồng Tutor. Người nhận mở link và đăng nhập đúng email được mời.
2. Tutor vào **Lớp học**, đặt tên và tạo lớp. Học viên được chia sẻ dự án tự thấy lớp; mã tham gia vẫn có thể dùng trong cùng dự án. Tutor thấy danh sách thành viên; Student không xem danh sách cá nhân của lớp.
3. Tutor vào **Giao bài IELTS**, chọn lớp, Task 1/Task 2/cả hai, Academic hoặc General Training, dạng bài, câu hỏi, hình đề, thời lượng và hạn nộp. Có thể lưu đề nháp hoặc giao cho cả lớp. Đề đã giao giữ nguyên để không đổi yêu cầu của bài đang làm.
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

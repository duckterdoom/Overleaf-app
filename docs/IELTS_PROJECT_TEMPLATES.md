# Bộ đề IELTS Writing

**Cập nhật:** thư viện chỉ hiển thị **một template Bộ đề đầy đủ 21 dạng**. Các dạng nằm trong cùng dự án dưới dạng `question-task1-*.tex` và `question-task2-*.tex`, mở từng tệp rồi compile độc lập. Đã bỏ các template lẻ và template mock khỏi catalog; các mô tả 24 mẫu bên dưới là lịch sử phiên bản trước.

Vào **Dự án mới → Đề mẫu IELTS Writing**. Có 24 mẫu dự án: bộ đầy đủ 21 dạng, hai đề luyện Academic/General Training 60 phút, và 21 mẫu riêng. Hướng dẫn có tiếng Việt/Anh; câu hỏi giữ tiếng Anh.

| Nhóm | Dạng bài |
| --- | --- |
| Task 1 Academic | Đường, cột, tròn, bảng, kết hợp, quy trình, bản đồ, sơ đồ hệ thống |
| Task 1 General Training | Thư trang trọng, bán trang trọng, thân mật |
| Task 2 | Quan điểm; thảo luận hai phía; ưu/nhược; lợi ích vượt bất lợi; nguyên nhân/giải pháp; vấn đề/giải pháp; nguyên nhân/hệ quả; hai câu hỏi; tích cực/tiêu cực; kết hợp |

Đây là các hồ sơ giảng dạy của ứng dụng, không phải danh sách phân loại chính thức khép kín của IELTS. Mỗi dạng có một đề tự biên soạn, số liệu/hình giả định, không gắn nhãn đề thi thật.

## Tệp và cách sử dụng

- `main.tex`: đề đơn hoặc cả bộ, bộ tổng hợp có mục lục PDF.
- `question-task1-*.tex`, `question-task2-*.tex`: đề độc lập có thể compile và lấy làm đề trong module IELTS.
- `questions/*.tex`: nội dung và hình TikZ/bảng để sửa; đây là phần chèn vào đề, không compile độc lập.
- `data/*.csv`: dữ liệu đối chiếu; không điều khiển biểu đồ tự động. Đổi số liệu cần sửa cả TikZ và CSV.
- `answers.tex`: khung bài làm tùy chọn, không có câu trả lời mẫu và không tự nộp bài. Tiêu đề đoạn chỉ là gợi ý.
- `GUIDE.md`: hướng dẫn, checklist Tutor theo catalog chấm hiện có; tick không tự quy đổi band.
- `ielts-bank.json`: ánh xạ task/profile/tệp; không tự tạo lớp hoặc cấp quyền.
- `preamble.tex`: font/preamble dùng chung hỗ trợ tiếng Việt và ba engine.

1. Tạo dự án từ mẫu, compile `main.tex` để xem và sửa đề nếu cần.
2. Chia sẻ dự án với vai trò Tutor/Học viên như bình thường.
3. Tutor vào IELTS Writing → Lớp học → Giao bài IELTS, chọn đúng Academic/General và dạng bài.
4. Mỗi task chọn tệp `question-taskN-*.tex` rồi bấm **Dùng tệp làm đề**. Không chọn `answers.tex` hoặc phần `questions/*.tex` làm tệp gốc.
5. Đặt thời lượng/hạn nộp trong form rồi giao. Đề đủ hai task có thể đặt 60 phút; template không tự bắt đầu đồng hồ.
6. Học viên xem PDF/biểu đồ và viết/nộp qua module IELTS. Đề là bản chốt khi lấy tệp, không thay theo sửa source sau đó; muốn đổi cần lấy lại/giao bài mới.

## Nguồn và kiểm chứng

Tham khảo dạng bài từ [IELTS CITY Writing 2025](https://ieltscity.vn/de-thi-ielts-writing-2025/); không sao chép ngân hàng câu hỏi, bài mẫu hay hình. Thời lượng/số từ đối chiếu [IELTS Academic](https://ielts.org/take-a-test/test-types/ielts-academic-test/ielts-academic-format-writing) và [General Training](https://ielts.org/take-a-test/test-types/ielts-general-training-test/ielts-general-training-format-writing).

Asset có sẵn trong backend resources, máy clone không cần chạy generator. Bảo trì bằng `scripts/build-ielts-templates.py`; script chỉ tái tạo asset, không sửa dự án người dùng.

Kiểm chứng 12/11/2026: 137 tests catalog/workspace/TeX đạt; 96 lượt compile PDF thật đạt gồm mọi mẫu en/vi, từng đề riêng, khung bài làm tổng hợp và XeLaTeX/LuaLaTeX trên đề Academic. Không có overfull box trong báo cáo. Bộ tổng hợp 23 trang, đề riêng 1 trang. Edge test đạt lọc 24 mẫu → tạo dự án qua UI → compile PDF → lấy hai tệp đề → giao bài 60 phút. Dữ liệu test riêng.

Báo cáo: `target/ielts-templates-20261112-01/report.json`, `target/ielts-template-browser-20261112-01/report.json`. Đã xem ảnh biểu đồ cột, quy trình và bản đồ.

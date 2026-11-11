# Prototype IELTS Writing

Thiết kế đã chuyển sang **IELTS Writing**, thay mẫu Research Introduction trước. Vẫn giữ khung workspace Latex: logo, header, rail, sidebar và theme sáng/tối.

IELTS vẫn kết hợp LaTeX: màn học viên và màn chấm Tutor có **Văn bản / LaTeX source / PDF preview**. Bấm Tạo/cập nhật LaTeX để tạo source từ bài tiếng Anh; Tutor có Tạo phiếu LaTeX và tải `.tex` thật. PDF trên prototype là bố cục minh họa có nhãn rõ; compile thật sẽ nối pipeline app hiện có. Trong demo, nội dung chấm chính vẫn là văn bản, sửa source không tự đổi ngược nội dung IELTS.

Mở từ root repository:

```powershell
Start-Process .\docs\prototypes\writing\index.html
```

Màn mặc định là **Học viên → Luyện viết → Task 2**. Có thể:

- Chọn Academic/General Training và Task 1/Task 2; Academic Task 1 có biểu đồ demo, General Training Task 1 có đề thư.
- Gõ bài trực tiếp, xem số từ, luyện một task hoặc đủ hai task với đồng hồ demo.
- Kiểm tra và nộp văn bản; thiếu từ được cảnh báo, không bắt chuyển LaTeX/PDF.
- Chọn Giảng viên ở footer → Bài nộp → Chấm bài; nhập TA/TR, CC, LR, GRA, nhận xét rồi công bố.
- Chuyển lại Học viên → Kết quả để xem band và phản hồi. Một task không hiện Writing tổng hợp.
- Dùng Sáng/Tối hoặc Reset demo ở footer.

## Góc nhìn Tutor

**Thử Task 1 ngay:** mở `tutor-task1.html` hoặc bấm **Thử Task 1 · biểu đồ** trong Tutor. Bài mẫu Academic gồm biểu đồ cột về phương tiện đi lại năm 2000/2020, câu trả lời 175 từ chia Introduction/Overview/Body 1/Body 2, thẻ comment/checklist và band TA/CC/LR/GRA. Có source LaTeX theo bản nộp. Các số liệu và bài mẫu tự biên soạn, chưa gán sẵn band.

```powershell
Start-Process .\docs\prototypes\writing\tutor-task1.html
```

Đề và biểu đồ hiển thị ngay trong màn chấm, có thể thu gọn để đọc bài. **Về bài trước** khôi phục phiên trước khi mở Task 1, gồm nháp và đánh giá; Task 1 cũng giữ trạng thái riêng khi mở lại. Các phiên demo chỉ nằm trong browser, không đụng dự án thật. Test: `node scripts/test-task1-demo.mjs`.

Mở trực tiếp `tutor.html` hoặc chọn **Tutor (Giảng viên)** ở mục Vai trò cuối trang:

```powershell
Start-Process .\docs\prototypes\writing\tutor.html
```

Tutor có tổng quan học viên, giao bài, bài nộp và tiêu chí IELTS. Bấm **Mở bài chấm mẫu** để xác nhận tạo một snapshot essay demo và vào màn chấm ngay, không cần tự nộp bài ở vai trò học viên. Nếu đã có submission thì mở bản hiện có, không thay bài/điểm; working copy học viên luôn được giữ nguyên. `tutor.js` cung cấp góc nhìn này; đây vẫn chỉ là prototype.

### Checklist Introduction / Body / Overview / Conclusion

Màn chấm hiện dùng **thẻ nhận xét bên lề giống cách Google Docs bố trí comment**, thay cho khung checklist cố định. Bài viết rộng hơn, mỗi phần có một thẻ gắn cạnh đoạn tương ứng. Bấm đoạn hoặc thẻ để mở checklist và nhận xét; chỉ một thẻ mở chi tiết. Đoạn đang nhận xét được tô vàng. Bài và các thẻ cuộn chung, không lặp cả đoạn văn trong khung góp ý.

Nút **Tổng kết band** mở bảng TA/TR, CC, LR, GRA khi cần; đóng bằng nút × hoặc Esc. **Dạng bài & phân phần** mở cấu hình hồ sơ và thêm Body; thao tác gán đoạn nằm trong thẻ đang mở. Trạng thái, checklist, notes, trích đoạn và công bố giữ nguyên như trước. Mobile xếp các thẻ dưới bài viết để không ép chữ vào cột hẹp.

Catalog có 8 hồ sơ Academic Task 1, 3 hồ sơ mức trang trọng GT Task 1 và 10 hồ sơ Task 2; thư có thêm 8 chức năng. Đây là hướng dẫn sư phạm của prototype, không phải checklist điểm chính thức hoặc bộ phân loại cố định mọi đề. Xem [nghiên cứu và checklist chi tiết](../../IELTS_SECTION_CHECKLISTS.md).

Góp ý theo phần được lưu cùng bản công bố demo và đưa vào phiếu LaTeX. Sau công bố, sửa nháp không thay phản hồi học viên đang thấy. Kiểm thử bổ sung: `node scripts/test-section-review-design.mjs`; ảnh ở `target/ielts-section-review/`.

`ielts.js` chứa luồng demo; `ielts.css` bố trí đề–bài viết–chấm IELTS; `workspace.css` và `styles.css` giữ kiểu trình bày của khung Latex. Các script nghiên cứu `app.js`/`workspace.js` cũ đã bỏ khỏi prototype để tránh nhầm định hướng.

Giới hạn: chỉ localStorage và UI demo, không phải ứng dụng thi/chấm thật. Không có backend, phân quyền thật, gửi email, chấm AI, export thật hoặc auto-submit. Đề/biểu đồ tự biên soạn; band là ví dụ ước tính luyện tập, không phải kết quả IELTS chính thức. Prototype chỉ giữ bản nộp gần nhất; backend thật phải lưu đủ lịch sử.

[Thiết kế IELTS đầy đủ](../../IELTS_WRITING_DESIGN.md). Kiểm thử: từ `apps/frontend`, chạy `node scripts/test-ielts-writing-design.mjs` (lệnh cũ `test-writing-design.mjs` trỏ sang cùng test). Ảnh kiểm chứng: `target/ielts-writing-design/`.

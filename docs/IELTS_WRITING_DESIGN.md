# Thiết kế IELTS Writing cho Latex

Cập nhật 07/11/2026 theo yêu cầu đính chính: **IELTS Writing là chức năng chính**, không lấy chấm paper/Research Introduction làm màn mặc định. Kế thừa ý tưởng giao bài và chấm trong `Writing.pdf`, nhưng phần mở rộng nghiên cứu khoa học để sau. Tài liệu này thay định hướng giao diện chính của bản thiết kế Writing trước.

**Cập nhật kết hợp LaTeX:** IELTS là nghiệp vụ đánh giá, LaTeX là công cụ soạn/trình bày và xuất phiếu trong cùng workspace. Học viên và Tutor có tab Văn bản / LaTeX source / PDF preview. Không tách IELTS thành một ứng dụng chỉ gõ essay; cũng không chấm kỹ năng viết LaTeX thay cho kỹ năng tiếng Anh. Các chỗ dưới đây nói không bắt compile có nghĩa là không bắt buộc với mọi bài luyện, không loại bỏ LaTeX khỏi sản phẩm.

[Mở prototype IELTS](prototypes/writing/index.html). Prototype giữ khung workspace hiện có; chưa tích hợp nghiệp vụ thật vào app/backend.

## 1. Màn hình chính: viết IELTS, không viết mã

```text
Latex | IELTS Writing | Lịch sử · Cộng tác · Bố cục
─────────────────────────────────────────────────────────
Rail | IELTS Writing | Task 1 / Task 2 · loại bài · đồng hồ
     | Luyện viết   |───────────────────┬─────────────────
     | Bài được giao| ĐỀ BÀI            | YOUR ANSWER
     | Bản nộp     | Câu hỏi / hình    | Viết tiếng Anh
     | Kết quả     | chart / letter    | trực tiếp
     |             |                   | Số từ · đã lưu
─────────────────────────────────────────────────────────
```

Giữ logo, header 42px, rail tối 46px, sidebar 270px, token/phông sáng–tối của app. Vùng trả lời/bản nộp có tab Văn bản / LaTeX source / PDF preview. Khi Tutor đổi dạng xem, cột tiêu chí vẫn giữ vị trí. Không dùng rubric Introduction/Research Gap hoặc thang /10 thay band IELTS.

Với desktop, đề và bài viết đặt cạnh nhau; tablet/mobile xếp dọc, vùng nội dung cuộn được. Trong lúc chấm, thêm cột tiêu chí bên phải. Giao diện tiếng Việt, đề và bài làm tiếng Anh. Chuyển task giữ nguyên bài viết của task trước.

## 2. Loại bài và chế độ

| Cấu hình | Giao diện |
| --- | --- |
| Academic Task 1 | Đề + ảnh biểu đồ/bảng/process/map; vùng mô tả dữ liệu; bộ chấm TA/CC/LR/GRA |
| General Training Task 1 | Tình huống viết thư + các yêu cầu; vùng viết thư; không hiển thị biểu đồ Academic |
| Task 2 | Đề essay: câu hỏi và yêu cầu; vùng viết luận; bộ chấm TR/CC/LR/GRA |
| Luyện một task | Chỉ nộp và nhận band cho task đó; không gọi điểm một task là Overall Writing |
| Làm đủ hai task | Hai vùng bài riêng; đồng hồ chung khi bật thời gian; kiểm cả hai bài trước nộp |

IELTS Academic Writing có hai task, tổng thời gian 60 phút; Task 1 tối thiểu 150 từ và thường dành khoảng 20 phút, Task 2 tối thiểu 250 từ và khoảng 40 phút. Task 2 đóng góp gấp đôi Task 1 vào điểm Writing. Các con số này là thông tin định dạng, không phải quy tắc bắt buộc tách đồng hồ thành hai lượt thi. [Nguồn IELTS.org](https://ielts.org/take-a-test/test-types/ielts-academic-test/ielts-academic-format-writing).

Prototype có đề và biểu đồ tự biên soạn để thử UI, không dùng nhãn đề thi chính thức. Phân biệt rõ phần cấu hình General Training với ví dụ Academic trong nguồn trên.

## 3. Luồng học viên

1. **Bài được giao:** xem task, loại bài, giảng viên, hạn và chế độ. Mặc định làm cá nhân.
2. **Luyện viết:** đọc đề bên trái, gõ câu trả lời bên phải. Số từ chỉ tính bài viết, không tính đề. Ghi rõ trạng thái lưu; chuyển task không mất nội dung.
3. **Đồng hồ:** luyện tập có thể không bấm giờ; bật thì dùng thời lượng được cấu hình. Hai task dùng một đồng hồ 60 phút nếu chọn bài đủ hai task. Sản phẩm thật lấy thời gian server; prototype chỉ đếm trên browser và không giả auto-submit.
4. **Kiểm tra:** xem câu trả lời từng task, số từ và phần còn trống. Task được giao bị trống thì chưa nộp được. Thiếu số từ có cảnh báo; không tự suy ra band hoặc trừ điểm máy móc. Giảng viên có thể nhận bài thiếu từ để phản hồi nếu policy cho phép.
5. **Nộp:** snapshot văn bản trực tiếp. Không yêu cầu compile, chuyển Word hoặc tạo LaTeX. Chỉ báo thành công khi backend xác nhận trong bản thật.
6. **Chờ chấm:** xem đúng bản đã nộp; sửa working copy không thay snapshot. Nộp lại tạo attempt mới theo policy.
7. **Kết quả:** band từng tiêu chí, phản hồi, minh chứng và việc nên luyện tiếp. Chỉ hiện bản đã công bố.

Counter trong prototype dùng phân tách khoảng trắng, là bộ đếm hỗ trợ viết; khi triển khai phải thống nhất cách đếm số, dấu nối và contraction, không tự nhận khớp tuyệt đối với quy trình chấm thi.

## 4. Luồng giảng viên

Trong prototype, vai trò này hiển thị là **Tutor (Giảng viên)**. Mở `docs/prototypes/writing/tutor.html` để vào thẳng tổng quan Tutor: học viên trong lớp, số bản nộp chờ chấm, phản hồi đã công bố, giao bài và mở bài chấm. Có thể dùng bài essay mẫu sau xác nhận để xem góc nhìn chấm khi chưa có submission, không ghi đè working copy. Các chỉ số lấy từ dữ liệu demo hiện có, không giả dữ liệu lớp thật.

**Giao bài:** chọn Academic/General Training, Task 1/Task 2/cả hai; nhập đề và đính kèm hình cho Academic Task 1; chọn học viên/lớp, hạn và thời lượng tùy chọn. Dùng rubric IELTS bốn tiêu chí, không bắt tạo cây tiêu chí paper. Cho phép lưu đề nháp trước khi giao.

**Danh sách bài nộp:** học viên, task, lần nộp, thời gian, số từ, trạng thái chấm/công bố. Không lấy điểm mặc định của JSON mẫu làm điểm đã chấm.

**Chấm ba cột:**

- Trái: học viên/bản nộp để chuyển bài.
- Giữa: đề rút gọn và bài nộp chỉ đọc, chuyển Task 1/Task 2 đúng snapshot.
- Phải: TA hoặc TR, CC, LR, GRA; band, gợi ý đánh giá và nhận xét theo từng tiêu chí; nhận xét chung.

Nhận xét nên gắn câu/đoạn cụ thể: điểm mạnh, điều chưa đạt, giải thích và cách sửa. Có thể đưa gợi ý sửa câu, nhưng không tự viết đè bài gốc. Chỉ dẫn band phải lấy từ nguồn được phép sử dụng hoặc diễn giải có nhãn; không gắn “official descriptors” lên nội dung tự soạn.

**Lưu nháp và công bố:** hai hành động riêng. Chưa chấm đủ tiêu chí thì không có band cuối; thiếu task của bài hai task thì không có tổng Writing. Công bố kiểm đủ dữ liệu và gửi thông báo; học viên không đọc được nháp qua API/WS. Chỉnh điểm đã công bố có revision và lý do.

## 5. Band và phản hồi

Chi tiết màn Tutor mới: [IELTS_SECTION_CHECKLISTS.md](IELTS_SECTION_CHECKLISTS.md) nghiên cứu theo từng họ dạng bài và cung cấp checklist cho Introduction/Overview/Body/Conclusion hoặc thư. Khung chat/checklist nằm giữa tài liệu và cột band cuối; có minh chứng, trạng thái, góp ý và bản công bố riêng. Đây là lớp hỗ trợ nhận xét, không phải công thức tính band theo từng phần.

| Tiêu chí | Áp dụng | Mục đích trên màn chấm |
| --- | --- | --- |
| TA / Task Achievement | Task 1 | Đáp ứng yêu cầu bài và thể hiện thông tin/ý cần thiết |
| TR / Task Response | Task 2 | Trả lời câu hỏi, lập trường và phát triển ý |
| CC / Coherence & Cohesion | Cả hai | Tổ chức bài và liên kết |
| LR / Lexical Resource | Cả hai | Sử dụng từ vựng |
| GRA / Grammatical Range & Accuracy | Cả hai | Cấu trúc và chính xác ngữ pháp |

Các tiêu chí được đối chiếu từ [định dạng và đánh giá Writing của IELTS](https://ielts.org/take-a-test/test-types/ielts-academic-test/ielts-academic-format-writing); phần giải thích ngắn trên UI là diễn giải học tập.

Quy tắc thiết kế:

- Điểm bắt đầu `null`, khác band 0; không điền sẵn 6.5/7.0 vào bài mới.
- Bốn tiêu chí cùng trọng số trong một task. Lưu điểm gốc, nhận xét và revision.
- Khi có cả hai task: `writingRaw = (task1Raw + 2 × task2Raw) / 3`. Không làm tròn từng task rồi mới tính tổng.
- Prototype cho chọn band theo bước 0.5 và làm tròn kết quả tới 0.5 để minh họa **ước tính luyện tập**. Không khẳng định đây là toàn bộ quy trình chấm/chuẩn hóa của IELTS.
- Một task chỉ ghi “Task 1/Task 2 band ước tính”. Hai task mới có “Writing band ước tính”. Không tính Overall IELTS vì không có Listening/Reading/Speaking.
- Không chấm band tự động từ số từ, lỗi spelling hoặc AI chưa được kiểm chứng. Giảng viên là người chấm trong phạm vi thiết kế hiện tại.

## 6. LaTeX/PDF/Word ở vị trí nào?

LaTeX được tích hợp ở ba bước:

1. **Làm bài:** viết văn bản rồi chủ động tạo source LaTeX để dàn trang; có thể xem/chỉnh source và preview PDF. Văn bản và source có revision/nguồn gốc rõ, không giả đồng bộ hai chiều. Trong prototype, văn bản vẫn là nội dung chấm chính; tab source là nhánh trình bày, sửa source không tự đổi câu trả lời tiếng Anh.
2. **Chấm:** Tutor đọc cùng bản nộp bằng Văn bản, source hoặc PDF, đối chiếu đoạn/câu với TA/TR, CC, LR, GRA. Bản nộp phải giữ source, asset và PDF snapshot cùng revision khi dùng pipeline thật; không lấy PDF stale hoặc source working copy sau nộp. Nhận xét IELTS không trừ điểm vì chọn template LaTeX khác nếu đề không quy định trình bày.
3. **Trả bài:** Tutor tạo phiếu LaTeX có task, bốn band, nhận xét và trạng thái nháp/công bố; compile thành PDF bằng pipeline hiện có. Có thể tải source .tex, PDF phiếu và DOCX qua converter hiện có theo yêu cầu. Word không thay source đánh giá.

Khi triển khai chế độ **LaTeX làm nguồn chính**, phải chọn canonical mode rõ trước, lưu source revision và bản trích nội dung tương ứng để đếm từ/chấm, loại macro/preamble khỏi số từ, cảnh báo lệnh không trích được. Không chỉ thay textarea rồi tự nhận rằng số từ và band đang chấm đúng source. Prototype hiện chưa triển khai nhánh source-first hoàn chỉnh này.

Teacher có thể quy định bài chỉ nộp văn bản hoặc nộp cả source/PDF. Với bài yêu cầu PDF, compile lỗi/stale phải chặn nộp artifact đó; chế độ luyện văn bản vẫn nộp được trực tiếp. Preview PDF thật và bản tải về phải cùng artifact bytes. Prototype dùng tờ giấy HTML có nhãn minh họa, không phải PDF đã compile.

Nút Tạo phiếu LaTeX trong prototype tải .tex thật, có escape ký tự và preamble XeLaTeX/fontspec. Việc compile/xuất PDF tự động trên web vẫn cần nối API backend, không giả thành công trong demo.

## 7. Cách gắn vào code hiện tại

- Giữ shell, theme, rail và panel của workspace. Writing/IELTS là một vùng công việc, không thay editor LaTeX chính của app.
- Có thể dùng lại `IeltsGradingPanel.tsx` và `documents/ielts.py` về ý tưởng tiêu chí/band/report; cần schema validation, dữ liệu trống mặc định, membership và submission/assessment revision trước khi dùng nhiều tài khoản.
- Domain chính: `IeltsAssignment`, `IeltsAttempt`, `TaskResponse`, `CriterionAssessment`, `PublishedFeedback`. TaskResponse lưu văn bản và đề version; không lưu nguyên trạng dữ liệu transient của PDF viewer làm bài nộp.
- Giữ các yêu cầu server storage, Mongo wiring, session riêng, quyền truy cập và snapshot bất biến của thiết kế trước. Lớp/assignment có thể dùng chung hạ tầng, nhưng UI và rubric IELTS độc lập với paper.
- Chức năng thi theo giờ thật cần deadline server, reconnect, receipt và policy hết giờ riêng. Không nâng prototype localStorage thành hệ thống thi chỉ bằng cách bật countdown.

## 8. Phạm vi prototype và nghiệm thu

Prototype hiện tại dùng `ielts.js`/`ielts.css`, thay màn mặc định Research Introduction. Hỗ trợ chọn task/loại bài/chế độ, viết và đếm từ, đồng hồ demo, kiểm tra/nộp trực tiếp, chấm nháp bốn tiêu chí, công bố và xem kết quả; UI sáng/tối, mobile. Không có backend, gửi đề thật, bảo mật role, export thật hoặc tự động chấm.

Nghiệm thu thiết kế: không có rubric Research Gap hoặc điểm /10; Academic Task 1 có hình, General Training Task 1 là thư; Task 2 dùng TR; đổi task không mất bài; thiếu từ chỉ cảnh báo; không cần LaTeX/PDF khi nộp; sửa working copy không đổi snapshot; nháp không xuất hiện ở màn kết quả; một task không hiện Writing tổng; đủ hai task mới tổng hợp đúng trọng số; theme và cuộn mobile hoạt động.

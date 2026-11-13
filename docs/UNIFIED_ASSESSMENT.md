# Chấm IELTS, Report và Research trong cùng workspace

Tính năng chấm hiện có được mở rộng, không có module hoặc khu vực dự án riêng cho Report/Research. Dùng icon chấm bài ở thanh trái dự án như trước. Header, quyền chia sẻ, avatar và tệp dự án dùng chung với editor.

Chuyển các mục dùng dữ liệu danh sách đang có và cập nhật ở nền, không chờ tải lại toàn bộ overview trước khi hiện trang. Nếu có bản sửa chưa lưu, giao diện báo đang lưu trước khi chuyển; lỗi lưu vẫn giữ bạn ở bài đang sửa. Khi mở bài cụ thể, ứng dụng lấy đầy đủ nội dung/tiêu chí snapshot qua API chi tiết.

## Giao bài không qua lớp

### Bài tập và bài kiểm tra

Tutor vào **Giao bài → Hình thức giao bài** để chọn **Bài tập** hoặc **Bài kiểm tra**. Cả hai lấy đề từ editor và dùng cùng bộ tiêu chí IELTS/Report/Research.

Với bài kiểm tra, bắt buộc đặt:

- Mật khẩu ít nhất 6 ký tự; không trả lại mật khẩu cũ qua API. Để trống khi sửa đề để giữ mật khẩu, hoặc dùng **Đổi mật khẩu** ở danh sách đề. Có thể đổi sau khi đã có học viên bắt đầu; người đang làm giữ nguyên lượt vào và đồng hồ, người mới phải dùng mật khẩu mới.
- Thời lượng 1–180 phút.
- Giờ mở và giờ đóng; giờ đóng phải sau giờ mở. Không dùng Hạn nộp hoặc Nhận bài quá hạn của bài tập thường.

Học viên mở mục **Bài kiểm tra** riêng ở sidebar, chọn bài, nhập mật khẩu rồi bấm **Bắt đầu bài kiểm tra**. Backend chỉ cho bắt đầu trong khoảng mở/đóng. Mốc kết thúc là thời điểm sớm hơn giữa lúc bắt đầu + thời lượng và giờ đóng. Mở lại/tải lại không đặt lại đồng hồ. Hết giờ hoặc đã nộp thì chỉ đọc bản nháp, không sửa hoặc nộp mới; bản đã lưu được giữ. Mỗi học viên có một lượt nộp bài kiểm tra, retry cùng yêu cầu không tạo bản nộp trùng. Hiện không tự động nộp thay học viên lúc hết giờ; cần bấm nộp trước mốc kết thúc.

**Đề đã tạo**, **Bài nộp học viên** của Tutor và **Lịch sử nộp**, **Kết quả & phản hồi** của Học viên có tab **Bài tập / Bài kiểm tra** ngay trong mục hiện tại. Chỉ sidebar Học viên có mục Bài kiểm tra riêng như yêu cầu; không tạo hệ thống chấm mới. Dữ liệu cũ không có trường kind được hiểu là bài tập.

Mật khẩu băm có salt, kiểm tra ở backend và có giới hạn thử sai. API danh sách/chi tiết đề chưa vào bài không gửi câu hỏi/attachment của bài kiểm tra; endpoint draft/save/submit và attachment cũng kiểm lượt vào. Quyền cây tệp editor vẫn theo chia sẻ dự án: nếu source đề nằm trong dự án được chia sẻ, thành viên có quyền đọc source đó như trước. Đây là cơ chế vào bài và tính giờ, không phải cơ chế giấu mọi source hoặc giám sát chống gian lận.

Tutor vào **Giao bài**, chọn loại **IELTS Writing / Report / Research**, đặt tên và bộ tiêu chí rồi lấy tệp đề từ editor. Chọn thời lượng/hạn nộp nếu cần. Học viên đã được chia sẻ dự án sẽ thấy bài ở **Bài được giao**, không nhập mã lớp. Mỗi người giữ bản nháp/bản nộp riêng; không ghi đè bài của người khác.

IELTS có Task 1/Task 2, Academic/General Training. Report và Research dùng một tài liệu, không chia Task IELTS. Cả ba loại dùng cùng cách lấy tệp LaTeX/PDF/ảnh/văn bản từ dự án, cùng màn đề lớn bên trái và ô viết bên phải, cùng lưu nháp/nộp bài. Thời lượng và hạn nộp vẫn thuộc bài tập của dự án.

Lớp học đã được bỏ khỏi giao diện và khỏi yêu cầu tạo bài tập mới trong dự án. API và dữ liệu lớp cũ được giữ để các bản nộp cũ/luồng tương thích không mất liên kết; không xóa lớp hoặc chuyển dữ liệu thật ngầm.

## Quản lý tiêu chí chấm

Mục **Tiêu chí IELTS** đổi thành **Tiêu chí chấm**. Ba tab phân loại, danh sách bộ tiêu chí bên trái và trình sửa bên phải:

- Thêm, đổi tên, phân loại hoặc xóa cả bộ tiêu chí, gồm cả bộ mặc định cũ.
- Với IELTS, chọn Task và biến thể. Report/Research không có cấu hình band.
- Thêm, đổi tên, xóa phần đánh giá như Introduction, Body, Methodology, References…
- Thêm/sửa/xóa các mục con trong mỗi phần.
- Gán mã TA/TR, CC, LR, GRA cho các mục IELTS; Report/Research dùng checklist nội dung.
- Bấm **Lưu tiêu chí dự án** để lưu MongoDB, có kiểm tra revision chống ghi đè từ cửa sổ khác.

Cấu hình lưu theo `projectId`. Đổi người sở hữu hoặc vai trò Tutor/Học viên không mất cấu hình. Bài tập đã giao giữ `rubricCatalog` snapshot: xóa/sửa tiêu chí dự án chỉ tác động bài tạo/cập nhật sau này, không làm hỏng bài cũ. Bộ Report/Research khởi tạo mẫu được bổ sung khi đọc cấu hình cũ; sau lần lưu theo schema mới, các bộ người dùng đã xóa không tự xuất hiện lại.

## Chấm và công bố

Cả ba loại dùng cùng màn đọc bài và thẻ nhận xét theo đoạn, chọn phần đánh giá, trạng thái từng mục, dẫn chứng và lời phê. Tutor có thể đổi bộ đối chiếu trong những bộ thuộc cùng loại được chốt trong bài.

**IELTS:** mở Tổng kết band. Có thể nhập điểm như trước hoặc bấm **Tự tổng kết từ checklist** cho Task đang chọn:

1. Đạt = 1, Một phần = 0.5, Cần sửa = 0; Không áp dụng không tính mẫu số.
2. Các mục của từng đoạn và phần Toàn bài phải có trạng thái rõ ràng; Chưa xem không được tự coi là đạt hoặc không đạt.
3. Một mục cùng loại lặp ở nhiều đoạn lấy mức thấp nhất, tránh nhiều đoạn dễ làm tăng điểm.
4. Với mỗi mã TA/TR, CC, LR, GRA: lấy trung bình các mục được gán mã × 9, làm tròn 0.5. Mỗi mã cần có ít nhất một mục hợp lệ.
5. Tutor điều chỉnh được. Khi đang dùng điểm tự tính, đổi checklist sẽ tính lại; nếu thiếu đánh giá, điểm trở về chưa chấm. Chuyển bộ đối chiếu sẽ đặt lại các dấu tích và điểm của Task đó.
6. Bốn điểm tạo Task band; hai Task tạo Writing band theo trọng số Task 1:Task 2 = 1:2 như trước. Lưu/công bố vẫn do Tutor thực hiện.

Đây là **quy ước điểm luyện tập của ứng dụng**, không phải thuật toán chuyển checklist thành band chính thức. IELTS Writing chính thức đánh giá theo bốn tiêu chí và mô tả mức năng lực; xem [IELTS — Understanding IELTS scoring](https://ielts.org/organisations/ielts-for-organisations/understanding-ielts-scoring). UI hiển thị công thức và giới hạn ngay cạnh nút tự tổng kết. Trạng thái nguồn điểm (`manual`/`checklist`) lưu trong bản chấm.

**Report / Research:** nút **Tổng kết nhận xét**, không có bảng bốn điểm, Task band hoặc Writing band. Nhập nhận xét chung và hướng cải thiện rồi công bố. Backend không nhận điểm IELTS cho hai loại này.

## Phiếu trong dự án

Sau công bố, `result/` trong chính dự án có tài liệu LaTeX đề → bài làm → lời phê/checklist. IELTS đặt tên theo học viên/Task/dạng bài; Report/Research theo học viên/loại bài/bộ tiêu chí. Ảnh/PDF đề giữ trong `result/assets/`. Không tạo dự án mới. Phiếu Report/Research không có band IELTS. Tệp kết quả là tệp dùng chung theo quyền đọc dự án; nháp viết và nháp chấm vẫn giữ riêng theo ACL module.

## Kiểm chứng

Backend có test luồng Report/Research không lớp, rubric CRUD, snapshot sau xóa bộ tiêu chí, công bố không band, chặn gán band sai loại, phiếu kết quả cùng dự án. Browser regression mở rộng kiểm thử IELTS, quản lý rubric và hai loại tài liệu trong cùng workspace. Kết quả cuối và đường dẫn báo cáo được ghi trong AI_Handoff.

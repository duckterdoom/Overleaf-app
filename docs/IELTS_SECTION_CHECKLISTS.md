# Tutor chấm IELTS theo phần: checklist và tổng kết band

Cập nhật 07/11/2026. Bản thiết kế và prototype hỗ trợ Tutor đọc bài theo Introduction/Overview/Body/Conclusion hoặc các phần thư, ghi nhận bằng checklist dạng thẻ góp ý, rồi tổng kết bốn tiêu chí ở cột cuối. Mở `docs/prototypes/writing/tutor.html` → Mở bài chấm mẫu.

## 1. Kết quả nghiên cứu và giới hạn áp dụng

IELTS đánh giá bài theo TA/TR, CC, LR, GRA; không có bảng điểm chính thức riêng cho Introduction hoặc Body và không có công thức đổi số dấu tích thành band. Hướng dẫn band là mô tả năng lực toàn bài, nên dấu tích chỉ là minh chứng hỗ trợ quyết định của Tutor. [Band descriptors chính thức, bản ghi Updated May 2023](https://ielts.org/cdn/ielts-guides/ielts-writing-band-descriptors.pdf), [British Council: cách đánh giá](https://takeielts.britishcouncil.org/results/how-is-ielts-assessed).

Task 1 Academic cần tổng hợp thông tin hình; General Training là thư với nội dung và giọng văn theo tình huống. Task 2 phải xử lý đúng các yêu cầu trong đề. Tên dạng bài không thay việc đọc câu hỏi; đề có thể kết hợp nhiều yêu cầu. [Định dạng Academic](https://ielts.org/take-a-test/test-types/ielts-academic-test/ielts-academic-format-writing), [định dạng General Training](https://ielts.org/take-a-test/test-types/ielts-general-training-test/ielts-general-training-format-writing), [IELTS: hiểu đề Task 2](https://ielts.org/news-and-insights/ielts-writing-task-2-how-to-understand-ielts-question-prompts).

Các checklist dưới đây là **thiết kế sư phạm đề xuất cho phần mềm**, được diễn giải và bổ sung để Tutor thao tác, không phải nguyên văn quy định/band descriptors. Cấu trúc đoạn là gợi ý có thể sửa; không phạt bài chỉ vì không có một câu theo mẫu hoặc không có đúng hai Body. “Mọi dạng” ở đây là các họ dạng phổ biến và chế độ tùy chỉnh cho đề kết hợp, không khẳng định một danh sách hữu hạn bao trùm mọi đề tương lai.

## 2. Giao diện theo yêu cầu

**Bố cục mới:** nhận xét gắn bên lề từng đoạn, thay khung giữa cố định. Diện tích mặc định ưu tiên tài liệu và các thẻ thu gọn; chỉ mở một thẻ checklist khi Tutor chọn. Tổng kết band là panel mở theo yêu cầu.

```text
Rail / menu Tutor
   BÀI ĐÃ NỘP RỘNG                       THẺ BÊN LỀ
   Văn bản / LaTeX / PDF                  Introduction [thu gọn]

   Đoạn đang đọc được tô vàng             ┌ Tutor · Body 1 ───────┐
                                         │ ☑ Tiêu chí            │
                                         │ ☐ Điểm cần kiểm       │
                                         │ Nhận xét + minh chứng │
                                         └──────────────────────┘
   Đoạn tiếp theo                        Body 2 [thu gọn]

   Nút Tổng kết band → mở TA/TR, CC, LR, GRA khi cần
```

Thẻ bên lề hoạt động như nhận xét của Tutor gắn với phần đang chọn, không phải chatbot chấm tự động. Mỗi mục gồm checkbox, trạng thái, nhãn liên quan TA/TR/CC/LR/GRA; nhận xét tự do và trích dẫn. Không lặp cả đoạn đã đọc trong thẻ. Dữ liệu chấm không thay đổi theo việc mở/thu gọn giao diện.

Trên màn lớn: bài viết và thẻ dùng chung vùng cuộn; các thẻ bám vị trí đoạn và tự giãn để không chồng nhau. Bấm Tổng kết band để mở panel; có × và Esc để đóng. Cấu hình dạng bài/phân phần được thu gọn vào một nút. Mobile đặt thẻ sau nội dung, cuộn vùng chính; không chia ba cột hẹp. Source/PDF vẫn xem được, nhưng các thẻ ở đó chỉ bám phần văn bản theo snapshot, chưa có geometry/SyncTeX thật.

## 3. Bộ checklist nền theo phần

Mỗi nhóm đều có kiểm tra từ vựng/ngữ pháp theo minh chứng. Không yêu cầu đạt một số lượng từ nối hoặc cấu trúc phức cố định.

### Task 2 — Introduction

- Đặt đúng vấn đề, không đổi phạm vi câu hỏi khi diễn đạt lại. → TR
- Có lập trường/hướng trả lời phù hợp **khi đề yêu cầu**, không bắt chọn quan điểm cho mọi dạng. → TR
- Dẫn nhập giúp đi vào bài, tránh câu mở chung không liên quan. → TR/CC
- Lưu ví dụ từ dùng chưa chính xác và lỗi câu ảnh hưởng nghĩa. → LR/GRA

### Task 2 — từng Body 1, Body 2, Body n

- Xác định trọng tâm đoạn và nó trả lời yêu cầu nào. → TR/CC
- Có giải thích/phát triển ý, không chỉ nêu khẳng định. → TR
- Có lý giải hoặc minh họa phù hợp; không ép mọi đoạn phải có một ví dụ theo công thức. → TR
- Các câu tiến triển hợp lý, không nhảy ý hoặc lặp lại. → CC
- Đoạn không mâu thuẫn với lập trường và phần khác. → TR/CC
- Ghi từ/cụm từ và câu cụ thể cần giữ hoặc sửa. → LR/GRA

### Task 2 — Conclusion

- Kết lại câu trả lời theo đúng đề.
- Giữ nhất quán với những gì đã phát triển.
- Tổng hợp thay vì mở thêm một lập luận lớn chưa giải thích.
- Không bắt dùng một cụm kết luận cố định hoặc sao chép Introduction.

### Task 1 Academic — Introduction

- Giới thiệu đúng đối tượng/hình.
- Không làm mất hoặc đổi mốc thời gian, đơn vị, địa điểm quan trọng.
- Diễn đạt rõ nhãn và chủ đề; không dùng từ đồng nghĩa sai nghĩa.

### Task 1 Academic — Overview

- Nhận ra ý tổng quan phù hợp với hình.
- Tutor đánh giá việc chọn đặc điểm nổi bật của **hình cụ thể**.
- Có thể gán cùng một đoạn cho Introduction và Overview nếu hai chức năng nằm chung.
- Không mặc định đoạn 2 luôn là Overview; không yêu cầu một đoạn Conclusion riêng như essay Task 2.

### Task 1 Academic — Body/details

- Mô tả có thể kiểm lại trên hình gốc.
- Nhóm thông tin và nối chi tiết theo logic.
- Tùy hình, xem so sánh, trình tự, vị trí, thành phần hoặc thay đổi.
- Kiểm sự nhất quán của số, đơn vị, mốc, tên và ngôn ngữ mô tả.

### Task 1 General Training — Opening, Body, Closing

- **Opening:** người nhận, mục đích, mức trang trọng.
- **Body:** ghi rõ đoạn đang đáp ứng bullet nào; kiểm cả ý phụ trong bullet; phát triển thông tin, giữ giọng phù hợp. Một đoạn có thể xử lý nhiều bullet; không ép mỗi bullet phải có đúng một đoạn.
- **Closing:** kết thư và hành động tiếp theo phù hợp mục đích/quan hệ.
- **Toàn thư:** kiểm đủ ba bullet của đề thực tế, không chỉ đủ số đoạn. [IELTS: tài nguyên hướng dẫn Writing và phần thư](https://ielts.org/take-a-test/preparation-resources/writing-test-resources).

## 4. Task 1 Academic: tám hồ sơ hình

Các nhóm hình được đối chiếu với [IDP: các dạng Task 1](https://ielts.idp.com/prepare/article-ielts-writing-task-1-question-types) và [IDP: dạng phối hợp](https://ielts.idp.com/prepare/article-common-task-1-types). Hai cột checklist là gợi ý thiết kế bổ sung của Tutor.

| Hồ sơ | Gợi ý kiểm Overview | Gợi ý kiểm Body |
| --- | --- | --- |
| Line graph | Những đường/xu hướng nổi bật nào giúp hiểu hình? | Kiểm mốc đầu/cuối, điểm ngoặt/giao nếu có; tránh biến dao động nhỏ thành xu hướng chung. |
| Bar chart | Khác biệt lớn giữa nhóm; nếu có năm thì nhận định thay đổi có căn cứ. | Đối chiếu cùng đơn vị/thang, phân biệt so nhóm với so thời gian. |
| Pie chart | Cơ cấu chính, tỷ trọng nổi bật. | Phân biệt phần trăm/số lượng; thay đổi tỷ trọng không tự đồng nghĩa thay đổi số lượng tuyệt đối. |
| Table | Mẫu hình hoặc ngoại lệ được chọn có giá trị tổng hợp. | Dùng hàng/cột để phân nhóm, tránh đọc mọi ô; kiểm đơn vị. |
| Mixed/multiple visuals | Có bao quát các hình và quan hệ hợp lệ? | Không bỏ một hình; không suy quan hệ nhân quả chỉ từ hai bộ số. |
| Process/cycle | Phân biệt chuỗi, vòng lặp hoặc nhánh theo hình. | Kiểm thứ tự, đầu vào/đầu ra, biến đổi và tác nhân; không ép cấu trúc trend chart. |
| Map/plan | Thay đổi hoặc cách sắp xếp không gian nổi bật. | Kiểm vị trí, hướng, giữ nguyên/xây mới/dỡ bỏ/chuyển chỗ nếu đề thể hiện. |
| Object/system diagram | Hình cho biết gì về cấu phần hoặc cách hoạt động? | Chỉ ra quan hệ/chức năng có trên hình, không tự thêm số liệu hoặc cơ chế. |

Thời gian tĩnh/động, nhiều mốc hoặc nhiều đơn vị là các yếu tố Tutor kiểm trong đề, không phải quy tắc chia đoạn cứng. Bài có hình khác được xử lý qua mô tả theo đề và checklist tùy chỉnh trong triển khai thật.

## 5. General Training: mức trang trọng × chức năng thư

Prototype có ba hồ sơ **Formal / Semi-formal / Informal**, mỗi hồ sơ có Opening, nhiều Body, Closing và Toàn bài. Mức trang trọng dựa quan hệ người viết–người nhận, không chỉ nhìn tên loại thư.

Thêm tám lựa chọn chức năng để hiện checkpoint ở Body/Toàn bài:

| Chức năng | Điểm cần Tutor đối chiếu với đề |
| --- | --- |
| Complaint | Vấn đề được mô tả cụ thể và mong muốn xử lý; giữ lịch sự. |
| Request | Thông tin/hành động muốn được đáp ứng và lý do cần. |
| Apology/explanation | Giải thích, trách nhiệm và khắc phục nếu đề yêu cầu. |
| Invitation | Mục đích lời mời, thông tin cần thiết và cách phản hồi. |
| Thanks | Sự việc được cảm ơn và ý nghĩa với người viết. |
| Advice/suggestion | Lời đề nghị khả thi, phù hợp quan hệ/tình huống. |
| Application | Mục đích và thông tin phù hợp cơ hội/yêu cầu được nêu. |
| Other/mixed | Tutor ghi yêu cầu kết hợp, không ép vào một chức năng. |

Đây là các bộ lọc nghiệp vụ của phần mềm; ba bullet trong **đề thực tế** vẫn là căn cứ. Không có giả định IELTS chỉ ra đúng tám chức năng thư trên.

## 6. Task 2: mười hồ sơ câu hỏi

Các tên nhóm giúp tổ chức worksheet. Cần đọc mệnh đề yêu cầu vì đề kết hợp không luôn khớp một nhãn. [IELTS: cách hiểu question prompts](https://ielts.org/news-and-insights/ielts-writing-task-2-how-to-understand-ielts-question-prompts), [IDP: question types](https://ielts.idp.com/about/news-and-articles/article-how-to-understand-task-2-writing-questions).

| Hồ sơ | Introduction | Body | Conclusion |
| --- | --- | --- | --- |
| Opinion/agree–disagree | Phạm vi và mức độ đồng ý rõ. | Phát triển lý do cho lập trường; không bắt chia hai phía bằng nhau. | Nhất quán mức độ/quan điểm đã lập luận. |
| Discussion (+ opinion) | Giới thiệu hai quan điểm và phần ý kiến nếu đề hỏi. | Thảo luận thực chất hai phía; quan điểm riêng có căn cứ. | Trả lời đủ phần đánh giá được yêu cầu. |
| Advantages/disadvantages | Nêu đúng vấn đề và hướng xem xét. | Bao quát hai phía theo phạm vi đề. | Tổng hợp; không bắt chọn phía thắng nếu đề không hỏi. |
| Outweigh | Chỉ ra hướng đánh giá tương quan. | Giải thích cơ sở so sánh thay vì đếm số ý. | Có quyết định tương quan được thân bài hỗ trợ. |
| Causes/solutions | Nhận đúng hiện tượng và hai yêu cầu. | Phân biệt nguyên nhân với biểu hiện; giải pháp xử lý được nguyên nhân. | Kết lại mối liên hệ, không thêm giải pháp lớn chưa bàn. |
| Problems/solutions | Xác định vấn đề được hỏi. | Phân tích vấn đề/tác động và cách xử lý tương ứng. | Tổng hợp đáp ứng hai yêu cầu. |
| Causes/effects | Nhận rõ vì sao và hệ quả. | Không thay effects bằng solutions nếu đề không hỏi. | Kết đúng quan hệ đã phân tích. |
| Two-part/direct questions | Xác định câu hỏi A và B. | Trả lời cả hai; không bỏ phần “why/how”. | Đáp lại cả hai phần cần kết luận. |
| Positive/negative development | Đặt hướng đánh giá khi câu hỏi yêu cầu. | Giải thích tác động lên đối tượng cụ thể, có cơ sở. | Nhất quán đánh giá được phát triển. |
| Mixed/custom | Tutor tách các yêu cầu thật. | Soát từng yêu cầu và quan hệ giữa chúng. | Trả lời các điểm chính, không áp một dàn bài có sẵn. |

Không có yêu cầu mọi Introduction phải đúng hai câu, mọi Body phải có ví dụ ở câu thứ ba, hoặc mọi bài chỉ có hai Body. Các kỹ thuật topic sentence/explanation/example có thể dùng để góp ý, không là công thức chấm tự động.

## 7. Trạng thái, minh chứng và dữ liệu

Mỗi checkpoint có năm trạng thái: Chưa xem, Đạt, Một phần, Cần sửa, Không áp dụng. Checkbox là cách nhanh chọn Đạt; bỏ chọn quay lại Chưa xem, **không tự coi là sai**. Dropdown biểu thị các trạng thái còn lại. Không áp dụng không thành điểm 0.

Tutor có thể chọn nhiều đoạn làm minh chứng cho một phần, và một đoạn có thể thuộc nhiều phần. Phân đoạn gợi ý chỉ dựa đoạn văn trong bản nộp; phải có Tutor xác nhận/chỉnh lại. Thêm Body mới không sửa hoặc chèn chữ vào bài học viên. Phần chưa gán đoạn hiển thị “chưa gán”, không khẳng định “học viên thiếu”.

Prototype giữ riêng theo lần nộp, task và hồ sơ; đổi hồ sơ không làm mất checklist hồ sơ cũ. Mỗi item có ID, mã IELTS liên quan, trạng thái; mỗi phần có note, quote và paragraph references; record có `catalogVersion`. Nội dung minh chứng gắn snapshot, không lấy working copy sau nộp. Số “đã xem/tổng checkpoint” là tiến độ thao tác, không phải tỷ lệ band.

Khi công bố, đóng băng bản góp ý từng phần cùng assessment. Học viên chỉ xem phản hồi đã công bố; Tutor sửa nháp mới không làm thay phản hồi cũ. Phiếu LaTeX nhận thêm phần nhận xét/checklist đã đánh giá. PDF/source vẫn là dạng xem của cùng bản nộp; prototype không giả SyncTeX anchor hoặc PDF annotation thật.

## 8. Tổng kết ở khung phải cuối

1. Đọc bản nộp và chọn dạng bài theo đề.
2. Soát Introduction/Overview/Body/Conclusion hoặc Opening/Closing.
3. Ghi nhận xét và câu/đoạn làm minh chứng; các nhãn TA/TR/CC/LR/GRA dẫn về tiêu chí liên quan.
4. Tutor đánh giá band của bốn tiêu chí dựa chất lượng toàn task và band descriptors.
5. Tổng hợp task/đủ hai task như thiết kế IELTS hiện tại. Checklist không sửa các ô band.
6. Lưu nháp hoặc công bố; xuất phiếu LaTeX kèm phản hồi theo phần.

## 9. Phạm vi triển khai hiện tại

Đã thêm vào **prototype độc lập**, chưa phải API/feature production:

- `rubric-catalog.js`: 21 hồ sơ (8 Academic Task 1, 3 GT Task 1, 10 Task 2), 8 chức năng thư, checkpoint theo phần.
- `section-review.js`: khung chat/checklist, gán đoạn, quote, trạng thái, notes, lưu demo, bản công bố và phụ lục phiếu .tex.
- `section-review.css`: bố cục ba khung, cuộn riêng, dark/mobile.
- `test-section-review-design.mjs`: kiểm hồ sơ, gán đoạn, tách checklist khỏi điểm, persistence/publish và responsive.

Sau khi duyệt: chuyển catalog sang schema có version trên backend; lưu `sectionFeedback` theo `submissionId/taskId/assessmentRevisionId`; xác thực quyền Tutor; artifact anchors dùng revision/offset/quote, PDF thêm page/geometry; kiểm optimistic lock, audit, publish transaction, báo cáo và quyền học viên. Không coi localStorage hoặc role switch demo là cơ chế chấm dùng thật.

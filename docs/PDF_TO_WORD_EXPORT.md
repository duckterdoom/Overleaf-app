# Export Word bằng bộ chuyển đổi gốc

Cập nhật 05/11/2026 theo yêu cầu giữ nguyên thiết kế ở `D:\ilovepdf-python`. Triển khai chọn pdf2docx làm mặc định trước đó đã được thay thế.

## Luồng hiện tại

**PDF snapshot đang preview → script convert_pdf2docx.py nguyên bản → DOCX**.

Ba file `convert_pdf2docx.py`, `universal_converter.py`, `build_perfect_docx.py` được sao chép nguyên byte vào `apps/backend/src/latex_backend/pdf_export/original/`, cùng 8 ảnh trong `extracted_figures`. Không sửa logic, thông số font/cột, header/footer, caption hoặc engine của các file này. Bản engine universal đã chỉnh sửa trong lần tích hợp trước đã gỡ khỏi runtime.

Worker gọi `convert_pdf_to_word(...)` **không truyền engine override**, vì vậy giữ thứ tự của bản gốc:

1. Bài mẫu phù hợp → `build_perfect_document` như lệnh chạy với paper_input.pdf.
2. PDF khác → Universal gốc.
3. Universal lỗi → patched pdf2docx gốc.

Không còn biến OVERLEAF_PDF_WORD_ENGINE điều khiển đường xuất. Pandoc/HTML Word draft không tạo file download.

## Nhận diện bài mẫu trong app

App ghi PDF preview vào tên file tạm, nên điều kiện tên paper_input.pdf của script gốc trước đây không được kích hoạt. Wrapper mới đối chiếu fingerprint toàn bộ trang: text, tọa độ block, kích thước trang, digest và vị trí ảnh. Chỉ khi trùng mẫu, worker đặt tên tạm paper_input.pdf rồi gọi nguyên script.

PDF của bài LTJournalArticle hiện tại đã xác nhận trùng fingerprint với bản mẫu trong D:\ilovepdf-python, mặc dù SHA-256 file PDF khác do metadata. PDF đã sửa nội dung/ảnh hoặc tài liệu khác không được gán vào template viết sẵn chỉ vì cùng tên. Script gốc vẫn nguyên trạng, wrapper chỉ khôi phục cách chọn nhánh cho PDF được app đặt tên tự động.

`reference.json` lưu fingerprint mẫu và SHA-256 của ba script/8 ảnh; các file đều được đóng gói trong wheel. Worker làm việc trong thư mục bundle để các đường dẫn extracted_figures tương đối của code gốc hoạt động, ghi DOCX vào temp riêng. Không cần giữ ổ D:\ilovepdf-python khi chạy app trên máy khác. Dependency và launcher vẫn dùng pyproject/uv.lock hiện tại.

## Cách dùng và kiểm soát nguồn

Mở lại app, compile PDF thành công, chọn **Word preview → Word phân trang**. Trình SuperDoc nhận trực tiếp bytes DOCX từ converter gốc, không chuyển qua HTML. Có sửa chữ/định dạng trực tiếp, phân trang, hình ảnh, cột và header/footer; tự lưu sau 1 giây ngừng gõ, nút Lưu Word và Ctrl/Cmd+S. Worker được chép local khi build, telemetry tắt. Không cần Microsoft Word hay dịch vụ cloud để mở preview.

`docx_workspace.py` giữ bản DOCX hiện tại và bản trước trong `.overleaf-docx/`, checksum và revision chống ghi đè từ cửa sổ khác. Mở preview không sửa/normalize bytes gốc; chỉ khi có chỉnh sửa mới gọi export DOCX của editor. Export Word tải đúng bytes bản đã sửa; nếu chưa sửa thì dùng output converter gốc. Các thao tác đổi tab, bố cục, lịch sử và rời dự án lưu trước.

DOCX edits được giữ riêng, không ghi ngược toàn bộ vào LaTeX. Nút **Đồng bộ chữ LaTeX** vẫn có chế độ HTML/mapping trước đây, có giới hạn và cần Pandoc; chế độ đó không phải nguồn DOCX download. Khi source/PDF đổi mà Word đã sửa, draft được giữ, có cảnh báo và chặn export; **Tạo lại từ PDF** có xác nhận và giữ revision trước.

App kiểm PDF current trước/sau conversion và sau dialog, chặn dirty/stale/compile lỗi. Timeout 180 giây, temp/cache/cancel worker giữ như đường xuất trước. Giới hạn bản DOCX chỉnh sửa 32 MiB, tổng nội dung ZIP giải nén 128 MiB. Preview dùng engine DOCX, nên không cam kết ngắt dòng/trang của mọi tài liệu trùng TeX hoặc Microsoft Word. SuperDoc AGPL-3.0 và dependency DOCX Engine có giấy phép riêng đi kèm package.

## Đối chiếu với mã gốc

Đã chạy trực tiếp script ở D:\ilovepdf-python bằng Python của app, sau đó chạy export trong app với PDF tương ứng. Hai DOCX có cùng 29 ZIP parts, **tất cả part đều trùng byte**: document.xml, style, header/footer, ảnh và metadata. Đây là so sánh với output thực của bộ mã gốc, không chỉ đếm số ảnh.

- `target/pdf-word-original-01/original-direct.docx`: chạy trực tiếp script gốc.
- `target/pdf-word-original-01/app-original-converter.docx`: app export, 954.450 bytes, 8 ảnh.
- `target/pdf-word-original-01/comparison.json`: 29 parts, 0 khác biệt.

Kết quả trên chứng minh app giữ đúng output của mã người dùng cho bài mẫu; không suy rộng thành converter chính xác tuyệt đối cho mọi tài liệu. Bài khác tiếp tục dùng đúng engine tổng quát/fallback của bản gốc.

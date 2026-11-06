# Nghiên cứu PDF → DOCX từ D:\ilovepdf-python

Đã sao chép vào repository ngày 05/11/2026, giữ nguyên thư mục gốc. Bốn script Python gốc được giữ để đối chiếu. PDF/DOCX mẫu và extracted_figures cũng có bản sao local tại đây, được ignore khỏi Git vì là dữ liệu/bản build của bài thử. Không sao chép venv, venv_ai hoặc cache Python.

## Phân biệt các bộ chuyển

- `convert_pdf2docx.py`: script gốc, default engine universal, fallback pdf2docx có bản vá thêm khoảng trắng giữa text span.
- `universal_converter.py`: phân tích block bằng PyMuPDF và dựng Word bằng python-docx; nhận diện cột/header/footer theo heuristic. Bản nguyên trạng nằm trong package `overleaf_desktop/pdf_export/original/universal_converter.py`.
- `build_perfect_docx.py`: dựng riêng bài paper_input bằng các đoạn văn và đường dẫn figure viết sẵn. **App giữ nguyên nhánh này cho đúng PDF bài mẫu**; đối chiếu text/layout/ảnh trước khi khôi phục tên tạm paper_input.pdf. Tài liệu khác dùng Universal/fallback gốc.
- `docx_to_pdf.py`: thử render qua Microsoft Word COM trên Windows, không phải dependency hoặc đường xuất của app.

## Bản tích hợp vào app

Source runtime: `apps/desktop/src/overleaf_desktop/pdf_export/`.

Theo yêu cầu chỉnh sửa ngày 05/11/2026, runtime gọi **nguyên ba script** trong `pdf_export/original/`, cùng đủ ảnh mẫu, không sửa mã gốc hoặc ghi đè engine. Giữ builder mẫu → Universal → patched pdf2docx theo dispatcher gốc. Bản tích hợp pdf2docx mặc định trước đây đã được thay; biến OVERLEAF_PDF_WORD_ENGINE không còn điều khiển đường xuất. Không fallback sang HTML/Pandoc.

Các dependency cần thiết đã khai báo trong `apps/desktop/pyproject.toml`, khóa cả transitive dependency trong `uv.lock`: pdf2docx, PyMuPDF, python-docx; các gói kéo theo gồm fonttools, numpy, opencv-python-headless, lxml, fire và termcolor. Dùng đúng venv của app; venv cũ dùng Python khác phiên bản nên không chuyển thư mục site-packages/exe sang môi trường mới.

```powershell
uv sync --project apps/desktop --extra desktop --extra dev --extra mongo --frozen
scripts\run-python-desktop.bat --build
scripts\run-python-desktop.bat
```

Launcher desktop cũng kiểm các import mới và tự cài dependency nếu thiếu. Mã chuyển đổi ở trong package của dự án, không cần ổ D:\ilovepdf-python khi chạy hoặc đóng gói wheel. Xem `docs/PDF_TO_WORD_EXPORT.md` cho luồng xuất và kiểm chứng. Các thư viện AI trong venv_ai không được script runtime này sử dụng nên không cài theo.

Các tên “perfect/100%” trong script nghiên cứu gốc được giữ nguyên để đối chiếu, không phải cam kết chất lượng của app với mọi PDF. Font máy đích, công thức, PDF scan, bảng phức tạp và engine Word có thể làm thay đổi kết quả; pdf2docx không tự OCR PDF scan.

# PDF, SyncTeX và hiệu năng

Cập nhật **02/11/2026**, Windows, Microsoft JDK 21.0.13, TinyTeX portable của máy kiểm thử. Đây là cải thiện tiêu chí 4; chưa benchmark đối chứng với Overleaf Cloud.

## Cách sử dụng

1. Đóng app cũ và chạy `scripts/run-client.bat`, mở dự án rồi **Recompile** để tạo PDF và index mới.
2. Đặt con trỏ trong `main.tex` hoặc file chương, bấm **Đến PDF / Show in PDF** ở khu vực preview. Viewer chuyển đến trang/vị trí tương ứng và tô vàng. Nút Back của viewer quay lại vị trí trước.
3. **Nhấp đúp vào đoạn văn trong PDF** để mở file nguồn và đặt con trỏ tại dòng tương ứng. Nếu đang PDF only, app chuyển về Split view; nếu PDF ở cửa sổ riêng, cửa sổ editor được đưa lên trước.

Nguồn đã sửa, đang compile hoặc compile lỗi: SyncTeX và export không được dùng với PDF cũ. Liên kết mục lục vẫn là thao tác bấm một lần riêng; nó không phải SyncTeX. Độ chính xác phụ thuộc dữ liệu TeX: nhiều công thức, float, macro có thể trỏ đến dòng gần nhất hoặc nơi khai báo, không bảo đảm chính xác từng ký tự.

Cần công cụ **synctex** trong cùng thư mục bin với latexmk đã chọn. Nếu thiếu, PDF preview/export vẫn dùng được; thao tác SyncTeX báo cần kiểm tra toolchain. Phiên này đã cài package synctex vào **target/TinyTeX của workspace** để kiểm thử; không sửa TeX hệ thống, không tự cài package trên các máy khác. TinyTeX/toolchain trong target bị Git ignore và không đi kèm commit code. Trên máy khác, chuẩn bị toolchain có SyncTeX rồi chọn trong Settings → Compilation.

## Thay đổi kỹ thuật

- Compiler thêm `-synctex=1`. Thành công sẽ công bố `.synctex.gz` cùng PDF trước callback; thiếu index mới sẽ bỏ index cũ.
- CompileWorkspace chuẩn hóa Input của source thành đường dẫn tương đối và newline LF. Nhờ vậy index còn dùng được sau khi xóa staging ASCII của build trong đường dẫn tiếng Việt trên Windows.
- SyncTexService giữ index của đúng lần compile, query CLI `view`/`edit` trên luồng nền, timeout 10 giây. Chỉ copy index vào scratch ASCII, không copy PDF lớn mỗi lần click. Không dùng `-x` và gỡ SYNCTEX_VIEWER/SYNCTEX_EDITOR khỏi môi trường process; không mở editor ngoài. Đích phải là `.tex` thuộc project, kiểm đường dẫn thật chống đi ra ngoài qua symlink.
- Query kiểm fingerprint nguồn; callback bị bỏ khi đổi dự án, file, revision hoặc PDF. Dữ liệu của project cũ không được dùng để điều hướng project mới.
- Tọa độ được đổi giữa điểm SyncTeX và crop/rotation của viewer; unit test có 0/90/180/270 độ và MediaBox/CropBox lệch gốc.
- **Tái sử dụng PDDocument/PDFRenderer trong một render session** trên luồng pdf-render. Bản trước mở/parse toàn PDF cho từng trang. Khi đổi/reset/đóng viewer, session được đóng trên cùng luồng render; kết quả cũ vẫn có generation guard.
- Giữ lazy rendering và ngân sách **24 triệu pixel ảnh trang được giữ**, mỗi ảnh render tối đa 12 triệu pixel. Không giữ raster của toàn tài liệu. Con số này không phải giới hạn tổng heap/native/GPU/PDFBox resource cache.
- Export vẫn từ bytes bất biến của PdfSnapshot; marker SyncTeX chỉ là overlay UI và không xuất vào PDF.

## Benchmark tài liệu lớn

Dataset được tạo cố định bởi `PdfPerformanceSmoke`: **120 file chương, 12 PNG 800×450, bảng, TikZ, hyperref, 248 trang**, PDF **11.708.355 byte**. Nguồn ASCII để đo cache độc lập với workaround Unicode; Unicode được kiểm riêng bằng native/UI smoke.

Chạy một lần cold, 5 lần không sửa, 5 lần sửa nội dung nhỏ ở chương cuối. So hai chiến lược render trên cùng 24 trang/scale 1.5, luân phiên thứ tự; so kích thước và pixel lấy mẫu để tránh đánh đổi kết quả. UI điều hướng 18 lượt, chờ trang hiện tại có ảnh, sau đó đợi render ổn định để đếm ảnh giữ lại. p50/p95 dùng nearest rank.

| Phép đo | Số mẫu | p50 (ms) | p95 (ms) |
| --- | ---: | ---: | ---: |
| Compile không sửa | 5 | 267 | 289 |
| Compile sửa nhỏ | 5 | 4.794 | 4.938 |
| Render mở lại document mỗi trang | 24 | 26 | 139 |
| Render tái sử dụng document/renderer | 24 | 12 | 85 |
| Điều hướng UI → trang có ảnh | 18 | 22 | 68 |
| Một lượt qua hàng đợi FX | 18 | 0 | 12 |

- Cold compile: **7.855 ms**, n=1. Đọc metadata PDF: **364 ms**. Từ lúc đưa snapshot đã đọc vào viewer đến trang đầu có ảnh: **368 ms**; không bao gồm compile/đọc metadata.
- Viewer mở document **1 lần**, giữ tối đa **3 ảnh trang / 3.271.752 pixel** tại các điểm đo đã đợi render ổn định; assert không vượt 24 triệu pixel.
- SyncTeX tới chương cuối của PDF lớn và chiều ngược lại đạt. Export giống từng byte với PDF compiler.
- Bằng chứng gốc: `target/pdf-performance-02/results.md`, log `target/pdf-performance.log`. Generator/nguồn/ảnh/PDF nằm trong home này, không được Git theo dõi.

Các số đo chỉ mô tả workload trên máy này: cold n=1, số mẫu nhỏ, OS/JIT/cache có ảnh hưởng; không phải cam kết p95 cho mọi tài liệu. Lượt FX queue không phải đo liên tục frame time. Chưa đo RSS/native/GPU, chưa stress nghìn trang, chưa kiểm native macOS/Linux hoặc cùng nguồn trên Overleaf web. Điểm đã chứng minh là tái sử dụng renderer nhanh hơn trong mẫu đo và giữ đúng output, không phải app nhanh hơn web.

## Kiểm thử và chạy lại

- **148/148 JUnit chọn lọc đạt**, common 9 + server 21 + client 118. Có regression compile flag, sidecar Unicode/LF, bỏ index cũ, parser kết quả, crop/rotation và các kiểm tra PDF/export cũ. Lệnh chọn bộ test trong README; không cần MongoDB/TeX thật cho JUnit.
- **SyncTexSmoke: 6/6 build native** — 3 engine × root ASCII/tiếng Việt; forward/inverse tới đúng file chương có dấu cách, đúng dòng, chặn source ra ngoài project. Home `target/synctex-native-03`, log `target/synctex-native.log`.
- **SyncTexUiSmoke** — nút forward, nhấp đúp PDF, mở đúng chapter/dòng 4, PDF only về split, stale chặn; ảnh `target/synctex-ui-03/synctex-preview.png`, log `target/synctex-ui.log`.
- Hồi quy **PdfPreviewUiSmoke**: PDF 7 trang, pixel render, export cùng bytes, stale/error/recovery/project isolation đạt; log `target/pdf-sync-export-01.log`.
- Hồi quy **PdfContinuousUiSmoke**: PDF 13 trang, scroll/link/Back/zoom/reset và ảnh trang giới hạn đạt; log `target/pdf-sync-continuous-01.log`.

Ba main explicit mới ở `modules/client/src/test/java`: `com.overleaf.client.compiler.SyncTexSmoke`, `com.overleaf.client.ui.SyncTexUiSmoke`, `com.overleaf.client.ui.PdfPerformanceSmoke`. Tham số: `<output-directory-chưa-tồn-tại> <absolute-latexmk-path>`. Build test classes trước; chạy bằng cấu hình IDE với test runtime classpath. Hai main UI cần JavaFX controls/fxml. Đây là smoke explicit, không tự chạy bởi Surefire. Helper `.cmd` trong target dùng classpath/cache trên máy hiện tại; không phải script portable của sản phẩm.

Nguồn tham chiếu giao thức CLI: `synctex help view` và `synctex help edit` của toolchain thực tế; tên trường/vị trí dùng output mặc định, không sinh command editor/viewer từ output.

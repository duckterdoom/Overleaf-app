# Compile và PDF → Word: hiệu năng và chạy trên server

## Những gì đã tối ưu

Compile giữ `latexmk` và các engine hiện tại, không giảm số lượt LaTeX cần để cập nhật mục lục/trích dẫn. Lượt đầu và lượt có sửa nguồn vẫn dùng pipeline thật. Khi không đổi nguồn, engine, executable và các input trong recorder `.fls`, backend có thể dùng lại PDF/SyncTeX/geometry/log của snapshot thành công trong cùng phiên. Log cũ được phát lại để không giấu cảnh báo. Thiếu recorder, dependency bị sửa/xóa, marker compile lỗi, nguồn dirty, đổi engine hoặc yêu cầu force sẽ bỏ qua đường cache.

API compile nhận thêm `force: true` để chạy lại với `latexmk -g`. Trong UI có thể dùng **Clean build cache** rồi compile; thao tác clean xóa `.fls`, nên không dùng fast cache. Cache compile này chưa được khôi phục qua restart, nhưng thư mục build vẫn giữ aux/fdb để latexmk làm incremental build bình thường. Các đường dẫn staging ASCII trên Windows có thể không dùng được fast cache sau khi temp bị dọn; khi đó tự chạy latexmk.

Word vẫn chạy nguyên bộ converter gốc trong `pdf_export/original`, không đổi universal engine, specialized builder hoặc patched fallback. Cache có khóa SHA-256 của **PDF bytes + phiên bản/bundle converter**, không gắn với snapshot ID. Cache riêng trong từng project `.overleaf-pdf-word-cache`, gồm DOCX và checksum metadata. Cache có thể dùng sau restart hoặc snapshot mới nếu PDF bytes thật sự giống nhau; không dùng semantic signature để bỏ qua khác biệt hình vẽ.

Mỗi project giữ tối đa 4 DOCX, giới hạn tổng 256 MiB; dữ liệu/cache lỗi được bỏ qua và convert lại. Cache không thay bản Word đã chỉnh sửa trong workspace. Nhiều request Word preview/download dùng cùng exporter sẽ đợi và dùng lại kết quả, thay vì convert trùng hoặc trả 409. Nguồn stale vẫn bị chặn trước và sau cache/convert; DOCX lấy từ cache giống từng byte với kết quả converter gốc.

Sau khi cập nhật bộ converter hoặc thư viện, cần restart backend để tính lại converter signature. Xóa cache ẩn nếu cần giải phóng đĩa; đây không phải file Word người dùng đã sửa ở `.overleaf-docx`.

## Số đo trên máy phát triển

Fixture **4 trang**, có chữ, công thức, bảng và PNG; TeX và converter thật, Store local, không có độ trễ MongoDB Atlas. Báo cáo cuối: `target/build-word-performance-20261111-final/benchmark.json`.

| Tình huống | Thời gian |
| --- | ---: |
| Compile đầu, chưa có aux | 572 ms |
| Không đổi nguồn, vẫn khởi chạy latexmk như đường cũ | 234 ms |
| Không đổi nguồn, fast snapshot cache | 8 / 8 / 8 ms |
| Có sửa chữ, latexmk incremental thật | 365 ms |
| Word convert lần đầu | 626 ms |
| Word RAM cache | 3 ms |
| Word disk cache, exporter mới | 4 ms |

Đây là một fixture/lượt đo để kiểm chứng cache, không phải SLA hay kết luận tài liệu lớn cũng nhanh như vậy. Chưa chứng minh tăng tốc cold compile hoặc cold convert cho mọi tài liệu. Máy server, số trang/TikZ/ảnh/font, I/O và RTT Atlas ảnh hưởng thời gian thực tế.

Chạy lại benchmark bằng Python environment đã cài dependencies; dùng **thư mục chưa tồn tại**:

```powershell
.\target\official-python-web-env\Scripts\python.exe apps/backend/tests/benchmark_build_word.py target/benchmark-build-word-new
```

## Điều phối CPU khi lên server

Có giới hạn chung theo process, dùng cho mọi account workspace:

```powershell
$env:LATEX_MAX_COMPILE_JOBS = '2'
$env:LATEX_MAX_WORD_JOBS = '1'
.\scripts\run-python-web.bat
```

Compile đợi slot và vẫn hủy được khi đang đợi; timeout compile hiện tại áp dụng lúc thực thi TeX. Word đợi slot tối đa 180 giây rồi trả lỗi busy, converter chạy tối đa 180 giây. Khi source đổi trong lúc đợi, kết quả cũ không được giao cho người dùng. Có thể cấu hình mỗi giá trị từ 1–16, phải chọn theo CPU/RAM thực tế; tăng số job không làm một tài liệu tự chạy nhanh hơn và có thể gây tranh tài nguyên.

Giới hạn cũ một lượt compile đang hoạt động mỗi account workspace vẫn được giữ. Giới hạn chung điều phối tổng số job giữa các account; không phải hàng đợi Redis bền vững hoặc cam kết FIFO.

Các semaphore/cache snapshot/single-flight hiện là **trong một process**. Chạy nhiều Uvicorn worker không tự tạo queue dùng chung; ngoài ra OT/presence hiện cũng chưa hỗ trợ nhiều worker. Để mở rộng sau này cần queue chung (ví dụ Redis + worker chuyên compile/convert), lưu artifact chung và job ID thay cho trạng thái RAM. Không chỉ tăng `--workers` rồi coi là đã scale.

Launcher/gateway hiện vẫn là local web loopback, chưa được chuyển thành cấu hình public production. Khi nhận LaTeX từ người dùng Internet, worker TeX/converter cần môi trường cách ly với giới hạn CPU/RAM/disk và không chứa secrets của API; phần triển khai đó chưa được làm trong lần tối ưu này. Không chỉnh deployment TCP/TLS cũ để giả làm web deployment.

## Kiểm tra

Tests có cache invalidation theo source/dependency/force/clean, replay cảnh báo, duplicate Word requests chỉ chạy converter một lần, disk cache sau snapshot ID mới, checksum/corrupt cache, stale guard và hủy compile đang đợi. Tests manifest xác nhận converter gốc không đổi.

Tài liệu kỹ thuật đối chiếu: [latexmk và recorder/dependencies](https://tug.ctan.org/support/latexmk/latexmk.pdf), [pdf2docx conversion/multiprocessing](https://pdf2docx.readthedocs.io/en/latest/quickstart.convert.html). Multiprocessing của pdf2docx không được bật ép vào universal converter của dự án vì đây là các pipeline khác nhau và cần kiểm chứng layout riêng.

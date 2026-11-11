# Nhiều máy chạy Latex cùng database Atlas

Áp dụng từ 11/11/2026. Mỗi máy chạy một backend và thư mục làm việc riêng, cùng kết nối một database MongoDB. Không cần `--rebind-home` để đổi qua lại giữa Admin và Minh Ngoc.

## Nơi lưu dữ liệu

- MongoDB giữ tài khoản, phiên, quyền, metadata, review và dữ liệu IELTS như trước.
- `project_files`: manifest theo project/owner, phiên bản, đường dẫn, SHA-256 và kích thước.
- `project_blobs` / `project_chunks`: nội dung tệp bất biến, chunk 512 KiB. Hỗ trợ source, ảnh, bibliography/class/style, thư mục con, history payload và Word draft.
- Backend tự tải working copy để compile. PDF build, cache TeX và cache chuyển Word được tạo riêng trên từng máy.
- `.overleaf-cloud.json` là mốc phiên bản local; không sửa tay.

Không upload build, `.git`, `.env`, `.venv`, node_modules, credential Google/MongoDB hay conflict backups. Giới hạn 100 MiB/tệp, 500 MiB/dự án, manifest 10 MiB. Chunk cũ được giữ, chưa có tự động garbage collection; theo dõi dung lượng Atlas khi test nhiều lần.

`app_config.storage_home` cũ được giữ làm descriptor volume ban đầu. Mỗi backend thêm `storage_node:<hash>` riêng thay vì tranh nhau một path. Không tách database hay namespace dev.

## Máy mới clone

Nhận code mới và file cấu hình riêng của nhóm, sau đó:

```powershell
.\scripts\run-python-web.bat --build
.\scripts\run-python-web.bat
```

Đăng nhập bằng tài khoản có quyền. Source được tải từ DB khi mở dự án; không cần copy đường dẫn C:\Users\Admin sang máy khác. Chủ dự án dùng nút Chia sẻ như trước; đưa file lên DB không tự chuyển private thành public.

Lưu/upload/rename/delete qua app publish lên DB trước khi trả thành công. Backend khác kiểm tra source khoảng mỗi giây rồi đưa thay đổi vào OT của CodeMirror. Đã kiểm hai backend với sửa hai chiều/đồng thời. Con trỏ/avatar/presence và chat hiện vẫn thuộc room của từng backend; chưa có presence/chat phân tán hoàn chỉnh.

Mỗi process phải có working home riêng; không chạy nhiều uvicorn workers ghi cùng home. Máy compile vẫn cần TeX. Không bỏ HTTPS/host restrictions để public app.

## Đẩy dự án có sẵn

Trên máy có source đầy đủ, lệnh đầu chỉ kiểm tra, lệnh sau publish và đọc lại chunk để kiểm checksum:

```powershell
.\scripts\publish-shared-projects.bat
.\scripts\publish-shared-projects.bat --apply
```

Đã thực hiện với dữ liệu Admin ngày 11/11/2026: **7 dự án, 198 tệp, 21.224.291 bytes**, checksum đạt. Tài khoản/quyền không đổi. Các dev không phải publish lại khi clone mới, nhưng phải cập nhật code và restart để sửa đổi mới được lưu vào shared storage.

## Xung đột và khôi phục

Manifest dùng compare-and-swap. Thay đổi trên các tệp khác nhau có thể gộp; cùng tệp đã đổi so với baseline thì từ chối ghi đè. WebSocket giữ bản chưa xác nhận để rebase/retry. Bản sửa HTTP/local ngoài app không gộp được sẽ trả 409 và được giữ tại `.overleaf-cloud-conflicts/<id>/` trong project.

Nếu máy đã có bản local khác DB, cần đối chiếu. Khi đã chọn dùng bản DB, dừng backend của chính máy đó rồi chạy:

```powershell
.\scripts\publish-shared-projects.bat --download
.\scripts\publish-shared-projects.bat --download --apply
```

Lệnh apply tải DB về local, giữ khác biệt local trong backup, không sửa source trên DB. Không xóa backup khi chưa lấy lại phần cần giữ. Sửa bằng IDE ngoài app chưa phải thay đổi đã xác nhận trên DB.

## Bằng chứng kiểm thử

- Tests backend: hai home, cùng database/accounts; source/binary/history; rename/delete; stale CAS; checksum/path/ACL; giữ draft và phục hồi local.
- Atlas thật: `target/presence-atlas-8e9efbc5481c306e/report.json` đạt hai process/home riêng, Edge/Chrome, sửa hai chiều/đồng thời, compile hai máy giả lập và reload. Namespace test đã dọn sạch, không dùng tài khoản thật.
- Data thật: `target/shared-cloud-verification-20261111/startup-report.json`: HTTP 200, Google cấu hình được, 7 manifests, primary binding không đổi.
- `target/shared-cloud-word-regression-20261111`: PDF/export, Word → LaTeX → PDF, DOCX export, upload/ZIP đạt.

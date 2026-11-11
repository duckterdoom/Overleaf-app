# Windows chặn Python khi khởi động Latex

## Dùng Python chính thức đã cài (đã kiểm tra với 3.15.2)

Không cần cài thêm phiên bản Python khi máy đã có Python tương thích. Ngày 09/11/2026 đã kiểm tra Python 3.15.2 chính thức, executable và launcher venv có chữ ký hợp lệ, chạy được theo chính sách Windows hiện tại. Dùng môi trường riêng; không sửa chính sách bảo mật hoặc môi trường cũ:

```powershell
.\scripts\setup-installed-python-web.ps1
.\scripts\run-python-web.bat
```

Script setup dùng `py -3` trên máy và tạo `target/official-python-web-env`; chỉ cài thư viện dự án, không tải interpreter khác. Launcher web ưu tiên môi trường này khi tồn tại. Nếu Python hoặc dependency vẫn bị Windows chặn, phải xem lại chính sách thiết bị, không mặc định rằng runtime khác sẽ được phép.

Nhóm `web-local` dùng Uvicorn core (HTTP h11), WebSocket và MongoDB client, không kéo các tùy chọn YAML/reload của `uvicorn[standard]`. PyYAML hiện không có wheel cho Python 3.15 trong nguồn pip đã kiểm tra; build source thất bại vì Windows chặn extension Cython. Ứng dụng local không sử dụng YAML config hoặc server auto-reload nên không cần các dependency này. Auto Compile của tài liệu vẫn là tính năng riêng của ứng dụng. Nhóm `web` cũ giữ nguyên cho môi trường khác.

Setup qua pip dùng ràng buộc pyproject, không bảo đảm tái tạo chính xác uv.lock; kiểm tra `pip check` sau cài. Lock đã bổ sung nhóm `web-local`. Môi trường local hiện tại đã cài sẵn, chỉ cần chạy `scripts\run-python-web.bat`.

## Lỗi Application Control

Thông báo `An Application Control policy has blocked this file` có nghĩa Windows từ chối chạy một file theo chính sách bảo mật. Lỗi xuất hiện trước khi backend hoặc frontend chạy; build lại Next.js không khắc phục được lỗi này.

Ngày 09/11/2026 trên máy hiện tại, nhật ký `Microsoft-Windows-CodeIntegrity/Operational` ghi Event 3077 cho `D:\Overleaf-app\apps\backend\.venv\Scripts\python.exe`. Kiểm tra Authenticode trả `NotSigned`. Trạng thái không có chữ ký tự nó chưa chứng minh chính sách cụ thể nào đã chặn; cần đọc sự kiện và chính sách trên máy để quyết định cách cấp phép.

## Cách xử lý

1. Mở Event Viewer → Applications and Services Logs → Microsoft → Windows → CodeIntegrity → Operational. Tìm Event 3077 tại thời điểm chạy launcher, ghi lại file bị chặn và Policy ID. Các file DLL khác cũng có thể bị chặn sau khi Python được cho phép.
2. Nhờ quản trị viên máy kiểm tra và cấp phép đúng Python runtime/venv cùng dependency cần thiết theo chính sách của thiết bị. Nếu phải thay runtime, dùng bản phân phối Python 3.12+ đã được quản trị viên chấp thuận và tạo lại môi trường dependency theo lock của dự án. Không chỉ cho phép Python hệ thống rồi mặc định rằng executable trong venv cũng được cho phép.
3. Khi môi trường đã được cấp phép, chạy lại tại thư mục dự án:

   ```powershell
   .\scripts\run-python-web.bat --build
   .\scripts\run-python-web.bat
   ```

4. Mở `http://127.0.0.1:8000/` sau khi server báo khởi động thành công.

Launcher dừng khi phát hiện lỗi chính sách. Không tắt App Control, không đổi tên/copy executable để tránh chặn. `ExecutionPolicy Bypass` của PowerShell không cấp quyền chạy executable bị App Control chặn. Dự án và dữ liệu bài viết không cần xóa để xử lý lỗi này.

Nguồn: [Microsoft — App Control debugging and troubleshooting](https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/operations/appcontrol-debugging-and-troubleshooting).

# Đăng nhập web và chọn vai trò (10/11/2026)

Cập nhật MongoDB: code web hiện dùng MongoDB làm nguồn chính cho identity/session/ACL và metadata. Mô tả SQLite bên dưới là mốc cũ, chỉ dùng làm nguồn migration. Xem [MONGODB_PRIMARY.md](MONGODB_PRIMARY.md); không tự fallback khi thiếu URI. Google ID, role và session cũ được giữ khi migration hoàn tất.

Cập nhật cùng ngày: dự án đã có ACL owner/editor/viewer, lời mời private và link sharing sau đăng nhập; xem [WEB_PROJECT_SHARING.md](WEB_PROJECT_SHARING.md). Các phần nói chia sẻ project sẽ làm sau trong tài liệu này là mốc trước cập nhật đó. Chưa có lớp học/LMS hoặc đồng bộ gõ realtime.

Luồng đang triển khai trong app chính Python + Next.js, không phải prototype HTML.

## Luồng sử dụng

1. Người dùng đăng ký bằng tên, email và mật khẩu ít nhất 8 ký tự, hoặc đăng nhập Google khi đã cấu hình.
2. Tài khoản mới vào màn hoàn thiện tên và chọn **Student — Học viên** hoặc **Tutor — Giảng viên**. Không chọn sẵn vai trò và không cho vào workspace trước khi hoàn tất.
3. Lựa chọn lưu vào tài khoản. Đóng tab, đăng xuất hoặc khởi động lại server trước khi chọn thì đăng nhập lần sau tiếp tục bước này.
4. Sau khi chọn, mở không gian học viên hoặc Tutor; đăng nhập lại đi thẳng đến không gian đã chọn. Cả hai vẫn sử dụng trình soạn thảo LaTeX, PDF và Word hiện có.
5. Vai trò chính chưa cho đổi sau khi hoàn tất. Cơ chế nhiều vai trò/chuyển không gian là phần mở rộng sau này. Tutor tự đăng ký được nhưng không có quyền Admin hoặc quyền đọc bài của người khác.

Màn đầu theo vai trò hiện là không gian dự án cá nhân với tiêu đề tương ứng. Quản lý lớp, giao/nộp bài và chia sẻ bài giữa Tutor/Student chưa được xây thành dịch vụ LMS trong thay đổi này; không hiển thị dữ liệu lớp hay bài nộp giả.

## Chạy local

```powershell
.\scripts\run-python-web.bat --build
.\scripts\run-python-web.bat
```

Nếu server cũ vẫn đang chạy, dừng bằng Ctrl+C ở terminal đó rồi chạy lại để nạp backend mới. Tải lại trình duyệt sau build. Chỉ chạy một phiên trên cùng cổng/home. Môi trường Python chính thức 3.15 hiện tại được giữ nguyên; không cần cài thêm phiên bản Python hay một dịch vụ database mới.

## Cấu hình Google

Tạo OAuth client loại **Web application** trong dự án Google của bạn; cấu hình màn consent với các scope `openid email profile`. Khi ở chế độ thử nghiệm, thêm tài khoản thử nghiệm nếu cấu hình audience yêu cầu.

Đăng ký Authorized redirect URI chính xác:

```text
http://127.0.0.1:8000/api/auth/google/callback
```

Đặt biến môi trường trong terminal sẽ khởi động backend:

```powershell
$env:LATEX_GOOGLE_CLIENT_ID = 'YOUR_WEB_CLIENT_ID'
$env:LATEX_GOOGLE_CLIENT_SECRET = 'YOUR_CLIENT_SECRET'
$env:LATEX_PUBLIC_URL = 'http://127.0.0.1:8000'
.\scripts\run-python-web.bat
```

`localhost` và `127.0.0.1` là hai origin khác nhau. Dùng nhất quán địa chỉ mở app, public URL và callback. Nếu đổi cổng, phải cập nhật cả ba và cấu hình Google. Không đặt secret trong `NEXT_PUBLIC_*`, mã frontend, Git hoặc nội dung chat. Chưa cấu hình đủ hai biến credential thì nút Google bị vô hiệu hóa và có giải thích; đăng ký bằng email vẫn hoạt động.

Flow sử dụng authorization code + PKCE S256, state gắn với cookie trình duyệt và nonce. Backend đổi code, thư viện Google xác minh chữ ký/issuer/audience/expiry ID token rồi kiểm nonce và email_verified. Định danh Google dùng `sub`, không dùng email. Không lưu Google access/refresh token; không yêu cầu Drive/Gmail. Email trùng tài khoản mật khẩu sẽ yêu cầu đăng nhập bằng mật khẩu, không tự động liên kết. Luồng liên kết chủ động chưa được triển khai.

Callback lỗi/hủy quay về trang đăng nhập với thông báo; flow hết hạn sau 10 phút và dùng một lần. Callback Google được phép điều hướng cross-site vì đã kiểm state/cookie/nonce; các API còn lại vẫn kiểm Host/Origin.

## Lưu dữ liệu và bảo vệ tài khoản

- `auth/accounts.py`: repository người dùng và phiên trong `<home>/.auth/accounts.sqlite3` bằng SQLite có sẵn trong Python. Đây chưa phải MongoDB. SQLite được chọn để chạy ngay trên môi trường local hiện tại, không yêu cầu cài database server. MongoDB repository cũ chưa tự động trở thành nguồn tài khoản.
- `http/web_accounts.py`: gateway định danh trình duyệt và định tuyến đến Store/Compiler của đúng tài khoản.
- `<home>/users/<user-id>/`: settings/history/avatar và dự án riêng; project ID của người khác trả 404. Compiler/PDF/Word/upload hoạt động trong cùng vùng dữ liệu riêng này.
- Cookie `latex_account` HttpOnly, SameSite=Lax, path=/api; Secure khi dùng HTTPS. Token ngẫu nhiên, database chỉ lưu SHA-256 token. Hết hạn 12 giờ, hoặc 30 ngày nếu chọn duy trì đăng nhập; cookie không duy trì bị bỏ khi kết thúc browser session.
- Tạo session mới khi đăng nhập; đăng xuất thu hồi token phía server. Đổi mật khẩu thu hồi các phiên cũ và cấp lại phiên hiện tại. Không dùng bootstrap cookie local làm danh tính người dùng.
- Mật khẩu lưu PBKDF2 theo bộ kiểm tra hiện có, không plaintext. Rate limit trong process cho đăng nhập/đăng ký và giới hạn body auth 16 KiB. Chính sách password reset/email verification của tài khoản mật khẩu chưa được triển khai.
- Quyền role chỉ nhận `student`/`tutor`, không chấp nhận `admin`. Vai trò không thay thế quyền sở hữu/chia sẻ dự án. Các dịch vụ nhóm/lớp và thành viên dự án sẽ cần cơ chế cấp quyền riêng trước khi mở cộng tác nhiều người qua web.

## Dữ liệu cũ

Lần đầu khởi tạo, import credential tài khoản cũ từ `<home>/.app/state.json` vào repository mới, không import phiên remembered chung. Tài khoản cũ phải đăng nhập lại và chọn vai trò.

**Dự án chung cũ ở `<home>/projects` được giữ nguyên nhưng không tự gán cho tài khoản đăng ký đầu tiên**, để tránh lộ dữ liệu cho sai người. Do đó danh sách dự án cá nhân mới có thể trống. Người vận hành cần xác định chủ sở hữu rồi nhập ZIP qua giao diện tài khoản đúng. Không xóa vùng dữ liệu cũ khi chưa chuyển và đối chiếu xong. Desktop tiếp tục dùng cơ chế cũ; không thay đổi tài khoản desktop bằng gateway này.

## Kiểm chứng và giới hạn triển khai

Backend kiểm thử role/tiếp tục onboarding, session persistence/revoke/expiry, tách dữ liệu người dùng, profile password, OAuth state/replay và danh tính Google bằng mô phỏng. Browser kiểm thử đăng ký thật, hai context độc lập, reload, Student/Tutor, mobile và Google chưa cấu hình/lỗi. Bộ regression local web kiểm PDF/Word/upload/ZIP chạy với tài khoản riêng.

Google chưa được đăng nhập end-to-end bằng tài khoản thật vì chưa có OAuth client của người vận hành. Không coi test mô phỏng là xác nhận consent/credential trên Google Console.

Launcher vẫn chỉ bind localhost và kiểm Host local. Chưa chuyển thành dịch vụ public/multi-worker; cần cấu hình trusted host/proxy, HTTPS, deployment secrets, tài nguyên compile và storage/permissions phục vụ nhiều người trước khi publish. Không chỉ đổi host thành `0.0.0.0` rồi đưa lên Internet.

Nguồn: [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect), [Google ID token verification](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

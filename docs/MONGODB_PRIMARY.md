# MongoDB là nguồn dữ liệu chính cho web Latex

**Cập nhật 11/11/2026 — nhiều backend dùng chung database:** source, ảnh, history payload và Word draft hiện có shared manifests/chunks trong MongoDB; local volume là working copy để compile. Các node đăng ký home riêng, không đổi binding của nhau. Đã publish 7 dự án/198 tệp từ Admin và verify checksum. Xem [SHARED_MONGODB_FILES.md](SHARED_MONGODB_FILES.md). Các đoạn mô tả file chỉ ở local và chuyển binding độc quyền phía dưới là kiến trúc lịch sử trước cập nhật này. `--rebind-home` không còn là cách cho hai máy chạy song song; không chạy lệnh đó chỉ vì username Windows khác.

**Đã cutover trên Atlas ngày 10/11/2026:** database `latex` giữ 3 tài khoản, 6 dự án, 28 bản lịch sử; migration complete, nguồn SQLite/JSON không đổi và có backup. Kiểm thử browser dùng PyMongo/Atlas thật với các collection tạm được namespace/guard trong cùng database, đã dọn sạch sau khi đạt các luồng accounts/sharing/PDF/Word/upload/ZIP. Chi tiết vận hành và artifact ở AI_Handoff mục 75. Không coi các kết quả mock của giai đoạn trước là bằng chứng live này.

Backend web dùng **PyMongo + MongoDB** cho tài khoản, phiên, quyền chia sẻ, metadata dự án, cài đặt/hoạt động, metadata history, review và dữ liệu chấm IELTS. Không có fallback tự động về SQLite/JSON khi thiếu cấu hình hoặc mất kết nối.

File source `.tex`/`.bib`, ảnh, PDF/DOCX, nội dung revision và cache compile vẫn nằm trong server volume. MongoDB lưu thông tin quản lý/tham chiếu. Backup phải gồm cả database và server volume; backup chỉ database không chứa các file này.

## Atlas ở local và khi đưa app lên server

Chạy backend local vẫn kết nối Atlas và xem dữ liệu bằng **Atlas Data Explorer / Browse Collections** hoặc MongoDB Compass. Khi chuyển backend sang server riêng, có thể tiếp tục dùng cùng Atlas; chỉ thay địa chỉ triển khai web, IP access list, cấu hình OAuth và chuyển file volume. Nếu chuyển cả database về MongoDB self-host thì dùng Compass/công cụ quản trị server để theo dõi; Atlas Data Explorer không quản lý database self-host ngoài Atlas.

## Kết nối

File local riêng: `config/mongodb.local.json`, đã gitignore. Copy từ file example nếu clone mới:

```powershell
Copy-Item config/mongodb.local.example.json config/mongodb.local.json
```

Chỉ copy khi file local chưa có; không ghi đè URI đã cấu hình. Điền connection string từ **Atlas → Connect → Drivers**, giữ database riêng cho app, ví dụ `latex`:

```json
{
  "uri": "mongodb+srv://USERNAME:PASSWORD@CLUSTER.mongodb.net/?retryWrites=true&w=majority",
  "database": "latex"
}
```

Thay toàn bộ placeholder bằng thông tin của bạn; password có ký tự đặc biệt cần encode đúng trong URI. Dùng **database user** của Atlas, không phải mật khẩu đăng nhập Google/Atlas web. Cấp quyền readWrite trên database ứng dụng và thêm IP máy chạy backend vào Atlas IP access list. Không commit/gửi URI có password lên chat. Backend không đưa URI vào frontend hoặc log lỗi.

Production ưu tiên biến môi trường `LATEX_MONGO_URI`, `LATEX_MONGO_DB`. URI môi trường ưu tiên file local. `OVERLEAF_MONGO_URI` chỉ là alias tương thích khi chưa có URI mới. MongoDB connection được ping khi mở app; lỗi mạng/auth được báo rõ, không tạo database SQLite thay thế.

```powershell
.\scripts\migrate-mongodb.bat --check
```

Lệnh này kiểm ping/quyền đọc, không in URI hoặc ghi dữ liệu ứng dụng.

## Chuyển dữ liệu cũ

1. Lưu bài và **Ctrl+C dừng web**. Không để app cũ tiếp tục ghi vào SQLite/JSON trong lúc chuyển.
2. Xem kế hoạch đọc-only, không cần kết nối database:

   ```powershell
   .\scripts\migrate-mongodb.bat --dry-run
   ```

3. Kiểm tra Atlas connection rồi áp dụng:

   ```powershell
   .\scripts\migrate-mongodb.bat --check
   .\scripts\migrate-mongodb.bat --apply
   ```

4. Chỉ khi báo `state: complete` mới chạy lại web từ **cùng PowerShell đã cấu hình Google**:

   ```powershell
   .\scripts\run-python-web.bat
   ```

Mặc định home lấy LATEX_DATA_DIR/OVERLEAF_WEB_HOME/OVERLEAF_PYTHON_HOME rồi `~/OverleafDesktopPython`, tương tự launcher. Có thể chỉ định `--home <folder>` để chuyển đúng volume khác.

Migration giữ nguyên user ID, Google sub, role, password hash, session digest/expiry, invitation token, public link token và quyền thành viên. Metadata được đọc từ các thư mục project có quản lý trong home; project legacy chưa gán người dùng vẫn mang owner `legacy`, không gán nhầm cho tài khoản đầu tiên. File source/artifact không bị di chuyển. Credential legacy `.app/state.json` cũng được nhập khi chưa có trong SQLite.

Trước khi ghi, script snapshot SQLite bằng backup API và copy các JSON/metadata đã đọc vào `<home>/.migration-backups/mongo-...`, kèm manifest. Không sửa/xóa SQLite/JSON gốc. Destination phải rỗng hoặc đúng migration đang dở; không tự merge/ghi đè một database có dữ liệu khác. Sau import đối chiếu từng document và checksum history, kiểm source không đổi, rồi mới đánh dấu complete. Lỗi giữa chừng để trạng thái in-progress; app không khởi động trên dữ liệu chuyển dở. Chạy lại `--apply` với cùng source để tiếp tục. Khi complete rồi, chạy lại không ghi đè dữ liệu MongoDB đang dùng.

SQLite/JSON gốc được giữ làm backup/compatibility. Các JSON manifest được tạo trong lúc tạo/import project hoặc history sidecar không còn là nguồn metadata runtime trên web; sửa chúng không thay metadata MongoDB. Desktop cũ vẫn sử dụng Store file/JSON riêng để tương thích.

## Collections để theo dõi trong Atlas

| Collection | Dữ liệu |
| --- | --- |
| `users` | ID ổn định, email, tên, Google sub, role, hash mật khẩu, auth version |
| `sessions` | Digest token, user ID, expiry; TTL index và kiểm expiry khi xác thực |
| `projects` | Owner, metadata title/main/engine/template, soft-delete |
| `shared_projects` | Owner và chính sách public/private/link role |
| `invitations` | Email, quyền, token lời mời, người đã chấp nhận |
| `link_visitors` | Tài khoản đã nhận quyền qua link nào |
| `user_state` | Settings, activity và compile counters theo workspace |
| `history` | File, revision ID, checksum, author, label và thời gian; payload ở volume |
| `project_records` | Review threads và dữ liệu IELTS theo project |
| `app_config` / `migrations` | Volume binding, trạng thái và kết quả migration |

Unique indexes bảo vệ email, Google identity, token và cặp membership. Expiry được kiểm ngay khi resolve session, không đợi TTL job xóa document. Đổi mật khẩu tăng auth version để thu hồi token cũ ngay cả khi việc dọn session sau đó lỗi mạng.

## Chuyển backend sang server khác nhưng giữ Atlas

Database được gắn với một storage home để tránh chạy nhầm vào volume trống. Khi chuyển máy:

1. Dừng backend cũ và copy **toàn bộ** volume dữ liệu sang máy mới.
2. Cấu hình cùng Atlas URI/database; thêm IP server mới trong Atlas.
3. Kiểm tra và đổi binding bằng CLI trên máy mới:

   ```powershell
   .\scripts\migrate-mongodb.bat --home D:\LatexData --rebind-home --dry-run
   .\scripts\migrate-mongodb.bat --home D:\LatexData --rebind-home --apply
   ```

   CLI kiểm các main source và checksum history trong bản copy trước khi đổi binding. Cần copy đầy đủ ảnh/binary/cache; các file này không có trong MongoDB. Không chạy đồng thời hai server dùng hai volume source độc lập cho cùng database.
4. Đặt LATEX_DATA_DIR đúng volume mới, cấu hình URL/Google callback, trusted host/proxy/HTTPS và triển khai compile worker an toàn trước khi public Internet. Launcher hiện vẫn giới hạn loopback.

## Kiểm thử

Unit/integration backend inject mongomock rõ ràng, không có cờ mock/fallback trong production launcher. Test kiểm migration SQLite/JSON thật ở fixture, bảo toàn ID/Google/session/ACL, idempotency, resume sau lỗi, DB metadata thắng JSON bị hỏng, rebind, indexes, expiry và fail-closed 503. Browser tests có thể dùng fixture `apps/backend/tests/run_mongo_web.py` qua biến **chỉ test Node harness** `LATEX_TEST_WEB_FACTORY`; bằng chứng đó không thay cho kiểm kết nối/migration trên Atlas thật.

Để chạy browser test trực tiếp với MongoDB thật, dùng **database thử nghiệm riêng** và volume `--home` riêng; không trỏ test vào database sản xuất. Nếu chưa có URI, việc kiểm thử mock hoàn tất không có nghĩa dữ liệu thật đã được chuyển lên Atlas.

Nguồn tham khảo: [kết nối Atlas](https://www.mongodb.com/resources/products/platform/mongodb-atlas-tutorial), [MongoDB TTL indexes](https://www.mongodb.com/docs/manual/core/index-ttl/), [Compass](https://www.mongodb.com/docs/compass/connect/).

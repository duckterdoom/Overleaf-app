# Kế hoạch dọn mã Java sau chuyển sang Python + Next.js

Ngày lập: 03/11/2026. **Tài liệu chuẩn bị; chưa xóa file hoặc thư mục nào.** Bản chạy chính là `apps/desktop` + `apps/frontend`. Mục tiêu là bỏ source/build/launcher Java khi đã xác nhận bản mới đủ dùng, giữ dữ liệu và tài nguyên liên quan.

## 1. Điều kiện trước khi dọn

- [ ] Lưu snapshot/backup hoặc commit đầy đủ bản hiện tại, gồm cả `apps/`, `deploy/python/` và launcher Python đang chưa được track. Chỉ tạo tag trên commit cũ không bảo vệ các file chưa commit.
- [ ] Sao lưu `~/OverleafDesktop`, `~/OverleafDesktopPython` và mọi thư mục dự án lưu ngoài; thử nhập dữ liệu Java, mở source/history và compile trên Python.
- [ ] Chạy bản desktop từ launcher Python, kiểm account/settings/import/upload/editor/history/cộng tác và preview/export/SyncTeX.
- [ ] Kiểm pytest, frontend build/typecheck; chạy nghiệm thu trên từng hệ điều hành dự định phát hành. Mac/Linux hiện chưa được kiểm native.
- [ ] Lưu bằng chứng đối chiếu Python ↔ Java cần dùng cho báo cáo trước khi bỏ server/classes Java.
- [ ] Xác định server nào còn chạy JAR/systemd Java và chuyển sang server Python trước khi gỡ cấu hình triển khai cũ.

## 2. Danh sách ứng viên xóa

Đường dẫn tính từ root repository. Kiểm tra nội dung thực tế lại trước thao tác; danh sách này không phải script xóa tự động.

| Đường dẫn | Nội dung / điều kiện |
| --- | --- |
| `modules/client/` | Source JavaFX, compiler Java, resource và test Java; template cần thiết đã có bản riêng trong Python, giữ giấy phép |
| `modules/common/` | Model/protocol/OT/security Java; Python đã có triển khai riêng |
| `modules/server/` | Server/session/broadcast Java; chỉ bỏ sau khi server Python thay thế các deployment đang dùng |
| `modules/persistence/` | Repository/service MongoDB Java; bản Python nằm trong `persistence.py`, vẫn là prototype |
| `modules/` | Chỉ bỏ thư mục cha khi đã kiểm không còn tài nguyên riêng cần lưu |
| `pom.xml` | Maven aggregator/build/format Java; không dùng cho Python/Next.js |
| `Overleaf Desktop.command` | Launcher macOS Java cũ; giữ `Overleaf Python Desktop.command` |
| `scripts/run-client.bat`, `scripts/run-client.sh` | Launcher JavaFX |
| `scripts/run-server.bat`, `scripts/run-network-demo.bat` | Launcher server/demo Java |
| `scripts/build-network.ps1`, `scripts/build-network.sh` | Build JAR Java |
| `deploy/overleaf.service` | systemd gọi `java -jar`; chuyển service đang chạy trước khi bỏ |
| `deploy/overleaf.env.example`, `deploy/overleaf-public.env.example` | Mẫu deployment Java cũ; đối chiếu biến chung với hướng dẫn Python, không copy cấu hình keystore Java sang PEM |
| `client/target/`, `common/target/`, `server/target/`, `persistence/target/` | Output Java cũ ở root; tại thời điểm kiểm tra các thư mục cha chỉ còn `target`. Chỉ bỏ cha nếu không có dữ liệu khác |
| Các JAR Java trong `dist/` | Kiểm từng file; không mặc định xóa toàn bộ `dist` nếu có artifact mới |

Java `src/main/resources/config.properties` hoặc keystore có thể bị Git ignore. Kiểm backup riêng trước khi dọn module; không đưa bí mật vào commit/backup công khai.

## 3. File cần điều chỉnh cùng đợt dọn

| File / nhóm | Hành động |
| --- | --- |
| `apps/desktop/tests/smoke_java_interop.py` | Helper hiện gọi JDK và `modules/common/target/classes`, `modules/server/target/classes`. Lưu kết quả rồi chuyển thành kiểm thử tương thích tùy chọn có fixture bên ngoài, hoặc bỏ helper khi không còn nhu cầu; không mô tả nó còn chạy được sau khi xóa Java |
| `docs/AI_Handoff.md` | Giữ lịch sử, ghi thêm mốc dọn và đường dẫn mới; các đoạn Java cũ là lịch sử, không cần xóa toàn bộ |
| `docs/JAVA_PYTHON_PARITY.md` | Giữ làm bằng chứng; ghi rõ source Java đã lưu ở backup/commit nào |
| Các tài liệu Java trong `docs/` | Chuyển sang nhóm tài liệu lịch sử hoặc thêm nhãn Java; các hướng dẫn cũ như `MACOS.md` không thay thế hướng dẫn Python |
| `.gitignore`, `.editorconfig`, `.gitattributes` | Có thể dọn rule chỉ dành Java sau khi rà soát; tiếp tục ignore output, bí mật và file hệ điều hành |
| CI/cấu hình IDE nếu có thêm | Tìm Maven/JDK/JAR/run-client; thay bằng Python/Node. Không giả định mọi cấu hình local đều được Git track |

Các hàm `import_java`/`migrate_java` trong Python **cần giữ** để nhập dữ liệu cũ; tên có chữ Java không có nghĩa là dependency JVM. Test đọc Java properties/history và fixture migration cũng cần giữ.

## 4. Phải giữ

- `apps/desktop/`, `apps/frontend/`, các manifest/lockfile và test Python/TypeScript.
- `scripts/run-python-desktop.*`, `run-python-server.*`, `run-python-console.*`; `Overleaf Python Desktop.command`; `deploy/python/`.
- `apps/desktop/src/overleaf_desktop/resources/`: toàn bộ template, ảnh/font đi kèm và rule giải thích lỗi; runtime không đọc asset Java.
- `LICENSE-TEMPLATES.md`, `examples/`, README và các tài liệu bàn giao/yêu cầu. Không xóa tài liệu chỉ vì mô tả công nghệ cũ.
- Dữ liệu người dùng, `.tex`, `.bib`, `.sty`, `.cls`, ảnh và PDF gốc. Các file `.sty`/`.cls` là LaTeX, không phải Java `.class`.
- **Không xóa toàn bộ `target/`**: hiện có TinyTeX, Pandoc, Python/uv/runtime và báo cáo/PDF kiểm thử. Đặc biệt `target/TinyTeX` đang phục vụ compile trên máy này; xóa sẽ làm mất công cụ và package/font vừa cài.
- Không mặc định xóa mọi `.class` trong cả ổ đĩa hoặc mọi thư mục `build`/`out`: `apps/frontend/out` là UI static đang dùng; build PDF thuộc dự án và tài nguyên công cụ có mục đích riêng.
- `.git/` và dữ liệu backup cho tới khi nghiệm thu bản mới hoàn tất.

## 5. Trình tự dọn đề xuất

1. Hoàn tất backup/commit nguồn hiện tại và ghi vị trí phục hồi. Giữ nguyên dữ liệu Java gốc ngoài repository.
2. Hoàn tất kiểm thử tương thích Java rồi xử lý helper phụ thuộc classes như mục 3.
3. Dọn source Maven và launcher Java theo danh sách mục 2 trong một thay đổi riêng, có thể review diff.
4. Dọn deployment cũ sau khi xác nhận server Python hoạt động; cập nhật tài liệu và CI tham chiếu đường dẫn đã bỏ.
5. Chỉ xóa output Java đã xác minh, không đụng TinyTeX/Pandoc/runtime hay bằng chứng kiểm thử cần lưu.
6. Build lại UI, chạy pytest/typecheck và mở app Python trên máy không dùng JDK/Maven. Kiểm compile PDF, export, mở lại dự án và cộng tác hai client.
7. Ghi vào AI_Handoff những đường dẫn thực sự đã xóa, kết quả kiểm và cách khôi phục. Chỉ khi đó mới coi việc dọn hoàn tất.

## 6. Tiêu chí hoàn tất

- [ ] Khởi động desktop/server Python không gọi JVM hoặc Maven.
- [ ] Không còn launcher/CI đang trỏ tới source/JAR đã xóa; tài liệu lịch sử được phân biệt rõ.
- [ ] Template và nhập dữ liệu Java vẫn hoạt động, dữ liệu gốc còn nguyên.
- [ ] Preview/export PDF đúng snapshot, TeX package/font cần thiết còn sẵn.
- [ ] Có kết quả kiểm thử sau dọn và backup có thể khôi phục.

Chưa có lệnh xóa tự động trong tài liệu này để mỗi đường dẫn được đối chiếu với nội dung thực tế trước khi thực hiện. Yêu cầu hiện tại chỉ cập nhật tài liệu chuẩn bị dọn.

# Networking server Python

Đây là server TCP/TLS cộng tác tương ứng M3/M4, **không phải API file/compile local** của cửa sổ desktop. Không cần Java, frontend, bộ TeX hay pywebview trên server.

Từ root repository, tạo Python 3.12+ venv và cài `pip install ./apps/backend`, sau đó:

```sh
export OVERLEAF_HOST=0.0.0.0
export OVERLEAF_PORT=8080
export OVERLEAF_TOKEN='replace-with-a-random-secret-at-least-24-characters'
export OVERLEAF_TLS_CERT=/etc/overleaf/server-cert.pem
export OVERLEAF_TLS_KEY=/etc/overleaf/server-key.pem
sh scripts/run-python-server.sh
```

Windows dùng `scripts\run-python-server.bat`. Local demo có thể dùng loopback mặc định, không TLS/token. Kết nối bên ngoài localhost yêu cầu TLS và token ≥24 ký tự ở cả client/server. Chứng chỉ PEM phải có SAN khớp hostname; client xác minh hostname và CA, không có chế độ bỏ qua xác minh. Đường dẫn key cần quyền đọc cho tài khoản chạy server, không commit key hoặc token vào Git.

Client: mở nút **Cộng tác**, nhập host/port/phòng/tên/token, bật TLS và chọn đường dẫn CA PEM nếu dùng CA riêng. Hai cửa sổ/máy dùng cùng room để đồng bộ `main.tex`. Console demo: `sh scripts/run-python-console.sh --host ... --port ... --tls --ca-file ...`; dùng `/offline`, `/reconnect`, `/show`, `/status` để kiểm queue/RESYNC.

Compose mẫu: chuẩn bị `deploy/python/certs/server-cert.pem`, `server-key.pem` và biến môi trường `OVERLEAF_TOKEN`, rồi chạy `docker compose -f deploy/python/compose.yaml up --build -d`. Đây là cấu hình mẫu chưa chạy Docker trên máy kiểm thử hiện tại; TCP/TLS bằng Python thật và tương thích với Java server đã được kiểm thử riêng.

Mặc định: 64 clients, idle 60 giây, outbox 32 frame, frame tối đa 1 MiB, tài liệu 100.000 ký tự UTF-16, history thao tác 10.000 mục, RESYNC tối đa 30.000 ops/4 MiB. Có các biến `OVERLEAF_MAX_CLIENTS`, `OVERLEAF_IDLE_TIMEOUT_MS`, `OVERLEAF_QUEUE_CAPACITY`, `OVERLEAF_MAX_DOCUMENT_CHARS`; dùng `--help` để xem tùy chọn CLI.

Giữ phạm vi Java: một room đồng bộ **main.tex**, shared-token authentication, engine OT/Lamport đơn giản, dữ liệu room/queue trong RAM. Server restart mất room sẽ trả `ROOM_UNKNOWN`; client giữ pending và bản local, không tự tạo phòng mới rồi vứt thao tác. MongoDB M5 vẫn là prototype repository/service tùy chọn, chưa gắn vào room hoặc UI; xem tài liệu chuyển đổi.

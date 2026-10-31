# Triển khai Overleaf TCP server

Ngày 01/11/2026. Đây là hướng dẫn triển khai artifact đã build; chưa có thao tác đăng nhập hoặc thay đổi VPS nào được thực hiện trong phiên code.

## 1. Build artifact

Windows, JDK đầy đủ 17+:

```powershell
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-21.0.13.0.2'
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-network.ps1
```

Linux/macOS có JDK 17+: `sh build-network.sh`.

Kết quả: `dist/overleaf-server.jar`, `dist/overleaf-network-demo.jar`. Chỉ cần copy JAR, không cần copy cache Maven, source hoặc `config.properties` MongoDB. Hai JAR không chứa credential/database resources. Máy server chạy cần Java runtime 17+, không cần GUI/JavaFX/TeX/MongoDB cho backend hiện tại.

## 2. Chạy trên VPS qua SSH tunnel

Cách này giữ TCP port trên loopback của VPS, dùng SSH để truyền dữ liệu mã hóa. Thay `user@server` bằng tài khoản và địa chỉ VPS của bạn.

```sh
# Trên máy local, chuyển artifact:
scp dist/overleaf-server.jar user@server:~/overleaf-server.jar

# Đăng nhập VPS và chạy thử:
ssh user@server
java -jar ~/overleaf-server.jar 8080
```

Giữ server chạy. Mỗi người dùng mở một terminal trên máy mình:

```sh
ssh -N -L 8080:127.0.0.1:8080 user@server
```

Client kết nối `127.0.0.1:8080`, TLS tắt, token rỗng nếu server đang dùng mặc định. Nếu port local bận, dùng `-L 18080:127.0.0.1:8080` rồi chọn port client `18080`. Có thể đặt `OVERLEAF_TOKEN` chung ở server và client ngay cả khi dùng tunnel.

Kiểm tra bằng demo:

```sh
java -jar dist/overleaf-network-demo.jar 127.0.0.1 8080 alice demo
```

Mở client Bob ở terminal khác và nhập `/insert 0 Hello` ở Alice. Bob phải nhận `TEXT_EDIT`; client vào sau nhận `FULL_SYNC` chứa `Hello`.

## 3. Chạy liên tục bằng systemd trên Linux

Các lệnh dưới chạy trên VPS có Java 17+, systemd và quyền sudo. Chúng tạo user/service mới; nếu VPS đã có user/thư mục cùng tên, dùng cấu hình hiện có tương ứng.

```sh
sudo useradd --system --home /opt/overleaf --shell /usr/sbin/nologin overleaf
sudo install -d -o root -g overleaf -m 750 /opt/overleaf /etc/overleaf
sudo install -o root -g overleaf -m 640 ~/overleaf-server.jar /opt/overleaf/overleaf-server.jar
```

Copy `deploy/overleaf.env.example` và `deploy/overleaf.service` từ repo lên VPS trước bước tiếp theo, rồi chạy trong thư mục chứa hai file đó:

```sh
sudo install -o root -g root -m 600 overleaf.env.example /etc/overleaf/overleaf.env
sudo install -o root -g root -m 644 overleaf.service /etc/systemd/system/overleaf.service
sudo systemctl daemon-reload
sudo systemctl enable --now overleaf
sudo systemctl status overleaf
sudo journalctl -u overleaf -f
```

Service mẫu mặc định bind loopback, tương thích SSH tunnel. `systemctl stop overleaf` gửi tín hiệu shutdown để đóng socket/pool. Sau khi thay JAR hoặc env: `sudo systemctl restart overleaf`.

## 4. Kết nối trực tiếp qua TLS public

Chỉ cần cách này nếu không dùng SSH tunnel. Server từ chối external bind nếu thiếu TLS/token.

1. Có tên DNS trỏ về VPS, ví dụ `latex.example.com`.
2. Chuẩn bị PKCS12 chứa certificate/private key cho đúng DNS. Có thể dùng certificate CA của tổ chức/nhà cung cấp. Với nhóm thử nghiệm, tạo self-signed bằng JDK:

```sh
keytool -genkeypair -alias overleaf -keyalg RSA -keysize 3072 -validity 365 \
  -dname "CN=latex.example.com" -ext "SAN=dns:latex.example.com" \
  -keystore server.p12 -storetype PKCS12
keytool -exportcert -rfc -alias overleaf -keystore server.p12 -file server.crt
```

`keytool` hỏi mật khẩu. Đổi hostname mẫu thành DNS thật. Private keystore chỉ đặt trên server, không gửi cho client.

3. Đặt `server.p12` tại `/etc/overleaf/server.p12`, owner `root:overleaf`, mode `640`.
4. Dùng `deploy/overleaf-public.env.example` làm `/etc/overleaf/overleaf.env`; thay token và password thật. Token nên được tạo ngẫu nhiên, tối thiểu 24 ký tự. Giữ file env mode `600`, không commit.
5. Mở TCP 8080 trong firewall/security group cho các máy cần kết nối; restart service. Không cần reverse proxy HTTP.

Nếu dùng self-signed/private CA, mỗi client nhập **certificate công khai** vào truststore riêng sau khi kiểm tra fingerprint với người quản trị:

```sh
keytool -importcert -alias overleaf -file server.crt -keystore client-trust.p12 -storetype PKCS12
```

Cấu hình client trên PowerShell:

```powershell
$env:OVERLEAF_TLS = 'true'
$env:OVERLEAF_TOKEN = '<token chung đã cấu hình trên server>'
$env:OVERLEAF_TRUSTSTORE = 'C:\path\client-trust.p12'
$env:OVERLEAF_TRUSTSTORE_PASSWORD = '<mật khẩu truststore>'
java -jar dist/overleaf-network-demo.jar latex.example.com 8080 alice demo
```

GUI có checkbox TLS và ô token; truststore lấy từ môi trường lúc khởi chạy GUI. Với certificate được CA mặc định JVM tin cậy, không cần đặt truststore riêng. Không tắt hostname verification để vượt lỗi certificate; sửa DNS/SAN/truststore tương ứng.

## 5. Phạm vi triển khai đã có

Đã có listener TCP, TLS, shared-token handshake, membership, broadcast M4, ACK, full sync, offline RESYNC, heartbeat, timeout và shutdown. Cấu hình đầy đủ ở [NETWORKING_M3.md](NETWORKING_M3.md).

Backend **chưa lưu dữ liệu bền vững**: phòng và lịch sử mất khi server restart; `DatabaseService` chưa nối vào luồng M4. Token chung chỉ cho phép vào server nhóm, chưa xác thực tài khoản hoặc giới hạn quyền từng dự án. Đây là các giới hạn thực tế cần giải quyết nếu dùng server làm nơi lưu tài liệu lâu dài. Nên lưu file ở client trước khi bảo trì/restart.

Các file service/shell là mẫu triển khai cho Linux; chúng chưa được chạy trên VPS trong phiên này. Backend/JAR được kiểm tra bằng socket/TLS local và Maven trên Windows.

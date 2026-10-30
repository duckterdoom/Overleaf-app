# OVERLEAF DESKTOP - HƯỚNG DẪN DÀNH CHO VỊ TRÍ M1 (GUI & CLIENT)

Nhánh hiện tại: **`feat/m1-gui-client`**  
Mã nguồn GUI của bạn đã được nâng cấp lên **JavaFX 17+ Hiện Đại & Tối Ưu Hóa** tại mô-đun **`overleaf-client`** theo đúng đặc tả môn học (mục 7.1 của `overleaf_desktop_ver2.md`).

---

## 1. Cấu trúc thư mục mô-đun GUI (M1)

```text
overleaf-desktop-project/
├── .gitignore                   # Loại trừ file IDE, tệp tạm biên dịch
├── pom.xml                      # Root Parent POM (đã cấu hình JavaFX 17)
├── HD_CHAY_UNG_DUNG.md          # Tài liệu hướng dẫn
├── run-client.bat               # Script nhấp đúp để chạy ứng dụng JavaFX hiện đại
├── run-client-swing.bat         # Script dự phòng chạy bản Java Swing cũ
│
├── overleaf-client/             # [PHẦN CỦA BẠN - M1: GUI & CLIENT]
│   ├── pom.xml                  # Cấu hình javafx-controls, javafx-fxml, javafx-maven-plugin
│   └── src/main/
│       ├── java/com/overleaf/client/
│       │   ├── ClientApp.java               - Điểm khởi chạy chính (hỗ trợ JavaFX và --swing)
│       │   └── gui/
│       │       ├── OverleafFxApp.java       - GIAO DIỆN JAVAFX TỐI ƯU (Flat Design, Hardware Canvas)
│       │       └── OverleafDesktopApp.java  - Bản Java Swing cũ (được giữ nguyên dự phòng)
│       └── resources/
│           └── styles/
│               └── overleaf-theme.css       - Theme phong cách Overleaf Web chuyên nghiệp
│
├── overleaf-common/             # [MÔ-ĐUN DÀNH CHO ĐỒNG ĐỘI - M4]
├── overleaf-server/             # [MÔ-ĐUN DÀNH CHO ĐỒNG ĐỘI - M2 & M3]
└── database/                    # [MÔ-ĐUN DÀNH CHO ĐỒNG ĐỘI - M5]
```

---

## 2. Cách chạy ứng dụng GUI JavaFX của bạn

### Cách 1: Dùng script tự động
Nhấp đúp chuột vào file **`run-client.bat`** ở thư mục gốc dự án.

### Cách 2: Chạy bằng dòng lệnh Maven
Mở Terminal / PowerShell tại thư mục gốc dự án:
```bash
# Biên dịch và khởi chạy giao diện JavaFX
mvn compile javafx:run -pl overleaf-client
```

*(Nếu muốn chạy lại bản Swing cũ để đối chứng: nhấp đúp `run-client-swing.bat` hoặc chạy `java -cp bin com.overleaf.client.ClientApp --swing`).*

---

## 3. Các tính năng nổi bật của giao diện JavaFX (M1)

1. **Giao diện chuẩn Overleaf**: Nút **Recompile** màu xanh đặc trưng (`#388E3C`), chọn bộ dịch `pdfLaTeX / XeLaTeX`, nhãn trạng thái kết nối phòng `● Chạy cục bộ` hoặc `● Đã kết nối ROOM-101`.
2. **Khung xem trước PDF tăng tốc phần cứng (Hardware Accelerated Canvas)**: Vẽ chuẩn khổ giấy A4, hiệu ứng bóng đổ (Drop Shadow) chân thực, nút phóng to / thu nhỏ / vừa trang mượt mà.
3. **Biên dịch nền bất đồng bộ (Background Daemon Task)**: Sử dụng `javafx.concurrent.Task` đảm bảo giao diện luôn mượt mà 60 FPS, cập nhật tiến trình và log `latexmk` thời gian thực.
4. **Phím tắt tiện dụng**: `Ctrl + S` để Lưu file, `F5` hoặc `Ctrl + Enter` để Recompile.

---

## 4. Cách đẩy nhánh GUI lên GitHub

Repository remote:
`https://github.com/duckterdoom/Overleaf-app.git`

Chạy lệnh sau trên Terminal:
```bash
git add .
git commit -m "feat(gui): upgrade client GUI to modern JavaFX with CSS and hardware-accelerated PDF canvas"
git push -u origin feat/m1-gui-client
```

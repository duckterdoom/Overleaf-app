# OVERLEAF DESKTOP - HƯỚNG DẪN DÀNH CHO VỊ TRÍ M1 (GUI & CLIENT)

Nhánh hiện tại: **`feat/m1-gui-client`**  
Mã nguồn GUI của bạn đã được đưa vào đúng vị trí mô-đun **`overleaf-client`** theo chuẩn Maven Multi-Module (mục 7.1 của `overleaf_desktop_ver2.md`).

---

## 1. Cấu trúc thư mục dự án

```text
overleaf-desktop-project/
├── .gitignore                   # Loại trừ file IDE, tệp tạm biên dịch
├── pom.xml                      # Root Parent POM quản lý 4 sub-modules
├── HD_CHAY_UNG_DUNG.md          # Tài liệu hướng dẫn
├── run-client.bat               # Script nhấp đúp để chạy nhanh ứng dụng GUI
│
├── overleaf-client/             # [PHẦN CỦA BẠN - M1: GUI & CLIENT]
│   ├── pom.xml
│   └── src/main/java/com/overleaf/client/
│       ├── ClientApp.java               - Điểm khởi chạy chính
│       └── gui/
│           └── OverleafDesktopApp.java  - Toàn bộ giao diện Java Swing của bạn
│
├── overleaf-common/             # [MÔ-ĐUN DÀNH CHO ĐỒNG ĐỘI - M4]
│   ├── pom.xml
│   └── src/main/java/.gitkeep
│
├── overleaf-server/             # [MÔ-ĐUN DÀNH CHO ĐỒNG ĐỘI - M2 & M3]
│   ├── pom.xml
│   └── src/main/java/.gitkeep
│
└── database/                    # [MÔ-ĐUN DÀNH CHO ĐỒNG ĐỘI - M5]
    ├── pom.xml
    ├── src/main/java/.gitkeep
    └── src/test/java/.gitkeep
```

---

## 2. Cách chạy ứng dụng GUI của bạn

### Cách 1: Dùng script có sẵn
Nhấp đúp chuột vào file `run-client.bat` ở thư mục gốc.

### Cách 2: Chạy bằng dòng lệnh Terminal
```bash
# Biên dịch mã nguồn GUI
javac -encoding UTF-8 -d bin overleaf-client/src/main/java/com/overleaf/client/gui/*.java overleaf-client/src/main/java/com/overleaf/client/*.java

# Khởi chạy giao diện
java -cp bin com.overleaf.client.ClientApp
```

---

## 3. Cách đẩy nhánh GUI lên GitHub

Repository remote:
`https://github.com/duckterdoom/Overleaf-app.git`

Chạy lệnh sau trên Terminal để đẩy nhánh của bạn lên:
```bash
git push -u origin feat/m1-gui-client
```
*(Nếu muốn tạo Pull Request vào `main`, sau khi push xong bạn chỉ cần lên trang GitHub của repo và bấm nút **Compare & pull request**).*

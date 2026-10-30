# OVERLEAF DESKTOP (PURE JAVA EDITION) - KIẾN TRÚC & HƯỚNG DẪN DỰ ÁN

Dự án được phân chia thành **5 Mô-đun (M1 - M5)** tương ứng chính xác với bảng phân công trách nhiệm 5 thành viên trong tệp báo cáo `overleaf_desktop_pure_java_project_report.md`.

---

## 1. Cấu trúc thư mục mã nguồn 5 Modules

```text
src/
└── com/
    └── overleaf/
        ├── client/                       <-- [M1: GUI & Client]
        │   ├── ClientApp.java            - Điểm khởi chạy ứng dụng Client (Main)
        │   ├── NetworkClient.java        - Luồng kết nối TCP Socket tới Server
        │   └── gui/
        │       ├── MainFrame.java        - Cửa sổ chính kết hợp Menu, Toolbar, SplitPane
        │       ├── CodeEditorPanel.java  - Khung soạn thảo mã LaTeX & Console Log
        │       ├── PdfPreviewPanel.java  - Khung hiển thị tài liệu PDF mô phỏng A4
        │       └── FileTreePanel.java    - Cây thư mục dự án & Danh sách cộng tác viên
        ├── compiler/                     <-- [M2: Core Compiler Engine]
        │   ├── CompilerService.java      - Gọi ProcessBuilder (latexmk/pdflatex) & Mô phỏng ngầm
        │   ├── CompileResult.java        - Đóng gói kết quả biên dịch, log & file đầu ra
        │   └── FileWatcherService.java   - Giám sát tệp bằng Java NIO WatchService
        ├── server/                       <-- [M3: Networking Backend]
        │   ├── ServerApp.java            - Điểm khởi chạy máy chủ TCP Socket (Main)
        │   ├── OverleafServer.java       - Quản lý ServerSocket, Thread Pool Executor
        │   └── ClientHandler.java        - Xử lý từng kết nối Client bất đồng bộ
        ├── collab/                       <-- [M4: Collaboration Logic]
        │   ├── RoomManager.java          - Quản lý các phòng soạn thảo (ConcurrentHashMap)
        │   ├── Room.java                 - Đại diện một phòng biên soạn & trạng thái văn bản
        │   ├── SyncMessage.java          - Gói tin tuần tự hóa (Serializable POJO)
        │   └── MessageType.java          - Enum các loại thông điệp (JOIN, TEXT_UPDATE, v.v.)
        └── database/                     <-- [M5: Database & Testing]
            ├── DatabaseConfig.java       - Cấu hình kết nối MongoDB & Repositories
            ├── UserRepository.java       - Repository Pattern cho User (CRUD)
            ├── ProjectRepository.java    - Repository Pattern cho Project (CRUD)
            ├── User.java                 - Entity người dùng
            └── Project.java              - Entity dự án LaTeX
```

---

## 2. Bản đồ đối chiếu 5 Thành viên với Mã nguồn

| Thành viên | Trách nhiệm theo Báo cáo | Gói mã nguồn tương ứng | Điểm nổi bật kỹ thuật |
| :--- | :--- | :--- | :--- |
| **M1** | **GUI & Client** | `com.overleaf.client.gui` | Giao diện Swing 3 cột, soạn thảo LaTeX, xem trước PDF A4, Zoom In/Out. |
| **M2** | **Core Compiler Engine** | `com.overleaf.compiler` | `ProcessBuilder` gọi `pdflatex`/`latexmk`, `SwingWorker` chạy ngầm, Java NIO `WatchService`. |
| **M3** | **Networking Backend** | `com.overleaf.server` | `ServerSocket` cổng 8080, `ExecutorService` quản lý luồng, truyền nhị phân qua TCP Socket. |
| **M4** | **Collaboration Logic** | `com.overleaf.collab` | Quản lý đa phòng bằng `ConcurrentHashMap`, đồng bộ văn bản và danh sách người dùng thời gian thực. |
| **M5** | **Database & Persistence** | `com.overleaf.database` | Repository Pattern cho `User` & `Project`, trừu tượng hóa tầng MongoDB Driver. |

---

## 3. Cách biên dịch và chạy

### Cách 1: Sử dụng các tệp batch tạo sẵn (Rất tiện lợi)
- **Biên dịch**: Nhấp đúp vào `build.bat`
- **Chạy Server**: Nhấp đúp vào `run-server.bat` (Mở port 8080 lắng nghe)
- **Chạy Client**: Nhấp đúp vào `run-client.bat` (Mở giao diện soạn thảo)

### Cách 2: Chạy trực tiếp từ dòng lệnh (Terminal)

```bash
# 1. Biên dịch toàn bộ mã nguồn vào thư mục bin/
javac -encoding UTF-8 -d bin src/com/overleaf/collab/*.java src/com/overleaf/database/*.java src/com/overleaf/compiler/*.java src/com/overleaf/server/*.java src/com/overleaf/client/gui/*.java src/com/overleaf/client/*.java

# 2. Khởi chạy Server (ở một cửa sổ terminal riêng)
java -cp bin com.overleaf.server.ServerApp 8080

# 3. Khởi chạy Client (ở một cửa sổ terminal khác)
java -cp bin com.overleaf.client.ClientApp
```

---

## 4. Kết nối và Đẩy mã nguồn lên Git (GitHub)

Dự án đã được kết nối sẵn với remote:
`https://github.com/duckterdoom/Overleaf-app.git`

Để đẩy toàn bộ mã nguồn lên GitHub:
```bash
# Đổi tên nhánh sang main (nếu cần)
git branch -M main

# Đẩy code lên GitHub
git push -u origin main
```

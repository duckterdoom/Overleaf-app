# Cài và chạy trên macOS

Cập nhật 03/11/2026. Chạy từ source bằng JDK/Maven/JavaFX native của máy Mac; chưa có bộ cài `.dmg` hay `.app` đã ký/notarize. Không dùng binary TinyTeX Windows trong target trên Mac. Chưa có máy macOS để xác nhận giao diện/native smoke; kiểm tra script không thay thế việc chạy thật trên Mac.

## 1. Chuẩn bị

Cài [Homebrew](https://brew.sh/) nếu chưa có, theo hướng dẫn chính thức. Mở Terminal và chạy:

```sh
brew install openjdk@21 maven
brew install --cask mactex-no-gui
```

MacTeX no GUI chứa full TeX Live, không cài các ứng dụng soạn thảo TeX bổ sung. Bộ cài lớn, cần mạng và quyền cài hệ thống. Nếu đã có MacTeX đầy đủ, bỏ qua lệnh cài này; không cài chồng với BasicTeX hoặc MacTeX khác. Có thể dùng JDK 21 từ nhà cung cấp khác và Maven hiện có, không bắt buộc cài lại.

Chọn JDK 21 cho Terminal hiện tại và thêm TeX vào PATH:

```sh
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:/Library/TeX/texbin:$PATH"
java -version
javac -version
mvn -version
latexmk -v
synctex help
```

JAVA_HOME này dùng đường dẫn do brew trả về nên không hardcode `/opt/homebrew` hay `/usr/local`. Launcher tự tìm JDK Homebrew/MacTeX khi JAVA_HOME chưa đặt; nếu JAVA_HOME đang trỏ vào JDK cũ, đổi nó hoặc `unset JAVA_HOME` trước khi chạy. Maven có thể kéo thêm JDK riêng làm dependency; JAVA_HOME ở trên xác định JDK dùng build app.

Nguồn lệnh cài: [OpenJDK 21](https://formulae.brew.sh/formula/openjdk@21), [Maven](https://formulae.brew.sh/formula/maven), [MacTeX no GUI](https://formulae.brew.sh/cask/mactex-no-gui). Phiên bản macOS được hỗ trợ tùy bản JDK/Homebrew/MacTeX đang cài; kiểm tra yêu cầu tại các trang chính thức thay vì mặc định mọi Mac cũ đều chạy được.

## 2. Chép source và chạy

Chép thư mục source hiện tại sang Mac qua ZIP/USB, hoặc clone repository nếu các thay đổi cần dùng đã được đẩy lên remote. Không chỉ chép JAR/binary đã build trên Windows. Không cần chép target, dist hoặc cache Windows; lần build đầu trên Mac cần mạng để Maven lấy dependency/JavaFX đúng nền tảng.

Trong Terminal, vào thư mục dự án thực tế, ví dụ:

```sh
cd ~/Downloads/Overleaf-app
sh scripts/run-client.sh --check
sh scripts/run-client.sh
```

Nếu tên thư mục có dấu cách, đặt đường dẫn trong dấu ngoặc kép. `--check` kiểm JDK/Maven và liệt kê tool TeX, không build hay cài package. Thiếu TeX không chặn mở editor nhưng các tính năng compile/SyncTeX tương ứng cần tool đã cài.

Launcher build các module trước, sau đó chạy JavaFX client. Chỉ build:

```sh
sh scripts/run-client.sh --build-only
```

Không cần chạy MongoDB/server để soạn và compile local. Muốn cộng tác thì kết nối tới server TCP/TLS đã triển khai riêng.

## 3. Mở bằng nhấp đúp

Chạy một lần trong thư mục dự án:

```sh
chmod +x "Overleaf Desktop.command" scripts/run-client.sh
```

Sau đó nhấp đúp **Overleaf Desktop.command** trong Finder. File này mở Terminal, build rồi mở app; không phải ứng dụng `.app` độc lập và lần chạy đầu vẫn cần dependency. Khi lỗi, Terminal giữ thông báo để đọc. Nếu macOS chặn file tải về, xác minh nguồn và dùng Open/Privacy & Security theo hướng dẫn macOS; không cần tắt Gatekeeper toàn hệ thống.

## 4. Compile và SyncTeX

Trong app vào Settings → Compilation, chọn đường dẫn latexmk nếu không tự tìm thấy:

```text
/Library/TeX/texbin/latexmk
```

Kiểm tra công cụ cùng bộ TeX:

```sh
/Library/TeX/texbin/latexmk -v
/Library/TeX/texbin/pdflatex --version
/Library/TeX/texbin/xelatex --version
/Library/TeX/texbin/lualatex --version
/Library/TeX/texbin/biber --version
/Library/TeX/texbin/synctex help
```

Chọn mẫu, Recompile rồi kiểm PDF/Export. SyncTeX cần index mới: đặt con trỏ và bấm Đến PDF hoặc nhấp đúp PDF để về nguồn. App không tự cài package vào MacTeX hệ thống; thiếu gói cần quản lý qua bộ TeX của bạn.

Nhập Word/Markdown cần thêm `brew install pandoc`; nhập GitHub cần Git, kiểm bằng `git --version` (macOS có thể yêu cầu cài Command Line Tools). Các công cụ này không bắt buộc để mở/soạn LaTeX thông thường.

## 5. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
| --- | --- |
| JDK thấp hơn 21 hoặc JAVA_HOME sai | Đặt JAVA_HOME như trên rồi kiểm `mvn -version`; launcher cần full JDK có javac |
| mvn không tìm thấy | Cài Maven và kiểm Homebrew PATH; `--check` hiện lỗi trước bước build |
| Không chạy được latexmk | Thêm `/Library/TeX/texbin` vào PATH hoặc chọn executable đầy đủ trong Settings |
| Không tìm thấy SyncTeX | Kiểm `/Library/TeX/texbin/synctex`, chọn cùng bộ TeX với latexmk, Recompile lại |
| JavaFX báo native library/architecture không phù hợp | Dùng JDK và Terminal cùng kiến trúc máy; build lại trên Mac, không dùng target/cache binary Windows. Kiểm `uname -m` và `java -XshowSettings:properties -version` |
| Không có quyền chạy file .command | Dùng chmod ở trên hoặc chạy `sh scripts/run-client.sh` trong Terminal |
| Download Maven lỗi hoặc hết mạng | Lần đầu cần mạng để lấy dependency; giữ cache sau build thành công để dùng lại |

## 6. Kiểm chứng trên máy phát triển

Đã kiểm tra cú pháp POSIX shell và các nhánh launcher bằng Git Bash trên Windows với công cụ giả lập (không chạy brew hoặc cài macOS dependency). Kiểm tra này xác nhận xử lý tham số, thiếu Maven, JDK cũ, đường dẫn có dấu cách và các lệnh build/launch; chưa xác nhận JavaFX, bộ gõ tiếng Việt hoặc compile native trên Mac. Cần chạy `--check`, mở app, compile ba engine và thử SyncTeX trên Mac để nghiệm thu.

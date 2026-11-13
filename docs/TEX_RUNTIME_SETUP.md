# TeX cho máy dev/test và web server

## Nguyên nhân ảnh báo thiếu package

Python/npm không cung cấp LaTeX. `target/TinyTeX`, môi trường Python và `node_modules` không được đưa lên Git; clone source không mang bộ TeX từ máy tác giả. Khi không tìm được `latexmk`/`kpsewhich`, preflight cũ liệt kê toàn bộ package cơ bản là thiếu. Ngoài ra, TinyTeX Windows di động có cấu hình font chứa đường dẫn máy build; copy sang máy khác có thể tìm thấy `.sty` nhưng XeLaTeX không tìm thấy font theo tên.

Việc compile trên web diễn ra ở backend. Người truy cập bằng trình duyệt không cài TeX hoặc package. Máy dev tự chạy backend là một máy chủ local nên cần bộ TeX của riêng nó.

## Máy vừa clone

```powershell
.\scripts\run-python-web.bat --build
.\scripts\run-python-web.bat
```

macOS/Linux dùng `sh scripts/run-python-web.sh --build` rồi `sh scripts/run-python-web.sh`. Đã có Python 3.12+ và Node 22.13+ thì launcher tự chuẩn bị phần còn lại. OAuth/MongoDB vẫn là cấu hình riêng của nhóm.

Launcher chạy `latex_backend.compilation.tex_runtime --ensure` sau khi cài dependency Python, trước UI/server. Nếu bộ TeX được chọn đã đáp ứng profile, chỉ kiểm tra và dùng lại, không tải lại mỗi lần. Nếu thiếu, tải bản TinyTeX native đã pin và kiểm SHA-256, giải nén vào `target/tex-runtime/TinyTeX`, cài package bằng tlmgr, sửa fontconfig cho đường dẫn hiện tại, compile tiếng Việt qua pdfLaTeX/XeLaTeX/LuaLaTeX và ghi marker xác nhận. Không có marker hợp lệ thì runtime chưa được coi là sẵn sàng. Setup có lock để ngăn hai launcher cùng sửa bộ TeX. Không sửa bộ TeX hệ thống hay xóa bộ TinyTeX cũ.

Profile có `titling` và `abstract` cho ZIP sử dụng `LTJournalArticle.cls`, kèm kiểm tra sự tồn tại của cả hai tệp `.sty`. Khi cập nhật profile, marker cũ tự mất hiệu lực để lần khởi động sau cài phần còn thiếu. Có thể chuẩn bị ngay bằng `.\scripts\setup-tex.bat` trên Windows, rồi bấm Biên dịch lại trong dự án đang mở; không cần nhập lại ZIP.

Lần đầu tải archive khoảng 150–250 MB tùy hệ điều hành, cộng các package bổ sung; bản cài thực tế cần nhiều dung lượng hơn file nén. Cho phép kết nối HTTPS tới GitHub và repository TeX. Một số policy của doanh nghiệp có thể chặn executable; script báo lỗi thật, không thay đổi policy bảo vệ máy.

Pin hiện tại: TinyTeX **2026.11**, archive/checksum và package profile ở `apps/backend/src/latex_backend/resources/tex-runtime.json`. Windows x64, Linux x86_64/ARM64 và macOS có archive riêng. Windows ARM64 native chưa có archive trong profile, cần bộ TeX native do operator chọn. Không dùng binary Windows cho Mac/Linux.

Có thể chuẩn bị/kiểm tra riêng, không chạy web và không đọc MongoDB:

```powershell
.\scripts\setup-tex.bat
.\scripts\setup-tex.bat --check
.\scripts\setup-tex.bat --check --smoke
.\scripts\setup-tex.bat --check --smoke-catalog
```

`--check` chỉ đọc, exit 1 nếu thiếu. `--smoke` compile một tài liệu tiếng Việt bằng cả ba engine; `--smoke-catalog` compile 31 template nội bộ/menu ở cả EN và VI, có hình, TikZ, Beamer, Biber và tài liệu nhiều file. `--managed` yêu cầu dùng bộ TeX riêng ngay cả khi đã có TeX hệ thống. `--full` cài thêm `scheme-full`, tải lớn, dành cho người quản trị chuẩn bị image. Dừng backend trước khi chủ động nâng cấp/cài thêm package.

Thứ tự chọn: `--latexmk`/`OVERLEAF_LATEXMK` do operator chỉ định → TinyTeX managed → PATH → TinyTeX cũ/MacTeX. Nếu đã chỉ định một đường dẫn nhưng bộ đó chưa đủ, script báo sửa cấu hình đó hoặc bỏ override; không âm thầm sửa TeX hệ thống. Chuyển máy/đổi năm TeX Live cần chuẩn bị lại runtime, không ghép package của năm mới vào binary năm cũ. Khi repository chuyển năm, cập nhật archive/checksum/profile trong source qua quy trình kiểm thử rồi phát hành image mới; có `LATEX_TEX_REPOSITORY` cho mirror HTTPS nội bộ phù hợp cùng năm.

## Chuẩn bị server trước khi có người dùng

Quy trình release nên thực hiện:

1. Build bộ TeX vào image/volume chuẩn bị riêng, không cài trong request của người dùng.
2. Kiểm `--check --smoke` và `--check --smoke-catalog`; lỗi nào thì dừng release.
3. Khởi động backend bằng entrypoint web có kiểm TeX trước khi bind HTTP. `--skip-tex-check` chỉ dành cho dev UI không compile, không dùng để vượt kiểm tra trong release.
4. Đưa máy chủ vào nhận traffic sau khi runtime và các dịch vụ dữ liệu sẵn sàng. Mỗi worker dùng cùng image TeX đã nghiệm thu. Người dùng chỉ mở URL web.

Base image mới cho môi trường compile:

```sh
docker build -f deploy/python/Dockerfile.tex-runtime -t latex-tex-runtime .
docker run --rm latex-tex-runtime
# Nếu cho phép người dùng nhập ZIP chứa nhiều package ngoài profile:
docker build -f deploy/python/Dockerfile.tex-runtime --build-arg TEX_PROFILE=full -t latex-tex-runtime:full .
```

Docker build cài package và chạy toàn bộ template trước khi tạo image thành công. Image này là **base môi trường compile**, không mở port và không thay Dockerfile TCP/TLS cũ. Image web/worker dùng base này rồi thêm UI, user chạy process, MongoDB/OAuth, volumes và entrypoint triển khai. Đây chưa phải bộ triển khai public Internet hoàn chỉnh. Trên máy nghiệm thu Docker Desktop đang tắt nên chưa build/chạy được image Linux; Windows provisioning và compile thật đã kiểm chứng. Không ghi credential vào image; `.dockerignore` loại `target`, venv, config riêng và key khỏi build context.

Pin image đã build bằng digest trong môi trường release để các worker dùng cùng phiên bản. `scheme-full` giảm lỗi thiếu package CTAN khi nhận ZIP tùy ý, nhưng không sửa được tài liệu sai cú pháp, file `.cls/.sty` riêng bị thiếu trong ZIP, font thương mại hoặc công cụ ngoài chưa cấu hình. Khi gặp trường hợp đó, operator bổ sung dependency vào image/profile đã kiểm thử; không để browser gọi trình quản lý package với tên gói tùy ý.

## Thông báo trong app

Nếu runtime bị hỏng hoặc chưa chuẩn bị trên server, dialog tạo dự án thông báo **Máy chủ chưa sẵn sàng biên dịch LaTeX**, nêu người dùng trình duyệt không cần cài TeX. Tên package nằm trong mục chi tiết. Khi server được chuẩn bị, bấm tạo lại sẽ kiểm preflight mới và tiếp tục; không tạo project dở dang khi preflight lỗi. Web vẫn không cho người dùng gọi `/api/install` để cài package hệ thống.

## Kết quả kiểm tra 12/11/2026

- Tải và kiểm SHA-256 TinyTeX Windows từ archive chính thức, cài package thật trong thư mục managed mới.
- Sửa font đường dẫn máy build; tiếng Việt compile thành PDF bằng cả ba engine.
- **62 lượt compile thành công**: 31 template × 2 ngôn ngữ. Log: `target/tex-catalog-20261112.log`.
- **251 tests backend/scripts Windows đạt**, gồm archive/checksum/path escape, chọn runtime, lock, không sửa TeX được cấu hình riêng, fontconfig và marker validation.
- Production UI build/typecheck đạt. Browser: giả lập server thiếu gói không yêu cầu cài ở client, chặn tạo project; sau khi preflight sẵn sàng tạo project và compile PDF thật. `target/tex-preflight-20261112-01/report.json`.

Nguồn: [TinyTeX chính thức](https://github.com/rstudio/tinytex-releases), [hướng dẫn TinyTeX/tlmgr](https://yihui.org/tinytex/). Các bản TinyTeX dùng giấy phép phân phối của TeX Live; giữ license đi kèm trong archive/image, không commit hàng GB runtime vào Git.

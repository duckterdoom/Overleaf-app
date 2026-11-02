# Prompt: bổ sung thư viện mẫu dự án (Project Templates) cho Overleaf Desktop

> Dán toàn bộ nội dung dưới đây cho AI lập trình (Claude Code, Codex, Cursor...) đang mở repo
> `duckterdoom/Overleaf-app`, nhánh `Network`.

---

## 1. Bối cảnh

Dự án là **Overleaf Desktop** (Java 21, JavaFX, Maven 4 module: `client`, `common`, `server`, `persistence`).
Mã nằm trong `modules/*/src/main/java`. **Đọc trước** `docs/AI_Handoff.md` (đặc biệt **mục 22 – tạo/nhập dự án**
và mục về compiler/TinyTeX) và `docs/DANH_GIA_SO_SANH_OVERLEAF.md`.

Menu **Dự án mới / New project** hiện đã giống Overleaf web:

- Dự án trống, Nhập tài liệu (ZIP, Word, Markdown, GitHub), Dự án mẫu, **Thêm mẫu dự án** (submenu 9 nhóm + *Xem tất cả*), Thư mục mặc định, Thêm đơn vị.
- 9 nhóm: Journal articles, Books, Formal letters, Assignments, Posters, Presentations, Reports, CVs and résumés, Theses.

## 2. Vấn đề cần sửa

So với Overleaf web (https://www.overleaf.com/latex/templates), menu thì đủ nhưng **nội dung mẫu còn rất mỏng**:

1. Mỗi nhóm chỉ sinh **một bộ khung ~10–20 dòng** trong `main.tex`
   (`ProjectTemplates.files()` dùng một `switch` hard-code, trả về `Map<String,String>`; chỉ riêng `example` có nhiều file).
   Bấm vào mẫu nào cũng ra tài liệu gần như trống, không có nội dung demo để người dùng thấy cấu trúc.
2. **Mỗi nhóm chỉ có 1 mẫu.** Overleaf web có nhiều mẫu cụ thể trong mỗi nhóm (bài báo hội nghị, báo cáo thực tập, đồ án tốt nghiệp, CV hiện đại...).
3. "Xem tất cả" (`HomeView.templateGallery()`) chỉ là danh sách nút 9 nhóm, chưa phải thư viện có tìm kiếm, mô tả, lọc.
4. Mẫu chưa có nhiều file (chương, `.bib`, hình), chưa có dữ liệu mẫu nên không thể thử ngay compile + citation + hình + bảng.

**Mục tiêu:** khi người dùng bấm một mẫu, app **tự sinh sẵn một dự án đầy đủ, compile được ngay lần đầu**, có nội dung mẫu đúng loại tài liệu.

## 3. Yêu cầu thiết kế

### 3.1 Chuyển danh mục mẫu sang dạng dữ liệu (không hard-code trong `switch`)

- Mỗi mẫu là một thư mục resource, ví dụ `modules/client/src/main/resources/templates/<category>/<id>/` gồm:
  - `template.json`: `id`, `category`, `title.en`, `title.vi`, `description.en`, `description.vi`, `tags[]`, `engine` mặc định (`pdflatex|xelatex|lualatex`), `mainFile`, `requiredPackages[]`, `needsBiber` (bool).
  - Các file nguồn thật (`main.tex`, `chapters/*.tex`, `references.bib`, `figures/*`...).
  - Cho phép placeholder: `{{TITLE}}`, `{{AUTHOR}}`, `{{AFFILIATION}}`, `{{DATE}}`; thay thế bằng `ProjectTemplates.escape()` để tránh lỗi ký tự LaTeX.
- Giữ nguyên API công khai `ProjectTemplates.all()`, `ProjectTemplates.files(id, title, vi, affiliation)`, `escape()`; mở rộng `Template` record thêm `category`, `description`, `tags`, `requiredPackages`.
- Tên nhóm vẫn dùng đúng 9 id hiện có (`journal`, `books`, `letters`, `assignments`, `posters`, `presentations`, `reports`, `cv`, `theses`) làm **category**; mỗi category chứa nhiều mẫu con.

### 3.2 Danh mục mẫu cần bổ sung (tác giả tự viết, mỗi mẫu có nội dung demo thật)

| Nhóm | Mẫu cần có (tối thiểu) |
|---|---|
| Journal articles | Bài báo tổng quát (abstract, keywords, tài liệu tham khảo); bài báo hội nghị 2 cột; bài tổng quan (review); bài ngắn (short communication) |
| Books | Sách có `\part`/`\chapter`; giáo trình/lecture notes (định lý, ví dụ, bài tập); sách ảnh/tư liệu ngắn |
| Formal letters | Thư trang trọng; thư xin việc (cover letter); thư giới thiệu; **đơn xin việc / đơn xin phép theo mẫu hành chính Việt Nam** |
| Assignments | Bài tập về nhà có đề và lời giải; đề kiểm tra; báo cáo thí nghiệm |
| Posters | Poster A0/A1 dọc; poster A3 ngang nhiều cột (không cần gói ngoài nặng) |
| Presentations | Beamer cơ bản; Beamer có nhiều theme (Madrid, Berlin...); bài thuyết trình tiếng Việt có TikZ |
| Reports | Báo cáo kỹ thuật; **báo cáo thực tập**; báo cáo dự án nhóm |
| CVs and résumés | CV hiện đại 1 cột; CV học thuật; sơ yếu lý lịch tiếng Việt |
| Theses | **Đồ án/luận văn có đủ phần đầu**: bìa, lời cảm ơn, tóm tắt, mục lục, danh mục hình, bảng, từ viết tắt; chương 1–5; phụ lục; `biblatex` + `references.bib` |

Quy tắc nội dung:
- Chỉ dùng **lớp và gói chuẩn có trong TeX Live/TinyTeX** (`article`, `report`, `book`, `beamer`, `letter`, `scrartcl`, `memoir` nếu có...). Nếu cần gói đặc biệt thì liệt kê trong `requiredPackages` (xem 3.4).
- **Không sao chép** nội dung từ thư viện mẫu của Overleaf web: mỗi mẫu trên đó có giấy phép riêng. Tự viết, hoặc dùng lớp chính thức của nhà xuất bản/hội nghị chỉ khi giấy phép cho phép phân phối kèm app (ghi giấy phép vào `LICENSE-TEMPLATES.md`).
- Mỗi mẫu có **bản tiếng Việt và tiếng Anh** (nội dung demo theo ngôn ngữ UI), đủ dấu, không dùng ký tự giả.
- Mỗi mẫu có `main.tex` dòng đầu là chú thích engine: `% !TeX program = xelatex` (hoặc `pdflatex`/`lualatex`).

### 3.3 Preamble dùng chung, chạy được trên cả 3 engine

Tái sử dụng logic của `LocalBackend.template()` (đã có `iftex`), chuẩn hóa thành một preamble dùng chung:

```latex
\usepackage{iftex}
\ifPDFTeX
  \usepackage[utf8]{inputenc}
  \usepackage[T5]{fontenc}
  \usepackage{lmodern}
\else
  \usepackage{fontspec}   % XeLaTeX / LuaLaTeX
\fi
\usepackage[vietnamese,english]{babel}
```

Không dùng `fontspec` vô điều kiện (đã gây lỗi "fontspec requires XeTeX or LuaTeX" khi chạy pdfLaTeX).

### 3.4 Kiểm tra gói trước khi tạo dự án

- Trước khi tạo, đọc `requiredPackages` và kiểm tra bằng `kpsewhich <pkg>.sty` (hoặc cơ chế sẵn có của `LatexCompilerService`).
- Nếu thiếu và distribution là **TinyTeX đi kèm app**: hỏi người dùng, rồi chạy `tlmgr install <pkg>` ở nền (dùng task nền có hủy giống `ProjectImports`), báo tiến độ.
- Nếu là TeX Live/MiKTeX hệ thống: chỉ báo thông điệp song ngữ, không tự cài.
- Biber: nếu `needsBiber=true` mà thiếu `biber`, báo rõ nguyên nhân thay vì chỉ hiện "exit code 12".

### 3.5 Giao diện thư viện mẫu

- Giữ nguyên menu **Dự án mới → Thêm mẫu dự án** và `id` UI hiện có (`project-templates`, `template-<id>`) để không vỡ `NewProjectUiSmoke`.
- Bấm **một nhóm** trong submenu: mở **thư viện mẫu** lọc theo nhóm đó. Bấm **Xem tất cả**: mở thư viện tất cả mẫu.
- Thư viện mẫu (`templateGallery()` viết lại): lưới thẻ gồm tên, mô tả ngắn, nhãn engine (pdfLaTeX/XeLaTeX/LuaLaTeX), nhãn tag; ô tìm kiếm; bộ lọc nhóm/tag; nút **Tạo dự án**. Có ảnh thu nhỏ nếu làm được (tùy chọn); nếu chưa có thì dùng biểu tượng theo nhóm.
- Bấm **Tạo dự án** mở `newProjectDialog(TEMPLATE, id)` hiện có (chọn tên, thư mục lưu), rồi sinh toàn bộ file, mở ngay `mainFile` và (tùy cài đặt) tự compile lần đầu.
- Mọi chuỗi UI qua cơ chế i18n Anh/Việt hiện có; tên thư viện và mô tả đọc từ `template.json`.

## 4. Ràng buộc kỹ thuật

- Java 21, JavaFX; **không thêm dependency nặng**. JSON: dùng thư viện đã có trong `pom.xml`, nếu chưa có thì dùng Jackson hoặc Gson sau khi báo lý do.
- Không chặn UI thread: sinh file và kiểm tra gói chạy ở task nền như `ProjectImports`.
- Chỉ **dự án mới** nhận mẫu mới; không sửa dự án cũ.
- Ghi file UTF-8; tên thư mục/file có dấu phải hoạt động (đã có test đường dẫn tiếng Việt).
- Không phá hành vi hiện có: 98/98 test chọn lọc, `NewProjectUiSmoke`, `ProjectImportSmoke` phải tiếp tục đạt.
- Không đưa credential, đường dẫn máy cá nhân hay file `target/`, `dist/` vào commit.

## 5. Kiểm thử bắt buộc

1. **Unit test:** mỗi `template.json` hợp lệ (đủ trường, id duy nhất, file `mainFile` tồn tại, placeholder được thay hết, không còn `{{`).
2. **`ProjectImportSmoke` mở rộng:** với **mỗi mẫu** và mỗi ngôn ngữ (en/vi), tạo dự án rồi compile bằng engine mặc định; kiểm tra PDF được tạo, log không có `undefined references` và không có lỗi `!`. Với mẫu chạy được nhiều engine, thử thêm cả ba (pdfLaTeX, XeLaTeX, LuaLaTeX). Chạy cả khi đường dẫn có dấu tiếng Việt.
3. **`NewProjectUiSmoke` mở rộng:** menu hiển thị đủ nhóm Anh/Việt; thư viện mở được, tìm kiếm "luận văn"/"thesis" ra đúng mẫu; bấm **Tạo dự án** sinh đúng cây file và mở `main.tex`.
4. **Thiếu gói:** mô phỏng thiếu một gói trong `requiredPackages`, xác nhận thông báo song ngữ đúng, và đường tự cài TinyTeX hoạt động (có thể mock `tlmgr`).
5. Cập nhật `docs/AI_Handoff.md` (mục 22 hoặc mục mới): cấu trúc `templates/`, cách thêm một mẫu mới, bảng mẫu và kết quả test; cập nhật `README.md` phần "Tạo hoặc nhập dự án".

## 6. Tiêu chí hoàn thành

- [ ] Mỗi trong 9 nhóm có **ít nhất 2–4 mẫu**, tổng ≥ 25 mẫu, có tiếng Việt và tiếng Anh.
- [ ] Bấm bất kỳ mẫu nào → dự án nhiều file sinh ra, **compile thành công lần đầu** (không sửa tay).
- [ ] Thư viện mẫu có tìm kiếm, lọc theo nhóm/tag, mô tả, nhãn engine.
- [ ] Thiếu gói hoặc thiếu Biber thì báo lỗi rõ, có đường tự cài trên TinyTeX.
- [ ] Toàn bộ test cũ và test mới đạt; tài liệu được cập nhật.
- [ ] Không còn `switch` hard-code nội dung mẫu trong `ProjectTemplates` (chỉ còn bộ nạp và thay placeholder).

## 7. Cách làm đề xuất

1. Đọc code: `ProjectTemplates.java`, `HomeView` (khoảng dòng 860–950, `templateGallery()`, `newProjectDialog`), `LocalBackend.template()`, `NewProjectRequest`, `ProjectImports`, các smoke test liên quan.
2. Làm bộ nạp mẫu từ resource + `template.json`, chuyển **9 mẫu cũ** sang dạng mới trước, đảm bảo test cũ vẫn đạt.
3. Viết thêm mẫu cho từng nhóm, mỗi nhóm một commit.
4. Làm lại thư viện mẫu UI.
5. Làm kiểm tra gói + tự cài TinyTeX.
6. Viết test và tài liệu, chạy toàn bộ test, báo lại: danh sách mẫu, kết quả compile từng mẫu, những gì chưa kiểm được.

Khi có điểm chưa chắc (giấy phép lớp tài liệu, gói không có trong TinyTeX, hành vi UI), **hỏi lại hoặc ghi rõ giả định** thay vì tự quyết định.

# Thư viện mẫu dự án

Thư viện gồm **29 mẫu tự viết, đủ nội dung Anh/Việt**, cùng hai starter `blank` và `example`. Vào **Dự án mới → Thêm mẫu dự án**, chọn nhóm hoặc **Xem tất cả**. Tìm kiếm nhận cả chữ có/không dấu; có bộ lọc nhóm và tag. Chọn **Tạo dự án** để đặt tên và thư mục cha như trước.

| Category | Mẫu (`id`) |
| --- | --- |
| `journal` | Bài nghiên cứu (`research-article`), hội nghị hai cột (`conference-paper`), tổng quan (`review-article`), bài ngắn (`short-communication`) |
| `books` | Sách chia phần/chương (`structured-book`), giáo trình (`lecture-notes`), sách tư liệu minh họa (`photo-book`) |
| `letters` | Thư trang trọng (`formal-letter`), ứng tuyển (`cover-letter`), giới thiệu (`recommendation-letter`), đơn hành chính Việt Nam (`vietnamese-application`) |
| `assignments` | Bài tập/lời giải (`homework-solutions`), đề kiểm tra (`exam-paper`), báo cáo thí nghiệm (`lab-report`) |
| `posters` | A0 dọc (`poster-a0`), A1 dọc (`poster-a1`), A3 ngang ba cột (`poster-a3`) |
| `presentations` | Beamer cơ bản (`beamer-basic`), Madrid (`beamer-madrid`), Berlin (`beamer-berlin`), thuyết trình TikZ (`beamer-tikz`) |
| `reports` | Kỹ thuật (`technical-report`), thực tập (`internship-report`), dự án nhóm (`team-project`) |
| `cv` | Một cột (`modern-cv`), học thuật (`academic-cv`), sơ yếu lý lịch (`vietnamese-resume`) |
| `theses` | Đồ án tốt nghiệp (`graduation-project`), luận văn thạc sĩ (`masters-thesis`) |

Luận văn/đồ án có bìa, cảm ơn, tóm tắt, mục lục, danh mục hình/bảng, từ viết tắt, 5 chương, phụ lục và BibLaTeX/Biber. Các mẫu khác có cấu trúc phù hợp loại tài liệu; chương/section, slide hoặc nội dung thư nằm trong tệp con. Có ảnh PNG tự tạo, CSV và `.bib` minh họa. Dữ liệu và tài liệu tham khảo là giả định, cần thay trước khi sử dụng thực tế. Giấy phép: [LICENSE-TEMPLATES.md](../LICENSE-TEMPLATES.md).

## Cấu trúc resource

```text
modules/client/src/main/resources/templates/
├── catalog.json                    # 9 nhóm và danh sách thư mục mẫu
├── common/en/preamble.tex           # iftex + font + các package dùng chung
├── common/vi/preamble.tex
├── journal/research-article/
│   ├── template.json
│   ├── en/main.tex
│   ├── en/chapters/*.tex
│   ├── en/figures/results.png
│   ├── en/data/results.csv
│   ├── en/references.bib
│   └── vi/…                        # Cùng tên file, nội dung tiếng Việt
└── internal/{blank,example}/…
```

`ProjectTemplates` chỉ nạp dữ liệu, kiểm manifest và thay placeholder. Nội dung tài liệu không còn nằm trong `switch` Java. Các API `all()`, `files(id, title, vi, affiliation)` và `escape()` vẫn có; `all()` trả về 29 mẫu, còn `categories()` trả về 9 nhóm. ID nhóm cũ như `journal`, `theses` vẫn được `files/get` ánh xạ tới mẫu đầu nhóm để tương thích caller cũ.

`files()` giữ kiểu `Map<String,String>`, trả về nguồn text và preamble. `materialize()` ghi cả text và binary PNG, kèm `.overleaf-template.properties` cho engine/mainFile mặc định. Không chuyển binary thành text hoặc base64 trong dự án xuất ra. Tất cả mẫu hiện dùng `main.tex` làm mainFile.

## Thêm một mẫu mới

1. Tạo thư mục `<category>/<id>` và hai cây `en/`, `vi/`, cùng tên file. Viết nội dung gốc hoặc bảo đảm quyền phân phối; cập nhật giấy phép nếu cần.
2. Tạo `template.json` theo cấu trúc sau. Bổ sung đầy đủ file vào `files`; `preamble.tex` được chép từ `common/<language>/` khi `sharedPreamble=true`, nên không đưa nó vào cây nguồn riêng.

```json
{
  "id": "new-report",
  "category": "reports",
  "title": {"en": "New report", "vi": "Báo cáo mới"},
  "description": {"en": "A worked report.", "vi": "Báo cáo có nội dung minh họa."},
  "tags": ["engineering", "multi-file"],
  "engine": "pdflatex",
  "mainFile": "main.tex",
  "requiredPackages": ["iftex", "inputenc", "fontenc", "lmodern", "fontspec", "babel", "babel-vietnamese", "vntex", "amsmath", "amssymb", "amsthm", "graphicx", "xcolor", "hyperref"],
  "needsBiber": false,
  "files": ["main.tex", "sections/introduction.tex"],
  "sharedPreamble": true
}
```

3. Thêm `<category>/<id>` vào mảng `templates` trong `catalog.json`. Index này hoạt động cả khi resource nằm trong JAR; không phụ thuộc việc liệt kê thư mục classpath.
4. Dòng đầu `main.tex` phải khớp engine: `% !TeX program = pdflatex` (hoặc xelatex/lualatex). Dùng `\input{preamble}` sau documentclass. Dùng placeholder `{{TITLE}}`, `{{AUTHOR}}`, `{{AFFILIATION}}`, `{{DATE}}`; giá trị được escape LaTeX trước khi thay. Không dùng placeholder lạ.
5. Khai báo package thực sự dùng. Kiểm tra thường bằng `kpsewhich <name>.sty`; một số ánh xạ đã có như `beamer` → `.cls`, `vntex` → `t5enc.def`, `babel-vietnamese` → `vietnamese.ldf`, `lmodern` → gói tlmgr `lm`, `amssymb` → `amsfonts`, `graphicx` → `graphics`. Nếu thêm gói có tên file khác tên gói phân phối, bổ sung ánh xạ kèm test trong `TemplatePrerequisites`.
6. Chạy `ProjectTemplatesTest`, `TemplatePrerequisitesTest`, `NewProjectUiSmoke` và ma trận `ProjectImportSmoke` trước khi phát hành.

## Kiểm tra môi trường trước khi tạo

Luồng tạo TEMPLATE kiểm package, engine mặc định, latexmk và Biber khi cần trên **đúng bản TeX đang chọn trong Settings**. Công việc kiểm tra/sinh file ở worker, không chạy trên FX thread. Nếu thiếu:

- **TinyTeX đi kèm:** hiển thị danh sách và hỏi **Cài gói**; chỉ click xác nhận mới chạy `tlmgr install` nền, báo package đang cài, có Hủy. Tạo dự án được thử lại sau đó và kiểm tra lại môi trường.
- **TeX hệ thống hoặc chưa có TeX:** báo thiếu bằng Anh/Việt, hướng dẫn người dùng cài/chọn lại toolchain; không sửa môi trường hệ thống.

Bundled TinyTeX được nhận qua đường dẫn thật dưới `target/TinyTeX`, `TinyTeX` hoặc `tools/TinyTeX` ở cây app, có `texmf-dist` và launcher tlmgr. Không tự nhận một TinyTeX bất kỳ trong AppData là bản đi kèm. Lệnh tlmgr nhận argv/package đã kiểm tra, không nhận shell command từ JSON.

Engine mặc định được áp dụng khi mở dự án mới tạo; Auto Compile chỉ chạy nếu người dùng đã bật. Không đổi nội dung hoặc chuyển đổi các dự án cũ. Máy khác vẫn cần có TeX hoặc bộ runtime được phát hành kèm; thư viện resource này không nhúng toàn bộ TeX Live.

## Kết quả trên Windows

- **143 JUnit chọn lọc đạt**, gồm schema/resources/placeholder/binary, tìm kiếm/alias, missing package/Biber, chặn cài hệ thống, mock tlmgr/recheck/cancel, và hồi quy hiện có.
- **174/174 build thật đạt:** 29 mẫu × en/vi × pdfLaTeX/XeLaTeX/LuaLaTeX trong đường dẫn tiếng Việt. Kiểm PDF và final TeX log: không còn undefined reference/citation hay lỗi `!`.
- Kết quả từng mẫu/ngôn ngữ/engine: [TEMPLATE_VALIDATION.md](TEMPLATE_VALIDATION.md).
- `ProjectImportSmoke` mở rộng vẫn đạt phần example, Word, Markdown và clone GitHub công khai.
- `NewProjectUiSmoke` mở rộng đạt 9 nhóm, tìm `luận văn`/`thesis`, lọc tag, tạo đúng cây luận văn và chọn XeLaTeX. Mock UI xác nhận không cài trước khi đồng ý/hủy và chỉ cài sau nút xác nhận.
- Đây là kiểm thử trên Windows/TinyTeX của máy làm việc; chưa chạy native matrix trên macOS/Linux. Không có tải gói thật qua UI trong ca test thiếu gói: dùng mock tlmgr theo yêu cầu để tránh thay đổi TeX chỉ nhằm tạo lỗi.

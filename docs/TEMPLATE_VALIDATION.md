# Kiểm chứng biên dịch thư viện mẫu

Ngày 02/11/2026, Windows, TinyTeX portable, compiler service thật của ứng dụng.

Mỗi ô PASS là một lần tạo dự án mới theo ngôn ngữ tương ứng, compile bằng engine ghi ở cột, kiểm tra PDF có trang và final TeX log không có lỗi `!`, undefined reference/citation hoặc yêu cầu chạy lại Biber. Đường dẫn dự án có tiếng Việt. Tổng **174/174 PASS**. Đây không phải benchmark tốc độ hoặc kết quả chạy trên macOS/Linux.

| ID mẫu | EN pdfLaTeX | EN XeLaTeX | EN LuaLaTeX | VI pdfLaTeX | VI XeLaTeX | VI LuaLaTeX |
| --- | --- | --- | --- | --- | --- | --- |
| research-article | PASS | PASS | PASS | PASS | PASS | PASS |
| conference-paper | PASS | PASS | PASS | PASS | PASS | PASS |
| review-article | PASS | PASS | PASS | PASS | PASS | PASS |
| short-communication | PASS | PASS | PASS | PASS | PASS | PASS |
| structured-book | PASS | PASS | PASS | PASS | PASS | PASS |
| lecture-notes | PASS | PASS | PASS | PASS | PASS | PASS |
| photo-book | PASS | PASS | PASS | PASS | PASS | PASS |
| formal-letter | PASS | PASS | PASS | PASS | PASS | PASS |
| cover-letter | PASS | PASS | PASS | PASS | PASS | PASS |
| recommendation-letter | PASS | PASS | PASS | PASS | PASS | PASS |
| vietnamese-application | PASS | PASS | PASS | PASS | PASS | PASS |
| homework-solutions | PASS | PASS | PASS | PASS | PASS | PASS |
| exam-paper | PASS | PASS | PASS | PASS | PASS | PASS |
| lab-report | PASS | PASS | PASS | PASS | PASS | PASS |
| poster-a0 | PASS | PASS | PASS | PASS | PASS | PASS |
| poster-a1 | PASS | PASS | PASS | PASS | PASS | PASS |
| poster-a3 | PASS | PASS | PASS | PASS | PASS | PASS |
| beamer-basic | PASS | PASS | PASS | PASS | PASS | PASS |
| beamer-madrid | PASS | PASS | PASS | PASS | PASS | PASS |
| beamer-berlin | PASS | PASS | PASS | PASS | PASS | PASS |
| beamer-tikz | PASS | PASS | PASS | PASS | PASS | PASS |
| technical-report | PASS | PASS | PASS | PASS | PASS | PASS |
| internship-report | PASS | PASS | PASS | PASS | PASS | PASS |
| team-project | PASS | PASS | PASS | PASS | PASS | PASS |
| modern-cv | PASS | PASS | PASS | PASS | PASS | PASS |
| academic-cv | PASS | PASS | PASS | PASS | PASS | PASS |
| vietnamese-resume | PASS | PASS | PASS | PASS | PASS | PASS |
| graduation-project | PASS | PASS | PASS | PASS | PASS | PASS |
| masters-thesis | PASS | PASS | PASS | PASS | PASS | PASS |

Bằng chứng local (không đưa vào Git): `target/template-import-smoke.log`, `target/template-import-smoke-03/template-matrix/evidence/results.csv` và log đầy đủ theo từng template/ngôn ngữ/engine trong thư mục evidence. `ProjectImportSmoke` sau ma trận còn xác nhận example, Word, Markdown và GitHub; `NewProjectUiSmoke` xác nhận tạo mẫu qua thư viện và luồng xác nhận cài gói giả lập.

# Latex backend

Backend Python của ứng dụng web nằm tại `src/latex_backend/`.

| Thư mục | Trách nhiệm |
| --- | --- |
| `entrypoints/` | Điểm chạy web, desktop tùy chọn và console |
| `core/` | Cấu hình và khởi tạo ứng dụng |
| `auth/` | Tài khoản, role Tutor/Student và phiên đăng nhập MongoDB |
| `http/` | FastAPI, middleware và HTTP endpoints |
| `storage/` | Project, file, upload/import, settings, history và repository |
| `compilation/` | TeX, job biên dịch, SyncTeX và giải thích lỗi |
| `documents/` | Word/DOCX và báo cáo IELTS |
| `collaboration/` | Review/comment và networking TCP/TLS cũ |
| `pdf_export/` | Bộ chuyển PDF sang DOCX |
| `resources/` | Template và rule lỗi |

Web gateway `http/web_accounts.py` cấp workspace riêng theo tài khoản. `storage/mongo.py` và `mongo_store.py` là persistence chính; `storage/migration.py` chuyển dữ liệu cũ có backup/verify. Xem [MONGODB_PRIMARY.md](../../docs/MONGODB_PRIMARY.md) trước khi chạy. Google cần OAuth config như trước; dữ liệu shared cũ không tự gán cho người đăng ký đầu tiên. SQLite chỉ còn là nguồn migration và tương thích desktop cũ.

Chạy từ root repository:

```powershell
.\scripts\run-python-web.bat
```

Hoặc sau khi cài dependency:

```powershell
apps\backend\.venv\Scripts\python.exe -m latex_backend.entrypoints.web
apps\backend\.venv\Scripts\python.exe -m pytest apps/backend/tests -q
```

Không lưu dự án người dùng vào source; cấu hình `LATEX_DATA_DIR` cho server. `.venv`, `__pycache__`, `.pytest_cache` và `*.egg-info` là môi trường/cache sinh tự động, không thuộc source.

Chi tiết: [kiến trúc backend](../../docs/WEB_BACKEND_ARCHITECTURE.md), [hướng dẫn web](../../docs/LOCAL_WEB.md).

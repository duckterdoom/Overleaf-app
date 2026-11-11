"""
Legacy interop smoke test: Python clients vs Java TCP server.

Java source (modules/) đã được xóa khỏi repository theo kế hoạch dọn dẹp
(docs/JAVA_CLEANUP_PLAN.md, 03/11/2026). Script này được giữ lại làm tài liệu
lịch sử về giao thức tương thích đã kiểm chứng trước khi xóa.

Để chạy lại (nếu cần đối chiếu), cần:
  - Khôi phục compiled Java classes từ backup/commit trước khi xóa (e9424cf trên nhánh Network)
  - Đặt JAVA_HOME trỏ tới JDK 21+
  - Chạy: python apps/backend/tests/smoke_java_interop.py <home-dir>

Kết quả đã ghi nhận (03/11/2026, §33 AI_Handoff.md):
  JAVA_SERVER_PYTHON_CLIENTS_OK AUTH, UTF16, presence, ACK, offline, RESYNC
"""

import sys

if __name__ == "__main__":
    print(
        "smoke_java_interop.py: Java source đã xóa theo JAVA_CLEANUP_PLAN.md. "
        "Xem comment đầu file để khôi phục và chạy lại nếu cần.",
        file=sys.stderr,
    )
    sys.exit(0)


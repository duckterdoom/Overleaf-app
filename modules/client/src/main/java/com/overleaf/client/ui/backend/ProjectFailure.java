package com.overleaf.client.ui.backend;

/**
 * Stable bilingual messages for the project/import flows; raw tool diagnostics remain in the cause.
 */
public final class ProjectFailure extends IllegalStateException {
    public enum Code {
        IO(
                "Could not access the project files. Check the folder and permissions.",
                "Không truy cập được tệp dự án. Hãy kiểm tra thư mục và quyền truy cập."),
        DIRECTORY(
                "Choose a writable folder outside the project being moved.",
                "Chọn thư mục có quyền ghi và nằm ngoài dự án đang di chuyển."),
        EXISTS(
                "A folder with this name already exists. Choose another name or location.",
                "Đã có thư mục trùng tên. Hãy chọn tên hoặc vị trí khác."),
        ZIP(
                "The ZIP archive is invalid or contains unsafe paths, duplicates or symbolic links.",
                "Tệp ZIP không hợp lệ hoặc chứa đường dẫn không an toàn, trùng lặp hay liên kết tượng trưng."),
        LIMIT(
                "The import exceeds 10,000 files or 500 MiB of extracted data.",
                "Dự án nhập vượt quá 10.000 tệp hoặc 500 MiB dữ liệu giải nén."),
        NO_TEX(
                "No root LaTeX document was found. The archive/repository must contain a .tex file with documentclass.",
                "Không tìm thấy tài liệu LaTeX gốc. ZIP/kho mã cần có tệp .tex khai báo documentclass."),
        REPOSITORY(
                "Enter an HTTPS GitHub repository URL, without credentials or query parameters.",
                "Nhập URL kho GitHub dạng HTTPS, không kèm thông tin đăng nhập hoặc tham số truy vấn."),
        GIT(
                "Could not clone the repository. Check Git, the URL, network and access permissions.",
                "Không sao chép được kho mã. Kiểm tra Git, URL, kết nối mạng và quyền truy cập."),
        PANDOC(
                "Pandoc is required. Install it on PATH or place pandoc.exe in target/pandoc/.",
                "Cần Pandoc để chuyển đổi. Cài Pandoc vào PATH hoặc đặt pandoc.exe trong target/pandoc/."),
        CONVERSION(
                "Could not convert the document. Use a valid .docx or UTF-8 Markdown file.",
                "Không chuyển đổi được tài liệu. Hãy dùng tệp .docx hợp lệ hoặc Markdown UTF-8."),
        CANCELLED("The operation was cancelled.", "Đã hủy thao tác."),
        MOVE(
                "Could not finish moving the project. Your original files were retained.",
                "Không hoàn tất di chuyển dự án. Các tệp gốc vẫn được giữ lại.");
        final String en, vi;

        Code(String en, String vi) {
            this.en = en;
            this.vi = vi;
        }
    }

    private final Code code;

    public ProjectFailure(Code code) {
        this(code, null);
    }

    public ProjectFailure(Code code, Throwable cause) {
        super(code.en, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public String localized(boolean vi) {
        return vi ? code.vi : code.en;
    }

    public static String message(Throwable failure, boolean vi) {
        while ((failure instanceof java.util.concurrent.CompletionException
                        || failure instanceof java.util.concurrent.ExecutionException)
                && failure.getCause() != null) failure = failure.getCause();
        return failure instanceof ProjectFailure known
                ? known.localized(vi)
                : vi ? Code.IO.vi : Code.IO.en;
    }
}

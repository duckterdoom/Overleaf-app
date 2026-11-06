// Legacy workspace strings retained as keys while both desktop languages share UI components.
const english: Record<string, string> = {
    "Đang kết nối…": "Connecting…",
    "Local workspace · Sẵn sàng": "Local workspace · Ready",
    "Không kết nối được backend. Mở lại ứng dụng desktop.":
        "Cannot connect to the backend. Restart the desktop app.",
    "Đã lưu thay đổi trên máy.": "Saved locally.",
    "Dữ liệu được lưu trên máy của bạn.": "Your data is stored locally.",
    "Đang biên dịch…": "Compiling…",
    "Biên dịch thất bại.": "Compilation failed.",
    "Biên dịch chưa thành công.": "Compilation failed.",
    "Nguồn đã đổi. Cần biên dịch lại trước khi xuất.":
        "Source changed. Recompile before exporting.",
    "Đã xuất PDF.": "PDF exported.",
    "Trên máy của bạn": "On your computer",
    "Dự án": "Projects",
    "Đóng thông báo": "Dismiss notification",
    "KHÔNG GIAN LÀM VIỆC": "WORKSPACE",
    "Tất cả dự án": "All projects",
    "Tập trung vào điều bạn viết.": "Focus on what you write.",
    "Tài liệu, bộ biên dịch và PDF đều ở trên máy của bạn.":
        "Your documents, compiler and PDF stay on your computer.",
    "Không cần tài khoản cloud": "No cloud account needed",
    "Dữ liệu local": "Local data",
    "MỘT Ý TƯỞNG MỚI BẮT ĐẦU Ở ĐÂY": "YOUR NEXT IDEA STARTS HERE",
    "Dự án của bạn": "Your projects",
    "Viết, biên dịch và hoàn thiện tài liệu theo cách của bạn.":
        "Write, compile and finish your document your way.",
    "Dự án mới": "New project",
    "KHÔNG GIAN CHO Ý TƯỞNG": "ROOM FOR IDEAS",
    "Từ dòng đầu tiên": "From your first line",
    "đến bản PDF hoàn chỉnh.": "to a finished PDF.",
    "Bắt đầu với trang trống, hoặc chọn từ 29 mẫu tài liệu":
        "Start with a blank page or choose from 29 templates",
    "có sẵn bằng tiếng Việt và tiếng Anh.":
        "available in Vietnamese and English.",
    "Khám phá thư viện mẫu": "Explore templates",
    "Tìm dự án": "Find projects",
    "Tìm dự án…": "Find projects…",
    "Không tìm thấy dự án": "No projects found",
    "Một trang mới đang chờ bạn": "A fresh page awaits",
    "Thử tìm bằng một tên khác.": "Try another project name.",
    "Tạo dự án đầu tiên để bắt đầu viết.":
        "Create your first project to begin writing.",
    "LaTeX local": "Local LaTeX",
    "29 mẫu tài liệu": "29 document templates",
    "Tệp nguồn thuộc về bạn": "Your source files belong to you",
    "Tệp dự án": "Project files",
    "Thêm tệp": "Add files",
    "Thêm thư mục": "Add folder",
    tệp: "files",
    "CẤU TRÚC TÀI LIỆU": "DOCUMENT OUTLINE",
    "Thêm section để thấy cấu trúc.":
        "Add sections to see the document outline.",
    "Thư mục:": "Folder:",
    "Đang biên dịch": "Compiling",
    Xóa: "Clear",
    "Nhật ký biên dịch sẽ hiển thị tại đây.": "Build logs appear here.",
    "Engine biên dịch": "Compile engine",
    "Biên dịch": "Recompile",
    "Dừng biên dịch": "Stop compilation",
    "Xuất PDF": "Export PDF",
    "Chưa lưu": "Unsaved",
    "Đã lưu": "Saved",
    "Tài liệu của bạn · Lưu trên máy": "Your documents · Stored locally",
    "KHÔNG GIAN CHO TÀI LIỆU MỚI": "A NEW HOME FOR YOUR DOCUMENT",
    "Tạo dự án mới": "Create new project",
    Đóng: "Close",
    "Tên dự án": "Project name",
    "Đường dẫn trong dự án": "Path within project",
    "Ví dụ: Luận văn tốt nghiệp": "Example: My thesis",
    "Tìm mẫu": "Find templates",
    "Tìm mẫu tài liệu…": "Find document templates…",
    "Nhóm mẫu": "Template category",
    "Tất cả nhóm": "All categories",
    "Ngôn ngữ tài liệu": "Document language",
    "Dự án trống": "Blank project",
    "Bắt đầu từ trang giấy đầu tiên.": "Start with a blank page.",
    "Dữ liệu được tạo trên máy của bạn.": "Files are created locally.",
    "Đường dẫn tính từ thư mục gốc dự án.":
        "Paths are relative to the project root.",
    Hủy: "Cancel",
    "Tạo dự án": "Create project",
    Tạo: "Create",
};
export function localize(value: string, language: string) {
    if (language !== "en") return value;
    const key = value.replace(/\s+/g, " ").trim();
    return (
        english[key] ||
        value.replace("Biên dịch thành công", "Compiled successfully")
    );
}

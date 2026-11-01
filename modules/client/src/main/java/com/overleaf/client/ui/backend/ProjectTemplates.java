package com.overleaf.client.ui.backend;

import java.util.List;
import java.util.Map;

/** Bundled, editable LaTeX templates; no network or paid gallery is required. */
public final class ProjectTemplates {
    public record Template(String id, String en, String vi) {
        public String title(boolean vietnamese) {
            return vietnamese ? vi : en;
        }
    }

    private static final List<Template> ALL =
            List.of(
                    new Template("journal", "Journal articles", "Bài báo khoa học"),
                    new Template("books", "Books", "Sách"),
                    new Template("letters", "Formal letters", "Thư trang trọng"),
                    new Template("assignments", "Assignments", "Bài tập"),
                    new Template("posters", "Posters", "Áp phích"),
                    new Template("presentations", "Presentations", "Bài thuyết trình"),
                    new Template("reports", "Reports", "Báo cáo"),
                    new Template("cv", "CVs and résumés", "CV và sơ yếu lý lịch"),
                    new Template("theses", "Theses", "Luận văn"));

    private ProjectTemplates() {}

    public static List<Template> all() {
        return ALL;
    }

    private static String t(boolean vi, String en, String text) {
        return vi ? text : en;
    }

    public static Map<String, String> files(
            String id, String title, boolean vi, String affiliation) {
        String preamble = LocalBackend.template(title);
        preamble = preamble.substring(0, preamble.indexOf("\\begin{document}"));
        preamble = preamble.replace("\\author{}", "\\author{" + escape(affiliation) + "}");
        if (vi)
            preamble =
                    preamble.replace("[vietnamese,english]{babel}", "[english,vietnamese]{babel}");
        String intro = t(vi, "Introduction", "Giới thiệu");
        String writing = t(vi, "Start writing here.", "Bắt đầu viết nội dung tại đây.");
        String body = "\\maketitle\n\\section{" + intro + "}\n" + writing + "\n";
        switch (id == null ? "blank" : id) {
            case "blank" -> {}
            case "example" -> {
                body =
                        "\\maketitle\n\\tableofcontents\n\\input{sections/introduction}\n"
                                + "\\section{"
                                + t(vi, "Mathematics", "Toán học")
                                + "}\n\\[E=mc^2\\]\n"
                                + "\\section{"
                                + t(vi, "References", "Tài liệu tham khảo")
                                + "}\n\\cite{example}\n"
                                + "\\bibliographystyle{plain}\n\\bibliography{references}\n";
                return Map.of(
                        "main.tex",
                        preamble + "\\begin{document}\n" + body + "\\end{document}\n",
                        "sections/introduction.tex",
                        "\\section{" + intro + "}\n" + writing + "\n",
                        "references.bib",
                        "@book{example, author={"
                                + t(vi, "Example Author", "Tác giả Mẫu")
                                + "}, title={"
                                + t(vi, "Example Reference", "Tài liệu tham khảo mẫu")
                                + "}, year={2026}}\n");
            }
            case "journal" ->
                    body =
                            "\\maketitle\n\\begin{abstract}\n"
                                    + t(
                                            vi,
                                            "Write the abstract here.",
                                            "Viết phần tóm tắt tại đây.")
                                    + "\n\\end{abstract}\n\\section{"
                                    + intro
                                    + "}\n"
                                    + writing
                                    + "\n\\section{"
                                    + t(vi, "Methods", "Phương pháp")
                                    + "}\n\\section{"
                                    + t(vi, "Results", "Kết quả")
                                    + "}\n";
            case "books", "reports", "theses" -> {
                preamble =
                        preamble.replace(
                                "\\documentclass{article}",
                                "\\documentclass{"
                                        + (id.equals("books") ? "book" : "report")
                                        + "}");
                body =
                        "\\maketitle\n\\tableofcontents\n\\chapter{"
                                + intro
                                + "}\n"
                                + writing
                                + "\n\\chapter{"
                                + t(vi, "Main content", "Nội dung chính")
                                + "}\n"
                                + "\\chapter{"
                                + t(vi, "Conclusion", "Kết luận")
                                + "}\n";
                if (id.equals("theses"))
                    body += "\\appendix\n\\chapter{" + t(vi, "Appendix", "Phụ lục") + "}\n";
            }
            case "letters" -> {
                preamble = preamble.replace("\\documentclass{article}", "\\documentclass{letter}");
                preamble =
                        preamble.lines()
                                .filter(s ->
                                        !s.startsWith("\\title{") && !s.startsWith("\\author{"))
                                .reduce("", (a, b) -> a + b + "\n");
                body =
                        "\\address{"
                                + escape(affiliation)
                                + "}\n\\signature{"
                                + t(vi, "Your name", "Họ và tên")
                                + "}\n\\begin{letter}{"
                                + t(vi, "Recipient", "Người nhận")
                                + "}\n\\opening{"
                                + t(vi, "Dear Sir or Madam,", "Kính gửi Quý đơn vị,")
                                + "}\n"
                                + writing
                                + "\n\\closing{"
                                + t(vi, "Sincerely,", "Trân trọng,")
                                + "}\n\\end{letter}\n";
            }
            case "assignments" ->
                    body =
                            "\\maketitle\n\\begin{enumerate}\n\\item "
                                    + t(
                                            vi,
                                            "Solve the equation $x^2-4=0$.",
                                            "Giải phương trình $x^2-4=0$.")
                                    + "\n\\item "
                                    + t(
                                            vi,
                                            "Write your solution here.",
                                            "Trình bày lời giải tại đây.")
                                    + "\n\\end{enumerate}\n";
            case "posters" -> {
                preamble += "\\usepackage[a3paper,landscape,margin=2cm]{geometry}\n";
                body =
                        "\\maketitle\n\\Large\n\\begin{minipage}[t]{0.45\\textwidth}\n\\section{"
                                + intro
                                + "}\n"
                                + writing
                                + "\n\\end{minipage}\\hfill\n\\begin{minipage}[t]{0.45\\textwidth}\n\\section{"
                                + t(vi, "Results", "Kết quả")
                                + "}\n"
                                + writing
                                + "\n\\end{minipage}\n";
            }
            case "presentations" -> {
                preamble = preamble.replace("\\documentclass{article}", "\\documentclass{beamer}");
                body =
                        "\\begin{frame}\\titlepage\\end{frame}\n\\begin{frame}{"
                                + intro
                                + "}\n\\begin{itemize}\n\\item "
                                + writing
                                + "\n\\item $E=mc^2$\n\\end{itemize}\n\\end{frame}\n";
            }
            case "cv" ->
                    body =
                            "\\maketitle\n\\section*{"
                                    + t(vi, "Contact", "Thông tin liên hệ")
                                    + "}\nemail@example.com\n"
                                    + "\\section*{"
                                    + t(vi, "Education", "Học vấn")
                                    + "}\n"
                                    + writing
                                    + "\n\\section*{"
                                    + t(vi, "Experience", "Kinh nghiệm")
                                    + "}\n\\section*{"
                                    + t(vi, "Skills", "Kỹ năng")
                                    + "}\n";
            default -> throw new IllegalArgumentException("Unknown template: " + id);
        }
        return Map.of("main.tex", preamble + "\\begin{document}\n" + body + "\\end{document}\n");
    }

    public static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (char c : (value == null ? "" : value).toCharArray())
            out.append(
                    switch (c) {
                        case '\\' -> "\\textbackslash{}";
                        case '{' -> "\\{";
                        case '}' -> "\\}";
                        case '&', '%', '$', '#', '_' -> "\\" + c;
                        case '~' -> "\\textasciitilde{}";
                        case '^' -> "\\textasciicircum{}";
                        case '\n', '\r' -> " ";
                        default -> String.valueOf(c);
                    });
        return out.toString();
    }
}

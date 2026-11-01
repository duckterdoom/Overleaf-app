package com.overleaf.client;

import com.overleaf.client.compiler.LatexCompilerService;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the raw message of a LaTeX error into a short, plain-language explanation
 * with a suggested fix, in English or Vietnamese.
 *
 * <p>Pure Java, no JavaFX and no network: it works offline and is easy to unit test.
 * Rules are checked in order and the first one that matches wins. To support a new
 * error, add one more {@code rule(...)} call to {@link #RULES}.
 *
 * <p>Placeholders inside the texts: {@code {1}}, {@code {2}}, {@code {3}} are replaced by
 * the capture groups of the regex, and {@code {cmd}} by the first backslash command found
 * in the message.
 */
public final class LatexErrorExplainer {

    /** What the UI shows for one error. {@code known} is false when no rule matched. */
    public record Explanation(String title, String why, String fix, boolean known) { }

    private record Text(String title, String why, String fix) { }

    private record Rule(Pattern pattern, Text en, Text vi) { }

    private static final Pattern COMMAND = Pattern.compile("\\\\[A-Za-z@]+");
    private static final int MAX_QUOTE = 140;

    private static final List<Rule> RULES = List.of(

            rule("command failed with exit code (\\d+)",
                    "latexmk could not finish the build",
                    "Exit code {1} is a launcher summary, not the original LaTeX error.",
                    "Find the first LaTeX or package error above this message. Check for missing packages, fonts or input files before changing document syntax.",
                    "latexmk chưa hoàn tất biên dịch",
                    "Mã {1} là thông báo tổng kết, chưa phải nguyên nhân lỗi LaTeX.",
                    "Tìm lỗi LaTeX hoặc gói đầu tiên ở phía trên. Kiểm tra gói, font và tệp đầu vào bị thiếu trước khi sửa cú pháp tài liệu."),

            rule("(?:can't|cannot) write on file [`']([^']+)'",
                    "Cannot write an auxiliary file",
                    "TeX could not write {1}. The output subfolder may be missing or unwritable.",
                    "Create the corresponding subfolder under build, check permissions and compile main.tex again.",
                    "Không ghi được tệp phụ trợ",
                    "TeX không ghi được {1}. Thư mục con đầu ra có thể chưa có hoặc không có quyền ghi.",
                    "Tạo thư mục con tương ứng trong build, kiểm tra quyền ghi rồi biên dịch lại main.tex."),

            rule("Undefined control sequence",
                    "Unknown command",
                    "LaTeX does not recognise the command {cmd}. This is usually a typo, or the package that defines it has not been loaded.",
                    "Check the spelling, then add the matching \\usepackage{...} line above \\begin{document} if the command comes from a package.",
                    "Lệnh không tồn tại",
                    "LaTeX không nhận ra lệnh {cmd}. Thường là do gõ sai tên lệnh, hoặc chưa nạp gói định nghĩa lệnh đó.",
                    "Kiểm tra lại chính tả. Nếu lệnh thuộc một gói, hãy thêm dòng \\usepackage{...} tương ứng phía trên \\begin{document}."),

            rule("Missing \\$ inserted",
                    "Math symbol used in normal text",
                    "A symbol or command that only works in math mode (such as _ or ^) appeared outside a formula.",
                    "Put the formula between $ signs, or write \\_ to print a plain underscore.",
                    "Ký hiệu toán học nằm ngoài công thức",
                    "Một ký hiệu chỉ dùng được trong chế độ toán (như _ hoặc ^) xuất hiện ở đoạn văn thường.",
                    "Đặt công thức giữa hai dấu $, hoặc gõ \\_ nếu chỉ muốn in dấu gạch dưới."),

            rule("(?:File [`'\u2018]([^'\u2019]+)['\u2019] (?:not found|does not exist)|(?:can't|cannot) find file [`'\u2018]([^'\u2019]+)['\u2019])",
                    "File not found",
                    "LaTeX could not find the file {file}.",
                    "If it is a package (.sty or .cls), install it with your TeX distribution's package manager. If it is your own file, check the name, the folder and the extension in the \\input or \\includegraphics line.",
                    "Không tìm thấy tệp",
                    "LaTeX không tìm thấy tệp {file}.",
                    "Nếu đó là một gói (.sty hoặc .cls), hãy cài nó bằng trình quản lý gói của bản TeX đang dùng. Nếu là tệp của bạn, hãy kiểm tra tên, thư mục và phần mở rộng trong dòng \\input hoặc \\includegraphics."),

            rule("Missing \\\\begin\\{document\\}",
                    "\\begin{document} is missing",
                    "LaTeX found printable text before the document started, or the preamble contains a mistake.",
                    "Make sure \\begin{document} exists, and look above it for stray text or an unclosed brace.",
                    "Thiếu \\begin{document}",
                    "LaTeX gặp nội dung cần in trước khi tài liệu bắt đầu, hoặc phần khai báo đầu tệp có lỗi.",
                    "Kiểm tra \\begin{document} đã có chưa, và tìm phía trên nó xem có chữ thừa hoặc dấu ngoặc nhọn chưa đóng không."),

            rule("\\\\begin\\{([^}]+)\\} on input line (\\d+) ended by \\\\end\\{([^}]+)\\}",
                    "Environment closed with the wrong name",
                    "\\begin{{1}} opened on line {2} was closed by \\end{{3}}.",
                    "Make the two names match, or add the missing \\end{{1}} before it.",
                    "Môi trường đóng sai tên",
                    "\\begin{{1}} mở ở dòng {2} nhưng lại bị đóng bởi \\end{{3}}.",
                    "Cho hai tên khớp nhau, hoặc thêm \\end{{1}} còn thiếu ở phía trước."),

            rule("Environment (\\S+) undefined",
                    "Unknown environment",
                    "LaTeX does not know the environment {1}.",
                    "Check the spelling, or load the package that provides it.",
                    "Môi trường không tồn tại",
                    "LaTeX không biết môi trường {1}.",
                    "Kiểm tra chính tả, hoặc nạp gói cung cấp môi trường này."),

            rule("Runaway argument|Paragraph ended before",
                    "A brace is probably not closed",
                    "A command received an argument that never ended, so LaTeX kept reading until the paragraph finished.",
                    "Look at the line shown and the lines just above it for a { without a matching }.",
                    "Có thể thiếu dấu đóng ngoặc nhọn",
                    "Một lệnh nhận đối số nhưng không có chỗ kết thúc, nên LaTeX đọc tiếp đến hết đoạn.",
                    "Xem dòng được báo và vài dòng ngay phía trên để tìm dấu { chưa có dấu } tương ứng."),

            rule("Extra \\}, or forgotten \\$",
                    "Extra closing brace or missing $",
                    "LaTeX found a } that does not close anything, or a formula that was opened with $ but never closed.",
                    "Count the braces on this line and check that every $ has a partner.",
                    "Thừa dấu } hoặc thiếu dấu $",
                    "LaTeX gặp một dấu } không đóng cho dấu nào, hoặc một công thức mở bằng $ nhưng chưa đóng.",
                    "Đếm lại các dấu ngoặc nhọn trên dòng này và kiểm tra mỗi dấu $ đều có dấu đi cặp."),

            rule("Missing ([{}]) inserted",
                    "Unbalanced braces",
                    "LaTeX expected a {1} and added one by itself.",
                    "Count the { and } on this line and in the command just before it.",
                    "Dấu ngoặc nhọn không cân",
                    "LaTeX cần một dấu {1} nên đã tự thêm vào.",
                    "Đếm lại các dấu { và } trên dòng này và ở lệnh ngay phía trước."),

            rule("Too many \\}'s",
                    "Too many closing braces",
                    "There are more } than { so far.",
                    "Remove the extra } or add the missing {.",
                    "Thừa dấu ngoặc nhọn đóng",
                    "Hiện có nhiều dấu } hơn dấu {.",
                    "Xóa dấu } thừa hoặc thêm dấu { còn thiếu."),

            rule("Argument of (\\\\[A-Za-z@]+) has an extra \\}",
                    "Extra closing brace in an argument",
                    "The argument of {1} contains a } that has no opening partner.",
                    "Remove the extra } or add the missing { just before it.",
                    "Thừa dấu } trong đối số",
                    "Đối số của {1} chứa một dấu } không có dấu { đi cặp.",
                    "Xóa dấu } thừa hoặc thêm dấu { còn thiếu ngay trước nó."),

            rule("File ended while scanning use of (\\\\[A-Za-z@]+)",
                    "File ended too early",
                    "LaTeX reached the end of the file while still reading the argument of {1}.",
                    "A closing brace is missing somewhere before the end. Check the last commands you wrote.",
                    "Tệp kết thúc quá sớm",
                    "LaTeX đến cuối tệp khi vẫn còn đang đọc đối số của {1}.",
                    "Có dấu ngoặc nhọn đóng bị thiếu ở đâu đó trước phần cuối. Kiểm tra lại những lệnh viết sau cùng."),

            rule("Misplaced alignment tab character",
                    "Stray & character",
                    "The & sign is only allowed inside tables and aligned formulas.",
                    "Write \\& to print a real ampersand, or move the text into a tabular.",
                    "Dấu & đặt sai chỗ",
                    "Dấu & chỉ được dùng trong bảng và công thức căn hàng.",
                    "Gõ \\& nếu muốn in dấu và, hoặc đưa đoạn văn vào trong môi trường tabular."),

            rule("Extra alignment tab",
                    "Too many columns in a table row",
                    "A row has more & separators than the table has columns.",
                    "Add a column to the tabular header, or remove the extra &.",
                    "Dòng bảng có quá nhiều cột",
                    "Một dòng có nhiều dấu & hơn số cột đã khai báo của bảng.",
                    "Thêm cột vào phần khai báo tabular, hoặc xóa dấu & thừa."),

            rule("Illegal parameter number",
                    "Misused # sign",
                    "A # appears where LaTeX does not expect it, often inside a \\newcommand.",
                    "Write \\# for a real hash sign, and check that #1, #2 and so on match the number of arguments you declared.",
                    "Dấu # dùng sai",
                    "Dấu # xuất hiện ở chỗ LaTeX không mong đợi, thường nằm trong một lệnh \\newcommand.",
                    "Gõ \\# nếu muốn in dấu thăng, và kiểm tra #1, #2... khớp với số đối số đã khai báo."),

            rule("Double (?:subscript|superscript)",
                    "Double subscript or superscript",
                    "The same symbol received two _ or two ^ in a row.",
                    "Group them with braces, for example x_{a_b} or {x^2}^3.",
                    "Hai chỉ số liên tiếp",
                    "Cùng một ký hiệu nhận hai dấu _ hoặc hai dấu ^ liền nhau.",
                    "Gom chúng bằng dấu ngoặc nhọn, ví dụ x_{a_b} hoặc {x^2}^3."),

            rule("Unicode character (\\S+)",
                    "Character not supported by this engine",
                    "The current setup could not print the character {1}.",
                    "Switch the compiler to XeLaTeX or LuaLaTeX in the toolbar, or load the right encoding package (for Vietnamese with pdfLaTeX, \\usepackage[utf8]{vietnam}).",
                    "Ký tự không được hỗ trợ",
                    "Cấu hình hiện tại không in được ký tự {1}.",
                    "Đổi trình biên dịch sang XeLaTeX hoặc LuaLaTeX trên thanh công cụ, hoặc nạp gói mã hóa phù hợp (với tiếng Việt dùng pdfLaTeX: \\usepackage[utf8]{vietnam})."),

            rule("font \"([^\"]+)\" cannot be found",
                    "Font not found",
                    "The font {1} is not installed on this computer.",
                    "Install the font, or choose another one, for example in \\setmainfont.",
                    "Không tìm thấy phông chữ",
                    "Phông {1} chưa được cài trên máy này.",
                    "Cài phông chữ đó, hoặc chọn phông khác, ví dụ trong \\setmainfont."),

            rule("Unknown graphics extension: (\\.?[A-Za-z0-9]+)",
                    "Image type not supported",
                    "The image format {1} cannot be used with this compiler.",
                    "Use PNG, JPG or PDF, or convert the image first.",
                    "Định dạng ảnh không được hỗ trợ",
                    "Định dạng ảnh {1} không dùng được với trình biên dịch này.",
                    "Dùng PNG, JPG hoặc PDF, hoặc chuyển đổi ảnh trước."),

            rule("Undefined color [`'\u2018]([^'\u2019]+)",
                    "Unknown color",
                    "The color {1} has not been defined.",
                    "Check the spelling, load the xcolor package, or define it with \\definecolor.",
                    "Màu chưa được định nghĩa",
                    "Màu {1} chưa được định nghĩa.",
                    "Kiểm tra chính tả, nạp gói xcolor, hoặc định nghĩa bằng \\definecolor."),

            rule("Command (\\\\[A-Za-z@]+) already defined",
                    "Command name already in use",
                    "{1} already exists, so \\newcommand cannot create it again.",
                    "Pick another name, or use \\renewcommand if you want to replace it on purpose.",
                    "Tên lệnh đã được dùng",
                    "{1} đã tồn tại nên \\newcommand không thể tạo lại.",
                    "Chọn tên khác, hoặc dùng \\renewcommand nếu bạn cố ý muốn thay thế."),

            rule("Option clash for package ([A-Za-z0-9_-]+)",
                    "Package loaded twice with different options",
                    "The package {1} was loaded again with options that differ from the first time.",
                    "Load it once, and put every option in that single \\usepackage line.",
                    "Gói được nạp hai lần với tùy chọn khác nhau",
                    "Gói {1} được nạp lại với các tùy chọn khác lần đầu.",
                    "Chỉ nạp một lần, và đưa mọi tùy chọn vào đúng một dòng \\usepackage."),

            rule("Lonely \\\\item",
                    "\\item outside a list",
                    "An \\item was used without a list around it.",
                    "Put it between \\begin{itemize} and \\end{itemize}, or use enumerate for a numbered list.",
                    "\\item nằm ngoài danh sách",
                    "Lệnh \\item được dùng mà không nằm trong danh sách nào.",
                    "Đặt nó giữa \\begin{itemize} và \\end{itemize}, hoặc dùng enumerate cho danh sách đánh số."),

            rule("Missing number, treated as zero",
                    "A number is missing",
                    "A length or counter command did not receive the value it needs.",
                    "Check the number and unit in the command on this line, for example 5pt or 0.5\\textwidth.",
                    "Thiếu một con số",
                    "Một lệnh về độ dài hoặc bộ đếm không nhận được giá trị cần thiết.",
                    "Kiểm tra số và đơn vị trong lệnh ở dòng này, ví dụ 5pt hoặc 0.5\\textwidth."),

            rule("Can be used only in preamble",
                    "Command in the wrong place",
                    "This command must appear before \\begin{document}.",
                    "Move it up into the preamble.",
                    "Lệnh đặt sai vị trí",
                    "Lệnh này phải nằm trước \\begin{document}.",
                    "Chuyển nó lên phần khai báo ở đầu tệp."),

            rule("Emergency stop",
                    "LaTeX stopped early",
                    "After a serious error LaTeX gave up reading the file.",
                    "Fix the first error in the list. Later errors are often caused by it.",
                    "LaTeX dừng sớm",
                    "Sau một lỗi nghiêm trọng, LaTeX bỏ dở việc đọc tệp.",
                    "Sửa lỗi đầu tiên trong danh sách. Các lỗi sau thường là hệ quả của nó.")
    );

    private LatexErrorExplainer() { }

    /**
     * Explains one LaTeX error message.
     *
     * @param message    the message as reported by the compiler (may be null or blank)
     * @param vietnamese true for Vietnamese text, false for English
     * @return never null; {@code known()} tells whether a built-in rule matched
     */
    public static Explanation explain(String message, boolean vietnamese) {
        return explain(message, "", vietnamese);
    }

    /**
     * Same as {@link #explain(String, boolean)}, but also uses the source line that LaTeX prints
     * under an error (the {@code l.12 ...} line). That line lets the explanation name the exact
     * command that was not recognised.
     *
     * @param context the text after {@code l.<n>} in the log, or null/blank when unknown
     */
    public static Explanation explain(String message, String context, boolean vietnamese) {
        String raw = message == null ? "" : message.strip();
        String ctx = context == null ? "" : context;
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(raw);
            if (m.find()) {
                Text t = vietnamese ? rule.vi() : rule.en();
                return new Explanation(
                        fill(t.title(), m, raw, ctx, vietnamese),
                        fill(t.why(), m, raw, ctx, vietnamese),
                        fill(t.fix(), m, raw, ctx, vietnamese),
                        true);
            }
        }
        return fallback(raw, vietnamese);
    }

    /**
     * Explains a build that failed without any parsable error line, using only the exit code
     * of the compiler service (see the {@code EXIT_*} constants of {@link LatexCompilerService}).
     */
    public static Explanation explainExit(int exitCode, boolean vi) {
        if (exitCode == LatexCompilerService.EXIT_MISSING_GLYPHS) {
            return vi
                    ? new Explanation("PDF bị thiếu ký tự",
                            "TeX đã tạo PDF nhưng font đang dùng không có một số ký tự. Với tiếng Việt, nguyên nhân thường là dùng khai báo T5 của pdfLaTeX khi chạy XeLaTeX/LuaLaTeX.",
                            "Dùng iftex: pdfLaTeX dùng inputenc + fontenc T5; XeLaTeX/LuaLaTeX dùng fontspec và font Unicode có tiếng Việt. Xem các dòng Missing character trong Build output.", true)
                    : new Explanation("PDF contains missing characters",
                            "TeX generated a PDF, but the selected font cannot display some characters.",
                            "Use iftex: inputenc and T5 fontenc for pdfLaTeX; fontspec and a Unicode font with the required glyphs for XeLaTeX/LuaLaTeX. Check Missing character in Build output.", true);
        }
        if (exitCode == LatexCompilerService.EXIT_START_FAILED) {
            return vi
                    ? new Explanation("Không chạy được LaTeX",
                            "Ứng dụng không khởi động được latexmk. Có thể LaTeX chưa được cài, hoặc chưa nằm trong PATH.",
                            "Cài TeX Live hoặc MiKTeX, khởi động lại ứng dụng, và kiểm tra lệnh latexmk -v chạy được trong terminal.", true)
                    : new Explanation("LaTeX is not available",
                            "The app could not start latexmk. LaTeX is probably not installed, or it is not on the PATH.",
                            "Install TeX Live or MiKTeX, restart the app, and check that latexmk -v works in a terminal.", true);
        }
        if (exitCode == LatexCompilerService.EXIT_TIMEOUT) {
            return vi
                    ? new Explanation("Biên dịch quá thời gian",
                            "Lần biên dịch chạy lâu hơn giới hạn cho phép nên đã bị dừng.",
                            "Đơn giản hóa các hình nặng (ví dụ TikZ lớn) hoặc biên dịch chúng riêng thành PDF, rồi biên dịch lại.", true)
                    : new Explanation("Build timed out",
                            "The build ran longer than the time limit and was stopped.",
                            "Simplify heavy figures (for example large TikZ drawings) or compile them once as separate PDFs, then recompile.", true);
        }
        if (exitCode == LatexCompilerService.EXIT_INTERRUPTED) {
            return vi
                    ? new Explanation("Biên dịch bị ngắt",
                            "Lần biên dịch bị dừng trước khi kết thúc.",
                            "Bấm Recompile để chạy lại.", true)
                    : new Explanation("Build was interrupted",
                            "The build was stopped before it finished.",
                            "Press Recompile to run it again.", true);
        }
        return vi
                ? new Explanation("Biên dịch thất bại",
                        "latexmk kết thúc với mã " + exitCode + " nhưng trong output không có dòng nào có tên tệp và số dòng.",
                        "Đọc vài dòng cuối của Build output. Dòng đầu tiên bắt đầu bằng ! thường là nguyên nhân thật.", true)
                : new Explanation("Build failed",
                        "latexmk exited with code " + exitCode + ", but no line with a file name and a line number was found in its output.",
                        "Read the last lines of Build output. The first line that starts with ! is usually the real cause.", true);
    }

    private static Explanation fallback(String raw, boolean vi) {
        String quote = raw.length() > MAX_QUOTE ? raw.substring(0, MAX_QUOTE) + "…" : raw;
        if (vi) {
            return new Explanation(
                    "LaTeX báo có lỗi",
                    quote.isEmpty()
                            ? "Chưa có giải thích dựng sẵn cho lỗi này."
                            : "Chưa có giải thích dựng sẵn cho thông báo này: \"" + quote + "\"",
                    "Xem dòng được báo và dòng ngay phía trên để tìm lỗi gõ hoặc dấu ngoặc nhọn bị thiếu, rồi đọc toàn bộ thông báo trong phần Build output.",
                    false);
        }
        return new Explanation(
                "LaTeX reported a problem",
                quote.isEmpty()
                        ? "There is no built-in explanation for this error yet."
                        : "There is no built-in explanation for this message yet: \"" + quote + "\"",
                "Look at the line shown and the line just above it for a typo or a missing brace, then read the full text in Build output.",
                false);
    }

    private static String fill(String template, Matcher m, String raw, String ctx, boolean vi) {
        String out = template;
        if (out.contains("{file}")) {
            out = out.replace("{file}", m.group(1) != null ? m.group(1) : m.group(2));
        }
        if (out.contains("{cmd}")) {
            String cmd = findCommand(raw, ctx);
            String fallback = vi ? "được dùng ở dòng này" : "it found on this line";
            out = out.replace("{cmd}", cmd != null ? cmd : fallback);
        }
        for (int i = 1; i <= m.groupCount(); i++) {
            String value = m.group(i) == null ? "?" : m.group(i);
            out = out.replace("{" + i + "}", value);
        }
        return out;
    }

    /**
     * The command to name in an explanation: the first one in the message itself, otherwise the last
     * one on the first context line (LaTeX stops reading right after the command it could not handle).
     */
    private static String findCommand(String raw, String ctx) {
        Matcher inMessage = COMMAND.matcher(raw);
        if (inMessage.find()) return inMessage.group();
        String firstLine = ctx.strip().split("\\R", 2)[0];
        Matcher inContext = COMMAND.matcher(firstLine);
        String last = null;
        while (inContext.find()) last = inContext.group();
        return last;
    }

    private static Rule rule(String regex,
                             String titleEn, String whyEn, String fixEn,
                             String titleVi, String whyVi, String fixVi) {
        return new Rule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                new Text(titleEn, whyEn, fixEn),
                new Text(titleVi, whyVi, fixVi));
    }
}

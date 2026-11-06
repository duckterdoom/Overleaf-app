export function tex(text: string) {
    const map: Record<string, string> = {
        "\\": "\\textbackslash{}",
        "{": "\\{",
        "}": "\\}",
        "%": "\\%",
        "#": "\\#",
        "&": "\\&",
        _: "\\_",
        $: "\\$",
        "^": "\\textasciicircum{}",
        "~": "\\textasciitilde{}",
    };
    return [...text].map((char) => map[char] || char).join("");
}
export function key(value: string) {
    value = value.trim();
    if (!/^[\p{L}\p{N}:._/+\-]+$/u.test(value))
        throw new Error("Khóa LaTeX không hợp lệ / Invalid LaTeX key");
    return value;
}
export function reference(command: string, target: string) {
    if (!["ref", "eqref", "pageref", "autoref"].includes(command))
        throw new Error("Invalid reference command");
    return `\\${command}{${key(target)}}`;
}
export function citation(value: string) {
    return `\\cite{${[...new Set(value.split(",").map(key))].join(",")}}`;
}
export function hyperlink(address: string, text: string) {
    const url = new URL(address.trim());
    if (
        !["http:", "https:", "mailto:"].includes(url.protocol) ||
        url.username ||
        url.password ||
        /[{}\\\r\n]/.test(address)
    )
        throw new Error("URL không hợp lệ / Invalid URL");
    return `\\href{${address.trim().replace(/[%#&_]/g, (char) => "\\" + char)}}{${tex(text || address.trim())}}`;
}
export function figure(
    path: string,
    width: number,
    caption: string,
    label: string,
) {
    if (
        !path ||
        /[{}%#\r\n:]/.test(path) ||
        path.includes("..") ||
        path.startsWith("/") ||
        path.includes("\\") ||
        !Number.isFinite(width) ||
        width < 0.05 ||
        width > 1 ||
        (label && !caption)
    )
        throw new Error(
            "Kiểm tra ảnh/độ rộng/chú thích / Check image, width and caption",
        );
    return `\\begin{figure}[htbp]\n  \\centering\n  \\includegraphics[width=${width}\\linewidth]{\\detokenize{${path}}}\n${caption ? `  \\caption{${tex(caption)}}\n` : ""}${label ? `  \\label{${key(label)}}\n` : ""}\\end{figure}`;
}
export function table(
    rows: number,
    columns: number,
    alignment: string,
    caption: string,
    label: string,
    vi = true,
) {
    if (
        !Number.isInteger(rows) ||
        rows < 1 ||
        rows > 50 ||
        !Number.isInteger(columns) ||
        columns < 1 ||
        columns > 20 ||
        !["l", "c", "r"].includes(alignment) ||
        (label && !caption)
    )
        throw new Error(
            "Kích thước bảng/chú thích không hợp lệ / Invalid table dimensions or caption",
        );
    const row = (values: string[]) => "    " + values.join(" & ") + " \\\\\n";
    return `\\begin{table}[htbp]\n  \\centering\n${caption ? `  \\caption{${tex(caption)}}\n` : ""}${label ? `  \\label{${key(label)}}\n` : ""}  \\begin{tabular}{|${(alignment + "|").repeat(columns)}}\n    \\hline\n${row(Array.from({ length: columns }, (_, i) => (vi ? "Cột " : "Column ") + (i + 1)))}    \\hline\n${Array.from({ length: rows }, () => row(Array(columns).fill("..."))).join("")}    \\hline\n  \\end{tabular}\n\\end{table}`;
}
export function list(kind: string, selected: string) {
    if (!["itemize", "enumerate", "description"].includes(kind))
        throw new Error("Invalid list");
    const lines = selected.split("\n").filter((line) => line.trim());
    return `\\begin{${kind}}\n${(lines.length ? lines : ["..."]).map((line) => `  \\item${kind === "description" ? "[Label]" : ""} ${line}`).join("\n")}\n\\end{${kind}}`;
}
export function lineEdit(
    text: string,
    anchor: number,
    caret: number,
    mode: "increase" | "decrease" | "comment",
) {
    const from = Math.min(anchor, caret);
    let to = Math.max(anchor, caret);
    if (to > from && text[to - 1] === "\n") to--;
    const start = text.lastIndexOf("\n", from - 1) + 1,
        found = text.indexOf("\n", to),
        end = found < 0 ? text.length : found;
    const lines = text.slice(start, end).split("\n"),
        uncomment =
            mode === "comment" && lines.every((line) => /^\s*%/.test(line));
    const replacement = lines
        .map((line) =>
            mode === "increase"
                ? "  " + line
                : mode === "decrease"
                  ? line.replace(/^(\t| {1,2})/, "")
                  : uncomment
                    ? line.replace(/^(\s*)% ?/, "$1")
                    : "% " + line,
        )
        .join("\n");
    return {
        start,
        end,
        replacement,
        anchor: start,
        caret: start + replacement.length,
    };
}

package com.overleaf.client.ui;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;

/** LaTeX snippets and line edits, independent of JavaFX and the filesystem. */
final class EditorInsertions {
    private EditorInsertions() {}

    static String key(String value) {
        String key = value == null ? "" : value.trim();
        if (!key.matches("[\\p{L}\\p{N}:._/+\\-]+")) throw new IllegalArgumentException("key");
        return key;
    }

    static String reference(String command, String value) {
        if (!List.of("ref", "eqref", "pageref", "autoref").contains(command))
            throw new IllegalArgumentException("command");
        return "\\" + command + "{" + key(value) + "}";
    }

    static String citation(String value) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String part : value.split(",", -1)) keys.add(key(part));
        return "\\cite{" + String.join(",", keys) + "}";
    }

    static String link(String address, String text) {
        URI uri = URI.create(address.trim());
        if (uri.getScheme() == null
                || !List.of("http", "https", "mailto").contains(uri.getScheme())
                || (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                        && uri.getHost() == null)
                || uri.getRawUserInfo() != null
                || address.matches("(?s).*[{}\\\\\\r\\n].*"))
            throw new IllegalArgumentException("url");
        String url =
                address.trim()
                        .replace("%", "\\%")
                        .replace("#", "\\#")
                        .replace("&", "\\&")
                        .replace("_", "\\_");
        return "\\href{" + url + "}{" + text(text.isBlank() ? address.trim() : text) + "}";
    }

    static String figure(String path, String width, String caption, String label) {
        String file = path.trim().replace('\\', '/');
        if (file.isBlank()
                || file.matches("(?s).*[{}%#\\r\\n].*")
                || file.contains("..")
                || file.startsWith("/")
                || file.contains(":")) throw new IllegalArgumentException("path");
        double fraction = Double.parseDouble(width.trim());
        if (!Double.isFinite(fraction) || fraction < .05 || fraction > 1)
            throw new IllegalArgumentException("width");
        if (!label.isBlank() && caption.isBlank()) throw new IllegalArgumentException("caption");
        return "\\begin{figure}[htbp]\n  \\centering\n  \\includegraphics[width="
                + java.math.BigDecimal.valueOf(fraction).stripTrailingZeros().toPlainString()
                + "\\linewidth]{\\detokenize{"
                + file
                + "}}\n"
                + (caption.isBlank() ? "" : "  \\caption{" + text(caption) + "}\n")
                + (label.isBlank() ? "" : "  \\label{" + key(label) + "}\n")
                + "\\end{figure}";
    }

    static String table(
            int rows, int columns, String alignment, String caption, String label, boolean vi) {
        if (rows < 1
                || rows > 50
                || columns < 1
                || columns > 20
                || !List.of("l", "c", "r").contains(alignment))
            throw new IllegalArgumentException("size");
        if (!label.isBlank() && caption.isBlank()) throw new IllegalArgumentException("caption");
        StringBuilder result = new StringBuilder("\\begin{table}[htbp]\n  \\centering\n");
        if (!caption.isBlank()) result.append("  \\caption{").append(text(caption)).append("}\n");
        if (!label.isBlank()) result.append("  \\label{").append(key(label)).append("}\n");
        result.append("  \\begin{tabular}{|")
                .append((alignment + "|").repeat(columns))
                .append("}\n    \\hline\n    ");
        for (int c = 1; c <= columns; c++) {
            if (c > 1) result.append(" & ");
            result.append(vi ? "Cột " : "Column ").append(c);
        }
        result.append(" \\\\\n    \\hline\n");
        for (int row = 0; row < rows; row++) {
            result.append("    ");
            for (int c = 0; c < columns; c++) {
                if (c > 0) result.append(" & ");
                result.append("...");
            }
            result.append(" \\\\\n");
        }
        return result.append("    \\hline\n  \\end{tabular}\n\\end{table}").toString();
    }

    static String text(String value) {
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray())
            result.append(
                    switch (c) {
                        case '\\' -> "\\textbackslash{}";
                        case '{', '}', '%', '#', '&', '_', '$' -> "\\" + c;
                        case '^' -> "\\textasciicircum{}";
                        case '~' -> "\\textasciitilde{}";
                        default -> String.valueOf(c);
                    });
        return result.toString();
    }

    record Edit(int start, int end, String replacement, int anchor, int caret) {}

    static Edit indent(String text, int anchor, int caret, boolean increase) {
        int from = Math.min(anchor, caret), to = Math.max(anchor, caret);
        if (to > from && text.charAt(to - 1) == '\n') to--;
        int start = text.lastIndexOf('\n', from - 1) + 1;
        int end = text.indexOf('\n', to);
        if (end < 0) end = text.length();
        String[] lines = text.substring(start, end).split("\n", -1);
        StringBuilder replacement = new StringBuilder();
        int oldPosition = start, newAnchor = anchor, newCaret = caret;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int removed =
                    line.startsWith("\t")
                            ? 1
                            : line.startsWith("  ") ? 2 : line.startsWith(" ") ? 1 : 0;
            int newPosition = start + replacement.length();
            if (anchor >= oldPosition && anchor <= oldPosition + line.length())
                newAnchor =
                        newPosition
                                + (increase
                                        ? anchor - oldPosition + 2
                                        : Math.max(0, anchor - oldPosition - removed));
            if (caret >= oldPosition && caret <= oldPosition + line.length())
                newCaret =
                        newPosition
                                + (increase
                                        ? caret - oldPosition + 2
                                        : Math.max(0, caret - oldPosition - removed));
            replacement.append(increase ? "  " + line : line.substring(removed));
            oldPosition += line.length() + 1;
            if (i + 1 < lines.length) replacement.append('\n');
        }
        int delta = replacement.length() - (end - start);
        if (anchor > end) newAnchor = anchor + delta;
        if (caret > end) newCaret = caret + delta;
        return new Edit(start, end, replacement.toString(), newAnchor, newCaret);
    }
}

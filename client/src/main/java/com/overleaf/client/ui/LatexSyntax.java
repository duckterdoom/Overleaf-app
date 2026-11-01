package com.overleaf.client.ui;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits LaTeX source into contiguous colored runs for the editor's highlight layer. */
public final class LatexSyntax {

    public enum Kind {
        // Light-mode code panel (Dracula-like)     Dark mode (GitHub dark)
        PLAIN("#F8F8F2",                            "#E6EDF3"),
        COMMENT("#6F7DB0",                          "#8B949E"),
        STRUCTURE("#FF79C6",                        "#FF7B72"),
        COMMAND("#8BE9FD",                          "#D2A8FF"),
        ENVIRONMENT("#50FA7B",                      "#7EE787"),
        MATH("#F1FA8C",                             "#A5D6FF"),
        OPTION("#FFB86C",                           "#FFA657");

        private final Color light;
        private final Color dark;

        Kind(String light, String dark) {
            this.light = Color.web(light);
            this.dark = Color.web(dark);
        }

        public Color color(boolean darkMode) {
            return darkMode ? dark : light;
        }
    }

    /** {@code [start, end)} of the source, painted as {@code kind}. */
    public record Run(int start, int end, Kind kind) {
    }

    private static final Pattern TOKEN = Pattern.compile(
            "(?<comment>(?<!\\\\)%[^\\n]*)"
                    + "|(?<structure>\\\\(?:begin|end|documentclass|usepackage|(?:sub)*section\\*?|chapter\\*?|paragraph|"
                    + "title|author|date|maketitle|item|label|ref|cite|input|include|newcommand|renewcommand)(?![A-Za-z@]))"
                    + "|(?<envname>(?<=\\\\begin\\{|\\\\end\\{)[^}\\n]*(?=\\}))"
                    + "|(?<command>\\\\[A-Za-z@]+\\*?|\\\\[^A-Za-z\\s])"
                    + "|(?<math>\\$\\$[^$]*\\$\\$|\\$[^$\\n]*\\$)"
                    + "|(?<option>\\[[^\\]\\n]*\\])");

    private LatexSyntax() {
    }

    /** Runs cover the whole text with no gaps; plain stretches are merged so the layer stays small. */
    public static List<Run> tokenize(String text) {
        List<Run> runs = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        int at = 0;
        while (m.find()) {
            Kind kind = m.group("comment") != null ? Kind.COMMENT
                    : m.group("structure") != null ? Kind.STRUCTURE
                    : m.group("envname") != null ? Kind.ENVIRONMENT
                    : m.group("command") != null ? Kind.COMMAND
                    : m.group("math") != null ? Kind.MATH
                    : Kind.OPTION;
            if (m.end() == m.start()) continue;
            if (m.start() > at) runs.add(new Run(at, m.start(), Kind.PLAIN));
            runs.add(new Run(m.start(), m.end(), kind));
            at = m.end();
        }
        if (at < text.length()) runs.add(new Run(at, text.length(), Kind.PLAIN));
        return runs;
    }
}

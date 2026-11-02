package com.overleaf.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EditorInsertionsTest {
    @Test
    void citationNormalizesMultipleKeysWithoutAllowingLatexInjection() {
        assertEquals(
                "\\cite{knuth1984,lamport1994}",
                EditorInsertions.citation(" knuth1984, lamport1994,knuth1984 "));
        assertThrows(IllegalArgumentException.class, () -> EditorInsertions.citation("key,,other"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EditorInsertions.reference("ref", "a}\\input{evil"));
        assertEquals("\\eqref{eq:energy}", EditorInsertions.reference("eqref", "eq:energy"));
    }

    @Test
    void linkEscapesLatexCharactersAndRejectsInvalidAddresses() {
        assertEquals(
                "\\href{https://example.com/a\\_b?x=1\\&y=2\\#part}{A \\& B}",
                EditorInsertions.link("https://example.com/a_b?x=1&y=2#part", "A & B"));
        for (String url :
                java.util.List.of(
                        "example.com",
                        "javascript:alert(1)",
                        "https://user:pass@example.com",
                        "https://"))
            assertThrows(IllegalArgumentException.class, () -> EditorInsertions.link(url, "label"));
    }

    @Test
    void figureKeepsSpacesInFileNamesAndCanonicalizesWidth() {
        String snippet =
                EditorInsertions.figure("figures/my plot.png", "8e-1", "A & B", "fig:plot");
        assertTrue(snippet.contains("width=0.8\\linewidth"));
        assertTrue(snippet.contains("\\detokenize{figures/my plot.png}"));
        assertTrue(snippet.contains("\\caption{A \\& B}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EditorInsertions.figure("../a.png", ".8", "a", "fig:a"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EditorInsertions.figure("a.png", "NaN", "a", "fig:a"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EditorInsertions.figure("a.png", ".8", "", "fig:a"));
    }

    @Test
    void tableHasValidRowsColumnsAndCaptionWithoutExtraPackages() {
        String table = EditorInsertions.table(2, 3, "c", "Cost 50%", "tab:cost", false);
        assertTrue(table.contains("\\begin{tabular}{|c|c|c|}"));
        assertEquals(3, table.lines().filter(line -> line.strip().endsWith("\\\\")).count());
        assertTrue(table.contains("Cost 50\\%"));
        assertThrows(
                IllegalArgumentException.class,
                () -> EditorInsertions.table(51, 3, "c", "a", "tab:a", false));
    }

    @Test
    void indentDoesNotChangeTheNextUnselectedLine() {
        String source = "one\ntwo\nthree";
        var edit = EditorInsertions.indent(source, 0, 8, true);
        assertEquals("  one\n  two\nthree", apply(source, edit));
        assertEquals(12, edit.caret());
    }

    @Test
    void indentPreservesCaretWithoutSelectingTheWholeLine() {
        var edit = EditorInsertions.indent("alpha\nbeta", 2, 2, true);
        assertEquals(4, edit.anchor());
        assertEquals(4, edit.caret());
        assertEquals("  alpha\nbeta", apply("alpha\nbeta", edit));
    }

    @Test
    void decreaseIndentHandlesTabsAndShortWhitespaceWithoutRemovingText() {
        String text = "\tone\n two\n  three\nfour";
        var edit = EditorInsertions.indent(text, 0, text.length(), false);
        assertEquals("one\ntwo\nthree\nfour", apply(text, edit));
    }

    @Test
    void reverseSelectionAndEmptyFinalLineRemainValid() {
        var reverse = EditorInsertions.indent("one\ntwo", 7, 0, true);
        assertTrue(reverse.anchor() > reverse.caret());
        assertEquals("  one\n  two", apply("one\ntwo", reverse));
        var empty = EditorInsertions.indent("one\n", 4, 4, true);
        assertEquals("one\n  ", apply("one\n", empty));
        assertEquals(6, empty.caret());
    }

    private String apply(String original, EditorInsertions.Edit edit) {
        return original.substring(0, edit.start())
                + edit.replacement()
                + original.substring(edit.end());
    }
}

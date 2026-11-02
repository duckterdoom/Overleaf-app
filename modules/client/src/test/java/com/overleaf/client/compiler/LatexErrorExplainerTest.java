package com.overleaf.client.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LatexErrorExplainerTest {

    @Test
    void wrapperExitCodeDoesNotSuggestAnInventedSyntaxError() {
        var result = LatexErrorExplainer.explain("command failed with exit code 12:", true);
        assertTrue(result.known());
        assertTrue(result.why().contains("tổng kết"));
    }

    @Test
    void auxiliaryWriteErrorExplainsOutputFolders() {
        var result =
                LatexErrorExplainer.explain(
                        "I can't write on file `chapters/01-introduction.aux'.", true);
        assertTrue(result.known());
        assertTrue(result.fix().contains("build"));
        assertTrue(result.why().contains("chapters/01-introduction.aux"));
    }

    @Test
    void undefinedCommandNamesTheCommand() {
        var e = LatexErrorExplainer.explain("! Undefined control sequence.\nl.12 \\foo", false);
        assertTrue(e.known());
        assertTrue(e.why().contains("\\foo"));
    }

    @Test
    void missingFileShowsTheFileName() {
        var e = LatexErrorExplainer.explain("! LaTeX Error: File `tikz.sty' not found.", false);
        assertTrue(e.known());
        assertTrue(e.why().contains("tikz.sty"));
    }

    @Test
    void mismatchedEnvironmentUsesAllThreeGroups() {
        var e =
                LatexErrorExplainer.explain(
                        "! LaTeX Error: \\begin{itemize} on input line 5 ended by \\end{document}.",
                        false);
        assertTrue(e.why().contains("\\begin{itemize}"));
        assertTrue(e.why().contains("line 5"));
        assertTrue(e.why().contains("\\end{document}"));
    }

    @Test
    void vietnameseTextIsReturnedWhenRequested() {
        var en = LatexErrorExplainer.explain("! Missing $ inserted.", false);
        var vi = LatexErrorExplainer.explain("! Missing $ inserted.", true);
        assertTrue(vi.known());
        assertFalse(en.title().equals(vi.title()));
    }

    @Test
    void unknownMessageFallsBackWithoutThrowing() {
        var e = LatexErrorExplainer.explain("! Something nobody has seen before", false);
        assertFalse(e.known());
        assertTrue(e.why().contains("Something nobody has seen before"));
    }

    @Test
    void nullAndBlankMessagesAreSafe() {
        assertFalse(LatexErrorExplainer.explain(null, false).known());
        assertEquals("LaTeX reported a problem", LatexErrorExplainer.explain("  ", false).title());
    }

    @Test
    void contextLineNamesTheOffendingCommand() {
        var e =
                LatexErrorExplainer.explain(
                        "Undefined control sequence.", "This is \\texbf", false);
        assertTrue(e.why().contains("\\texbf"));
    }

    @Test
    void exitCodesHaveTheirOwnExplanation() {
        assertEquals(
                "LaTeX is not available",
                LatexErrorExplainer.explainExit(LatexCompilerService.EXIT_START_FAILED, false)
                        .title());
        assertEquals(
                "Build timed out",
                LatexErrorExplainer.explainExit(LatexCompilerService.EXIT_TIMEOUT, false).title());
        assertTrue(LatexErrorExplainer.explainExit(12, false).why().contains("12"));
    }

    @Test
    void existingFileIsNotExplainedAsMissing() {
        assertFalse(
                LatexErrorExplainer.explain("File `output.pdf' already exists.", false).known());
        assertTrue(
                LatexErrorExplainer.explain("I can't find file `chapter.tex'.", false)
                        .why()
                        .contains("chapter.tex"));
    }

    @Test
    void missingGlyphsExplainTheUnicodeEngineConfiguration() {
        var explanation =
                LatexErrorExplainer.explainExit(LatexCompilerService.EXIT_MISSING_GLYPHS, true);
        assertTrue(explanation.fix().contains("fontspec"));
        assertTrue(explanation.title().contains("thiếu ký tự"));
    }
}

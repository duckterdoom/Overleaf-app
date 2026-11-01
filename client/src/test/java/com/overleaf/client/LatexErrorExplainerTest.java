package com.overleaf.client;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.overleaf.client.compiler.LatexCompilerService;
import org.junit.Assert;
import org.testng.annotations.Test;

class LatexErrorExplainerTest {

    @Test
    void undefinedCommandNamesTheCommand() {
        var e = LatexErrorExplainer.explain("! Undefined control sequence.\nl.12 \\foo", false);
        Assert.assertTrue(e.known());
        Assert.assertTrue(e.why().contains("\\foo"));
    }

    @Test
    void missingFileShowsTheFileName() {
        var e = LatexErrorExplainer.explain("! LaTeX Error: File `tikz.sty' not found.", false);
        Assert.assertTrue(e.known());
        Assert.assertTrue(e.why().contains("tikz.sty"));
    }

    @Test
    void mismatchedEnvironmentUsesAllThreeGroups() {
        var e = LatexErrorExplainer.explain(
                "! LaTeX Error: \\begin{itemize} on input line 5 ended by \\end{document}.", false);
        Assert.assertTrue(e.why().contains("\\begin{itemize}"));
        Assert.assertTrue(e.why().contains("line 5"));
        Assert.assertTrue(e.why().contains("\\end{document}"));
    }

    @Test
    void vietnameseTextIsReturnedWhenRequested() {
        var en = LatexErrorExplainer.explain("! Missing $ inserted.", false);
        var vi = LatexErrorExplainer.explain("! Missing $ inserted.", true);
        Assert.assertTrue(vi.known());
        assertFalse(en.title().equals(vi.title()));
    }

    @Test
    void unknownMessageFallsBackWithoutThrowing() {
        var e = LatexErrorExplainer.explain("! Something nobody has seen before", false);
        assertFalse(e.known());
        Assert.assertTrue(e.why().contains("Something nobody has seen before"));
    }

    @Test
    void nullAndBlankMessagesAreSafe() {
        assertFalse(LatexErrorExplainer.explain(null, false).known());
        assertEquals("LaTeX reported a problem", LatexErrorExplainer.explain("  ", false).title());
    }

    @Test
    void contextLineNamesTheOffendingCommand() {
        var e = LatexErrorExplainer.explain("Undefined control sequence.", "This is \\texbf", false);
        Assert.assertTrue(e.why().contains("\\texbf"));
    }

    @Test
    void exitCodesHaveTheirOwnExplanation() {
        assertEquals("LaTeX is not available",
                LatexErrorExplainer.explainExit(LatexCompilerService.EXIT_START_FAILED, false).title());
        assertEquals("Build timed out",
                LatexErrorExplainer.explainExit(LatexCompilerService.EXIT_TIMEOUT, false).title());
        Assert.assertTrue(LatexErrorExplainer.explainExit(12, false).why().contains("12"));
    }
}

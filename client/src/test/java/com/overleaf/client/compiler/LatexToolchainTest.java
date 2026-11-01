package com.overleaf.client.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LatexToolchainTest {
    @TempDir Path root;

    @Test void explicitSelectionIsNeverOverridden() {
        assertEquals("chosen/latexmk", LatexToolchain.resolve("chosen/latexmk", Map.of(), root, true, p -> true));
    }
    @Test void portableIsDiscoveredWithoutPathOrSavedPreference() {
        Path portable = root.resolve("target/TinyTeX/bin/windows/latexmk.exe");
        assertEquals(portable.toString(), LatexToolchain.resolve("", Map.of(), root.resolve("client"), true,
                portable::equals));
    }
    @Test void pathInstallationWinsOverPortable() {
        Path bin = root.resolve("installed bin");
        Path expected = bin.resolve("latexmk.exe");
        assertEquals(expected.toString(), LatexToolchain.resolve("", Map.of("Path", bin.toString()), root, true,
                p -> p.equals(expected) || p.toString().contains("TinyTeX")));
    }
    @Test void environmentOverrideAndMissingToolsHavePredictableResults() {
        assertEquals("custom-latexmk", LatexToolchain.resolve("", Map.of("OVERLEAF_LATEXMK", "custom-latexmk"),
                root, true, p -> false));
        assertEquals("latexmk", LatexToolchain.resolve("", Map.of(), root, true, p -> false));
    }
}

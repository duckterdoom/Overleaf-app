package com.overleaf.client.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncTexServiceTest {
    @TempDir Path root;

    @Test
    void parsesFirstMatchAndPreservesDriveColon() {
        var fields =
                SyncTexService.parseResult(
                        "noise\nSyncTeX result begin\nInput:C:/Project/part.tex\nLine:42\nColumn:-1\nInput:other.tex\nLine:99\nSyncTeX result end\n");
        assertEquals("C:/Project/part.tex", fields.get("Input"));
        assertEquals("42", fields.get("Line"));
        assertTrue(SyncTexService.parseResult("No results").isEmpty());
    }

    @Test
    void absentIndexCannotUseOldData() throws Exception {
        Path pdf = Files.createDirectories(root.resolve("build")).resolve("main.pdf");
        Files.writeString(pdf, "%PDF");
        assertThrows(java.io.IOException.class, () -> SyncTexService.load(pdf, "latexmk"));
    }
}

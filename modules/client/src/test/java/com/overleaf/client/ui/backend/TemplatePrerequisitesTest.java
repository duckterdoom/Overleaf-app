package com.overleaf.client.ui.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplatePrerequisitesTest {
    @TempDir Path root;

    @Test
    void systemTexMissingPackageAndBiberAreExplainedButNeverInstalled() throws Exception {
        var inspector =
                new TemplatePrerequisites(
                        root.resolve("system-bin"),
                        root,
                        true,
                        (command, time) ->
                                command.getLast().equals("csquotes.sty")
                                                || command.getFirst().endsWith("biber.exe")
                                        ? new TemplatePrerequisites.Result(1, "")
                                        : new TemplatePrerequisites.Result(0, "found"));
        var report = inspector.inspect(ProjectTemplates.get("masters-thesis"));
        assertFalse(report.ready());
        assertFalse(report.bundled());
        assertTrue(report.message(true).contains("biber"));
        assertNotEquals(report.message(true), report.message(false));
        assertThrows(
                java.io.IOException.class,
                () -> inspector.install(report, p -> fail("System install not allowed")));
    }

    @Test
    void bundledInstallUsesValidatedPackageMappingAndRecheckBecomesReady() throws Exception {
        Path bin = Files.createDirectories(root.resolve("target/TinyTeX/bin/windows"));
        Files.createDirectories(root.resolve("target/TinyTeX/texmf-dist"));
        Files.createFile(bin.resolve("tlmgr.bat"));
        boolean[] installed = {false};
        List<List<String>> installs = new ArrayList<>();
        var inspector =
                new TemplatePrerequisites(
                        bin,
                        root,
                        true,
                        (command, time) -> {
                            if (command.contains("install")) {
                                installs.add(command);
                                installed[0] = true;
                                return new TemplatePrerequisites.Result(0, "installed");
                            }
                            if (command.getLast().equals("lmodern.sty") && !installed[0])
                                return new TemplatePrerequisites.Result(1, "");
                            return new TemplatePrerequisites.Result(0, "found");
                        });
        var report = inspector.inspect(ProjectTemplates.get("research-article"));
        assertTrue(report.bundled());
        assertEquals(1, report.missing().size());
        inspector.install(report, p -> assertEquals("lm", p));
        assertEquals("lm", installs.getFirst().getLast());
        assertTrue(inspector.inspect(ProjectTemplates.get("research-article")).ready());
    }

    @Test
    void interruptPropagatesToCallerWithoutStartingFurtherProbes() {
        var inspector =
                new TemplatePrerequisites(
                        root,
                        root,
                        true,
                        (command, time) -> {
                            throw new InterruptedException("cancelled");
                        });
        assertThrows(
                InterruptedException.class,
                () -> inspector.inspect(ProjectTemplates.get("research-article")));
    }
}

package com.overleaf.client.ui.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectTemplatesTest {
    @TempDir Path root;

    @Test
    void everyMetadataEntryHasUniqueIdentityRequiredFieldsAndBothLanguageSources()
            throws Exception {
        assertEquals(9, ProjectTemplates.categories().size());
        assertTrue(ProjectTemplates.all().size() >= 25);
        Set<String> ids = new HashSet<>();
        for (var template : ProjectTemplates.all()) {
            assertTrue(ids.add(template.id()));
            assertFalse(template.description(true).isBlank());
            assertFalse(template.description(false).isBlank());
            assertFalse(template.tags().isEmpty());
            assertFalse(template.requiredPackages().isEmpty());
            assertTrue(template.paths().contains(template.mainFile()));
            for (boolean vi : List.of(false, true)) {
                var files =
                        ProjectTemplates.files(
                                template.id(), "Title & 50% {demo}", vi, "Trường A & B");
                assertTrue(files.containsKey(template.mainFile()));
                assertTrue(
                        files.get(template.mainFile())
                                .startsWith("% !TeX program = " + template.engine()));
                for (var text : files.values()) assertFalse(text.contains("{{"));
                assertTrue(String.join("\n", files.values()).contains("Trường A \\& B"));
                Path dir = Files.createDirectory(root.resolve(template.id() + "-" + vi));
                ProjectTemplates.materialize(dir, template.id(), "Title", vi, "");
                for (String name : template.paths())
                    assertTrue(Files.isRegularFile(dir.resolve(name)), name);
                assertEquals(137, Files.readAllBytes(dir.resolve("figures/results.png"))[0] & 255);
            }
        }
        for (var category : ProjectTemplates.categories())
            assertTrue(
                    ProjectTemplates.all().stream()
                                    .filter(t -> t.category().equals(category.id()))
                                    .count()
                            >= 2);
    }

    @Test
    void aliasesStartersAndAccentInsensitiveSearchRemainCompatible() {
        assertEquals("journal", ProjectTemplates.get("journal").category());
        assertEquals("blank", ProjectTemplates.get(null).id());
        assertTrue(
                ProjectTemplates.files("blank", "Test", true, "")
                        .get("main.tex")
                        .contains("Giới thiệu"));
        assertTrue(
                ProjectTemplates.files("example", "Test", true, "").containsKey("references.bib"));
        assertTrue(
                ProjectTemplates.search("luan van", null, null).stream()
                        .anyMatch(t -> t.category().equals("theses")));
        assertTrue(ProjectTemplates.search("thesis", "theses", "biblatex").size() >= 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> ProjectTemplates.files("../evil", "Test", true, ""));
    }
}

package com.overleaf.client.ui.backend;

import java.nio.file.Path;

public record NewProjectRequest(String name, String icon, String color, Path parent,
                                Kind kind, Path source, String repository, String template, boolean vietnamese) {
    public enum Kind { BLANK, ZIP, WORD, MARKDOWN, GITHUB, EXAMPLE, TEMPLATE }
}

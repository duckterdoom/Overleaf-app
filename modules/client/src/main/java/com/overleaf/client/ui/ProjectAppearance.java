package com.overleaf.client.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.paint.Color;

/** Icons and colors a project can be given in the New project dialog. */
public final class ProjectAppearance {

    public static final String DEFAULT_ICON = "folder";
    public static final String DEFAULT_COLOR = "#23664A";

    /** Icon id -> 24x24 stroke path, in the order shown in the picker. */
    private static final Map<String, String> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("folder", UiSupport.FOLDER);
        ICONS.put(
                "file",
                "M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8zM14 2v6h6M16 13H8M16 17H8M10 9H8");
        ICONS.put(
                "book",
                "M2 3h6a4 4 0 014 4v14a3 3 0 00-3-3H2zM22 3h-6a4 4 0 00-4 4v14a3 3 0 013-3h7z");
        ICONS.put("cap", "M22 10L12 5 2 10l10 5 10-5zM6 12v5c3 3 9 3 12 0v-5M22 10v6");
        ICONS.put(
                "flask",
                "M10 2v7.5a2 2 0 01-.2.9L4.7 20.5a1 1 0 00.9 1.5h12.8a1 1 0 00.9-1.5L14.2 10.4a2 2 0 01-.2-.9V2M8.5 2h7M7 16h10");
        ICONS.put(
                "bulb",
                "M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 006 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5M9 18h6M10 22h4");
        ICONS.put(
                "calc",
                "M6 2h12a2 2 0 012 2v16a2 2 0 01-2 2H6a2 2 0 01-2-2V4a2 2 0 012-2zM8 6h8M8 10h.01M12 10h.01M16 10h.01M8 14h.01M12 14h.01M16 14v4M8 18h.01M12 18h.01");
        ICONS.put("pi", "M5 7h14M9 7v10M15 7v9c0 1.5.8 2 2.2 2");
        ICONS.put("pen", UiSupport.EDIT);
        ICONS.put("code", "M16 18l6-6-6-6M8 6l-6 6 6 6");
        ICONS.put("globe", UiSupport.GLOBE);
        ICONS.put(
                "star",
                "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z");
        ICONS.put(
                "heart",
                "M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z");
        ICONS.put("bell", "M6 8a6 6 0 0112 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 003.4 0");
    }

    private static final List<String> COLORS =
            List.of(
                    "#23664A", "#6B9E75", "#4A7FDB", "#E6A93E", "#DB5E58", "#CC5A94", "#6455D9",
                    "#E9C44B", "#DE8446", "#5FB3A5", "#4F86F0", "#8B8B95", "#1F2336", "#B49BF2",
                    "#8DB59B");

    private ProjectAppearance() {}

    public static Map<String, String> icons() {
        return ICONS;
    }

    public static List<String> colors() {
        return COLORS;
    }

    /** Path for an icon id, or null when the id is unknown or unset. */
    public static String iconPath(String id) {
        return id == null ? null : ICONS.get(id);
    }

    /** The picked color as drawn: as-is in light mode, lifted for dark surfaces in dark mode. */
    public static Color color(String hex) {
        Color picked;
        try {
            picked = Color.web(hex == null ? DEFAULT_COLOR : hex);
        } catch (IllegalArgumentException invalid) {
            picked = Color.web(DEFAULT_COLOR);
        }
        return ThemeManager.forSurface(picked);
    }
}

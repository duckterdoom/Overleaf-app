package com.overleaf.client.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recolors the base stylesheet for the chosen theme and light/dark mode and
 * hands it to the scene. The base file stays the single source of layout;
 * greens are shifted to the theme's hue and, in dark mode, the neutral greys
 * are remapped per property (so white text on a green button stays white while
 * a white panel turns dark).
 */
public final class ThemeManager {

    /**
     * {@code darkFg} and {@code darkEmphasis} are the theme's dark-mode accents, chosen the way Material and
     * GitHub Primer do it: a lighter, desaturated tone for text and icons (7.6-9.5:1 on the dark cards, WCAG
     * AAA) and a deeper tone for filled buttons (white text 5.0-5.7:1, AA; 3:1 against the cards). Tinted
     * areas use translucent washes of the emphasis tone instead of solid tints.
     */
    private record Theme(String id, double hue, double saturation, double lift, String page,
                         String darkFg, String darkEmphasis) {
    }

    private static final Map<String, Theme> THEMES = Map.of(
            "forest", new Theme("forest", -1, 1.0, 0.0, null, "#6CC092", "#1E7A52"),
            "mint", new Theme("mint", 172, 0.90, 0.05, "#F2F8F6", "#6CC5B6", "#18776A"),
            "bee", new Theme("bee", 43, 0.85, 0.13, "#FAF7EA", "#E0BC5E", "#956608"),
            "parchment", new Theme("parchment", 14, 0.72, 0.24, "#F8F4EC", "#E59A7E", "#A9492A"));

    // Dark mode follows GitHub's dark palette: canvas #0D1117, cards #161B22, subtle fills #21262D,
    // borders #30363D, text #E6EDF3 / muted #8B949E.
    private static final Map<String, String> DARK_SURFACE = Map.ofEntries(
            Map.entry("#FFFFFF", "#161B22"), Map.entry("#F7F7F5", "#0D1117"),
            Map.entry("#FCFCFB", "#0D1117"), Map.entry("#FCFCFA", "#0D1117"), Map.entry("#FDFDFB", "#0D1117"),
            Map.entry("#FBFCFA", "#0D1117"), Map.entry("#F6F6F3", "#0D1117"), Map.entry("#F6F6F2", "#0D1117"),
            Map.entry("#F7F7F4", "#0D1117"), Map.entry("#F8F8F5", "#0D1117"),
            // The code panel is dark in both modes; in dark mode it joins the GitHub palette too.
            Map.entry("#1B222C", "#0D1117"), Map.entry("#252E3C", "#161B22"), Map.entry("#212A37", "#161B22"),
            Map.entry("#2F3A4C", "#21262D"), Map.entry("#384559", "#30363D"), Map.entry("#34415A", "#30363D"),
            Map.entry("#EBEBE6", "#010409"));

    private static final Map<String, String> DARK_BORDER = Map.of("#2F3A4C", "#30363D");

    private static final Pattern DECLARATION = Pattern.compile("(-fx-[a-z-]+)\\s*:\\s*([^;}]+)");
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}\\b");
    private static final Pattern RGBA = Pattern.compile("rgba\\((\\d+),\\s*(\\d+),\\s*(\\d+),");

    private static String baseCss;
    private static boolean dark;
    private static Theme theme = THEMES.get("forest");

    private ThemeManager() {
    }

    public static boolean isDark() {
        return dark;
    }

    /** Main accent color of the current theme (what #23664A is in Forest). */
    public static Color accent() {
        return Color.web(recolor("#23664A", Kind.TEXT_OR_FILL_ACCENT));
    }

    /** The theme's accent hue at the given lightness (0..1), e.g. a dark head and lighter body for the snake. */
    public static Color accentAt(double lightness) {
        double[] hsl;
        if (dark) {
            Color fg = Color.web(theme.darkFg());
            hsl = toHsl((int) Math.round(fg.getRed() * 255), (int) Math.round(fg.getGreen() * 255),
                    (int) Math.round(fg.getBlue() * 255));
        } else {
            hsl = shiftAccent(toHsl(35, 102, 74), false);
        }
        hsl[2] = lightness;
        return Color.web(fromHsl(hsl));
    }

    /**
     * A color the user picked (a project's color) made readable on the current surface: unchanged in light
     * mode; in dark mode lifted to a light tone and desaturated 20%, as dark-theme guidance recommends for
     * colors on dark backgrounds.
     */
    public static Color forSurface(Color color) {
        if (!dark) return color;
        double[] hsl = toHsl((int) Math.round(color.getRed() * 255), (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
        hsl[1] *= 0.8;
        hsl[2] = Math.max(hsl[2], 0.68);
        return Color.web(fromHsl(hsl));
    }

    /** Snake food: amber, unless the theme itself is amber/terracotta, where it would blend into the snake. */
    public static Color foodColor() {
        return switch (theme.id()) {
            case "bee" -> Color.web("#C2415D");
            case "parchment" -> Color.web("#3E8E8B");
            default -> Color.web("#C9822B");
        };
    }

    /** Text/icon ink: near-black in light mode, near-white in dark mode. */
    public static Color ink() {
        return dark ? Color.web("#C9D1D9") : Color.web("#2B2B28");
    }

    /** Five heat-map levels from empty to busiest. */
    public static Color[] heatLevels() {
        String[] base = {"#EEEEEA", "#DCE9DF", "#B3D1BD", "#77A98B", "#2A6A4D"};
        Color[] out = new Color[5];
        for (int i = 0; i < 5; i++) {
            out[i] = Color.web(recolor(base[i], i == 0 ? Kind.BACKGROUND : Kind.HEAT));
        }
        if (dark) {
            // Like GitHub's dark contribution graph: dim tint up to the bright accent.
            Color card = Color.web("#161B22");
            Color emphasis = Color.web(theme.darkEmphasis());
            Color fg = Color.web(theme.darkFg());
            out[0] = Color.web("#21262D");
            out[1] = card.interpolate(emphasis, 0.40);
            out[2] = card.interpolate(emphasis, 0.80);
            out[3] = emphasis.interpolate(fg, 0.45);
            out[4] = fg;
        }
        return out;
    }

    public static boolean isSystemDark() {
        try {
            return Platform.getPreferences().getColorScheme() == javafx.application.ColorScheme.DARK;
        } catch (RuntimeException unsupported) {
            return false;
        }
    }

    /** Re-reads the settings, rebuilds the stylesheet and swaps it into the scene. */
    public static void apply(Scene scene, AppSettings settings) {
        String mode = settings.get(AppSettings.MODE);
        dark = "dark".equals(mode) || ("system".equals(mode) && isSystemDark());
        theme = THEMES.getOrDefault(settings.get(AppSettings.THEME), THEMES.get("forest"));

        StringBuilder css = new StringBuilder(recolorStylesheet(baseCss()));
        String family = "serif".equals(settings.get(AppSettings.FONT_STYLE)) ? "\"Georgia\"" : "\"System\"";
        css.append("\n.root { -fx-font-family: ").append(family).append("; }\n");
        css.append(".home-content { -fx-max-width: ")
                .append(switch (settings.get(AppSettings.CONTENT_WIDTH)) {
                    case "compact" -> "960px";
                    case "wide" -> "1600px";
                    default -> "1240px";
                }).append("; }\n");
        try {
            Path file = Files.createTempFile("overleaf-theme-", ".css");
            file.toFile().deleteOnExit();
            Files.writeString(file, css.toString(), StandardCharsets.UTF_8);
            scene.getStylesheets().setAll(file.toUri().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        scene.getRoot().getStyleClass().removeAll("theme-dark", "theme-light");
        scene.getRoot().getStyleClass().add(dark ? "theme-dark" : "theme-light");
    }

    private static String baseCss() {
        if (baseCss == null) {
            try (InputStream in = ThemeManager.class.getResourceAsStream("/styles/overleaf-theme.css")) {
                baseCss = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException | NullPointerException e) {
                throw new IllegalStateException("overleaf-theme.css is missing from the build", e);
            }
        }
        return baseCss;
    }

    // -------------------------------------------------------------- recoloring

    private enum Kind { BACKGROUND, BORDER, TEXT, HEAT, TEXT_OR_FILL_ACCENT }

    private static String recolorStylesheet(String css) {
        Matcher declarations = DECLARATION.matcher(css);
        StringBuilder out = new StringBuilder();
        while (declarations.find()) {
            String property = declarations.group(1);
            String value = declarations.group(2);
            Kind kind = switch (property) {
                case "-fx-text-fill", "-fx-fill", "-fx-stroke", "-fx-prompt-text-fill", "-fx-highlight-text-fill" -> Kind.TEXT;
                case "-fx-border-color" -> Kind.BORDER;
                default -> Kind.BACKGROUND;
            };
            String replaced = value;
            if (!property.equals("-fx-effect") && !property.equals("-fx-font-family")) {
                Matcher hex = HEX.matcher(value);
                StringBuilder v = new StringBuilder();
                while (hex.find()) {
                    hex.appendReplacement(v, recolor(hex.group().toUpperCase(Locale.ROOT), kind));
                }
                hex.appendTail(v);
                replaced = v.toString();
            }
            replaced = recolorRgba(replaced);
            declarations.appendReplacement(out, Matcher.quoteReplacement(property + ": " + replaced));
        }
        declarations.appendTail(out);
        return out.toString();
    }

    private static String recolorRgba(String value) {
        Matcher m = RGBA.matcher(value);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            double[] hsl = toHsl(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            String replacement = m.group();
            if (isAccentHue(hsl)) {
                Color c = Color.web(fromHsl(shiftAccent(hsl, false)));
                replacement = "rgba(" + Math.round(c.getRed() * 255) + ", " + Math.round(c.getGreen() * 255)
                        + ", " + Math.round(c.getBlue() * 255) + ",";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String recolor(String hex, Kind kind) {
        Color c = Color.web(hex);
        double[] hsl = toHsl((int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));

        if (kind == Kind.HEAT) {
            double[] shifted = shiftAccent(hsl, false);
            if (dark) shifted[2] = Math.min(0.78, 1.0 - shifted[2] * 0.85 + 0.05);
            return fromHsl(shifted);
        }
        if (kind == Kind.TEXT_OR_FILL_ACCENT) {
            kind = Kind.TEXT;
        }
        if (isAccentHue(hsl)) {
            return dark ? darkAccent(hsl[2], kind) : fromHsl(shiftAccent(hsl, true));
        }
        if (!dark) {
            return hex.equals("#F7F7F5") && theme.page() != null && kind == Kind.BACKGROUND ? theme.page() : hex;
        }

        boolean neutral = hsl[1] < 0.12;
        switch (kind) {
            case TEXT -> {
                if (hsl[2] >= 0.85) return hex;
                if (!neutral) {
                    hsl[2] = Math.max(hsl[2], 0.62);
                    return fromHsl(hsl);
                }
                if (hsl[2] >= 0.5) return "#8B949E";
                if (hsl[2] < 0.2) return "#E6EDF3";
                if (hsl[2] < 0.35) return "#C9D1D9";
                return "#B1BAC4";
            }
            case BORDER -> {
                String mapped = DARK_BORDER.get(hex);
                if (mapped != null) return mapped;
                if (hsl[2] > 0.88) return "#30363D";
                if (hsl[2] > 0.76) return "#3D444D";
                return hex;
            }
            default -> {
                String mapped = DARK_SURFACE.get(hex);
                if (mapped != null) return mapped;
                if (hsl[2] > 0.93) return neutral ? "#21262D" : fromHsl(new double[]{hsl[0], hsl[1] * 0.5, 0.14});
                if (hsl[2] > 0.88) return "#262C36";
                if (hsl[2] > 0.78) return "#30363D";
                return hex;
            }
        }
    }

    /**
     * Dark-mode stand-in for one of the light theme's greens, chosen by what it was used for: text and icons
     * get the light accent; pale tints become translucent washes of the deep accent; solid fills (buttons,
     * their hover and pressed states) keep their relative depth around the deep accent.
     */
    private static String darkAccent(double lightness, Kind kind) {
        Color emphasis = Color.web(theme.darkEmphasis());
        switch (kind) {
            case TEXT -> {
                return theme.darkFg();
            }
            case BORDER -> {
                return lightness > 0.75 ? rgba(emphasis, 0.45) : theme.darkEmphasis();
            }
            default -> {
                if (lightness > 0.93) return rgba(emphasis, 0.10);
                if (lightness > 0.86) return rgba(emphasis, 0.16);
                if (lightness > 0.80) return rgba(emphasis, 0.24);
                double[] hsl = toHsl((int) Math.round(emphasis.getRed() * 255), (int) Math.round(emphasis.getGreen() * 255),
                        (int) Math.round(emphasis.getBlue() * 255));
                hsl[2] = Math.max(0.05, Math.min(0.6, hsl[2] + (lightness - 0.27) * 0.8));
                return fromHsl(hsl);
            }
        }
    }

    private static String rgba(Color color, double alpha) {
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.2f)", Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255), alpha);
    }

    private static boolean isAccentHue(double[] hsl) {
        return hsl[1] >= 0.12 && hsl[0] >= 80 && hsl[0] <= 200;
    }

    /** Moves a green to the theme's hue; dark greens are lifted so warm themes do not turn muddy. */
    private static double[] shiftAccent(double[] hsl, boolean lift) {
        double[] out = hsl.clone();
        if (theme.hue() >= 0) {
            out[0] = theme.hue();
            out[1] = Math.min(1, hsl[1] * theme.saturation());
            if (lift && hsl[2] < 0.5) {
                out[2] = Math.min(0.62, hsl[2] + theme.lift());
            }
        }
        return out;
    }

    private static double[] toHsl(int r, int g, int b) {
        double rf = r / 255.0, gf = g / 255.0, bf = b / 255.0;
        double max = Math.max(rf, Math.max(gf, bf)), min = Math.min(rf, Math.min(gf, bf));
        double l = (max + min) / 2, h = 0, s = 0;
        double d = max - min;
        if (d > 1e-9) {
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == rf) h = ((gf - bf) / d) + (gf < bf ? 6 : 0);
            else if (max == gf) h = ((bf - rf) / d) + 2;
            else h = ((rf - gf) / d) + 4;
            h *= 60;
        }
        return new double[]{h, s, l};
    }

    private static String fromHsl(double[] hsl) {
        double h = ((hsl[0] % 360) + 360) % 360 / 360.0, s = clamp(hsl[1]), l = clamp(hsl[2]);
        double r, g, b;
        if (s == 0) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = channel(p, q, h + 1.0 / 3);
            g = channel(p, q, h);
            b = channel(p, q, h - 1.0 / 3);
        }
        return String.format("#%02X%02X%02X", Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    private static double channel(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2) return q;
        if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6;
        return p;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}

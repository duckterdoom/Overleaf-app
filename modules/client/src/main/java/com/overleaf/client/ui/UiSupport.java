package com.overleaf.client.ui;

import java.io.File;
import java.net.URL;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * Small shared helpers for the light Qoder-style shell: brand mark, line icons, dialog theming and
 * a few layout utilities.
 */
public final class UiSupport {

    public static final String LEAF_PATH =
            "M17 8C8 10 5.9 16.17 3.82 21.34l1.89.66.95-2.3c.48.17.98.3 1.34.3C19 20 22 3 22 3"
                    + "c-1 2-8 2.25-13 3.25S2 11.5 2 13.5s1.75 3.75 1.75 3.75C7 8 17 8 17 8z";

    public static final String ARROW_LEFT = "M19 12H5M12 19l-7-7 7-7";
    public static final String CHEVRON_DOWN = "M6 9l6 6 6-6";
    public static final String EYE =
            "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8zM12 15a3 3 0 100-6 3 3 0 000 6z";
    public static final String EYE_OFF =
            "M17.94 17.94A10.07 10.07 0 0112 20c-7 0-11-8-11-8a18.45 18.45 0 015.06-5.94M9.9 4.24A9.12 9.12 0 0112 4c7 0 11 8 11 8a18.5 18.5 0 01-2.16 3.19m-6.72-1.07a3 3 0 11-4.24-4.24M1 1l22 22";
    public static final String UNDO = "M9 14L4 9l5-5M4 9h10.5a5.5 5.5 0 010 11H11";
    public static final String REDO = "M15 14l5-5-5-5M20 9H9.5a5.5 5.5 0 000 11H13";
    public static final String BOLD = "M6 4h8a4 4 0 010 8H6zM6 12h9a4 4 0 010 8H6z";
    public static final String ITALIC = "M19 4h-9M14 20H5M15 4L9 20";
    public static final String SEARCH = "M11 3a8 8 0 100 16 8 8 0 000-16zM21 21l-4.35-4.35";
    public static final String ARROW_UP = "M12 19V5M5 12l7-7 7 7";
    public static final String ARROW_DOWN = "M12 5v14M19 12l-7 7-7-7";
    public static final String LOCK = "M5 11h14v10H5zM8 11V7a4 4 0 018 0v4";
    public static final String PLUS = "M12 5v14M5 12h14";
    public static final String FOLDER =
            "M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2z";
    public static final String FILE =
            "M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8zM14 2v6h6";
    public static final String USERS =
            "M17 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9.5 7a4 4 0 110 8 4 4 0 010-8zM22 21v-2a4 4 0 00-3-3.87";
    public static final String PLAY = "M8 5l11 7-11 7z";
    public static final String SAVE =
            "M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2zM17 21v-8H7v8M7 3v5h8";
    public static final String SETTINGS =
            "M12 15a3 3 0 100-6 3 3 0 000 6zM19.4 15a1.7 1.7 0 00.34 1.87l.06.06a2 2 0 11-2.83 2.83l-.06-.06a1.7 1.7 0 00-1.87-.34 1.7 1.7 0 00-1 1.55V21a2 2 0 11-4 0v-.09a1.7 1.7 0 00-1.11-1.55 1.7 1.7 0 00-1.87.34l-.06.06a2 2 0 11-2.83-2.83l.06-.06a1.7 1.7 0 00.34-1.87 1.7 1.7 0 00-1.55-1H3a2 2 0 110-4h.09a1.7 1.7 0 001.55-1.11 1.7 1.7 0 00-.34-1.87l-.06-.06a2 2 0 112.83-2.83l.06.06a1.7 1.7 0 001.87.34H9a1.7 1.7 0 001-1.55V3a2 2 0 114 0v.09a1.7 1.7 0 001 1.55 1.7 1.7 0 001.87-.34l.06-.06a2 2 0 112.83 2.83l-.06.06a1.7 1.7 0 00-.34 1.87V9a1.7 1.7 0 001.55 1H21a2 2 0 110 4h-.09a1.7 1.7 0 00-1.51 1z";
    public static final String SIGN_OUT =
            "M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9";
    public static final String REFRESH = "M21 12a9 9 0 11-2.64-6.36M21 3v6h-6";
    public static final String CLOSE = "M18 6L6 18M6 6l12 12";
    public static final String TRASH =
            "M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6M10 11v6M14 11v6";
    public static final String EDIT = "M12 20h9M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z";
    public static final String INFO = "M12 22a10 10 0 100-20 10 10 0 000 20zM12 16v-4M12 8h.01";
    public static final String ALERT =
            "M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0zM12 9v4M12 17h.01";
    public static final String LINK =
            "M10 13a5 5 0 007.5.5l3-3a5 5 0 00-7-7l-1.7 1.7M14 11a5 5 0 00-7.5-.5l-3 3a5 5 0 007 7l1.7-1.7";
    public static final String PDF =
            "M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8zM14 2v6h6M9 15h6M9 18h4";
    public static final String GLOBE =
            "M12 2a10 10 0 100 20 10 10 0 000-20zM2 12h20"
                    + "M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z";

    private UiSupport() {}

    public static Node brandMark(double size) {
        SVGPath leaf = new SVGPath();
        leaf.setContent(LEAF_PATH);
        leaf.getStyleClass().add("brand-mark");
        double scale = size / 24.0;
        leaf.setScaleX(scale);
        leaf.setScaleY(scale);
        StackPane holder = new StackPane(leaf);
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);
        return holder;
    }

    public static HBox brandLockup(double markSize, String word) {
        Label name = new Label(word);
        name.getStyleClass().add("brand-word");
        HBox box = new HBox(8, brandMark(markSize), name);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return box;
    }

    public static SVGPath lineIcon(String path, double size, Color color) {
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.setFill(Color.TRANSPARENT);
        icon.setStroke(color);
        icon.setStrokeWidth(size >= 16 ? 1.7 : 1.9);
        icon.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        icon.getStyleClass().add("line-icon");
        double scale = size / 24.0;
        icon.setScaleX(scale);
        icon.setScaleY(scale);
        return icon;
    }

    public static StackPane iconBox(String path, double size, Color color, double boxSize) {
        StackPane holder = new StackPane(lineIcon(path, size, color));
        holder.setMinSize(boxSize, boxSize);
        holder.setPrefSize(boxSize, boxSize);
        holder.setMaxSize(boxSize, boxSize);
        return holder;
    }

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static void setHgrow(Node node) {
        HBox.setHgrow(node, javafx.scene.layout.Priority.ALWAYS);
    }

    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, javafx.scene.layout.Priority.ALWAYS);
        return region;
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String trimmed = name.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    /**
     * Shows the profile picture as a circle inside an avatar label, or the initials when there is
     * no picture (or it cannot be read).
     */
    public static void applyAvatar(
            Label avatar,
            String name,
            java.util.Optional<java.nio.file.Path> picture,
            double size) {
        javafx.scene.image.ImageView view =
                picture.map(path -> circularImage(path, size)).orElse(null);
        if (view != null) {
            avatar.setText(null);
            avatar.setGraphic(view);
            avatar.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            if (!avatar.getStyleClass().contains("avatar-photo"))
                avatar.getStyleClass().add("avatar-photo");
        } else {
            avatar.setGraphic(null);
            avatar.setContentDisplay(javafx.scene.control.ContentDisplay.TEXT_ONLY);
            avatar.setText(initials(name));
            avatar.getStyleClass().remove("avatar-photo");
        }
    }

    /**
     * Centre-cropped, circular image of {@code size} px; {@code null} if the file is not a readable
     * image.
     */
    public static javafx.scene.image.ImageView circularImage(java.nio.file.Path path, double size) {
        javafx.scene.image.Image image =
                new javafx.scene.image.Image(path.toUri().toString(), false);
        if (image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return null;
        }
        double side = Math.min(image.getWidth(), image.getHeight());
        javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
        view.setViewport(
                new javafx.geometry.Rectangle2D(
                        (image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setSmooth(true);
        view.setClip(new javafx.scene.shape.Circle(size / 2, size / 2, size / 2));
        return view;
    }

    public static Tooltip tooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(Duration.millis(280));
        return tip;
    }

    public static URL stylesheet() {
        URL url = UiSupport.class.getResource("/styles/overleaf-theme.css");
        if (url != null) {
            return url;
        }
        File local = new File("client/src/main/resources/styles/overleaf-theme.css");
        if (local.exists()) {
            try {
                return local.toURI().toURL();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}

package com.overleaf.client.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * Line-number column shown to the left of the code editor.
 *
 * <p>It only reads from the TextArea (text, caret, scroll position), so it does not touch
 * CodeOverlay. Numbers are drawn on a canvas, only for the lines that are visible. With word wrap
 * on, a long line takes several rows but keeps one number (like Overleaf).
 */
final class LineNumberGutter extends Region {

    private static final double SIDE_PADDING = 10;
    private static final int MIN_DIGITS = 3;

    private final TextArea editor;
    private final Canvas canvas = new Canvas();

    private Font font = Font.font("Monospaced", 14);
    private boolean wrap;
    private double lineHeight = 18;
    private double charWidth = 8;

    private int[] rows = new int[] {1}; // visual rows of each logical line
    private boolean rowsDirty = true;
    private boolean repaintQueued;
    private int lastDigits = MIN_DIGITS;

    // Semi-transparent, so it reads on both the light and the dark editor background.
    private Color numberColor = Color.rgb(140, 148, 165, 0.85);
    private Color currentColor = Color.rgb(200, 206, 220, 1.0);
    private Color borderColor = Color.rgb(128, 128, 128, 0.25);

    LineNumberGutter(TextArea editor) {
        this.editor = editor;
        getChildren().add(canvas);
        setMinWidth(USE_PREF_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        setMouseTransparent(true);
        measureFont();

        editor.textProperty()
                .addListener((o, a, b) -> {
                    rowsDirty = true;
                    queueRepaint();
                });
        editor.caretPositionProperty().addListener((o, a, b) -> queueRepaint());
        editor.scrollTopProperty().addListener((o, a, b) -> queueRepaint());
        editor.widthProperty()
                .addListener((o, a, b) -> {
                    if (wrap) rowsDirty = true;
                    queueRepaint();
                });
        editor.heightProperty().addListener((o, a, b) -> queueRepaint());
    }

    void setFont(Font font) {
        this.font = font;
        measureFont();
        rowsDirty = true;
        requestLayout();
        queueRepaint();
    }

    void setWrap(boolean wrap) {
        this.wrap = wrap;
        rowsDirty = true;
        queueRepaint();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected double computePrefWidth(double height) {
        return digits() * charWidth + SIDE_PADDING * 2;
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(Math.max(0, getWidth()));
        canvas.setHeight(Math.max(0, getHeight()));
        redraw();
    }

    // ------------------------------------------------------------------ painting

    private void queueRepaint() {
        if (repaintQueued) return;
        repaintQueued = true;
        // Wait one pulse so the TextArea has already updated its own scroll position.
        Platform.runLater(() -> {
            repaintQueued = false;
            redraw();
        });
    }

    private void redraw() {
        if (rowsDirty) recomputeRows();

        int digits = digits();
        if (digits != lastDigits) { // 99 -> 100 lines: the column gets wider
            lastDigits = digits;
            requestLayout();
        }

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        if (w <= 0 || h <= 0) return;

        gc.setStroke(borderColor);
        gc.strokeLine(w - 0.5, 0, w - 0.5, h);

        gc.setFont(font);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.TOP);

        int caretLine = caretLineIndex();
        double y = contentTopPadding() - editor.getScrollTop();
        for (int i = 0; i < rows.length; i++) {
            double next = y + rows[i] * lineHeight;
            if (next >= 0) {
                if (y > h) break;
                gc.setFill(i == caretLine ? currentColor : numberColor);
                gc.fillText(Integer.toString(i + 1), w - SIDE_PADDING, y);
            }
            y = next;
        }
    }

    // ------------------------------------------------------------------ measuring

    private void measureFont() {
        Text probe = new Text("Ag");
        probe.setFont(font);
        lineHeight = probe.getLayoutBounds().getHeight();
        probe.setText("M");
        charWidth = probe.getLayoutBounds().getWidth();
    }

    private int digits() {
        return Math.max(MIN_DIGITS, Integer.toString(rows.length).length());
    }

    private int caretLineIndex() {
        String text = editor.getText();
        if (text == null) return 0;
        int end = Math.min(editor.getCaretPosition(), text.length());
        int line = 0;
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private Region content() {
        Node n = editor.lookup(".content");
        return n instanceof Region r ? r : null;
    }

    private double contentTopPadding() {
        Region content = content();
        return content != null ? content.getInsets().getTop() : 4;
    }

    private double wrapWidth() {
        Region content = content();
        if (content != null && content.getWidth() > 0) {
            Insets in = content.getInsets();
            return content.getWidth() - in.getLeft() - in.getRight();
        }
        return editor.getWidth() - 40; // fallback before the skin is laid out
    }

    private void recomputeRows() {
        rowsDirty = false;
        String text = editor.getText();
        String[] lines = (text == null ? "" : text).split("\n", -1);
        int[] result = new int[lines.length];
        java.util.Arrays.fill(result, 1);

        if (wrap) {
            double width = wrapWidth();
            if (width > charWidth * 4) {
                int columns = (int) Math.floor(width / charWidth);
                Text probe = new Text();
                probe.setFont(font);
                probe.setWrappingWidth(width);
                for (int i = 0; i < lines.length; i++) {
                    // Short lines surely fit; only measure the long ones.
                    if (lines[i].length() <= columns) continue;
                    probe.setText(lines[i]);
                    result[i] =
                            Math.max(
                                    1,
                                    (int)
                                            Math.round(
                                                    probe.getLayoutBounds().getHeight()
                                                            / lineHeight));
                }
            }
        }
        rows = result;
    }
}

package com.overleaf.client.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Syntax-colored view of a {@link TextArea}. The text area stays the editor
 * (input, undo, clipboard, scrolling) but paints nothing; this layer sits
 * behind it and draws the text in colors, plus the selection and caret, since
 * a text area with invisible text also loses its caret.
 * <p>
 * Positions come from the text area's own laid-out text node, so the colors
 * sit exactly where clicks land, and only the lines on screen are drawn, so
 * typing stays fast in long files.
 */
final class CodeOverlay {

    /** Lines drawn above and below the visible ones, so short scrolls do not show uncolored gaps. */
    private static final int MARGIN_LINES = 6;

    private final TextArea area;
    private final StackPane root = new StackPane();
    private final Pane layer = new Pane();
    /** Shares the coordinate system of the text area's text node. */
    private final Group group = new Group();
    private final Group lines = new Group();
    private final Path selection = new Path();
    private final Path caret = new Path();
    private final Timeline blink;

    private Font font = Font.font("Menlo", 13);
    private double lineSpacing;
    private boolean wrap;

    private Text areaText;
    private int[] lineStarts = {0};
    private int version;
    private int renderedVersion = -1;
    private int renderedFirst = -1;
    private int renderedLast = -1;
    private final List<TextFlow> renderedFlows = new ArrayList<>();
    private final List<Integer> renderedLines = new ArrayList<>();
    private boolean renderQueued;
    private boolean darkPalette;
    private boolean bindQueued;

    CodeOverlay(TextArea area) {
        this.area = area;

        selection.setStroke(null);
        caret.setStrokeWidth(1.6);
        paintCursorColors();
        caret.setFill(null);
        group.getChildren().addAll(selection, lines, caret);
        group.setMouseTransparent(true);
        layer.getChildren().add(group);
        layer.setMouseTransparent(true);
        layer.setManaged(false);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);
        root.getStyleClass().add("code-stack-layer");
        root.getChildren().addAll(layer, area);

        blink = new Timeline(new KeyFrame(Duration.millis(530), e ->
                caret.setVisible(caretWanted() && !caret.isVisible())));
        blink.setCycleCount(Animation.INDEFINITE);
        blink.play();

        area.textProperty().addListener((obs, was, now) -> {
            version++;
            lineStarts = null;
            queueRender();
        });
        area.caretPositionProperty().addListener((obs, was, now) -> refreshShapes());
        area.selectionProperty().addListener((obs, was, now) -> refreshShapes());
        area.focusedProperty().addListener((obs, was, now) -> refreshShapes());
        area.skinProperty().addListener((obs, was, now) -> queueBind());
        area.sceneProperty().addListener((obs, was, now) -> queueBind());
        root.heightProperty().addListener((obs, was, now) -> queueRender());
        queueBind();
    }

    Node node() {
        return root;
    }

    /** Must match the font the text area uses, or the colors would not sit under the characters. */
    void setFont(Font font) {
        this.font = font;
        // The text area's own Text uses vertically centred line bounds, which spaces lines a little wider
        // than a TextFlow does; measure both pitches for this font and make up the difference.
        double areaPitch = textHeight("X\nX") - textHeight("X");
        double flowPitch = flowHeight("X\nX") - flowHeight("X");
        lineSpacing = Math.max(0, areaPitch - flowPitch);
        version++;
        queueRender();
    }

    /** Re-reads light/dark mode, e.g. after the user switches it in Settings. */
    void repaint() {
        version++;
        queueRender();
    }

    private void paintCursorColors() {
        boolean darkMode = ThemeManager.isDark();
        selection.setFill(darkMode ? Color.web("#388BFD", 0.30) : Color.web("#44475A", 0.95));
        caret.setStroke(darkMode ? Color.web("#E6EDF3") : Color.web("#F8F8F2"));
    }

    void setWrap(boolean wrap) {
        this.wrap = wrap;
        version++;
        queueRender();
    }

    private double textHeight(String sample) {
        Text probe = new Text(sample);
        probe.setFont(font);
        probe.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);
        return probe.getLayoutBounds().getHeight();
    }

    private double flowHeight(String sample) {
        Text probe = new Text(sample);
        probe.setFont(font);
        return new TextFlow(probe).prefHeight(-1);
    }

    // ------------------------------------------------------------- binding

    private void queueBind() {
        if (bindQueued) return;
        bindQueued = true;
        Platform.runLater(() -> {
            bindQueued = false;
            bindAreaText();
        });
    }

    private void bindAreaText() {
        if (area.getScene() == null) return;
        Node content = area.lookup(".content");
        if (content == null || !(content.lookup(".text") instanceof Text text)) {
            queueBind();
            return;
        }
        if (text == areaText) return;
        areaText = text;
        // Scrolling moves the text node; resizing or re-wrapping changes its layout.
        text.localToSceneTransformProperty().addListener((obs, was, now) -> {
            reposition();
            queueRender();
        });
        text.layoutBoundsProperty().addListener((obs, was, now) -> {
            version++;
            queueRender();
        });
        layer.localToSceneTransformProperty().addListener((obs, was, now) -> reposition());
        version++;
        reposition();
        queueRender();
    }

    /** Keeps {@link #group} in the text node's coordinates as the text area scrolls. */
    private void reposition() {
        if (areaText == null || areaText.getScene() == null || layer.getScene() == null) return;
        Point2D origin = layer.sceneToLocal(areaText.localToScene(0, 0));
        if (origin == null) return;
        group.setLayoutX(origin.getX());
        group.setLayoutY(origin.getY());
    }

    // ------------------------------------------------------------- drawing

    private void queueRender() {
        if (renderQueued) return;
        renderQueued = true;
        Platform.runLater(() -> {
            renderQueued = false;
            render();
        });
    }

    private void render() {
        if (areaText == null || areaText.getScene() == null) return;
        String text = area.getText() == null ? "" : area.getText();
        if (lineStarts == null) lineStarts = lineStarts(text);

        int[] visible = visibleLines(text);
        if (visible == null) return;
        int first = Math.max(0, visible[0] - MARGIN_LINES);
        int last = Math.min(lineStarts.length - 1, visible[1] + MARGIN_LINES);
        if (version == renderedVersion && first == renderedFirst && last == renderedLast) {
            refreshShapes();
            return;
        }

        darkPalette = ThemeManager.isDark();
        paintCursorColors();
        double offset = glyphOffset();
        double wrapWidth = wrap ? areaText.getWrappingWidth() : 0;
        List<Node> flows = new ArrayList<>();
        renderedFlows.clear();
        renderedLines.clear();
        for (int line = first; line <= last; line++) {
            int start = lineStarts[line];
            int end = line + 1 < lineStarts.length ? lineStarts[line + 1] - 1 : text.length();
            if (end <= start) continue;
            String lineText = text.substring(start, end);
            TextFlow flow = new TextFlow();
            flow.setLineSpacing(lineSpacing);
            for (LatexSyntax.Run run : LatexSyntax.tokenize(lineText)) {
                Text piece = new Text(lineText.substring(run.start(), run.end()));
                piece.setFont(font);
                piece.setFill(run.kind().color(darkPalette));
                flow.getChildren().add(piece);
            }
            if (wrapWidth > 0) {
                flow.setPrefWidth(wrapWidth);
                flow.setMaxWidth(wrapWidth);
            }
            flow.autosize();
            flow.layout();
            double top = lineTop(start);
            flow.relocate(0, top + offset);
            flows.add(flow);
            renderedFlows.add(flow);
            renderedLines.add(line);
        }
        lines.getChildren().setAll(flows);
        renderedVersion = version;
        renderedFirst = first;
        renderedLast = last;
        refreshShapes();
    }

    /** First and last logical line under the visible part of the editor. */
    private int[] visibleLines(String text) {
        Point2D top = areaText.sceneToLocal(root.localToScene(0, 0));
        Point2D bottom = areaText.sceneToLocal(root.localToScene(0, root.getHeight()));
        if (top == null || bottom == null) return null;
        int firstChar = text.isEmpty() ? 0 : areaText.hitTest(new Point2D(0, top.getY())).getCharIndex();
        int lastChar = text.isEmpty() ? 0 : areaText.hitTest(new Point2D(0, bottom.getY())).getCharIndex();
        return new int[]{lineOf(firstChar), lineOf(lastChar)};
    }

    private int lineOf(int charIndex) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= charIndex) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    private static int[] lineStarts(String text) {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        int[] starts = new int[count];
        int n = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') starts[n++] = i + 1;
        }
        return starts;
    }

    /** Top of the caret at {@code index} in the text node's coordinates: where that line begins. */
    private double lineTop(int index) {
        PathElement[] shape = areaText.caretShape(index, true);
        return shape.length > 0 && shape[0] instanceof MoveTo move ? move.getY() : 0;
    }

    /**
     * Distance from the top of a text-area row to where a TextFlow must start so its glyphs land on the
     * text area's: the text area spreads each row's extra pitch evenly above and below the glyphs.
     */
    private double glyphOffset() {
        double scale = area.getScene() != null && area.getScene().getWindow() != null
                ? area.getScene().getWindow().getRenderScaleY() : 1;
        // Rounded to a device pixel so the colored text is as sharp as the text area's own would be.
        return Math.round(lineSpacing / 2 * scale) / scale;
    }

    private boolean caretWanted() {
        return area.isFocused() && area.getSelection().getLength() == 0;
    }

    /** Caret and selection come straight from the text node, so they match the text area exactly. */
    private void refreshShapes() {
        if (areaText == null) return;
        String text = area.getText() == null ? "" : area.getText();
        int length = text.length();
        int at = Math.min(area.getCaretPosition(), length);
        caret.getElements().setAll(areaText.caretShape(at, true));

        IndexRange range = area.getSelection();
        if (range.getLength() > 0) {
            selection.getElements().setAll(areaText.rangeShape(Math.min(range.getStart(), length),
                    Math.min(range.getEnd(), length)));
        } else {
            selection.getElements().clear();
        }
        caret.setVisible(caretWanted());
        blink.playFromStart();
    }

    /**
     * For tests: the largest misalignment, in pixels, between the text area and the drawn lines, comparing
     * the row box each one reports for the first character of every line on screen.
     */
    double driftForTests() {
        double worst = 0;
        for (int i = 0; i < renderedFlows.size(); i++) {
            TextFlow flow = renderedFlows.get(i);
            int start = lineStarts[renderedLines.get(i)];
            PathElement[] areaBox = areaText.rangeShape(start, start + 1);
            PathElement[] flowBox = flow.rangeShape(0, 1);
            if (areaBox.length == 0 || flowBox.length == 0) continue;
            MoveTo a = (MoveTo) areaBox[0];
            MoveTo f = (MoveTo) flowBox[0];
            double dy = (flow.getLayoutY() + f.getY()) - (a.getY() + glyphOffset());
            double dx = (flow.getLayoutX() + f.getX()) - a.getX();
            worst = Math.max(worst, Math.max(Math.abs(dx), Math.abs(dy)));
        }
        return worst;
    }

    int renderedLineCountForTests() {
        return renderedFlows.size();
    }
}

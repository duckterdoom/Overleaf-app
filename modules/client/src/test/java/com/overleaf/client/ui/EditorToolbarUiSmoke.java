package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.LocalBackend;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Explicit real-control smoke plus a real TeX build of snippets inserted by the toolbar. */
public final class EditorToolbarUiSmoke {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(home)) throw new IllegalArgumentException("Use a new home");
        System.setProperty("overleaf.home", home.toString());
        LocalBackend backend = new LocalBackend(home);
        backend.register("Toolbar Test", "toolbar@example.test", "test-password", true).join();
        var project = backend.create("Toolbar project");
        Path root = Path.of(project.folder());
        Files.writeString(
                root.resolve("references.bib"),
                "@book{knuth1984,author={Donald Knuth},title={The TeXbook},year={1984},publisher={Test}}\n@book{lamport1994,author={Leslie Lamport},title={LaTeX},year={1994},publisher={Test}}\n");
        Files.createDirectories(root.resolve("figures"));
        var pixels =
                new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(pixels, "png", root.resolve("figures/test plot.png").toFile());
        new AppSettings(backend).set(AppSettings.LANGUAGE, "vi");
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Platform.startup(() -> ready.complete(null));
        ready.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        OverleafFxApp app = UiSmoke.fx(OverleafFxApp::new);
        Stage stage =
                UiSmoke.fx(() -> {
                    Stage s = new Stage();
                    s.setOpacity(0);
                    app.start(s);
                    UiSmoke.invoke(app, "openProject", project);
                    return s;
                });
        TextArea editor = UiSmoke.field(app, "codeEditor");
        Map<String, String> snippets = new HashMap<>();
        try {
            UiSmoke.fx(() -> {
                List<String> expected =
                        List.of(
                                "editor-italic",
                                "editor-insert-math",
                                "editor-insert-link",
                                "editor-add-comment",
                                "editor-insert-reference",
                                "editor-insert-citation",
                                "editor-insert-figure",
                                "editor-insert-table",
                                "editor-insert-list",
                                "editor-decrease-indent",
                                "editor-increase-indent");
                HBox bar = (HBox) stage.getScene().lookup("#editor-italic").getParent();
                var actual =
                        bar.getChildren().stream()
                                .map(n -> n.getId())
                                .filter(Objects::nonNull)
                                .filter(expected::contains)
                                .toList();
                require(expected.equals(actual), "Toolbar order differs");
                text(editor, "a+b");
                editor.selectAll();
                click(stage, "editor-insert-math");
                menu(0);
                require(editor.getText().equals("$a+b$"), "Inline math failed");
                text(editor, "c+d");
                editor.selectAll();
                click(stage, "editor-insert-math");
                menu(1);
                require(editor.getText().equals("\\[\nc+d\n\\]"), "Display math failed");
                snippets.put("math", editor.getText());
                text(editor, "first\nsecond");
                editor.selectAll();
                click(stage, "editor-add-comment");
                require(editor.getText().equals("% first\n% second"), "Comment failed");
                click(stage, "editor-add-comment");
                require(editor.getText().equals("first\nsecond"), "Uncomment failed");
                text(editor, "one\ntwo\nthree");
                editor.selectRange(0, 8);
                click(stage, "editor-increase-indent");
                require(editor.getText().equals("  one\n  two\nthree"), "Indent selection failed");
                click(stage, "editor-decrease-indent");
                require(editor.getText().equals("one\ntwo\nthree"), "Decrease failed");
                text(editor, "Alpha\nBeta");
                editor.selectAll();
                click(stage, "editor-insert-list");
                menu(0);
                require(editor.getText().contains("\\begin{itemize}"), "List failed");
                snippets.put("list", editor.getText());
                text(editor, "Alpha\nBeta");
                editor.selectAll();
                click(stage, "editor-insert-list");
                menu(1);
                require(editor.getText().contains("\\begin{enumerate}"), "Numbered list failed");
                text(editor, "L & R");
                editor.selectAll();
                click(stage, "editor-insert-link");
                field(stage, "insert-link-url").setText("https://example.com/a_b?x=1&y=2");
                confirm(stage);
                require(editor.getText().contains("L \\& R"), "Link failed");
                snippets.put("link", editor.getText());
                text(editor, "\\label{sec:pending}\n");
                click(stage, "editor-insert-reference");
                var labels = combo(stage, "insert-reference-label");
                require(labels.getItems().contains("sec:pending"), "Unsaved labels not read");
                labels.getEditor().setText("sec:pending");
                confirm(stage);
                require(editor.getText().endsWith("\\ref{sec:pending}"), "Reference failed");
                snippets.put("ref", "\\ref{sec:pending}");
                text(editor, "");
                click(stage, "editor-insert-citation");
                var keys = combo(stage, "insert-citation-key");
                require(
                        keys.getItems().containsAll(List.of("knuth1984", "lamport1994")),
                        "Bib keys not read");
                keys.getEditor().setText("knuth1984, lamport1994");
                confirm(stage);
                require(
                        editor.getText().equals("\\cite{knuth1984,lamport1994}"),
                        "Citation failed");
                snippets.put("cite", editor.getText());
                text(editor, "");
                click(stage, "editor-insert-figure");
                combo(stage, "insert-figure-path").getEditor().setText("figures/test plot.png");
                field(stage, "insert-figure-caption").setText("Chart & data");
                confirm(stage);
                require(editor.getText().contains("\\includegraphics"), "Figure failed");
                snippets.put("figure", editor.getText());
                text(editor, "");
                click(stage, "editor-insert-table");
                field(stage, "insert-table-caption").setText("Demo & test");
                screenshot(stage, home.resolve("insert-table.png"));
                confirm(stage);
                require(editor.getText().contains("\\begin{tabular}"), "Table failed");
                snippets.put("table", editor.getText());
                text(editor, "unchanged");
                click(stage, "editor-insert-link");
                field(stage, "insert-link-url").setText("https://example.com");
                editor.appendText(" remote edit");
                confirm(stage);
                require(
                        stage.getScene().lookup("#editor-insert-confirm") != null,
                        "Stale dialog was accepted");
                require(
                        editor.getText().equals("unchanged remote edit"),
                        "Stale selection corrupted text");
                cancel(stage);
                editor.setEditable(false);
                for (String id : expected)
                    require(
                            ((Button) stage.getScene().lookup("#" + id)).isDisabled(),
                            "Read-only action enabled: " + id);
                editor.setEditable(true);
                screenshot(stage, home.resolve("editor-toolbar.png"));
                return null;
            });
            String source =
                    "\\documentclass{article}\n\\usepackage[utf8]{inputenc}\n\\usepackage[T5]{fontenc}\n\\usepackage{lmodern,graphicx,hyperref,amsmath}\n\\begin{document}\n\\section{Intro}\\label{sec:pending}\n"
                            + snippets.get("math")
                            + "\n"
                            + snippets.get("link")
                            + "\n Reference "
                            + snippets.get("ref")
                            + "\n Cite "
                            + snippets.get("cite")
                            + "\n"
                            + snippets.get("list")
                            + "\n"
                            + snippets.get("figure")
                            + "\n"
                            + snippets.get("table")
                            + "\n\\bibliographystyle{plain}\n\\bibliography{references}\n\\end{document}\n";
            UiSmoke.fx(() -> {
                text(editor, source);
                UiSmoke.invoke(app, "triggerRecompile");
                return null;
            });
            waitFor(() ->
                    UiSmoke.fx(() -> !((Button) UiSmoke.field(app, "btnRecompile")).isDisabled()));
            PdfPreviewPane preview = UiSmoke.field(app, "pdfPreviewPane");
            require(
                    UiSmoke.fx(preview::canExport),
                    "Inserted snippets did not compile; inspect test project build/main.log");
            System.out.println(
                    "EDITOR_TOOLBAR_UI_SMOKE_OK order, math/link/comment/ref/cite/figure/table/list/indent, read-only/stale guards, real PDF compile");
        } finally {
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "cleanupOnExit");
                stage.hide();
                return null;
            });
            Platform.exit();
        }
    }

    private static void text(TextArea editor, String value) {
        editor.setText(value);
        editor.positionCaret(value.length());
    }

    private static void click(Stage stage, String id) {
        layout(stage);
        ((Button) stage.getScene().lookup("#" + id)).fire();
        layout(stage);
    }

    private static void layout(Stage stage) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private static TextField field(Stage stage, String id) {
        layout(stage);
        return (TextField) stage.getScene().lookup("#" + id);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> combo(Stage stage, String id) {
        layout(stage);
        return (ComboBox<String>) stage.getScene().lookup("#" + id);
    }

    private static void confirm(Stage stage) {
        click(stage, "editor-insert-confirm");
    }

    private static void cancel(Stage stage) {
        stage.getScene().getRoot().lookupAll(".modal-cancel").stream()
                .map(n -> (Button) n)
                .findFirst()
                .orElseThrow()
                .fire();
    }

    private static void menu(int index) {
        ContextMenu menu =
                (ContextMenu)
                        Window.getWindows().stream()
                                .filter(w -> w instanceof ContextMenu && w.isShowing())
                                .findFirst()
                                .orElseThrow();
        menu.hide();
        menu.getItems().get(index).fire();
    }

    private static void require(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }

    private static void waitFor(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(150);
        while (!condition.call()) {
            if (System.nanoTime() > end) throw new AssertionError("Timed out");
            Thread.sleep(80);
        }
    }

    private static void screenshot(Stage stage, Path file) throws Exception {
        layout(stage);
        var image = stage.getScene().snapshot(null);
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] pixels = new int[w * h];
        image.getPixelReader()
                .getPixels(
                        0,
                        0,
                        w,
                        h,
                        javafx.scene.image.PixelFormat.getIntArgbInstance(),
                        pixels,
                        0,
                        w);
        var out =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        javax.imageio.ImageIO.write(out, "png", file.toFile());
    }
}

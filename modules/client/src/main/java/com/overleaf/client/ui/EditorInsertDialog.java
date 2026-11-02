package com.overleaf.client.ui;

import com.overleaf.client.workspace.ProjectFileManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Captures the insertion range and refuses to apply to a changed/read-only document. */
final class EditorInsertDialog {
    enum Kind {
        LINK,
        REFERENCE,
        CITATION,
        FIGURE,
        TABLE
    }

    private final TextArea editor;
    private final Path root;
    private final String currentFile, original;
    private final IndexRange selection;
    private final BooleanSupplier sameDocument;
    private final boolean vi;
    private final UiModal modal;

    EditorInsertDialog(
            StackPane host,
            TextArea editor,
            Path root,
            String currentFile,
            BooleanSupplier sameDocument,
            boolean vi,
            Kind kind) {
        this.editor = editor;
        this.root = root;
        this.currentFile = currentFile;
        this.sameDocument = sameDocument;
        this.vi = vi;
        original = editor.getText();
        selection = editor.getSelection();
        String title =
                switch (kind) {
                    case LINK -> t("Insert link", "Chèn liên kết");
                    case REFERENCE -> t("Insert cross reference", "Chèn tham chiếu chéo");
                    case CITATION -> t("Insert citation", "Chèn trích dẫn");
                    case FIGURE -> t("Insert figure", "Chèn hình ảnh");
                    case TABLE -> t("Insert table", "Chèn bảng");
                };
        modal =
                UiModal.create(host, UiSupport.PLUS, UiModal.Tone.NEUTRAL, title, null)
                        .localized(vi)
                        .width(540);
        switch (kind) {
            case LINK -> link();
            case REFERENCE -> reference();
            case CITATION -> citation();
            case FIGURE -> figure();
            case TABLE -> table();
        }
    }

    void show() {
        modal.show();
    }

    private String t(String en, String vn) {
        return vi ? vn : en;
    }

    private TextField field(String en, String vn, String value, String id) {
        TextField field = modal.field(t(en, vn), value, "");
        field.setId(id);
        return field;
    }

    private ComboBox<String> choice(
            String en, String vn, Collection<String> values, String value, String id) {
        ComboBox<String> box = new ComboBox<>();
        box.setEditable(true);
        box.getItems().setAll(values);
        box.setValue(value);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setId(id);
        Label label = new Label(t(en, vn));
        label.getStyleClass().add("field-label");
        modal.node(new VBox(6, label, box));
        return box;
    }

    private String value(ComboBox<String> box) {
        return box.getEditor().getText().trim();
    }

    private void actions(java.util.function.Supplier<String> snippet) {
        Button insert =
                modal.actions(
                        null,
                        t("Cancel", "Hủy"),
                        t("Insert", "Chèn"),
                        UiModal.Tone.NEUTRAL,
                        () -> {
                            if (!editor.isEditable()
                                    || !sameDocument.getAsBoolean()
                                    || !original.equals(editor.getText())) {
                                modal.error(
                                        null,
                                        t(
                                                "The document changed or is read-only. Close and reopen this dialog.",
                                                "Tài liệu đã thay đổi hoặc đang chỉ đọc. Hãy đóng và mở lại hộp thoại."));
                                return false;
                            }
                            try {
                                String text = snippet.get();
                                editor.replaceText(selection.getStart(), selection.getEnd(), text);
                                editor.positionCaret(selection.getStart() + text.length());
                                editor.requestFocus();
                                return true;
                            } catch (IllegalArgumentException failure) {
                                modal.error(
                                        null,
                                        t(
                                                "Check the fields: use a valid URL/key or project image, numeric width 0.05–1, and a caption if a label is supplied.",
                                                "Kiểm tra các ô: URL/khóa hợp lệ hoặc ảnh trong dự án, độ rộng là số 0.05–1; nếu có nhãn cần có chú thích."));
                                return false;
                            }
                        });
        insert.setId("editor-insert-confirm");
    }

    private void link() {
        TextField address = field("URL", "URL", "https://", "insert-link-url");
        TextField text =
                field("Link text", "Chữ hiển thị", editor.getSelectedText(), "insert-link-text");
        modal.text(
                t(
                        "Requires \\usepackage{hyperref} in the main document preamble.",
                        "Cần \\usepackage{hyperref} trong phần khai báo của tài liệu chính."));
        actions(() -> EditorInsertions.link(address.getText(), text.getText()));
    }

    private void reference() {
        var labels = labels();
        ComboBox<String> target =
                choice(
                        "Label",
                        "Nhãn",
                        labels,
                        labels.isEmpty() ? "sec:intro" : labels.first(),
                        "insert-reference-label");
        ComboBox<String> command =
                choice(
                        "Reference command",
                        "Lệnh tham chiếu",
                        List.of("ref", "eqref", "pageref", "autoref"),
                        "ref",
                        "insert-reference-command");
        command.setEditable(false);
        modal.text(
                t(
                        "Labels are read from project .tex files, including unsaved text in the editor. eqref needs amsmath; autoref needs hyperref.",
                        "Nhãn được đọc từ các tệp .tex và nội dung chưa lưu trong editor. eqref cần amsmath; autoref cần hyperref."));
        actions(() -> EditorInsertions.reference(command.getValue(), value(target)));
    }

    private void citation() {
        var keys =
                scan(
                        "(?im)@(?!(?:comment|string|preamble)\\b)[a-z]+\\s*[\\{(]\\s*([^,\\s]+)\\s*,",
                        ".bib");
        ComboBox<String> key =
                choice(
                        "Bibliography key(s)",
                        "Khóa tài liệu tham khảo",
                        keys,
                        keys.isEmpty() ? "" : keys.first(),
                        "insert-citation-key");
        modal.text(
                t(
                        "Choose a key from .bib files or type keys separated by commas. Configure bibliography/BibLaTeX in main.tex; this inserts \\cite only.",
                        "Chọn khóa từ tệp .bib hoặc nhập nhiều khóa cách nhau bởi dấu phẩy. Cần cấu hình bibliography/BibLaTeX trong main.tex; nút này chèn \\cite."));
        actions(() -> EditorInsertions.citation(value(key)));
    }

    private void figure() {
        List<String> images = List.of();
        if (root != null)
            try {
                images =
                        new ProjectFileManager(root.toString())
                                .listFiles(".png", ".jpg", ".jpeg", ".pdf");
            } catch (IOException ignored) {
            }
        ComboBox<String> image =
                choice(
                        "Image in this project",
                        "Ảnh trong dự án",
                        images,
                        images.isEmpty() ? "" : images.getFirst(),
                        "insert-figure-path");
        TextField width =
                field(
                        "Width (fraction of line width)",
                        "Độ rộng (tỷ lệ chiều rộng dòng)",
                        "0.8",
                        "insert-figure-width");
        TextField caption =
                field(
                        "Caption",
                        "Chú thích",
                        t("Figure caption", "Chú thích hình"),
                        "insert-figure-caption");
        TextField label =
                field(
                        "Label (optional)",
                        "Nhãn (tùy chọn)",
                        uniqueLabel("fig:figure"),
                        "insert-figure-label");
        modal.text(
                t(
                        "Upload PNG/JPG/PDF to the workspace first. Requires \\usepackage{graphicx} in the main preamble.",
                        "Tải PNG/JPG/PDF vào workspace trước. Cần \\usepackage{graphicx} trong phần khai báo của tài liệu chính."));
        actions(() -> {
            String path = value(image);
            if (root == null || !path.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|pdf)$"))
                throw new IllegalArgumentException("path");
            Path file = root.resolve(path).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file))
                throw new IllegalArgumentException("path");
            return EditorInsertions.figure(
                    path, width.getText(), caption.getText(), label.getText());
        });
    }

    private void table() {
        Spinner<Integer> rows = new Spinner<>(1, 50, 2), columns = new Spinner<>(1, 20, 3);
        rows.setId("insert-table-rows");
        columns.setId("insert-table-columns");
        modal.node(
                new VBox(
                        6,
                        new Label(
                                t(
                                        "Data rows (plus a header row)",
                                        "Số dòng dữ liệu (thêm một dòng tiêu đề)")),
                        rows,
                        new Label(t("Columns", "Số cột")),
                        columns));
        ComboBox<String> align =
                choice(
                        "Alignment: l / c / r",
                        "Căn lề: l / c / r",
                        List.of("l", "c", "r"),
                        "c",
                        "insert-table-align");
        align.setEditable(false);
        TextField caption =
                field(
                        "Caption",
                        "Chú thích",
                        t("Table caption", "Chú thích bảng"),
                        "insert-table-caption");
        TextField label =
                field(
                        "Label (optional)",
                        "Nhãn (tùy chọn)",
                        uniqueLabel("tab:table"),
                        "insert-table-label");
        actions(() ->
                EditorInsertions.table(
                        rows.getValue(),
                        columns.getValue(),
                        align.getValue(),
                        caption.getText(),
                        label.getText(),
                        vi));
    }

    private SortedSet<String> labels() {
        return scan("\\\\label\\s*\\{([^}]+)}", ".tex");
    }

    private String uniqueLabel(String base) {
        var used = labels();
        String result = base;
        for (int i = 2; used.contains(result); i++) result = base + i;
        return result;
    }

    private SortedSet<String> scan(String regex, String extension) {
        TreeSet<String> result = new TreeSet<>();
        Pattern pattern = Pattern.compile(regex);
        if (root == null) return result;
        try {
            for (String file : new ProjectFileManager(root.toString()).listFiles(extension)) {
                String text;
                if (file.equals(currentFile)) text = original;
                else
                    try (var in = Files.newInputStream(root.resolve(file))) {
                        text = new String(in.readNBytes(2 * 1024 * 1024), StandardCharsets.UTF_8);
                    }
                StringBuilder visible = new StringBuilder();
                for (String line : text.split("\\R")) {
                    int cut = line.length();
                    for (int i = 0; i < line.length(); i++)
                        if (line.charAt(i) == '%') {
                            int slashes = 0;
                            for (int j = i - 1; j >= 0 && line.charAt(j) == '\\'; j--) slashes++;
                            if (slashes % 2 == 0) {
                                cut = i;
                                break;
                            }
                        }
                    visible.append(line, 0, cut).append('\n');
                }
                var matcher = pattern.matcher(visible);
                while (matcher.find()) {
                    try {
                        result.add(EditorInsertions.key(matcher.group(1)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return result;
    }
}

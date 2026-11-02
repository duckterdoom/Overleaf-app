package com.overleaf.client.ui;

import com.overleaf.client.workspace.ProjectHistory;
import com.overleaf.client.workspace.ProjectHistory.Revision;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

/** Read-only three-column revision browser; restoration is an explicit application action. */
final class HistoryView extends BorderPane implements AutoCloseable {
    private final ProjectHistory history;
    private final StackPane host;
    private final boolean vi;
    private final BooleanSupplier canRestore;
    private final BiConsumer<Revision, String> restoreAction;
    private final ListView<String> files = new ListView<>();
    private final ListView<Revision> timeline = new ListView<>();
    private final TextArea saved = textArea(), current = textArea();
    private final Label heading = new Label(), changes = new Label(), error = new Label();
    private final ToggleButton all = new ToggleButton(), labels = new ToggleButton();
    private final Button restore = new Button(), label = new Button();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "history-reader");
                t.setDaemon(true);
                return t;
            });
    private List<Revision> revisions = List.of();
    private String selectedText;
    private long generation;
    private boolean closed;

    HistoryView(
            StackPane host,
            Path root,
            String projectName,
            boolean vi,
            Runnable back,
            BooleanSupplier canRestore,
            BiConsumer<Revision, String> restoreAction) {
        this.host = host;
        this.vi = vi;
        this.history = new ProjectHistory(root);
        this.canRestore = canRestore;
        this.restoreAction = restoreAction;
        getStyleClass().add("history-view");
        setId("history-view");
        Button returnButton = new Button(t("← Back to editor", "← Quay lại editor"));
        returnButton.setId("history-back");
        returnButton.getStyleClass().add("btn-secondary");
        returnButton.setOnAction(e -> back.run());
        Button refresh = new Button(t("Refresh", "Làm mới"));
        refresh.setOnAction(e -> refresh());
        HBox top = new HBox(12, returnButton, new Label(projectName), UiSupport.spacer(), refresh);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));
        setTop(top);
        files.setId("history-files");
        files.setPrefWidth(200);
        files.setMinWidth(130);
        files.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> filter());
        timeline.setId("history-timeline");
        timeline.setPrefWidth(300);
        timeline.setMinWidth(200);
        timeline.setPlaceholder(
                new Label(
                        t(
                                "No saved revisions yet. Save a file to begin recording history.",
                                "Chưa có phiên bản. Lưu tệp để bắt đầu ghi lịch sử.")));
        DateTimeFormatter format =
                DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm:ss")
                        .withZone(ZoneId.systemDefault());
        timeline.setCellFactory(list ->
                new ListCell<>() {
                    @Override
                    protected void updateItem(Revision revision, boolean empty) {
                        super.updateItem(revision, empty);
                        setText(null);
                        setGraphic(null);
                        if (!empty && revision != null) {
                            Label date = new Label(format.format(revision.time()));
                            date.setStyle("-fx-font-weight: bold;");
                            Label meta =
                                    new Label(
                                            revision.file()
                                                    + "\n"
                                                    + revision.author()
                                                    + " · "
                                                    + reason(revision.reason())
                                                    + (revision.label().isBlank()
                                                            ? ""
                                                            : "\n◆ " + revision.label()));
                            meta.setWrapText(true);
                            VBox card = new VBox(5, date, meta);
                            card.setPadding(new Insets(8));
                            setGraphic(card);
                        }
                    }
                });
        timeline.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> load(b));
        all.setText(t("All history", "Tất cả lịch sử"));
        labels.setText(t("Labels", "Nhãn"));
        all.setId("history-all");
        labels.setId("history-labels");
        ToggleGroup group = new ToggleGroup();
        all.setToggleGroup(group);
        labels.setToggleGroup(group);
        all.setSelected(true);
        group.selectedToggleProperty()
                .addListener((o, a, b) -> {
                    if (b == null) all.setSelected(true);
                    filter();
                });
        HBox filters = new HBox(6, all, labels);
        filters.setPadding(new Insets(8));
        VBox right = new VBox(filters, timeline);
        VBox.setVgrow(timeline, Priority.ALWAYS);
        label.setText(t("Add / edit label", "Thêm / sửa nhãn"));
        label.setId("history-label");
        label.setOnAction(e -> editLabel());
        restore.setText(t("Restore this file", "Khôi phục tệp này"));
        restore.setId("history-restore");
        restore.setOnAction(e -> confirmRestore());
        restore.getStyleClass().add("btn-primary");
        heading.setWrapText(true);
        changes.setWrapText(true);
        heading.setId("history-heading");
        changes.setId("history-changes");
        HBox actions = new HBox(8, label, restore);
        actions.setPadding(new Insets(8));
        TabPane tabs = new TabPane();
        Tab revisionTab = new Tab(t("Revision", "Phiên bản"), numbered(saved));
        SplitPane compare =
                new SplitPane(
                        new VBox(
                                new Label(t("Saved revision", "Phiên bản đã lưu")),
                                numbered(savedCopy)),
                        new VBox(new Label(t("Current file", "Tệp hiện tại")), numbered(current)));
        for (var item : compare.getItems())
            if (item instanceof VBox box)
                VBox.setVgrow(box.getChildren().getLast(), Priority.ALWAYS);
        tabs.getTabs()
                .addAll(
                        revisionTab,
                        new Tab(t("Compare with current", "So sánh với hiện tại"), compare));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox center = new VBox(8, heading, changes, tabs, actions);
        center.setPadding(new Insets(8));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        SplitPane body = new SplitPane(files, center, right);
        body.setDividerPositions(.16, .73);
        setCenter(body);
        error.setWrapText(true);
        error.setStyle("-fx-text-fill: #b42318; -fx-padding: 8;");
        setBottom(error);
        refresh();
    }

    private final TextArea savedCopy = textArea();

    private static TextArea textArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setFont(Font.font("Consolas", 13));
        return area;
    }

    private HBox numbered(TextArea area) {
        LineNumberGutter gutter = new LineNumberGutter(area);
        gutter.setFont(Font.font("Consolas", 13));
        HBox box = new HBox(gutter, area);
        HBox.setHgrow(area, Priority.ALWAYS);
        return box;
    }

    private String t(String en, String vn) {
        return vi ? vn : en;
    }

    private String reason(String reason) {
        if (!vi) return reason;
        return switch (reason) {
            case "Edited" -> "Đã sửa";
            case "Before edit" -> "Trước khi sửa";
            case "Opened" -> "Bản khi mở tệp";
            case "Restored" -> "Đã khôi phục";
            case "External edit" -> "Thay đổi từ bên ngoài";
            default -> reason;
        };
    }

    private void refresh() {
        long ticket = ++generation;
        Task<List<Revision>> task =
                new Task<>() {
                    @Override
                    protected List<Revision> call() throws Exception {
                        return history.revisions();
                    }
                };
        task.setOnSucceeded(e -> {
            if (closed || ticket != generation) return;
            revisions = task.getValue();
            String selected = files.getSelectionModel().getSelectedItem();
            List<String> names = new ArrayList<>();
            names.add(t("All files", "Tất cả tệp"));
            revisions.stream().map(Revision::file).distinct().sorted().forEach(names::add);
            files.getItems().setAll(names);
            files.getSelectionModel()
                    .select(
                            selected != null && names.contains(selected)
                                    ? selected
                                    : names.getFirst());
            filter();
        });
        task.setOnFailed(e -> {
            if (!closed && ticket == generation)
                error.setText(
                        t("Could not read history: ", "Không đọc được lịch sử: ")
                                + task.getException().getMessage());
        });
        worker.execute(task);
    }

    private void filter() {
        String file =
                files.getSelectionModel().getSelectedIndex() > 0
                        ? files.getSelectionModel().getSelectedItem()
                        : null;
        timeline.getItems()
                .setAll(
                        revisions.stream()
                                .filter(r -> file == null || r.file().equals(file))
                                .filter(r -> !labels.isSelected() || !r.label().isBlank())
                                .toList());
        if (!timeline.getItems().isEmpty()) timeline.getSelectionModel().selectFirst();
        else load(null);
    }

    private void load(Revision revision) {
        long ticket = ++generation;
        selectedText = null;
        restore.setDisable(true);
        label.setDisable(revision == null);
        error.setText("");
        if (revision == null) {
            saved.clear();
            savedCopy.clear();
            current.clear();
            heading.setText(t("Select a revision", "Chọn một phiên bản"));
            changes.setText("");
            return;
        }
        Task<String[]> task =
                new Task<>() {
                    @Override
                    protected String[] call() throws Exception {
                        String text = history.read(revision);
                        Path file = history.filePath(revision.file());
                        String now = "";
                        if (Files.isRegularFile(file)) {
                            if (Files.size(file) > 2 * 1024 * 1024)
                                throw new java.io.IOException("Current file exceeds 2 MiB");
                            now = Files.readString(file, StandardCharsets.UTF_8);
                        }
                        return new String[] {text, now};
                    }
                };
        task.setOnSucceeded(e -> {
            if (closed || ticket != generation) return;
            String[] values = task.getValue();
            selectedText = values[0];
            saved.setText(values[0]);
            savedCopy.setText(values[0]);
            current.setText(values[1]);
            heading.setText(
                    revision.file()
                            + " · "
                            + revision.time()
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                            + " · "
                            + revision.author());
            changes.setText(
                    values[0].equals(values[1])
                            ? t(
                                    "No changes compared with the current file.",
                                    "Không khác nội dung hiện tại.")
                            : t(
                                    "This revision differs from the current file. Compare both panes; restoring only replaces this file.",
                                    "Phiên bản khác tệp hiện tại. So sánh hai bên; khôi phục chỉ thay thế tệp này."));
            int prefix = 0;
            while (prefix < Math.min(values[0].length(), values[1].length())
                    && values[0].charAt(prefix) == values[1].charAt(prefix)) prefix++;
            int suffix = 0;
            while (suffix < Math.min(values[0].length(), values[1].length()) - prefix
                    && values[0].charAt(values[0].length() - suffix - 1)
                            == values[1].charAt(values[1].length() - suffix - 1)) suffix++;
            savedCopy.selectRange(prefix, values[0].length() - suffix);
            current.selectRange(prefix, values[1].length() - suffix);
            restore.setDisable(!canRestore.getAsBoolean());
        });
        task.setOnFailed(e -> {
            if (!closed && ticket == generation)
                error.setText(
                        t("Could not load revision: ", "Không tải được phiên bản: ")
                                + task.getException().getMessage());
        });
        worker.execute(task);
    }

    private void editLabel() {
        Revision revision = timeline.getSelectionModel().getSelectedItem();
        if (revision == null) return;
        UiModal modal =
                UiModal.create(
                                host,
                                UiSupport.EDIT,
                                UiModal.Tone.NEUTRAL,
                                t("Revision label", "Nhãn phiên bản"),
                                null)
                        .localized(vi);
        TextField name =
                modal.field(
                        t("Label (up to 120 characters)", "Nhãn (tối đa 120 ký tự)"),
                        revision.label(),
                        "");
        name.setId("history-label-name");
        modal.actions(
                null,
                t("Cancel", "Hủy"),
                t("Save", "Lưu"),
                UiModal.Tone.NEUTRAL,
                () -> {
                    try {
                        history.label(revision, name.getText());
                        refresh();
                        return true;
                    } catch (Exception failure) {
                        modal.error(name, failure.getMessage());
                        return false;
                    }
                });
        modal.show();
    }

    private void confirmRestore() {
        Revision revision = timeline.getSelectionModel().getSelectedItem();
        String text = selectedText;
        if (revision == null || text == null || !canRestore.getAsBoolean()) return;
        UiModal modal =
                UiModal.create(
                                host,
                                UiSupport.UNDO,
                                UiModal.Tone.NEUTRAL,
                                t("Restore this file?", "Khôi phục tệp này?"),
                                revision.file())
                        .localized(vi);
        modal.text(
                t(
                        "The current file is saved in history before it is replaced. Other files are unchanged.",
                        "Tệp hiện tại được lưu vào lịch sử trước khi thay thế. Các tệp khác giữ nguyên."));
        Button confirm =
                modal.actions(
                        null,
                        t("Cancel", "Hủy"),
                        t("Restore", "Khôi phục"),
                        UiModal.Tone.NEUTRAL,
                        () -> {
                            if (!canRestore.getAsBoolean()) {
                                modal.error(
                                        null,
                                        t(
                                                "Leave the collaboration room before restoring.",
                                                "Rời phòng cộng tác trước khi khôi phục."));
                                return false;
                            }
                            restoreAction.accept(revision, text);
                            return true;
                        });
        confirm.setId("history-confirm-restore");
        modal.show();
    }

    @Override
    public void close() {
        closed = true;
        generation++;
        worker.shutdownNow();
    }
}

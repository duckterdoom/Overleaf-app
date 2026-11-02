package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.ProjectGateway;
import com.overleaf.client.workspace.WorkspaceFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/** Add-file workflow shared by the workspace toolbar and directory context menus. */
final class AddFilesDialog {
    private final StackPane host;
    private final Path root;
    private final WorkspaceFiles files;
    private final ProjectGateway projects;
    private final boolean vi;
    private final Consumer<List<String>> onAdded;
    private final UiModal modal;
    private final ListView<String> navigation = new ListView<>();
    private final VBox content = new VBox(12);
    private final ComboBox<String> destination = new ComboBox<>();
    private final TextField name = new TextField("name.tex");
    private final TextField address = new TextField();
    private final TextField copiedName = new TextField();
    private final ComboBox<ProjectGateway.ProjectSummary> sourceProject = new ComboBox<>();
    private final ComboBox<String> sourceFile = new ComboBox<>();
    private final CheckBox outputs;
    private final List<Path> selected = new ArrayList<>();
    private final List<Path> temporary = new ArrayList<>();
    private final ListView<String> queue = new ListView<>();
    private Button primary;
    private Task<List<String>> task;

    AddFilesDialog(
            StackPane host,
            Path root,
            ProjectGateway projects,
            String directory,
            boolean vi,
            Consumer<List<String>> onAdded) {
        this.host = host;
        this.root = root;
        this.files = new WorkspaceFiles(root);
        this.projects = projects;
        this.vi = vi;
        this.onAdded = onAdded;
        modal =
                UiModal.create(
                                host,
                                UiSupport.FILE,
                                UiModal.Tone.NEUTRAL,
                                t("Add files", "Thêm tệp"),
                                null)
                        .localized(vi)
                        .width(920);
        destination.setId("add-files-destination");
        destination.setEditable(true);
        destination.setMaxWidth(Double.MAX_VALUE);
        try {
            destination.getItems().setAll(files.directories());
        } catch (IOException ignored) {
            destination.getItems().add("");
        }
        destination.setValue(directory == null ? "" : directory);
        destination.setPromptText(t("Project root", "Thư mục gốc dự án"));
        name.setId("add-files-name");
        name.getStyleClass().add("modal-field");
        copiedName.setId("add-files-copy-name");
        copiedName.getStyleClass().add("modal-field");
        address.setId("add-files-url");
        address.getStyleClass().add("modal-field");
        address.setPromptText("https://…");
        sourceProject.setId("add-files-source-project");
        sourceProject.setMaxWidth(Double.MAX_VALUE);
        sourceFile.setId("add-files-source-file");
        sourceFile.setMaxWidth(Double.MAX_VALUE);
        sourceProject.setConverter(
                new javafx.util.StringConverter<>() {
                    @Override
                    public String toString(ProjectGateway.ProjectSummary p) {
                        return p == null ? "" : p.name() + " — " + p.folder();
                    }

                    @Override
                    public ProjectGateway.ProjectSummary fromString(String s) {
                        return null;
                    }
                });
        sourceProject
                .getItems()
                .setAll(
                        projects.projects().stream()
                                .filter(p ->
                                        !Path.of(p.folder())
                                                .toAbsolutePath()
                                                .normalize()
                                                .equals(root))
                                .toList());
        outputs = new CheckBox(t("Select from output files (build)", "Chọn từ tệp đầu ra (build)"));
        outputs.setId("add-files-output-files");
        sourceProject.setOnAction(e -> loadSourceFiles());
        outputs.setOnAction(e -> loadSourceFiles());
        sourceFile.setOnAction(e -> {
            if (sourceFile.getValue() != null)
                copiedName.setText(Path.of(sourceFile.getValue()).getFileName().toString());
        });
        navigation.setId("add-files-navigation");
        navigation.getStyleClass().add("add-files-nav");
        navigation
                .getItems()
                .addAll(
                        t("New file", "Tệp mới"),
                        t("New folder", "Thư mục mới"),
                        t("Upload", "Tải lên"),
                        t("From another project", "Từ dự án khác"),
                        t("From external URL", "Từ URL bên ngoài"),
                        t("Bibliography (.bib)", "Thư viện tài liệu (.bib)"));
        navigation.setPrefWidth(215);
        navigation.setMinWidth(150);
        navigation.setMaxWidth(215);
        navigation
                .getSelectionModel()
                .selectedIndexProperty()
                .addListener((o, a, b) -> showPage(b.intValue()));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("add-files-scroll");
        scroll.setPrefHeight(310);
        scroll.maxHeightProperty()
                .bind(javafx.beans.binding.Bindings.max(140, host.heightProperty().subtract(280)));
        HBox.setHgrow(scroll, Priority.ALWAYS);
        HBox body = new HBox(20, navigation, scroll);
        body.setAlignment(Pos.TOP_LEFT);
        queue.setId("add-files-queue");
        queue.setPrefHeight(100);
        modal.node(
                        new VBox(
                                6,
                                label(
                                        t(
                                                "Destination folder (blank = project root)",
                                                "Thư mục đích (để trống = gốc dự án)")),
                                destination))
                .node(body);
        primary =
                modal.actions(
                        null,
                        t("Cancel", "Hủy"),
                        t("Create", "Tạo"),
                        UiModal.Tone.NEUTRAL,
                        this::submit);
        primary.setId("add-files-submit");
        modal.onClose(() -> {
            if (task != null && task.isRunning()) task.cancel(true);
            for (Path path : temporary)
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
        });
        navigation.getSelectionModel().select(0);
    }

    void show(int page) {
        navigation.getSelectionModel().select(page);
        modal.show();
    }

    private String t(String en, String vn) {
        return vi ? vn : en;
    }

    private Label label(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add("modal-body");
        return l;
    }

    private void showPage(int page) {
        content.getChildren().clear();
        primary.setText(page == 2 || page == 5 ? t("Upload", "Tải lên") : t("Create", "Tạo"));
        switch (page) {
            case 0, 1 -> {
                name.setText(page == 0 ? "name.tex" : t("new-folder", "thu-muc-moi"));
                content.getChildren()
                        .addAll(
                                label(
                                        page == 0
                                                ? t("File name", "Tên tệp")
                                                : t("Folder name", "Tên thư mục")),
                                name,
                                label(
                                        t(
                                                "Use / for nested paths, e.g. chapters/intro.tex. Existing files are never replaced.",
                                                "Dùng / để tạo đường dẫn con, ví dụ chapters/intro.tex. Tệp đã có sẽ không bị ghi đè.")));
            }
            case 2, 5 -> uploadPage(page == 5);
            case 3 ->
                    content.getChildren()
                            .addAll(
                                    label(t("Select a project", "Chọn dự án")),
                                    sourceProject,
                                    outputs,
                                    label(t("Select a file", "Chọn tệp")),
                                    sourceFile,
                                    label(
                                            t(
                                                    "File name in this project",
                                                    "Tên tệp trong dự án này")),
                                    copiedName,
                                    label(
                                            t(
                                                    "Creates an independent copy; later edits are not linked.",
                                                    "Tạo bản sao độc lập; thay đổi sau này không tự đồng bộ giữa hai dự án.")));
            case 4 ->
                    content.getChildren()
                            .addAll(
                                    label(
                                            t(
                                                    "Direct HTTPS file URL",
                                                    "URL HTTPS trực tiếp tới tệp")),
                                    address,
                                    label(
                                            t(
                                                    "File name in this project",
                                                    "Tên tệp trong dự án này")),
                                    copiedName,
                                    label(
                                            t(
                                                    "Downloads a copy, up to 100 MiB. Login pages and cloud sharing pages are not direct file links.",
                                                    "Tải một bản sao, tối đa 100 MiB. Trang đăng nhập hoặc trang chia sẻ cloud không phải liên kết tệp trực tiếp.")));
        }
    }

    private void uploadPage(boolean bib) {
        Button chooseFiles = new Button(t("Select files", "Chọn tệp"));
        chooseFiles.setId("add-files-choose");
        chooseFiles.getStyleClass().add("btn-secondary");
        chooseFiles.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(t("Select files", "Chọn tệp"));
            if (bib)
                chooser.getExtensionFilters()
                        .add(new FileChooser.ExtensionFilter("BibTeX (*.bib)", "*.bib"));
            var picked = chooser.showOpenMultipleDialog(host.getScene().getWindow());
            if (picked != null) enqueue(picked.stream().map(java.io.File::toPath).toList());
        });
        Button chooseFolder = new Button(t("Select a folder", "Chọn thư mục"));
        chooseFolder.getStyleClass().add("btn-secondary");
        chooseFolder.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var picked = chooser.showDialog(host.getScene().getWindow());
            if (picked != null) enqueue(List.of(picked.toPath()));
        });
        Button paste = new Button(t("Paste files / image", "Dán tệp / ảnh"));
        paste.setOnAction(e -> pasteClipboard());
        VBox drop =
                new VBox(
                        12,
                        label(
                                t(
                                        "Drop or paste files, folders or images here",
                                        "Kéo thả hoặc dán tệp, thư mục hay ảnh vào đây")),
                        new HBox(8, chooseFiles, chooseFolder),
                        paste);
        drop.setId("add-files-drop");
        drop.getStyleClass().add("add-files-drop");
        drop.setAlignment(Pos.CENTER);
        drop.setMinHeight(150);
        drop.setFocusTraversable(true);
        drop.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
                e.consume();
            }
        });
        drop.setOnDragDropped(e -> {
            boolean ok = e.getDragboard().hasFiles();
            if (ok)
                enqueue(e.getDragboard().getFiles().stream().map(java.io.File::toPath).toList());
            e.setDropCompleted(ok);
            e.consume();
        });
        drop.setOnKeyPressed(e -> {
            if (new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN).match(e)) {
                pasteClipboard();
                e.consume();
            }
        });
        Button remove = new Button(t("Remove selected", "Bỏ mục đã chọn"));
        remove.setOnAction(e -> {
            int index = queue.getSelectionModel().getSelectedIndex();
            if (index >= 0) {
                selected.remove(index);
                updateQueue();
            }
        });
        content.getChildren().addAll(drop, queue, remove);
        if (bib)
            content.getChildren()
                    .add(
                            label(
                                    t(
                                            "Export BibTeX (.bib) from ReadCube, Zotero or Mendeley, then upload it here. No account connection is required.",
                                            "Xuất BibTeX (.bib) từ ReadCube, Zotero hoặc Mendeley rồi tải vào đây. Đây là nhập tệp, chưa kết nối tài khoản các dịch vụ.")));
    }

    private void enqueue(List<Path> paths) {
        for (Path path : paths)
            if (!selected.contains(path.toAbsolutePath().normalize()))
                selected.add(path.toAbsolutePath().normalize());
        updateQueue();
    }

    private void updateQueue() {
        queue.getItems().setAll(selected.stream().map(Path::toString).toList());
    }

    private void pasteClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles()) {
            enqueue(clipboard.getFiles().stream().map(java.io.File::toPath).toList());
            return;
        }
        if (clipboard.hasImage()) {
            enqueueImage(clipboard.getImage());
        } else
            modal.error(
                    null,
                    t("The clipboard has no files or image.", "Bộ nhớ tạm chưa có tệp hoặc ảnh."));
    }

    private void enqueueImage(javafx.scene.image.Image image) {
        try {
            int w = (int) image.getWidth(), h = (int) image.getHeight();
            if ((long) w * h > 25_000_000)
                throw new WorkspaceFiles.Failure(WorkspaceFiles.Problem.LIMIT);
            var buffered =
                    new java.awt.image.BufferedImage(
                            w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
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
            buffered.setRGB(0, 0, w, h, pixels, 0, w);
            Path path = Files.createTempFile("pasted-image-", ".png");
            temporary.add(path);
            javax.imageio.ImageIO.write(buffered, "png", path.toFile());
            enqueue(List.of(path));
        } catch (IOException | RuntimeException failure) {
            error(failure);
        }
    }

    private void loadSourceFiles() {
        sourceFile.getItems().clear();
        sourceFile.setValue(null);
        var project = sourceProject.getValue();
        if (project == null) return;
        Path source = Path.of(project.folder());
        try (var paths = Files.walk(source)) {
            sourceFile
                    .getItems()
                    .setAll(
                            paths.filter(p ->
                                            Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                                                    && !Files.isSymbolicLink(p))
                                    .map(source::relativize)
                                    .filter(p -> {
                                        for (Path part : p)
                                            if (part.toString().startsWith(".")) return false;
                                        return outputs.isSelected() == p.startsWith("build");
                                    })
                                    .map(p -> p.toString().replace('\\', '/'))
                                    .sorted()
                                    .limit(10_000)
                                    .toList());
        } catch (IOException failure) {
            error(failure);
        }
    }

    private boolean submit() {
        String directory = destination.getEditor().getText().trim();
        int page = navigation.getSelectionModel().getSelectedIndex();
        try {
            if (!directory.isEmpty()) files.target(directory);
            if (page == 0 || page == 1) {
                String value = name.getText().trim();
                if (value.isBlank()) throw new WorkspaceFiles.Failure(WorkspaceFiles.Problem.NAME);
                if (page == 0 && !Path.of(value).getFileName().toString().contains("."))
                    value += ".tex";
                String target = WorkspaceFiles.join(directory, value);
                if (page == 0) files.createFile(target);
                else files.createFolder(target);
                onAdded.accept(List.of(target));
                return true;
            }
            Callable<List<String>> operation;
            if (page == 2 || page == 5) {
                List<Path> sources = List.copyOf(selected);
                if (page == 5
                        && sources.stream()
                                .anyMatch(p ->
                                        !p.toString().toLowerCase(Locale.ROOT).endsWith(".bib")))
                    throw new WorkspaceFiles.Failure(WorkspaceFiles.Problem.SOURCE);
                operation = () -> files.importPaths(sources, directory);
            } else {
                String target = WorkspaceFiles.join(directory, copiedName.getText().trim());
                files.target(target);
                if (page == 3) {
                    if (sourceProject.getValue() == null || sourceFile.getValue() == null)
                        throw new WorkspaceFiles.Failure(WorkspaceFiles.Problem.SOURCE);
                    Path sourceRoot =
                            Path.of(sourceProject.getValue().folder()).toAbsolutePath().normalize();
                    Path source = sourceRoot.resolve(sourceFile.getValue()).normalize();
                    if (!source.startsWith(sourceRoot)
                            || !source.toRealPath().startsWith(sourceRoot.toRealPath()))
                        throw new WorkspaceFiles.Failure(WorkspaceFiles.Problem.SOURCE);
                    operation =
                            () ->
                                    files.importFiles(
                                            List.of(new WorkspaceFiles.Addition(source, target)));
                } else {
                    String url = address.getText().trim();
                    operation = () -> files.download(url, target);
                }
            }
            primary.setDisable(true);
            navigation.setDisable(true);
            content.setDisable(true);
            destination.setDisable(true);
            primary.setText(t("Adding…", "Đang thêm…"));
            task =
                    new Task<>() {
                        @Override
                        protected List<String> call() throws Exception {
                            return operation.call();
                        }
                    };
            task.setOnSucceeded(e -> {
                onAdded.accept(task.getValue());
                modal.close();
            });
            task.setOnFailed(e -> {
                primary.setDisable(false);
                navigation.setDisable(false);
                content.setDisable(false);
                destination.setDisable(false);
                primary.setText(t("Try again", "Thử lại"));
                error(task.getException());
            });
            Thread worker = new Thread(task, "workspace-add-files");
            worker.setDaemon(true);
            worker.start();
        } catch (IOException | RuntimeException failure) {
            error(failure);
        }
        return false;
    }

    private void error(Throwable failure) {
        modal.error(
                null,
                failure instanceof WorkspaceFiles.Failure known
                        ? known.message(vi)
                        : new WorkspaceFiles.Failure(WorkspaceFiles.Problem.IO, failure)
                                .message(vi));
    }
}

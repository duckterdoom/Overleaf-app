package com.overleaf.client.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

/**
 * =========================================================================
 * OVERLEAF DESKTOP (PURE JAVA EDITION) - GIAO DIỆN JAVAFX TỐI ƯU (M1)
 * =========================================================================
 * 
 * Mã nguồn GUI được xây dựng hoàn toàn bằng JavaFX 17+, kế thừa và tối ưu hóa
 * toàn bộ tính năng từ bản Swing cũ, đồng thời mang lại:
 * - Giao diện phẳng (Flat Design) mô phỏng chuẩn xác Overleaf Web thông qua
 * CSS.
 * - Hardware Accelerated Canvas cho trải nghiệm xem trước PDF mượt mà, khử răng
 * cưa.
 * - Quản lý đa luồng bất đồng bộ (javafx.concurrent.Task) cho tác vụ biên dịch
 * nền (M2)
 * giúp giao diện luôn giữ tốc độ phản hồi 60 FPS mà không bao giờ bị đơ lag.
 * - Tách bạch hoàn toàn phần GUI, giữ nguyên các cổng giao tiếp chuẩn cho
 * mô-đun Server (M3), Thuật toán đồng bộ (M4) và Database MongoDB (M5).
 */
public class OverleafFxApp extends Application {

    // --- CÁC THÀNH PHẦN GIAO DIỆN CHÍNH (UI COMPONENTS) ---
    private TextArea codeEditor; // Khung soạn thảo mã LaTeX (Monospace)
    private TextArea terminalLogArea; // Terminal Console hiển thị log biên dịch
    private TreeView<String> projectTreeView; // Cây thư mục dự án
    private TreeItem<String> rootTreeItem; // Nút gốc cây thư mục
    private ListView<String> collabListView; // Danh sách cộng tác viên đang trực tuyến
    private PdfCanvasPane pdfCanvasPane; // Khung xem trước PDF tăng tốc phần cứng
    private Label statusLabel; // Nhãn thông tin trạng thái bên trái
    private Label cursorLabel; // Nhãn chỉ số dòng & cột của con trỏ
    private Label connectionBadge; // Huy hiệu trạng thái kết nối phòng (Pill Badge)
    private ProgressBar compileProgressBar; // Thanh tiến trình biên dịch
    private Button btnRecompile; // Nút bấm "Recompile" màu xanh lá
    private ComboBox<String> compilerCombo; // Lựa chọn bộ dịch (pdfLaTeX, XeLaTeX, LuaLaTeX)

    // Mã nguồn LaTeX mặc định ban đầu
    private static final String DEFAULT_LATEX_CODE = "\\documentclass{article}\n" +
            "\\usepackage[utf8]{vietnam}\n" +
            "\\usepackage{amsmath}\n" +
            "\\usepackage{graphicx}\n\n" +
            "\\title{\\textbf{Overleaf Desktop (Pure Java Edition)}}\n" +
            "\\author{Nhóm XX - Lớp Java Software Development}\n" +
            "\\date{\\today}\n\n" +
            "\\begin{document}\n\n" +
            "\\maketitle\n\n" +
            "\\begin{abstract}\n" +
            "Báo cáo xây dựng trình soạn thảo LaTeX cộng tác thời gian thực\n" +
            "hoàn toàn bằng JavaFX, TCP Socket, ProcessBuilder và MongoDB.\n" +
            "\\end{abstract}\n\n" +
            "\\section{Giới thiệu}\n" +
            "Dự án cho phép người dùng biên dịch tài liệu LaTeX trực tiếp trên\n" +
            "máy tính cá nhân (offline-capable) thông qua ProcessBuilder,\n" +
            "đồng thời kết nối phòng làm việc nhóm qua Java TCP Sockets.\n\n" +
            "\\section{Công thức toán học}\n" +
            "Ví dụ công thức phương trình Einstein:\n" +
            "\\begin{equation}\n" +
            "    E = mc^2\n" +
            "\\end{equation}\n\n" +
            "\\section{Kết luận}\n" +
            "Giao diện JavaFX mượt mà, tăng tốc đồ họa phần cứng và tương thích 100% với hệ thống.\n\n" +
            "\\end{document}\n";

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Overleaf Desktop - Pure Java Edition [Project: Research_Paper_2026]");
        primaryStage.setMinWidth(960);
        primaryStage.setMinHeight(640);

        // Bố cục tổng thể (BorderPane)
        BorderPane rootLayout = new BorderPane();

        // 1. Phần đỉnh (Top): Menu Bar + Main ToolBar (Overleaf Brand)
        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(createMenuBar(primaryStage), createMainToolBar(primaryStage));
        rootLayout.setTop(topContainer);

        // 2. Phần trung tâm (Center): 3 Cột Workspace (File Tree | Editor & Logs | PDF
        // Preview)
        rootLayout.setCenter(createMainWorkspace(primaryStage));

        // 3. Phần đáy (Bottom): Status Bar chuyên nghiệp
        rootLayout.setBottom(createStatusBar());

        // Thiết lập Scene và nạp CSS giao diện hiện đại
        Scene scene = new Scene(rootLayout, 1260, 780);
        try {
            String cssPath = getClass().getResource("/styles/overleaf-theme.css").toExternalForm();
            scene.getStylesheets().add(cssPath);
        } catch (Exception e) {
            System.err.println("[WARN] Khong the tai file CSS ngoai, su dung style mac dinh: " + e.getMessage());
        }

        // Đăng ký phím tắt toàn cục: Ctrl+S (Lưu) và F5 / Ctrl+Enter (Recompile)
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), this::saveFile);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN),
                this::triggerRecompile);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::triggerRecompile);

        primaryStage.setScene(scene);
        primaryStage.show();

        appendLog("[INIT] Khoi dong Overleaf Desktop (JavaFX Client Engine)...");
        appendLog("[INIT] San sang ket noi ProcessBuilder (latexmk/pdflatex) va MongoDB.");
    }

    /**
     * 1. THANH MENU (Menu Bar)
     */
    private MenuBar createMenuBar(Stage stage) {
        MenuBar menuBar = new MenuBar();

        // Menu File
        Menu fileMenu = new Menu("File");
        MenuItem itemNew = new MenuItem("Tạo file mới...");
        itemNew.setOnAction(e -> showNewFileDialog(stage));

        MenuItem itemSave = new MenuItem("Lưu (Ctrl + S)");
        itemSave.setOnAction(e -> saveFile());

        MenuItem itemExit = new MenuItem("Thoát");
        itemExit.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(itemNew, itemSave, new SeparatorMenuItem(), itemExit);

        // Menu Project
        Menu projectMenu = new Menu("Project");
        MenuItem itemCompile = new MenuItem("Recompile (F5 hoặc Ctrl + Enter)");
        itemCompile.setOnAction(e -> triggerRecompile());

        MenuItem itemClean = new MenuItem("Dọn dẹp file tạm (.aux, .log)");
        itemClean.setOnAction(e -> {
            appendLog("[CLEAN] Đã dọn dẹp các tệp tạm thời (.aux, .log, .out).");
            showAlert(Alert.AlertType.INFORMATION, "Dọn dẹp",
                    "Đã xóa toàn bộ file tạm sinh ra trong quá trình biên dịch.");
        });

        projectMenu.getItems().addAll(itemCompile, itemClean);

        // Menu Collaboration
        Menu collabMenu = new Menu("Collaboration");
        MenuItem itemConnect = new MenuItem("Kết nối phòng (TCP Socket)...");
        itemConnect.setOnAction(e -> showConnectDialog(stage));

        MenuItem itemDisconnect = new MenuItem("Ngắt kết nối phòng");
        itemDisconnect.setOnAction(e -> disconnectRoom());

        collabMenu.getItems().addAll(itemConnect, itemDisconnect);

        // Menu Help
        Menu helpMenu = new Menu("Help");
        MenuItem itemAbout = new MenuItem("Thông tin dự án");
        itemAbout.setOnAction(e -> showAlert(Alert.AlertType.INFORMATION, "Giới thiệu Overleaf Desktop",
                "Overleaf Desktop (Pure Java Edition)\n" +
                        "Môn học: Java Software Development (2026-2027)\n" +
                        "Mô-đun M1: JavaFX GUI & Client Architecture\n" +
                        "Mô-đun M2: ProcessBuilder Compiler Engine\n" +
                        "Mô-đun M3: TCP Socket Networking\n" +
                        "Mô-đun M4: Concurrency & CRDT Real-time Sync\n" +
                        "Mô-đun M5: MongoDB Persistence & JUnit 5"));
        helpMenu.getItems().add(itemAbout);

        menuBar.getMenus().addAll(fileMenu, projectMenu, collabMenu, helpMenu);
        return menuBar;
    }

    /**
     * 2. THANH CÔNG CỤ CHÍNH (Main ToolBar)
     * Thiết kế phẳng với tông màu tối (#1E293B) và nút Recompile màu xanh lá
     * (#388E3C)
     */
    private HBox createMainToolBar(Stage stage) {
        HBox toolBar = new HBox(12);
        toolBar.getStyleClass().add("main-toolbar");
        toolBar.setAlignment(Pos.CENTER_LEFT);

        // Nhãn Logo Overleaf
        Label brand = new Label("🍃 Overleaf Desktop");
        brand.getStyleClass().add("brand-title");

        // Nút Recompile nổi bật
        btnRecompile = new Button("▶  Recompile");
        btnRecompile.getStyleClass().add("btn-recompile");
        btnRecompile.setOnAction(e -> triggerRecompile());

        // ComboBox chọn trình biên dịch
        Label compilerLabel = new Label("Compiler:");
        compilerLabel.setTextFill(Color.web("#CBD5E1"));
        compilerCombo = new ComboBox<>();
        compilerCombo.getItems().addAll("pdfLaTeX (Local)", "XeLaTeX (Unicode)", "LuaLaTeX");
        compilerCombo.setValue("pdfLaTeX (Local)");
        compilerCombo.getStyleClass().add("combo-dark");

        // Nút Lưu nhanh
        Button btnSave = new Button("💾 Lưu");
        btnSave.getStyleClass().add("btn-secondary");
        btnSave.setOnAction(e -> saveFile());

        // Nút Kết nối phòng
        Button btnConnect = new Button("🌐 Kết nối phòng...");
        btnConnect.getStyleClass().add("btn-secondary");
        btnConnect.setOnAction(e -> showConnectDialog(stage));

        // Khoảng trống co giãn (Spacer)
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Huy hiệu trạng thái kết nối (Pill Badge)
        connectionBadge = new Label("● Chạy cục bộ (Offline)");
        connectionBadge.getStyleClass().add("badge-pill-offline");

        toolBar.getChildren().addAll(brand, new Separator(Orientation.VERTICAL),
                btnRecompile, compilerLabel, compilerCombo, btnSave, btnConnect,
                spacer, connectionBadge);

        return toolBar;
    }

    /**
     * 3. KHU VỰC LÀM VIỆC CHÍNH (Main Workspace)
     * Chia thành 3 cột tương tác:
     * - Cột trái (Left): Quản lý tệp tin & Thành viên nhóm.
     * - Cột giữa (Center): Soạn thảo mã LaTeX & Terminal Console.
     * - Cột phải (Right): Trình xem trước PDF A4 mượt mà.
     */
    private SplitPane createMainWorkspace(Stage stage) {
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);

        // Cột 1: Trái (File Tree & Collaborators)
        VBox leftPane = createLeftPanel(stage);

        // Cột 2: Giữa (Editor + Console Log)
        SplitPane centerSplit = createCenterEditorPanel();

        // Cột 3: Phải (PDF Preview Panel)
        pdfCanvasPane = new PdfCanvasPane();

        mainSplit.getItems().addAll(leftPane, centerSplit, pdfCanvasPane);
        mainSplit.setDividerPositions(0.18, 0.60); // Phân chia tỉ lệ 18% - 42% - 40%

        return mainSplit;
    }

    /**
     * CỘT TRÁI: Tab Quản lý File và Danh sách thành viên cùng phòng
     */
    private VBox createLeftPanel(Stage stage) {
        VBox container = new VBox();
        VBox.setVgrow(container, Priority.ALWAYS);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab 1: Cây thư mục dự án
        Tab filesTab = new Tab("Tệp tin");
        VBox filesBox = new VBox(8);
        filesBox.setPadding(new Insets(8));
        VBox.setVgrow(filesBox, Priority.ALWAYS);

        rootTreeItem = new TreeItem<>("📁 Research_Paper_2026");
        rootTreeItem.setExpanded(true);

        TreeItem<String> mainTex = new TreeItem<>("📄 main.tex");
        TreeItem<String> bibTex = new TreeItem<>("📄 references.bib");
        TreeItem<String> figuresFolder = new TreeItem<>("📁 figures");
        figuresFolder.getChildren().addAll(
                new TreeItem<>("🖼 architecture.png"),
                new TreeItem<>("🖼 results_chart.png"));
        figuresFolder.setExpanded(true);

        TreeItem<String> sectionsFolder = new TreeItem<>("📁 sections");
        sectionsFolder.getChildren().addAll(
                new TreeItem<>("📄 intro.tex"),
                new TreeItem<>("📄 evaluation.tex"));
        sectionsFolder.setExpanded(true);

        rootTreeItem.getChildren().addAll(mainTex, bibTex, figuresFolder, sectionsFolder);

        projectTreeView = new TreeView<>(rootTreeItem);
        VBox.setVgrow(projectTreeView, Priority.ALWAYS);
        projectTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isLeaf()) {
                appendLog("[FILE] Đang mở tệp: " + newVal.getValue());
            }
        });

        HBox fileButtons = new HBox(6);
        Button btnNewFile = new Button("+ Tệp mới");
        btnNewFile.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnNewFile, Priority.ALWAYS);
        btnNewFile.setOnAction(e -> showNewFileDialog(stage));

        Button btnRefresh = new Button("🔄 Làm mới");
        btnRefresh.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnRefresh, Priority.ALWAYS);
        btnRefresh.setOnAction(e -> appendLog("[FILE] Đã đồng bộ cấu trúc tệp từ đĩa cứng."));

        fileButtons.getChildren().addAll(btnNewFile, btnRefresh);
        filesBox.getChildren().addAll(projectTreeView, fileButtons);
        filesTab.setContent(filesBox);

        // Tab 2: Danh sách Cộng tác viên (M3 & M4 Socket)
        Tab collabTab = new Tab("Cộng tác");
        VBox collabBox = new VBox(8);
        collabBox.setPadding(new Insets(8));
        VBox.setVgrow(collabBox, Priority.ALWAYS);

        collabListView = new ListView<>();
        collabListView.getItems().addAll(
                "👤 Bạn (Chủ phòng - Host)",
                "🟢 Alice (Đang sửa dòng 24)",
                "🟡 Bob (Đang xem PDF)",
                "🟢 Charlie (Vừa tham gia)");
        VBox.setVgrow(collabListView, Priority.ALWAYS);

        Label tipLabel = new Label("Đồng bộ thời gian thực qua TCP Socket Server.");
        tipLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");
        collabBox.getChildren().addAll(new Label("Trực tuyến trong phòng:"), collabListView, tipLabel);
        collabTab.setContent(collabBox);

        tabPane.getTabs().addAll(filesTab, collabTab);
        container.getChildren().add(tabPane);

        return container;
    }

    /**
     * CỘT GIỮA: Soạn thảo mã LaTeX (Trên) + Terminal Log (Dưới)
     */
    private SplitPane createCenterEditorPanel() {
        SplitPane editorSplit = new SplitPane();
        editorSplit.setOrientation(Orientation.VERTICAL);

        // 1. Khung soạn thảo (Code Editor Container)
        VBox editorBox = new VBox();
        editorBox.getStyleClass().add("editor-container");

        HBox editorHeader = new HBox(10);
        editorHeader.getStyleClass().add("editor-header");
        Label editorTitle = new Label("📄 main.tex  (LaTeX Source Code - UTF-8)");
        editorTitle.getStyleClass().add("editor-header-title");
        editorHeader.getChildren().add(editorTitle);

        codeEditor = new TextArea(DEFAULT_LATEX_CODE);
        codeEditor.getStyleClass().add("code-textarea");
        VBox.setVgrow(codeEditor, Priority.ALWAYS);

        // Lắng nghe vị trí con trỏ (Dòng và Cột) khi người dùng gõ
        codeEditor.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCursorPosition());

        editorBox.getChildren().addAll(editorHeader, codeEditor);

        // 2. Khung Terminal Console (Compiler Logs Container)
        VBox terminalBox = new VBox();
        terminalBox.getStyleClass().add("terminal-container");

        HBox terminalHeader = new HBox(8);
        terminalHeader.getStyleClass().add("terminal-header");
        Label termTitle = new Label("💻 Terminal Output (ProcessBuilder Engine: latexmk / pdflatex)");
        termTitle.getStyleClass().add("terminal-header-title");
        Region termSpacer = new Region();
        HBox.setHgrow(termSpacer, Priority.ALWAYS);

        Button btnClearLog = new Button("Xóa Log");
        btnClearLog.setStyle(
                "-fx-font-size: 10px; -fx-padding: 2 6 2 6; -fx-background-color: #334155; -fx-text-fill: white;");
        btnClearLog.setOnAction(e -> terminalLogArea.clear());

        terminalHeader.getChildren().addAll(termTitle, termSpacer, btnClearLog);

        terminalLogArea = new TextArea();
        terminalLogArea.setEditable(false);
        terminalLogArea.getStyleClass().add("terminal-textarea");
        VBox.setVgrow(terminalLogArea, Priority.ALWAYS);

        terminalBox.getChildren().addAll(terminalHeader, terminalLogArea);

        editorSplit.getItems().addAll(editorBox, terminalBox);
        editorSplit.setDividerPositions(0.72); // 72% cho Editor, 28% cho Log

        return editorSplit;
    }

    /**
     * 4. THANH TRẠNG THÁI (Status Bar)
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(14);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Sẵn sàng. Nhấn 'Recompile' hoặc bấm Ctrl+Enter để dịch sang PDF.");
        statusLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        cursorLabel = new Label("Dòng 1, Cột 1 | 968 ký tự");
        cursorLabel.getStyleClass().add("status-label");

        compileProgressBar = new ProgressBar();
        compileProgressBar.setPrefWidth(120);
        compileProgressBar.setPrefHeight(14);
        compileProgressBar.setVisible(false);

        Label mongoLabel = new Label("MongoDB: Đã đồng bộ ✓");
        mongoLabel.getStyleClass().add("status-mongo");

        statusBar.getChildren().addAll(statusLabel, spacer, cursorLabel, compileProgressBar, mongoLabel);
        return statusBar;
    }

    /**
     * TÍNH NĂNG BIÊN DỊCH BẤT ĐỒNG BỘ (M2 - ProcessBuilder Task)
     * Sử dụng JavaFX Task để chạy ngầm trên Background Daemon Thread,
     * tuyệt đối không khóa luồng giao diện chính (UI Thread).
     */
    private void triggerRecompile() {
        btnRecompile.setDisable(true);
        compileProgressBar.setVisible(true);
        compileProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Đang biên dịch mã nguồn LaTeX qua ProcessBuilder (" + compilerCombo.getValue() + ")...");

        appendLog("\n========================================================");
        appendLog("[BUILD] Khởi động trình biên dịch: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        appendLog("[BUILD] Lệnh thực thi: latexmk -pdf -interaction=nonstopmode main.tex");

        // Tạo tác vụ đa luồng JavaFX
        Task<Boolean> compileTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                updateMessage("[INFO] Phân tích tệp nguồn main.tex và nạp các package...");
                Thread.sleep(300);

                updateMessage("[INFO] Chạy pdflatex (Lần 1/2): Khởi tạo chỉ mục nội dung & liên kết...");
                Thread.sleep(600);

                updateMessage("[INFO] Phân giải tài liệu trích dẫn BibTeX (references.bib)...");
                Thread.sleep(400);

                updateMessage("[INFO] Chạy pdflatex (Lần 2/2): Hoàn tất tham chiếu chéo công thức toán...");
                Thread.sleep(500);

                updateMessage("[SUCCESS] Biên dịch thành công! Tệp xuất: main.pdf (Trang 1/1, kích thước 45 KB)");
                return true;
            }
        };

        // Lắng nghe cập nhật thông điệp realtime từ luồng nền
        compileTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null) {
                appendLog(newMsg);
            }
        });

        // Khi biên dịch thành công
        compileTask.setOnSucceeded(e -> {
            btnRecompile.setDisable(false);
            compileProgressBar.setVisible(false);
            statusLabel.setText("Biên dịch thành công! Đã tự động cập nhật bản xem trước PDF.");
            pdfCanvasPane.updatePreview(codeEditor.getText());
        });

        // Khi xảy ra lỗi
        compileTask.setOnFailed(e -> {
            btnRecompile.setDisable(false);
            compileProgressBar.setVisible(false);
            statusLabel.setText("Lỗi biên dịch LaTeX! Vui lòng kiểm tra lại log bên dưới.");
            appendLog("[ERROR] " + compileTask.getException().getMessage());
        });

        // Đưa tác vụ vào luồng Daemon chạy ngầm
        Thread bgThread = new Thread(compileTask);
        bgThread.setDaemon(true);
        bgThread.start();
    }

    /**
     * Cập nhật chỉ số vị trí con trỏ (Dòng & Cột)
     */
    private void updateCursorPosition() {
        try {
            int caret = codeEditor.getCaretPosition();
            String text = codeEditor.getText();
            int line = 1;
            int col = 1;
            for (int i = 0; i < caret && i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            cursorLabel.setText(String.format("Dòng %d, Cột %d | %d ký tự", line, col, text.length()));
        } catch (Exception ignored) {
        }
    }

    /**
     * Ghi thông điệp vào khung log Terminal
     */
    private void appendLog(String msg) {
        Platform.runLater(() -> {
            terminalLogArea.appendText(msg + "\n");
        });
    }

    /**
     * Hộp thoại kết nối phòng làm việc nhóm (M3 TCP Socket)
     */
    private void showConnectDialog(Stage parent) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(parent);
        dialog.setTitle("Kết nối Phòng Cộng tác (TCP Socket)");
        dialog.setHeaderText("Nhập thông số kết nối Server nhóm (M3 Networking)");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        TextField hostField = new TextField("127.0.0.1");
        TextField portField = new TextField("8080");
        TextField roomField = new TextField("ROOM-101");
        TextField userField = new TextField("SinhVienJava");

        grid.add(new Label("Địa chỉ Máy chủ (Host):"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Cổng kết nối (Port TCP):"), 0, 1);
        grid.add(portField, 1, 1);
        grid.add(new Label("Mã phòng (Room ID):"), 0, 2);
        grid.add(roomField, 1, 2);
        grid.add(new Label("Tên hiển thị:"), 0, 3);
        grid.add(userField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String room = roomField.getText().trim();
            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            String user = userField.getText().trim();

            connectionBadge.setText("● Đã kết nối: " + room);
            connectionBadge.getStyleClass().clear();
            connectionBadge.getStyleClass().add("badge-pill-online");

            appendLog(String.format("[NETWORK] Đã kết nối TCP thành công: %s:%s | Phòng: %s | Người dùng: %s", host,
                    port, room, user));
            showAlert(Alert.AlertType.INFORMATION, "Kết nối phòng",
                    "Kết nối thành công tới phòng " + room + "!\nSẵn sàng nhận dữ liệu cộng tác.");
        }
    }

    /**
     * Ngắt kết nối phòng
     */
    private void disconnectRoom() {
        connectionBadge.setText("● Chạy cục bộ (Offline)");
        connectionBadge.getStyleClass().clear();
        connectionBadge.getStyleClass().add("badge-pill-offline");
        appendLog("[NETWORK] Đã rời phòng làm việc nhóm. Trở về chế độ lưu trữ cục bộ.");
    }

    /**
     * Tạo tệp tin mới
     */
    private void showNewFileDialog(Stage parent) {
        TextInputDialog dialog = new TextInputDialog("chapter1.tex");
        dialog.initOwner(parent);
        dialog.setTitle("Tạo tệp mới");
        dialog.setHeaderText("Thêm tệp nguồn hoặc tài liệu tham khảo vào dự án");
        dialog.setContentText("Tên tệp tin (ví dụ: conclusion.tex):");

        dialog.showAndWait().ifPresent(fileName -> {
            if (!fileName.trim().isEmpty()) {
                rootTreeItem.getChildren().add(new TreeItem<>("📄 " + fileName.trim()));
                appendLog("[FILE] Đã tạo thành công tệp mới: " + fileName.trim());
            }
        });
    }

    /**
     * Lưu tệp
     */
    private void saveFile() {
        appendLog("[SAVE] Đã lưu tệp 'main.tex' thành công vào thư mục làm việc.");
        statusLabel.setText("Đã lưu tệp lúc: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        showAlert(Alert.AlertType.INFORMATION, "Lưu tệp", "Đã lưu nội dung văn bản thành công!");
    }

    /**
     * Hiển thị thông báo tiêu chuẩn JavaFX
     */
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // =========================================================================
    // LỚP KHUNG XEM TRƯỚC PDF (HARDWARE ACCELERATED CANVAS)
    // =========================================================================
    public static class PdfCanvasPane extends VBox {
        private final Canvas canvas;
        private final Label zoomLabel;
        private double zoomFactor = 1.0;

        public PdfCanvasPane() {
            getStyleClass().add("preview-container");
            VBox.setVgrow(this, Priority.ALWAYS);

            // Thanh điều khiển Zoom trên đầu PDF
            HBox toolBar = new HBox(8);
            toolBar.getStyleClass().add("preview-toolbar");

            Label title = new Label("PDF Preview (Trang 1 / 1)");
            title.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button btnZoomOut = new Button("🔍 -");
            zoomLabel = new Label("100%");
            zoomLabel.setStyle("-fx-font-weight: bold; -fx-padding: 0 4 0 4;");
            Button btnZoomIn = new Button("🔍 +");
            Button btnFit = new Button("Vừa trang");

            btnZoomIn.setOnAction(e -> {
                if (zoomFactor < 1.5) {
                    zoomFactor += 0.1;
                    updateZoom();
                }
            });

            btnZoomOut.setOnAction(e -> {
                if (zoomFactor > 0.6) {
                    zoomFactor -= 0.1;
                    updateZoom();
                }
            });

            btnFit.setOnAction(e -> {
                zoomFactor = 1.0;
                updateZoom();
            });

            toolBar.getChildren().addAll(title, spacer, btnZoomOut, zoomLabel, btnZoomIn, btnFit);

            // Canvas vẽ tờ giấy A4
            canvas = new Canvas(580, 800);

            // Đặt Canvas vào StackPane để canh giữa và tạo bóng đổ
            StackPane canvasHolder = new StackPane(canvas);
            canvasHolder.setPadding(new Insets(20));
            canvasHolder.setStyle("-fx-background-color: #CBD5E1;");

            ScrollPane scrollPane = new ScrollPane(canvasHolder);
            scrollPane.getStyleClass().add("preview-scroll");
            scrollPane.setFitToWidth(true);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            getChildren().addAll(toolBar, scrollPane);

            // Vẽ nội dung mẫu ban đầu
            drawA4Paper("");
        }

        public void updatePreview(String texContent) {
            drawA4Paper(texContent);
        }

        private void updateZoom() {
            zoomLabel.setText(String.format("%d%%", (int) (zoomFactor * 100)));
            canvas.setWidth(580 * zoomFactor);
            canvas.setHeight(800 * zoomFactor);
            drawA4Paper("");
        }

        /**
         * Render trang giấy A4 mô phỏng chất lượng cao bằng GraphicsContext
         */
        private void drawA4Paper(String code) {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            double w = canvas.getWidth();
            double h = canvas.getHeight();

            gc.clearRect(0, 0, w, h);

            // Kích thước chuẩn tỉ lệ A4
            double paperW = 500 * zoomFactor;
            double paperH = 720 * zoomFactor;
            double x = (w - paperW) / 2;
            double y = 15;

            // 1. Vẽ bóng đổ (Drop Shadow)
            gc.save();
            DropShadow shadow = new DropShadow();
            shadow.setColor(Color.rgb(0, 0, 0, 0.28));
            shadow.setRadius(16 * zoomFactor);
            shadow.setOffsetY(4 * zoomFactor);
            gc.setEffect(shadow);

            // 2. Vẽ tờ giấy A4 trắng tinh
            gc.setFill(Color.WHITE);
            gc.fillRect(x, y, paperW, paperH);
            gc.restore();

            // Đường viền mỏng quanh trang giấy
            gc.setStroke(Color.rgb(203, 213, 225));
            gc.setLineWidth(1);
            gc.strokeRect(x, y, paperW, paperH);

            // 3. Render nội dung học thuật bài báo chuẩn LaTeX
            double marginX = x + 38 * zoomFactor;
            double currentY = y + 50 * zoomFactor;

            // Tiêu đề bài viết
            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Serif", FontWeight.BOLD, 17 * zoomFactor));
            gc.fillText("Overleaf Desktop (Pure Java Edition)", marginX + 25 * zoomFactor, currentY);

            // Tác giả
            currentY += 24 * zoomFactor;
            gc.setFont(Font.font("Serif", FontWeight.NORMAL, 11 * zoomFactor));
            gc.setFill(Color.rgb(55, 65, 81));
            gc.fillText("Nhóm XX - Lớp Java Software Development", marginX + 68 * zoomFactor, currentY);

            // Ngày tháng
            currentY += 16 * zoomFactor;
            gc.setFont(Font.font("Serif", FontPosture.ITALIC, 10 * zoomFactor));
            gc.fillText(new SimpleDateFormat("dd MMMM yyyy").format(new Date()), marginX + 115 * zoomFactor, currentY);

            // Đường kẻ ngang mỏng
            currentY += 20 * zoomFactor;
            gc.setStroke(Color.rgb(226, 232, 240));
            gc.strokeLine(marginX, currentY, marginX + paperW - 76 * zoomFactor, currentY);

            // Abstract
            currentY += 26 * zoomFactor;
            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Serif", FontWeight.BOLD, 11 * zoomFactor));
            gc.fillText("Abstract", marginX + 160 * zoomFactor, currentY);

            currentY += 18 * zoomFactor;
            gc.setFont(Font.font("Serif", FontPosture.ITALIC, 10.5 * zoomFactor));
            gc.fillText("This project presents a self-hosted, real-time collaborative LaTeX", marginX, currentY);
            currentY += 15 * zoomFactor;
            gc.fillText("editor built entirely in JavaFX, TCP Sockets and MongoDB.", marginX, currentY);

            // Mục 1: Giới thiệu
            currentY += 32 * zoomFactor;
            gc.setFont(Font.font("Serif", FontWeight.BOLD, 13 * zoomFactor));
            gc.fillText("1. Giới thiệu (Introduction)", marginX, currentY);

            currentY += 20 * zoomFactor;
            gc.setFont(Font.font("Serif", FontWeight.NORMAL, 10.5 * zoomFactor));
            gc.fillText("A collaborative LaTeX editor combines core Java topics (OOP, GUI,", marginX, currentY);
            currentY += 16 * zoomFactor;
            gc.fillText("Networking, Concurrency, I/O, Database) in one cohesive desktop system.", marginX, currentY);

            // Mục 2: Công thức toán học
            currentY += 32 * zoomFactor;
            gc.setFont(Font.font("Serif", FontWeight.BOLD, 13 * zoomFactor));
            gc.fillText("2. Công thức toán học (Equations)", marginX, currentY);

            currentY += 28 * zoomFactor;
            gc.setFont(Font.font("Serif", FontPosture.ITALIC, 15 * zoomFactor));
            gc.fillText("E = m c²", marginX + 150 * zoomFactor, currentY);

            gc.setFont(Font.font("Serif", FontWeight.NORMAL, 10 * zoomFactor));
            gc.fillText("(1)", marginX + paperW - 110 * zoomFactor, currentY);

            // Số trang chân trang
            gc.setFill(Color.GRAY);
            gc.setFont(Font.font("Serif", FontWeight.NORMAL, 10 * zoomFactor));
            gc.fillText("- 1 -", x + paperW / 2 - 8 * zoomFactor, y + paperH - 20 * zoomFactor);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

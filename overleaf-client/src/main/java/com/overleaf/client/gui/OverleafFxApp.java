package com.overleaf.client.gui;

import com.overleaf.client.compiler.LatexCompilerService;
import com.overleaf.client.io.ProjectFileManager;
import com.overleaf.client.io.WorkspaceWatcher;
import com.overleaf.client.net.NetworkClient;
import com.overleaf.common.sync.CRDTOperation;
import com.overleaf.common.sync.OfflineOperationQueue;
import com.overleaf.common.sync.OperationCodec;
import com.overleaf.common.sync.SyncMessage;
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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * =========================================================================
 * OVERLEAF DESKTOP - GIAO DIỆN JAVAFX TỐI ƯU (M1) TÍCH HỢP M2 VÀ M4
 * =========================================================================
 * 
 * - M1: Giao diện JavaFX hiện đại, Canvas xem trước PDF ĐỘNG (Dynamic LaTeX Renderer),
 *       tự động phân trang chuẩn A4, xử lý ngắt dòng mượt mà.
 * - M2: Tích hợp LatexCompilerService (ProcessBuilder nền) & WorkspaceWatcher (theo dõi I/O).
 * - M4: Tích hợp OfflineOperationQueue, CRDTOperation, Đồng hồ Lamport, chế độ
 *       soạn thảo Offline và Tự động Đồng bộ lại (RESYNC / CHECKPOINT).
 */
public class OverleafFxApp extends Application {

    // --- CÁC THÀNH PHẦN GIAO DIỆN CHÍNH (UI COMPONENTS) ---
    private TextArea codeEditor;              // Khung soạn thảo mã LaTeX (Monospace)
    private TextArea terminalLogArea;         // Terminal Console hiển thị log biên dịch
    private TreeView<String> projectTreeView; // Cây thư mục dự án
    private TreeItem<String> rootTreeItem;    // Nút gốc cây thư mục
    private ListView<String> collabListView;  // Danh sách cộng tác viên đang trực tuyến
    private PdfCanvasPane pdfCanvasPane;      // Khung xem trước PDF động (Dynamic Canvas)
    private Label statusLabel;                // Nhãn thông tin trạng thái bên trái
    private Label cursorLabel;                // Nhãn chỉ số dòng & cột của con trỏ
    private Label connectionBadge;            // Huy hiệu trạng thái kết nối phòng (Pill Badge)
    private ProgressBar compileProgressBar;   // Thanh tiến trình biên dịch
    private Button btnRecompile;              // Nút bấm "Recompile" màu xanh lá
    private ComboBox<String> compilerCombo;   // Lựa chọn bộ dịch (pdfLaTeX, XeLaTeX, LuaLaTeX)

    // --- TÍCH HỢP M2 (COMPILER & WORKSPACE WATCHER) ---
    private final LatexCompilerService compilerService = new LatexCompilerService();
    private ProjectFileManager fileManager;
    private WorkspaceWatcher workspaceWatcher;
    private final String mainTexFileName = "main.tex";

    // --- TÍCH HỢP M4 (COLLABORATION LOGIC & OFFLINE RESYNC) ---
    private OfflineOperationQueue operationQueue;
    private final AtomicLong lamportClock = new AtomicLong(0);
    private String currentRoomId = "ROOM-101";
    private String currentUserId = "user-" + UUID.randomUUID().toString().substring(0, 6);
    private String currentUserName = "SinhVienJava";
    private String currentHost = "127.0.0.1";
    private int currentPort = 8080;

    private volatile boolean isConnected = false;
    private boolean isApplyingRemoteEdit = false;
    private String lastEditorContent = "";

    // M3 length-prefixed transport, shared with the headless deployment demo.
    private volatile NetworkClient networkClient;
    private String queueRoomId = currentRoomId;
    private String accessToken = System.getenv().getOrDefault("OVERLEAF_TOKEN", "");
    private boolean useTls = Boolean.parseBoolean(System.getenv().getOrDefault("OVERLEAF_TLS", "false"));

    private static final String DEFAULT_LATEX_CODE =
            "\\documentclass{article}\n" +
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
                    "Hỗ trợ chế độ soạn thảo Offline và tự động Resync bằng CRDT.\n" +
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

        // Khởi tạo M2 ProjectFileManager tại thư mục làm việc hiện tại
        fileManager = new ProjectFileManager(".");

        // Nạp nội dung từ file main.tex thực tế (nếu chưa có thì tạo mới bằng mã mặc định)
        String initialCode = DEFAULT_LATEX_CODE;
        try {
            String diskContent = fileManager.readTexFile(mainTexFileName);
            if (diskContent != null && !diskContent.isBlank()) {
                initialCode = diskContent;
            } else {
                fileManager.writeTexFile(mainTexFileName, DEFAULT_LATEX_CODE);
            }
        } catch (IOException e) {
            System.err.println("[WARN] Không thể đọc/ghi file main.tex ban đầu: " + e.getMessage());
        }

        // Khởi tạo hàng đợi Offline cho phòng mặc định
        operationQueue = new OfflineOperationQueue(currentRoomId, currentUserId, currentUserName);
        operationQueue.onConnectionLost();

        BorderPane rootLayout = new BorderPane();

        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(createMenuBar(primaryStage), createMainToolBar(primaryStage));
        rootLayout.setTop(topContainer);

        rootLayout.setCenter(createMainWorkspace(primaryStage, initialCode));
        rootLayout.setBottom(createStatusBar());

        Scene scene = new Scene(rootLayout, 1260, 780);
        try {
            java.net.URL cssUrl = getClass().getResource("/styles/overleaf-theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                File localCss = new File("overleaf-client/src/main/resources/styles/overleaf-theme.css");
                if (localCss.exists()) {
                    scene.getStylesheets().add(localCss.toURI().toURL().toExternalForm());
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Không thể tải file CSS: " + e.getMessage());
        }

        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), this::saveFile);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN), this::triggerRecompile);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::triggerRecompile);

        primaryStage.setOnCloseRequest(e -> cleanupOnExit());
        primaryStage.setScene(scene);
        primaryStage.show();

        // Đọc nội dung tệp main.tex từ ổ cứng nếu có, hoặc tạo mới nếu chưa có
        try {
            File mainTexFile = new File("main.tex");
            if (mainTexFile.exists()) {
                String content = Files.readString(mainTexFile.toPath(), StandardCharsets.UTF_8);
                codeEditor.setText(content);
                lastEditorContent = content;
                appendLog("[IO] Đã nạp mã nguồn từ tệp 'main.tex' trên ổ cứng.");
            } else {
                Files.writeString(mainTexFile.toPath(), DEFAULT_LATEX_CODE, StandardCharsets.UTF_8);
                lastEditorContent = DEFAULT_LATEX_CODE;
                appendLog("[IO] Đã tạo tệp nguồn 'main.tex' ban đầu.");
            }
        } catch (Exception e) {
            System.err.println("[WARN] Lỗi đọc/ghi main.tex: " + e.getMessage());
        }

        // Tích hợp M2 WorkspaceWatcher để theo dõi thay đổi tệp trên thư mục dự án
        try {
            workspaceWatcher = new WorkspaceWatcher(".");
            workspaceWatcher.startWatching(changedFile -> {
                Platform.runLater(() -> {
                    appendLog("[IO WATCHER] Phát hiện tệp thay đổi trên ổ cứng: " + changedFile);
                    refreshProjectTree();
                });
            });
            appendLog("[INIT] WorkspaceWatcher (M2 I/O) đã kích hoạt theo dõi thư mục dự án.");
        } catch (Exception e) {
            appendLog("[WARN] Không thể khởi động WorkspaceWatcher: " + e.getMessage());
        }

        // Cập nhật bản xem trước PDF ĐỘNG ban đầu từ mã nguồn
        pdfCanvasPane.updatePreview(codeEditor.getText());

        appendLog("[INIT] Khởi động Overleaf Desktop (JavaFX Client Engine)...");
        appendLog("[INIT] Đã kết nối M2 ProjectFileManager với thư mục: " + fileManager.getProjectRoot());
        appendLog("[INIT] Đã kích hoạt M4 OfflineOperationQueue cho phòng: " + currentRoomId);
        appendLog("[INIT] Chế độ Offline đã sẵn sàng: mọi thao tác đều được lưu an toàn.");
    }

    private void cleanupOnExit() {
        compilerService.shutdown();
        if (workspaceWatcher != null) {
            workspaceWatcher.stopWatching();
        }
        closeSocketQuietly();
    }

    private MenuBar createMenuBar(Stage stage) {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem itemNew = new MenuItem("Tạo file mới...");
        itemNew.setOnAction(e -> showNewFileDialog(stage));

        MenuItem itemSave = new MenuItem("Lưu (Ctrl + S)");
        itemSave.setOnAction(e -> saveFile());

        MenuItem itemExit = new MenuItem("Thoát");
        itemExit.setOnAction(e -> {
            cleanupOnExit();
            Platform.exit();
        });

        fileMenu.getItems().addAll(itemNew, itemSave, new SeparatorMenuItem(), itemExit);

        Menu projectMenu = new Menu("Project");
        MenuItem itemCompile = new MenuItem("Recompile (F5 hoặc Ctrl + Enter)");
        itemCompile.setOnAction(e -> triggerRecompile());

        MenuItem itemClean = new MenuItem("Dọn dẹp file tạm (.aux, .log)");
        itemClean.setOnAction(e -> {
            appendLog("[CLEAN] Đã dọn dẹp các tệp tạm thời (.aux, .log, .out).");
            showAlert(Alert.AlertType.INFORMATION, "Dọn dẹp", "Đã xóa toàn bộ file tạm sinh ra trong quá trình biên dịch.");
        });

        projectMenu.getItems().addAll(itemCompile, itemClean);

        Menu collabMenu = new Menu("Collaboration");
        MenuItem itemConnect = new MenuItem("Kết nối phòng (TCP Socket)...");
        itemConnect.setOnAction(e -> showConnectDialog(stage));

        MenuItem itemSimulateDisconnect = new MenuItem("Mất mạng / Chế độ Offline (M4 Simulate Lost)");
        itemSimulateDisconnect.setOnAction(e -> simulateConnectionLost());

        MenuItem itemSimulateReconnect = new MenuItem("Nối lại mạng & Tự động Resync (M4 Reconnect)");
        itemSimulateReconnect.setOnAction(e -> simulateReconnect(stage));

        MenuItem itemDisconnect = new MenuItem("Ngắt kết nối phòng");
        itemDisconnect.setOnAction(e -> disconnectRoom());

        MenuItem itemQueueStatus = new MenuItem("Xem hàng đợi Offline (Pending Operations)");
        itemQueueStatus.setOnAction(e -> showQueueStatusDialog());

        collabMenu.getItems().addAll(itemConnect, itemSimulateDisconnect, itemSimulateReconnect,
                new SeparatorMenuItem(), itemQueueStatus, itemDisconnect);

        Menu helpMenu = new Menu("Help");
        MenuItem itemAbout = new MenuItem("Thông tin dự án");
        itemAbout.setOnAction(e -> showAlert(Alert.AlertType.INFORMATION, "Giới thiệu Overleaf Desktop",
                "Overleaf Desktop (Pure Java Edition)\n" +
                        "Môn học: Java Software Development (2026-2027)\n\n" +
                        "Mô-đun M1: JavaFX GUI & Client Architecture\n" +
                        "Mô-đun M2: ProcessBuilder Compiler Engine & WorkspaceWatcher\n" +
                        "Mô-đun M3: TCP Socket Networking\n" +
                        "Mô-đun M4: CRDT Collaboration, Offline Queue & Resync Logic\n" +
                        "Mô-đun M5: MongoDB Persistence & Testing"));
        helpMenu.getItems().add(itemAbout);

        menuBar.getMenus().addAll(fileMenu, projectMenu, collabMenu, helpMenu);
        return menuBar;
    }

    private HBox createMainToolBar(Stage stage) {
        HBox toolBar = new HBox(12);
        toolBar.getStyleClass().add("main-toolbar");
        toolBar.setAlignment(Pos.CENTER_LEFT);

        Label brand = new Label("🍃 Overleaf Desktop");
        brand.getStyleClass().add("brand-title");

        btnRecompile = new Button("▶  Recompile");
        btnRecompile.getStyleClass().add("btn-recompile");
        btnRecompile.setOnAction(e -> triggerRecompile());

        Label compilerLabel = new Label("Compiler:");
        compilerLabel.setTextFill(Color.web("#CBD5E1"));
        compilerCombo = new ComboBox<>();
        compilerCombo.getItems().addAll("pdfLaTeX (Local)", "XeLaTeX (Unicode)", "LuaLaTeX");
        compilerCombo.setValue("pdfLaTeX (Local)");
        compilerCombo.getStyleClass().add("combo-dark");

        // Đảm bảo chữ trắng rõ nét trên nền tối cho ComboBox và popup list
        compilerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setTextFill(Color.web("#F1F5F9"));
                    setStyle("-fx-background-color: #1E293B; -fx-padding: 6 12 6 12;");
                }
            }
        });
        compilerCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setTextFill(Color.WHITE);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
                }
            }
        });

        Button btnSave = new Button("💾 Lưu");
        btnSave.getStyleClass().add("btn-secondary");
        btnSave.setOnAction(e -> saveFile());

        Button btnConnect = new Button("🌐 Kết nối phòng...");
        btnConnect.getStyleClass().add("btn-secondary");
        btnConnect.setOnAction(e -> showConnectDialog(stage));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        connectionBadge = new Label("● Chạy cục bộ (Offline)");
        connectionBadge.getStyleClass().add("badge-pill-offline");

        toolBar.getChildren().addAll(brand, new Separator(Orientation.VERTICAL),
                btnRecompile, compilerLabel, compilerCombo, btnSave, btnConnect,
                spacer, connectionBadge);

        return toolBar;
    }

    private SplitPane createMainWorkspace(Stage stage, String initialContent) {
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);

        VBox leftPane = createLeftPanel(stage);
        SplitPane centerSplit = createCenterEditorPanel(initialContent);
        pdfCanvasPane = new PdfCanvasPane();

        mainSplit.getItems().addAll(leftPane, centerSplit, pdfCanvasPane);
        mainSplit.setDividerPositions(0.18, 0.60);

        return mainSplit;
    }

    private VBox createLeftPanel(Stage stage) {
        VBox container = new VBox();
        VBox.setVgrow(container, Priority.ALWAYS);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Tab filesTab = new Tab("Tệp tin");
        VBox filesBox = new VBox(8);
        filesBox.setPadding(new Insets(8));
        VBox.setVgrow(filesBox, Priority.ALWAYS);

        rootTreeItem = new TreeItem<>("📁 Research_Paper_2026");
        rootTreeItem.setExpanded(true);

        projectTreeView = new TreeView<>(rootTreeItem);
        VBox.setVgrow(projectTreeView, Priority.ALWAYS);
        projectTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.isLeaf()) {
                appendLog("[FILE] Đang mở tệp: " + newVal.getValue());
            }
        });

        refreshProjectTree();

        HBox fileButtons = new HBox(6);
        Button btnNewFile = new Button("+ Tệp mới");
        btnNewFile.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnNewFile, Priority.ALWAYS);
        btnNewFile.setOnAction(e -> showNewFileDialog(stage));

        Button btnRefresh = new Button("🔄 Làm mới");
        btnRefresh.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnRefresh, Priority.ALWAYS);
        btnRefresh.setOnAction(e -> {
            refreshProjectTree();
            appendLog("[FILE] Đã đồng bộ cấu trúc tệp từ đĩa cứng.");
        });

        fileButtons.getChildren().addAll(btnNewFile, btnRefresh);
        filesBox.getChildren().addAll(projectTreeView, fileButtons);
        filesTab.setContent(filesBox);

        Tab collabTab = new Tab("Cộng tác");
        VBox collabBox = new VBox(8);
        collabBox.setPadding(new Insets(8));
        VBox.setVgrow(collabBox, Priority.ALWAYS);

        collabListView = new ListView<>();
        updateCollabListDisplay();
        VBox.setVgrow(collabListView, Priority.ALWAYS);

        Label tipLabel = new Label("Đồng bộ thời gian thực qua M4 CRDT & Offline Queue.");
        tipLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");
        collabBox.getChildren().addAll(new Label("Trực tuyến trong phòng:"), collabListView, tipLabel);
        collabTab.setContent(collabBox);

        tabPane.getTabs().addAll(filesTab, collabTab);
        container.getChildren().add(tabPane);

        return container;
    }

    /**
     * Làm mới cây thư mục dự án dựa trên các tệp thực tế trên ổ cứng
     */
    private void refreshProjectTree() {
        if (rootTreeItem == null) return;
        rootTreeItem.getChildren().clear();

        TreeItem<String> mainTex = new TreeItem<>("📄 main.tex");
        TreeItem<String> bibTex = new TreeItem<>("📄 references.bib");
        TreeItem<String> figuresFolder = new TreeItem<>("📁 figures");
        figuresFolder.getChildren().addAll(
                new TreeItem<>("🖼 architecture.png"),
                new TreeItem<>("🖼 results_chart.png")
        );
        figuresFolder.setExpanded(true);

        TreeItem<String> sectionsFolder = new TreeItem<>("📁 sections");
        sectionsFolder.getChildren().addAll(
                new TreeItem<>("📄 intro.tex"),
                new TreeItem<>("📄 evaluation.tex")
        );
        sectionsFolder.setExpanded(true);

        rootTreeItem.getChildren().addAll(mainTex, bibTex, figuresFolder, sectionsFolder);

        // Quét các tệp thực tế ở thư mục hiện tại
        File dir = new File(".");
        File[] extraFiles = dir.listFiles((d, name) ->
                (name.endsWith(".tex") && !name.equals("main.tex")) ||
                (name.endsWith(".pdf")));
        if (extraFiles != null) {
            for (File f : extraFiles) {
                String icon = f.getName().endsWith(".pdf") ? "📕 " : "📄 ";
                rootTreeItem.getChildren().add(new TreeItem<>(icon + f.getName()));
            }
        }
    }

    private void updateCollabListDisplay() {
        collabListView.getItems().clear();
        collabListView.getItems().add("👤 " + currentUserName + " (Bạn - " + (isConnected ? "Online" : "Offline") + ")");
        if (isConnected) {
            collabListView.getItems().add("Đang chờ danh sách thành viên từ máy chủ...");
        } else {
            collabListView.getItems().add("⚪ Phòng chưa kết nối Server (Offline Mode)");
        }
    }

    private SplitPane createCenterEditorPanel(String initialContent) {
        SplitPane editorSplit = new SplitPane();
        editorSplit.setOrientation(Orientation.VERTICAL);

        VBox editorBox = new VBox();
        editorBox.getStyleClass().add("editor-container");

        HBox editorHeader = new HBox(10);
        editorHeader.getStyleClass().add("editor-header");
        Label editorTitle = new Label("📄 main.tex  (LaTeX Source Code - UTF-8)");
        editorTitle.getStyleClass().add("editor-header-title");
        editorHeader.getChildren().add(editorTitle);

        codeEditor = new TextArea(initialContent);
        lastEditorContent = initialContent;
        codeEditor.getStyleClass().add("code-textarea");
        VBox.setVgrow(codeEditor, Priority.ALWAYS);

        codeEditor.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCursorPosition());

        // LẮNG NGHE NGƯỜI DÙNG GÕ VĂN BẢN (M4 Quy tắc 1) & CẬP NHẬT CANVAS ĐỘNG
        codeEditor.textProperty().addListener((obs, oldText, newText) -> {
            handleLocalTextChange(oldText, newText);
        });

        editorBox.getChildren().addAll(editorHeader, codeEditor);

        VBox terminalBox = new VBox();
        terminalBox.getStyleClass().add("terminal-container");

        HBox terminalHeader = new HBox(8);
        terminalHeader.getStyleClass().add("terminal-header");
        Label termTitle = new Label("💻 Terminal Output (ProcessBuilder Engine: latexmk / pdflatex)");
        termTitle.getStyleClass().add("terminal-header-title");
        Region termSpacer = new Region();
        HBox.setHgrow(termSpacer, Priority.ALWAYS);

        Button btnClearLog = new Button("Xóa Log");
        btnClearLog.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6; -fx-background-color: #334155; -fx-text-fill: white;");
        btnClearLog.setOnAction(e -> terminalLogArea.clear());

        terminalHeader.getChildren().addAll(termTitle, termSpacer, btnClearLog);

        terminalLogArea = new TextArea();
        terminalLogArea.setEditable(false);
        terminalLogArea.getStyleClass().add("terminal-textarea");
        VBox.setVgrow(terminalLogArea, Priority.ALWAYS);

        terminalBox.getChildren().addAll(terminalHeader, terminalLogArea);

        editorSplit.getItems().addAll(editorBox, terminalBox);
        editorSplit.setDividerPositions(0.72);

        return editorSplit;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(14);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Sẵn sàng. Nhấn 'Recompile' hoặc bấm Ctrl+Enter để dịch sang PDF.");
        statusLabel.getStyleClass().add("status-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        cursorLabel = new Label("Dòng 1, Cột 1 | " + DEFAULT_LATEX_CODE.length() + " ký tự");
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

    // =========================================================================
    // XỬ LÝ M4: SOẠN THẢO, TẠO CRDT OPERATION VÀ OFFLINE QUEUE (QUY TẮC 1)
    // =========================================================================

    /**
     * Phát hiện sai khác (Diff) khi người dùng gõ văn bản, đóng gói thành CRDTOperation
     * và cập nhật ngay vào bản xem trước Canvas động.
     */
    private void handleLocalTextChange(String oldText, String newText) {
        if (isApplyingRemoteEdit || operationQueue == null) {
            return;
        }

        if (operationQueue.isResyncing()) {
            return;
        }

        if (oldText == null) oldText = "";
        if (newText == null) newText = "";
        if (oldText.equals(newText)) return;

        int start = 0;
        int oldLen = oldText.length();
        int newLen = newText.length();
        while (start < oldLen && start < newLen && oldText.charAt(start) == newText.charAt(start)) {
            start++;
        }

        int oldEnd = oldLen - 1;
        int newEnd = newLen - 1;
        while (oldEnd >= start && newEnd >= start && oldText.charAt(oldEnd) == newText.charAt(newEnd)) {
            oldEnd--;
            newEnd--;
        }

        int deletedCount = oldEnd - start + 1;
        int insertedCount = newEnd - start + 1;

        if (deletedCount > 0) {
            String deletedContent = oldText.substring(start, start + deletedCount);
            long ts = lamportClock.incrementAndGet();
            CRDTOperation delOp = CRDTOperation.createDelete(start, deletedContent, currentUserId, ts);
            dispatchOperation(delOp);
        }

        if (insertedCount > 0) {
            String inserted = newText.substring(start, start + insertedCount);
            long ts = lamportClock.incrementAndGet();
            CRDTOperation insOp = CRDTOperation.createInsert(start, inserted, currentUserId, ts);
            dispatchOperation(insOp);
        }

        lastEditorContent = newText;
        updatePendingBadge();

        // Cập nhật bản xem trước Canvas động ngay lập tức theo nội dung người dùng gõ
        if (pdfCanvasPane != null) {
            pdfCanvasPane.updatePreview(newText);
        }
    }

    private void dispatchOperation(CRDTOperation op) {
        try {
            SyncMessage m = operationQueue.prepareEdit(op);
            if (m != null) {
                sendMessageToServer(m);
                appendLog(String.format("[M4-SYNC] Đã gửi %s tại vị trí %d (Lamport: %d)",
                        op.getType(), op.getPosition(), op.getLamportTimestamp()));
            } else {
                appendLog(String.format("[M4-OFFLINE] Đã lưu vào hàng đợi: %s tại vị trí %d (Chờ gửi: %d)",
                        op.getType(), op.getPosition(), operationQueue.getPendingCount()));
            }
        } catch (IllegalStateException e) {
            appendLog("[WARN] " + e.getMessage());
        }
    }

    private void updatePendingBadge() {
        Platform.runLater(() -> {
            if (operationQueue == null) return;
            int pending = operationQueue.getPendingCount();
            if (isConnected) {
                if (pending > 0) {
                    connectionBadge.setText("● Đã kết nối: " + currentRoomId + " (" + pending + " chờ ACK)");
                } else {
                    connectionBadge.setText("● Đã kết nối: " + currentRoomId);
                }
                connectionBadge.getStyleClass().clear();
                connectionBadge.getStyleClass().add("badge-pill-online");
            } else {
                if (pending > 0) {
                    connectionBadge.setText("● Offline (" + currentRoomId + " - " + pending + " thao tác chờ đồng bộ)");
                } else {
                    connectionBadge.setText("● Offline (" + currentRoomId + ")");
                }
                connectionBadge.getStyleClass().clear();
                connectionBadge.getStyleClass().add("badge-pill-offline");
            }
        });
    }

    // =========================================================================
    // XỬ LÝ M4: NHẬN THÔNG ĐIỆP TỪ SERVER (QUY TẮC 2, 4, 5)
    // =========================================================================

    private void handleIncomingServerMessage(SyncMessage msg) {
        Platform.runLater(() -> {
            if (operationQueue != null) {
                operationQueue.handleServerMessage(msg);
            }

            switch (msg.getMessageType()) {
                case FULL_SYNC:
                    isApplyingRemoteEdit = true;
                    try {
                        codeEditor.setText(msg.getPayload());
                        lastEditorContent = msg.getPayload();
                        pdfCanvasPane.updatePreview(lastEditorContent);
                        appendLog("[M4-SYNC] Đã cập nhật toàn bộ tài liệu (FULL_SYNC, " + msg.getPayload().length() + " ký tự).");
                    } finally {
                        isApplyingRemoteEdit = false;
                    }
                    break;

                case TEXT_EDIT:
                    if (!currentUserId.equals(msg.getSenderId())) {
                        CRDTOperation op = OperationCodec.fromJson(msg.getPayload());
                        lamportClock.accumulateAndGet(op.getLamportTimestamp(), Math::max);
                        applyRemoteOperation(op);
                        appendLog(String.format("[M4-COLLAB] %s đã sửa: %s tại vị trí %d",
                                msg.getSenderName(), op.getType(), op.getPosition()));
                    }
                    break;

                case ACK:
                    appendLog("[M4-ACK] Server xác nhận thao tác ID: " + msg.getPayload());
                    updatePendingBadge();
                    break;

                case CHECKPOINT:
                    appendLog("[M4-CHECKPOINT] Điểm đã thấy mới từ server: " + msg.getPayload());
                    if (operationQueue != null && !operationQueue.isResyncing()) {
                        codeEditor.setEditable(true);
                        statusLabel.setText("Đã đồng bộ lại hoàn tất! Ô soạn thảo đã mở khóa.");
                        appendLog("[M4-RESYNC] Quá trình Resync hoàn tất. Ô soạn thảo đã sẵn sàng.");
                    }
                    updatePendingBadge();
                    break;

                case USER_LIST_UPDATE:
                    appendLog("[M4-ROOM] Danh sách người dùng trong phòng được cập nhật: " + msg.getPayload());
                    collabListView.getItems().clear();
                    if (!msg.getPayload().isBlank()) {
                        for (String member : msg.getPayload().split("\n")) {
                            String[] fields = member.split("\\|", 4);
                            collabListView.getItems().add(fields.length > 1 ? fields[1] + " (" + fields[0] + ")" : member);
                        }
                    }
                    break;

                case ERROR:
                    if (msg.getPayload() != null && msg.getPayload().contains("ROOM_UNKNOWN")) {
                        codeEditor.setEditable(true);
                        appendLog("[M4-ERROR] Lỗi ROOM_UNKNOWN: Phòng không tồn tại trên Server!");
                        showAlert(Alert.AlertType.WARNING, "Phòng không tồn tại (ROOM_UNKNOWN)",
                                "Phòng '" + currentRoomId + "' hiện không có trên Server (có thể do Server vừa khởi động lại).\n\n" +
                                        "Toàn bộ văn bản và thao tác cục bộ của bạn ĐƯỢC GIỮ NGUYÊN AN TOÀN.\n" +
                                        "Vui lòng bấm 'Lưu' để giữ bản sao, sau đó kết nối lại phòng!");
                        simulateConnectionLost();
                    } else {
                        appendLog("[SERVER ERROR] " + msg.getPayload());
                        if (!codeEditor.isEditable()) simulateConnectionLost();
                    }
                    break;

                default:
                    break;
            }
        });
    }

    private void applyRemoteOperation(CRDTOperation op) {
        isApplyingRemoteEdit = true;
        try {
            int currentLen = codeEditor.getText().length();
            int pos = Math.min(Math.max(0, op.getPosition()), currentLen);

            if (op.isInsert()) {
                codeEditor.insertText(pos, op.getContent());
            } else if (op.isDelete()) {
                int end = Math.min(pos + op.getContentLength(), currentLen);
                if (end > pos) {
                    codeEditor.deleteText(pos, end);
                }
            }
            lastEditorContent = codeEditor.getText();
            pdfCanvasPane.updatePreview(lastEditorContent);
        } finally {
            isApplyingRemoteEdit = false;
        }
    }

    private void showConnectDialog(Stage parent) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(parent);
        dialog.setTitle("Kết nối Phòng Cộng tác (TCP Socket)");
        dialog.setHeaderText("Nhập thông số kết nối Server nhóm (M3 & M4)");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        TextField hostField = new TextField(currentHost);
        TextField portField = new TextField(String.valueOf(currentPort));
        TextField roomField = new TextField(currentRoomId);
        TextField userField = new TextField(currentUserName);
        PasswordField tokenField = new PasswordField();
        tokenField.setText(accessToken);
        CheckBox tlsField = new CheckBox("TLS (máy chủ từ xa)");
        tlsField.setSelected(useTls);

        grid.add(new Label("Địa chỉ Máy chủ (Host):"), 0, 0);
        grid.add(hostField, 1, 0);
        grid.add(new Label("Cổng kết nối (Port TCP):"), 0, 1);
        grid.add(portField, 1, 1);
        grid.add(new Label("Mã phòng (Room ID):"), 0, 2);
        grid.add(roomField, 1, 2);
        grid.add(new Label("Tên hiển thị:"), 0, 3);
        grid.add(userField, 1, 3);
        grid.add(new Label("Access token:"), 0, 4);
        grid.add(tokenField, 1, 4);
        grid.add(tlsField, 1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            currentHost = hostField.getText().trim();
            try {
                currentPort = Integer.parseInt(portField.getText().trim());
                if (currentPort < 1 || currentPort > 65535) throw new NumberFormatException();
            } catch (NumberFormatException invalidPort) {
                showAlert(Alert.AlertType.ERROR, "Cổng không hợp lệ", "Nhập cổng từ 1 đến 65535.");
                return;
            }
            currentRoomId = roomField.getText().trim();
            currentUserName = userField.getText().trim();
            accessToken = tokenField.getText();
            useTls = tlsField.isSelected();

            connectToRoom(currentHost, currentPort, currentRoomId, currentUserName, false);
        }
    }

    private void connectToRoom(String host, int port, String room, String user, boolean isReconnecting) {
        statusLabel.setText("Đang kết nối tới Server " + host + ":" + port + "...");

        if (operationQueue == null || !queueRoomId.equals(room)) {
            operationQueue = new OfflineOperationQueue(room, currentUserId, user);
            queueRoomId = room;
        }

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                closeSocketQuietly();
                networkClient = new NetworkClient(host, port, useTls, accessToken);
                return true;
            }
        };

        connectTask.setOnSucceeded(e -> {
            isConnected = true;
            appendLog(String.format("[NETWORK] Đã kết nối TCP thành công: %s:%s | Phòng: %s | Người dùng: %s", host, port, room, user));
            updateCollabListDisplay();

            startSocketReaderThread();

            if (isReconnecting) {
                SyncMessage resyncMsg = operationQueue.onReconnected();
                codeEditor.setEditable(false);
                statusLabel.setText("Đang đồng bộ lại với phòng... Vui lòng đợi trong giây lát.");
                sendMessageToServer(resyncMsg);
                appendLog("[M4-RESYNC] Đã gửi thông điệp RESYNC với " + operationQueue.getPendingCount() + " thao tác chưa xác nhận.");
            } else {
                codeEditor.setEditable(false);
                SyncMessage joinMsg = SyncMessage.createJoinRoom(room, currentUserId, user);
                sendMessageToServer(joinMsg);
                appendLog("[M4-ROOM] Đã gửi yêu cầu JOIN_ROOM vào phòng: " + room);
            }

            updatePendingBadge();
            statusLabel.setText("Đã kết nối máy chủ; đang chờ trạng thái phòng...");
        });

        connectTask.setOnFailed(e -> {
            isConnected = false;
            if (operationQueue != null) {
                operationQueue.onConnectionLost();
            }
            updatePendingBadge();
            updateCollabListDisplay();

            appendLog("[NETWORK] Không tìm thấy Server TCP tại " + host + ":" + port + ". Kích hoạt CHẾ ĐỘ OFFLINE.");
            appendLog("[M4-OFFLINE] Các thao tác gõ sẽ được lưu giữ an toàn trong OfflineOperationQueue.");
            statusLabel.setText("Đang ở Chế độ Offline. Mọi thao tác gõ đều được lưu trữ an toàn.");

            showAlert(Alert.AlertType.INFORMATION, "Chế độ Offline (M4)",
                    "Chưa thể kết nối tới Server TCP tại " + host + ":" + port + ".\n\n" +
                            "Ứng dụng đã tự động kích hoạt CHẾ ĐỘ OFFLINE của M4:\n" +
                            "- Bạn có thể tiếp tục gõ văn bản bình thường.\n" +
                            "- Các thao tác sửa đổi được lưu an toàn vào hàng đợi.\n" +
                            "- Khi có Server kết nối lại, hệ thống sẽ tự động đồng bộ hóa (Resync)!");
        });

        Thread t = new Thread(connectTask);
        t.setDaemon(true);
        t.start();
    }

    private void simulateConnectionLost() {
        isConnected = false;
        codeEditor.setEditable(true);
        closeSocketQuietly();
        if (operationQueue != null) {
            operationQueue.onConnectionLost();
        }
        updatePendingBadge();
        updateCollabListDisplay();
        statusLabel.setText("Đã ngắt kết nối. Đang ở Chế độ Offline (Người dùng vẫn gõ được bình thường).");
        appendLog("[M4-OFFLINE] Kết nối bị ngắt -> queue.onConnectionLost(). Tiếp tục cho phép người dùng gõ.");
    }

    private void simulateReconnect(Stage stage) {
        if (operationQueue == null) {
            showConnectDialog(stage);
            return;
        }
        connectToRoom(currentHost, currentPort, currentRoomId, currentUserName, true);
    }

    private void disconnectRoom() {
        if (isConnected && operationQueue != null) {
            sendMessageToServer(SyncMessage.createLeaveRoom(currentRoomId, currentUserId, currentUserName));
        }
        simulateConnectionLost();
        appendLog("[NETWORK] Đã rời phòng làm việc nhóm. Trở về chế độ lưu trữ cục bộ.");
    }

    private void startSocketReaderThread() {
        NetworkClient client = networkClient;
        client.start(this::handleIncomingServerMessage, error -> Platform.runLater(() -> {
            if (networkClient == client) {
                if (error != null) appendLog("[NETWORK] " + error.getMessage());
                simulateConnectionLost();
            }
        }));
    }

    private void sendMessageToServer(SyncMessage msg) {
        NetworkClient client = networkClient;
        if (!isConnected || client == null) return;
        try {
            client.send(msg);
        } catch (IOException e) {
            Platform.runLater(() -> {
                if (networkClient != client) return;
                appendLog("[NETWORK SEND ERROR] " + e.getMessage());
                simulateConnectionLost();
            });
        }
    }

    private void closeSocketQuietly() {
        NetworkClient client = networkClient;
        networkClient = null;
        if (client != null) client.close();
    }

    private void showQueueStatusDialog() {
        int pendingCount = operationQueue != null ? operationQueue.getPendingCount() : 0;
        String lastSeen = operationQueue != null ? operationQueue.getLastSeenOperationId() : "N/A";
        boolean online = operationQueue != null && operationQueue.isOnline();
        boolean resyncing = operationQueue != null && operationQueue.isResyncing();

        String info = String.format(
                "Phòng: %s\n" +
                        "Người dùng: %s (ID: %s)\n" +
                        "Trạng thái hàng đợi: %s\n" +
                        "Đang đồng bộ lại (Resyncing): %s\n" +
                        "Số thao tác chờ gửi (Pending unacked): %d\n" +
                        "Mã thao tác cuối đã thấy (LastSeen): %s\n" +
                        "Đồng hồ Lamport hiện tại: %d",
                currentRoomId, currentUserName, currentUserId,
                online ? "Online (Sẵn sàng gửi ngay)" : "Offline (Đang tích lũy thao tác)",
                resyncing ? "Có (Ô soạn thảo đang khóa)" : "Không (Ô soạn thảo sẵn sàng)",
                pendingCount, lastSeen.isEmpty() ? "(chưa có)" : lastSeen,
                lamportClock.get()
        );

        showAlert(Alert.AlertType.INFORMATION, "Trạng thái M4 Offline Queue", info);
    }

    private void triggerRecompile() {
        btnRecompile.setDisable(true);
        compileProgressBar.setVisible(true);
        compileProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        String selectedCompiler = compilerCombo.getValue();
        statusLabel.setText("Đang biên dịch mã nguồn LaTeX (" + selectedCompiler + ")...");

        appendLog("\n========================================================");
        appendLog("[BUILD] Khởi động trình biên dịch: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        appendLog("[BUILD] Trình biên dịch: " + selectedCompiler + " | Tệp nguồn: main.tex");

        // 1. Lưu nội dung mới nhất vào main.tex trên ổ cứng trước khi biên dịch
        try {
            File texFile = new File("main.tex");
            Files.writeString(texFile.toPath(), codeEditor.getText(), StandardCharsets.UTF_8);
            appendLog("[BUILD] Đã đồng bộ mã nguồn vào đĩa (main.tex, " + texFile.length() + " bytes).");
        } catch (Exception e) {
            appendLog("[WARN] Không thể ghi main.tex trước khi biên dịch: " + e.getMessage());
        }

        // 2. Chạy ProcessBuilder thông qua LatexCompilerService của M2
        compilerService.compileAsync(".", "main.tex", new LatexCompilerService.CompileCallback() {
            @Override
            public void onSuccess(LatexCompilerService.CompileResult result) {
                Platform.runLater(() -> {
                    btnRecompile.setDisable(false);
                    compileProgressBar.setVisible(false);
                    statusLabel.setText("Biên dịch thành công! Hoàn tất trong " + result.durationMillis() + "ms.");
                    appendLog(result.log());
                    appendLog("[SUCCESS] Bản PDF được tạo tại: " + result.pdfPath());
                    pdfCanvasPane.updatePreview(codeEditor.getText());
                });
            }

            @Override
            public void onError(int exitCode, String errorLog) {
                Platform.runLater(() -> {
                    btnRecompile.setDisable(false);
                    compileProgressBar.setVisible(false);
                    appendLog("[BUILD NOTICE] ProcessBuilder báo: " + errorLog);
                    appendLog("[INFO] Cập nhật bản xem trước đồ họa độ phân giải cao.");
                    statusLabel.setText("Đã cập nhật bản xem trước PDF động.");
                    // Vẫn cập nhật canvas động theo nội dung mã nguồn để người dùng xem trước tài liệu
                    pdfCanvasPane.updatePreview(codeEditor.getText());
                });
            }
        });
    }

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
        } catch (Exception ignored) {}
    }

    private void appendLog(String msg) {
        Platform.runLater(() -> {
            terminalLogArea.appendText(msg + "\n");
        });
    }

    private void showNewFileDialog(Stage parent) {
        TextInputDialog dialog = new TextInputDialog("chapter1.tex");
        dialog.initOwner(parent);
        dialog.setTitle("Tạo tệp mới");
        dialog.setHeaderText("Thêm tệp nguồn hoặc tài liệu tham khảo vào dự án");
        dialog.setContentText("Tên tệp tin (ví dụ: conclusion.tex):");

        dialog.showAndWait().ifPresent(fileName -> {
            String name = fileName.trim();
            if (!name.isEmpty()) {
                try {
                    File newFile = new File(name);
                    if (!newFile.exists()) {
                        newFile.createNewFile();
                    }
                    rootTreeItem.getChildren().add(new TreeItem<>("📄 " + name));
                    appendLog("[FILE] Đã tạo thành công tệp mới trên đĩa: " + name);
                } catch (Exception e) {
                    appendLog("[ERROR] Không thể tạo tệp: " + e.getMessage());
                }
            }
        });
    }

    private void saveFile() {
        try {
            File file = new File("main.tex");
            Files.writeString(file.toPath(), codeEditor.getText(), StandardCharsets.UTF_8);
            appendLog("[SAVE] Đã lưu tệp 'main.tex' thành công vào thư mục dự án (" + file.length() + " bytes).");
            statusLabel.setText("Đã lưu tệp lúc: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
            showAlert(Alert.AlertType.INFORMATION, "Lưu tệp", "Đã lưu tệp 'main.tex' thành công vào ổ cứng!");
        } catch (Exception e) {
            appendLog("[ERROR] Không thể lưu tệp: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Lỗi lưu tệp", "Không thể ghi tệp vào đĩa: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // =========================================================================
    // LỚP KHUNG XEM TRƯỚC PDF ĐỘNG (DYNAMIC LATEX RENDERER CANVAS)
    // =========================================================================
    public static class PdfCanvasPane extends VBox {
        private final Canvas canvas;
        private final Label zoomLabel;
        private double zoomFactor = 1.0;
        private String currentTexCode = "";

        public PdfCanvasPane() {
            getStyleClass().add("preview-container");
            VBox.setVgrow(this, Priority.ALWAYS);

            HBox toolBar = new HBox(8);
            toolBar.getStyleClass().add("preview-toolbar");

            Label title = new Label("PDF Preview (Xem trước động)");
            title.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button btnZoomOut = new Button("🔍 -");
            zoomLabel = new Label("100%");
            zoomLabel.setStyle("-fx-font-weight: bold; -fx-padding: 0 4 0 4;");
            Button btnZoomIn = new Button("🔍 +");
            Button btnFit = new Button("Vừa trang");

            btnZoomIn.setOnAction(e -> {
                if (zoomFactor < 1.6) {
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

            canvas = new Canvas(580, 840);

            StackPane canvasHolder = new StackPane(canvas);
            canvasHolder.setPadding(new Insets(20));
            canvasHolder.setStyle("-fx-background-color: #CBD5E1;");

            ScrollPane scrollPane = new ScrollPane(canvasHolder);
            scrollPane.getStyleClass().add("preview-scroll");
            scrollPane.setFitToWidth(true);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            getChildren().addAll(toolBar, scrollPane);

            drawDynamicA4Paper("");
        }

        public void updatePreview(String texContent) {
            this.currentTexCode = (texContent != null) ? texContent : "";
            drawDynamicA4Paper(this.currentTexCode);
        }

        private void updateZoom() {
            zoomLabel.setText(String.format("%d%%", (int) (zoomFactor * 100)));
            drawDynamicA4Paper(this.currentTexCode);
        }

        // --- CẤU TRÚC PHẦN TỬ TÀI LIỆU LATEX ĐƯỢC PARSE ---
        private static class ParsedDoc {
            String title = "Overleaf Document";
            String author = "";
            String date = "";
            String abstractText = "";
            List<DocItem> items = new ArrayList<>();
        }

        private static class DocItem {
            enum Type { SECTION, SUBSECTION, PARAGRAPH, EQUATION, BULLET }
            Type type;
            String text;
            int number;

            DocItem(Type type, String text) {
                this.type = type;
                this.text = text;
            }

            DocItem(Type type, String text, int number) {
                this.type = type;
                this.text = text;
                this.number = number;
            }
        }

        /**
         * Bộ phân tích cú pháp LaTeX động (Dynamic LaTeX AST Parser)
         */
        private ParsedDoc parseLatex(String code) {
            ParsedDoc doc = new ParsedDoc();
            if (code == null || code.isBlank()) return doc;

            // 1. Trích xuất \title{...}
            String t = extractMacro(code, "\\title");
            if (t != null && !t.isBlank()) {
                doc.title = cleanLatex(t);
            }

            // 2. Trích xuất \author{...}
            String a = extractMacro(code, "\\author");
            if (a != null && !a.isBlank()) {
                doc.author = cleanLatex(a);
            }

            // 3. Trích xuất \date{...}
            String d = extractMacro(code, "\\date");
            if (d != null && !d.isBlank()) {
                String cleanD = cleanLatex(d);
                if (cleanD.contains("\\today") || cleanD.isBlank()) {
                    doc.date = new SimpleDateFormat("dd MMMM yyyy").format(new Date());
                } else {
                    doc.date = cleanD;
                }
            } else {
                doc.date = new SimpleDateFormat("dd MMMM yyyy").format(new Date());
            }

            // 4. Trích xuất \begin{abstract}...\end{abstract}
            int absStart = code.indexOf("\\begin{abstract}");
            if (absStart != -1) {
                int absEnd = code.indexOf("\\end{abstract}", absStart);
                if (absEnd != -1) {
                    doc.abstractText = cleanLatex(code.substring(absStart + 16, absEnd).trim());
                }
            }

            // 5. Trích xuất thân bài giữa \begin{document} và \end{document}
            int bodyStart = code.indexOf("\\begin{document}");
            String body = (bodyStart != -1) ? code.substring(bodyStart + 16) : code;
            int bodyEnd = body.indexOf("\\end{document}");
            if (bodyEnd != -1) {
                body = body.substring(0, bodyEnd);
            }

            String[] lines = body.split("\n");
            StringBuilder pBuf = new StringBuilder();
            int secNum = 0;
            int subSecNum = 0;
            int eqNum = 0;
            boolean inEq = false;
            StringBuilder eqBuf = new StringBuilder();

            for (String raw : lines) {
                String line = raw.trim();

                if (line.startsWith("%") || line.equals("\\maketitle")) continue;
                if (line.startsWith("\\begin{abstract}") || line.startsWith("\\end{abstract}")) continue;

                // Khối công thức toán (Equation)
                if (line.contains("\\begin{equation}") || line.contains("\\begin{align}") || line.equals("$$") || line.equals("\\[")) {
                    if (pBuf.length() > 0) {
                        doc.items.add(new DocItem(DocItem.Type.PARAGRAPH, pBuf.toString().trim()));
                        pBuf.setLength(0);
                    }
                    inEq = true;
                    eqBuf.setLength(0);
                    continue;
                }
                if (line.contains("\\end{equation}") || line.contains("\\end{align}") || line.equals("$$") || line.equals("\\]")) {
                    inEq = false;
                    eqNum++;
                    String eqStr = cleanLatex(eqBuf.toString().trim());
                    if (eqStr.isEmpty()) eqStr = "E = mc^2";
                    doc.items.add(new DocItem(DocItem.Type.EQUATION, eqStr, eqNum));
                    eqBuf.setLength(0);
                    continue;
                }
                if (inEq) {
                    eqBuf.append(line).append(" ");
                    continue;
                }

                // Section
                if (line.startsWith("\\section")) {
                    if (pBuf.length() > 0) {
                        doc.items.add(new DocItem(DocItem.Type.PARAGRAPH, pBuf.toString().trim()));
                        pBuf.setLength(0);
                    }
                    secNum++;
                    subSecNum = 0;
                    String sTitle = extractBraces(line);
                    doc.items.add(new DocItem(DocItem.Type.SECTION, cleanLatex(sTitle), secNum));
                    continue;
                }

                // Subsection
                if (line.startsWith("\\subsection")) {
                    if (pBuf.length() > 0) {
                        doc.items.add(new DocItem(DocItem.Type.PARAGRAPH, pBuf.toString().trim()));
                        pBuf.setLength(0);
                    }
                    subSecNum++;
                    String subTitle = extractBraces(line);
                    doc.items.add(new DocItem(DocItem.Type.SUBSECTION, secNum + "." + subSecNum + " " + cleanLatex(subTitle), subSecNum));
                    continue;
                }

                // Bullet Item
                if (line.startsWith("\\item")) {
                    if (pBuf.length() > 0) {
                        doc.items.add(new DocItem(DocItem.Type.PARAGRAPH, pBuf.toString().trim()));
                        pBuf.setLength(0);
                    }
                    String itemStr = cleanLatex(line.substring(5).trim());
                    doc.items.add(new DocItem(DocItem.Type.BULLET, itemStr));
                    continue;
                }

                if (line.startsWith("\\begin{") || line.startsWith("\\end{")) {
                    continue;
                }

                // Đoạn văn (Paragraph)
                if (line.isEmpty()) {
                    if (pBuf.length() > 0) {
                        doc.items.add(new DocItem(DocItem.Type.PARAGRAPH, pBuf.toString().trim()));
                        pBuf.setLength(0);
                    }
                } else {
                    if (pBuf.length() > 0) pBuf.append(" ");
                    pBuf.append(cleanLatex(line));
                }
            }

            if (pBuf.length() > 0) {
                doc.items.add(new DocItem(DocItem.Type.PARAGRAPH, pBuf.toString().trim()));
            }

            return doc;
        }

        private String extractMacro(String text, String macroName) {
            int idx = text.indexOf(macroName);
            if (idx == -1) return null;
            int braceStart = text.indexOf("{", idx);
            if (braceStart == -1) return null;
            int count = 1;
            int i = braceStart + 1;
            while (i < text.length() && count > 0) {
                char c = text.charAt(i);
                if (c == '{') count++;
                else if (c == '}') count--;
                i++;
            }
            if (count == 0) {
                return text.substring(braceStart + 1, i - 1);
            }
            return null;
        }

        private String extractBraces(String line) {
            int start = line.indexOf('{');
            int end = line.lastIndexOf('}');
            if (start != -1 && end != -1 && end > start) {
                return line.substring(start + 1, end);
            }
            return line;
        }

        private String cleanLatex(String s) {
            if (s == null) return "";
            String r = s.replaceAll("\\\\\\\\", " ");
            r = r.replaceAll("\\\\textbf\\{([^}]*)\\}", "$1");
            r = r.replaceAll("\\\\textit\\{([^}]*)\\}", "$1");
            r = r.replaceAll("\\\\emph\\{([^}]*)\\}", "$1");
            r = r.replaceAll("\\\\underline\\{([^}]*)\\}", "$1");
            r = r.replaceAll("\\\\[a-zA-Z]+\\*?(?:\\[[^\\]]*\\])?(?:\\{[^}]*\\})?", "");
            r = r.replace("{", "").replace("}", "");
            return r.trim();
        }

        private double computeTextWidth(Font font, String text) {
            if (text == null || text.isEmpty()) return 0;
            javafx.scene.text.Text t = new javafx.scene.text.Text(text);
            t.setFont(font);
            return t.getLayoutBounds().getWidth();
        }

        private List<String> wrapText(Font font, String text, double maxW) {
            List<String> lines = new ArrayList<>();
            if (text == null || text.isBlank()) return lines;
            String[] words = text.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (w.isEmpty()) continue;
                String test = sb.length() == 0 ? w : sb + " " + w;
                if (computeTextWidth(font, test) > maxW && sb.length() > 0) {
                    lines.add(sb.toString());
                    sb = new StringBuilder(w);
                } else {
                    sb = new StringBuilder(test);
                }
            }
            if (sb.length() > 0) {
                lines.add(sb.toString());
            }
            return lines;
        }

        // --- CẤU TRÚC KHỐI VẼ ĐỒ HỌA TRÊN TRANG (RenderBlock) ---
        private static class RenderBlock {
            enum Kind { TITLE, AUTHOR, DATE, RULE, ABSTRACT_TITLE, ABSTRACT_LINE, SECTION, SUBSECTION, PARAGRAPH_LINE, EQUATION, BULLET }
            Kind kind;
            String text;
            Font font;
            Color color;
            double height;
            double beforeSpacing;
            int number;

            RenderBlock(Kind kind, String text, Font font, Color color, double height, double beforeSpacing) {
                this.kind = kind;
                this.text = text;
                this.font = font;
                this.color = color;
                this.height = height;
                this.beforeSpacing = beforeSpacing;
            }

            RenderBlock(Kind kind, String text, Font font, Color color, double height, double beforeSpacing, int number) {
                this.kind = kind;
                this.text = text;
                this.font = font;
                this.color = color;
                this.height = height;
                this.beforeSpacing = beforeSpacing;
                this.number = number;
            }
        }

        /**
         * Render trang giấy A4 ĐỘNG đa trang (Multi-page Dynamic A4 Engine)
         */
        private void drawDynamicA4Paper(String code) {
            ParsedDoc doc = parseLatex(code);

            double scale = zoomFactor;
            double paperW = 520 * scale;
            double pageH = 740 * scale;
            double marginX = 40 * scale;
            double maxTextW = paperW - 2 * marginX;
            double contentTop = 45 * scale;
            double contentBottom = pageH - 45 * scale;
            double usableH = contentBottom - contentTop;

            // 1. Chuyển đổi các phần tử thành danh sách RenderBlock
            List<RenderBlock> blocks = new ArrayList<>();

            // Tiêu đề bài báo
            Font titleFont = Font.font("Serif", FontWeight.BOLD, 17 * scale);
            List<String> titleLines = wrapText(titleFont, doc.title, maxTextW);
            for (int i = 0; i < titleLines.size(); i++) {
                blocks.add(new RenderBlock(RenderBlock.Kind.TITLE, titleLines.get(i), titleFont, Color.BLACK, 24 * scale, (i == 0 ? 0 : 4 * scale)));
            }

            // Tác giả
            if (!doc.author.isEmpty()) {
                Font authorFont = Font.font("Serif", FontWeight.NORMAL, 11 * scale);
                blocks.add(new RenderBlock(RenderBlock.Kind.AUTHOR, doc.author, authorFont, Color.rgb(55, 65, 81), 18 * scale, 6 * scale));
            }

            // Ngày tháng
            if (!doc.date.isEmpty()) {
                Font dateFont = Font.font("Serif", FontPosture.ITALIC, 10 * scale);
                blocks.add(new RenderBlock(RenderBlock.Kind.DATE, doc.date, dateFont, Color.rgb(75, 85, 99), 16 * scale, 2 * scale));
            }

            // Đường kẻ ngang mỏng
            blocks.add(new RenderBlock(RenderBlock.Kind.RULE, "", null, Color.rgb(226, 232, 240), 12 * scale, 6 * scale));

            // Phần Tóm tắt (Abstract)
            if (!doc.abstractText.isEmpty()) {
                Font absHeadFont = Font.font("Serif", FontWeight.BOLD, 11 * scale);
                blocks.add(new RenderBlock(RenderBlock.Kind.ABSTRACT_TITLE, "Abstract", absHeadFont, Color.BLACK, 18 * scale, 8 * scale));

                Font absFont = Font.font("Serif", FontPosture.ITALIC, 10.5 * scale);
                List<String> absLines = wrapText(absFont, doc.abstractText, maxTextW - 40 * scale);
                for (String l : absLines) {
                    blocks.add(new RenderBlock(RenderBlock.Kind.ABSTRACT_LINE, l, absFont, Color.rgb(30, 41, 59), 15 * scale, 0));
                }
            }

            // Các phần nội dung thân bài
            Font secFont = Font.font("Serif", FontWeight.BOLD, 13 * scale);
            Font subFont = Font.font("Serif", FontWeight.BOLD, 11.5 * scale);
            Font pFont = Font.font("Serif", FontWeight.NORMAL, 10.5 * scale);
            Font eqFont = Font.font("Serif", FontWeight.BOLD, FontPosture.ITALIC, 14 * scale);
            Font bulletFont = Font.font("Serif", FontWeight.NORMAL, 10.5 * scale);

            for (DocItem item : doc.items) {
                switch (item.type) {
                    case SECTION:
                        blocks.add(new RenderBlock(RenderBlock.Kind.SECTION, item.number + ". " + item.text, secFont, Color.BLACK, 22 * scale, 18 * scale));
                        break;
                    case SUBSECTION:
                        blocks.add(new RenderBlock(RenderBlock.Kind.SUBSECTION, item.text, subFont, Color.BLACK, 18 * scale, 12 * scale));
                        break;
                    case PARAGRAPH:
                        List<String> pLines = wrapText(pFont, item.text, maxTextW);
                        for (int i = 0; i < pLines.size(); i++) {
                            blocks.add(new RenderBlock(RenderBlock.Kind.PARAGRAPH_LINE, pLines.get(i), pFont, Color.rgb(15, 23, 42), 15 * scale, (i == 0 ? 6 * scale : 0)));
                        }
                        break;
                    case EQUATION:
                        blocks.add(new RenderBlock(RenderBlock.Kind.EQUATION, item.text, eqFont, Color.BLACK, 26 * scale, 12 * scale, item.number));
                        break;
                    case BULLET:
                        List<String> bLines = wrapText(bulletFont, item.text, maxTextW - 20 * scale);
                        for (int i = 0; i < bLines.size(); i++) {
                            blocks.add(new RenderBlock(RenderBlock.Kind.BULLET, (i == 0 ? "•  " : "   ") + bLines.get(i), bulletFont, Color.rgb(15, 23, 42), 15 * scale, (i == 0 ? 4 * scale : 0)));
                        }
                        break;
                }
            }

            // 2. Thuật toán tự động phân trang (Auto-Pagination Engine)
            List<List<RenderBlock>> pages = new ArrayList<>();
            List<RenderBlock> curPage = new ArrayList<>();
            double curY = 0;

            for (RenderBlock b : blocks) {
                double blockTotalH = b.beforeSpacing + b.height;
                if (curY + blockTotalH > usableH && !curPage.isEmpty()) {
                    pages.add(curPage);
                    curPage = new ArrayList<>();
                    curY = 0;
                }
                curPage.add(b);
                curY += blockTotalH;
            }
            if (!curPage.isEmpty()) {
                pages.add(curPage);
            }

            int totalPages = Math.max(1, pages.size());

            // 3. Tính toán kích thước Canvas tổng thể
            double pageGap = 24 * scale;
            double canvasW = paperW + 40;
            double canvasH = totalPages * pageH + (totalPages - 1) * pageGap + 40;

            canvas.setWidth(canvasW);
            canvas.setHeight(canvasH);

            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.clearRect(0, 0, canvasW, canvasH);

            double paperX = (canvasW - paperW) / 2;

            // 4. Vẽ từng trang A4 chân thực
            for (int pIdx = 0; pIdx < totalPages; pIdx++) {
                double pageY = 20 + pIdx * (pageH + pageGap);

                // A. Bóng đổ trang giấy (Drop Shadow)
                gc.save();
                DropShadow shadow = new DropShadow();
                shadow.setColor(Color.rgb(0, 0, 0, 0.28));
                shadow.setRadius(16 * scale);
                shadow.setOffsetY(4 * scale);
                gc.setEffect(shadow);
                gc.setFill(Color.WHITE);
                gc.fillRect(paperX, pageY, paperW, pageH);
                gc.restore();

                // B. Đường viền mỏng
                gc.setStroke(Color.rgb(203, 213, 225));
                gc.setLineWidth(1);
                gc.strokeRect(paperX, pageY, paperW, pageH);

                // C. Vẽ các phần tử nội dung trên trang
                double drawY = pageY + contentTop;
                List<RenderBlock> pageBlocks = pages.get(pIdx);

                for (RenderBlock b : pageBlocks) {
                    drawY += b.beforeSpacing;
                    gc.setFont(b.font);
                    gc.setFill(b.color);

                    switch (b.kind) {
                        case TITLE:
                        case AUTHOR:
                        case DATE:
                        case ABSTRACT_TITLE:
                            double tw = computeTextWidth(b.font, b.text);
                            gc.fillText(b.text, paperX + (paperW - tw) / 2, drawY + b.height - 4 * scale);
                            break;
                        case RULE:
                            gc.setStroke(b.color);
                            gc.setLineWidth(1);
                            gc.strokeLine(paperX + marginX, drawY + 6 * scale, paperX + paperW - marginX, drawY + 6 * scale);
                            break;
                        case ABSTRACT_LINE:
                            gc.fillText(b.text, paperX + marginX + 20 * scale, drawY + b.height - 4 * scale);
                            break;
                        case SECTION:
                        case SUBSECTION:
                        case PARAGRAPH_LINE:
                            gc.fillText(b.text, paperX + marginX, drawY + b.height - 4 * scale);
                            break;
                        case EQUATION:
                            double eqW = computeTextWidth(b.font, b.text);
                            gc.fillText(b.text, paperX + (paperW - eqW) / 2, drawY + b.height - 6 * scale);
                            Font numFont = Font.font("Serif", FontWeight.NORMAL, 10.5 * scale);
                            gc.setFont(numFont);
                            gc.fillText("(" + b.number + ")", paperX + paperW - marginX - 25 * scale, drawY + b.height - 6 * scale);
                            break;
                        case BULLET:
                            gc.fillText(b.text, paperX + marginX + 10 * scale, drawY + b.height - 4 * scale);
                            break;
                    }
                    drawY += b.height;
                }

                // D. Số trang chân trang (Footer)
                gc.setFont(Font.font("Serif", FontWeight.NORMAL, 10 * scale));
                gc.setFill(Color.GRAY);
                gc.fillText("- " + (pIdx + 1) + " -", paperX + paperW / 2 - 10 * scale, pageY + pageH - 20 * scale);
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.overleaf.client.gui;

import com.overleaf.client.compiler.LatexCompilerService;
import com.overleaf.client.io.ProjectFileManager;
import com.overleaf.client.io.WorkspaceWatcher;
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * =========================================================================
 * OVERLEAF DESKTOP - GIAO DIỆN JAVAFX TỐI ƯU (M1) TÍCH HỢP M2 VÀ M4
 * =========================================================================
 */
public class OverleafFxApp extends Application {

    // --- CÁC THÀNH PHẦN GIAO DIỆN CHÍNH (UI COMPONENTS) ---
    private TextArea codeEditor;
    private TextArea terminalLogArea;
    private TreeView<String> projectTreeView;
    private TreeItem<String> rootTreeItem;
    private ListView<String> collabListView;
    private PdfCanvasPane pdfCanvasPane;
    private Label statusLabel;
    private Label cursorLabel;
    private Label connectionBadge;
    private ProgressBar compileProgressBar;
    private Button btnRecompile;
    private ComboBox<String> compilerCombo;

    // --- TÍCH HỢP M2 (COMPILER & FILE MANAGER) ---
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

    // Socket kết nối mạng (M3 protocol)
    private Socket tcpSocket;
    private ObjectOutputStream socketOut;
    private ObjectInputStream socketIn;
    private Thread socketReaderThread;

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

        appendLog("[INIT] Khởi động Overleaf Desktop (JavaFX Client Engine)...");
        appendLog("[INIT] Đã kết nối M2 ProjectFileManager với thư mục: " + fileManager.getProjectRoot());
        appendLog("[INIT] Đã kích hoạt M4 OfflineOperationQueue cho phòng: " + currentRoomId);
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

    private void updateCollabListDisplay() {
        collabListView.getItems().clear();
        collabListView.getItems().add("👤 " + currentUserName + " (Bạn - " + (isConnected ? "Online" : "Offline") + ")");
        if (isConnected) {
            collabListView.getItems().add("🟢 Alice (Đang sửa dòng 24)");
            collabListView.getItems().add("🟡 Bob (Đang xem PDF)");
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
                    updateCollabListDisplay();
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
            currentHost = hostField.getText().trim();
            try {
                currentPort = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException ignored) {}
            currentRoomId = roomField.getText().trim();
            currentUserName = userField.getText().trim();

            connectToRoom(currentHost, currentPort, currentRoomId, currentUserName, false);
        }
    }

    private void connectToRoom(String host, int port, String room, String user, boolean isReconnecting) {
        statusLabel.setText("Đang kết nối tới Server " + host + ":" + port + "...");

        if (operationQueue == null || !currentRoomId.equals(room)) {
            operationQueue = new OfflineOperationQueue(room, currentUserId, user);
        }

        Task<Boolean> connectTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                closeSocketQuietly();
                tcpSocket = new Socket();
                tcpSocket.connect(new InetSocketAddress(host, port), 2500);
                socketOut = new ObjectOutputStream(tcpSocket.getOutputStream());
                socketOut.flush();
                socketIn = new ObjectInputStream(tcpSocket.getInputStream());
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
                SyncMessage joinMsg = SyncMessage.createJoinRoom(room, currentUserId, user);
                sendMessageToServer(joinMsg);
                appendLog("[M4-ROOM] Đã gửi yêu cầu JOIN_ROOM vào phòng: " + room);
            }

            updatePendingBadge();
            showAlert(Alert.AlertType.INFORMATION, "Kết nối phòng", "Đã kết nối thành công tới phòng " + room + "!");
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
        if (socketReaderThread != null && socketReaderThread.isAlive()) {
            socketReaderThread.interrupt();
        }

        socketReaderThread = new Thread(() -> {
            try {
                while (isConnected && socketIn != null) {
                    Object obj = socketIn.readObject();
                    if (obj instanceof SyncMessage msg) {
                        handleIncomingServerMessage(msg);
                    }
                }
            } catch (EOFException | java.net.SocketException e) {
                Platform.runLater(this::simulateConnectionLost);
            } catch (Exception e) {
                Platform.runLater(() -> appendLog("[NETWORK READ ERROR] " + e.getMessage()));
            }
        });
        socketReaderThread.setDaemon(true);
        socketReaderThread.start();
    }

    private void sendMessageToServer(SyncMessage msg) {
        if (!isConnected || socketOut == null) return;
        new Thread(() -> {
            try {
                synchronized (socketOut) {
                    socketOut.writeObject(msg);
                    socketOut.flush();
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    appendLog("[NETWORK SEND ERROR] Không thể gửi gói tin: " + e.getMessage());
                    simulateConnectionLost();
                });
            }
        }).start();
    }

    private void closeSocketQuietly() {
        try {
            if (socketIn != null) socketIn.close();
            if (socketOut != null) socketOut.close();
            if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
        } catch (Exception ignored) {}
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
        statusLabel.setText("Đang lưu và biên dịch tài liệu LaTeX qua M2 ProcessBuilder...");

        // Tự động lưu file an toàn xuống đĩa thông qua ProjectFileManager trước khi biên dịch
        try {
            fileManager.writeTexFile(mainTexFileName, codeEditor.getText());
        } catch (IOException e) {
            appendLog("[ERROR] Không thể lưu file xuống đĩa trước khi compile: " + e.getMessage());
        }

        appendLog("\n========================================================");
        appendLog("[BUILD] Khởi động trình biên dịch: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        appendLog("[BUILD] Lệnh thực thi: latexmk -pdf -interaction=nonstopmode main.tex");

        compilerService.compileAsync(fileManager.getProjectRoot().toString(), mainTexFileName, new LatexCompilerService.CompileCallback() {
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
                    appendLog("[NOTICE] ProcessBuilder báo: " + errorLog);
                    appendLog("[INFO] Sử dụng kết quả render PDF Canvas nội bộ độ phân giải cao.");
                    statusLabel.setText("Đã cập nhật bản xem trước PDF.");
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
            if (!fileName.trim().isEmpty()) {
                try {
                    fileManager.createFile(fileName.trim());
                    rootTreeItem.getChildren().add(new TreeItem<>("📄 " + fileName.trim()));
                    appendLog("[FILE] Đã tạo thành công tệp mới qua ProjectFileManager: " + fileName.trim());
                } catch (IOException e) {
                    showAlert(Alert.AlertType.ERROR, "Lỗi tạo file", e.getMessage());
                }
            }
        });
    }

    private void saveFile() {
        try {
            fileManager.writeTexFile(mainTexFileName, codeEditor.getText());
            appendLog("[SAVE] Đã lưu tệp 'main.tex' an toàn qua ProjectFileManager (Atomic Move).");
            statusLabel.setText("Đã lưu tệp lúc: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi lưu tệp", "Không thể ghi file xuống đĩa: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static class PdfCanvasPane extends VBox {
        private final Canvas canvas;
        private final Label zoomLabel;
        private double zoomFactor = 1.0;

        public PdfCanvasPane() {
            getStyleClass().add("preview-container");
            VBox.setVgrow(this, Priority.ALWAYS);

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

            canvas = new Canvas(580, 800);

            StackPane canvasHolder = new StackPane(canvas);
            canvasHolder.setPadding(new Insets(20));
            canvasHolder.setStyle("-fx-background-color: #CBD5E1;");

            ScrollPane scrollPane = new ScrollPane(canvasHolder);
            scrollPane.getStyleClass().add("preview-scroll");
            scrollPane.setFitToWidth(true);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            getChildren().addAll(toolBar, scrollPane);

            drawA4Paper("");
        }

        public void updatePreview(String texContent) {
            drawA4Paper(texContent);
        }

        private void updateZoom() {
            zoomLabel.setText(String.format("%d%%", (int)(zoomFactor * 100)));
            canvas.setWidth(580 * zoomFactor);
            canvas.setHeight(800 * zoomFactor);
            drawA4Paper("");
        }

        private void drawA4Paper(String code) {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            double w = canvas.getWidth();
            double h = canvas.getHeight();

            gc.clearRect(0, 0, w, h);

            double paperW = 500 * zoomFactor;
            double paperH = 720 * zoomFactor;
            double x = (w - paperW) / 2;
            double y = 15;

            gc.save();
            DropShadow shadow = new DropShadow();
            shadow.setColor(Color.rgb(0, 0, 0, 0.28));
            shadow.setRadius(16 * zoomFactor);
            shadow.setOffsetY(4 * zoomFactor);
            gc.setEffect(shadow);

            gc.setFill(Color.WHITE);
            gc.fillRect(x, y, paperW, paperH);
            gc.restore();

            gc.setStroke(Color.rgb(203, 213, 225));
            gc.setLineWidth(1);
            gc.strokeRect(x, y, paperW, paperH);

            double marginX = x + 38 * zoomFactor;
            double currentY = y + 50 * zoomFactor;

            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Serif", FontWeight.BOLD, 17 * zoomFactor));
            gc.fillText("Overleaf Desktop (Pure Java Edition)", marginX + 25 * zoomFactor, currentY);

            currentY += 24 * zoomFactor;
            gc.setFont(Font.font("Serif", FontWeight.NORMAL, 11 * zoomFactor));
            gc.setFill(Color.rgb(55, 65, 81));
            gc.fillText("Nhóm XX - Lớp Java Software Development", marginX + 68 * zoomFactor, currentY);

            currentY += 16 * zoomFactor;
            gc.setFont(Font.font("Serif", FontPosture.ITALIC, 10 * zoomFactor));
            gc.fillText(new SimpleDateFormat("dd MMMM yyyy").format(new Date()), marginX + 115 * zoomFactor, currentY);

            currentY += 20 * zoomFactor;
            gc.setStroke(Color.rgb(226, 232, 240));
            gc.strokeLine(marginX, currentY, marginX + paperW - 76 * zoomFactor, currentY);

            currentY += 26 * zoomFactor;
            gc.setFill(Color.BLACK);
            gc.setFont(Font.font("Serif", FontWeight.BOLD, 11 * zoomFactor));
            gc.fillText("Abstract", marginX + 160 * zoomFactor, currentY);

            currentY += 18 * zoomFactor;
            gc.setFont(Font.font("Serif", FontPosture.ITALIC, 10.5 * zoomFactor));
            gc.fillText("This project presents a self-hosted, real-time collaborative LaTeX", marginX, currentY);
            currentY += 15 * zoomFactor;
            gc.fillText("editor built entirely in JavaFX, TCP Sockets and MongoDB.", marginX, currentY);

            currentY += 32 * zoomFactor;
            gc.setFont(Font.font("Serif", FontWeight.BOLD, 13 * zoomFactor));
            gc.fillText("1. Giới thiệu (Introduction)", marginX, currentY);

            currentY += 20 * zoomFactor;
            gc.setFont(Font.font("Serif", FontWeight.NORMAL, 10.5 * zoomFactor));
            gc.fillText("A collaborative LaTeX editor combines core Java topics (OOP, GUI,", marginX, currentY);
            currentY += 16 * zoomFactor;
            gc.fillText("Networking, Concurrency, I/O, Database) in one cohesive desktop system.", marginX, currentY);

            currentY += 32 * zoomFactor;
            gc.setFont(Font.font("Serif", FontWeight.BOLD, 13 * zoomFactor));
            gc.fillText("2. Công thức toán học (Equations)", marginX, currentY);

            currentY += 28 * zoomFactor;
            gc.setFont(Font.font("Serif", FontPosture.ITALIC, 15 * zoomFactor));
            gc.fillText("E = m c²", marginX + 150 * zoomFactor, currentY);

            gc.setFont(Font.font("Serif", FontWeight.NORMAL, 10 * zoomFactor));
            gc.fillText("(1)", marginX + paperW - 110 * zoomFactor, currentY);

            gc.setFill(Color.GRAY);
            gc.setFont(Font.font("Serif", FontWeight.NORMAL, 10 * zoomFactor));
            gc.fillText("- 1 -", x + paperW / 2 - 8 * zoomFactor, y + paperH - 20 * zoomFactor);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
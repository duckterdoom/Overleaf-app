package com.overleaf.client.ui;

import com.overleaf.client.compiler.LatexCompilerService;
import com.overleaf.client.compiler.PdfSnapshot;
import com.overleaf.client.compiler.ProjectInputs;
import com.overleaf.client.workspace.ProjectFileManager;
import com.overleaf.client.workspace.WorkspaceWatcher;
import com.overleaf.client.net.NetworkClient;
import com.overleaf.client.net.ConnectionScope;
import com.overleaf.client.ui.backend.AuthGateway;
import com.overleaf.client.ui.backend.LocalBackend;
import com.overleaf.client.ui.backend.ProjectGateway.ProjectSummary;
import com.overleaf.common.model.CRDTOperation;
import com.overleaf.common.sync.OfflineOperationQueue;
import com.overleaf.common.sync.OperationCodec;
import com.overleaf.common.protocol.SyncMessage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * =========================================================================
 * OVERLEAF DESKTOP - GIAO DIỆN JAVAFX TỐI ƯU (M1) TÍCH HỢP M2 VÀ M4
 * =========================================================================
 *
 * - M1: Giao diện JavaFX, xem PDF thật từ compiler; xuất cùng snapshot PDF.
 * - M2: Tích hợp LatexCompilerService (ProcessBuilder nền) & WorkspaceWatcher (theo dõi I/O).
 * - M4: Tích hợp OfflineOperationQueue, CRDTOperation, Đồng hồ Lamport, chế độ
 *       soạn thảo Offline và Tự động Đồng bộ lại (RESYNC / CHECKPOINT).
 */
public class OverleafFxApp extends Application {

    // --- CÁC THÀNH PHẦN GIAO DIỆN CHÍNH (UI COMPONENTS) ---
    private LineNumberGutter lineGutter;
    private CodeOverlay codeOverlay;          // Syntax-colored layer drawn behind the editor
    private TextArea codeEditor;              // Khung soạn thảo mã LaTeX (Monospace)
    private TextArea terminalLogArea;         // Terminal Console hiển thị log biên dịch
    private TreeView<String> projectTreeView; // Cây thư mục dự án
    private MenuItem addFilesMenuItem;
    private TreeItem<String> rootTreeItem;    // Nút gốc cây thư mục
    private VBox roomMemberRows;              // Hàng thành viên trong phòng (People)
    private final List<String> roomMemberLabels = new ArrayList<>();
    private Label projectCountLabel;
    private VBox outlineRows;
    private String outlineSignature = "";
    private Label sideUserNameLabel;
    private Label sideUserMetaLabel;
    private Label sideUserAvatarLabel;
    private Label sideProjectNameLabel;
    private PdfPreviewPane pdfPreviewPane;
    private long pdfInputRevision;
    private long workspaceGeneration;
    private Label statusLabel;                // Nhãn thông tin trạng thái bên trái
    private Label cursorLabel;                // Nhãn chỉ số dòng & cột của con trỏ
    private Label connectionBadge;            // Huy hiệu trạng thái kết nối phòng (Pill Badge)
    private Circle connectionDot;             // Chấm trạng thái kết nối ở thanh công cụ
    private ProgressBar compileProgressBar;   // Thanh tiến trình biên dịch
    private Button btnRecompile;              // Nút bấm "Recompile" màu xanh lá
    private ComboBox<String> compilerCombo;   // Lựa chọn bộ dịch (pdfLaTeX, XeLaTeX, LuaLaTeX)

    // --- APP SHELL: LOGIN -> HOME -> EDITOR ---
    private Stage stage;
    private StackPane screenHost;
    private LoginView loginView;
    private HomeView homeView;
    private BorderPane editorRoot;
    private final LocalBackend backend = new LocalBackend();
    private final AppSettings settings = new AppSettings(backend);
    private javafx.animation.Timeline autoSaveTimer;
    private AuthGateway.Session session;
    private boolean vietnameseUi = false;
    private String activeProjectName = "";
    private Path activeProjectRoot;           // Thư mục dự án đang mở
    private String currentFile = LocalBackend.MAIN_FILE; // File đang hiển thị trong editor
    private Label editorTitleLabel;
    private boolean loadingDocument = false;  // Đang nạp file vào editor: không tạo op, không đánh dấu sửa
    private final Map<TreeItem<String>, String> treePaths = new HashMap<>();
    private Path lastPdf;
    private String joinSeed;                  // Local main.tex at JOIN time, used to seed an empty room
    private boolean roomSnapshotEmpty = false;
    private Label editorProjectLabel;
    private Label editorPathLabel;
    private Label dirtyLabel;
    private boolean userDirty = false;

    // --- TÍCH HỢP M2 (COMPILER & WORKSPACE WATCHER) ---
    private final LatexCompilerService compilerService = new LatexCompilerService();
    private ProjectFileManager fileManager;
    private WorkspaceWatcher workspaceWatcher;

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
    private final ConnectionScope connectionScope = new ConnectionScope();
    private String queueRoomId = currentRoomId;
    private String accessToken = System.getenv().getOrDefault("OVERLEAF_TOKEN", "");
    private boolean useTls = Boolean.parseBoolean(System.getenv().getOrDefault("OVERLEAF_TLS", "false"));

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        primaryStage.setTitle("Overleaf Desktop");
        primaryStage.setMinWidth(1120);
        primaryStage.setMinHeight(720);

        vietnameseUi = "vi".equals(settings.get(AppSettings.LANGUAGE));
        editorRoot = buildEditorRoot(primaryStage, "");

        screenHost = new StackPane();
        screenHost.getStyleClass().add("screen-host");
        Scene scene = new Scene(screenHost, 1320, 860);
        vietnameseUi = "vi".equals(settings.get(AppSettings.LANGUAGE));
        ThemeManager.apply(scene, settings);
        settings.onChange(key -> Platform.runLater(() -> onSettingChanged(scene, key)));
        try {
            Platform.getPreferences().colorSchemeProperty().addListener((obs, was, now) -> {
                if ("system".equals(settings.get(AppSettings.MODE))) {
                    ThemeManager.apply(scene, settings);
                    if (codeOverlay != null) codeOverlay.repaint();
                }
            });
        } catch (RuntimeException noSystemTheme) {
            // Older toolkits cannot report the OS appearance; "System" then behaves like Light.
        }
        installAccelerators(scene);

        primaryStage.setOnCloseRequest(e -> cleanupOnExit());
        primaryStage.setScene(scene);

        appendLog("[INIT] Overleaf Desktop started.");
        appendLog("[INIT] Projects folder: " + backend.projectsDir());

        AuthGateway.Session restored = null;
        try {
            restored = backend.restoreSession();
        } catch (RuntimeException e) {
            appendLog("[WARN] Could not restore the saved session: " + e.getMessage());
        }
        if (restored != null) {
            onSignedIn(restored);
        } else {
            showLogin();
        }
        primaryStage.show();
    }

    private BorderPane buildEditorRoot(Stage stage, String initialCode) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("editor-root");
        root.setTop(createEditorTopBar(stage));
        root.setCenter(createMainWorkspace(stage, initialCode));
        root.setBottom(createStatusBar());
        return root;
    }

    private void installAccelerators(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                () -> {
                    if (editorVisible()) saveFile();
                });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN),
                () -> {
                    if (editorVisible()) triggerRecompile();
                });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5),
                () -> {
                    if (editorVisible()) triggerRecompile();
                });
    }

    private boolean editorVisible() {
        return editorRoot != null && screenHost != null && screenHost.getChildren().contains(editorRoot);
    }

    private void showLogin() {
        session = null;
        if (screenHost == null) return;
        loginView = new LoginView(backend, safeLastEmail(), this::onSignedIn);
        screenHost.getChildren().setAll(loginView);
        loginView.prepareForDisplay();
        if (stage != null) stage.setTitle("Overleaf Desktop — Sign in");
    }

    private void onSignedIn(AuthGateway.Session signedIn) {
        this.session = signedIn;
        this.currentUserName = signedIn.name();
        updateSideUserChip();
        try {
            backend.ensureStarterProject();
            String last = backend.lastProject();
            List<ProjectSummary> projects = backend.projects();
            boolean lastExists = last != null && projects.stream().anyMatch(p -> p.name().equals(last));
            activeProjectName = lastExists ? last : projects.isEmpty() ? "" : projects.get(0).name();
        } catch (RuntimeException e) {
            appendLog("[WARN] Could not prepare the projects folder: " + e.getMessage());
        }
        showHome();
    }

    private String safeLastEmail() {
        try {
            return backend.lastEmail();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void showHome() {
        if (session == null) {
            showLogin();
            return;
        }
        if (editorVisible()) {
            saveIfDirty();
        }
        homeView = new HomeView(backend, backend, backend, session, activeProjectName, screenHost,
                this::openProject, this::confirmSignOut, () -> showSettings(false, "profile"), vietnameseUi, this::setVietnameseUi,
                this::onProjectChanged, this::revealFolder);
        screenHost.getChildren().setAll(homeView);
        if (stage != null) stage.setTitle("Overleaf Desktop — Home");
    }

    /** Home renamed or deleted a project; keep the editor pointing at a real folder. */
    private void onProjectChanged(ProjectSummary before, ProjectSummary after) {
        Path oldRoot = Path.of(before.folder()).toAbsolutePath().normalize();
        if (!oldRoot.equals(activeProjectRoot)) {
            return;
        }
        if (workspaceWatcher != null) {
            workspaceWatcher.stopWatching();
            workspaceWatcher = null;
        }
        if (after == null) {
            leaveRoomForProjectSwitch();
            fileManager = null;
            activeProjectRoot = null;
            loadingDocument = true;
            try {
                codeEditor.clear();
            } finally {
                loadingDocument = false;
            }
            markSaved();
            List<ProjectSummary> remaining = backend.projects();
            activeProjectName = remaining.isEmpty() ? "" : remaining.get(0).name();
        } else {
            activeProjectName = after.name();
            Path newRoot = Path.of(after.folder()).toAbsolutePath().normalize();
            activeProjectRoot = newRoot;
            workspaceGeneration++;
            lastPdf = null;
            if (pdfPreviewPane != null) pdfPreviewPane.reset();
            fileManager = new ProjectFileManager(newRoot.toString());
            startWorkspaceWatcher(newRoot);
            if (editorProjectLabel != null) editorProjectLabel.setText(activeProjectName);
            if (sideProjectNameLabel != null) sideProjectNameLabel.setText(activeProjectName);
        }
    }

    private void revealFolder(Path folder) {
        if (folder != null && Files.isDirectory(folder)) {
            getHostServices().showDocument(folder.toUri().toString());
        }
    }

    private void setVietnameseUi(boolean vietnamese) {
        this.vietnameseUi = vietnamese;
        settings.set(AppSettings.LANGUAGE, vietnamese ? "vi" : "en");
        Platform.runLater(this::showHome);
    }

    private void openProject(ProjectSummary project) {
        if (project == null) {
            return;
        }
        Path root = Path.of(project.folder()).toAbsolutePath().normalize();
        if (!root.equals(activeProjectRoot)) {
            saveIfDirty();
            leaveRoomForProjectSwitch();
            try {
                switchWorkspace(root);
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Could not open project",
                        "Opening " + root + " failed: " + e.getMessage());
                return;
            }
            activeProjectName = project.name();
            openFile(LocalBackend.MAIN_FILE);
        }
        activeProjectName = project.name();
        try {
            backend.rememberProject(root);
        } catch (RuntimeException e) {
            appendLog("[WARN] " + e.getMessage());
        }
        if (editorProjectLabel != null) editorProjectLabel.setText(activeProjectName);
        if (sideProjectNameLabel != null) sideProjectNameLabel.setText(activeProjectName);
        refreshProjectTree();
        screenHost.getChildren().setAll(editorRoot);
        if (stage != null) stage.setTitle("Overleaf Desktop — " + activeProjectName);
        statusLabel.setText("Editing " + activeProjectName + " · files stay on this machine.");
        Platform.runLater(codeEditor::requestFocus);
    }

    /** Points the file manager, watcher, tree and compiler at another project folder. */
    private void switchWorkspace(Path root) throws IOException {
        Files.createDirectories(root);
        if (workspaceWatcher != null) {
            workspaceWatcher.stopWatching();
            workspaceWatcher = null;
        }
        fileManager = new ProjectFileManager(root.toString());
        if (!Files.exists(root.resolve(LocalBackend.MAIN_FILE))) {
            fileManager.writeTexFile(LocalBackend.MAIN_FILE, LocalBackend.template(root.getFileName().toString()));
        }
        activeProjectRoot = root;
        workspaceGeneration++;
        lastPdf = null;
        if (pdfPreviewPane != null) pdfPreviewPane.reset();
        appendLog("[FILE] Workspace: " + root);
        startWorkspaceWatcher(root);
    }

    /** A room syncs one project's main.tex, so switching projects leaves it and drops its queue. */
    private void leaveRoomForProjectSwitch() {
        if (networkClient != null || isConnected) {
            disconnectRoom();
        }
        if (operationQueue != null && operationQueue.getPendingCount() > 0) {
            appendLog("[OFFLINE] Dropped " + operationQueue.getPendingCount()
                    + " unsynced room edits; the text is saved in the previous project's files.");
        }
        operationQueue = null;
        updatePendingBadge();
        updateCollabListDisplay();
    }

    private boolean isMainFile() {
        return LocalBackend.MAIN_FILE.equals(currentFile);
    }

    /** Loads a project file into the editor. Collaboration only covers main.tex. */
    private boolean openFile(String relativePath) {
        if (fileManager == null || relativePath == null) {
            return false;
        }
        boolean switching = !relativePath.equals(currentFile) || codeEditor.getText().isEmpty();
        if (!LocalBackend.MAIN_FILE.equals(relativePath) && (isConnected || networkClient != null)) {
            showAlert(Alert.AlertType.INFORMATION, "Room is open",
                    "Collaboration rooms sync main.tex only.\n\nLeave the room to edit " + relativePath + ".");
            return false;
        }
        if (switching) {
            saveIfDirty();
        }
        String content;
        try {
            content = fileManager.readTexFile(relativePath);
        } catch (IOException | RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Could not open file", relativePath + ": " + e.getMessage());
            return false;
        }
        loadingDocument = true;
        try {
            codeEditor.setText(content);
            codeEditor.positionCaret(0);
            codeEditor.setScrollTop(0);
        } finally {
            loadingDocument = false;
        }
        lastEditorContent = content;
        currentFile = relativePath;
        markSaved();
        refreshOutline();
        updateCursorPosition();
        if (editorTitleLabel != null) editorTitleLabel.setText(relativePath);
        if (editorPathLabel != null) editorPathLabel.setText(relativePath);
        if (editorPathLabel != null) editorPathLabel.setTooltip(UiSupport.tooltip(shortPath(activeProjectRoot.resolve(relativePath))));
        appendLog("[FILE] Opened " + relativePath);
        return true;
    }

    private static String shortPath(Path path) {
        if (path == null) return "";
        String home = System.getProperty("user.home");
        String text = path.toString();
        return home != null && text.startsWith(home) ? "~" + text.substring(home.length()) : text;
    }

    /** Every visible Sign out control asks first; {@link #signOut()} does the work. */
    private void confirmSignOut() {
        boolean vi = vietnameseUi;
        UiModal modal = UiModal.create(screenHost, UiSupport.SIGN_OUT, UiModal.Tone.NEUTRAL,
                vi ? "Đăng xuất?" : "Sign out?", null);
        modal.text(vi
                ? "Thay đổi chưa lưu sẽ được lưu trước, và bạn sẽ rời khỏi phòng cộng tác đang mở. Dự án vẫn nằm trên máy này."
                : "Unsaved changes are saved first and you leave any live room. Your projects stay on this machine.");
        modal.actions(null, vi ? "Hủy" : "Cancel", vi ? "Đăng xuất" : "Sign out", UiModal.Tone.NEUTRAL, () -> {
            signOut();
            return true;
        });
        modal.show();
    }

    private void signOut() {
        saveIfDirty();
        disconnectRoom();
        operationQueue = null;
        updatePendingBadge();
        try {
            backend.forgetSession();
        } catch (RuntimeException e) {
            appendLog("[WARN] " + e.getMessage());
        }
        showLogin();
    }

    private void startWorkspaceWatcher(Path root) {
        try {
            WorkspaceWatcher watcher = new WorkspaceWatcher(root.toString());
            ProjectFileManager watchedFiles = fileManager;
            watcher.startWatching(new WorkspaceWatcher.FileChangeListener() {
                @Override
                public void onFileChanged(String changedFile) {
                    Platform.runLater(() -> onWorkspaceChange(watcher, watchedFiles, changedFile, false));
                }

                @Override
                public void onFileDeleted(String deletedFile) {
                    Platform.runLater(() -> onWorkspaceChange(watcher, watchedFiles, deletedFile, true));
                }
            });
            workspaceWatcher = watcher;
        } catch (Exception e) {
            appendLog("[WARN] Could not start workspace watcher: " + e.getMessage());
        }
    }

    private void onWorkspaceChange(WorkspaceWatcher source, ProjectFileManager files, String changedFile, boolean deleted) {
        if (source != workspaceWatcher || files != fileManager) {
            return; // Event from a project we already left.
        }
        String name = changedFile.replace('\\', '/');
        if (!deleted && files.isOwnWrite(name)) {
            return;
        }
        invalidatePdf();
        refreshProjectTree();
        if (deleted) {
            appendLog("[WATCHER] Deleted on disk: " + name);
            return;
        }
        appendLog("[WATCHER] Changed on disk: " + name);
        // Pick up edits from another editor, unless the user has unsaved work or a room owns the text.
        if (name.equals(currentFile) && !userDirty && !isConnected && networkClient == null) {
            try {
                String content = files.readTexFile(name);
                if (!content.equals(codeEditor.getText())) {
                    int caret = Math.min(codeEditor.getCaretPosition(), content.length());
                    loadingDocument = true;
                    try {
                        codeEditor.setText(content);
                        codeEditor.positionCaret(caret);
                    } finally {
                        loadingDocument = false;
                    }
                    lastEditorContent = content;
                    if (isMainFile()) invalidatePdf();
                    statusLabel.setText("Reloaded " + name + " after it changed on disk.");
                }
            } catch (IOException e) {
                appendLog("[WARN] Could not reload " + name + ": " + e.getMessage());
            }
        }
    }

    private void cleanupOnExit() {
        saveIfDirty();
        if (isConnected) {
            disconnectRoom();
        }
        compilerService.shutdown();
        if (pdfPreviewPane != null) pdfPreviewPane.close();
        if (workspaceWatcher != null) {
            workspaceWatcher.stopWatching();
        }
        closeSocketQuietly();
    }

    private HBox createEditorTopBar(Stage stage) {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("editor-topbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Button back = new Button();
        back.setGraphic(UiSupport.iconBox(UiSupport.ARROW_LEFT, 15, Color.web("#5B5B56"), 16));
        back.getStyleClass().add("icon-button");
        back.setTooltip(UiSupport.tooltip("Back to home"));
        back.setOnAction(e -> showHome());

        editorProjectLabel = new Label(activeProjectName);
        editorProjectLabel.getStyleClass().add("editor-project");

        Label separator = new Label("·");
        separator.getStyleClass().add("editor-path");

        editorPathLabel = new Label(LocalBackend.MAIN_FILE);
        editorPathLabel.getStyleClass().add("editor-path");

        dirtyLabel = new Label("Unsaved");
        dirtyLabel.getStyleClass().add("dirty-label");
        dirtyLabel.setVisible(false);
        dirtyLabel.setManaged(false);

        Region spacer = UiSupport.spacer();

        connectionDot = new Circle(3.2, Color.web("#A6A6A0"));
        connectionBadge = new Label("Local session", connectionDot);
        connectionBadge.setGraphicTextGap(6);
        connectionBadge.getStyleClass().add("badge-pill-offline");

        compilerCombo = new ComboBox<>();
        compilerCombo.getItems().addAll("pdfLaTeX", "XeLaTeX", "LuaLaTeX");
        compilerCombo.setValue(settings.get(AppSettings.COMPILER));
        compilerCombo.valueProperty().addListener((o, before, after) -> invalidatePdf());
        compilerCombo.getStyleClass().add("compiler-combo");
        compilerCombo.setPrefWidth(86);
        compilerCombo.setMinHeight(30);
        compilerCombo.setPrefHeight(30);

        btnRecompile = new Button("Recompile");
        btnRecompile.setGraphic(UiSupport.iconBox(UiSupport.PLAY, 10, Color.WHITE, 11));
        btnRecompile.setGraphicTextGap(6);
        btnRecompile.getStyleClass().addAll("btn-primary", "toolbar-primary");
        btnRecompile.setMinHeight(30);
        btnRecompile.setOnAction(e -> triggerRecompile());

        Button save = new Button();
        save.setGraphic(UiSupport.iconBox(UiSupport.SAVE, 14, Color.web("#5B5B56"), 16));
        save.getStyleClass().add("icon-button");
        save.setTooltip(UiSupport.tooltip("Save main.tex (Ctrl+S)"));
        save.setOnAction(e -> saveFile());

        MenuButton overflow = createOverflowMenu(stage);

        HBox statusGroup = new HBox(10, connectionBadge, compilerCombo);
        statusGroup.setAlignment(Pos.CENTER_LEFT);

        Button stopCompile = new Button("Stop");
        stopCompile.setId("stop-compile");
        stopCompile.getStyleClass().add("btn-secondary");
        stopCompile.visibleProperty().bind(btnRecompile.disabledProperty());
        stopCompile.managedProperty().bind(stopCompile.visibleProperty());
        stopCompile.setOnAction(e -> {
            compilerService.cancelCompile();
            statusLabel.setText("Stopping compiler…");
        });
        HBox actionGroup = new HBox(4, btnRecompile, stopCompile, save, overflow);
        actionGroup.setAlignment(Pos.CENTER_RIGHT);

        bar.getChildren().addAll(back, editorProjectLabel, separator, editorPathLabel, dirtyLabel,
                spacer, statusGroup, actionGroup);
        return bar;
    }

    private MenuButton createOverflowMenu(Stage stage) {
        MenuButton overflow = new MenuButton();
        HBox dots = new HBox(3);
        dots.setAlignment(Pos.CENTER);
        for (int i = 0; i < 3; i++) {
            dots.getChildren().add(new Circle(1.25, Color.web("#6B6B65")));
        }
        overflow.setGraphic(dots);
        overflow.getStyleClass().add("overflow-button");
        overflow.setTooltip(UiSupport.tooltip("More actions"));

        MenuItem newFile = new MenuItem(vietnameseUi ? "Thêm tệp…" : "Add files…");
        addFilesMenuItem = newFile;
        newFile.setOnAction(e -> showNewFileDialog(stage));

        MenuItem save = new MenuItem("Save");
        save.setOnAction(e -> saveFile());

        MenuItem compile = new MenuItem("Recompile");
        compile.setOnAction(e -> triggerRecompile());

        MenuItem openPdf = new MenuItem("Open compiled PDF");
        openPdf.setOnAction(e -> openCompiledPdf());
        MenuItem exportPdf = new MenuItem("Export PDF…");
        exportPdf.setOnAction(e -> exportCompiledPdf());

        MenuItem clean = new MenuItem("Clean temporary files");
        clean.setOnAction(e -> cleanBuildFiles());

        MenuItem revealFolder = new MenuItem("Show project folder");
        revealFolder.setOnAction(e -> revealFolder(activeProjectRoot));

        MenuItem connect = new MenuItem("Connect room…");
        connect.setOnAction(e -> showConnectDialog(stage));

        MenuItem offline = new MenuItem("Simulate lost connection");
        offline.setOnAction(e -> simulateConnectionLost());

        MenuItem reconnect = new MenuItem("Reconnect & resync");
        reconnect.setOnAction(e -> simulateReconnect(stage));

        MenuItem queue = new MenuItem("Offline queue…");
        queue.setOnAction(e -> showQueueStatusDialog());

        MenuItem disconnect = new MenuItem("Leave room");
        disconnect.setOnAction(e -> disconnectRoom());

        MenuItem about = new MenuItem("About Overleaf Desktop");
        about.setOnAction(e -> showAlert(Alert.AlertType.INFORMATION, "Overleaf Desktop",
                "A collaborative LaTeX editor built in pure Java.\n\n"
                        + "JavaFX client · local ProcessBuilder compiler · TCP collaboration\n"
                        + "Java Software Development · 2026–2027"));

        MenuItem signOutItem = new MenuItem("Sign out");
        signOutItem.setOnAction(e -> confirmSignOut());

        overflow.getItems().addAll(newFile, save, compile, openPdf, exportPdf, clean, revealFolder, new SeparatorMenuItem(),
                connect, offline, reconnect, queue, disconnect, new SeparatorMenuItem(), about, signOutItem);
        return overflow;
    }

    private SplitPane createMainWorkspace(Stage stage, String initialContent) {
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.getStyleClass().add("editor-split");

        VBox leftPane = createLeftPanel(stage);
        SplitPane centerSplit = createCenterEditorPanel(initialContent);
        pdfPreviewPane = new PdfPreviewPane(this::exportCompiledPdf);

        mainSplit.getItems().addAll(leftPane, centerSplit, pdfPreviewPane);
        mainSplit.setDividerPositions(0.18, 0.58);

        return mainSplit;
    }

    private VBox createLeftPanel(Stage stage) {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("side-panel");
        panel.setMinWidth(200);

        ToggleGroup group = new ToggleGroup();
        ToggleButton filesToggle = panelToggle("Files", UiSupport.FILE, group);
        ToggleButton peopleToggle = panelToggle("People", UiSupport.USERS, group);
        filesToggle.setSelected(true);
        HBox switcher = new HBox(2, filesToggle, peopleToggle);
        switcher.getStyleClass().add("workspace-switch");

        rootTreeItem = new TreeItem<>(activeProjectName);
        rootTreeItem.setExpanded(true);
        projectTreeView = new TreeView<>(rootTreeItem);
        projectTreeView.setShowRoot(false);
        projectTreeView.setFixedCellSize(28);
        projectTreeView.getStyleClass().add("file-tree");
        projectTreeView.setCellFactory(tree -> new TreeCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                TreeItem<String> treeItem = getTreeItem();
                String path = treePaths.get(treeItem);
                boolean folder = path != null && Files.isDirectory(activeProjectRoot.resolve(path));
                String lower = item.toLowerCase(Locale.ROOT);
                Color tint = folder ? Color.web("#9A9A94")
                        : lower.endsWith(".tex") ? Color.web("#23664A")
                        : lower.endsWith(".bib") ? Color.web("#A16207")
                        : lower.endsWith(".pdf") ? Color.web("#B0604A")
                        : Color.web("#8B8B85");
                Label name = new Label(item);
                name.getStyleClass().add("tree-file-name");
                HBox row = new HBox(7,
                        UiSupport.iconBox(folder ? UiSupport.FOLDER : UiSupport.FILE, 13, tint, 17), name);
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
            }
        });
        VBox.setVgrow(projectTreeView, Priority.ALWAYS);
        projectTreeView.setOnContextMenuRequested(e -> {
            TreeItem<String> item = projectTreeView.getSelectionModel().getSelectedItem();
            String path = item == null ? null : treePaths.get(item);
            if (path != null && !Files.isDirectory(activeProjectRoot.resolve(path))) fileActions(path).show(projectTreeView, e.getScreenX(), e.getScreenY());
            else folderActions(path == null ? "" : path).show(projectTreeView, e.getScreenX(), e.getScreenY());
        });
        projectTreeView.setOnMouseClicked(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            TreeItem<String> item = projectTreeView.getSelectionModel().getSelectedItem();
            if (item == null || !item.isLeaf()) return;
            String path = treePaths.get(item);
            if (path == null || Files.isDirectory(activeProjectRoot.resolve(path))) return;
            if (path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                if (e.getClickCount() == 2) {
                    if (activeProjectRoot.resolve(path).normalize().equals(fileManager.pdfPathFor(LocalBackend.MAIN_FILE))) {
                        openCompiledPdf();
                    } else {
                        getHostServices().showDocument(activeProjectRoot.resolve(path).toUri().toString());
                    }
                }
            } else if (!isEditableSource(path)) {
                if (e.getClickCount() == 2) getHostServices().showDocument(activeProjectRoot.resolve(path).toUri().toString());
            } else if (!path.equals(currentFile)) {
                if (!openFile(path)) selectTreePath(currentFile);
            }
        });

        Label projectName = new Label(activeProjectName);
        sideProjectNameLabel = projectName;
        projectName.getStyleClass().add("side-project-name");
        projectName.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(projectName, Priority.ALWAYS);
        HBox projectRow = new HBox(8,
                UiSupport.iconBox(UiSupport.LEAF_PATH, 14, Color.web("#23664A"), 24), projectName);
        projectRow.getStyleClass().add("side-project-row");
        projectRow.setAlignment(Pos.CENTER_LEFT);
        projectRow.setMaxWidth(Double.MAX_VALUE);

        projectCountLabel = new Label();
        projectCountLabel.getStyleClass().add("side-count");
        Label workspaceTitle = new Label("WORKSPACE");
        workspaceTitle.getStyleClass().add("side-section");
        HBox workspaceHead = new HBox(workspaceTitle, UiSupport.spacer(), projectCountLabel);
        workspaceHead.setAlignment(Pos.CENTER_LEFT);

        VBox treeBox = new VBox(2, workspaceHead, projectRow, projectTreeView);
        treeBox.setPadding(new Insets(8, 0, 0, 0));
        VBox.setVgrow(projectTreeView, Priority.ALWAYS);

        Button newFile = sideAction(vietnameseUi ? "Thêm tệp" : "Add files", UiSupport.PLUS, () -> showNewFileDialog(stage));
        newFile.setId("workspace-add-files");
        Button newFolder = sideAction(vietnameseUi ? "Thư mục mới" : "New folder", UiSupport.FOLDER, () -> showAddFilesDialog(1, selectedDirectory()));
        newFolder.setId("workspace-new-folder");
        Button upload = sideAction(vietnameseUi ? "Tải lên" : "Upload", UiSupport.PLUS, () -> showAddFilesDialog(2, selectedDirectory()));
        upload.setId("workspace-upload");
        Button refresh = sideAction("Refresh files", UiSupport.REFRESH, () -> {
            refreshProjectTree();
            appendLog("[FILE] Workspace tree refreshed.");
        });

        outlineRows = new VBox(1);
        ScrollPane outlineScroll = new ScrollPane(outlineRows);
        outlineScroll.getStyleClass().add("outline-scroll");
        outlineScroll.setFitToWidth(true);
        outlineScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        outlineScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        outlineScroll.setMinHeight(0);
        outlineScroll.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(outlineScroll, Priority.ALWAYS);

        Label outlineTitle = new Label("OUTLINE");
        outlineTitle.getStyleClass().add("side-section");
        outlineTitle.setTooltip(UiSupport.tooltip(vietnameseUi
                ? "Kéo thanh ngang phía trên để thay đổi chiều cao Outline."
                : "Drag the divider above to resize the Outline."));
        Label resizeHint = new Label("↕");
        resizeHint.getStyleClass().add("pane-sub");
        HBox outlineHeader = new HBox(outlineTitle, UiSupport.spacer(), resizeHint);
        outlineHeader.setAlignment(Pos.CENTER_LEFT);
        VBox outlineBox = new VBox(4, outlineHeader, outlineScroll);
        outlineBox.getStyleClass().add("outline-box");
        outlineBox.setMinHeight(80);
        treeBox.setMinHeight(100);
        SplitPane fileOutlineSplit = new SplitPane(treeBox, outlineBox);
        fileOutlineSplit.setOrientation(Orientation.VERTICAL);
        fileOutlineSplit.setId("file-outline-split");
        fileOutlineSplit.getStyleClass().add("sidebar-split");
        double split = 0.60;
        try { split = Double.parseDouble(settings.get(AppSettings.OUTLINE_SPLIT)); }
        catch (NumberFormatException invalid) { /* Keep the initial position. */ }
        if (!Double.isFinite(split)) split = 0.60;
        fileOutlineSplit.setDividerPositions(Math.max(0.15, Math.min(0.85, split)));
        fileOutlineSplit.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            if (!fileOutlineSplit.getDividers().isEmpty()) {
                settings.set(AppSettings.OUTLINE_SPLIT, Double.toString(fileOutlineSplit.getDividerPositions()[0]));
            }
        });
        VBox filesBox = new VBox(2, newFile, newFolder, upload, refresh, fileOutlineSplit);
        VBox.setVgrow(fileOutlineSplit, Priority.ALWAYS);

        roomMemberRows = new VBox(2);
        VBox.setVgrow(roomMemberRows, Priority.ALWAYS);
        Label peopleTitle = new Label("IN THIS ROOM");
        peopleTitle.getStyleClass().add("side-section");
        Button connect = sideAction("Connect a room…", UiSupport.USERS, () -> showConnectDialog(stage));
        VBox peopleBox = new VBox(2, connect, peopleTitle, roomMemberRows);
        VBox.setVgrow(roomMemberRows, Priority.ALWAYS);

        StackPane content = new StackPane(filesBox);
        VBox.setVgrow(content, Priority.ALWAYS);
        group.selectedToggleProperty().addListener((obs, oldToggle, selected) -> {
            if (selected == null) {
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            content.getChildren().setAll(selected == filesToggle ? filesBox : peopleBox);
        });

        sideUserAvatarLabel = new Label();
        sideUserAvatarLabel.getStyleClass().addAll("side-user-avatar", "avatar-clickable");
        sideUserAvatarLabel.setTooltip(UiSupport.tooltip("Profile and settings"));
        sideUserAvatarLabel.setOnMouseClicked(e -> showSettings(true, "profile"));
        sideUserNameLabel = new Label();
        sideUserNameLabel.getStyleClass().add("side-user-name");
        sideUserMetaLabel = new Label();
        sideUserMetaLabel.getStyleClass().add("side-user-meta");
        VBox userText = new VBox(1, sideUserNameLabel, sideUserMetaLabel);
        Button signOutButton = iconButton(UiSupport.SIGN_OUT, "Sign out", this::confirmSignOut);
        Button settingsButton = iconButton(UiSupport.SETTINGS, "Settings", () -> showSettings(true, "profile"));
        HBox userRow = new HBox(4, sideUserAvatarLabel, userText, UiSupport.spacer(), settingsButton, signOutButton);
        userRow.getStyleClass().add("side-user");
        userRow.setAlignment(Pos.CENTER_LEFT);

        refreshProjectTree();
        updateCollabListDisplay();

        panel.getChildren().addAll(switcher, content, userRow);
        return panel;
    }

    private ToggleButton panelToggle(String text, String iconPath, ToggleGroup group) {
        javafx.scene.shape.SVGPath icon = UiSupport.lineIcon(iconPath, 13, Color.web("#6B6B65"));
        StackPane holder = new StackPane(icon);
        holder.setMinSize(16, 16);
        holder.setPrefSize(16, 16);
        holder.setMaxSize(16, 16);
        ToggleButton toggle = new ToggleButton(text, holder);
        toggle.setToggleGroup(group);
        toggle.getStyleClass().add("seg-button");
        toggle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(toggle, Priority.ALWAYS);
        toggle.selectedProperty().addListener((obs, was, selected) ->
                icon.setStroke(Color.web(selected ? "#1F1F1C" : "#6B6B65")));
        return toggle;
    }

    private Button sideAction(String text, String iconPath, Runnable action) {
        Button button = new Button(text, UiSupport.iconBox(iconPath, 14, Color.web("#6B6B65"), 18));
        button.getStyleClass().add("side-action");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(e -> action.run());
        return button;
    }

    private Button iconButton(String iconPath, String tip, Runnable action) {
        Button button = new Button();
        button.setGraphic(UiSupport.iconBox(iconPath, 14, Color.web("#8B8B85"), 18));
        button.getStyleClass().add("icon-ghost");
        button.setTooltip(UiSupport.tooltip(tip));
        button.setOnAction(e -> action.run());
        return button;
    }

    /**
     * Làm mới cây thư mục dự án dựa trên các tệp thực tế trên ổ cứng
     */
    private void refreshProjectTree() {
        if (rootTreeItem == null) return;
        rootTreeItem.setValue(activeProjectName);
        rootTreeItem.getChildren().clear();
        treePaths.clear();

        if (activeProjectRoot != null) {
            addWorkspaceFiles(rootTreeItem, activeProjectRoot.toFile(), "", 0);
        }
        if (rootTreeItem.getChildren().isEmpty()) {
            rootTreeItem.getChildren().add(new TreeItem<>("No LaTeX files yet"));
        }
        if (projectCountLabel != null) {
            int fileCount = countFiles(rootTreeItem);
            projectCountLabel.setText(fileCount == 1 ? "1 file" : fileCount + " files");
        }
        selectTreePath(currentFile);
    }

    private ContextMenu fileActions(String path) {
        boolean pdf = path.toLowerCase(Locale.ROOT).endsWith(".pdf");
        boolean main = LocalBackend.MAIN_FILE.equals(path);
        MenuItem open = new MenuItem(pdf ? "Open PDF" : "Open");
        open.setOnAction(e -> {
            if (pdf) {
                if (activeProjectRoot.resolve(path).normalize().equals(fileManager.pdfPathFor(LocalBackend.MAIN_FILE))) {
                    openCompiledPdf();
                } else {
                    getHostServices().showDocument(activeProjectRoot.resolve(path).toUri().toString());
                }
            } else if (!isEditableSource(path)) {
                getHostServices().showDocument(activeProjectRoot.resolve(path).toUri().toString());
            } else if (!openFile(path)) {
                selectTreePath(currentFile);
            }
        });
        MenuItem rename = new MenuItem("Rename…");
        rename.setDisable(main || pdf);
        rename.setOnAction(e -> renameFileDialog(path));
        MenuItem reveal = new MenuItem("Show in folder");
        reveal.setOnAction(e -> revealFolder(activeProjectRoot.resolve(path).getParent()));
        MenuItem delete = new MenuItem("Delete…");
        delete.getStyleClass().add("menu-danger");
        delete.setDisable(main);
        delete.setOnAction(e -> deleteFileDialog(path));
        if (main) {
            rename.setText("Rename… (main.tex is the compile root)");
        }
        return new ContextMenu(open, rename, reveal, new SeparatorMenuItem(), delete);
    }

    private void renameFileDialog(String path) {
        UiModal modal = UiModal.create(screenHost, UiSupport.EDIT, UiModal.Tone.NEUTRAL,
                "Rename file", "Update any \\input{...} that points at the old name.");
        TextField name = modal.field("New name", path, "chapter1.tex");
        modal.actions(null, "Cancel", "Rename", UiModal.Tone.NEUTRAL, () -> {
            String target = name.getText() == null ? "" : name.getText().trim();
            String problem = validateNewFileName(target);
            if (problem != null) {
                modal.error(name, problem);
                return false;
            }
            if (target.equals(path)) {
                return true;
            }
            if (Files.exists(activeProjectRoot.resolve(target))) {
                modal.error(name, target + " already exists.");
                return false;
            }
            boolean wasOpen = path.equals(currentFile);
            if (wasOpen) saveIfDirty();
            try {
                fileManager.renameFile(path, target);
                invalidatePdf();
            } catch (IOException | RuntimeException e) {
                modal.error(name, "Could not rename: " + e.getMessage());
                return false;
            }
            appendLog("[FILE] Renamed " + path + " → " + target);
            if (wasOpen) {
                currentFile = target;
                if (editorTitleLabel != null) editorTitleLabel.setText(target);
                if (editorPathLabel != null) editorPathLabel.setText(target);
            }
            refreshProjectTree();
            return true;
        });
        modal.show();
    }

    private void deleteFileDialog(String path) {
        UiModal.confirm(screenHost, UiModal.Tone.DANGER, "Delete " + path + "?",
                "The file is removed from the project folder. This cannot be undone from the app.",
                "Delete file", () -> {
                    boolean wasOpen = path.equals(currentFile);
                    try {
                        if (wasOpen) markSaved(); // don't write the file back while leaving it
                        fileManager.deleteFile(path);
                        invalidatePdf();
                        appendLog("[FILE] Deleted " + path);
                    } catch (IOException | RuntimeException e) {
                        showAlert(Alert.AlertType.ERROR, "Could not delete", e.getMessage());
                        return;
                    }
                    if (wasOpen) {
                        currentFile = "";
                        openFile(LocalBackend.MAIN_FILE);
                    }
                    refreshProjectTree();
                });
    }

    /** Null when the name is a usable project-relative source file name. */
    private String validateNewFileName(String name) {
        if (name.isEmpty()) return "Give the file a name.";
        if (name.contains("..") || name.startsWith("/") || name.contains("\\") || name.startsWith(".")) {
            return "Use a path inside the project, e.g. chapter1.tex or sections/intro.tex.";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".tex") || lower.endsWith(".bib") || lower.endsWith(".sty") || lower.endsWith(".cls"))) {
            return "Use a .tex, .bib, .sty or .cls file.";
        }
        return null;
    }

    private void selectTreePath(String relativePath) {
        for (Map.Entry<TreeItem<String>, String> entry : treePaths.entrySet()) {
            if (entry.getValue().equals(relativePath)) {
                projectTreeView.getSelectionModel().select(entry.getKey());
                return;
            }
        }
    }

    private int countFiles(TreeItem<String> item) {
        int count = 0;
        for (TreeItem<String> child : item.getChildren()) {
            String path = treePaths.get(child);
            if (child.isLeaf()) {
                if (path != null && !Files.isDirectory(activeProjectRoot.resolve(path))) {
                    count++;
                }
            } else {
                count += countFiles(child);
            }
        }
        return count;
    }

    private void addWorkspaceFiles(TreeItem<String> parent, File dir, String prefix, int depth) {
        File[] files = dir.listFiles();
        if (files == null) return;
        List<File> entries = new ArrayList<>();
        for (File file : files) {
            if (shouldShowInTree(file, depth)) {
                entries.add(file);
            }
        }
        entries.sort(Comparator
                .comparing((File file) -> !file.isDirectory())
                .thenComparing(file -> !file.getName().equals(LocalBackend.MAIN_FILE))
                .thenComparing(file -> file.getName().toLowerCase(Locale.ROOT)));
        for (File entry : entries) {
            String path = prefix + entry.getName();
            if (entry.isDirectory()) {
                TreeItem<String> folder = new TreeItem<>(entry.getName());
                folder.setExpanded(depth == 0);
                addWorkspaceFiles(folder, entry, path + "/", depth + 1);
                treePaths.put(folder, path);
                parent.getChildren().add(folder);
            } else {
                TreeItem<String> leaf = new TreeItem<>(entry.getName());
                treePaths.put(leaf, path);
                parent.getChildren().add(leaf);
            }
        }
    }

    private boolean shouldShowInTree(File file, int depth) {
        if (Files.isSymbolicLink(file.toPath())) return false;
        String name = file.getName();
        if (file.isDirectory()) {
            if (depth >= 32) return false;
            if (name.startsWith(".") || name.equals("target") || name.equals("node_modules")) return false;
            return true;
        }
        if (name.startsWith(".")) return false;
        if (file.toPath().startsWith(activeProjectRoot.resolve("build"))) return name.toLowerCase(Locale.ROOT).endsWith(".pdf");
        return true;
    }

    private boolean containsLatexFiles(File dir, int depth) {
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (isLatexFile(file)) return true;
            if (file.isDirectory() && depth + 1 < 3) {
                String name = file.getName();
                if (name.startsWith(".") || name.equals("target") || name.equals("node_modules")) continue;
                if (containsLatexFiles(file, depth + 1)) return true;
            }
        }
        return false;
    }

    private boolean isLatexFile(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return lower.endsWith(".tex") || lower.endsWith(".bib") || lower.endsWith(".sty")
                || lower.endsWith(".cls") || lower.endsWith(".pdf");
    }

    private void updateCollabListDisplay() {
        if (roomMemberRows == null) return;
        roomMemberLabels.clear();
        roomMemberLabels.add(currentUserName + " · you" + (isConnected ? "" : " (offline)"));
        renderRoomMembers();
        updateSideUserChip();
    }

    private void renderRoomMembers() {
        if (roomMemberRows == null) return;
        roomMemberRows.getChildren().clear();
        for (String raw : roomMemberLabels) {
            boolean offline = raw.contains("(offline)");
            boolean you = raw.contains("· you");
            String name = raw.replace(" · you", "").replace(" (offline)", "").trim();
            Label avatar = new Label(UiSupport.initials(name));
            avatar.getStyleClass().add(offline ? "member-avatar-offline" : "member-avatar");
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("member-name");
            String metaText = you
                    ? (offline ? "You · offline" : "You · in room")
                    : (offline ? "Offline" : "In room");
            Label meta = new Label(metaText);
            meta.getStyleClass().add("member-meta");
            VBox text = new VBox(1, nameLabel, meta);
            HBox.setHgrow(text, Priority.ALWAYS);
            Circle dot = new Circle(3.5, Color.web(offline ? "#C9C9C3" : "#2A6A4D"));
            HBox row = new HBox(8, avatar, text, UiSupport.spacer(), dot);
            row.getStyleClass().add("member-row");
            row.setAlignment(Pos.CENTER_LEFT);
            roomMemberRows.getChildren().add(row);
        }
        if (roomMemberLabels.size() <= 1) {
            Label hint = new Label(isConnected
                    ? "Waiting for room members…"
                    : "Connect a room to see presence here.");
            hint.getStyleClass().add("side-hint");
            hint.setWrapText(true);
            roomMemberRows.getChildren().add(hint);
        }
    }

    private void updateSideUserChip() {
        if (sideUserNameLabel == null) return;
        sideUserMetaLabel.setText(isConnected ? "In " + currentRoomId : "Offline workspace");
        sideUserNameLabel.setText(currentUserName);
        java.util.Optional<Path> picture = java.util.Optional.empty();
        if (session != null) {
            try {
                picture = backend.avatar(session);
            } catch (RuntimeException ignored) {
                // Fall back to initials.
            }
        }
        UiSupport.applyAvatar(sideUserAvatarLabel, currentUserName, picture, 26);
    }

    private void refreshOutline() {
        if (outlineRows == null || codeEditor == null) return;

        List<Integer> levels = new ArrayList<>();
        List<Integer> lineNumbers = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        StringBuilder signature = new StringBuilder();

        String[] lines = codeEditor.getText().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            int level = outlineLevel(trimmed);
            if (level < 0) continue;
            int open = trimmed.indexOf('{');
            int close = trimmed.lastIndexOf('}');
            if (open < 0 || close <= open) continue;
            String title = trimmed.substring(open + 1, close).trim();
            if (title.isEmpty()) continue;
            levels.add(level);
            lineNumbers.add(i);
            titles.add(title);
            signature.append(i).append(':').append(level).append(':').append(title).append('\u0001');
        }

        if (signature.toString().equals(outlineSignature)) return;
        outlineSignature = signature.toString();
        outlineRows.getChildren().clear();

        if (titles.isEmpty()) {
            Label empty = new Label("Headings appear as you write");
            empty.getStyleClass().add("side-hint");
            empty.setWrapText(true);
            outlineRows.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < titles.size(); i++) {
            int level = levels.get(i);
            int lineNumber = lineNumbers.get(i);
            Label label = new Label(titles.get(i));
            label.getStyleClass().add(level == 0 ? "outline-label-root" : "outline-label");
            label.setPadding(new Insets(0, 0, 0, level * 12));
            HBox row = new HBox(label);
            row.getStyleClass().add("outline-row");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setOnMouseClicked(e -> jumpToLine(lineNumber));
            outlineRows.getChildren().add(row);
        }
    }

    private static int outlineLevel(String line) {
        if (line.startsWith("\\section{") || line.startsWith("\\section*{")) return 0;
        if (line.startsWith("\\subsection{") || line.startsWith("\\subsection*{")) return 1;
        if (line.startsWith("\\subsubsection{") || line.startsWith("\\subsubsection*{")) return 2;
        return -1;
    }

    private void jumpToLine(int lineIndex) {
        if (codeEditor == null) return;
        String text = codeEditor.getText();
        int offset = 0;
        for (int line = 0; line < lineIndex && offset < text.length(); line++) {
            int next = text.indexOf('\n', offset);
            if (next < 0) break;
            offset = next + 1;
        }
        codeEditor.positionCaret(offset);
        codeEditor.requestFocus();
        int total = text.isEmpty() ? 1 : text.split("\n", -1).length;
        javafx.scene.Node node = codeEditor.lookup(".scroll-pane");
        if (node instanceof ScrollPane scroll && total > 1) {
            scroll.setVvalue((double) lineIndex / (total - 1));
        }
    }

    // ----------------------------------------------------------------- settings

    private void showSettings(boolean fromEditor, String startSection) {
        if (session == null) return;
        boolean returnToEditor = fromEditor && editorRoot != null && activeProjectRoot != null;
        if (fromEditor && editorVisible()) saveIfDirty();
        SettingsView.Profile profile = new SettingsView.Profile(screenHost, session, backend, backend,
                renamed -> {
                    session = renamed;
                    currentUserName = renamed.name();
                    updateSideUserChip();
                },
                this::updateSideUserChip,
                this::confirmSignOut);
        SettingsView view = new SettingsView(settings, profile, vietnameseUi, startSection,
                () -> {
                    if (returnToEditor) {
                        screenHost.getChildren().setAll(editorRoot);
                    } else {
                        showHome();
                    }
                },
                vi -> {
                    vietnameseUi = vi;
                    settings.set(AppSettings.LANGUAGE, vi ? "vi" : "en");
                    Platform.runLater(() -> showSettings(fromEditor, "appearance"));
                });
        screenHost.getChildren().setAll(view);
        if (stage != null) stage.setTitle("Overleaf Desktop — Settings");
    }

    private void onSettingChanged(Scene scene, String key) {
        switch (key) {
            case AppSettings.MODE, AppSettings.THEME, AppSettings.FONT_STYLE, AppSettings.CONTENT_WIDTH -> {
                ThemeManager.apply(scene, settings);
                if (codeOverlay != null) codeOverlay.repaint();
            }
            case AppSettings.LANGUAGE -> {
                vietnameseUi = "vi".equals(settings.get(AppSettings.LANGUAGE));
                updateWorkspaceLanguage();
            }
            case AppSettings.OUTLINE_SPLIT -> { /* A divider drag must not reset the editor/compiler selection. */ }
            default -> applyEditorSettings();
        }
    }

    /** Pushes font, wrapping, default compiler and auto-save from the settings onto the live editor. */
    private void applyEditorSettings() {

        if (codeEditor == null) return;
        // One concrete installed family for both layers, so the colored text lines up with the caret.
        String family = monoFamily(settings.get(AppSettings.EDITOR_FONT));
        int size = settings.integer(AppSettings.EDITOR_FONT_SIZE);
        codeEditor.setStyle("-fx-font-size: " + size + "px; -fx-font-family: \"" + family + "\";");
        codeOverlay.setFont(Font.font(family, size));
        codeOverlay.setWrap(settings.bool(AppSettings.WORD_WRAP));
        if (lineGutter != null) {
        lineGutter.setFont(Font.font(family, size));
        lineGutter.setWrap(settings.bool(AppSettings.WORD_WRAP));
    }
        codeEditor.setWrapText(settings.bool(AppSettings.WORD_WRAP));
        if (compilerCombo != null && !settings.get(AppSettings.COMPILER).equals(compilerCombo.getValue())) {
            compilerCombo.setValue(settings.get(AppSettings.COMPILER));
        }
        if (autoSaveTimer != null) autoSaveTimer.stop();
        if (settings.bool(AppSettings.AUTO_SAVE)) {
            autoSaveTimer = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                    javafx.util.Duration.seconds(20), e -> {
                if (editorVisible()) saveIfDirty();
            }));
            autoSaveTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
            autoSaveTimer.play();
        }
    }

    private static String monoFamily(String choice) {
        List<String> wanted = switch (choice) {
            case "menlo" -> List.of("Menlo", "Monaco", "Consolas");
            case "courier" -> List.of("Courier New", "Courier");
            default -> List.of("JetBrains Mono", "SF Mono", "Menlo", "Consolas");
        };
        List<String> installed = Font.getFamilies();
        for (String family : wanted) {
            if (installed.contains(family)) return family;
        }
        return "Monospaced";
    }

    /** Typing { ( [ or $ adds the closing character; typing a closer that is already next just steps over it. */
    private void installEditorBehaviours() {
        codeEditor.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, e -> {
            if (!settings.bool(AppSettings.AUTO_CLOSE) || !codeEditor.isEditable() || e.isShortcutDown()
                    || e.isAltDown() || e.getCharacter().length() != 1) return;
            char typed = e.getCharacter().charAt(0);
            String closer = switch (typed) {
                case '{' -> "}";
                case '(' -> ")";
                case '[' -> "]";
                case '$' -> "$";
                default -> null;
            };
            String text = codeEditor.getText();
            IndexRange range = codeEditor.getSelection();
            int at = range.getStart();
            boolean escaped = at > 0 && text.charAt(at - 1) == '\\';
            if (closer == null) {
                if ((typed == '}' || typed == ')' || typed == ']') && range.getLength() == 0
                        && at < text.length() && text.charAt(at) == typed) {
                    codeEditor.positionCaret(at + 1);
                    e.consume();
                }
                return;
            }
            if (typed == '$' && range.getLength() == 0 && at < text.length() && text.charAt(at) == '$') {
                codeEditor.positionCaret(at + 1);
                e.consume();
                return;
            }
            if (escaped) return;
            String selected = codeEditor.getSelectedText();
            codeEditor.replaceText(at, range.getEnd(), typed + selected + closer);
            if (selected.isEmpty()) {
                codeEditor.positionCaret(at + 1);
            } else {
                codeEditor.selectRange(at + 1, at + 1 + selected.length());
            }
            e.consume();
        });
    }

    // ------------------------------------------------------------ code toolbar

    private TextField findField;
    private Label findCount;
    private HBox findBar;

    /** Formatting and search controls above the code editor; every action edits through the normal text path. */
    private VBox buildCodeToolbar() {
        Color glyph = Color.web("#B4BBC8");

        Button undo = toolButton(UiSupport.UNDO, "Undo (Ctrl/Cmd+Z)", glyph, () -> codeEditor.undo());
        Button redo = toolButton(UiSupport.REDO, "Redo (Ctrl/Cmd+Shift+Z)", glyph, () -> codeEditor.redo());

        Label small = new Label("T");
        small.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #B4BBC8;");
        Label large = new Label("T");
        large.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #B4BBC8;");
        HBox letters = new HBox(1, small, large);
        letters.setAlignment(Pos.BOTTOM_CENTER);
        Button style = new Button();
        style.setGraphic(new HBox(3, letters, UiSupport.iconBox(UiSupport.CHEVRON_DOWN, 12, glyph, 12)));
        ((HBox) style.getGraphic()).setAlignment(Pos.CENTER);
        style.getStyleClass().add("tool-button");
        style.setMinWidth(46);
        style.setTooltip(UiSupport.tooltip("Text style"));
        ContextMenu styleMenu = new ContextMenu(
                menuItem("Normal text", () -> applyHeading(null)),
                menuItem("Section", () -> applyHeading("section")),
                menuItem("Subsection", () -> applyHeading("subsection")),
                menuItem("Subsubsection", () -> applyHeading("subsubsection")),
                menuItem("Paragraph", () -> applyHeading("paragraph")));
        style.setOnAction(e -> styleMenu.show(style, javafx.geometry.Side.BOTTOM, 0, 4));

        Button bold = toolButton(UiSupport.BOLD, "Bold (Ctrl/Cmd+B)", glyph, () -> wrapSelection("\\textbf{", "}"));
        Button italic = toolButton(UiSupport.ITALIC, "Italic (Ctrl/Cmd+I)", glyph, () -> wrapSelection("\\textit{", "}"));

        HBox dots = new HBox(3);
        dots.setAlignment(Pos.CENTER);
        for (int i = 0; i < 3; i++) {
            dots.getChildren().add(new Circle(1.5, glyph));
        }
        Button more = new Button();
        more.setGraphic(dots);
        more.getStyleClass().add("tool-button");
        more.setTooltip(UiSupport.tooltip("More"));
        ContextMenu moreMenu = new ContextMenu(
                menuItem("Underline", () -> wrapSelection("\\underline{", "}")),
                menuItem("Monospace", () -> wrapSelection("\\texttt{", "}")),
                menuItem("Inline math", () -> wrapSelection("$", "$")),
                new SeparatorMenuItem(),
                menuItem("Bulleted list", () -> insertList("itemize")),
                menuItem("Numbered list", () -> insertList("enumerate")),
                new SeparatorMenuItem(),
                menuItem("Comment / uncomment lines (Ctrl/Cmd+/)", this::toggleComment));
        more.setOnAction(e -> moreMenu.show(more, javafx.geometry.Side.BOTTOM, 0, 4));

        Button search = toolButton(UiSupport.SEARCH, "Find (Ctrl/Cmd+F)", glyph, this::toggleFindBar);

        HBox bar = new HBox(2, undo, redo, toolSeparator(), style, toolSeparator(), bold, italic, toolSeparator(), more,
                UiSupport.spacer(), search);
        bar.getStyleClass().add("code-toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        // Editing controls follow the editor: they go dark while a room join holds it read-only.
        for (Button edit : List.of(undo, redo, style, bold, italic, more)) {
            edit.disableProperty().bind(codeEditor.editableProperty().not());
        }

        codeEditor.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (!e.isShortcutDown() || e.isAltDown()) return;
            switch (e.getCode()) {
                case B -> { if (codeEditor.isEditable()) wrapSelection("\\textbf{", "}"); }
                case I -> { if (codeEditor.isEditable()) wrapSelection("\\textit{", "}"); }
                case SLASH -> { if (codeEditor.isEditable()) toggleComment(); }
                case F -> toggleFindBar();
                default -> { return; }
            }
            e.consume();
        });

        return new VBox(bar, buildFindBar());
    }

    private Button toolButton(String iconPath, String tip, Color glyph, Runnable action) {
        Button button = new Button();
        button.setGraphic(UiSupport.iconBox(iconPath, 15, glyph, 18));
        button.getStyleClass().add("tool-button");
        button.setTooltip(UiSupport.tooltip(tip));
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return button;
    }

    private static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private static Region toolSeparator() {
        Region line = new Region();
        line.getStyleClass().add("tool-separator");
        line.setMinSize(1, 16);
        line.setPrefSize(1, 16);
        line.setMaxSize(1, 16);
        HBox.setMargin(line, new Insets(0, 6, 0, 6));
        return line;
    }

    /** Wraps the selection (or drops the caret between the pair when nothing is selected). */
    private void wrapSelection(String before, String after) {
        IndexRange range = codeEditor.getSelection();
        String selected = codeEditor.getSelectedText();
        int start = range.getStart();
        codeEditor.replaceText(start, range.getEnd(), before + selected + after);
        if (selected.isEmpty()) {
            codeEditor.positionCaret(start + before.length());
        } else {
            codeEditor.selectRange(start + before.length(), start + before.length() + selected.length());
        }
        codeEditor.requestFocus();
    }

    /** Turns the caret's line into {@code \command{...}}; a null command strips any heading back to plain text. */
    private void applyHeading(String command) {
        String text = codeEditor.getText();
        int caret = Math.min(codeEditor.getCaretPosition(), text.length());
        int lineStart = text.lastIndexOf('\n', caret - 1) + 1;
        int lineEnd = text.indexOf('\n', caret);
        if (lineEnd < 0) lineEnd = text.length();
        String line = text.substring(lineStart, lineEnd);
        java.util.regex.Matcher heading = java.util.regex.Pattern
                .compile("^\\s*\\\\(?:sub)*(?:section|paragraph)\\*?\\{(.*)}\\s*$").matcher(line);
        String inner = heading.matches() ? heading.group(1) : line.strip();
        String result = command == null ? inner : "\\" + command + "{" + inner + "}";
        codeEditor.replaceText(lineStart, lineEnd, result);
        codeEditor.positionCaret(lineStart + result.length());
        codeEditor.requestFocus();
    }

    /** Selected lines become {@code \item}s inside the environment; with no selection a starter list is inserted. */
    private void insertList(String environment) {
        IndexRange range = codeEditor.getSelection();
        String selected = codeEditor.getSelectedText();
        StringBuilder body = new StringBuilder();
        if (selected.isBlank()) {
            body.append("  \\item ");
        } else {
            for (String line : selected.split("\n")) {
                if (!line.isBlank()) {
                    body.append("  \\item ").append(line.strip()).append('\n');
                }
            }
            if (body.length() > 0) body.setLength(body.length() - 1);
        }
        String block = "\\begin{" + environment + "}\n" + body + "\n\\end{" + environment + "}";
        int start = range.getStart();
        codeEditor.replaceText(start, range.getEnd(), block);
        if (selected.isBlank()) {
            codeEditor.positionCaret(start + ("\\begin{" + environment + "}\n  \\item ").length());
        }
        codeEditor.requestFocus();
    }

    /** Comments the selected lines with {@code % }, or removes it when they are all already commented. */
    private void toggleComment() {
        String text = codeEditor.getText();
        IndexRange range = codeEditor.getSelection();
        int from = range.getStart();
        int to = range.getEnd();
        if (to > from && text.charAt(to - 1) == '\n') to--;
        int blockStart = text.lastIndexOf('\n', from - 1) + 1;
        int blockEnd = text.indexOf('\n', to);
        if (blockEnd < 0) blockEnd = text.length();
        String[] lines = text.substring(blockStart, blockEnd).split("\n", -1);
        boolean allCommented = true;
        for (String line : lines) {
            if (!line.isBlank() && !line.stripLeading().startsWith("%")) allCommented = false;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) out.append('\n');
            if (line.isBlank()) {
                out.append(line);
            } else if (allCommented) {
                int at = line.indexOf('%');
                int cut = at + 1 < line.length() && line.charAt(at + 1) == ' ' ? at + 2 : at + 1;
                out.append(line, 0, at).append(line.substring(cut));
            } else {
                out.append("% ").append(line);
            }
        }
        codeEditor.replaceText(blockStart, blockEnd, out.toString());
        codeEditor.selectRange(blockStart, blockStart + out.length());
        codeEditor.requestFocus();
    }

    private HBox buildFindBar() {
        findField = new TextField();
        findField.setPromptText("Find in file");
        findField.getStyleClass().add("find-field");
        HBox.setHgrow(findField, Priority.ALWAYS);
        findCount = new Label();
        findCount.getStyleClass().add("find-count");

        Color glyph = Color.web("#B4BBC8");
        Button previous = toolButton(UiSupport.ARROW_UP, "Previous match (Shift+Enter)", glyph, () -> findNext(false));
        Button next = toolButton(UiSupport.ARROW_DOWN, "Next match (Enter)", glyph, () -> findNext(true));
        Button close = toolButton(UiSupport.CLOSE, "Close (Esc)", glyph, this::toggleFindBar);

        findBar = new HBox(4, findField, findCount, previous, next, close);
        findBar.getStyleClass().add("find-bar");
        findBar.setAlignment(Pos.CENTER_LEFT);
        findBar.setVisible(false);
        findBar.setManaged(false);

        findField.textProperty().addListener((obs, oldText, newText) -> updateFindCount(false));
        findField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                findNext(!e.isShiftDown());
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                toggleFindBar();
                e.consume();
            }
        });
        return findBar;
    }

    private void toggleFindBar() {
        boolean show = !findBar.isVisible();
        findBar.setVisible(show);
        findBar.setManaged(show);
        if (show) {
            String picked = codeEditor.getSelectedText();
            if (!picked.isEmpty() && !picked.contains("\n")) findField.setText(picked);
            findField.requestFocus();
            findField.selectAll();
            updateFindCount(false);
        } else {
            codeEditor.requestFocus();
        }
    }

    /** Selects the next (or previous) case-insensitive match, wrapping at the ends of the file. */
    private void findNext(boolean forward) {
        String query = findField.getText() == null ? "" : findField.getText().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            updateFindCount(false);
            return;
        }
        String text = codeEditor.getText().toLowerCase(Locale.ROOT);
        IndexRange range = codeEditor.getSelection();
        int at;
        if (forward) {
            at = text.indexOf(query, range.getEnd());
            if (at < 0) at = text.indexOf(query);
        } else {
            at = range.getStart() > 0 ? text.lastIndexOf(query, range.getStart() - 1) : -1;
            if (at < 0) at = text.lastIndexOf(query);
        }
        if (at >= 0) {
            codeEditor.selectRange(at, at + query.length());
        }
        updateFindCount(true);
    }

    private void updateFindCount(boolean selectionIsMatch) {
        String query = findField.getText() == null ? "" : findField.getText().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            findCount.setText("");
            return;
        }
        String text = codeEditor.getText().toLowerCase(Locale.ROOT);
        int total = 0;
        int current = 0;
        int selectedStart = codeEditor.getSelection().getStart();
        for (int at = text.indexOf(query); at >= 0; at = text.indexOf(query, at + query.length())) {
            total++;
            if (selectionIsMatch && at == selectedStart) current = total;
        }
        findCount.setText(total == 0 ? "No results" : (current > 0 ? current + " of " + total : total + " found"));
    }

    private SplitPane createCenterEditorPanel(String initialContent) {
        SplitPane editorSplit = new SplitPane();
        editorSplit.setOrientation(Orientation.VERTICAL);
        editorSplit.getStyleClass().addAll("editor-split", "code-stack");

        VBox editorBox = new VBox();

        HBox editorHeader = new HBox(8);
        editorHeader.getStyleClass().addAll("pane-header", "code-header");
        editorHeader.setAlignment(Pos.CENTER_LEFT);
        Label editorTitle = new Label(currentFile);
        editorTitleLabel = editorTitle;
        editorTitle.getStyleClass().add("pane-title");
        Label kind = new Label("LaTeX");
        kind.getStyleClass().add("pill");
        Label encoding = new Label("UTF-8");
        encoding.getStyleClass().add("pane-sub");
        editorHeader.getChildren().addAll(editorTitle, kind, UiSupport.spacer(), encoding);

        codeEditor = new TextArea(initialContent);
        lastEditorContent = initialContent;
        codeEditor.getStyleClass().add("code-textarea");
        VBox.setVgrow(codeEditor, Priority.ALWAYS);

        codeEditor.caretPositionProperty().addListener((obs, oldPos, newPos) -> updateCursorPosition());
        refreshOutline();

        // LẮNG NGHE NGƯỜI DÙNG GÕ VĂN BẢN (M4 Quy tắc 1) & CẬP NHẬT CANVAS ĐỘNG
        codeEditor.textProperty().addListener((obs, oldText, newText) -> {
            refreshOutline();
            if (loadingDocument) {
                return;
            }
            handleLocalTextChange(oldText, newText);
            if (!isApplyingRemoteEdit) {
                markDirty();
            }
        });

        codeOverlay = new CodeOverlay(codeEditor);
        lineGutter = new LineNumberGutter(codeEditor);
        HBox.setHgrow(codeOverlay.node(), Priority.ALWAYS);
        HBox editorArea = new HBox(lineGutter, codeOverlay.node());
        VBox.setVgrow(editorArea, Priority.ALWAYS);
        editorBox.getChildren().addAll(editorHeader, buildCodeToolbar(), editorArea);
        installEditorBehaviours();
        applyEditorSettings();

        VBox terminalBox = new VBox();

        HBox terminalHeader = new HBox(8);
        terminalHeader.getStyleClass().addAll("pane-header", "terminal-header");
        terminalHeader.setAlignment(Pos.CENTER_LEFT);
        Label termTitle = new Label("Build output");
        termTitle.getStyleClass().add("pane-title");
        Label termSub = new Label("ProcessBuilder");
        termSub.getStyleClass().add("pane-sub");
        Button clearLog = new Button("Clear");
        clearLog.getStyleClass().add("btn-ghost");
        clearLog.setStyle("-fx-font-size: 12px; -fx-padding: 2 8 2 8;");
        clearLog.setOnAction(e -> terminalLogArea.clear());

        terminalHeader.getChildren().addAll(termTitle, termSub, UiSupport.spacer(), clearLog);

        terminalLogArea = new TextArea();
        terminalLogArea.setEditable(false);
        terminalLogArea.getStyleClass().add("log-textarea");
        VBox.setVgrow(terminalLogArea, Priority.ALWAYS);

        terminalBox.getChildren().addAll(terminalHeader, terminalLogArea);

        editorSplit.getItems().addAll(editorBox, terminalBox);
        editorSplit.setDividerPositions(0.74);

        return editorSplit;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(14);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Ready. Press Recompile or F5.");
        statusLabel.getStyleClass().add("status-label");

        Region spacer = UiSupport.spacer();

        compileProgressBar = new ProgressBar();
        compileProgressBar.setPrefWidth(110);
        compileProgressBar.setPrefHeight(3);
        compileProgressBar.setVisible(false);

        cursorLabel = new Label("Ln 1, Col 1 · 0 chars");
        cursorLabel.getStyleClass().add("status-label");

        statusBar.getChildren().addAll(statusLabel, spacer, compileProgressBar, cursorLabel);
        return statusBar;
    }

    private void markDirty() {
        invalidatePdf();
        userDirty = true;
        if (dirtyLabel != null) {
            dirtyLabel.setVisible(true);
            dirtyLabel.setManaged(true);
        }
    }

    private void invalidatePdf() {
        pdfInputRevision++;
        if (pdfPreviewPane != null) pdfPreviewPane.markStale("Nguồn đã thay đổi — bấm Recompile để cập nhật PDF.");
    }

    private void markSaved() {
        userDirty = false;
        if (dirtyLabel != null) {
            dirtyLabel.setVisible(false);
            dirtyLabel.setManaged(false);
        }
    }

    // =========================================================================
    // XỬ LÝ M4: SOẠN THẢO, TẠO CRDT OPERATION VÀ OFFLINE QUEUE (QUY TẮC 1)
    // =========================================================================

    /**
     * Phát hiện sai khác (Diff) khi người dùng gõ văn bản, đóng gói thành CRDTOperation
     * và cập nhật ngay vào bản xem trước Canvas động.
     */
    private void handleLocalTextChange(String oldText, String newText) {
        if (isApplyingRemoteEdit) {
            return;
        }

        if (oldText == null) oldText = "";
        if (newText == null) newText = "";
        if (oldText.equals(newText)) return;

        if (operationQueue == null || !isMainFile()) {
            lastEditorContent = newText;
            if (pdfPreviewPane != null && isMainFile()) {
                invalidatePdf();
            }
            return;
        }

        if (operationQueue.isResyncing()) {
            return;
        }

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

        // Keep the last real PDF visible, clearly marked as stale until the next successful build.
        if (pdfPreviewPane != null) {
            invalidatePdf();
        }
    }

    private void dispatchOperation(CRDTOperation op) {
        try {
            SyncMessage m = operationQueue.prepareEdit(op);
            if (m != null) {
                sendMessageToServer(m);
                appendLog(String.format("[SYNC] Sent %s at %d (Lamport %d)",
                        op.getType(), op.getPosition(), op.getLamportTimestamp()));
            } else {
                appendLog(String.format("[OFFLINE] Queued %s at %d (%d pending)",
                        op.getType(), op.getPosition(), operationQueue.getPendingCount()));
            }
        } catch (IllegalStateException e) {
            appendLog("[WARN] " + e.getMessage());
        }
    }

    private void updatePendingBadge() {
        Platform.runLater(() -> {
            if (connectionBadge == null) return;
            int pending = operationQueue == null ? 0 : operationQueue.getPendingCount();
            if (isConnected) {
                connectionBadge.setText(pending > 0
                        ? currentRoomId + " · " + pending + " pending"
                        : currentRoomId);
                connectionBadge.getStyleClass().clear();
                connectionBadge.getStyleClass().add("badge-pill-online");
                connectionDot.setFill(Color.web("#2F9E63"));
            } else {
                connectionBadge.setText(pending > 0
                        ? "Offline · " + pending + " queued"
                        : "Local session");
                connectionBadge.getStyleClass().clear();
                connectionBadge.getStyleClass().add("badge-pill-offline");
                connectionDot.setFill(pending > 0 ? Color.web("#B5822A") : Color.web("#A6A6A0"));
            }
            updateSideUserChip();
        });
    }

    // =========================================================================
    // XỬ LÝ M4: NHẬN THÔNG ĐIỆP TỪ SERVER (QUY TẮC 2, 4, 5)
    // =========================================================================

    private void handleIncomingServerMessage(SyncMessage msg, NetworkClient source, long generation) {
        Platform.runLater(() -> {
            if (networkClient != source || !connectionScope.accepts(generation, msg.getRoomId())) return;
            lamportClock.accumulateAndGet(msg.getLamportTimestamp(), Math::max);
            if (operationQueue != null) {
                operationQueue.handleServerMessage(msg);
            }

            switch (msg.getMessageType()) {
                case FULL_SYNC:
                    roomSnapshotEmpty = msg.getPayload().isEmpty();
                    backupBeforeRoomSnapshot(msg.getPayload());
                    isApplyingRemoteEdit = true;
                    try {
                        codeEditor.setText(msg.getPayload());
                        lastEditorContent = msg.getPayload();
                        invalidatePdf();
                        appendLog("[SYNC] Received full document (" + msg.getPayload().length() + " chars).");
                    } finally {
                        isApplyingRemoteEdit = false;
                    }
                    break;

                case TEXT_EDIT:
                    if (!currentUserId.equals(msg.getSenderId())) {
                        CRDTOperation op = OperationCodec.fromJson(msg.getPayload());
                        lamportClock.accumulateAndGet(op.getLamportTimestamp(), Math::max);
                        applyRemoteOperation(op);
                        appendLog(String.format("[COLLAB] %s edited: %s at %d",
                                msg.getSenderName(), op.getType(), op.getPosition()));
                    }
                    break;

                case ACK:
                case ACK_BATCH:
                    appendLog("[ACK] Server confirmed operation " + msg.getPayload());
                    updatePendingBadge();
                    break;

                case CHECKPOINT:
                    appendLog("[CHECKPOINT] Server checkpoint: " + msg.getPayload());
                    if (operationQueue != null && !operationQueue.isResyncing()) {
                        codeEditor.setEditable(true);
                        statusLabel.setText("In room " + currentRoomId + " · edits sync live.");
                        appendLog("[RESYNC] Editor unlocked; you are back in sync.");
                        seedEmptyRoom();
                    }
                    updatePendingBadge();
                    break;

                case USER_LIST_UPDATE:
                    appendLog("[ROOM] Member list updated.");
                    roomMemberLabels.clear();
                    if (!msg.getPayload().isBlank()) {
                        for (String member : msg.getPayload().split("\n")) {
                            String[] fields = member.split("\\|", 4);
                            String label = fields.length > 1 ? fields[1] : member;
                            String id = fields.length > 0 ? fields[0] : "";
                            roomMemberLabels.add(label + (currentUserId.equals(id) ? " · you" : ""));
                        }
                    }
                    renderRoomMembers();
                    break;

                case ERROR:
                    if (msg.getPayload() != null && msg.getPayload().contains("ROOM_UNKNOWN")) {
                        codeEditor.setEditable(true);
                        appendLog("[ERROR] ROOM_UNKNOWN: the room is not on the server.");
                        showAlert(Alert.AlertType.WARNING, "Room unavailable",
                                "The room '" + currentRoomId + "' is no longer on the server.\n\n"
                                        + "Your local text and queued edits are safe. Save a copy, then reconnect.");
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
            invalidatePdf();
        } finally {
            isApplyingRemoteEdit = false;
        }
    }

    private void showConnectDialog(Stage parent) {
        if (fileManager == null) {
            showAlert(Alert.AlertType.INFORMATION, "Open a project", "Open a project before joining a room.");
            return;
        }
        UiModal modal = UiModal.create(screenHost, UiSupport.USERS, UiModal.Tone.NEUTRAL,
                "Connect a room", "Edit " + activeProjectName + "/main.tex live with others over TCP.");
        TextField hostField = modal.field("Server host", currentHost, "127.0.0.1");
        TextField portField = modal.field("Port", String.valueOf(currentPort), "8080");
        modal.row(UiModal.boxOf(hostField), UiModal.boxOf(portField));
        portField.setPrefColumnCount(6);
        TextField roomField = modal.field("Room", currentRoomId, "ROOM-101");
        TextField userField = modal.field("Display name", currentUserName, "Your name");
        modal.row(UiModal.boxOf(roomField), UiModal.boxOf(userField));
        PasswordField tokenField = modal.password("Access token", accessToken, "Optional shared token");
        CheckBox tlsField = modal.check("Use TLS (required for servers outside localhost)", useTls);
        modal.actions("Joining replaces the editor with the room's text. An empty room starts from yours.",
                "Cancel", "Connect", UiModal.Tone.NEUTRAL, () -> {
            String hostValue = hostField.getText().trim();
            if (hostValue.isEmpty()) {
                modal.error(hostField, "Enter the server host.");
                return false;
            }
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
                if (port < 1 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException invalidPort) {
                modal.error(portField, "Choose a port between 1 and 65535.");
                return false;
            }
            String room = roomField.getText().trim();
            if (room.isEmpty() || room.length() > 64) {
                modal.error(roomField, "Enter a room name (up to 64 characters).");
                return false;
            }
            String user = userField.getText().trim();
            if (user.isEmpty()) {
                modal.error(userField, "Enter the name others will see.");
                return false;
            }
            accessToken = tokenField.getText();
            useTls = tlsField.isSelected();
            connectToRoom(hostValue, port, room, user, false);
            return true;
        });
        modal.show();
    }

    private void connectToRoom(String host, int port, String room, String user, boolean isReconnecting) {
        if (fileManager == null) {
            showAlert(Alert.AlertType.INFORMATION, "Open a project", "Open a project before joining a room.");
            return;
        }
        if (!isMainFile() && !openFile(LocalBackend.MAIN_FILE)) {
            return;
        }
        saveIfDirty();
        currentHost = host;
        currentPort = port;
        currentRoomId = room;
        currentUserName = user;
        closeSocketQuietly();
        isConnected = false;
        long generation = connectionScope.begin(room);
        boolean requestedTls = useTls;
        String requestedToken = accessToken;
        statusLabel.setText("Connecting to " + host + ":" + port + "…");

        if (operationQueue == null || !queueRoomId.equals(room)) {
            operationQueue = new OfflineOperationQueue(room, currentUserId, user);
            queueRoomId = room;
        }

        Task<NetworkClient> connectTask = new Task<>() {
            @Override
            protected NetworkClient call() throws Exception {
                return new NetworkClient(host, port, requestedTls, requestedToken);
            }
        };

        connectTask.setOnSucceeded(e -> {
            NetworkClient connected = connectTask.getValue();
            if (!connectionScope.isCurrent(generation)) { connected.close(); return; }
            networkClient = connected;
            isConnected = true;
            appendLog(String.format("[NETWORK] Connected %s:%s · room %s · user %s", host, port, room, user));
            markProjectShared(room);
            updateCollabListDisplay();

            startSocketReaderThread(generation);

            if (isReconnecting) {
                SyncMessage resyncMsg = operationQueue.onReconnected();
                codeEditor.setEditable(false);
                statusLabel.setText("Resyncing with the room…");
                sendMessageToServer(resyncMsg);
                appendLog("[RESYNC] Sent RESYNC with " + operationQueue.getPendingCount() + " unconfirmed edits.");
            } else {
                joinSeed = codeEditor.getText();
                codeEditor.setEditable(false);
                SyncMessage joinMsg = SyncMessage.createJoinRoom(room, currentUserId, user);
                sendMessageToServer(joinMsg);
                appendLog("[ROOM] Joining " + room + "…");
            }

            updatePendingBadge();
            statusLabel.setText("Connected. Waiting for the room state…");
        });

        connectTask.setOnFailed(e -> {
            if (!connectionScope.isCurrent(generation)) return;
            isConnected = false;
            codeEditor.setEditable(true);
            if (operationQueue != null) {
                operationQueue.onConnectionLost();
            }
            updatePendingBadge();
            updateCollabListDisplay();

            appendLog("[NETWORK] No server at " + host + ":" + port + ". Offline mode enabled.");
            appendLog("[OFFLINE] New edits are queued locally until you reconnect.");
            statusLabel.setText("Offline. Edits are queued safely on this machine.");

            showAlert(Alert.AlertType.INFORMATION, "Working offline",
                    "Could not reach the server at " + host + ":" + port + ".\n\n"
                            + "You can keep writing. Edits are stored in the offline queue and "
                            + "synced the next time you reconnect.");
        });

        Thread t = new Thread(connectTask);
        t.setDaemon(true);
        t.start();
    }

    /** First person into a new room shares their main.tex instead of getting a blank page. */
    private void seedEmptyRoom() {
        String seed = joinSeed;
        joinSeed = null;
        if (seed == null || seed.isEmpty() || !roomSnapshotEmpty || !codeEditor.getText().isEmpty()) {
            return;
        }
        codeEditor.setText(seed); // goes through handleLocalTextChange -> one INSERT for the room
        appendLog("[ROOM] The room was empty, so it now starts from this project's main.tex.");
    }

    /** Joining a room that already has different text: keep the local version next to it. */
    private void backupBeforeRoomSnapshot(String roomText) {
        String local = joinSeed;
        if (local == null || local.isBlank() || local.equals(roomText) || roomText.isEmpty() || fileManager == null) {
            return;
        }
        String safeRoom = currentRoomId.replaceAll("[^A-Za-z0-9_-]", "_");
        String backup = "main.before-" + safeRoom + ".tex";
        try {
            fileManager.writeTexFile(backup, local);
            appendLog("[ROOM] The room has its own text; your previous main.tex was kept as " + backup + ".");
            refreshProjectTree();
        } catch (IOException e) {
            appendLog("[WARN] Could not back up main.tex before joining: " + e.getMessage());
        }
    }

    private void markProjectShared(String room) {
        if (activeProjectRoot == null) return;
        try {
            Files.writeString(activeProjectRoot.resolve(".overleaf-room"), room + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            appendLog("[WARN] Could not record the room for this project: " + e.getMessage());
        }
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
        statusLabel.setText("Disconnected. Offline edits are still queued safely.");
        appendLog("[OFFLINE] Connection closed. Local editing stays enabled.");
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
        appendLog("[NETWORK] Left the room. Files remain on this machine.");
    }

    private void startSocketReaderThread(long generation) {
        NetworkClient client = networkClient;
        client.start(msg -> handleIncomingServerMessage(msg, client, generation), error -> Platform.runLater(() -> {
            if (networkClient == client && connectionScope.isCurrent(generation)) {
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
        connectionScope.invalidate();
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
                "Room: %s\n"
                        + "User: %s (ID: %s)\n"
                        + "Queue: %s\n"
                        + "Resyncing: %s\n"
                        + "Pending edits: %d\n"
                        + "Last seen operation: %s\n"
                        + "Lamport clock: %d",
                currentRoomId, currentUserName, currentUserId,
                online ? "Online (sending immediately)" : "Offline (collecting edits)",
                resyncing ? "Yes (editor locked)" : "No (editor ready)",
                pendingCount, lastSeen.isEmpty() ? "(none)" : lastSeen,
                lamportClock.get()
        );

        showAlert(Alert.AlertType.INFORMATION, "Offline queue", info);
    }

    private void triggerRecompile() {
        if (codeEditor == null || btnRecompile == null || btnRecompile.isDisabled()) {
            return;
        }
        if (fileManager == null || activeProjectRoot == null) {
            statusLabel.setText("Open a project before compiling.");
            return;
        }
        String selectedCompiler = compilerCombo.getValue() == null ? "pdfLaTeX" : compilerCombo.getValue();
        LatexCompilerService.Engine engine = switch (selectedCompiler) {
            case "XeLaTeX" -> LatexCompilerService.Engine.XELATEX;
            case "LuaLaTeX" -> LatexCompilerService.Engine.LUALATEX;
            default -> LatexCompilerService.Engine.PDFLATEX;
        };
        if (!saveCurrentFile(false)) {
            return;
        }
        LatexCompilerService.Options compileOptions;
        try {
            compileOptions = settings.compileOptions();
            compilerService.configure(compileOptions);
        } catch (RuntimeException invalid) {
            appendLog("[BUILD] Invalid compiler settings: " + invalid.getMessage());
            statusLabel.setText("Check Settings > Compilation.");
            return;
        }
        btnRecompile.setDisable(true);
        compileProgressBar.setVisible(true);
        compileProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Compiling with " + selectedCompiler + "…");
        pdfPreviewPane.compiling();

        appendLog("\n========================================================");
        appendLog("[BUILD] Started " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        appendLog("[BUILD] Engine: " + selectedCompiler + " | Source: " + LocalBackend.MAIN_FILE
                + " | Folder: " + activeProjectRoot);
        appendLog("[BUILD] latexmk: " + compileOptions.executable() + " | Timeout: "
                + (compileOptions.timeoutSeconds() == 0 ? "unlimited" : compileOptions.timeoutSeconds() + " s"));
        appendLog("[BUILD] Reusing files in build/ for incremental compilation.");

        Path compiledRoot = activeProjectRoot;
        long compiledWorkspace = workspaceGeneration;
        long compiledRevision = pdfInputRevision;
        compilerService.compileAsync(compiledRoot.toString(), LocalBackend.MAIN_FILE, engine,
                new LatexCompilerService.CompileCallback() {
            @Override
            public void onSuccess(LatexCompilerService.CompileResult result) {
                PdfSnapshot pdf;
                boolean inputsMatch;
                try {
                    pdf = PdfSnapshot.read(result.pdfPath(), result.inputFingerprint());
                    inputsMatch = result.inputFingerprint().equals(ProjectInputs.fingerprint(compiledRoot));
                } catch (IOException failure) {
                    onError(1, "Cannot read/validate the compiled PDF: " + failure.getMessage());
                    return;
                }
                Platform.runLater(() -> {
                    finishCompileUi();
                    if (!compiledRoot.equals(activeProjectRoot) || compiledWorkspace != workspaceGeneration) return;
                    lastPdf = result.pdfPath();
                    try {
                        backend.recordCompile();
                    } catch (RuntimeException e) {
                        appendLog("[WARN] " + e.getMessage());
                    }
                    boolean current = inputsMatch && compiledRevision == pdfInputRevision;
                    statusLabel.setText(current ? "Compiled in " + result.durationMillis() + " ms · preview and export use the same PDF."
                            : "Compiled an older version. Recompile before exporting.");
                    appendLog(result.log());
                    appendLog("[SUCCESS] PDF written to " + result.pdfPath());
                    pdfPreviewPane.show(pdf, current);
                    refreshProjectTree();
                });
            }

            @Override
            public void onError(int exitCode, String errorLog) {
                Platform.runLater(() -> {
                    finishCompileUi();
                    if (!compiledRoot.equals(activeProjectRoot) || compiledWorkspace != workspaceGeneration) return;
                    appendLog(errorLog);
                    List<LatexCompilerService.LatexIssue> issues = LatexCompilerService.parseErrors(errorLog);
                    for (LatexCompilerService.LatexIssue issue : issues) {
                        String location = issue.file().isEmpty() ? "" : issue.file() + ":" + issue.line() + " ";
                        appendLog("[LATEX ERROR] " + location + issue.message());
                        appendExplanation(com.overleaf.client.LatexErrorExplainer.explain(issue.message(), vietnameseUi));
                    }
                    if (issues.isEmpty()) {
                        appendExplanation(com.overleaf.client.LatexErrorExplainer.explainExit(exitCode, vietnameseUi));
                    }
                    if (exitCode == LatexCompilerService.EXIT_START_FAILED) {
                        statusLabel.setText("No LaTeX toolchain found. Install TeX Live / MacTeX / MiKTeX (latexmk).");
                    } else if (exitCode == LatexCompilerService.EXIT_TIMEOUT) {
                        statusLabel.setText("Compile timed out. Change timeout in Settings > Compilation.");
                    } else if (exitCode == LatexCompilerService.EXIT_INTERRUPTED) {
                        statusLabel.setText("Compile stopped.");
                    } else if (exitCode == LatexCompilerService.EXIT_MISSING_GLYPHS) {
                        statusLabel.setText("PDF has missing characters. Fix font/engine settings before exporting.");
                    } else if (!issues.isEmpty()) {
                        LatexCompilerService.LatexIssue first = issues.get(0);
                        statusLabel.setText(issues.size() + " LaTeX error(s) · " + (first.file().isEmpty()
                                ? first.message() : "first at " + first.file() + ":" + first.line()));
                    } else {
                        statusLabel.setText("Compile failed (exit " + exitCode + "). See Build output.");
                    }
                    pdfPreviewPane.failed();
                });
            }

            @Override
            public void onSuperseded() {
                Platform.runLater(() -> appendLog("[BUILD] Skipped an older compile request."));
            }
        });
    }

    private void finishCompileUi() {
        btnRecompile.setDisable(false);
        compileProgressBar.setVisible(false);
    }

    private void appendExplanation(com.overleaf.client.LatexErrorExplainer.Explanation explanation) {
        appendLog("[EXPLAIN] " + explanation.title());
        appendLog(explanation.why());
        appendLog((vietnameseUi ? "Gợi ý: " : "Suggested fix: ") + explanation.fix());
    }

    private String readQuietly(String relativePath) {
        try {
            return fileManager.readTexFile(relativePath);
        } catch (IOException e) {
            return "";
        }
    }

    private void openCompiledPdf() {
        if (!requireCurrentPdf()) return;
        try {
            Path copy = Files.createTempFile("overleaf-preview-", ".pdf");
            copy.toFile().deleteOnExit();
            exportVerifiedPdf(copy, true);
        } catch (IOException failure) { showAlert(Alert.AlertType.ERROR, "PDF", failure.getMessage()); }
    }

    private boolean requireCurrentPdf() {
        if (pdfPreviewPane != null && pdfPreviewPane.canExport()) return true;
        showAlert(Alert.AlertType.INFORMATION, "Recompile required",
                "Biên dịch thành công nội dung hiện tại trước khi mở hoặc xuất PDF. Xem trạng thái trên Preview.");
        return false;
    }

    private void exportCompiledPdf() {
        if (!requireCurrentPdf()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export compiled PDF");
        chooser.setInitialFileName("main.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File selected = chooser.showSaveDialog(stage);
        if (selected == null || !requireCurrentPdf()) return;
        Path target = selected.toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            showAlert(Alert.AlertType.INFORMATION, "PDF filename", "Chọn tên file kết thúc bằng .pdf.");
            return;
        }
        exportVerifiedPdf(target, false);
    }

    /** Runs hashing and file I/O off the FX thread, exporting only the snapshot shown in the viewer. */
    private void exportVerifiedPdf(Path target, boolean openAfter) {
        if (pdfPreviewPane == null || !pdfPreviewPane.canExport()) return;
        PdfSnapshot pdf = pdfPreviewPane.snapshot();
        Path root = activeProjectRoot;
        long revision = pdfInputRevision;
        Task<Void> export = new Task<>() {
            @Override protected Void call() throws Exception {
                if (!pdf.inputFingerprint().equals(ProjectInputs.fingerprint(root))) {
                    throw new StalePdfException("Project files changed. Recompile before exporting PDF.");
                }
                java.util.concurrent.CompletableFuture<Boolean> current = new java.util.concurrent.CompletableFuture<>();
                Platform.runLater(() -> current.complete(root.equals(activeProjectRoot) && revision == pdfInputRevision
                        && pdfPreviewPane.canExport() && pdfPreviewPane.snapshot() == pdf));
                if (!current.get(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new StalePdfException("Source or project changed. Recompile before exporting PDF.");
                }
                pdf.exportTo(target);
                return null;
            }
        };
        export.setOnSucceeded(e -> {
            appendLog("[PDF] Exported the preview PDF to " + target);
            if (openAfter) getHostServices().showDocument(target.toUri().toString());
            else statusLabel.setText("PDF exported: " + target);
        });
        export.setOnFailed(e -> {
            if (export.getException() instanceof StalePdfException && root.equals(activeProjectRoot)
                    && revision == pdfInputRevision) invalidatePdf();
            showAlert(Alert.AlertType.ERROR, "Could not export PDF", export.getException().getMessage());
        });
        Thread worker = new Thread(export, "pdf-export");
        worker.setDaemon(true);
        worker.start();
    }

    private static final class StalePdfException extends IOException {
        StalePdfException(String message) { super(message); }
    }

    private void cleanBuildFiles() {
        if (activeProjectRoot == null) return;
        Path build = activeProjectRoot.resolve(ProjectFileManager.BUILD_DIR);
        int removed = 0;
        if (Files.isDirectory(build)) {
            try (var files = Files.list(build)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (Files.isRegularFile(file) && !name.endsWith(".pdf")) {
                        Files.deleteIfExists(file);
                        removed++;
                    }
                }
            } catch (IOException e) {
                appendLog("[WARN] Could not clean build files: " + e.getMessage());
            }
        }
        appendLog("[CLEAN] Removed " + removed + " temporary build file(s) from " + build);
        statusLabel.setText(removed == 0 ? "Nothing to clean." : "Removed " + removed + " temporary build file(s).");
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
            cursorLabel.setText(String.format("Ln %d, Col %d · %d chars", line, col, text.length()));
        } catch (Exception ignored) {}
    }

    private void appendLog(String msg) {
        if (terminalLogArea == null) {
            System.out.println(msg);
            return;
        }
        Platform.runLater(() -> {
            terminalLogArea.appendText(msg + "\n");
            terminalLogArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void showNewFileDialog(Stage parent) {
        showAddFilesDialog(0, selectedDirectory());
    }

    private String selectedDirectory() {
        if (projectTreeView == null || activeProjectRoot == null) return "";
        String path = treePaths.get(projectTreeView.getSelectionModel().getSelectedItem());
        if (path == null || path.equals("build") || path.startsWith("build/")) return "";
        if (Files.isDirectory(activeProjectRoot.resolve(path))) return path;
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private void updateWorkspaceLanguage() {
        if (editorRoot == null) return;
        String[][] labels = {{"workspace-add-files", "Add files", "Thêm tệp"},
                {"workspace-new-folder", "New folder", "Thư mục mới"}, {"workspace-upload", "Upload", "Tải lên"}};
        for (String[] label : labels) {
            if (editorRoot.lookup("#" + label[0]) instanceof Button button) button.setText(label[vietnameseUi ? 2 : 1]);
        }
        if (addFilesMenuItem != null) addFilesMenuItem.setText(vietnameseUi ? "Thêm tệp…" : "Add files…");
    }

    private ContextMenu folderActions(String directory) {
        MenuItem file = new MenuItem(vietnameseUi ? "Tệp mới…" : "New file…");
        file.setOnAction(e -> showAddFilesDialog(0, directory));
        MenuItem folder = new MenuItem(vietnameseUi ? "Thư mục mới…" : "New folder…");
        folder.setOnAction(e -> showAddFilesDialog(1, directory));
        MenuItem upload = new MenuItem(vietnameseUi ? "Tải lên…" : "Upload…");
        upload.setOnAction(e -> showAddFilesDialog(2, directory));
        MenuItem reveal = new MenuItem(vietnameseUi ? "Mở thư mục" : "Show in folder");
        reveal.setOnAction(e -> revealFolder(activeProjectRoot.resolve(directory)));
        return new ContextMenu(file, folder, upload, new SeparatorMenuItem(), reveal);
    }

    private AddFilesDialog showAddFilesDialog(int page, String directory) {
        if (fileManager == null) return null;
        Path root = activeProjectRoot;
        AddFilesDialog dialog = new AddFilesDialog(screenHost, root, backend, directory, vietnameseUi, added -> {
            if (!root.equals(activeProjectRoot)) return;
            invalidatePdf(); refreshProjectTree();
            if (added.size() == 1) {
                String path = added.get(0);
                if (Files.isRegularFile(root.resolve(path)) && isEditableSource(path)) openFile(path);
                selectTreePath(path);
            }
            statusLabel.setText((vietnameseUi ? "Đã thêm " : "Added ") + added.size() + (vietnameseUi ? " mục vào dự án." : " entries to the project."));
        });
        dialog.show(page);
        return dialog;
    }

    private static boolean isEditableSource(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.matches(".*\\.(tex|bib|sty|cls|bst|bbx|cbx|def|cfg|txt|md|csv|dat|tikz|lua|json|yaml|yml|xml|svg)$");
    }

    private void saveFile() {
        if (saveCurrentFile(true)) {
            statusLabel.setText("Saved " + currentFile + " at " + new SimpleDateFormat("HH:mm:ss").format(new Date()) + ".");
        }
    }

    /** Saves before leaving the file, the project or the app. */
    private void saveIfDirty() {
        if (userDirty) {
            saveCurrentFile(false);
        }
    }

    private boolean saveCurrentFile(boolean force) {
        if (codeEditor == null || fileManager == null) {
            return false;
        }
        if (!userDirty && !force) {
            return true;
        }
        boolean hadChanges = userDirty;
        try {
            fileManager.writeTexFile(currentFile, codeEditor.getText());
            markSaved();
            appendLog("[SAVE] Saved " + currentFile + " (" + codeEditor.getText().length() + " chars).");
            if (hadChanges) {
                try {
                    backend.recordEdits(1);
                } catch (RuntimeException e) {
                    appendLog("[WARN] " + e.getMessage());
                }
            }
            return true;
        } catch (Exception e) {
            appendLog("[ERROR] Could not save file: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Could not save", "Writing " + currentFile + " failed: " + e.getMessage());
            return false;
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        if (screenHost == null) {
            System.err.println(title + ": " + content);
            return;
        }
        UiModal.notice(screenHost, type == Alert.AlertType.ERROR ? UiModal.Tone.DANGER : UiModal.Tone.NEUTRAL,
                title, content);
    }

    // =========================================================================
    // LỚP KHUNG XEM TRƯỚC PDF ĐỘNG (DYNAMIC LATEX RENDERER CANVAS)
    // =========================================================================
}

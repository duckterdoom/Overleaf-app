package com.overleaf.client.ui;

import com.overleaf.client.compiler.LatexCompilerService;
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
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
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
 * - M1: Giao diện JavaFX hiện đại, Canvas xem trước PDF ĐỘNG (Dynamic LaTeX Renderer),
 *       tự động phân trang chuẩn A4, xử lý ngắt dòng mượt mà.
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
    private PdfCanvasPane pdfCanvasPane;      // Khung xem trước PDF động (Dynamic Canvas)
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
            backend.rememberProject(activeProjectName);
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
        lastPdf = Files.isRegularFile(fileManager.pdfPathFor(LocalBackend.MAIN_FILE))
                ? fileManager.pdfPathFor(LocalBackend.MAIN_FILE) : null;
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
        if (isMainFile()) {
            pdfCanvasPane.updatePreview(content);
        }
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
                    if (isMainFile()) pdfCanvasPane.updatePreview(content);
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

        HBox actionGroup = new HBox(4, btnRecompile, save, overflow);
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

        MenuItem newFile = new MenuItem("New file…");
        newFile.setOnAction(e -> showNewFileDialog(stage));

        MenuItem save = new MenuItem("Save");
        save.setOnAction(e -> saveFile());

        MenuItem compile = new MenuItem("Recompile");
        compile.setOnAction(e -> triggerRecompile());

        MenuItem openPdf = new MenuItem("Open compiled PDF");
        openPdf.setOnAction(e -> openCompiledPdf());

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

        overflow.getItems().addAll(newFile, save, compile, openPdf, clean, revealFolder, new SeparatorMenuItem(),
                connect, offline, reconnect, queue, disconnect, new SeparatorMenuItem(), about, signOutItem);
        return overflow;
    }

    private SplitPane createMainWorkspace(Stage stage, String initialContent) {
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.getStyleClass().add("editor-split");

        VBox leftPane = createLeftPanel(stage);
        SplitPane centerSplit = createCenterEditorPanel(initialContent);
        pdfCanvasPane = new PdfCanvasPane();

        mainSplit.getItems().addAll(leftPane, centerSplit, pdfCanvasPane);
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
                boolean folder = treeItem != null && !treeItem.isLeaf();
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
            if (path != null) {
                fileActions(path).show(projectTreeView, e.getScreenX(), e.getScreenY());
            }
        });
        projectTreeView.setOnMouseClicked(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            TreeItem<String> item = projectTreeView.getSelectionModel().getSelectedItem();
            if (item == null || !item.isLeaf()) return;
            String path = treePaths.get(item);
            if (path == null) return;
            if (path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                if (e.getClickCount() == 2) {
                    getHostServices().showDocument(activeProjectRoot.resolve(path).toUri().toString());
                }
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

        Button newFile = sideAction("New file", UiSupport.PLUS, () -> showNewFileDialog(stage));
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
        outlineScroll.setPrefHeight(180);
        outlineScroll.setMinHeight(120);
        outlineScroll.setMaxHeight(260);

        Label outlineTitle = new Label("OUTLINE");
        outlineTitle.getStyleClass().add("side-section");
        VBox outlineBox = new VBox(4, outlineTitle, outlineScroll);
        outlineBox.getStyleClass().add("outline-box");

        Region sideDivider = new Region();
        sideDivider.getStyleClass().add("side-divider");

        VBox filesBox = new VBox(2, newFile, refresh, treeBox, sideDivider, outlineBox);
        VBox.setVgrow(treeBox, Priority.ALWAYS);

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
                if (path != null && !path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
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
                if (!folder.getChildren().isEmpty()) {
                    parent.getChildren().add(folder);
                }
            } else {
                TreeItem<String> leaf = new TreeItem<>(entry.getName());
                treePaths.put(leaf, path);
                parent.getChildren().add(leaf);
            }
        }
    }

    private boolean shouldShowInTree(File file, int depth) {
        String name = file.getName();
        if (file.isDirectory()) {
            if (depth >= 3) return false;
            if (name.startsWith(".") || name.equals("target") || name.equals("node_modules")) return false;
            // build/ only matters for the PDF it holds.
            return containsLatexFiles(file, depth);
        }
        if (name.startsWith(".")) return false;
        return isLatexFile(file);
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
            case AppSettings.LANGUAGE -> vietnameseUi = "vi".equals(settings.get(AppSettings.LANGUAGE));
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
        clearLog.setStyle("-fx-font-size: 10.5px; -fx-padding: 2 8 2 8;");
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
        userDirty = true;
        if (dirtyLabel != null) {
            dirtyLabel.setVisible(true);
            dirtyLabel.setManaged(true);
        }
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
            if (pdfCanvasPane != null && isMainFile()) {
                pdfCanvasPane.updatePreview(newText);
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
                        pdfCanvasPane.updatePreview(lastEditorContent);
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
            pdfCanvasPane.updatePreview(lastEditorContent);
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
        if (codeEditor == null || btnRecompile == null) {
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
        btnRecompile.setDisable(true);
        compileProgressBar.setVisible(true);
        compileProgressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Compiling with " + selectedCompiler + "…");

        appendLog("\n========================================================");
        appendLog("[BUILD] Started " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
        appendLog("[BUILD] Engine: " + selectedCompiler + " | Source: " + LocalBackend.MAIN_FILE
                + " | Folder: " + activeProjectRoot);

        Path compiledRoot = activeProjectRoot;
        String previewSource = isMainFile() ? codeEditor.getText() : readQuietly(LocalBackend.MAIN_FILE);
        compilerService.compileAsync(compiledRoot.toString(), LocalBackend.MAIN_FILE, engine,
                new LatexCompilerService.CompileCallback() {
            @Override
            public void onSuccess(LatexCompilerService.CompileResult result) {
                Platform.runLater(() -> {
                    finishCompileUi();
                    if (!compiledRoot.equals(activeProjectRoot)) return;
                    lastPdf = result.pdfPath();
                    try {
                        backend.recordCompile();
                    } catch (RuntimeException e) {
                        appendLog("[WARN] " + e.getMessage());
                    }
                    statusLabel.setText("Compiled in " + result.durationMillis() + " ms · open the PDF from ⋯ or the file tree.");
                    appendLog(result.log());
                    appendLog("[SUCCESS] PDF written to " + result.pdfPath());
                    pdfCanvasPane.updatePreview(previewSource);
                    refreshProjectTree();
                });
            }

            @Override
            public void onError(int exitCode, String errorLog) {
                Platform.runLater(() -> {
                    finishCompileUi();
                    if (!compiledRoot.equals(activeProjectRoot)) return;
                    appendLog(errorLog);
                    List<LatexCompilerService.LatexIssue> issues = LatexCompilerService.parseErrors(errorLog);
                    for (LatexCompilerService.LatexIssue issue : issues) {
                        appendLog("[LATEX ERROR] " + issue.file() + ":" + issue.line() + " " + issue.message());
                    }
                    if (exitCode == LatexCompilerService.EXIT_START_FAILED) {
                        statusLabel.setText("No LaTeX toolchain found. Install TeX Live / MacTeX / MiKTeX (latexmk).");
                    } else if (exitCode == LatexCompilerService.EXIT_TIMEOUT) {
                        statusLabel.setText("Compile timed out. The preview shows the current source.");
                    } else if (!issues.isEmpty()) {
                        LatexCompilerService.LatexIssue first = issues.get(0);
                        statusLabel.setText(issues.size() + " LaTeX error(s) · first at " + first.file() + ":" + first.line());
                    } else {
                        statusLabel.setText("Compile failed (exit " + exitCode + "). See Build output.");
                    }
                    pdfCanvasPane.updatePreview(previewSource);
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

    private String readQuietly(String relativePath) {
        try {
            return fileManager.readTexFile(relativePath);
        } catch (IOException e) {
            return "";
        }
    }

    private void openCompiledPdf() {
        if (fileManager == null) return;
        Path pdf = lastPdf != null ? lastPdf : fileManager.pdfPathFor(LocalBackend.MAIN_FILE);
        if (!Files.isRegularFile(pdf)) {
            showAlert(Alert.AlertType.INFORMATION, "No PDF yet",
                    "Recompile first. The PDF is written to " + LocalBackend.MAIN_FILE.replace(".tex", ".pdf")
                            + " in the project's build folder.");
            return;
        }
        getHostServices().showDocument(pdf.toUri().toString());
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
        if (fileManager == null) {
            return;
        }
        UiModal modal = UiModal.create(screenHost, UiSupport.FILE, UiModal.Tone.NEUTRAL,
                "New file", "Add a LaTeX source or bibliography file to " + activeProjectName + ".");
        TextField name = modal.field("File name", "chapter1.tex", "chapter1.tex or sections/intro.tex");
        modal.actions("A name without an extension gets .tex", "Cancel", "Create file", UiModal.Tone.NEUTRAL, () -> {
            String fileName = name.getText() == null ? "" : name.getText().trim();
            if (!fileName.isEmpty() && !fileName.contains(".")) {
                fileName = fileName + ".tex";
            }
            String problem = validateNewFileName(fileName);
            if (problem != null) {
                modal.error(name, problem);
                return false;
            }
            if (Files.exists(activeProjectRoot.resolve(fileName))) {
                modal.error(name, fileName + " is already in this project.");
                return false;
            }
            try {
                fileManager.createFile(fileName);
            } catch (IOException | RuntimeException e) {
                modal.error(name, "Could not create the file: " + e.getMessage());
                return false;
            }
            appendLog("[FILE] Created " + fileName);
            refreshProjectTree();
            if (openFile(fileName)) {
                selectTreePath(fileName);
            }
            if (fileName.endsWith(".tex")) {
                statusLabel.setText("Created " + fileName + ". Add \\input{" + fileName.replaceAll("\\.tex$", "")
                        + "} to main.tex to include it.");
            } else {
                statusLabel.setText("Created " + fileName + ".");
            }
            return true;
        });
        modal.show();
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
    public static class PdfCanvasPane extends VBox {
        // One canvas per page: a single canvas for the whole document exceeds the GPU's
        // 16384 px texture limit after about 16 pages and the preview goes blank.
        private final VBox pagesBox = new VBox();
        private final javafx.beans.property.DoubleProperty docWidth = new javafx.beans.property.SimpleDoubleProperty(580);
        private final javafx.beans.property.DoubleProperty docHeight = new javafx.beans.property.SimpleDoubleProperty(840);
        private final Label zoomLabel;
        private static final int PREVIEW_PAUSE_MS = 250;
        /** Beyond this the preview stops drawing pages; the compiled PDF has the rest. */
        private static final int MAX_PREVIEW_PAGES = 50;
        private long lastDrawMillis;
        private final javafx.animation.PauseTransition redraw = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(PREVIEW_PAUSE_MS));
        private double zoomFactor = 1.0;
        private boolean autoFit = true;
        private String currentTexCode = "";

        public PdfCanvasPane() {
            getStyleClass().add("preview-pane");
            VBox.setVgrow(this, Priority.ALWAYS);

            HBox toolBar = new HBox(6);
            toolBar.getStyleClass().add("preview-toolbar");
            toolBar.setAlignment(Pos.CENTER_LEFT);

            Label title = new Label("Preview");
            title.getStyleClass().add("pane-title");
            Label sub = new Label("A4");
            sub.getStyleClass().add("pane-sub");

            Region spacer = UiSupport.spacer();

            Button btnZoomOut = new Button("−");
            btnZoomOut.getStyleClass().add("zoom-button");
            btnZoomOut.setTooltip(UiSupport.tooltip("Zoom out"));

            zoomLabel = new Label("100%");
            zoomLabel.getStyleClass().add("pane-sub");
            zoomLabel.setPadding(new Insets(0, 4, 0, 4));

            Button btnZoomIn = new Button("+");
            btnZoomIn.getStyleClass().add("zoom-button");
            btnZoomIn.setTooltip(UiSupport.tooltip("Zoom in"));

            Button btnFit = new Button("Fit");
            btnFit.getStyleClass().add("zoom-button");
            btnFit.setTooltip(UiSupport.tooltip("Fit page to width"));

            btnZoomIn.setOnAction(e -> {
                autoFit = false;
                if (zoomFactor < 1.6) {
                    zoomFactor += 0.1;
                    updateZoom();
                }
            });

            btnZoomOut.setOnAction(e -> {
                autoFit = false;
                if (zoomFactor > 0.5) {
                    zoomFactor -= 0.1;
                    updateZoom();
                }
            });

            btnFit.setOnAction(e -> {
                autoFit = true;
                fitToPane(getWidth(), getHeight());
            });

            toolBar.getChildren().addAll(title, sub, spacer, btnZoomOut, zoomLabel, btnZoomIn, btnFit);

            pagesBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            pagesBox.setAlignment(Pos.TOP_CENTER);

            StackPane canvasHolder = new StackPane(pagesBox);
            canvasHolder.setPadding(new Insets(20));
            canvasHolder.setAlignment(Pos.TOP_CENTER);
            canvasHolder.getStyleClass().add("preview-holder");
            canvasHolder.prefWidthProperty().bind(docWidth.add(40));
            canvasHolder.prefHeightProperty().bind(docHeight.add(40));

            ScrollPane scrollPane = new ScrollPane(canvasHolder);
            scrollPane.getStyleClass().add("preview-scroll");
            scrollPane.setFitToWidth(false);
            scrollPane.setFitToHeight(false);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            canvasHolder.minWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(docWidth.get() + 40, scrollPane.getViewportBounds().getWidth()),
                    docWidth, scrollPane.viewportBoundsProperty()));
            canvasHolder.minHeightProperty().bind(Bindings.createDoubleBinding(
                    () -> Math.max(docHeight.get() + 40, scrollPane.getViewportBounds().getHeight()),
                    docHeight, scrollPane.viewportBoundsProperty()));
            VBox.setVgrow(scrollPane, Priority.ALWAYS);

            getChildren().addAll(toolBar, scrollPane);

            widthProperty().addListener((obs, oldW, newW) -> {
                if (autoFit && newW.doubleValue() > 0) {
                    fitToPane(newW.doubleValue(), getHeight());
                }
            });
            heightProperty().addListener((obs, oldH, newH) -> {
                if (autoFit && newH.doubleValue() > 0) {
                    fitToPane(getWidth(), newH.doubleValue());
                }
            });

            redraw.setOnFinished(e -> {
                lastDrawMillis = System.currentTimeMillis();
                drawDynamicA4Paper(currentTexCode);
            });
            drawDynamicA4Paper("");
        }

        private void fitToPane(double paneWidth, double paneHeight) {
            if (paneWidth <= 0) return;
            double byWidth = (paneWidth - 92) / 520.0;
            double byHeight = paneHeight > 0 ? (paneHeight - 77) / 740.0 : byWidth;
            double scale = Math.min(byWidth, byHeight);
            scale = Math.max(0.5, Math.min(1.0, scale));
            scale = Math.floor(scale * 20) / 20.0;
            if (Math.abs(scale - zoomFactor) > 0.001) {
                zoomFactor = scale;
                updateZoom();
            } else {
                zoomLabel.setText(String.format("%d%%", (int) Math.round(zoomFactor * 100)));
            }
        }

        /** Redraws now if the preview has been idle, otherwise once typing pauses, so long files stay responsive. */
        public void updatePreview(String texContent) {
            this.currentTexCode = (texContent != null) ? texContent : "";
            long now = System.currentTimeMillis();
            if (now - lastDrawMillis >= PREVIEW_PAUSE_MS && redraw.getStatus() != javafx.animation.Animation.Status.RUNNING) {
                lastDrawMillis = now;
                drawDynamicA4Paper(this.currentTexCode);
            } else {
                redraw.playFromStart();
            }
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
                    doc.date = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(new Date());
                } else {
                    doc.date = cleanD;
                }
            } else {
                doc.date = new SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH).format(new Date());
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
            boolean inAbstract = false;
            StringBuilder eqBuf = new StringBuilder();

            for (String raw : lines) {
                String line = raw.trim();

                if (line.startsWith("%") || line.equals("\\maketitle")) continue;
                if (line.startsWith("\\begin{abstract}")) {
                    inAbstract = true;
                    continue;
                }
                if (line.startsWith("\\end{abstract}")) {
                    inAbstract = false;
                    continue;
                }
                if (inAbstract) continue;

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

            // 3. Kích thước mỗi trang: một Canvas riêng cho từng trang, có lề cho bóng đổ
            double pagePad = 20;
            double canvasW = paperW + 40;
            double paperX = (canvasW - paperW) / 2;
            List<Canvas> pageCanvases = new ArrayList<>();

            // 4. Vẽ từng trang A4 chân thực (tối đa MAX_PREVIEW_PAGES trang)
            int drawnPages = Math.min(totalPages, MAX_PREVIEW_PAGES);
            for (int pIdx = 0; pIdx < drawnPages; pIdx++) {
                Canvas pageCanvas = new Canvas(canvasW, pageH + 2 * pagePad);
                pageCanvases.add(pageCanvas);
                GraphicsContext gc = pageCanvas.getGraphicsContext2D();
                double pageY = pagePad;

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
            pagesBox.getChildren().setAll(pageCanvases);
            double noteHeight = 0;
            if (drawnPages < totalPages) {
                Label more = new Label("Preview shows the first " + drawnPages + " of " + totalPages
                        + " pages. Recompile for the full PDF.");
                more.getStyleClass().add("pane-sub");
                more.setPadding(new Insets(4, 0, 12, 0));
                pagesBox.getChildren().add(more);
                noteHeight = 36;
            }
            docWidth.set(canvasW);
            docHeight.set(drawnPages * (pageH + 2 * pagePad) + noteHeight);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

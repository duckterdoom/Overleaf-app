package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.ActivityGateway;
import com.overleaf.client.ui.backend.AuthGateway;
import com.overleaf.client.ui.backend.ProfileGateway;
import com.overleaf.client.ui.backend.ProjectGateway;
import com.overleaf.client.ui.backend.ProjectGateway.ProjectSummary;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Home dashboard: workspace rail, continue-writing hero, recent projects and
 * an activity card. Purely presentational; projects and activity come from
 * gateways so the persistence layer can be attached later.
 */
public class HomeView extends BorderPane {

    private static final int WEEKS = 52;
    private static final Locale VI = Locale.forLanguageTag("vi-VN");

    private final ProjectGateway projectGateway;
    private final ProfileGateway profileGateway;
    /** (old, renamed) after a rename; (old, null) after a delete. */
    private final BiConsumer<ProjectSummary, ProjectSummary> onProjectChanged;
    private final Consumer<java.nio.file.Path> onReveal;
    private final ActivityGateway activityGateway;
    private final AuthGateway.Session session;
    private final Consumer<ProjectSummary> onOpen;
    private final Runnable onSignOut;
    private final Consumer<Boolean> onLanguageChange;
    private String activeProjectName;
    private final StackPane host;
    private final boolean vi;

    private final VBox railProjects = new VBox(2);
    private final VBox projectRows = new VBox(1);
    private final Button heroProject = new Button();
    private final Label heroMeta = new Label();
    private final Label projectCount = new Label();
    private final Label folderValue = new Label("—");
    private final Label accountValue = new Label("—");
    private final ActivityHeatmap heatmap;
    private final Map<LocalDate, Integer> activityData;

    private List<ProjectSummary> projects = new ArrayList<>();

    public HomeView(ProjectGateway projectGateway,
                    ProfileGateway profileGateway,
                    ActivityGateway activityGateway,
                    AuthGateway.Session session,
                    String activeProjectName,
                    StackPane host,
                    Consumer<ProjectSummary> onOpen,
                    Runnable onSignOut,
                    boolean vietnamese,
                    Consumer<Boolean> onLanguageChange,
                    BiConsumer<ProjectSummary, ProjectSummary> onProjectChanged,
                    Consumer<java.nio.file.Path> onReveal) {
        this.projectGateway = projectGateway;
        this.profileGateway = profileGateway;
        this.activityGateway = activityGateway;
        this.session = session;
        this.activeProjectName = activeProjectName;
        this.host = host;
        this.onOpen = onOpen;
        this.onSignOut = onSignOut;
        this.vi = vietnamese;
        this.onLanguageChange = onLanguageChange;
        this.onProjectChanged = onProjectChanged;
        this.onReveal = onReveal;

        LocalDate today = LocalDate.now();
        this.activityData = activityGateway.editsByDay(today.minusDays(370), today);
        this.heatmap = new ActivityHeatmap(activityData, WEEKS, 14, vi);

        getStyleClass().add("home-root");
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setLeft(buildRail());
        setCenter(buildCenter());
        refreshProjects();
    }

    private String t(String en, String viText) {
        return vi ? viText : en;
    }

    private DateTimeFormatter dateFormat() {
        return vi
                ? DateTimeFormatter.ofPattern("EEEE, d MMMM", VI)
                : DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH);
    }

    private VBox buildRail() {
        VBox rail = new VBox(10);
        rail.getStyleClass().add("home-rail");

        HBox brand = new HBox(UiSupport.brandLockup(22, "Overleaf"));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("rail-brand");

        Button newProject = new Button(t("New project", "Dự án mới"));
        newProject.setGraphic(UiSupport.iconBox(UiSupport.PLUS, 13, Color.WHITE, 14));
        newProject.getStyleClass().add("btn-primary");
        newProject.setMaxWidth(Double.MAX_VALUE);
        newProject.setOnAction(e -> newProjectDialog());

        Label section = new Label(t("WORKSPACES", "KHÔNG GIAN"));
        section.getStyleClass().add("rail-section");

        VBox.setMargin(section, new Insets(8, 0, 0, 0));
        VBox.setMargin(newProject, new Insets(4, 0, 2, 0));

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);

        rail.getChildren().addAll(brand, newProject, section, railProjects, grow, buildUserChip());
        return rail;
    }

    private HBox buildUserChip() {
        Label avatar = new Label();
        avatar.getStyleClass().addAll("avatar", "avatar-clickable");
        UiSupport.applyAvatar(avatar, session.name(), profileGateway.avatar(session), 28);

        Label name = new Label(session.name());
        name.getStyleClass().add("rail-item-name");
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMaxWidth(128);

        Label host = new Label(session.offline()
                ? t("Offline session", "Phiên ngoại tuyến")
                : session.email());
        host.getStyleClass().add("rail-item-meta");
        host.setTextOverrun(OverrunStyle.ELLIPSIS);
        host.setMaxWidth(128);

        VBox info = new VBox(1, name, host);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button signOut = new Button();
        signOut.setGraphic(UiSupport.iconBox(UiSupport.SIGN_OUT, 14, Color.web("#7A7A74"), 16));
        signOut.getStyleClass().add("icon-button");
        signOut.setTooltip(UiSupport.tooltip(t("Sign out", "Đăng xuất")));
        signOut.setOnAction(e -> onSignOut.run());
        signOut.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);

        HBox chip = new HBox(9, avatar, info, signOut);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("user-chip");
        Tooltip.install(chip, UiSupport.tooltip(t("Change profile picture", "Đổi ảnh đại diện")));
        chip.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                ProfilePhotoDialog.show(this.host, profileGateway, session, vi,
                        () -> UiSupport.applyAvatar(avatar, session.name(), profileGateway.avatar(session), 28));
            }
        });
        return chip;
    }

    private ScrollPane buildCenter() {
        VBox content = new VBox(18);
        content.getStyleClass().add("home-content");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("home-header");
        Label today = new Label(LocalDate.now().format(dateFormat()));
        today.getStyleClass().add("home-date");
        header.getChildren().addAll(UiSupport.spacer(), today, buildLanguageButton());

        HBox board = new HBox(16, buildProjectsCard(), buildSideColumn());
        board.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(board.getChildren().get(0), Priority.ALWAYS);

        content.getChildren().addAll(header, buildActivityCard(), buildContinueCard(), board);

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("home-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    private MenuButton buildLanguageButton() {
        MenuButton language = new MenuButton(vi ? "VI" : "EN");
        language.setGraphic(UiSupport.iconBox(UiSupport.GLOBE, 13, Color.web("#6B6B65"), 16));
        language.getStyleClass().add("lang-button");
        language.setTooltip(UiSupport.tooltip(t("Change language", "Đổi ngôn ngữ")));

        ToggleGroup group = new ToggleGroup();
        RadioMenuItem english = new RadioMenuItem("English");
        RadioMenuItem vietnamese = new RadioMenuItem("Tiếng Việt");
        english.setToggleGroup(group);
        vietnamese.setToggleGroup(group);
        english.setSelected(!vi);
        vietnamese.setSelected(vi);
        english.setOnAction(e -> {
            if (vi) {
                onLanguageChange.accept(false);
            }
        });
        vietnamese.setOnAction(e -> {
            if (!vi) {
                onLanguageChange.accept(true);
            }
        });
        language.getItems().addAll(english, vietnamese);
        return language;
    }

    private HBox buildContinueCard() {
        Label eyebrow = new Label(t("CONTINUE WRITING", "TIẾP TỤC VIẾT"));
        eyebrow.getStyleClass().add("card-eyebrow");

        heroProject.getStyleClass().add("hero-project");
        heroProject.setText(t("No project", "Chưa có dự án") + "  ⌄");
        heroProject.setOnAction(e -> showProjectMenu(heroProject));

        heroMeta.getStyleClass().add("hero-meta");
        heroMeta.setTextOverrun(OverrunStyle.ELLIPSIS);

        Button open = new Button(t("Open editor", "Mở trình soạn thảo"));
        open.setGraphic(UiSupport.iconBox(UiSupport.PLAY, 12, Color.WHITE, 13));
        open.getStyleClass().add("btn-primary");
        open.setOnAction(e -> {
            ProjectSummary active = activeProject();
            if (active != null) {
                onOpen.accept(active);
            }
        });

        Button newProject = new Button(t("New project", "Dự án mới"));
        newProject.setGraphic(UiSupport.iconBox(UiSupport.PLUS, 12, Color.web("#2B2B28"), 13));
        newProject.getStyleClass().add("btn-secondary");
        newProject.setOnAction(e -> newProjectDialog());

        HBox actions = new HBox(8, open, newProject);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox copy = new VBox(eyebrow, heroProject, heroMeta, actions);
        copy.setAlignment(Pos.CENTER_LEFT);
        copy.setMinWidth(0);
        VBox.setMargin(heroProject, new Insets(5, 0, 4, 0));
        VBox.setMargin(actions, new Insets(16, 0, 0, 0));

        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox card = new HBox(24, copy, buildPaperThumb());
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("continue-card");
        return card;
    }

    private StackPane buildPaperThumb() {
        VBox sheet = new VBox(5);
        sheet.getStyleClass().add("thumb-sheet");
        sheet.setPadding(new Insets(13, 12, 12, 12));
        sheet.setPrefSize(108, 144);
        sheet.setMinSize(108, 144);
        sheet.setMaxSize(108, 144);
        sheet.getChildren().addAll(
                thumbBar(20, "thumb-accent"),
                gap(5),
                thumbBar(66, "thumb-strong"),
                thumbBar(78, "thumb-line"),
                thumbBar(58, "thumb-line"),
                gap(7),
                thumbBar(72, "thumb-line"),
                thumbBar(80, "thumb-line"),
                thumbBar(64, "thumb-line"),
                gap(7),
                thumbBar(38, "thumb-strong"),
                thumbBar(76, "thumb-line"),
                thumbBar(70, "thumb-line"),
                thumbBar(48, "thumb-line"));

        StackPane front = new StackPane(sheet);
        front.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        front.setRotate(-4);

        Region back = new Region();
        back.getStyleClass().add("thumb-sheet-back");
        back.setPrefSize(108, 144);
        back.setMaxSize(108, 144);
        back.setRotate(5);

        StackPane stack = new StackPane(back, front);
        stack.setMinSize(128, 158);
        stack.setPrefSize(128, 158);
        stack.setMaxSize(128, 158);
        stack.getStyleClass().add("thumb-wrap");
        return stack;
    }

    private Region thumbBar(double width, String styleClass) {
        Region bar = new Region();
        bar.getStyleClass().addAll("thumb-bar", styleClass);
        bar.setPrefSize(width, styleClass.equals("thumb-strong") || styleClass.equals("thumb-accent") ? 4 : 3);
        bar.setMaxSize(width, styleClass.equals("thumb-strong") || styleClass.equals("thumb-accent") ? 4 : 3);
        return bar;
    }

    private Region gap(double height) {
        Region region = new Region();
        region.setMinHeight(height);
        region.setPrefHeight(height);
        return region;
    }

    private VBox buildProjectsCard() {
        VBox card = new VBox();
        card.getStyleClass().add("panel");
        card.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(t("Recent projects", "Dự án gần đây"));
        title.getStyleClass().add("panel-title");
        projectCount.getStyleClass().add("count-pill");

        Button add = new Button(t("New project", "Dự án mới"));
        add.setGraphic(UiSupport.iconBox(UiSupport.PLUS, 11, Color.web("#2B2B28"), 12));
        add.getStyleClass().add("btn-secondary");
        add.setOnAction(e -> newProjectDialog());

        HBox head = new HBox(9, title, projectCount, UiSupport.spacer(), add);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getStyleClass().add("panel-head");

        HBox columns = new HBox(10);
        columns.getStyleClass().add("table-head");
        columns.setMaxWidth(Double.MAX_VALUE);
        Region iconGap = new Region();
        iconGap.setMinWidth(30);
        iconGap.setPrefWidth(30);
        Label projectHead = new Label(t("PROJECT", "DỰ ÁN"));
        Label modifiedHead = new Label(t("MODIFIED", "CẬP NHẬT"));
        Label filesHead = new Label(t("FILES", "TỆP"));
        projectHead.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(projectHead, Priority.ALWAYS);
        fixedWidth(modifiedHead, 112);
        fixedWidth(filesHead, 48);
        Region arrowGap = new Region();
        arrowGap.setMinWidth(18);
        arrowGap.setPrefWidth(18);
        columns.getChildren().addAll(iconGap, projectHead, modifiedHead, filesHead, arrowGap);

        VBox rows = new VBox(1, columns, projectRows);
        rows.getStyleClass().add("panel-body");
        VBox.setVgrow(rows, Priority.ALWAYS);

        card.getChildren().addAll(head, rows);
        return card;
    }

    private void fixedWidth(Label label, double width) {
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setAlignment(Pos.CENTER_RIGHT);
    }

    private VBox buildSideColumn() {
        VBox side = new VBox(16, buildWorkspaceCard());
        side.getStyleClass().add("home-sidecol");
        return side;
    }

    private VBox buildActivityCard() {
        int totalEdits = activityData.values().stream().mapToInt(Integer::intValue).sum();
        int streak = currentStreak();

        Label title = new Label(t("Activity", "Hoạt động"));
        title.getStyleClass().add("panel-title");
        Label scope = new Label(t("Last 52 weeks", "52 tuần qua"));
        scope.getStyleClass().add("pane-sub");

        Label bestChip = new Label();
        bestChip.getStyleClass().add("best-chip");

        Button play = new Button();
        play.setGraphicTextGap(6);
        play.getStyleClass().addAll("btn-secondary", "snake-button");
        play.setOnAction(e -> {
            if (heatmap.isGameActive()) {
                heatmap.stopGame();
            } else {
                heatmap.startGame();
                heatmap.requestFocus();
            }
        });

        Label hint = new Label();
        hint.getStyleClass().add("heat-legend");

        Runnable syncGameUi = () -> {
            boolean active = heatmap.isGameActive();
            play.setText(active ? t("Back to heatmap", "Quay lại lưới") : t("Play Snake", "Chơi Snake"));
            play.setGraphic(UiSupport.iconBox(active ? UiSupport.ARROW_LEFT : UiSupport.PLAY, 10,
                    Color.web("#23664A"), 12));
            hint.setText(active
                    ? t("Arrow keys to steer · Esc to return", "Phím mũi tên để di chuyển · Esc để quay lại")
                    : t("Double-click the grid · or press Play Snake",
                            "Nhấp đúp vào lưới · hoặc bấm Chơi Snake"));
            int best = ActivityHeatmap.bestScore();
            bestChip.setText("BEST " + best);
            bestChip.setVisible(!active && best > 0);
            bestChip.setManaged(!active && best > 0);
        };
        heatmap.gameActiveProperty().addListener((obs, oldActive, active) -> syncGameUi.run());
        syncGameUi.run();

        HBox head = new HBox(10, title, scope, UiSupport.spacer(), bestChip, play);
        head.setAlignment(Pos.CENTER_LEFT);

        HBox stats = new HBox(28,
                statBlock(String.valueOf(totalEdits), t("edits", "chỉnh sửa")),
                statBlock(String.valueOf(activityGateway.compilesThisYear()), t("compiles", "biên dịch")),
                statBlock(String.valueOf(streak), t("day streak", "chuỗi ngày")));
        stats.setAlignment(Pos.CENTER_LEFT);

        Pane chartHolder = new Pane(heatmap);
        heatmap.setManaged(false);
        heatmap.setLayoutX(0);
        heatmap.setLayoutY(0);
        chartHolder.minHeightProperty().bind(heatmap.heightProperty());
        chartHolder.prefHeightProperty().bind(heatmap.heightProperty());

        HBox legend = new HBox(4);
        legend.setAlignment(Pos.CENTER_LEFT);
        Label less = new Label(t("Less", "Ít"));
        less.getStyleClass().add("heat-legend");
        legend.getChildren().add(less);
        for (Color color : ActivityHeatmap.legendColors()) {
            Rectangle square = new Rectangle(9, 9, color);
            square.setArcWidth(4);
            square.setArcHeight(4);
            legend.getChildren().add(square);
        }
        Label more = new Label(t("More", "Nhiều"));
        more.getStyleClass().add("heat-legend");
        legend.getChildren().addAll(more, UiSupport.spacer(), hint);

        VBox card = new VBox(14, head, stats, chartHolder, legend);
        card.getStyleClass().addAll("panel", "activity-card");
        card.widthProperty().addListener((obs, oldWidth, newWidth) ->
                resizeHeatmap(newWidth.doubleValue()));
        Platform.runLater(() -> resizeHeatmap(card.getWidth()));
        return card;
    }

    private void resizeHeatmap(double cardWidth) {
        if (cardWidth <= 0) {
            return;
        }
        double usable = cardWidth - 36;
        double cell = (usable - (WEEKS - 1) * 3) / WEEKS;
        heatmap.setCellSize(Math.max(9, Math.min(17, cell)));
    }

    private VBox statBlock(String value, String label) {
        Label number = new Label(value);
        number.getStyleClass().add("stat-num");
        Label text = new Label(label);
        text.getStyleClass().add("stat-label");
        return new VBox(0, number, text);
    }

    private VBox buildWorkspaceCard() {
        Label title = new Label(t("WORKSPACE", "KHÔNG GIAN LÀM VIỆC"));
        title.getStyleClass().add("side-title");

        Label note = new Label(t("Local compiler · room sync ready",
                "Trình biên dịch cục bộ · sẵn sàng đồng bộ phòng"));
        note.getStyleClass().add("side-note");
        note.setWrapText(true);

        folderValue.setTextOverrun(OverrunStyle.ELLIPSIS);
        accountValue.setTextOverrun(OverrunStyle.ELLIPSIS);
        folderValue.setMaxWidth(198);
        accountValue.setMaxWidth(198);

        VBox card = new VBox(9,
                title,
                envRow(t("Folder", "Thư mục"), folderValue),
                envRow(t("Compiles with", "Biên dịch bằng"), staticValue("pdfLaTeX")),
                envRow(t("Account", "Tài khoản"), accountValue),
                note);
        card.getStyleClass().addAll("panel", "mini-card");
        VBox.setMargin(note, new Insets(4, 0, 0, 0));
        return card;
    }

    private HBox envRow(String key, Label value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("side-key");
        value.getStyleClass().add("side-value");
        HBox row = new HBox(10, keyLabel, UiSupport.spacer(), value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label staticValue(String text) {
        Label value = new Label(text);
        value.getStyleClass().add("side-value");
        return value;
    }

    private void refreshProjects() {
        projects = projectGateway.projects();
        refreshRailProjects();
        refreshProjectRows();

        ProjectSummary active = activeProject();
        heroProject.setText(active == null
                ? t("No project", "Chưa có dự án") + "  ⌄"
                : active.name() + "  ⌄");
        heroMeta.setText(active == null
                ? t("Create a project to start writing.", "Tạo một dự án để bắt đầu viết.")
                : modifiedText(active.modified()) + "  ·  " + filesText(active.files())
                + (active.shared() ? "  ·  " + t("Shared", "Chia sẻ") : ""));
        projectCount.setText(projects.size() + " "
                + t(projects.size() == 1 ? "project" : "projects", "dự án"));
        folderValue.setText(active == null ? "—" : shortenPath(active.folder()));
        accountValue.setText(session.offline()
                ? session.name() + " · " + t("offline", "ngoại tuyến")
                : session.name());
    }

    private String filesText(int files) {
        return files + " " + t(files == 1 ? "file" : "files", "tệp");
    }

    private String modifiedText(String value) {
        if (!vi || value == null) {
            return value;
        }
        String text = value
                .replace("Created just now", "Vừa tạo")
                .replace("Edited just now", "Vừa sửa")
                .replace("Edited yesterday", "Sửa hôm qua")
                .replace("Edited last week", "Sửa tuần trước");
        text = text.replaceAll("^Edited (\\d+) minutes? ago$", "Sửa $1 phút trước")
                .replaceAll("^Edited (\\d+) hours? ago$", "Sửa $1 giờ trước")
                .replaceAll("^Edited (\\d+) days? ago$", "Sửa $1 ngày trước")
                .replaceAll("^Created (\\d+) minutes? ago$", "Tạo $1 phút trước")
                .replaceAll("^Created (\\d+) hours? ago$", "Tạo $1 giờ trước")
                .replaceAll("^Created (\\d+) days? ago$", "Tạo $1 ngày trước");
        return text;
    }

    private void refreshRailProjects() {
        railProjects.getChildren().clear();
        for (ProjectSummary project : projects) {
            boolean active = project.name().equals(activeProjectName);
            Label name = new Label(project.name());
            name.getStyleClass().add("rail-item-name");
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
            name.setMaxWidth(158);
            Label meta = new Label(filesText(project.files())
                    + (project.shared() ? " · " + t("shared", "chia sẻ") : ""));
            meta.getStyleClass().add("rail-item-meta");

            VBox text = new VBox(1, name, meta);
            Circle dot = new Circle(2.5, active ? Color.web("#23664A") : Color.TRANSPARENT);

            HBox item = new HBox(8, dot, text);
            item.setAlignment(Pos.CENTER_LEFT);
            item.getStyleClass().add("rail-item");
            if (active) {
                item.getStyleClass().add("rail-item-active");
            }
            item.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) onOpen.accept(project);
            });
            item.setOnContextMenuRequested(e -> projectActions(project).show(item, e.getScreenX(), e.getScreenY()));
            railProjects.getChildren().add(item);
        }
    }

    private void refreshProjectRows() {
        projectRows.getChildren().clear();
        for (ProjectSummary project : projects) {
            projectRows.getChildren().add(buildProjectRow(project));
        }
        projectRows.getChildren().add(buildNewProjectRow());
    }

    private HBox buildProjectRow(ProjectSummary project) {
        Label icon = new Label(project.name().substring(0, 1).toUpperCase());
        icon.getStyleClass().add("project-icon");

        Label name = new Label(project.name());
        name.getStyleClass().add("project-name");
        HBox nameRow = new HBox(7, name);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        if (project.shared()) {
            Label shared = new Label(t("Shared", "Chia sẻ"));
            shared.getStyleClass().addAll("pill", "pill-green");
            nameRow.getChildren().add(shared);
        }
        HBox.setHgrow(nameRow, Priority.ALWAYS);

        Label modified = new Label(modifiedText(project.modified()));
        modified.getStyleClass().add("project-meta");
        fixedWidth(modified, 112);

        Label files = new Label(String.valueOf(project.files()));
        files.getStyleClass().add("project-meta");
        fixedWidth(files, 48);

        Button more = new Button();
        HBox dots = new HBox(3);
        dots.setAlignment(Pos.CENTER);
        for (int i = 0; i < 3; i++) {
            dots.getChildren().add(new Circle(1.25, Color.web("#8B8B85")));
        }
        more.setGraphic(dots);
        more.getStyleClass().add("icon-button");
        more.setTooltip(UiSupport.tooltip(t("Project actions", "Thao tác dự án")));
        more.setOnAction(e -> {
            ContextMenu menu = projectActions(project);
            menu.show(more, Side.BOTTOM, 0, 4);
        });
        more.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);

        HBox row = new HBox(10, icon, nameRow, modified, files, more);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("project-row");
        if (project.name().equals(activeProjectName)) {
            row.getStyleClass().add("project-row-active");
        }
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) onOpen.accept(project);
        });
        row.setOnContextMenuRequested(e -> projectActions(project).show(row, e.getScreenX(), e.getScreenY()));
        return row;
    }

    private ContextMenu projectActions(ProjectSummary project) {
        MenuItem open = new MenuItem(t("Open", "Mở"));
        open.setOnAction(e -> onOpen.accept(project));
        MenuItem rename = new MenuItem(t("Rename…", "Đổi tên…"));
        rename.setOnAction(e -> renameProjectDialog(project));
        MenuItem reveal = new MenuItem(t("Show folder", "Mở thư mục"));
        reveal.setOnAction(e -> onReveal.accept(java.nio.file.Path.of(project.folder())));
        MenuItem delete = new MenuItem(t("Delete…", "Xóa…"));
        delete.getStyleClass().add("menu-danger");
        delete.setOnAction(e -> deleteProjectDialog(project));
        return new ContextMenu(open, rename, reveal, new SeparatorMenuItem(), delete);
    }

    private void renameProjectDialog(ProjectSummary project) {
        UiModal modal = UiModal.create(host, UiSupport.EDIT, UiModal.Tone.NEUTRAL,
                t("Rename project", "Đổi tên dự án"),
                t("The project folder is renamed on disk.", "Thư mục dự án trên ổ đĩa sẽ được đổi tên."));
        TextField name = modal.field(t("Project name", "Tên dự án"), project.name(), "");
        modal.actions(null, t("Cancel", "Hủy"), t("Rename", "Đổi tên"), UiModal.Tone.NEUTRAL, () -> {
            String value = name.getText() == null ? "" : name.getText().trim();
            if (value.isEmpty()) {
                modal.error(name, t("Give the project a name.", "Hãy đặt tên cho dự án."));
                return false;
            }
            try {
                ProjectSummary renamed = projectGateway.rename(project, value);
                if (project.name().equals(activeProjectName)) activeProjectName = renamed.name();
                onProjectChanged.accept(project, renamed);
            } catch (RuntimeException failure) {
                modal.error(name, failure.getMessage());
                return false;
            }
            refreshProjects();
            return true;
        });
        modal.show();
    }

    private void deleteProjectDialog(ProjectSummary project) {
        UiModal.confirm(host, UiModal.Tone.DANGER,
                t("Delete \"" + project.name() + "\"?", "Xóa \"" + project.name() + "\"?"),
                t("The folder and every file in it move to the app's .trash folder, so you can still recover them by hand.",
                        "Thư mục và mọi tệp bên trong được chuyển vào thư mục .trash của ứng dụng để có thể khôi phục thủ công."),
                t("Delete project", "Xóa dự án"),
                () -> {
                    try {
                        projectGateway.delete(project);
                        onProjectChanged.accept(project, null);
                    } catch (RuntimeException failure) {
                        UiModal.notice(host, UiModal.Tone.DANGER, t("Could not delete", "Không xóa được"), failure.getMessage());
                    }
                    refreshProjects();
                });
    }

    private HBox buildNewProjectRow() {
        Label icon = new Label("+");
        icon.getStyleClass().add("project-icon");

        Label name = new Label(t("New project", "Dự án mới"));
        name.getStyleClass().add("project-name");
        Label meta = new Label(t("Start from a blank LaTeX document",
                "Bắt đầu từ một tài liệu LaTeX trống"));
        meta.getStyleClass().add("project-meta");
        VBox text = new VBox(1, name, meta);

        HBox row = new HBox(10, icon, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("new-project-row");
        row.setOnMouseClicked(e -> newProjectDialog());
        return row;
    }

    private void showProjectMenu(Button anchor) {
        ContextMenu menu = new ContextMenu();
        for (ProjectSummary project : projects) {
            MenuItem item = new MenuItem(project.name());
            item.setOnAction(e -> onOpen.accept(project));
            menu.getItems().add(item);
        }
        menu.getItems().add(new SeparatorMenuItem());
        MenuItem create = new MenuItem(t("New project…", "Dự án mới…"));
        create.setOnAction(e -> newProjectDialog());
        menu.getItems().add(create);
        menu.show(anchor, Side.BOTTOM, 0, 6);
    }

    private void newProjectDialog() {
        if (host == null) {
            return;
        }

        Label title = new Label(t("New project", "Dự án mới"));
        title.getStyleClass().add("modal-title");
        Label sub = new Label(t("Start a local LaTeX workspace.",
                "Bắt đầu không gian LaTeX cục bộ."));
        sub.getStyleClass().add("modal-sub");
        VBox copy = new VBox(2, title, sub);

        StackPane iconTile = new StackPane(
                UiSupport.lineIcon(UiSupport.PLUS, 16, Color.web("#23664A")));
        iconTile.getStyleClass().add("modal-icon-tile");
        iconTile.setMinSize(38, 38);
        iconTile.setPrefSize(38, 38);
        iconTile.setMaxSize(38, 38);

        Button close = new Button();
        close.setGraphic(UiSupport.iconBox(UiSupport.CLOSE, 13, Color.web("#8B8B85"), 16));
        close.getStyleClass().add("icon-button");
        close.setTooltip(UiSupport.tooltip(t("Close", "Đóng")));

        HBox head = new HBox(12, iconTile, copy, UiSupport.spacer(), close);
        head.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(t("PROJECT NAME", "TÊN DỰ ÁN"));
        nameLabel.getStyleClass().add("field-label");
        TextField nameField = new TextField(t("Untitled project", "Dự án chưa đặt tên"));
        nameField.getStyleClass().add("modal-field");
        Label error = new Label();
        error.getStyleClass().add("modal-error");
        error.setVisible(false);
        error.setManaged(false);
        VBox nameBlock = new VBox(6, nameLabel, nameField, error);

        Label pathLabel = new Label(t("LOCATION", "VỊ TRÍ"));
        pathLabel.getStyleClass().add("field-label");
        Label path = new Label();
        path.getStyleClass().add("modal-path");
        path.setMaxWidth(Double.MAX_VALUE);
        VBox pathBlock = new VBox(6, pathLabel, path);

        Label hint = new Label(t("Files stay on this machine", "Tệp được lưu trên máy này"));
        hint.getStyleClass().add("modal-hint");

        Button cancel = new Button(t("Cancel", "Hủy"));
        cancel.getStyleClass().addAll("btn-secondary", "modal-cancel");

        Button create = new Button(t("Create project", "Tạo dự án"));
        create.setGraphic(UiSupport.iconBox(UiSupport.PLUS, 11, Color.WHITE, 12));
        create.setGraphicTextGap(6);
        create.getStyleClass().add("btn-primary");

        HBox footer = new HBox(8, hint, UiSupport.spacer(), cancel, create);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(16, head, nameBlock, pathBlock, footer);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(430);
        card.setPrefWidth(430);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane scrim = new StackPane(card);
        scrim.getStyleClass().add("modal-scrim");
        StackPane.setAlignment(card, Pos.CENTER);

        Runnable dismiss = () -> host.getChildren().remove(scrim);
        Runnable updatePath = () -> {
            String raw = nameField.getText() == null ? "" : nameField.getText().trim();
            path.setText(projectGateway.locationFor(raw));
        };
        Runnable submit = () -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (name.isEmpty()) {
                showFieldError(nameField, error,
                        t("Give the project a name.", "Hãy đặt tên cho dự án."));
                return;
            }
            if (name.contains("/") || name.contains("\\")) {
                showFieldError(nameField, error,
                        t("Use a name without slashes.", "Tên dự án không được chứa dấu /."));
                return;
            }
            ProjectSummary created;
            try {
                created = projectGateway.create(name);
            } catch (RuntimeException failure) {
                showFieldError(nameField, error, failure.getMessage());
                return;
            }
            dismiss.run();
            refreshProjects();
            onOpen.accept(created);
        };

        nameField.textProperty().addListener((obs, oldText, newText) -> {
            nameField.getStyleClass().remove("modal-field-error");
            error.setVisible(false);
            error.setManaged(false);
            updatePath.run();
        });
        nameField.setOnAction(e -> submit.run());
        close.setOnAction(e -> dismiss.run());
        cancel.setOnAction(e -> dismiss.run());
        create.setOnAction(e -> submit.run());
        scrim.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                dismiss.run();
            }
        });
        scrim.setOnMouseClicked(e -> {
            if (e.getTarget() == scrim) {
                dismiss.run();
            }
        });

        updatePath.run();
        host.getChildren().add(scrim);
        Platform.runLater(() -> {
            nameField.requestFocus();
            nameField.selectAll();
        });
    }

    private void showFieldError(TextField field, Label error, String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
        if (!field.getStyleClass().contains("modal-field-error")) {
            field.getStyleClass().add("modal-field-error");
        }
        field.requestFocus();
    }

    private ProjectSummary activeProject() {
        for (ProjectSummary project : projects) {
            if (project.name().equals(activeProjectName)) {
                return project;
            }
        }
        return projects.isEmpty() ? null : projects.get(0);
    }

    private int currentStreak() {
        int streak = 0;
        LocalDate day = LocalDate.now();
        while (activityData.getOrDefault(day, 0) > 0) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    private static String shortenPath(String path) {
        String home = System.getProperty("user.home");
        String shown = path;
        if (home != null && !home.isBlank() && path.startsWith(home)) {
            shown = "~" + path.substring(home.length());
        }
        if (shown.length() > 30) {
            shown = "…" + shown.substring(shown.length() - 29);
        }
        return shown;
    }
}

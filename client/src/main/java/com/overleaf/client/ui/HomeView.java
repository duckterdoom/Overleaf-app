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
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Labeled;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.CustomMenuItem;
import javafx.concurrent.Task;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import com.overleaf.client.ui.backend.NewProjectRequest;
import com.overleaf.client.ui.backend.ProjectTemplates;
import com.overleaf.client.ui.backend.ProjectFailure;
import java.nio.file.Path;
import java.io.File;
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
import javafx.scene.layout.FlowPane;
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
    private final Runnable onSettings;
    private final Consumer<Boolean> onLanguageChange;
    private String activeProjectName;
    private final StackPane host;
    private final boolean vi;

    private final VBox railProjects = new VBox(2);
    private final VBox projectRows = new VBox(1);
    private final Button heroProject = new Button();
    private final Label projectCount = new Label();
    private final Label folderPrefix = new Label();
    private final Label folderName = new Label("—");
    private final StackPane projectTile = stripTile();
    private final ActivityHeatmap heatmap;
    private final Map<LocalDate, Integer> activityData;

    private List<ProjectSummary> projects = new ArrayList<>();
    private ContextMenu newProjectMenu;

    public HomeView(ProjectGateway projectGateway,
                    ProfileGateway profileGateway,
                    ActivityGateway activityGateway,
                    AuthGateway.Session session,
                    String activeProjectName,
                    StackPane host,
                    Consumer<ProjectSummary> onOpen,
                    Runnable onSignOut,
                    Runnable onSettings,
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
        this.onSettings = onSettings;
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
        newProject.setOnAction(e -> showNewProjectMenu(newProject));

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
        name.setMaxWidth(92);

        Label host = new Label(session.offline()
                ? t("Offline session", "Phiên ngoại tuyến")
                : session.email());
        host.getStyleClass().add("rail-item-meta");
        host.setTextOverrun(OverrunStyle.ELLIPSIS);
        host.setMaxWidth(92);

        VBox info = new VBox(1, name, host);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button signOut = new Button();
        signOut.setGraphic(UiSupport.iconBox(UiSupport.SIGN_OUT, 14, Color.web("#7A7A74"), 16));
        signOut.getStyleClass().add("icon-button");
        signOut.setTooltip(UiSupport.tooltip(t("Sign out", "Đăng xuất")));
        signOut.setOnAction(e -> onSignOut.run());
        signOut.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);

        Button settings = new Button();
        settings.setGraphic(UiSupport.iconBox(UiSupport.SETTINGS, 14, Color.web("#7A7A74"), 16));
        settings.getStyleClass().add("icon-button");
        settings.setTooltip(UiSupport.tooltip(t("Settings", "Cài đặt")));
        settings.setOnAction(e -> onSettings.run());
        settings.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);

        HBox chip = new HBox(4, avatar, info, settings, signOut);
        HBox.setMargin(info, new Insets(0, 0, 0, 5));
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("user-chip");
        Tooltip.install(chip, UiSupport.tooltip(t("Profile and settings", "Hồ sơ và cài đặt")));
        chip.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                onSettings.run();
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

        content.getChildren().addAll(header, buildWorkspaceStrip(), buildActivityCard(), buildProjectsCard());

        // Centred, so a Compact or Standard content width does not leave all the spare room on the right.
        StackPane centred = new StackPane(content);
        centred.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(centred);
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
        add.setOnAction(e -> showNewProjectMenu(add));

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
                    ThemeManager.accent(), 12));
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
        double cell = ActivityHeatmap.cellSizeFor(usable, WEEKS);
        heatmap.setCellSize(Math.max(9, Math.min(20, cell)));
    }

    private VBox statBlock(String value, String label) {
        Label number = new Label(value);
        number.getStyleClass().add("stat-num");
        Label text = new Label(label);
        text.getStyleClass().add("stat-label");
        return new VBox(0, number, text);
    }

    /** Workspace details in one evenly divided strip: project, folder and compiler. */
    private HBox buildWorkspaceStrip() {
        heroProject.getStyleClass().add("strip-button");
        heroProject.setContentDisplay(ContentDisplay.RIGHT);
        heroProject.setGraphicTextGap(4);
        heroProject.setGraphic(UiSupport.iconBox(UiSupport.CHEVRON_DOWN, 14, Color.web("#8B8B85"), 16));
        heroProject.setTextOverrun(OverrunStyle.ELLIPSIS);
        heroProject.setOnAction(e -> showProjectMenu(heroProject));

        // The path reads as a breadcrumb: the folder it lives in fades back, the project name stands out.
        folderPrefix.getStyleClass().add("strip-path");
        folderPrefix.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
        folderPrefix.setMinWidth(0);
        folderName.getStyleClass().add("strip-value");
        folderName.setMinWidth(Region.USE_PREF_SIZE);
        HBox path = new HBox(folderPrefix, folderName);
        path.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(folderPrefix, Priority.SOMETIMES);

        Circle ready = new Circle(3, Color.web("#3E9B6D"));
        Label compiler = new Label("pdfLaTeX");
        compiler.getStyleClass().add("strip-value");
        HBox compilerValue = new HBox(7, ready, compiler);
        compilerValue.setAlignment(Pos.CENTER_LEFT);

        StackPane folderTile = stripTile();
        folderTile.getChildren().setAll(UiSupport.lineIcon(UiSupport.FOLDER, 15, ThemeManager.accent()));
        StackPane compilerTile = stripTile();
        compilerTile.getChildren().setAll(UiSupport.lineIcon(
                ProjectAppearance.iconPath("code"), 15, ThemeManager.accent()));

        HBox strip = new HBox(
                stripCell(t("PROJECT", "DỰ ÁN"), projectTile, heroProject, 3),
                stripDivider(),
                stripCell(t("FOLDER", "THƯ MỤC"), folderTile, path, 5),
                stripDivider(),
                stripCell(t("COMPILES WITH", "BIÊN DỊCH BẰNG"), compilerTile, compilerValue, 2));
        strip.setAlignment(Pos.TOP_LEFT);
        strip.getStyleClass().addAll("panel", "workspace-strip");
        return strip;
    }

    /** The project cell's tile takes the project's own icon and color; older projects show their initial. */
    private void paintProjectTile(ProjectSummary active) {
        String svg = active == null ? null : ProjectAppearance.iconPath(active.icon());
        Color accent = active == null ? Color.web("#8B8B85") : ProjectAppearance.color(active.color());
        if (svg != null) {
            projectTile.getChildren().setAll(UiSupport.lineIcon(svg, 16, accent));
            projectTile.setStyle("-fx-background-color: " + tint(accent) + ";");
        } else if (active != null) {
            Label initial = new Label(active.name().substring(0, 1).toUpperCase());
            Color theme = ThemeManager.accent();
            initial.setStyle(String.format("-fx-font-size: 14px; -fx-text-fill: #%02X%02X%02X;",
                    Math.round(theme.getRed() * 255), Math.round(theme.getGreen() * 255), Math.round(theme.getBlue() * 255)));
            projectTile.getChildren().setAll(initial);
            projectTile.setStyle(null);
        } else {
            projectTile.getChildren().setAll(UiSupport.lineIcon(UiSupport.FOLDER, 15, accent));
            projectTile.setStyle(null);
        }
    }

    private static StackPane stripTile() {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("strip-tile");
        tile.setMinSize(36, 36);
        tile.setPrefSize(36, 36);
        tile.setMaxSize(36, 36);
        return tile;
    }

    private Region stripDivider() {
        Region divider = new Region();
        divider.getStyleClass().add("strip-divider");
        divider.setMinWidth(1);
        divider.setPrefWidth(1);
        divider.setMaxWidth(1);
        HBox.setMargin(divider, new Insets(12, 0, 12, 0));
        return divider;
    }

    /** Icon tile beside a label-over-value pair; the value row is a fixed height so every cell lines up. */
    private HBox stripCell(String key, Node tile, Region value, int weight) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("strip-key");
        value.setMinHeight(20);
        value.setPrefHeight(20);
        value.setMaxHeight(20);
        value.setMaxWidth(Double.MAX_VALUE);
        if (value instanceof Labeled labeled) {
            labeled.setAlignment(Pos.CENTER_LEFT);
        }
        VBox text = new VBox(2, keyLabel, value);
        text.setAlignment(Pos.CENTER_LEFT);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox cell = new HBox(12, tile, text);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.getStyleClass().add("strip-cell");
        cell.setMinWidth(0);
        cell.setPrefWidth(weight * 100);
        HBox.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }

    private void refreshProjects() {
        projects = projectGateway.projects();
        refreshRailProjects();
        refreshProjectRows();

        ProjectSummary active = activeProject();
        heroProject.setText(active == null
                ? t("No project", "Chưa có dự án")
                : active.name());
        projectCount.setText(projects.size() + " "
                + t(projects.size() == 1 ? "project" : "projects", "dự án"));
        if (active == null) {
            folderPrefix.setText("");
            folderName.setText("—");
        } else {
            java.nio.file.Path folder = java.nio.file.Path.of(active.folder());
            String parent = folder.getParent() == null ? "" : folder.getParent().toString();
            folderPrefix.setText(parent.isEmpty() ? "" : parent + java.io.File.separator);
            folderName.setText(folder.getFileName().toString());
        }
        paintProjectTile(active);
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
            Circle dot = new Circle(2.5, active ? ProjectAppearance.color(project.color()) : Color.TRANSPARENT);

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
        Node icon = projectBadge(project);

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
        MenuItem move = new MenuItem(t("Change folder…", "Đổi thư mục lưu…"));
        move.setOnAction(e -> moveProjectDialog(project));
        return new ContextMenu(open, rename, move, reveal, new SeparatorMenuItem(), delete);
    }

    private void renameProjectDialog(ProjectSummary project) {
        UiModal modal = UiModal.create(host, UiSupport.EDIT, UiModal.Tone.NEUTRAL,
                t("Rename project", "Đổi tên dự án"),
                t("The project folder is renamed on disk.", "Thư mục dự án trên ổ đĩa sẽ được đổi tên.")).localized(vi);
        boolean wasActive = activeProject() != null && activeProject().folder().equals(project.folder());
        TextField name = modal.field(t("Project name", "Tên dự án"), project.name(), "");
        modal.actions(null, t("Cancel", "Hủy"), t("Rename", "Đổi tên"), UiModal.Tone.NEUTRAL, () -> {
            String value = name.getText() == null ? "" : name.getText().trim();
            if (value.isEmpty()) {
                modal.error(name, t("Give the project a name.", "Hãy đặt tên cho dự án."));
                return false;
            }
            try {
                ProjectSummary renamed = projectGateway.rename(project, value);
                if (wasActive) activeProjectName = renamed.name();
                onProjectChanged.accept(project, renamed);
            } catch (RuntimeException failure) {
                modal.error(name, ProjectFailure.message(failure, vi));
                return false;
            }
            refreshProjects();
            return true;
        });
        modal.show();
    }

    private void deleteProjectDialog(ProjectSummary project) {
        UiModal modal = UiModal.create(host, UiSupport.TRASH, UiModal.Tone.DANGER,
                t("Delete \"" + project.name() + "\"?", "Xóa \"" + project.name() + "\"?"),
                null).localized(vi);
        modal.text(t("Files move to a recoverable trash folder.", "Tệp được chuyển vào thư mục thùng rác để có thể khôi phục."));
        modal.actions(null, t("Cancel", "Hủy"), t("Delete project", "Xóa dự án"), UiModal.Tone.DANGER,
                () -> {
                    try {
                        projectGateway.delete(project);
                        onProjectChanged.accept(project, null);
                    } catch (RuntimeException failure) {
                        modal.error(null, ProjectFailure.message(failure, vi));
                        return false;
                    }
                    refreshProjects();
                    return true;
                });
        modal.show();
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
        row.setOnMouseClicked(e -> showNewProjectMenu(row));
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
        create.setOnAction(e -> showNewProjectMenu(anchor));
        menu.getItems().add(create);
        menu.show(anchor, Side.BOTTOM, 0, 6);
    }

    private void newProjectDialog() {
        newProjectDialog(NewProjectRequest.Kind.BLANK, null);
    }

    private void showNewProjectMenu(Node anchor) {
        if (newProjectMenu != null) newProjectMenu.hide();
        newProjectMenu = new ContextMenu();
        newProjectMenu.setId("new-project-menu");
        newProjectMenu.getStyleClass().add("new-project-menu");
        MenuItem blank = projectItem(t("Blank project", "Dự án trống"), "new-project-blank", NewProjectRequest.Kind.BLANK);
        MenuItem zip = projectItem(t("Existing project (.zip)", "Dự án có sẵn (.zip)"), "import-zip", NewProjectRequest.Kind.ZIP);
        MenuItem word = projectItem(t("Word document (.docx)", "Tài liệu Word (.docx)"), "import-word", NewProjectRequest.Kind.WORD);
        MenuItem markdown = projectItem(t("Markdown document", "Tài liệu Markdown"), "import-markdown", NewProjectRequest.Kind.MARKDOWN);
        MenuItem github = projectItem(t("GitHub repo", "Kho mã GitHub"), "import-github", NewProjectRequest.Kind.GITHUB);
        MenuItem example = projectItem(t("Example project", "Dự án mẫu"), "new-project-example", NewProjectRequest.Kind.EXAMPLE);
        Menu more = new Menu(t("More templates", "Thêm mẫu dự án"));
        more.setId("project-templates");
        for (var template : ProjectTemplates.all()) {
            MenuItem item = new MenuItem(template.title(vi));
            item.setId("template-" + template.id());
            item.setOnAction(e -> newProjectDialog(NewProjectRequest.Kind.TEMPLATE, template.id()));
            more.getItems().add(item);
        }
        MenuItem all = new MenuItem(t("View all", "Xem tất cả"));
        all.setOnAction(e -> templateGallery());
        more.getItems().addAll(new SeparatorMenuItem(), all);
        MenuItem storage = new MenuItem(t("Default project folder…", "Thư mục dự án mặc định…"));
        storage.setId("default-project-folder");
        storage.setOnAction(e -> projectStorageDialog());
        MenuItem affiliation = new MenuItem(t("Add affiliation…", "Thêm đơn vị…"));
        affiliation.setOnAction(e -> affiliationDialog());
        newProjectMenu.getItems().addAll(blank, new SeparatorMenuItem(), menuHeading(t("Import", "Nhập tài liệu")),
                zip, word, markdown, github, new SeparatorMenuItem(), menuHeading(t("Templates", "Mẫu dự án")),
                example, more, new SeparatorMenuItem(), storage, affiliation);
        newProjectMenu.show(anchor, Side.BOTTOM, 0, 4);
    }

    private MenuItem projectItem(String text, String id, NewProjectRequest.Kind kind) {
        MenuItem item = new MenuItem(text);
        item.setId(id);
        item.setOnAction(e -> newProjectDialog(kind, null));
        return item;
    }

    private CustomMenuItem menuHeading(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("project-menu-heading");
        CustomMenuItem item = new CustomMenuItem(title, false);
        item.setDisable(true);
        return item;
    }

    private void templateGallery() {
        UiModal modal = UiModal.create(host, UiSupport.FILE, UiModal.Tone.NEUTRAL,
                t("Project templates", "Mẫu dự án"), t("Choose a category to create an editable LaTeX project.",
                        "Chọn một nhóm để tạo dự án LaTeX có thể chỉnh sửa.")).localized(vi);
        VBox templates = new VBox(6);
        for (var template : ProjectTemplates.all()) {
            Button item = new Button(template.title(vi));
            item.getStyleClass().add("btn-secondary");
            item.setMaxWidth(Double.MAX_VALUE);
            item.setOnAction(e -> { modal.close(); newProjectDialog(NewProjectRequest.Kind.TEMPLATE, template.id()); });
            templates.getChildren().add(item);
        }
        ScrollPane scroll = new ScrollPane(templates);
        scroll.setFitToWidth(true); scroll.setPrefViewportHeight(320);
        modal.node(scroll);
        modal.actions(null, null, t("Close", "Đóng"), UiModal.Tone.NEUTRAL, () -> true);
        modal.show();
    }

    private void chooseDirectory(TextField field) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("Choose a project folder", "Chọn thư mục lưu dự án"));
        try {
            File current = Path.of(field.getText()).toFile();
            if (current.isDirectory()) chooser.setInitialDirectory(current);
        } catch (RuntimeException ignored) { }
        File folder = chooser.showDialog(getScene().getWindow());
        if (folder != null) field.setText(folder.getAbsolutePath());
    }

    private void projectStorageDialog() {
        UiModal modal = UiModal.create(host, UiSupport.FOLDER, UiModal.Tone.NEUTRAL,
                t("Default project folder", "Thư mục dự án mặc định"),
                t("New projects use this folder. Existing projects remain listed at their current locations.",
                        "Dự án mới được lưu tại đây. Dự án cũ vẫn xuất hiện trong danh sách và giữ nguyên vị trí.")).localized(vi);
        TextField folder = modal.field(t("Folder", "Thư mục"), projectGateway.projectsDirectory().toString(), "");
        folder.setId("default-project-directory");
        Button browse = new Button(t("Browse…", "Chọn…"));
        browse.getStyleClass().add("btn-secondary");
        browse.setOnAction(e -> chooseDirectory(folder)); modal.node(browse);
        Button[] save = new Button[1];
        save[0] = modal.actions(null, t("Cancel", "Hủy"), t("Save", "Lưu"), UiModal.Tone.NEUTRAL, () -> {
            String selected = folder.getText();
            if (selected.isBlank()) { modal.error(folder, t("Choose a folder.", "Hãy chọn thư mục.")); return false; }
            runProjectOperation(modal, save[0], () -> { projectGateway.setProjectsDirectory(Path.of(selected)); return null; }, p -> refreshProjects());
            return false;
        });
        modal.show();
    }

    private void moveProjectDialog(ProjectSummary project) {
        UiModal modal = UiModal.create(host, UiSupport.FOLDER, UiModal.Tone.NEUTRAL,
                t("Change project folder", "Đổi thư mục lưu dự án"),
                t("The entire project is copied to the new location. A recoverable backup is kept at the old location.",
                        "Toàn bộ dự án được chuyển sang vị trí mới. Bản sao khôi phục được giữ tại vị trí cũ.")).localized(vi);
        modal.path(project.folder());
        TextField folder = modal.field(t("New parent folder", "Thư mục cha mới"), Path.of(project.folder()).getParent().toString(), "");
        folder.setId("move-project-directory");
        Button browse = new Button(t("Browse…", "Chọn…"));
        browse.getStyleClass().add("btn-secondary");
        browse.setOnAction(e -> chooseDirectory(folder)); modal.node(browse);
        Button[] move = new Button[1];
        move[0] = modal.actions(null, t("Cancel", "Hủy"), t("Move project", "Chuyển dự án"), UiModal.Tone.NEUTRAL, () -> {
            String selected = folder.getText();
            if (selected.isBlank()) { modal.error(folder, t("Choose a folder.", "Hãy chọn thư mục.")); return false; }
            runProjectOperation(modal, move[0], () -> projectGateway.move(project, Path.of(selected)), moved -> {
                onProjectChanged.accept(project, moved); refreshProjects();
            });
            return false;
        });
        modal.show();
    }

    private void affiliationDialog() {
        UiModal modal = UiModal.create(host, UiSupport.USERS, UiModal.Tone.NEUTRAL,
                t("Affiliation", "Đơn vị công tác / trường học"),
                t("Used in the author field of new bundled templates.", "Dùng trong thông tin tác giả của các mẫu dự án mới.")).localized(vi);
        TextField value = modal.field(t("Institution or organization", "Trường học hoặc cơ quan"), projectGateway.affiliation(), "");
        modal.actions(null, t("Cancel", "Hủy"), t("Save", "Lưu"), UiModal.Tone.NEUTRAL, () -> {
            try { projectGateway.setAffiliation(value.getText()); return true; }
            catch (RuntimeException failure) { modal.error(value, ProjectFailure.message(failure, vi)); return false; }
        });
        modal.show();
    }

    private void runProjectOperation(UiModal modal, Button button, java.util.concurrent.Callable<ProjectSummary> action,
                                     Consumer<ProjectSummary> success) {
        if (button.isDisabled()) return;
        String caption = button.getText();
        button.setDisable(true); button.setText(t("Working…", "Đang xử lý…"));
        Task<ProjectSummary> task = new Task<>() {
            @Override protected ProjectSummary call() throws Exception { return action.call(); }
        };
        modal.onClose(() -> { if (!task.isDone()) task.cancel(true); });
        task.setOnSucceeded(e -> { modal.close(); success.accept(task.getValue()); });
        task.setOnFailed(e -> {
            button.setDisable(false); button.setText(caption);
            modal.error(null, ProjectFailure.message(task.getException(), vi));
        });
        Thread worker = new Thread(task, "project-storage"); worker.setDaemon(true); worker.start();
    }

    private void newProjectDialog(NewProjectRequest.Kind kind, String templateId) {
        if (host == null) {
            return;
        }

        Label title = new Label(t("New project", "Dự án mới"));
        title.getStyleClass().add("modal-title");
        Label sub = new Label(switch (kind) {
            case ZIP -> t("Import a LaTeX project from a ZIP archive.", "Nhập dự án LaTeX từ tệp ZIP.");
            case WORD -> t("Convert a Word .docx file to LaTeX. Review complex formatting after import.", "Chuyển tệp Word .docx sang LaTeX. Hãy kiểm tra lại định dạng phức tạp sau khi nhập.");
            case MARKDOWN -> t("Convert UTF-8 Markdown to an editable LaTeX project.", "Chuyển Markdown UTF-8 thành dự án LaTeX có thể chỉnh sửa.");
            case GITHUB -> t("Clone a public GitHub repository containing a root LaTeX document.", "Sao chép kho GitHub công khai có tài liệu LaTeX gốc.");
            case EXAMPLE -> t("A working example with sections, mathematics and bibliography.", "Dự án mẫu có các phần, công thức và tài liệu tham khảo.");
            case TEMPLATE -> t("Create an editable project from a bundled template.", "Tạo dự án có thể chỉnh sửa từ mẫu có sẵn.");
            default -> t("Start a local LaTeX workspace.", "Bắt đầu không gian LaTeX cục bộ.");
        });
        sub.setWrapText(true);
        sub.getStyleClass().add("modal-sub");
        VBox copy = new VBox(2, title, sub);

        StackPane iconTile = new StackPane(
                UiSupport.lineIcon(UiSupport.PLUS, 16, ThemeManager.accent()));
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
        String suggestedName = kind == NewProjectRequest.Kind.EXAMPLE ? t("Example project", "Dự án mẫu")
                : kind == NewProjectRequest.Kind.TEMPLATE ? ProjectTemplates.all().stream().filter(item -> item.id().equals(templateId))
                        .map(item -> item.title(vi)).findFirst().orElse(t("Untitled project", "Dự án chưa đặt tên"))
                : t("Untitled project", "Dự án chưa đặt tên");
        TextField nameField = new TextField(suggestedName);
        nameField.setId("new-project-name");
        nameField.getStyleClass().add("modal-field");
        Label error = new Label();
        error.getStyleClass().add("modal-error");
        error.setVisible(false);
        error.setManaged(false);
        VBox nameBlock = new VBox(6, nameLabel, nameField, error);

        String[] pickedIcon = {ProjectAppearance.DEFAULT_ICON};
        String[] pickedColor = {ProjectAppearance.DEFAULT_COLOR};
        Runnable previewLook = () -> {
            Color accent = ProjectAppearance.color(pickedColor[0]);
            iconTile.getChildren().setAll(UiSupport.lineIcon(
                    ProjectAppearance.iconPath(pickedIcon[0]), 18, accent));
            iconTile.setStyle("-fx-background-color: " + tint(accent) + ";");
        };

        Label iconLabel = new Label(t("PROJECT ICON", "BIỂU TƯỢNG"));
        iconLabel.getStyleClass().add("field-label");
        FlowPane iconGrid = pickGrid();
        ProjectAppearance.icons().forEach((id, svg) -> {
            StackPane tile = pickTile(UiSupport.lineIcon(svg, 16, Color.web("#3A3A36")));
            tile.setUserData(id);
            iconGrid.getChildren().add(tile);
        });
        selectTile(iconGrid, pickedIcon[0]);
        iconGrid.getChildren().forEach(tile -> tile.setOnMouseClicked(e -> {
            pickedIcon[0] = (String) tile.getUserData();
            selectTile(iconGrid, pickedIcon[0]);
            previewLook.run();
        }));
        VBox iconBlock = new VBox(6, iconLabel, iconGrid);

        Label colorLabel = new Label(t("PROJECT COLOR", "MÀU DỰ ÁN"));
        colorLabel.getStyleClass().add("field-label");
        FlowPane colorGrid = pickGrid();
        for (String hex : ProjectAppearance.colors()) {
            StackPane tile = pickTile(new Circle(6.5, ProjectAppearance.color(hex)));
            tile.setUserData(hex);
            colorGrid.getChildren().add(tile);
        }
        selectTile(colorGrid, pickedColor[0]);
        colorGrid.getChildren().forEach(tile -> tile.setOnMouseClicked(e -> {
            pickedColor[0] = (String) tile.getUserData();
            selectTile(colorGrid, pickedColor[0]);
            previewLook.run();
        }));
        VBox colorBlock = new VBox(6, colorLabel, colorGrid);
        previewLook.run();

        Label pathLabel = new Label(t("LOCATION", "VỊ TRÍ"));
        pathLabel.getStyleClass().add("field-label");
        Label path = new Label();
        path.getStyleClass().add("modal-path");
        path.setMaxWidth(Double.MAX_VALUE);
        TextField directoryField = new TextField(projectGateway.projectsDirectory().toString());
        directoryField.setId("new-project-directory");
        directoryField.getStyleClass().add("modal-field");
        HBox.setHgrow(directoryField, Priority.ALWAYS);
        Button chooseDirectory = new Button(t("Browse…", "Chọn…"));
        chooseDirectory.getStyleClass().add("btn-secondary");
        chooseDirectory.setOnAction(e -> chooseDirectory(directoryField));
        VBox pathBlock = new VBox(6, pathLabel, new HBox(8, directoryField, chooseDirectory), path);

        boolean imported = kind == NewProjectRequest.Kind.ZIP || kind == NewProjectRequest.Kind.WORD
                || kind == NewProjectRequest.Kind.MARKDOWN || kind == NewProjectRequest.Kind.GITHUB;
        TextField sourceField = new TextField();
        sourceField.setId("new-project-source");
        sourceField.getStyleClass().add("modal-field");
        sourceField.setPromptText(kind == NewProjectRequest.Kind.GITHUB ? "https://github.com/owner/repository"
                : t("Choose a source file", "Chọn tệp nguồn"));
        HBox.setHgrow(sourceField, Priority.ALWAYS);
        Button sourceBrowse = new Button(t("Browse…", "Chọn…"));
        sourceBrowse.getStyleClass().add("btn-secondary");
        sourceBrowse.setVisible(kind != NewProjectRequest.Kind.GITHUB);
        sourceBrowse.setManaged(sourceBrowse.isVisible());
        sourceBrowse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(t("Choose a document to import", "Chọn tài liệu để nhập"));
            String[] extensions = kind == NewProjectRequest.Kind.ZIP ? new String[]{"*.zip"}
                    : kind == NewProjectRequest.Kind.WORD ? new String[]{"*.docx"} : new String[]{"*.md", "*.markdown"};
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(t("Supported documents", "Tài liệu được hỗ trợ"), extensions));
            File file = chooser.showOpenDialog(getScene().getWindow());
            if (file != null) {
                sourceField.setText(file.getAbsolutePath());
                if (nameField.getText().equals(suggestedName)) nameField.setText(file.getName().replaceFirst("\\.[^.]+$", ""));
            }
        });
        Label sourceCaption = new Label(kind == NewProjectRequest.Kind.GITHUB ? t("GITHUB REPOSITORY", "KHO GITHUB") : t("SOURCE FILE", "TỆP NGUỒN"));
        sourceCaption.getStyleClass().add("field-label");
        VBox sourceBlock = new VBox(6, sourceCaption, new HBox(8, sourceField, sourceBrowse));
        sourceBlock.setVisible(imported);
        sourceBlock.setManaged(imported);

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

        VBox card = new VBox(16, head, nameBlock, sourceBlock, iconBlock, colorBlock, pathBlock, footer);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(520);
        card.setPrefWidth(520);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        ScrollPane cardScroll = new ScrollPane(card);
        cardScroll.setFitToWidth(true);
        cardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardScroll.setMaxWidth(540);
        cardScroll.maxHeightProperty().bind(host.heightProperty().subtract(40));
        cardScroll.getStyleClass().add("project-dialog-scroll");
        StackPane scrim = new StackPane(cardScroll);
        scrim.getStyleClass().add("modal-scrim");
        StackPane.setAlignment(card, Pos.CENTER);

        java.util.concurrent.atomic.AtomicReference<Task<ProjectSummary>> running = new java.util.concurrent.atomic.AtomicReference<>();
        Runnable dismiss = () -> {
            Task<ProjectSummary> task = running.get();
            if (task != null && !task.isDone()) task.cancel(true);
            host.getChildren().remove(scrim);
        };
        Runnable updatePath = () -> {
            String raw = nameField.getText() == null ? "" : nameField.getText().trim();
            try { path.setText(Path.of(directoryField.getText()).resolve(raw.isBlank() ? "…" : raw).toString()); }
            catch (RuntimeException invalid) { path.setText(t("Choose a valid folder", "Hãy chọn thư mục hợp lệ")); }
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
            if (imported && sourceField.getText().isBlank()) {
                showFieldError(sourceField, error, t("Choose a source file or enter a repository URL.", "Chọn tệp nguồn hoặc nhập URL kho mã."));
                return;
            }
            NewProjectRequest request;
            try {
                if (directoryField.getText().isBlank()) throw new IllegalArgumentException("Empty folder");
                request = new NewProjectRequest(name, pickedIcon[0], pickedColor[0], Path.of(directoryField.getText()).toAbsolutePath(), kind,
                        imported && kind != NewProjectRequest.Kind.GITHUB ? Path.of(sourceField.getText()).toAbsolutePath() : null,
                        kind == NewProjectRequest.Kind.GITHUB ? sourceField.getText() : null, templateId, vi);
            } catch (RuntimeException failure) {
                showFieldError(directoryField, error, t("Choose a valid folder.", "Hãy chọn thư mục hợp lệ."));
                return;
            }
            if (create.isDisabled()) return;
            create.setDisable(true);
            create.setText(t("Creating…", "Đang tạo…"));
            Task<ProjectSummary> task = new Task<>() {
                @Override protected ProjectSummary call() { return projectGateway.create(request); }
            };
            running.set(task);
            task.setOnSucceeded(e -> {
                if (!host.getChildren().contains(scrim)) return;
                dismiss.run(); refreshProjects(); onOpen.accept(task.getValue());
            });
            task.setOnFailed(e -> {
                create.setDisable(false); create.setText(t("Create project", "Tạo dự án"));
                showFieldError(nameField, error, ProjectFailure.message(task.getException(), vi));
            });
            Thread worker = new Thread(task, "project-import"); worker.setDaemon(true); worker.start();
        };

        nameField.textProperty().addListener((obs, oldText, newText) -> {
            nameField.getStyleClass().remove("modal-field-error");
            error.setVisible(false);
            error.setManaged(false);
            updatePath.run();
        });
        directoryField.textProperty().addListener((obs, oldText, newText) -> updatePath.run());
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

    private static FlowPane pickGrid() {
        FlowPane grid = new FlowPane(4, 4);
        grid.getStyleClass().add("pick-grid");
        return grid;
    }

    private static StackPane pickTile(Node content) {
        StackPane tile = new StackPane(content);
        tile.getStyleClass().add("pick-tile");
        tile.setMinSize(28, 28);
        tile.setPrefSize(28, 28);
        tile.setMaxSize(28, 28);
        return tile;
    }

    private static void selectTile(FlowPane grid, String id) {
        for (Node tile : grid.getChildren()) {
            tile.getStyleClass().remove("pick-tile-selected");
            if (id.equals(tile.getUserData())) {
                tile.getStyleClass().add("pick-tile-selected");
            }
        }
    }

    private static String tint(Color accent) {
        return String.format("rgba(%d, %d, %d, 0.14)", Math.round(accent.getRed() * 255),
                Math.round(accent.getGreen() * 255), Math.round(accent.getBlue() * 255));
    }

    /** Project icon in its chosen color; older projects without one fall back to the initial. */
    private Node projectBadge(ProjectSummary project) {
        String svg = ProjectAppearance.iconPath(project.icon());
        if (svg == null) {
            Label initial = new Label(project.name().substring(0, 1).toUpperCase());
            initial.getStyleClass().add("project-icon");
            return initial;
        }
        Color accent = ProjectAppearance.color(project.color());
        StackPane badge = new StackPane(UiSupport.lineIcon(svg, 15, accent));
        badge.getStyleClass().add("project-icon");
        badge.setStyle("-fx-background-color: " + tint(accent) + ";");
        return badge;
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
        String preferred = projectGateway.preferredProjectFolder();
        if (preferred != null) {
            for (ProjectSummary project : projects) {
                if (project.name().equals(activeProjectName) && project.folder().equals(preferred)) return project;
            }
        }
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
}

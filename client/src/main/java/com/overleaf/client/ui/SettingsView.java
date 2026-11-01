package com.overleaf.client.ui;

import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.concurrent.Task;
import com.overleaf.client.compiler.ToolchainDiagnostics;
import javafx.stage.FileChooser;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import com.overleaf.client.ui.backend.AuthGateway;
import com.overleaf.client.ui.backend.ProfileGateway;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Settings page: a section list on the left and a card of rows on the right
 * (title and description beside the control). Every control writes straight to
 * {@link AppSettings}, which restyles the app live.
 */
public final class SettingsView extends BorderPane {

    private record Option(String id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** The signed-in account and the actions the Profile page needs from the app. */
    public record Profile(StackPane host, AuthGateway.Session session, ProfileGateway pictures, AuthGateway auth,
                          Consumer<AuthGateway.Session> onRenamed, Runnable onPictureChanged, Runnable onSignOut) {
    }

    private final AppSettings settings;
    private final Profile profile;
    private AuthGateway.Session account;
    private final boolean vi;
    private final Consumer<Boolean> onLanguage;
    private final ScrollPane scroll = new ScrollPane();
    private final List<Button> navButtons = new ArrayList<>();
    private String section = "profile";

    public SettingsView(AppSettings settings, Profile profile, boolean vietnamese, String startSection,
                        Runnable onBack, Consumer<Boolean> onLanguage) {
        this.settings = settings;
        this.profile = profile;
        this.account = profile.session();
        this.vi = vietnamese;
        this.onLanguage = onLanguage;
        getStyleClass().add("settings-root");

        Button back = new Button(t("Back", "Quay lại"));
        back.setGraphic(UiSupport.iconBox(UiSupport.ARROW_LEFT, 14, Color.web("#5B5B56"), 16));
        back.setGraphicTextGap(8);
        back.getStyleClass().add("settings-back");
        back.setOnAction(e -> onBack.run());

        Label heading = new Label(t("Settings", "Cài đặt"));
        heading.getStyleClass().add("settings-nav-title");

        VBox nav = new VBox(4, back, heading);
        nav.getStyleClass().add("settings-nav");
        VBox.setMargin(heading, new Insets(14, 0, 6, 8));
        addNav(nav, "profile", t("Profile", "Hồ sơ"), UiSupport.USERS);
        addNav(nav, "appearance", t("Appearance", "Giao diện"), UiSupport.SETTINGS);
        addNav(nav, "editor", t("Editor", "Trình soạn thảo"), UiSupport.EDIT);
        addNav(nav, "compilation", t("Compilation", "Biên dịch"), UiSupport.PLAY);
        addNav(nav, "projects", t("Projects", "Dự án"), UiSupport.FOLDER);
        addNav(nav, "about", t("About", "Giới thiệu"), UiSupport.INFO);

        scroll.getStyleClass().add("settings-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        setLeft(nav);
        setCenter(scroll);
        show(startSection);
    }

    private String t(String en, String viText) {
        return vi ? viText : en;
    }

    private void addNav(VBox nav, String id, String text, String icon) {
        Button item = new Button(text);
        item.setGraphic(UiSupport.iconBox(icon, 14, Color.web("#6B6B65"), 16));
        item.setGraphicTextGap(9);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("settings-nav-item");
        item.setUserData(id);
        item.setOnAction(e -> show(id));
        navButtons.add(item);
        nav.getChildren().add(item);
    }

    private void show(String id) {
        section = id;
        for (Button button : navButtons) {
            button.getStyleClass().remove("settings-nav-active");
            if (id.equals(button.getUserData())) button.getStyleClass().add("settings-nav-active");
        }
        VBox page = switch (id) {
            case "profile" -> profilePage();
            case "editor" -> editorPage();
            case "compilation" -> compilationPage();
            case "projects" -> projectsPage();
            case "about" -> aboutPage();
            default -> appearancePage();
        };
        page.getStyleClass().add("settings-page");
        scroll.setContent(page);
        scroll.setVvalue(0);
    }

    // ------------------------------------------------------------------ pages

    private VBox profilePage() {
        Label title = pageTitle(t("Profile", "Hồ sơ"));

        // Header: picture, name and email, with the photo actions underneath.
        Label avatar = new Label();
        avatar.getStyleClass().addAll("avatar", "avatar-large");
        Label name = new Label(account.name());
        name.getStyleClass().add("profile-name");
        Label email = new Label(account.offline() ? t("Offline session", "Phiên ngoại tuyến") : account.email());
        email.getStyleClass().add("setting-desc");

        Label error = new Label();
        error.getStyleClass().add("modal-error");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        Button choose = new Button(t("Choose image…", "Chọn ảnh…"));
        choose.setGraphic(UiSupport.iconBox(UiSupport.PLUS, 12, ThemeManager.ink(), 13));
        choose.setGraphicTextGap(6);
        choose.getStyleClass().add("btn-secondary");
        Button remove = new Button(t("Remove photo", "Xóa ảnh"));
        remove.getStyleClass().add("btn-link-danger");
        HBox photoActions = new HBox(10, choose, remove);
        photoActions.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(t("PNG, JPG or GIF up to 5 MB. Cropped to a centred square.",
                "PNG, JPG hoặc GIF, tối đa 5 MB. Ảnh được cắt vuông ở giữa."));
        hint.getStyleClass().add("modal-hint");

        VBox identity = new VBox(3, name, email, photoActions, hint);
        VBox.setMargin(photoActions, new Insets(10, 0, 0, 0));
        VBox.setMargin(hint, new Insets(4, 0, 0, 0));
        identity.setAlignment(Pos.CENTER_LEFT);
        HBox header = new HBox(22, avatar, identity);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("setting-row");

        Runnable refreshPicture = () -> {
            var picture = profile.pictures().avatar(account);
            UiSupport.applyAvatar(avatar, account.name(), picture, 76);
            remove.setVisible(picture.isPresent());
            remove.setManaged(picture.isPresent());
        };
        refreshPicture.run();

        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(t("Choose a profile picture", "Chọn ảnh đại diện"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    t("Images", "Ảnh"), "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File pictures = new File(System.getProperty("user.home"), "Pictures");
            if (pictures.isDirectory()) chooser.setInitialDirectory(pictures);
            File picked = chooser.showOpenDialog(getScene().getWindow());
            if (picked == null) return;
            if (UiSupport.circularImage(picked.toPath(), 76) == null) {
                showProfileError(error, t("That file could not be read as an image.", "Không đọc được tệp ảnh này."));
                return;
            }
            try {
                profile.pictures().setAvatar(account, picked.toPath());
            } catch (RuntimeException failure) {
                showProfileError(error, failure.getMessage());
                return;
            }
            error.setVisible(false);
            error.setManaged(false);
            refreshPicture.run();
            profile.onPictureChanged().run();
        });
        remove.setOnAction(e -> {
            try {
                profile.pictures().removeAvatar(account);
            } catch (RuntimeException failure) {
                showProfileError(error, failure.getMessage());
                return;
            }
            refreshPicture.run();
            profile.onPictureChanged().run();
        });

        // Display name.
        TextField nameField = new TextField(account.name());
        nameField.getStyleClass().add("modal-field");
        nameField.setPrefWidth(220);
        Button save = new Button(t("Save", "Lưu"));
        save.getStyleClass().add("btn-primary");
        save.setDisable(true);
        nameField.textProperty().addListener((obs, was, now) ->
                save.setDisable(now == null || now.trim().isEmpty() || now.trim().equals(account.name())));
        Runnable saveName = () -> {
            if (save.isDisabled()) return;
            try {
                account = profile.auth().updateName(account, nameField.getText());
            } catch (RuntimeException failure) {
                showProfileError(error, failure.getMessage());
                return;
            }
            error.setVisible(false);
            error.setManaged(false);
            name.setText(account.name());
            nameField.setText(account.name());
            save.setDisable(true);
            refreshPicture.run();
            profile.onRenamed().accept(account);
        };
        save.setOnAction(e -> saveName.run());
        nameField.setOnAction(e -> saveName.run());
        HBox nameControl = new HBox(8, nameField, save);
        nameControl.setAlignment(Pos.CENTER_RIGHT);

        Label emailValue = new Label(account.offline() ? t("Not signed in to an account", "Chưa đăng nhập tài khoản")
                : account.email());
        emailValue.getStyleClass().add("setting-value");

        Button changeEmail = new Button(t("Change…", "Đổi…"));
        changeEmail.getStyleClass().add("btn-secondary");
        changeEmail.setDisable(account.offline());
        changeEmail.setOnAction(e -> changeEmailDialog(email, emailValue));
        HBox emailControl = new HBox(14, emailValue, changeEmail);
        emailControl.setAlignment(Pos.CENTER_RIGHT);

        Button changePassword = new Button(t("Change…", "Đổi…"));
        changePassword.getStyleClass().add("btn-secondary");
        changePassword.setDisable(account.offline());
        changePassword.setOnAction(e -> changePasswordDialog());

        Button signOut = new Button(t("Sign out", "Đăng xuất"));
        signOut.setGraphic(UiSupport.iconBox(UiSupport.SIGN_OUT, 13, ThemeManager.ink(), 14));
        signOut.setGraphicTextGap(6);
        signOut.getStyleClass().add("btn-secondary");
        signOut.setOnAction(e -> profile.onSignOut().run());

        VBox card = new VBox(
                header,
                row(t("Username", "Tên người dùng"),
                        t("The name collaborators see, also shown in the sidebar.", "Tên người cộng tác nhìn thấy, cũng hiển thị trên thanh bên."),
                        nameControl, false),
                row(t("Email", "Email"), account.offline()
                                ? t("Sign in with an account to change your email.", "Đăng nhập tài khoản để đổi email.")
                                : t("Used to sign in on this machine.", "Dùng để đăng nhập trên máy này."),
                        emailControl, false),
                row(t("Password", "Mật khẩu"), account.offline()
                                ? t("Sign in with an account to change your password.", "Đăng nhập tài khoản để đổi mật khẩu.")
                                : t("Choose a new password for this account.", "Chọn mật khẩu mới cho tài khoản này."),
                        changePassword, false),
                row(t("Sign out", "Đăng xuất"), t("Leave this account. Your projects stay on this machine.",
                        "Rời tài khoản này. Dự án vẫn nằm trên máy."), signOut, true));
        card.getStyleClass().add("settings-card");
        return new VBox(18, title, card, error);
    }

    private void changeEmailDialog(Label headerEmail, Label rowEmail) {
        UiModal modal = UiModal.create(profile.host(), UiSupport.LINK, UiModal.Tone.NEUTRAL,
                t("Change email", "Đổi email"),
                t("You will sign in with the new address from now on.", "Từ giờ bạn đăng nhập bằng địa chỉ mới."));
        TextField email = modal.field(t("New email", "Email mới"), "", "name@example.edu");
        javafx.scene.control.PasswordField password = modal.password(
                t("Current password", "Mật khẩu hiện tại"), "", t("Confirm it is you", "Xác nhận đó là bạn"));
        modal.actions(null, t("Cancel", "Hủy"), t("Change email", "Đổi email"), UiModal.Tone.NEUTRAL, () -> {
            try {
                account = profile.auth().changeEmail(account, email.getText(), password.getText());
            } catch (RuntimeException failure) {
                boolean wrongPassword = failure.getMessage() != null && failure.getMessage().contains("password");
                modal.error(wrongPassword ? password : email, failure.getMessage());
                return false;
            }
            headerEmail.setText(account.email());
            rowEmail.setText(account.email());
            profile.onRenamed().accept(account);
            return true;
        });
        modal.show();
    }

    private void changePasswordDialog() {
        UiModal modal = UiModal.create(profile.host(), UiSupport.LOCK, UiModal.Tone.NEUTRAL,
                t("Change password", "Đổi mật khẩu"),
                t("Use at least 6 characters.", "Dùng ít nhất 6 ký tự."));
        javafx.scene.control.PasswordField current = modal.password(
                t("Current password", "Mật khẩu hiện tại"), "", "");
        javafx.scene.control.PasswordField next = modal.password(
                t("New password", "Mật khẩu mới"), "", "");
        javafx.scene.control.PasswordField again = modal.password(
                t("Confirm new password", "Nhập lại mật khẩu mới"), "", "");
        modal.actions(null, t("Cancel", "Hủy"), t("Change password", "Đổi mật khẩu"), UiModal.Tone.NEUTRAL, () -> {
            if (!next.getText().equals(again.getText())) {
                modal.error(again, t("The two new passwords do not match.", "Hai mật khẩu mới không khớp."));
                return false;
            }
            try {
                profile.auth().changePassword(account, current.getText(), next.getText());
            } catch (RuntimeException failure) {
                boolean wrongCurrent = failure.getMessage() != null && failure.getMessage().contains("not correct");
                modal.error(wrongCurrent ? current : next, failure.getMessage());
                return false;
            }
            return true;
        });
        modal.show();
    }

    private static void showProfileError(Label error, String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }

    private VBox appearancePage() {
        Label title = pageTitle(t("Appearance", "Giao diện"));
        Label label = sectionLabel(t("Interface style", "Phong cách giao diện"));

        HBox modeRow = row(t("Theme mode", "Chế độ giao diện"),
                t("Choose the interface theme and light or dark mode.", "Chọn giao diện và chế độ sáng hoặc tối."),
                choice(AppSettings.MODE, List.of(
                        new Option("light", t("Light", "Sáng")),
                        new Option("dark", t("Dark", "Tối")),
                        new Option("system", t("System", "Hệ thống"))), null), true);
        modeRow.getStyleClass().setAll("setting-row-inner");
        VBox themeBlock = new VBox(18, modeRow, themeCards());
        themeBlock.getStyleClass().add("setting-row");

        VBox card = new VBox(
                themeBlock,
                row(t("Language", "Ngôn ngữ"), t("Select interface language.", "Chọn ngôn ngữ giao diện."),
                        languageChoice(), false),
                row(t("Font style", "Kiểu chữ"), t("Sans or serif for the interface.", "Chữ không chân hoặc có chân cho giao diện."),
                        choice(AppSettings.FONT_STYLE, List.of(
                                new Option("sans", "Sans"), new Option("serif", "Serif")), null), false),
                row(t("Content width", "Độ rộng nội dung"),
                        t("Adjust the maximum width of the Home page content.", "Chỉnh độ rộng tối đa của trang chủ."),
                        choice(AppSettings.CONTENT_WIDTH, List.of(
                                new Option("compact", t("Compact", "Gọn")),
                                new Option("standard", t("Standard", "Chuẩn")),
                                new Option("wide", t("Wide", "Rộng"))), null), true));
        card.getStyleClass().add("settings-card");
        return new VBox(18, title, label, card);
    }

    private VBox editorPage() {
        Label title = pageTitle(t("Editor", "Trình soạn thảo"));

        VBox textCard = new VBox(
                row(t("Font size", "Cỡ chữ"), t("Size of the code in the editor.", "Cỡ chữ của mã trong trình soạn thảo."),
                        choice(AppSettings.EDITOR_FONT_SIZE, sizes(), null), false),
                row(t("Editor font", "Phông chữ"), t("Monospaced font used for LaTeX source.", "Phông đơn cách dùng cho mã LaTeX."),
                        choice(AppSettings.EDITOR_FONT, List.of(
                                new Option("mono", "JetBrains Mono"), new Option("menlo", "Menlo"),
                                new Option("courier", "Courier New")), null), false),
                row(t("Word wrap", "Tự xuống dòng"), t("Wrap long lines instead of scrolling sideways.",
                        "Tự xuống dòng thay vì cuộn ngang."), toggle(AppSettings.WORD_WRAP), true));
        textCard.getStyleClass().add("settings-card");

        VBox behaviourCard = new VBox(
                row(t("Auto-close brackets", "Tự đóng ngoặc"),
                        t("Type { ( [ or $ and the closing one is added for you.", "Gõ { ( [ hoặc $ sẽ tự thêm ký tự đóng."),
                        toggle(AppSettings.AUTO_CLOSE), false),
                row(t("Auto-save", "Tự động lưu"),
                        t("Save every 20 seconds while there are unsaved changes.", "Lưu mỗi 20 giây khi có thay đổi chưa lưu."),
                        toggle(AppSettings.AUTO_SAVE), true));
        behaviourCard.getStyleClass().add("settings-card");

        return new VBox(18, title,
                sectionLabel(t("Text", "Văn bản")), textCard,
                sectionLabel(t("Behaviour", "Hành vi")), behaviourCard);
    }

    private VBox compilationPage() {
        Label error = new Label();
        error.getStyleClass().add("modal-error");
        error.setWrapText(true);
        TextField path = new TextField(settings.get(AppSettings.LATEXMK_PATH));
        path.setId("latexmk-path");
        path.setPromptText(t("Automatic discovery", "Tự tìm toolchain"));
        path.getStyleClass().add("modal-field");
        path.setPrefWidth(280);
        Button browse = new Button(t("Browse…", "Chọn…"));
        browse.getStyleClass().add("btn-secondary");
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(t("Choose latexmk executable", "Chọn tệp thực thi latexmk"));
            File selected = chooser.showOpenDialog(getScene().getWindow());
            if (selected != null) path.setText(selected.getAbsolutePath());
        });
        TextField timeout = new TextField(settings.get(AppSettings.COMPILE_TIMEOUT));
        timeout.setId("compile-timeout");
        timeout.setPrefWidth(100);
        timeout.getStyleClass().add("modal-field");
        Button save = new Button(t("Save compiler settings", "Lưu cài đặt biên dịch"));
        save.setId("save-compiler-settings");
        save.getStyleClass().add("btn-primary");
        save.setOnAction(e -> {
            try {
                long seconds = Long.parseLong(timeout.getText().strip());
                new com.overleaf.client.compiler.LatexCompilerService.Options(path.getText(), seconds);
                String executable = path.getText().strip();
                if (!executable.isBlank() && !java.nio.file.Files.isRegularFile(java.nio.file.Path.of(executable))) {
                    throw new IllegalArgumentException(t("Choose an existing latexmk executable, or leave it blank for PATH.",
                            "Chọn tệp latexmk có thật, hoặc để trống để dùng PATH."));
                }
                if (!executable.isBlank()) executable = java.nio.file.Path.of(executable).toAbsolutePath().normalize().toString();
                settings.set(AppSettings.LATEXMK_PATH, executable);
                settings.set(AppSettings.COMPILE_TIMEOUT, Long.toString(seconds));
                error.setText(t("Saved. Applies to the next build.", "Đã lưu. Áp dụng cho lần biên dịch tiếp theo."));
            } catch (RuntimeException invalid) {
                error.setText(t("Use an existing executable and a timeout of 0 or more seconds.",
                        "Chọn tệp thực thi có thật và timeout từ 0 giây trở lên."));
            }
        });
        TextArea diagnostic = new TextArea();
        diagnostic.setId("compiler-diagnostics");
        diagnostic.setEditable(false);
        diagnostic.setWrapText(true);
        diagnostic.setPrefRowCount(7);
        diagnostic.getStyleClass().add("modal-field");
        Button check = new Button(t("Check saved toolchain", "Kiểm tra toolchain đã lưu"));
        check.setId("check-toolchain");
        check.getStyleClass().add("btn-secondary");
        check.setOnAction(e -> {
            com.overleaf.client.compiler.LatexCompilerService.Options options;
            try { options = settings.compileOptions(); }
            catch (RuntimeException invalid) { error.setText(invalid.getMessage()); return; }
            check.setDisable(true);
            diagnostic.setText(t("Checking…", "Đang kiểm tra…"));
            Task<List<ToolchainDiagnostics.Tool>> task = new Task<>() {
                @Override protected List<ToolchainDiagnostics.Tool> call() { return ToolchainDiagnostics.inspect(options); }
            };
            task.setOnSucceeded(done -> {
                StringBuilder report = new StringBuilder();
                for (var tool : task.getValue()) {
                    report.append(tool.available() ? "OK " : "MISSING ").append(tool.name()).append("\n")
                            .append(tool.executable()).append("\n").append(tool.detail()).append("\n\n");
                }
                diagnostic.setText(report.toString());
                check.setDisable(false);
            });
            task.setOnFailed(done -> { diagnostic.setText(task.getException().toString()); check.setDisable(false); });
            Thread worker = new Thread(task, "toolchain-check");
            worker.setDaemon(true);
            worker.start();
        });
        VBox compileCard = new VBox(
                row(t("Default compiler", "Trình biên dịch mặc định"),
                        t("Engine used when you press Recompile.", "Bộ biên dịch dùng khi bấm Biên dịch lại."),
                        choice(AppSettings.COMPILER, List.of(
                                new Option("pdfLaTeX", "pdfLaTeX"), new Option("XeLaTeX", "XeLaTeX"),
                                new Option("LuaLaTeX", "LuaLaTeX")), null), false),
                row("latexmk", t("Leave blank to find TeX on PATH or the local portable installation.",
                        "Để trống để tự tìm TeX trên PATH hoặc bộ portable cục bộ."), new HBox(8, path, browse), false),
                row(t("Timeout (seconds)", "Timeout (giây)"), t("0 means no time limit. Stop remains available.",
                        "0 là không giới hạn thời gian. Vẫn có thể bấm Dừng."), timeout, true));
        compileCard.getStyleClass().add("settings-card");

        Label help = new Label(t("Install TeX Live / MiKTeX with latexmk. No packages are downloaded by this check.",
                "Cài TeX Live / MiKTeX có latexmk. Bước kiểm tra không tải gói TeX."));
        help.setWrapText(true);
        help.getStyleClass().add("setting-desc");
        return new VBox(18, pageTitle(t("Compilation", "Biên dịch")), compileCard, save, error, help, check, diagnostic);
    }

    private VBox aboutPage() {
        Label title = pageTitle(t("About", "Giới thiệu"));
        Button reset = new Button(t("Reset", "Đặt lại"));
        reset.getStyleClass().add("btn-secondary");
        reset.setOnAction(e -> {
            settings.resetAll();
            show(section);
        });
        VBox card = new VBox(
                row("Overleaf Desktop", t("Local-first LaTeX editor with live collaboration.",
                        "Trình soạn LaTeX ưu tiên cục bộ, hỗ trợ cộng tác trực tiếp."), versionLabel(), false),
                row(t("Reset settings", "Đặt lại cài đặt"),
                        t("Restore every setting on these pages to its default.", "Khôi phục mọi cài đặt về mặc định."),
                        reset, true));
        card.getStyleClass().add("settings-card");
        return new VBox(18, title, card);
    }

    private VBox projectsPage() {
        TextField path = new TextField(settings.projects().projectsDirectory().toString());
        path.setId("settings-project-directory");
        path.getStyleClass().add("modal-field");
        HBox.setHgrow(path, Priority.ALWAYS);
        Button browse = new Button(t("Browse…", "Chọn…"));
        browse.getStyleClass().add("btn-secondary");
        browse.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle(t("Choose the default project folder", "Chọn thư mục dự án mặc định"));
            File selected = chooser.showDialog(getScene().getWindow());
            if (selected != null) path.setText(selected.getAbsolutePath());
        });
        Label result = new Label(); result.setWrapText(true); result.getStyleClass().add("setting-desc");
        Button save = new Button(t("Save", "Lưu"));
        save.setId("save-project-directory"); save.getStyleClass().add("btn-primary");
        save.setOnAction(e -> {
            String selected = path.getText();
            if (selected.isBlank()) { result.setText(t("Choose a folder.", "Hãy chọn thư mục.")); return; }
            save.setDisable(true);
            Task<Void> task = new Task<>() {
                @Override protected Void call() { settings.projects().setProjectsDirectory(java.nio.file.Path.of(selected)); return null; }
            };
            task.setOnSucceeded(done -> { save.setDisable(false); result.setText(t("Saved. Existing projects keep their locations.", "Đã lưu. Dự án cũ giữ nguyên vị trí.")); });
            task.setOnFailed(done -> { save.setDisable(false); result.setText(com.overleaf.client.ui.backend.ProjectFailure.message(task.getException(), vi)); });
            Thread worker = new Thread(task, "project-directory"); worker.setDaemon(true); worker.start();
        });
        Label hint = new Label(t("Used for new projects. Choose a different location in each creation dialog, or right-click an existing project to move it.",
                "Dùng cho dự án mới. Bạn có thể chọn vị trí khác trong hộp thoại tạo dự án, hoặc nhấp phải dự án có sẵn để chuyển thư mục."));
        hint.setWrapText(true); hint.getStyleClass().add("setting-desc");
        return new VBox(18, pageTitle(t("Projects", "Dự án")), hint, new HBox(8, path, browse), save, result);
    }

    private static Label versionLabel() {
        Label version = new Label("v1.0");
        version.getStyleClass().add("setting-desc");
        return version;
    }

    // --------------------------------------------------------------- building

    private Label pageTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-title");
        return label;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-section");
        return label;
    }

    /** Title and description on the left, the control on the right; {@code last} drops the divider. */
    private HBox row(String title, String description, Node control, boolean last) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("setting-title");
        Label desc = new Label(description);
        desc.getStyleClass().add("setting-desc");
        desc.setWrapText(true);
        VBox copy = new VBox(4, titleLabel, desc);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox row = new HBox(24, copy, control);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        if (last) row.getStyleClass().add("setting-row-last");
        return row;
    }

    private ComboBox<Option> choice(String key, List<Option> options, Consumer<String> after) {
        ComboBox<Option> box = new ComboBox<>();
        box.getItems().addAll(options);
        String current = settings.get(key);
        for (Option option : options) {
            if (option.id().equals(current)) box.setValue(option);
        }
        box.getStyleClass().add("setting-combo");
        box.setMinWidth(150);
        box.setOnAction(e -> {
            Option picked = box.getValue();
            if (picked == null) return;
            settings.set(key, picked.id());
            if (after != null) after.accept(picked.id());
        });
        return box;
    }

    private ComboBox<Option> languageChoice() {
        List<Option> options = List.of(new Option("en", "English"), new Option("vi", "Tiếng Việt"));
        ComboBox<Option> box = new ComboBox<>();
        box.getItems().addAll(options);
        box.setValue(vi ? options.get(1) : options.get(0));
        box.getStyleClass().add("setting-combo");
        box.setMinWidth(150);
        box.setOnAction(e -> {
            boolean wantsVi = box.getValue() != null && "vi".equals(box.getValue().id());
            if (wantsVi != vi) onLanguage.accept(wantsVi);
        });
        return box;
    }

    private static List<Option> sizes() {
        List<Option> list = new ArrayList<>();
        for (int size = 11; size <= 20; size++) {
            list.add(new Option(Integer.toString(size), size + " px"));
        }
        return list;
    }

    private StackPane toggle(String key) {
        Region track = new Region();
        track.getStyleClass().add("switch-track");
        Circle thumb = new Circle(8.5, Color.WHITE);
        thumb.getStyleClass().add("switch-thumb");
        StackPane holder = new StackPane(track, thumb);
        holder.setAlignment(Pos.CENTER_LEFT);
        holder.setMinSize(42, 24);
        holder.setPrefSize(42, 24);
        holder.setMaxSize(42, 24);
        holder.getStyleClass().add("switch");
        holder.setCursor(javafx.scene.Cursor.HAND);
        StackPane.setMargin(thumb, new Insets(0, 0, 0, 3));

        Runnable paint = () -> {
            boolean on = settings.bool(key);
            holder.getStyleClass().remove("switch-on");
            if (on) holder.getStyleClass().add("switch-on");
            TranslateTransition slide = new TranslateTransition(Duration.millis(110), thumb);
            slide.setToX(on ? 18 : 0);
            slide.play();
        };
        holder.setOnMouseClicked(e -> {
            settings.set(key, !settings.bool(key));
            paint.run();
        });
        paint.run();
        return holder;
    }

    // ----------------------------------------------------------- theme cards

    private record Swatch(String id, String name, String background, String bar, String ring) {
    }

    private HBox themeCards() {
        List<Swatch> swatches = List.of(
                new Swatch("forest", "Forest", "#EFEFEC", "#567A62", "#2F5B47"),
                new Swatch("mint", "Mint", "#EDF5F2", "#5FA697", "#3E9B8B"),
                new Swatch("bee", "Bee", "linear-gradient(to bottom, #F3EDC8, #E3E9F2)", "#C4B16A", "#B8A04A"),
                new Swatch("parchment", "Parchment", "#FBF8F2", "#C4694B", "#C0634A"));
        HBox cards = new HBox(22);
        List<VBox> built = new ArrayList<>();
        for (Swatch swatch : swatches) {
            VBox card = themeCard(swatch);
            built.add(card);
            card.setOnMouseClicked(e -> {
                settings.set(AppSettings.THEME, swatch.id());
                for (VBox other : built) {
                    markSelected(other, swatches, other == card ? swatch : null);
                }
            });
            cards.getChildren().add(card);
            markSelected(card, swatches, swatch.id().equals(settings.get(AppSettings.THEME)) ? swatch : null);
        }
        return cards;
    }

    private VBox themeCard(Swatch swatch) {
        Region bar = new Region();
        bar.setPrefSize(76, 7);
        bar.setMaxSize(76, 7);
        bar.setStyle("-fx-background-color: " + swatch.bar() + "; -fx-background-radius: 4;");
        Region line = new Region();
        line.setPrefSize(60, 6);
        line.setMaxSize(60, 6);
        line.setStyle("-fx-background-color: rgba(120,120,110,0.22); -fx-background-radius: 3;");

        Region inner1 = new Region();
        inner1.setPrefSize(60, 6);
        inner1.setMaxSize(60, 6);
        inner1.setStyle("-fx-background-color: rgba(120,120,110,0.30); -fx-background-radius: 3;");
        Region inner2 = new Region();
        inner2.setPrefSize(40, 6);
        inner2.setMaxSize(40, 6);
        inner2.setStyle("-fx-background-color: rgba(120,120,110,0.30); -fx-background-radius: 3;");
        VBox sheet = new VBox(7, inner1, inner2);
        sheet.setPadding(new Insets(13, 13, 0, 13));
        sheet.setPrefSize(128, 62);
        sheet.setMaxSize(128, 62);
        sheet.setStyle("-fx-background-color: rgba(255,255,255,0.78); -fx-background-radius: 8 8 0 0;"
                + " -fx-border-color: rgba(120,120,110,0.25); -fx-border-radius: 8 8 0 0; -fx-border-width: 1 1 0 1;");

        VBox lines = new VBox(7, bar, line);
        lines.setPadding(new Insets(14, 0, 0, 16));
        VBox top = new VBox(10, lines, sheet);
        top.setAlignment(Pos.TOP_LEFT);
        VBox.setMargin(sheet, new Insets(0, 0, 0, 16));

        Circle radio = new Circle(9, Color.WHITE);
        radio.setStroke(Color.web("#C9C9C2"));
        radio.setUserData("radio");
        StackPane.setAlignment(radio, Pos.TOP_RIGHT);
        StackPane.setMargin(radio, new Insets(18, 18, 0, 0));

        StackPane preview = new StackPane(top, radio);
        preview.setAlignment(Pos.TOP_LEFT);
        preview.setPrefSize(158, 100);
        preview.setMinSize(158, 100);
        preview.setMaxSize(158, 100);
        preview.setStyle("-fx-background-color: " + swatch.background() + "; -fx-background-radius: 10;");
        preview.setUserData("preview");

        Label name = new Label(swatch.name());
        name.getStyleClass().add("theme-card-name");
        VBox card = new VBox(8, preview, name);
        card.setAlignment(Pos.TOP_CENTER);
        card.setCursor(javafx.scene.Cursor.HAND);
        card.getStyleClass().add("theme-card");
        card.setUserData(swatch.id());
        return card;
    }

    /** Selected cards get a ring in their own color and a filled radio; others go back to plain. */
    private void markSelected(VBox card, List<Swatch> swatches, Swatch selected) {
        StackPane preview = (StackPane) card.getChildren().get(0);
        Circle radio = (Circle) preview.getChildren().get(1);
        Swatch mine = swatches.stream().filter(s -> s.id().equals(card.getUserData())).findFirst().orElseThrow();
        String base = preview.getStyle().replaceAll("-fx-border-color:[^;]*;|-fx-border-width:[^;]*;|-fx-border-radius:[^;]*;", "");
        if (selected != null) {
            preview.setStyle(base + " -fx-border-color: " + mine.ring() + "; -fx-border-width: 1.5; -fx-border-radius: 10;");
            radio.setFill(Color.web(mine.ring()));
            radio.setStroke(Color.web(mine.ring()));
            radio.setRadius(9);
            card.getStyleClass().remove("theme-card-selected");
            card.getStyleClass().add("theme-card-selected");
        } else {
            preview.setStyle(base);
            radio.setFill(Color.WHITE);
            radio.setStroke(Color.web("#C9C9C2"));
            card.getStyleClass().remove("theme-card-selected");
        }
    }
}

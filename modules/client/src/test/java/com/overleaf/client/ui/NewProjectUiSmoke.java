package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.LocalBackend;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/** Explicit JavaFX smoke; uses a fresh isolated home and real project operations. */
public final class NewProjectUiSmoke {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(home)) throw new IllegalArgumentException("Use a new test home");
        System.setProperty("overleaf.home", home.toString());
        LocalBackend backend = new LocalBackend(home);
        backend.register("Project Test", "projects@example.test", "test-password", true).join();
        new AppSettings(backend).set(AppSettings.LANGUAGE, "vi");
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> started.complete(null));
        started.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        OverleafFxApp app = UiSmoke.fx(OverleafFxApp::new);
        Stage stage =
                UiSmoke.fx(() -> {
                    Stage s = new Stage();
                    s.setOpacity(0);
                    app.start(s);
                    return s;
                });
        try {
            Path custom = home.resolve("Vị trí riêng");
            UiSmoke.fx(() -> {
                HomeView view = (HomeView) UiSmoke.field(app, "homeView");
                Button anchor = buttons(stage.getScene().getRoot(), "Dự án mới");
                UiSmoke.invoke(view, "showNewProjectMenu", anchor);
                ContextMenu menu = (ContextMenu) UiSmoke.field(view, "newProjectMenu");
                Menu templates =
                        (Menu)
                                menu.getItems().stream()
                                        .filter(i -> "project-templates".equals(i.getId()))
                                        .findFirst()
                                        .orElseThrow();
                if (templates.getItems().size() != 11)
                    throw new AssertionError("Missing template categories");
                if (!templates.getText().contains("mẫu"))
                    throw new AssertionError("Menu not translated");
                menu.hide();
                menu.getItems().stream()
                        .filter(i -> "new-project-blank".equals(i.getId()))
                        .findFirst()
                        .orElseThrow()
                        .fire();
                text(stage, "new-project-name").setText("Luận văn UI");
                text(stage, "new-project-directory").setText(custom.toString());
                buttons(stage.getScene().getRoot(), "Tạo dự án").fire();
                return null;
            });
            await(() -> Files.isRegularFile(custom.resolve("Luận văn UI/main.tex")));
            await(() -> UiSmoke.fx(() -> stage.getScene().lookup("#new-project-name") == null));
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "showHome");
                return null;
            });
            Path next = home.resolve("Default mới");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(UiSmoke.field(app, "homeView"), "projectStorageDialog");
                text(stage, "default-project-directory").setText(next.toString());
                buttons(stage.getScene().getRoot(), "Lưu").fire();
                return null;
            });
            await(() -> backend.projectsDirectory().equals(next));
            await(() ->
                    UiSmoke.fx(
                            () -> stage.getScene().lookup("#default-project-directory") == null));
            if (backend.projects().stream().noneMatch(p -> p.name().equals("Luận văn UI")))
                throw new AssertionError("Old project disappeared");
            var original =
                    backend.projects().stream()
                            .filter(p -> p.name().equals("Luận văn UI"))
                            .findFirst()
                            .orElseThrow();
            Path moved = home.resolve("Chuyển tới");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(UiSmoke.field(app, "homeView"), "moveProjectDialog", original);
                text(stage, "move-project-directory").setText(moved.toString());
                buttons(stage.getScene().getRoot(), "Chuyển dự án").fire();
                return null;
            });
            await(() ->
                    Files.isRegularFile(moved.resolve("Luận văn UI/main.tex"))
                            && !Files.exists(Path.of(original.folder())));
            await(() ->
                    UiSmoke.fx(() -> stage.getScene().lookup("#move-project-directory") == null));
            Path settingsFolder = home.resolve("Settings location");
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "showSettings", false, "projects");
                text(stage, "settings-project-directory").setText(settingsFolder.toString());
                ((Button) stage.getScene().lookup("#save-project-directory")).fire();
                return null;
            });
            await(() -> backend.projectsDirectory().equals(settingsFolder));
            await(() ->
                    UiSmoke.fx(() ->
                            !((Button) stage.getScene().lookup("#save-project-directory"))
                                    .isDisabled()));
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "setVietnameseUi", false);
                return null;
            });
            await(() ->
                    UiSmoke.fx(() ->
                            stage.getScene().getRoot().lookupAll(".button").stream()
                                    .anyMatch(n ->
                                            n instanceof Button b
                                                    && "New project".equals(b.getText()))));
            UiSmoke.fx(() -> {
                HomeView view = (HomeView) UiSmoke.field(app, "homeView");
                UiSmoke.invoke(
                        view,
                        "showNewProjectMenu",
                        buttons(stage.getScene().getRoot(), "New project"));
                ContextMenu menu = (ContextMenu) UiSmoke.field(view, "newProjectMenu");
                if (menu.getItems().stream().noneMatch(i -> "Blank project".equals(i.getText())))
                    throw new AssertionError("English menu missing");
                menu.hide();
                return null;
            });
            System.out.println(
                    "NEW_PROJECT_UI_SMOKE_OK vi/en menu, create custom folder, change default, move existing project, Settings storage");
        } finally {
            UiSmoke.fx(() -> {
                UiSmoke.invoke(app, "cleanupOnExit");
                stage.hide();
                return null;
            });
            Platform.exit();
        }
    }

    private static TextField text(Stage stage, String id) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        return (TextField) stage.getScene().lookup("#" + id);
    }

    private static Button buttons(Parent root, String label) {
        return root.lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && label.equals(b.getText()))
                .map(n -> (Button) n)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing button " + label));
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.call()) {
            if (System.nanoTime() > end) throw new AssertionError("UI operation timed out");
            Thread.sleep(60);
        }
    }
}

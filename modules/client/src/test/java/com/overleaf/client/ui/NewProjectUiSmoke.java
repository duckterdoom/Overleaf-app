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
            UiSmoke.fx(() -> {
                HomeView view = UiSmoke.field(app, "homeView");
                UiSmoke.invoke(
                        view,
                        "showNewProjectMenu",
                        buttons(stage.getScene().getRoot(), "Dự án mới"));
                ContextMenu menu = UiSmoke.field(view, "newProjectMenu");
                Menu templates =
                        (Menu)
                                menu.getItems().stream()
                                        .filter(i -> "project-templates".equals(i.getId()))
                                        .findFirst()
                                        .orElseThrow();
                menu.hide();
                templates.getItems().stream()
                        .filter(i -> "template-theses".equals(i.getId()))
                        .findFirst()
                        .orElseThrow()
                        .fire();
                if (!"theses"
                        .equals(
                                ((javafx.scene.control.ComboBox<?>)
                                                stage.getScene().lookup("#template-category"))
                                        .getValue()))
                    throw new AssertionError("Category was not selected");
                ((javafx.scene.control.ComboBox<String>)
                                stage.getScene().lookup("#template-category"))
                        .setValue("");
                text(stage, "template-search").setText("luận văn");
                if (stage.getScene().lookup("#create-template-masters-thesis") == null)
                    throw new AssertionError("Vietnamese thesis search missing");
                ((Button) stage.getScene().lookup("#create-template-masters-thesis")).fire();
                text(stage, "new-project-name").setText("Luận văn thư viện UI");
                text(stage, "new-project-directory").setText(custom.toString());
                buttons(stage.getScene().getRoot(), "Tạo dự án").fire();
                return null;
            });
            Path thesis = custom.resolve("Luận văn thư viện UI");
            await(() -> Files.isRegularFile(thesis.resolve("chapters/05-content.tex")));
            await(() -> UiSmoke.fx(() -> stage.getScene().lookup("#new-project-name") == null));
            UiSmoke.fx(() -> {
                if (!"main.tex".equals(UiSmoke.field(app, "currentFile")))
                    throw new AssertionError("Template main file not opened");
                if (!"XeLaTeX"
                        .equals(
                                ((javafx.scene.control.ComboBox<?>)
                                                UiSmoke.field(app, "compilerCombo"))
                                        .getValue()))
                    throw new AssertionError("Template engine not applied");
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
                UiSmoke.invoke(view, "templateGallery");
                text(stage, "template-search").setText("thesis");
                if (stage.getScene().lookup("#create-template-masters-thesis") == null
                        || stage.getScene().lookup("#create-template-graduation-project") == null)
                    throw new AssertionError("English thesis search missing");
                ((javafx.scene.control.ComboBox<String>) stage.getScene().lookup("#template-tag"))
                        .setValue("engineering");
                if (stage.getScene().lookup("#create-template-masters-thesis") != null)
                    throw new AssertionError("Tag filter did not apply");
                ((javafx.scene.control.ComboBox<String>) stage.getScene().lookup("#template-tag"))
                        .setValue("biblatex");
                if (stage.getScene().lookup("#create-template-masters-thesis") == null)
                    throw new AssertionError("Tag filter removed matching template");
                screenshot(stage, home.resolve("template-gallery.png"));
                return null;
            });
            System.out.println(
                    "TEMPLATE_GALLERY_UI_OK bilingual categories, search and multi-file creation");
            UiSmoke.fx(() -> {
                buttons(stage.getScene().getRoot(), "Close").fire();
                return null;
            });
            Path fixture = home.resolve("package-fixture");
            Path fakeBin = Files.createDirectories(fixture.resolve("target/TinyTeX/bin/windows"));
            Files.createDirectories(fixture.resolve("target/TinyTeX/texmf-dist"));
            Files.createFile(fakeBin.resolve("tlmgr.bat"));
            java.util.concurrent.atomic.AtomicInteger installs =
                    new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicBoolean retried =
                    new java.util.concurrent.atomic.AtomicBoolean();
            var service =
                    new com.overleaf.client.ui.backend.TemplatePrerequisites(
                            fakeBin,
                            fixture,
                            true,
                            (command, time) -> {
                                if (command.contains("install")) {
                                    installs.incrementAndGet();
                                    return new com.overleaf.client.ui.backend.TemplatePrerequisites
                                            .Result(0, "installed");
                                }
                                return new com.overleaf.client.ui.backend.TemplatePrerequisites
                                        .Result(
                                        command.getLast().equals("lmodern.sty")
                                                        && installs.get() == 0
                                                ? 1
                                                : 0,
                                        "found");
                            });
            var report =
                    service.inspect(
                            com.overleaf.client.ui.backend.ProjectTemplates.get(
                                    "research-article"));
            UiSmoke.fx(() -> {
                TemplateInstallDialog.show(
                        (javafx.scene.layout.StackPane) UiSmoke.field(app, "screenHost"),
                        service,
                        report,
                        false,
                        () -> retried.set(true));
                if (installs.get() != 0) throw new AssertionError("Installed before confirmation");
                buttons(stage.getScene().getRoot(), "Cancel").fire();
                if (installs.get() != 0) throw new AssertionError("Installed after cancellation");
                TemplateInstallDialog.show(
                        (javafx.scene.layout.StackPane) UiSmoke.field(app, "screenHost"),
                        service,
                        report,
                        false,
                        () -> retried.set(true));
                ((Button) stage.getScene().lookup("#template-install-confirm")).fire();
                return null;
            });
            await(retried::get);
            if (installs.get() != 1
                    || !service.inspect(
                                    com.overleaf.client.ui.backend.ProjectTemplates.get(
                                            "research-article"))
                            .ready()) throw new AssertionError("Bundled install/recheck failed");
            System.out.println(
                    "NEW_PROJECT_UI_SMOKE_OK vi/en categories and search, create multi-file thesis with engine, custom folder, default storage and move");
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

    private static void screenshot(Stage stage, Path path) throws Exception {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        var image = stage.getScene().snapshot(null);
        int w = (int) image.getWidth(), h = (int) image.getHeight();
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
        var out =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, pixels, 0, w);
        javax.imageio.ImageIO.write(out, "png", path.toFile());
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

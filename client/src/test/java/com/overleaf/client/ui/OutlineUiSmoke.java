package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.LocalBackend;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import java.nio.file.*;
import java.util.concurrent.*;

/** Explicit display smoke for resizing the file tree/outline divider. */
public final class OutlineUiSmoke {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(home)) throw new IllegalArgumentException("Use a new test home.");
        System.setProperty("overleaf.home", home.toString());
        LocalBackend backend = new LocalBackend(home);
        backend.register("Outline Test", "outline@example.test", "test-password", true).join();
        CompletableFuture<Void> started = new CompletableFuture<>();
        Platform.startup(() -> started.complete(null));
        started.get(10, TimeUnit.SECONDS);
        Platform.setImplicitExit(false);
        OverleafFxApp app = UiSmoke.fx(OverleafFxApp::new);
        Stage stage = UiSmoke.fx(() -> { Stage s = new Stage(); s.setOpacity(0); app.start(s); return s; });
        try {
            UiSmoke.fx(() -> { UiSmoke.invoke(app, "openProject", backend.projects().get(0)); return null; });
            SplitPane split = UiSmoke.fx(() -> (SplitPane) stage.getScene().lookup("#file-outline-split"));
            UiSmoke.fx(() -> {
                ((javafx.scene.control.ComboBox<String>) UiSmoke.field(app, "compilerCombo")).setValue("XeLaTeX");
                return null;
            });
            double initial = UiSmoke.fx(() -> split.getItems().get(1).getBoundsInParent().getHeight());
            UiSmoke.fx(() -> { split.setDividerPositions(0.30); stage.getScene().getRoot().layout(); return null; });
            double expanded = UiSmoke.fx(() -> split.getItems().get(1).getBoundsInParent().getHeight());
            if (expanded <= initial + 50) throw new AssertionError("Outline did not expand: " + initial + " -> " + expanded);
            UiSmoke.fx(() -> {
                Event.fireEvent(split, new MouseEvent(MouseEvent.MOUSE_RELEASED, 0, 0, 0, 0, MouseButton.PRIMARY,
                        1, false, false, false, false, false, false, false, false, false, true, null));
                return null;
            });
            double saved = Double.parseDouble(new AppSettings(backend).get(AppSettings.OUTLINE_SPLIT));
            if (Math.abs(saved - 0.30) > 0.04) throw new AssertionError("Divider position was not saved: " + saved);
            UiSmoke.fx(() -> {
                if (!"XeLaTeX".equals(((javafx.scene.control.ComboBox<?>) UiSmoke.field(app, "compilerCombo")).getValue())) {
                    throw new AssertionError("Resizing Outline must not reset the compiler.");
                }
                return null;
            });
            UiSmoke.fx(() -> { split.setDividerPositions(0.78); stage.getScene().getRoot().layout(); return null; });
            double smaller = UiSmoke.fx(() -> split.getItems().get(1).getBoundsInParent().getHeight());
            if (smaller >= expanded - 50 || smaller < 70) throw new AssertionError("Outline sizing limits failed.");
            System.out.println("OUTLINE_UI_SMOKE_OK initial=" + initial + " expanded=" + expanded + " smaller=" + smaller + " saved=" + saved);
        } finally {
            UiSmoke.fx(() -> { UiSmoke.invoke(app, "cleanupOnExit"); stage.hide(); return null; });
            Platform.exit();
        }
    }
}

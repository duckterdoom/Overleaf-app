package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.TemplatePrerequisites;
import java.util.concurrent.atomic.AtomicReference;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

/** The user's click is the consent boundary; constructing this dialog never installs packages. */
final class TemplateInstallDialog {
    static void show(
            StackPane host,
            TemplatePrerequisites service,
            TemplatePrerequisites.Report report,
            boolean vi,
            Runnable retry) {
        UiModal modal =
                UiModal.create(
                                host,
                                UiSupport.INFO,
                                UiModal.Tone.NEUTRAL,
                                vi ? "Bổ sung gói TinyTeX?" : "Install TinyTeX packages?",
                                report.message(vi))
                        .localized(vi)
                        .width(560);
        Label progress =
                new Label(
                        vi
                                ? "Chỉ bản TinyTeX đi kèm sẽ được cập nhật. Cần kết nối mạng."
                                : "Only bundled TinyTeX will be modified. An internet connection is required.");
        progress.setWrapText(true);
        modal.node(progress);
        AtomicReference<Task<Void>> running = new AtomicReference<>();
        Button[] install = {null};
        install[0] =
                modal.actions(
                        null,
                        vi ? "Hủy" : "Cancel",
                        vi ? "Cài gói" : "Install packages",
                        UiModal.Tone.NEUTRAL,
                        () -> {
                            install[0].setDisable(true);
                            Task<Void> task =
                                    new Task<>() {
                                        @Override
                                        protected Void call() throws Exception {
                                            service.install(
                                                    report,
                                                    pkg ->
                                                            updateMessage(
                                                                    (vi
                                                                                    ? "Đang cài "
                                                                                    : "Installing ")
                                                                            + pkg));
                                            return null;
                                        }
                                    };
                            running.set(task);
                            progress.textProperty().bind(task.messageProperty());
                            task.setOnSucceeded(e -> {
                                modal.close();
                                retry.run();
                            });
                            task.setOnFailed(e -> {
                                progress.textProperty().unbind();
                                install[0].setDisable(false);
                                modal.error(
                                        null,
                                        (vi
                                                        ? "Không cài được gói: "
                                                        : "Package installation failed: ")
                                                + task.getException().getMessage());
                            });
                            Thread thread = new Thread(task, "template-packages");
                            thread.setDaemon(true);
                            thread.start();
                            return false;
                        });
        install[0].setId("template-install-confirm");
        modal.onClose(() -> {
            Task<Void> task = running.get();
            if (task != null && !task.isDone()) task.cancel(true);
        });
        modal.show();
    }
}

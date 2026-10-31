package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.AuthGateway;
import com.overleaf.client.ui.backend.ProfileGateway;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;

/**
 * "Profile picture" modal: preview, choose an image, remove it. Opened by
 * clicking the avatar on Home or in the editor sidebar.
 */
public final class ProfilePhotoDialog {

    private static final double PREVIEW = 76;

    private ProfilePhotoDialog() {
    }

    public static void show(StackPane host, ProfileGateway profiles, AuthGateway.Session session,
                            boolean vietnamese, Runnable onChanged) {
        UiModal modal = UiModal.create(host, UiSupport.USERS, UiModal.Tone.NEUTRAL,
                vietnamese ? "Ảnh đại diện" : "Profile picture",
                vietnamese ? "Hiển thị thay cho chữ viết tắt tên của bạn." : "Shown in place of your initials.");

        Label preview = new Label();
        preview.getStyleClass().addAll("avatar", "avatar-large");
        Label name = new Label(session.name());
        name.getStyleClass().add("modal-title");
        Label meta = new Label(session.offline()
                ? (vietnamese ? "Phiên ngoại tuyến" : "Offline session")
                : session.email());
        meta.getStyleClass().add("modal-sub");

        Button choose = new Button(vietnamese ? "Chọn ảnh…" : "Choose image…");
        choose.setGraphic(UiSupport.iconBox(UiSupport.PLUS, 12, Color.web("#2B2B28"), 13));
        choose.getStyleClass().add("btn-secondary");
        Button remove = new Button(vietnamese ? "Xóa ảnh" : "Remove photo");
        remove.getStyleClass().add("btn-link-danger");

        HBox buttons = new HBox(10, choose, remove);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox info = new VBox(3, name, meta, buttons);
        VBox.setMargin(buttons, new javafx.geometry.Insets(8, 0, 0, 0));
        HBox row = new HBox(18, preview, info);
        row.setAlignment(Pos.CENTER_LEFT);
        modal.node(row);

        Label hint = new Label(vietnamese ? "PNG, JPG hoặc GIF, tối đa 5 MB. Ảnh được cắt vuông ở giữa."
                : "PNG, JPG or GIF up to 5 MB. Cropped to a centred square.");
        hint.getStyleClass().add("modal-hint");
        modal.node(hint);

        Runnable refresh = () -> {
            var picture = profiles.avatar(session);
            UiSupport.applyAvatar(preview, session.name(), picture, PREVIEW);
            remove.setVisible(picture.isPresent());
            remove.setManaged(picture.isPresent());
        };
        refresh.run();

        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(vietnamese ? "Chọn ảnh đại diện" : "Choose a profile picture");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    vietnamese ? "Ảnh" : "Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            File home = new File(System.getProperty("user.home"), "Pictures");
            if (home.isDirectory()) chooser.setInitialDirectory(home);
            File picked = chooser.showOpenDialog(host.getScene().getWindow());
            if (picked != null) {
                apply(modal, profiles, session, picked, vietnamese, refresh, onChanged);
            }
        });
        remove.setOnAction(e -> {
            try {
                profiles.removeAvatar(session);
            } catch (RuntimeException failure) {
                modal.error(null, failure.getMessage());
                return;
            }
            refresh.run();
            onChanged.run();
        });

        modal.actions(null, null, vietnamese ? "Xong" : "Done", UiModal.Tone.NEUTRAL, () -> true);
        modal.show();
    }

    /** Validates the image can be shown before storing it. Package-visible for the smoke run. */
    static void apply(UiModal modal, ProfileGateway profiles, AuthGateway.Session session, File picked,
                      boolean vietnamese, Runnable refresh, Runnable onChanged) {
        if (UiSupport.circularImage(picked.toPath(), PREVIEW) == null) {
            modal.error(null, vietnamese ? "Không đọc được tệp ảnh này." : "That file could not be read as an image.");
            return;
        }
        try {
            profiles.setAvatar(session, picked.toPath());
        } catch (RuntimeException failure) {
            modal.error(null, failure.getMessage());
            return;
        }
        refresh.run();
        onChanged.run();
    }
}

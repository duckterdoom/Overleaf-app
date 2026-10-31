package com.overleaf.client.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.function.BooleanSupplier;

/**
 * In-app modal in the same look as HomeView's "New project" card (scrim,
 * white card, icon tile, pill fields, green/red actions). Used instead of
 * stock JavaFX dialogs so every prompt matches the theme.
 */
public final class UiModal {

    public enum Tone { NEUTRAL, DANGER }

    private final StackPane host;
    private final StackPane scrim;
    private final VBox card;
    private final VBox body = new VBox(12);
    private final Label error = new Label();
    private Node firstFocus;
    private Runnable onClose = () -> { };

    private UiModal(StackPane host, String iconPath, Tone tone, String title, String subtitle) {
        this.host = host;

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("modal-title");
        VBox copy = new VBox(2, titleLabel);
        if (subtitle != null && !subtitle.isBlank()) {
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("modal-sub");
            sub.setWrapText(true);
            copy.getChildren().add(sub);
        }
        HBox.setHgrow(copy, Priority.ALWAYS);

        Color iconColor = tone == Tone.DANGER ? Color.web("#B4453A") : Color.web("#23664A");
        StackPane tile = new StackPane(UiSupport.lineIcon(iconPath, 16, iconColor));
        tile.getStyleClass().addAll("modal-icon-tile", tone == Tone.DANGER ? "modal-icon-tile-danger" : "modal-icon-tile-ok");
        tile.setMinSize(38, 38);
        tile.setPrefSize(38, 38);
        tile.setMaxSize(38, 38);

        Button close = new Button();
        close.setGraphic(UiSupport.iconBox(UiSupport.CLOSE, 13, Color.web("#8B8B85"), 16));
        close.getStyleClass().add("icon-button");
        close.setTooltip(UiSupport.tooltip("Close"));
        close.setOnAction(e -> close());

        HBox head = new HBox(12, tile, copy, close);
        head.setAlignment(Pos.CENTER_LEFT);

        error.getStyleClass().add("modal-error");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        card = new VBox(16, head, body);
        card.getStyleClass().add("modal-card");
        card.setMaxWidth(440);
        card.setPrefWidth(440);
        card.setMaxHeight(Region.USE_PREF_SIZE);

        scrim = new StackPane(card);
        scrim.getStyleClass().add("modal-scrim");
        StackPane.setAlignment(card, Pos.CENTER);
        scrim.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) close();
        });
        scrim.setOnMouseClicked(e -> {
            if (e.getTarget() == scrim) close();
        });
    }

    public static UiModal create(StackPane host, String iconPath, Tone tone, String title, String subtitle) {
        return new UiModal(host, iconPath, tone, title, subtitle);
    }

    /** One-button message, replacing {@code Alert}. */
    public static void notice(StackPane host, Tone tone, String title, String message) {
        UiModal modal = create(host, tone == Tone.DANGER ? UiSupport.ALERT : UiSupport.INFO, tone, title, null);
        modal.text(message);
        modal.actions(null, null, "OK", Tone.NEUTRAL, () -> true);
        modal.show();
    }

    /** Two-button confirmation. */
    public static void confirm(StackPane host, Tone tone, String title, String message, String confirmText, Runnable onConfirm) {
        UiModal modal = create(host, tone == Tone.DANGER ? UiSupport.TRASH : UiSupport.INFO, tone, title, null);
        modal.text(message);
        modal.actions(null, "Cancel", confirmText, tone, () -> {
            onConfirm.run();
            return true;
        });
        modal.show();
    }

    public UiModal text(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("modal-body");
        label.setWrapText(true);
        label.setMaxWidth(400);
        body.getChildren().add(label);
        return this;
    }

    public TextField field(String label, String value, String prompt) {
        return addField(label, new TextField(value == null ? "" : value), prompt);
    }

    public PasswordField password(String label, String value, String prompt) {
        PasswordField field = new PasswordField();
        field.setText(value == null ? "" : value);
        addField(label, field, prompt);
        return field;
    }

    private <T extends TextField> T addField(String label, T field, String prompt) {
        field.getStyleClass().add("modal-field");
        field.setPromptText(prompt);
        field.textProperty().addListener((obs, oldText, newText) -> clearError(field));
        Label caption = new Label(label.toUpperCase());
        caption.getStyleClass().add("field-label");
        body.getChildren().add(new VBox(6, caption, field));
        if (firstFocus == null) firstFocus = field;
        return field;
    }

    /** Lays out several fields side by side, e.g. host + port. */
    public GridPane row(Node... cells) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        for (int i = 0; i < cells.length; i++) {
            body.getChildren().remove(cells[i]);
            grid.add(cells[i], i, 0);
            GridPane.setHgrow(cells[i], i == 0 ? Priority.ALWAYS : Priority.SOMETIMES);
        }
        body.getChildren().add(grid);
        return grid;
    }

    /** The labelled box wrapping a field, for {@link #row(Node...)}. */
    public static Node boxOf(TextField field) {
        return field.getParent();
    }

    public CheckBox check(String text, boolean selected) {
        CheckBox box = new CheckBox(text);
        box.getStyleClass().add("q-check");
        box.setSelected(selected);
        body.getChildren().add(box);
        return box;
    }

    public UiModal path(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("modal-path");
        label.setMaxWidth(Double.MAX_VALUE);
        body.getChildren().add(label);
        return this;
    }

    public UiModal node(Node node) {
        body.getChildren().add(node);
        return this;
    }

    /**
     * Footer: optional hint, optional cancel, primary action. {@code onPrimary}
     * returns {@code true} to close the modal (return {@code false} after
     * calling {@link #error(TextField, String)}).
     */
    public Button actions(String hint, String cancelText, String primaryText, Tone tone, BooleanSupplier onPrimary) {
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        if (hint != null) {
            Label hintLabel = new Label(hint);
            hintLabel.getStyleClass().add("modal-hint");
            hintLabel.setWrapText(true);
            hintLabel.setMinWidth(0);
            hintLabel.setMaxWidth(220);
            footer.getChildren().add(hintLabel);
        }
        footer.getChildren().add(UiSupport.spacer());
        if (cancelText != null) {
            Button cancel = new Button(cancelText);
            cancel.getStyleClass().addAll("btn-secondary", "modal-cancel");
            cancel.setCancelButton(true);
            cancel.setMinWidth(Region.USE_PREF_SIZE);
            cancel.setOnAction(e -> close());
            footer.getChildren().add(cancel);
        }
        Button primary = new Button(primaryText);
        primary.getStyleClass().add(tone == Tone.DANGER ? "btn-danger" : "btn-primary");
        primary.setDefaultButton(true);
        primary.setMinWidth(Region.USE_PREF_SIZE);
        primary.setOnAction(e -> {
            if (onPrimary.getAsBoolean()) close();
        });
        footer.getChildren().add(primary);
        card.getChildren().addAll(error, footer);
        if (firstFocus == null) firstFocus = primary;
        return primary;
    }

    public void error(TextField field, String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
        if (field != null) {
            if (!field.getStyleClass().contains("modal-field-error")) field.getStyleClass().add("modal-field-error");
            field.requestFocus();
        }
    }

    private void clearError(TextField field) {
        field.getStyleClass().remove("modal-field-error");
        error.setVisible(false);
        error.setManaged(false);
    }

    public UiModal onClose(Runnable action) {
        this.onClose = action;
        return this;
    }

    public void show() {
        host.getChildren().add(scrim);
        Platform.runLater(() -> {
            if (firstFocus != null) {
                firstFocus.requestFocus();
                if (firstFocus instanceof TextField field) field.selectAll();
            } else {
                scrim.requestFocus();
            }
        });
    }

    public void close() {
        if (host.getChildren().remove(scrim)) onClose.run();
    }

    public boolean isShowing() {
        return host.getChildren().contains(scrim);
    }
}

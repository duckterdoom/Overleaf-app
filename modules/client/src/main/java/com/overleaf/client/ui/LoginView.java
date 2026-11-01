package com.overleaf.client.ui;

import com.overleaf.client.ui.backend.AuthGateway;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Split sign-in screen: atmospheric brand panel on the left, minimal form on the right. Talks to
 * {@link AuthGateway} so persistence can be wired later.
 */
public class LoginView extends GridPane {

    private final AuthGateway auth;
    private final Consumer<AuthGateway.Session> onSignedIn;

    private final VBox nameGroup;
    private final TextField nameField = new TextField();
    private final TextField emailField = new TextField();
    private final PasswordField passwordField = new PasswordField();

    /** Shows the same text as {@link #passwordField} while the eye is toggled on. */
    private final TextField passwordShown = new TextField();

    private final StackPane passwordBox = new StackPane();
    private final CheckBox rememberBox = new CheckBox("Keep me signed in");
    private final Label errorLabel = new Label();
    private final Button primaryButton = new Button("Sign in");
    private final Button offlineButton = new Button("Continue offline");
    private final Button toggleModeButton = new Button("Create an account");

    private boolean registerMode = false;
    private boolean busy = false;

    public LoginView(AuthGateway auth, String lastEmail, Consumer<AuthGateway.Session> onSignedIn) {
        this.auth = auth;
        this.onSignedIn = onSignedIn;

        getStyleClass().add("login-root");
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        ColumnConstraints heroColumn = new ColumnConstraints();
        heroColumn.setPercentWidth(50);
        heroColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints formColumn = new ColumnConstraints();
        formColumn.setPercentWidth(50);
        formColumn.setHgrow(Priority.ALWAYS);
        getColumnConstraints().addAll(heroColumn, formColumn);

        RowConstraints fillRow = new RowConstraints();
        fillRow.setVgrow(Priority.ALWAYS);
        fillRow.setFillHeight(true);
        getRowConstraints().add(fillRow);

        nameGroup = pillField("Display name", nameField, "Display name");

        add(createHero(), 0, 0);
        add(createFormSide(), 1, 0);
        setRegisterMode(false);

        boolean known = lastEmail != null && !lastEmail.isBlank();
        emailField.setText(known ? lastEmail : "");
        rememberBox.setSelected(true);
        // New machine: most people need an account first.
        setRegisterMode(!known);

        Platform.runLater(() -> (known ? passwordField : nameField).requestFocus());
    }

    private StackPane createHero() {
        StackPane hero = new StackPane();
        hero.getStyleClass().add("login-hero");
        hero.setMinWidth(420);

        Node watermark = UiSupport.brandMark(380);
        watermark.getStyleClass().add("hero-watermark");
        StackPane watermarkHolder = new StackPane(watermark);
        watermarkHolder.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(watermarkHolder, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(watermarkHolder, new Insets(0, -70, -110, 0));
        watermarkHolder.setMouseTransparent(true);

        Label eyebrow = new Label("OVERLEAF DESKTOP");
        eyebrow.getStyleClass().add("hero-eyebrow");

        VBox headline =
                new VBox(
                        2,
                        headlineLine("Write LaTeX."),
                        headlineLine("Compile locally."),
                        headlineLine("Collaborate live."));

        Label copy =
                new Label(
                        "A focused desktop workspace for papers and research — "
                                + "a full LaTeX toolchain on your machine, shared rooms when you need them.");
        copy.getStyleClass().add("hero-copy");
        copy.setWrapText(true);
        copy.setMaxWidth(400);

        offlineButton.getStyleClass().add("hero-cta");
        offlineButton.setOnAction(e -> continueOffline());
        HBox ctaRow = new HBox(offlineButton);
        ctaRow.setAlignment(Pos.CENTER_LEFT);

        VBox copyBox = new VBox(eyebrow, headline, copy, ctaRow);
        copyBox.getStyleClass().add("hero-copy-box");
        copyBox.setFillWidth(false);
        copyBox.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setMargin(headline, new Insets(14, 0, 16, 0));
        VBox.setMargin(copy, new Insets(0, 0, 26, 0));

        Label meta = new Label("LOCAL-FIRST   ·   pdfLaTeX   ·   CRDT SYNC");
        meta.getStyleClass().add("hero-meta");
        StackPane.setAlignment(meta, Pos.BOTTOM_LEFT);
        StackPane.setMargin(meta, new Insets(0, 0, 30, 64));

        StackPane.setAlignment(copyBox, Pos.CENTER_LEFT);
        StackPane.setMargin(copyBox, new Insets(0, 64, 0, 64));

        hero.getChildren().addAll(watermarkHolder, copyBox, meta);
        return hero;
    }

    private Label headlineLine(String text) {
        Label line = new Label(text);
        line.getStyleClass().add("hero-headline");
        return line;
    }

    private StackPane createFormSide() {
        StackPane side = new StackPane();
        side.getStyleClass().add("login-form-side");
        side.setMinWidth(420);

        HBox brand = UiSupport.brandLockup(26, "Overleaf");
        brand.getStyleClass().add("login-brand");
        Label badge = new Label("DESKTOP");
        badge.getStyleClass().add("brand-badge");
        HBox brandRow = new HBox(7, brand, badge);
        brandRow.setAlignment(Pos.CENTER);
        VBox.setMargin(brandRow, new Insets(0, 0, 42, 0));

        nameField.getStyleClass().add("q-pill-field");
        emailField.getStyleClass().add("q-pill-field");
        emailField.setPromptText("Email address");
        passwordField.getStyleClass().add("q-pill-field");
        passwordField.setPromptText("Password");
        buildPasswordBox();

        rememberBox.getStyleClass().add("q-check");

        errorLabel.getStyleClass().add("form-error");
        errorLabel.setWrapText(true);
        hideError();

        primaryButton.getStyleClass().add("btn-pill-primary");
        primaryButton.setMaxWidth(Double.MAX_VALUE);
        primaryButton.setOnAction(e -> submit());

        emailField.setOnAction(e -> submit());
        passwordField.setOnAction(e -> submit());
        passwordShown.setOnAction(e -> submit());
        nameField.setOnAction(e -> submit());

        Label consent =
                new Label(
                        "Your drafts and compiles stay on this machine. "
                                + "Only collaborative rooms sync through your server.");
        consent.getStyleClass().add("login-consent");
        consent.setWrapText(true);
        consent.setMaxWidth(380);
        VBox.setMargin(consent, new Insets(14, 0, 0, 14));

        toggleModeButton.getStyleClass().add("btn-link");
        toggleModeButton.setOnAction(e -> setRegisterMode(!registerMode));
        HBox optionsRow = new HBox(10, rememberBox, UiSupport.spacer(), toggleModeButton);
        optionsRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(optionsRow, new Insets(14, 0, 14, 0));

        Label hint = new Label("Accounts are stored on this machine with a salted password hash.");
        hint.getStyleClass().add("login-foot");
        hint.setWrapText(true);
        VBox.setMargin(hint, new Insets(16, 0, 0, 0));

        VBox form =
                new VBox(
                        brandRow,
                        nameGroup,
                        emailField,
                        passwordBox,
                        errorLabel,
                        consent,
                        optionsRow,
                        primaryButton,
                        hint);
        form.getStyleClass().add("login-form");
        form.setMaxWidth(380);
        form.setMinWidth(380);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        form.setFillWidth(true);
        VBox.setMargin(emailField, new Insets(0, 0, 12, 0));
        VBox.setMargin(passwordBox, new Insets(0, 0, 4, 0));

        Label version = new Label("Overleaf Desktop · v1.0");
        version.getStyleClass().add("login-foot");
        StackPane.setAlignment(version, Pos.BOTTOM_CENTER);
        StackPane.setMargin(version, new Insets(0, 0, 24, 0));

        side.getChildren().addAll(form, version);
        return side;
    }

    private VBox pillField(String labelText, TextField field, String prompt) {
        field.getStyleClass().add("q-pill-field");
        field.setMaxWidth(Double.MAX_VALUE);
        if (prompt != null) {
            field.setPromptText(prompt);
        }
        field.setAccessibleText(labelText);
        VBox box = new VBox(field);
        VBox.setMargin(box, new Insets(0, 0, 12, 0));
        return box;
    }

    private void continueOffline() {
        if (busy) {
            return;
        }
        AuthGateway.Session session = auth.continueOffline(preferredName());
        onSignedIn.accept(session);
    }

    private void setRegisterMode(boolean enabled) {
        registerMode = enabled;
        nameGroup.setVisible(enabled);
        nameGroup.setManaged(enabled);
        primaryButton.setText(enabled ? "Create account" : "Sign in");
        toggleModeButton.setText(enabled ? "I already have an account" : "Create an account");
        hideError();
        if (enabled) {
            Platform.runLater(nameField::requestFocus);
        }
    }

    private void submit() {
        if (busy) {
            return;
        }
        hideError();

        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();
        String name = nameField.getText() == null ? "" : nameField.getText().trim();

        if (email.isEmpty() || !email.contains("@") || !email.contains(".")) {
            showError("Enter a valid email address.");
            return;
        }
        if (password.length() < 6) {
            showError("Password must be at least 6 characters.");
            return;
        }
        if (registerMode && name.isEmpty()) {
            showError("Tell us what to call you.");
            return;
        }

        setBusy(true);
        var future =
                registerMode
                        ? auth.register(name, email, password, rememberBox.isSelected())
                        : auth.signIn(email, password, rememberBox.isSelected());

        future.whenComplete((session, error) ->
                Platform.runLater(() -> {
                    setBusy(false);
                    if (error != null) {
                        showError(messageOf(error));
                        return;
                    }
                    onSignedIn.accept(session);
                }));
    }

    private static String messageOf(Throwable error) {
        Throwable cause =
                error instanceof CompletionException && error.getCause() != null
                        ? error.getCause()
                        : error;
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? "Could not reach the authentication service. Try again or continue offline."
                : message;
    }

    /** Password field with an eye button on its right edge that reveals the text. */
    private void buildPasswordBox() {
        passwordShown.getStyleClass().addAll("q-pill-field", "q-pill-field-eye");
        passwordShown.setPromptText("Password");
        passwordShown.textProperty().bindBidirectional(passwordField.textProperty());
        passwordField.getStyleClass().add("q-pill-field-eye");

        Button eye = new Button();
        eye.getStyleClass().add("eye-button");
        eye.setFocusTraversable(false);
        eye.setCursor(javafx.scene.Cursor.HAND);
        eye.setOnAction(e -> setPasswordVisible(!passwordShown.isVisible()));
        eye.setUserData("eye");
        StackPane.setAlignment(eye, Pos.CENTER_RIGHT);
        StackPane.setMargin(eye, new Insets(0, 10, 0, 0));

        passwordBox.getChildren().addAll(passwordField, passwordShown, eye);
        setPasswordVisible(false);
    }

    private void setPasswordVisible(boolean visible) {
        passwordShown.setVisible(visible);
        passwordShown.setManaged(visible);
        passwordField.setVisible(!visible);
        passwordField.setManaged(!visible);
        Button eye = (Button) passwordBox.getChildren().get(2);
        eye.setGraphic(
                UiSupport.iconBox(
                        visible ? UiSupport.EYE_OFF : UiSupport.EYE,
                        15,
                        javafx.scene.paint.Color.web("#8B8B85"),
                        20));
        eye.setTooltip(UiSupport.tooltip(visible ? "Hide password" : "Show password"));
        TextField active = visible ? passwordShown : passwordField;
        if (passwordField.isFocused() || passwordShown.isFocused()) {
            active.requestFocus();
            active.positionCaret(active.getText() == null ? 0 : active.getText().length());
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        primaryButton.setDisable(value);
        offlineButton.setDisable(value);
        toggleModeButton.setDisable(value);
        nameField.setDisable(value);
        emailField.setDisable(value);
        passwordField.setDisable(value);
        passwordShown.setDisable(value);
        if (value) {
            primaryButton.setText(registerMode ? "Creating account…" : "Signing in…");
        } else {
            primaryButton.setText(registerMode ? "Create account" : "Sign in");
        }
    }

    private String preferredName() {
        String typed = nameField.getText();
        if (registerMode && typed != null && !typed.isBlank()) {
            return typed.trim();
        }
        String email = emailField.getText();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "Local User";
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    public void prepareForDisplay() {
        setBusy(false);
        hideError();
        passwordField.clear();
        setPasswordVisible(false);
        Platform.runLater(
                () -> (emailField.getText().isBlank() ? emailField : passwordField).requestFocus());
    }
}

package com.overleaf.client.ui.backend;

import java.util.concurrent.CompletableFuture;

/**
 * UI seam for authentication. {@link LocalBackend} is the default; a
 * MongoDB-backed implementation can replace it without changing the views.
 */
public interface AuthGateway {

    record Session(String name, String email, boolean offline) {
    }

    CompletableFuture<Session> signIn(String email, String password, boolean remember);

    CompletableFuture<Session> register(String name, String email, String password, boolean remember);

    Session continueOffline(String name);

    /** Changes the display name; throws {@link IllegalStateException} with a user-facing message. */
    Session updateName(Session session, String newName);

    /** Moves the account to a new email after checking the password; throws {@link IllegalStateException} with a user-facing message. */
    Session changeEmail(Session session, String newEmail, String currentPassword);

    /** Replaces the password after checking the current one; throws {@link IllegalStateException} with a user-facing message. */
    void changePassword(Session session, String currentPassword, String newPassword);
}

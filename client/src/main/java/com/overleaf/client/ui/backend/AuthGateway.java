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
}

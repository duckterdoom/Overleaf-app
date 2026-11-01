package com.overleaf.client.ui.backend;

import java.nio.file.Path;
import java.util.Optional;

/**
 * UI seam for the profile picture shown in place of the initials avatar.
 */
public interface ProfileGateway {

    /** Largest picture accepted, in bytes. */
    long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    Optional<Path> avatar(AuthGateway.Session session);

    /** Copies {@code image} as the account's picture; throws {@link IllegalStateException} with a user-facing message. */
    void setAvatar(AuthGateway.Session session, Path image);

    void removeAvatar(AuthGateway.Session session);
}

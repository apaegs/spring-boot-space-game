package org.example.springbootspacegame.auth;

import java.util.Collections;
import java.util.UUID;

/**
 * Principal stored in Spring's SecurityContext after successful authentication.
 * Carries the user's UUID so downstream code (controllers, services) can look up the User
 * without an extra DB hit just to translate username → id.
 */
public class AuthenticatedUser extends org.springframework.security.core.userdetails.User {

    private final UUID userId;

    public AuthenticatedUser(User user) {
        super(user.getUsername(), user.getPasswordHash(), Collections.emptyList());
        this.userId = user.getId();
    }

    public UUID getUserId() {
        return userId;
    }
}

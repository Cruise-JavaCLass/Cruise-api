package com.cruise.common.security;

import com.cruise.common.enums.Role;
import java.security.Principal;

/** Identity from verified auth-service state, never from caller supplied headers. */
public record VerifiedCaller(Long userId, String username, Role role) implements Principal {
    public static final String ATTRIBUTE = VerifiedCaller.class.getName();

    public VerifiedCaller {
        if (userId == null || userId <= 0 || username == null || username.isBlank() || role == null) {
            throw new IllegalArgumentException("A verified caller requires user ID, username and role");
        }
    }

    @Override
    public String getName() { return username; }
}

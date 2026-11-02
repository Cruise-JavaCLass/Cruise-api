package com.cruise.common.security;

import java.util.Optional;

@FunctionalInterface
public interface AccessTokenVerifier {
    Optional<VerifiedCaller> verify(String token);

    /** An auth outage must never turn into an allow decision. */
    class Unavailable extends RuntimeException {
        public Unavailable(Throwable cause) { super("Identity service unavailable", cause); }
    }
}

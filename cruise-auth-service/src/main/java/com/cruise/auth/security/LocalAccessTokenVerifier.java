package com.cruise.auth.security;

import com.cruise.auth.repository.UserRepository;
import com.cruise.auth.service.JwtService;
import com.cruise.common.security.AccessTokenVerifier;
import com.cruise.common.security.VerifiedCaller;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LocalAccessTokenVerifier implements AccessTokenVerifier {
    private final JwtService jwt;
    private final UserRepository users;
    private final String issuer;

    public LocalAccessTokenVerifier(JwtService jwt, UserRepository users,
            @Value("${jwt.issuer:cruise-auth-service}") String issuer) {
        this.jwt = jwt;
        this.users = users;
        this.issuer = issuer;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerifiedCaller> verify(String token) {
        var verification = jwt.introspectToken(token);
        if (!verification.isValid() || !JwtService.TOKEN_TYPE_ACCESS.equals(verification.getTokenType())
                || verification.getExpiresAt() == null || verification.getExpiresAt() <= Instant.now().getEpochSecond()
                || !issuer.equals(jwt.extractClaim(token, "iss")) || verification.getUsername() == null) {
            return Optional.empty();
        }
        return users.findByUsername(verification.getUsername())
                .filter(user -> Boolean.TRUE.equals(user.getActive()) && user.getRole() != null
                        && user.getId().equals(verification.getUserId())
                        && user.getRole().name().equals(verification.getRole()))
                .map(user -> new VerifiedCaller(user.getId(), user.getUsername(), user.getRole()));
    }
}

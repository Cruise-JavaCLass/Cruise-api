package com.cruise.auth.service;

import com.cruise.auth.dto.TokenPair;
import com.cruise.auth.dto.VerifyTokenResponse;
import com.cruise.auth.entity.User;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("my-super-secret-jwt-test-key-2026-auth", 3600000L, 86400000L, "cruise-auth-test");

        testUser = User.builder()
                .username("minh.nguyen")
                .fullName("Nguyễn Văn Minh")
                .email("minh.nguyen@cruise.com")
                .role(Role.SYSTEM_ADMIN)
                .cabinNumber("Suite 101")
                .rfidTag("RFID-778899")
                .active(true)
                .build();
        testUser.setId(42L);
    }

    @Test
    @DisplayName("Should generate valid Access Token with all user claims")
    void shouldGenerateAccessTokenWithClaims() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(token).isNotNull().contains(".");
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.validateAccessToken(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("minh.nguyen");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractRole(token)).isEqualTo("SYSTEM_ADMIN");
        assertThat(jwtService.extractTokenType(token)).isEqualTo("ACCESS");
        assertThat(jwtService.extractJti(token)).isNotEmpty();
        assertThat(jwtService.extractExp(token)).isGreaterThan(jwtService.extractIat(token));
    }

    @Test
    @DisplayName("Should generate valid Refresh Token with REFRESH token type")
    void shouldGenerateRefreshTokenWithClaims() {
        String refreshToken = jwtService.generateRefreshToken(testUser);

        assertThat(refreshToken).isNotNull();
        assertThat(jwtService.validateToken(refreshToken)).isTrue();
        assertThat(jwtService.validateRefreshToken(refreshToken)).isTrue();
        assertThat(jwtService.extractTokenType(refreshToken)).isEqualTo("REFRESH");
        assertThat(jwtService.extractUsername(refreshToken)).isEqualTo("minh.nguyen");
        assertThat(jwtService.extractUserId(refreshToken)).isEqualTo(42L);
    }

    @Test
    @DisplayName("Should generate TokenPair containing both Access Token and Refresh Token")
    void shouldGenerateTokenPairSuccessfully() {
        TokenPair pair = jwtService.generateTokenPair(testUser);

        assertThat(pair).isNotNull();
        assertThat(pair.getAccessToken()).isNotEmpty();
        assertThat(pair.getRefreshToken()).isNotEmpty();
        assertThat(pair.getTokenType()).isEqualTo("Bearer");
        assertThat(pair.getExpiresIn()).isPositive();
        assertThat(pair.getRefreshExpiresIn()).isPositive();

        assertThat(jwtService.validateAccessToken(pair.getAccessToken())).isTrue();
        assertThat(jwtService.validateRefreshToken(pair.getRefreshToken())).isTrue();
    }

    @Test
    @DisplayName("Should introspect valid token and return complete details")
    void shouldIntrospectValidToken() {
        String token = jwtService.generateAccessToken(testUser);
        VerifyTokenResponse introspection = jwtService.introspectToken(token);

        assertThat(introspection.isValid()).isTrue();
        assertThat(introspection.getStatus()).isEqualTo("VALID");
        assertThat(introspection.getUsername()).isEqualTo("minh.nguyen");
        assertThat(introspection.getUserId()).isEqualTo(42L);
        assertThat(introspection.getEmail()).isEqualTo("minh.nguyen@cruise.com");
        assertThat(introspection.getFullName()).isEqualTo("Nguyễn Văn Minh");
        assertThat(introspection.getRole()).isEqualTo("SYSTEM_ADMIN");
        assertThat(introspection.getCabinNumber()).isEqualTo("Suite 101");
        assertThat(introspection.getRfidTag()).isEqualTo("RFID-778899");
        assertThat(introspection.getTokenType()).isEqualTo("ACCESS");
        assertThat(introspection.getRemainingSeconds()).isPositive();
        assertThat(introspection.getClaims()).isNotEmpty();
    }

    @Test
    @DisplayName("Should reject malformed token string")
    void shouldRejectMalformedToken() {
        VerifyTokenResponse res1 = jwtService.introspectToken("invalid.token");
        assertThat(res1.isValid()).isFalse();
        assertThat(res1.getStatus()).isEqualTo("MALFORMED");

        VerifyTokenResponse res2 = jwtService.introspectToken(null);
        assertThat(res2.isValid()).isFalse();
        assertThat(res2.getStatus()).isEqualTo("MISSING");
    }

    @Test
    @DisplayName("Should reject token with tampered cryptographic signature")
    void shouldRejectTamperedSignature() {
        String token = jwtService.generateAccessToken(testUser);
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".tamperedSignatureValue";

        VerifyTokenResponse res = jwtService.introspectToken(tamperedToken);
        assertThat(res.isValid()).isFalse();
        assertThat(res.getStatus()).isEqualTo("INVALID_SIGNATURE");
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        // Tạo service với thời hạn âm (đã hết hạn ngay khi sinh)
        JwtService expiredService = new JwtService("my-super-secret-jwt-test-key-2026-auth", -1000L, -1000L, "cruise-auth-test");
        String expiredToken = expiredService.generateAccessToken(testUser);

        VerifyTokenResponse res = expiredService.introspectToken(expiredToken);
        assertThat(res.isValid()).isFalse();
        assertThat(res.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("Should invalidate token and treat it as REVOKED")
    void shouldHandleTokenInvalidation() {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.validateToken(token)).isTrue();

        jwtService.invalidateToken(token);

        assertThat(jwtService.isTokenInvalidated(token)).isTrue();
        VerifyTokenResponse res = jwtService.introspectToken(token);
        assertThat(res.isValid()).isFalse();
        assertThat(res.getStatus()).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("Should invalidate all existing tokens for a user after password reset or revocation")
    void shouldHandleUserLevelRevocation() throws InterruptedException {
        String token = jwtService.generateAccessToken(testUser);
        assertThat(jwtService.validateToken(token)).isTrue();

        // Đợi 10ms để đảm bảo mốc thời gian cutoff sau iat
        Thread.sleep(10);
        jwtService.revokeAllUserTokens("minh.nguyen");

        VerifyTokenResponse res = jwtService.introspectToken(token);
        assertThat(res.isValid()).isFalse();
        assertThat(res.getStatus()).isEqualTo("REVOKED");
    }

    @Test
    @DisplayName("Should extract arbitrary custom claim accurately")
    void shouldExtractCustomClaims() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(jwtService.extractClaim(token, "sub")).isEqualTo("minh.nguyen");
        assertThat(jwtService.extractClaim(token, "role")).isEqualTo("SYSTEM_ADMIN");
        assertThat(jwtService.extractClaim(token, "nonExistingClaim")).isNull();

        Map<String, Object> allClaims = jwtService.extractAllClaims(token);
        assertThat(allClaims).isNotNull().containsKey("iss");
    }

    @Test
    @DisplayName("Should support constructors and convenience methods")
    void shouldSupportConstructorsAndConvenienceMethods() {
        JwtService defaultService = new JwtService();
        assertThat(defaultService.getExpirationSeconds()).isPositive();
        assertThat(defaultService.getRefreshExpirationSeconds()).isPositive();

        JwtService twoArgService = new JwtService("custom-secret-key-12345678901234567890", 5000L);
        assertThat(twoArgService.getExpirationSeconds()).isEqualTo(5L);

        String token = jwtService.generateToken(testUser);
        assertThat(token).isNotEmpty();
    }

    @Test
    @DisplayName("Should generate token for user with minimal/null fields")
    void shouldGenerateTokenForUserWithNullFields() {
        User minimalUser = User.builder().username("bare.user").build();
        String token = jwtService.generateAccessToken(minimalUser);
        assertThat(token).isNotEmpty();

        VerifyTokenResponse res = jwtService.introspectToken("Bearer " + token);
        assertThat(res.isValid()).isTrue();
        assertThat(res.getUsername()).isEqualTo("bare.user");
        assertThat(res.getEmail()).isNull();
        assertThat(res.getRole()).isNull();
    }

    @Test
    @DisplayName("Should handle edge cases for token validation and extraction")
    void shouldHandleEdgeCases() {
        assertThat(jwtService.isTokenInvalidated(null)).isFalse();

        jwtService.revokeAllUserTokens(null);
        jwtService.revokeAllUserTokens("");

        assertThat(jwtService.extractUserId("invalid.token.here")).isNull();
        assertThat(jwtService.extractExp("invalid.token.here")).isZero();
        assertThat(jwtService.extractIat("invalid.token.here")).isZero();
        assertThat(jwtService.extractClaim("invalid", "claim")).isNull();
        assertThat(jwtService.extractAllClaims("invalid")).isNull();

        jwtService.invalidateToken(null);
        jwtService.invalidateToken("");

        String refreshToken = jwtService.generateRefreshToken(testUser);
        assertThat(jwtService.validateAccessToken(refreshToken)).isFalse();
    }
}

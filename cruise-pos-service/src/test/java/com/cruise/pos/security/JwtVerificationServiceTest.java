package com.cruise.pos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JWT verification")
class JwtVerificationServiceTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-1234567890";
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtVerificationService service = new JwtVerificationService(SECRET, objectMapper);

    @Test
    @DisplayName("Token hợp lệ trả đúng danh tính và role")
    void validTokenIsAccepted() throws Exception {
        String token = buildToken(Map.of(
                "sub", "huytran",
                "role", "FINANCE_FRONT_DESK",
                "type", "ACCESS",
                "exp", Instant.now().plusSeconds(600).getEpochSecond(),
                "cabinNumber", "CABIN-7012"), SECRET);

        Optional<AuthPrincipal> principal = service.verify(token);

        assertThat(principal).isPresent();
        assertThat(principal.get().username()).isEqualTo("huytran");
        assertThat(principal.get().role()).isEqualTo("FINANCE_FRONT_DESK");
        assertThat(principal.get().cabinNumber()).isEqualTo("CABIN-7012");
    }

    @Test
    @DisplayName("Token hết hạn bị từ chối")
    void expiredTokenIsRejected() throws Exception {
        String token = buildToken(Map.of(
                "sub", "huytran",
                "role", "PASSENGER",
                "type", "ACCESS",
                "exp", Instant.now().minusSeconds(60).getEpochSecond()), SECRET);

        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("Sai secret bị từ chối")
    void wrongSecretIsRejected() throws Exception {
        String token = buildToken(Map.of(
                "sub", "huytran",
                "role", "PASSENGER",
                "type", "ACCESS",
                "exp", Instant.now().plusSeconds(600).getEpochSecond()), "another-secret-key-000000000000");

        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("Refresh token không dùng được cho API")
    void refreshTokenIsRejected() throws Exception {
        String token = buildToken(Map.of(
                "sub", "huytran",
                "role", "PASSENGER",
                "type", "REFRESH",
                "exp", Instant.now().plusSeconds(600).getEpochSecond()), SECRET);

        assertThat(service.verify(token)).isEmpty();
    }

    @Test
    @DisplayName("Token rác hoặc thiếu cấu hình secret bị từ chối")
    void malformedOrUnconfigured() {
        assertThat(service.verify("not-a-jwt")).isEmpty();
        assertThat(service.verify(null)).isEmpty();

        JwtVerificationService unconfigured = new JwtVerificationService("", objectMapper);
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.verify("a.b.c")).isEmpty();
    }

    private String buildToken(Map<String, Object> claims, String secret) throws Exception {
        String header = base64Url(HEADER.getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(objectMapper.writeValueAsBytes(new LinkedHashMap<>(claims)));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = base64Url(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));

        return header + "." + payload + "." + signature;
    }

    private String base64Url(byte[] payload) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    }
}

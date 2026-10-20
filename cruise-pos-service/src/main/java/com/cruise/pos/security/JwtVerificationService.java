package com.cruise.pos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Xác minh Access Token HS256 do cruise-auth-service phát hành.
 *
 * <p>Secret bắt buộc lấy từ biến môi trường {@code JWT_SECRET}; không có giá trị
 * mặc định trong mã nguồn (CWE-798). Nếu chưa cấu hình, mọi token đều bị từ chối
 * và filter sẽ trả 401.</p>
 */
@Service
@Slf4j
public class JwtVerificationService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";

    private final byte[] secret;
    private final ObjectMapper objectMapper;

    public JwtVerificationService(@Value("${jwt.secret:}") String secret, ObjectMapper objectMapper) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
        if (this.secret.length == 0) {
            log.warn("jwt.secret chưa được cấu hình (JWT_SECRET). Mọi request có token sẽ bị 401.");
        }
    }

    public boolean isConfigured() {
        return secret.length > 0;
    }

    @SuppressWarnings("unchecked")
    public Optional<AuthPrincipal> verify(String token) {
        if (token == null || token.isBlank() || secret.length == 0) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }

        try {
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapper.readValue(payloadJson, Map.class);

            if (!TOKEN_TYPE_ACCESS.equals(claims.get("type"))) {
                return Optional.empty();
            }

            Object exp = claims.get("exp");
            if (exp instanceof Number expNumber && Instant.now().getEpochSecond() >= expNumber.longValue()) {
                return Optional.empty();
            }

            String username = asString(claims.get("sub"));
            String role = asString(claims.get("role"));
            if (username == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthPrincipal(username, role, asString(claims.get("cabinNumber"))));
        } catch (Exception ex) {
            log.debug("Token không hợp lệ: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private byte[] sign(String signingInput) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(secret, HMAC_SHA256));
        return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}

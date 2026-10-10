package com.cruise.auth.service;

import com.cruise.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service xử lý tạo, xác thực và quản lý thu hồi JSON Web Token (JWT)
 */
@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String HEADER_BASE64 = Base64.getUrlEncoder().withoutPadding().encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));

    private final String secretKey;
    private final long expirationMillis;

    // Danh sách các token đã đăng xuất (Token Blacklist / Invalidation cache)
    private final Map<String, Long> invalidatedTokens = new ConcurrentHashMap<>();

    public JwtService(
            @Value("${jwt.secret:cruise-secret-key-super-secure-token-2026-auth-jwt-system}") String secretKey,
            @Value("${jwt.expiration-millis:86400000}") long expirationMillis) {
        this.secretKey = secretKey;
        this.expirationMillis = expirationMillis;
    }

    /**
     * Tạo access token chứa claims của người dùng
     */
    public String generateToken(User user) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long expSeconds = nowSeconds + (expirationMillis / 1000);

        String payloadJson = buildPayload(user, nowSeconds, expSeconds);
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String dataToSign = HEADER_BASE64 + "." + payloadBase64;
        String signatureBase64 = sign(dataToSign, secretKey);

        return dataToSign + "." + signatureBase64;
    }

    /**
     * Kiểm tra tính hợp lệ của token
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        String cleanToken = cleanToken(token);
        if (isTokenInvalidated(cleanToken)) {
            return false;
        }

        String[] parts = cleanToken.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        String dataToSign = parts[0] + "." + parts[1];
        String expectedSignature = sign(dataToSign, secretKey);
        if (!expectedSignature.equals(parts[2])) {
            return false;
        }

        long exp = extractExp(cleanToken);
        long nowSeconds = System.currentTimeMillis() / 1000;
        return exp > nowSeconds;
    }

    /**
     * Trích xuất username từ payload token
     */
    public String extractUsername(String token) {
        return extractClaim(cleanToken(token), "sub");
    }

    /**
     * Trích xuất role từ payload token
     */
    public String extractRole(String token) {
        return extractClaim(cleanToken(token), "role");
    }

    /**
     * Trích xuất thời điểm hết hạn (seconds)
     */
    public long extractExp(String token) {
        String expStr = extractClaim(cleanToken(token), "exp");
        try {
            return expStr != null ? Long.parseLong(expStr) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Đưa token vào danh sách vô hiệu hóa khi đăng xuất
     */
    public void invalidateToken(String token) {
        if (token != null && !token.isBlank()) {
            String clean = cleanToken(token);
            long exp = extractExp(clean);
            long expiryTime = exp > 0 ? exp * 1000 : System.currentTimeMillis() + expirationMillis;
            invalidatedTokens.put(clean, expiryTime);
            cleanupExpiredTokens();
        }
    }

    /**
     * Kiểm tra xem token đã bị đăng xuất trước đó hay chưa
     */
    public boolean isTokenInvalidated(String token) {
        if (token == null) return false;
        return invalidatedTokens.containsKey(cleanToken(token));
    }

    public long getExpirationSeconds() {
        return expirationMillis / 1000;
    }

    private String cleanToken(String token) {
        if (token.startsWith("Bearer ")) {
            return token.substring(7).trim();
        }
        return token.trim();
    }

    private void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        invalidatedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private String buildPayload(User user, long nowSeconds, long expSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"sub\":\"").append(escape(user.getUsername())).append("\",");
        sb.append("\"userId\":").append(user.getId() != null ? user.getId() : 0).append(",");
        sb.append("\"email\":\"").append(escape(user.getEmail())).append("\",");
        sb.append("\"fullName\":\"").append(escape(user.getFullName())).append("\",");
        sb.append("\"role\":\"").append(user.getRole() != null ? user.getRole().name() : "").append("\",");
        if (user.getCabinNumber() != null) {
            sb.append("\"cabinNumber\":\"").append(escape(user.getCabinNumber())).append("\",");
        }
        if (user.getRfidTag() != null) {
            sb.append("\"rfidTag\":\"").append(escape(user.getRfidTag())).append("\",");
        }
        sb.append("\"iat\":").append(nowSeconds).append(",");
        sb.append("\"exp\":").append(expSeconds);
        sb.append("}");
        return sb.toString();
    }

    private String extractClaim(String token, String claimKey) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(decoded, StandardCharsets.UTF_8);

            // Tìm dạng "key":"value" hoặc "key":value
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(claimKey) + "\"\\s*:\\s*\"?([^,\"}]+)\"?");
            Matcher matcher = pattern.matcher(payload);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private String sign(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    private String escape(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

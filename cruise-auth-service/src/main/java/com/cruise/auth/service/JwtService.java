package com.cruise.auth.service;

import com.cruise.auth.dto.TokenPair;
import com.cruise.auth.dto.VerifyTokenResponse;
import com.cruise.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service xử lý tạo, xác thực, làm mới và quản lý thu hồi JSON Web Token (JWT).
 * Hỗ trợ Access Token ngắn hạn, Refresh Token dài hạn, Token Introspection (RFC 7662)
 * và danh sách vô hiệu hóa (Blacklist / Revocation cache) sử dụng thuần Java SE.
 */
@Service
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String HEADER_BASE64 = Base64.getUrlEncoder().withoutPadding().encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));

    private final String secretKey;
    private final long accessExpirationMillis;
    private final long refreshExpirationMillis;
    private final String issuer;

    // Danh sách các token đã bị vô hiệu hóa (Token Blacklist: token -> expMillis)
    private final Map<String, Long> invalidatedTokens = new ConcurrentHashMap<>();

    // Mốc thời gian vô hiệu hóa theo user (username -> timestampMillis): các token sinh trước mốc này bị coi là REVOKED
    private final Map<String, Long> userRevocationTimestamps = new ConcurrentHashMap<>();

    public JwtService() {
        this("cruise-secret-key-super-secure-token-2026-auth-jwt-system", 86400000L, 604800000L, "cruise-auth-service");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JwtService(
            @Value("${jwt.secret:cruise-secret-key-super-secure-token-2026-auth-jwt-system}") String secretKey,
            @Value("${jwt.access-token.expiration-millis:${jwt.expiration-millis:86400000}}") long accessExpirationMillis,
            @Value("${jwt.refresh-token.expiration-millis:604800000}") long refreshExpirationMillis,
            @Value("${jwt.issuer:cruise-auth-service}") String issuer) {
        this.secretKey = secretKey;
        this.accessExpirationMillis = accessExpirationMillis;
        this.refreshExpirationMillis = refreshExpirationMillis;
        this.issuer = issuer;
    }

    /**
     * Constructor tương thích ngược với cấu hình 2 tham số
     */
    public JwtService(String secretKey, long expirationMillis) {
        this(secretKey, expirationMillis, 604800000L, "cruise-auth-service");
    }

    /**
     * Cấp phát Access Token chứa claims của người dùng
     */
    public String generateToken(User user) {
        return generateAccessToken(user);
    }

    /**
     * Cấp phát Access Token ngắn hạn (mặc định 24h hoặc theo cấu hình)
     */
    public String generateAccessToken(User user) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long expSeconds = nowSeconds + (accessExpirationMillis / 1000);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", user.getUsername());
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("type", TOKEN_TYPE_ACCESS);
        if (user.getId() != null) claims.put("userId", user.getId());
        if (user.getEmail() != null) claims.put("email", user.getEmail());
        if (user.getFullName() != null) claims.put("fullName", user.getFullName());
        if (user.getRole() != null) claims.put("role", user.getRole().name());
        if (user.getCabinNumber() != null) claims.put("cabinNumber", user.getCabinNumber());
        if (user.getRfidTag() != null) claims.put("rfidTag", user.getRfidTag());
        claims.put("iat", nowSeconds);
        claims.put("exp", expSeconds);

        return createSignedToken(claims);
    }

    /**
     * Cấp phát Refresh Token dài hạn (mặc định 7 ngày)
     */
    public String generateRefreshToken(User user) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        long expSeconds = nowSeconds + (refreshExpirationMillis / 1000);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("sub", user.getUsername());
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("type", TOKEN_TYPE_REFRESH);
        if (user.getId() != null) claims.put("userId", user.getId());
        claims.put("iat", nowSeconds);
        claims.put("exp", expSeconds);

        return createSignedToken(claims);
    }

    /**
     * Cấp phát cặp token (Access Token và Refresh Token)
     */
    public TokenPair generateTokenPair(User user) {
        return TokenPair.builder()
                .accessToken(generateAccessToken(user))
                .refreshToken(generateRefreshToken(user))
                .tokenType("Bearer")
                .expiresIn(getExpirationSeconds())
                .refreshExpiresIn(getRefreshExpirationSeconds())
                .build();
    }

    /**
     * Kiểm tra tính hợp lệ cơ bản của token
     */
    public boolean validateToken(String token) {
        VerifyTokenResponse response = introspectToken(token);
        return response.isValid();
    }

    /**
     * Kiểm tra tính hợp lệ của access token
     */
    public boolean validateAccessToken(String token) {
        VerifyTokenResponse response = introspectToken(token);
        return response.isValid() && (response.getTokenType() == null || TOKEN_TYPE_ACCESS.equalsIgnoreCase(response.getTokenType()));
    }

    /**
     * Kiểm tra tính hợp lệ của refresh token
     */
    public boolean validateRefreshToken(String token) {
        VerifyTokenResponse response = introspectToken(token);
        return response.isValid() && TOKEN_TYPE_REFRESH.equalsIgnoreCase(response.getTokenType());
    }

    /**
     * Xác minh và phân tích chi tiết thông tin token (Token Introspection RFC 7662)
     */
    public VerifyTokenResponse introspectToken(String token) {
        if (token == null || token.isBlank()) {
            return VerifyTokenResponse.builder()
                    .valid(false)
                    .status("MISSING")
                    .message("Token is missing or blank")
                    .build();
        }

        String clean = cleanToken(token);
        String[] parts = clean.split("\\.");
        if (parts.length != 3) {
            return VerifyTokenResponse.builder()
                    .valid(false)
                    .status("MALFORMED")
                    .message("Malformed token structure: must contain header, payload, and signature")
                    .build();
        }

        // 1. Kiểm tra chữ ký HMAC-SHA256
        String dataToSign = parts[0] + "." + parts[1];
        String expectedSignature = sign(dataToSign, secretKey);
        if (!expectedSignature.equals(parts[2])) {
            return VerifyTokenResponse.builder()
                    .valid(false)
                    .status("INVALID_SIGNATURE")
                    .message("Invalid token cryptographic signature")
                    .build();
        }

        // 2. Giải mã payload claims
        Map<String, Object> claims = extractAllClaims(clean);
        if (claims == null) {
            return VerifyTokenResponse.builder()
                    .valid(false)
                    .status("MALFORMED")
                    .message("Cannot parse token payload claims")
                    .build();
        }

        String username = getClaimString(claims, "sub");
        String tokenType = getClaimString(claims, "type");
        String jti = getClaimString(claims, "jti");
        Long exp = getClaimLong(claims, "exp");
        Long iat = getClaimLong(claims, "iat");

        long nowSeconds = System.currentTimeMillis() / 1000;

        // 3. Kiểm tra danh sách đen (Blacklist)
        if (isTokenInvalidated(clean)) {
            return buildIntrospectionResponse(false, "REVOKED", "Token has been revoked or logged out",
                    tokenType, username, claims, iat, exp, 0L, jti);
        }

        // 4. Kiểm tra vô hiệu hóa theo người dùng (User-level cutoff timestamp)
        if (username != null && userRevocationTimestamps.containsKey(username)) {
            long cutoffMillis = userRevocationTimestamps.get(username);
            long tokenIatMillis = (iat != null ? iat : 0L) * 1000;
            if (tokenIatMillis <= cutoffMillis) {
                return buildIntrospectionResponse(false, "REVOKED",
                        "Token was revoked due to password update or user session reset",
                        tokenType, username, claims, iat, exp, 0L, jti);
            }
        }

        // 5. Kiểm tra thời hạn hết hạn (Expiration)
        if (exp != null && exp <= nowSeconds) {
            return buildIntrospectionResponse(false, "EXPIRED", "Token has expired",
                    tokenType, username, claims, iat, exp, 0L, jti);
        }

        long remaining = (exp != null && exp > nowSeconds) ? (exp - nowSeconds) : 0L;

        return buildIntrospectionResponse(true, "VALID", "Token is valid and active",
                tokenType, username, claims, iat, exp, remaining, jti);
    }

    /**
     * Vô hiệu hóa một token cụ thể (đưa vào blacklist)
     */
    public void invalidateToken(String token) {
        if (token != null && !token.isBlank()) {
            String clean = cleanToken(token);
            long exp = extractExp(clean);
            long expiryTime = exp > 0 ? exp * 1000 : System.currentTimeMillis() + accessExpirationMillis;
            invalidatedTokens.put(clean, expiryTime);
            cleanupExpiredTokens();
        }
    }

    /**
     * Vô hiệu hóa toàn bộ token hiện có của người dùng (khi đổi mật khẩu hoặc reset session)
     */
    public void revokeAllUserTokens(String username) {
        if (username != null && !username.isBlank()) {
            userRevocationTimestamps.put(username, System.currentTimeMillis());
        }
    }

    /**
     * Kiểm tra token đã bị thu hồi trong blacklist hay chưa
     */
    public boolean isTokenInvalidated(String token) {
        if (token == null) return false;
        return invalidatedTokens.containsKey(cleanToken(token));
    }

    /**
     * Xóa sạch toàn bộ danh sách blacklist và mốc thời gian thu hồi (phục vụ reset cache & test isolation)
     */
    public void clearRevocations() {
        invalidatedTokens.clear();
        userRevocationTimestamps.clear();
    }

    public long getExpirationSeconds() {
        return accessExpirationMillis / 1000;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpirationMillis / 1000;
    }

    public String extractUsername(String token) {
        return extractClaim(token, "sub");
    }

    public String extractRole(String token) {
        return extractClaim(token, "role");
    }

    public Long extractUserId(String token) {
        String val = extractClaim(token, "userId");
        try {
            return val != null ? Long.parseLong(val) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String extractTokenType(String token) {
        return extractClaim(token, "type");
    }

    public String extractJti(String token) {
        return extractClaim(token, "jti");
    }

    public long extractExp(String token) {
        String expStr = extractClaim(token, "exp");
        try {
            return expStr != null ? Long.parseLong(expStr) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public long extractIat(String token) {
        String iatStr = extractClaim(token, "iat");
        try {
            return iatStr != null ? Long.parseLong(iatStr) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public String extractClaim(String token, String claimKey) {
        Map<String, Object> claims = extractAllClaims(cleanToken(token));
        if (claims == null || !claims.containsKey(claimKey)) {
            return null;
        }
        Object val = claims.get(claimKey);
        return val != null ? String.valueOf(val) : null;
    }

    public Map<String, Object> extractAllClaims(String token) {
        String clean = cleanToken(token);
        String[] parts = clean.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            return parseJson(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private String createSignedToken(Map<String, Object> claims) {
        String payloadJson = buildJson(claims);
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String dataToSign = HEADER_BASE64 + "." + payloadBase64;
        String signatureBase64 = sign(dataToSign, secretKey);
        return dataToSign + "." + signatureBase64;
    }

    private String buildJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() == null) continue;
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(escapeJson(String.valueOf(val))).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;

        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|([0-9]+|true|false|null))");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (matcher.group(3) != null) {
                map.put(key, unescapeJson(matcher.group(3)));
            } else {
                String rawVal = matcher.group(4);
                if ("null".equals(rawVal)) {
                    map.put(key, null);
                } else if ("true".equalsIgnoreCase(rawVal) || "false".equalsIgnoreCase(rawVal)) {
                    map.put(key, Boolean.parseBoolean(rawVal));
                } else {
                    try {
                        map.put(key, Long.parseLong(rawVal));
                    } catch (NumberFormatException e) {
                        map.put(key, rawVal);
                    }
                }
            }
        }
        return map;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private String cleanToken(String token) {
        if (token == null) return "";
        if (token.startsWith("Bearer ")) {
            return token.substring(7).trim();
        }
        return token.trim();
    }

    private void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        invalidatedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
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

    private String getClaimString(Map<String, Object> claims, String key) {
        Object val = claims.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    private Long getClaimLong(Map<String, Object> claims, String key) {
        Object val = claims.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private VerifyTokenResponse buildIntrospectionResponse(
            boolean valid, String status, String message,
            String tokenType, String username, Map<String, Object> claims,
            Long iat, Long exp, Long remaining, String jti) {

        Long userId = claims != null ? getClaimLong(claims, "userId") : null;
        String email = claims != null ? getClaimString(claims, "email") : null;
        String fullName = claims != null ? getClaimString(claims, "fullName") : null;
        String role = claims != null ? getClaimString(claims, "role") : null;
        String cabinNumber = claims != null ? getClaimString(claims, "cabinNumber") : null;
        String rfidTag = claims != null ? getClaimString(claims, "rfidTag") : null;

        return VerifyTokenResponse.builder()
                .valid(valid)
                .status(status)
                .message(message)
                .tokenType(tokenType)
                .username(username)
                .userId(userId)
                .email(email)
                .fullName(fullName)
                .role(role)
                .cabinNumber(cabinNumber)
                .rfidTag(rfidTag)
                .issuedAt(iat)
                .expiresAt(exp)
                .remainingSeconds(remaining)
                .jti(jti)
                .claims(claims)
                .build();
    }
}

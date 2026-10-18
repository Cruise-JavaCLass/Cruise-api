package com.cruise.activity.service;

import com.cruise.activity.exception.BusinessRuleException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service sinh và xác thực mã QR bảo mật động sử dụng HMAC-SHA256 có TTL (KAN-222)
 */
@Service
public class QrTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long DEFAULT_TTL_SECONDS = 60L;

    private static final Pattern ACT_PATTERN = Pattern.compile("\"activityId\"\\s*:\\s*(\\d+)");
    private static final Pattern PASS_PATTERN = Pattern.compile("\"passengerId\"\\s*:\\s*(\\d+)");
    private static final Pattern CABIN_PATTERN = Pattern.compile("\"cabinNumber\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern ISSUED_PATTERN = Pattern.compile("\"issuedAt\"\\s*:\\s*(\\d+)");
    private static final Pattern EXP_PATTERN = Pattern.compile("\"expiresAt\"\\s*:\\s*(\\d+)");
    private static final Pattern NONCE_PATTERN = Pattern.compile("\"nonce\"\\s*:\\s*\"([^\"]*)\"");

    private final byte[] secretKeyBytes;

    public QrTokenService(
            @Value("${cruise.security.qr.secret:CruiseActivityDynamicQRHMACSecretKey2026!@#$%^&*}") String secret
    ) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QrTokenPayload {
        private Long activityId;
        private Long passengerId;
        private String cabinNumber;
        private long issuedAt;
        private long expiresAt;
        private String nonce;
    }

    public String generateToken(Long activityId, Long passengerId, String cabinNumber) {
        return generateToken(activityId, passengerId, cabinNumber, DEFAULT_TTL_SECONDS);
    }

    public String generateToken(Long activityId, Long passengerId, String cabinNumber, long ttlSeconds) {
        try {
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(ttlSeconds);
            String safeCabin = cabinNumber != null ? cabinNumber.replace("\"", "\\\"") : "";
            String nonce = UUID.randomUUID().toString();

            String jsonPayload = String.format(
                    "{\"activityId\":%d,\"passengerId\":%d,\"cabinNumber\":\"%s\",\"issuedAt\":%d,\"expiresAt\":%d,\"nonce\":\"%s\"}",
                    activityId, passengerId, safeCabin, now.toEpochMilli(), exp.toEpochMilli(), nonce
            );

            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String signature = sign(encodedPayload);

            return encodedPayload + "." + signature;
        } catch (Exception e) {
            throw new BusinessRuleException("Lỗi sinh mã QR bảo mật: " + e.getMessage());
        }
    }

    public QrTokenPayload verifyAndDecode(String token, Long expectedActivityId) {
        if (token == null || token.isBlank()) {
            throw new BusinessRuleException("Mã QR không được để trống");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 2) {
            throw new BusinessRuleException("Định dạng mã QR không hợp lệ");
        }

        String encodedPayload = parts[0];
        String providedSignature = parts[1];

        String expectedSignature = sign(encodedPayload);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), providedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessRuleException("Mã QR không hợp lệ hoặc đã bị thay đổi (Tampered QR token)");
        }

        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedPayload);
            String json = new String(decodedBytes, StandardCharsets.UTF_8);

            Long activityId = parseLong(ACT_PATTERN, json);
            Long passengerId = parseLong(PASS_PATTERN, json);
            String cabin = parseString(CABIN_PATTERN, json);
            long issuedAt = parseLong(ISSUED_PATTERN, json);
            long expiresAt = parseLong(EXP_PATTERN, json);
            String nonce = parseString(NONCE_PATTERN, json);

            long now = Instant.now().toEpochMilli();
            if (now > expiresAt) {
                throw new BusinessRuleException("Mã QR đã hết hạn. Vui lòng làm mới mã.");
            }

            if (expectedActivityId != null && !expectedActivityId.equals(activityId)) {
                throw new BusinessRuleException("Mã QR này không thuộc hoạt động hiện tại");
            }

            return QrTokenPayload.builder()
                    .activityId(activityId)
                    .passengerId(passengerId)
                    .cabinNumber(cabin)
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .nonce(nonce)
                    .build();
        } catch (BusinessRuleException bre) {
            throw bre;
        } catch (Exception e) {
            throw new BusinessRuleException("Không thể giải mã dữ liệu QR: " + e.getMessage());
        }
    }

    private Long parseLong(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return 0L;
    }

    private String parseString(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi khởi tạo HMAC: " + e.getMessage(), e);
        }
    }
}

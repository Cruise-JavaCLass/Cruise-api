package com.cruise.auth.service;

import com.cruise.auth.dto.BoardingPassPayload;
import com.cruise.auth.dto.BoardingPassVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Service sinh và xác thực mã QR thẻ lên tàu (Boarding Pass) bằng chữ ký số HMAC-SHA256 (KAN-722).
 * Hỗ trợ xác thực ngoại tuyến (100% offline) không cần truy vấn CSDL hay kết nối mạng.
 */
@Slf4j
@Service
public class BoardingPassCryptoService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long DEFAULT_TTL_SECONDS = 86400L; // 24 giờ

    private final byte[] secretKeyBytes;
    private final ObjectMapper objectMapper;

    public BoardingPassCryptoService(
            @Value("${cruise.security.boarding-pass.secret:${jwt.secret}}") String secret,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "Boarding pass secret must be configured with at least 32 UTF-8 bytes for HMAC-SHA256 security"
            );
        }
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    /**
     * Sinh chuỗi mã QR thẻ lên tàu từ payload đối tượng.
     * Định dạng kết quả: <Base64URLSafePayload>.<Base64URLSafeSignature>
     */
    public String generateBoardingPassQr(BoardingPassPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("BoardingPassPayload không được để null");
        }
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String signature = sign(encodedPayload);
            return encodedPayload + "." + signature;
        } catch (Exception e) {
            log.error("Lỗi sinh mã QR thẻ lên tàu: {}", e.getMessage(), e);
            throw new IllegalStateException("Lỗi sinh mã QR thẻ lên tàu: " + e.getMessage(), e);
        }
    }

    /**
     * Hàm overload tiện lợi sinh mã QR với thời gian sống (TTL tính bằng giây).
     */
    public String generateBoardingPassQr(Long passengerId, String cabinNumber, String musterStation,
                                        String voyageCode, long ttlSeconds) {
        long validUntilTimestamp = System.currentTimeMillis() + (ttlSeconds * 1000L);
        BoardingPassPayload payload = BoardingPassPayload.builder()
                .passengerId(passengerId)
                .cabinNumber(cabinNumber)
                .musterStation(musterStation)
                .voyageCode(voyageCode)
                .validUntilTimestamp(validUntilTimestamp)
                .build();
        return generateBoardingPassQr(payload);
    }

    /**
     * Xác thực mã QR thẻ lên tàu hoàn toàn offline (chống can thiệp, chống làm giả, kiểm tra hạn dùng).
     */
    public BoardingPassVerificationResult verifyBoardingPassQr(String qrPayload) {
        if (qrPayload == null || qrPayload.isBlank()) {
            return BoardingPassVerificationResult.invalid("Mã QR không được để trống");
        }

        String trimmed = qrPayload.trim();
        int dotIndex = trimmed.indexOf('.');
        if (dotIndex <= 0 || dotIndex == trimmed.length() - 1 || trimmed.indexOf('.', dotIndex + 1) != -1) {
            return BoardingPassVerificationResult.invalid("Định dạng mã QR không hợp lệ");
        }

        String encodedPayload = trimmed.substring(0, dotIndex);
        String providedSignature = trimmed.substring(dotIndex + 1);

        // 1. Kiểm tra chữ ký HMAC-SHA256 (constant-time comparison để chống timing attacks)
        String expectedSignature = sign(encodedPayload);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8))) {
            return BoardingPassVerificationResult.invalid("Mã QR không hợp lệ hoặc đã bị thay đổi (Tampered QR token)");
        }

        // 2. Giải mã Base64 URL-Safe và parse JSON thành BoardingPassPayload
        BoardingPassPayload payload;
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedPayload);
            payload = objectMapper.readValue(decodedBytes, BoardingPassPayload.class);
        } catch (Exception e) {
            return BoardingPassVerificationResult.invalid("Không thể giải mã dữ liệu mã QR: " + e.getMessage());
        }

        // 3. Kiểm tra hạn dùng của thẻ lên tàu
        long now = System.currentTimeMillis();
        if (now > payload.getValidUntilTimestamp()) {
            return BoardingPassVerificationResult.invalid("Thẻ lên tàu đã hết hạn");
        }

        return BoardingPassVerificationResult.valid(payload);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Lỗi tính toán HMAC-SHA256: " + e.getMessage(), e);
        }
    }
}

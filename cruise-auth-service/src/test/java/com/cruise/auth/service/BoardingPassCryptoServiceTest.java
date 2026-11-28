package com.cruise.auth.service;

import com.cruise.auth.dto.BoardingPassPayload;
import com.cruise.auth.dto.BoardingPassVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit Test Suite kiểm thử BoardingPassCryptoService (KAN-722).
 * Kiểm thử toàn diện cơ chế sinh mã QR HMAC-SHA256 và xác thực thẻ lên tàu offline.
 */
class BoardingPassCryptoServiceTest {

    private static final String VALID_SECRET_1 = "cruise-secret-key-super-secure-token-2026-auth-jwt-system";
    private static final String VALID_SECRET_2 = "another-super-secure-secret-key-32-bytes-long-for-testing";

    private ObjectMapper objectMapper;
    private BoardingPassCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cryptoService = new BoardingPassCryptoService(VALID_SECRET_1, objectMapper);
    }

    @Nested
    @DisplayName("1. Sinh mã QR và xác thực ngoại tuyến thành công")
    class GenerateAndVerifySuccessTests {

        @Test
        @DisplayName("Sinh mã QR thành công, định dạng Base64 URL-Safe không padding (không có +, /, =)")
        void shouldGenerateAndVerifyBoardingPassQrSuccessfully() {
            long passengerId = 5001L;
            String cabinNumber = "CAB-402";
            String musterStation = "Muster Station A - Deck 4";
            String voyageCode = "VOY-2026-SG-PK";
            long ttlSeconds = 3600L;

            String qr = cryptoService.generateBoardingPassQr(passengerId, cabinNumber, musterStation, voyageCode, ttlSeconds);

            // Kiểm tra định dạng chuỗi: chứa đúng 1 dấu chấm phân cách
            assertThat(qr).isNotNull().contains(".");
            String[] parts = qr.split("\\.");
            assertThat(parts).hasSize(2);

            // Xác nhận cả payload và signature đều tuân thủ Base64 URL-Safe (không có '+', '/', '=')
            assertThat(qr).doesNotContain("+", "/", "=");

            // Xác thực offline
            BoardingPassVerificationResult result = cryptoService.verifyBoardingPassQr(qr);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).isEqualTo("Xác thực thẻ lên tàu thành công");
            assertThat(result.getPayload()).isNotNull();

            BoardingPassPayload payload = result.getPayload();
            assertThat(payload.getPassengerId()).isEqualTo(passengerId);
            assertThat(payload.getCabinNumber()).isEqualTo(cabinNumber);
            assertThat(payload.getMusterStation()).isEqualTo(musterStation);
            assertThat(payload.getVoyageCode()).isEqualTo(voyageCode);
            assertThat(payload.getValidUntilTimestamp()).isGreaterThan(System.currentTimeMillis());
        }

        @Test
        @DisplayName("Sinh từ BoardingPassPayload đối tượng và xác thực thành công")
        void shouldGenerateFromObjectPayloadSuccessfully() {
            long validUntil = System.currentTimeMillis() + 60000L;
            BoardingPassPayload originalPayload = BoardingPassPayload.builder()
                    .passengerId(8888L)
                    .cabinNumber("CAB-7012")
                    .musterStation("Muster Station A - Deck 7")
                    .voyageCode("VOY-AUTUMN-2026")
                    .validUntilTimestamp(validUntil)
                    .build();

            String qr = cryptoService.generateBoardingPassQr(originalPayload);
            BoardingPassVerificationResult result = cryptoService.verifyBoardingPassQr(qr);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getPayload().getPassengerId()).isEqualTo(8888L);
            assertThat(result.getPayload().getCabinNumber()).isEqualTo("CAB-7012");
            assertThat(result.getPayload().getValidUntilTimestamp()).isEqualTo(validUntil);
        }
    }

    @Nested
    @DisplayName("2. Phát hiện giả mạo và can thiệp trái phép (Tampering & Signature attacks)")
    class TamperingAndSecurityTests {

        @Test
        @DisplayName("Phát hiện bị sửa lén payload (đổi cabinNumber) nhưng giữ nguyên signature -> từ chối (valid=false)")
        void shouldDetectTamperedCabinNumberInPayload() throws Exception {
            String originalQr = cryptoService.generateBoardingPassQr(5001L, "CAB-402", "Muster Station A - Deck 4", "VOY-001", 3600L);
            String[] parts = originalQr.split("\\.");
            String originalSignature = parts[1];

            // Kẻ tấn công giải mã payload, sửa cabin thành VIP "CAB-999" rồi mã hóa lại URL-Safe
            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            BoardingPassPayload payload = objectMapper.readValue(decoded, BoardingPassPayload.class);
            payload.setCabinNumber("CAB-999");
            String tamperedJson = objectMapper.writeValueAsString(payload);
            String tamperedEncodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8));

            String forgedQr = tamperedEncodedPayload + "." + originalSignature;

            BoardingPassVerificationResult result = cryptoService.verifyBoardingPassQr(forgedQr);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getPayload()).isNull();
            assertThat(result.getMessage()).contains("Tampered");
        }

        @Test
        @DisplayName("Phát hiện bị sửa lén passengerId -> từ chối (valid=false)")
        void shouldDetectTamperedPassengerIdInPayload() throws Exception {
            String originalQr = cryptoService.generateBoardingPassQr(5001L, "CAB-402", "Muster Station A - Deck 4", "VOY-001", 3600L);
            String[] parts = originalQr.split("\\.");

            byte[] decoded = Base64.getUrlDecoder().decode(parts[0]);
            BoardingPassPayload payload = objectMapper.readValue(decoded, BoardingPassPayload.class);
            payload.setPassengerId(9999L); // Sửa ID hành khách
            String tamperedJson = objectMapper.writeValueAsString(payload);
            String tamperedEncodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(tamperedJson.getBytes(StandardCharsets.UTF_8));

            String forgedQr = tamperedEncodedPayload + "." + parts[1];

            BoardingPassVerificationResult result = cryptoService.verifyBoardingPassQr(forgedQr);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getPayload()).isNull();
        }

        @Test
        @DisplayName("Ký bằng secret key khác -> từ chối (valid=false)")
        void shouldRejectWhenSignedWithDifferentSecretKey() {
            BoardingPassCryptoService attackerService = new BoardingPassCryptoService(VALID_SECRET_2, objectMapper);
            String fakeQr = attackerService.generateBoardingPassQr(5001L, "CAB-402", "Muster Station A - Deck 4", "VOY-001", 3600L);

            // Hệ thống tàu dùng VALID_SECRET_1 để xác thực
            BoardingPassVerificationResult result = cryptoService.verifyBoardingPassQr(fakeQr);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getPayload()).isNull();
            assertThat(result.getMessage()).contains("Tampered");
        }
    }

    @Nested
    @DisplayName("3. Kiểm tra hết hạn (Expiration Checks)")
    class ExpirationTests {

        @Test
        @DisplayName("Thẻ lên tàu có validUntilTimestamp trong quá khứ -> từ chối (valid=false)")
        void shouldDetectExpiredBoardingPass() {
            long pastTimestamp = System.currentTimeMillis() - 5000L; // Đã hết hạn 5 giây trước
            BoardingPassPayload expiredPayload = BoardingPassPayload.builder()
                    .passengerId(5001L)
                    .cabinNumber("CAB-402")
                    .musterStation("Muster Station A - Deck 4")
                    .voyageCode("VOY-001")
                    .validUntilTimestamp(pastTimestamp)
                    .build();

            String qr = cryptoService.generateBoardingPassQr(expiredPayload);

            BoardingPassVerificationResult result = cryptoService.verifyBoardingPassQr(qr);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getPayload()).isNull();
            assertThat(result.getMessage()).isEqualTo("Thẻ lên tàu đã hết hạn");
        }
    }

    @Nested
    @DisplayName("4. Xử lý các đầu vào rác, sai định dạng (Malformed & Null Inputs)")
    class MalformedInputsTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "   ",
                "invalid-string-no-dot",
                "part1.part2.part3",
                ".onlysignature",
                "onlypayload.",
                "invalid!payload.validsignature",
                "not_base64_json.signature"
        })
        @DisplayName("Chuỗi QR sai định dạng -> từ chối an toàn mà không ném lỗi unhandled exception")
        void shouldHandleMalformedStringsGracefully(String malformedQr) {
            BoardingPassVerificationResult result = cryptoService.verifyBoardingPassQr(malformedQr);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getPayload()).isNull();
        }

        @Test
        @DisplayName("qrPayload == null -> trả về valid=false")
        void shouldHandleNullQrPayloadGracefully() {
            BoardingPassVerificationResult result = cryptoService.verifyBoardingPassQr(null);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getPayload()).isNull();
            assertThat(result.getMessage()).isEqualTo("Mã QR không được để trống");
        }

        @Test
        @DisplayName("generateBoardingPassQr(null) -> ném IllegalArgumentException")
        void shouldThrowWhenGeneratingWithNullPayload() {
            assertThatThrownBy(() -> cryptoService.generateBoardingPassQr(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("5. Kiểm tra ràng buộc độ dài khóa bảo mật (Secret Key Validation)")
    class SecretKeyConstraintTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "short",
                "1234567890123456789012345678901" // Đúng 31 ký tự (< 32 bytes)
        })
        @DisplayName("Secret key ngắn hơn 32 byte UTF-8 -> ném IllegalArgumentException")
        void shouldThrowWhenSecretKeyIsShorterThan32Bytes(String shortSecret) {
            assertThatThrownBy(() -> new BoardingPassCryptoService(shortSecret, objectMapper))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 32 UTF-8 bytes");
        }

        @Test
        @DisplayName("Secret key == null -> ném IllegalArgumentException")
        void shouldThrowWhenSecretKeyIsNull() {
            assertThatThrownBy(() -> new BoardingPassCryptoService(null, objectMapper))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least 32 UTF-8 bytes");
        }
    }
}

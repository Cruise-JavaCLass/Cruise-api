package com.cruise.auth.security;

import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import com.cruise.auth.service.JwtService;
import com.cruise.auth.service.PasswordService;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test Suite kiểm thử toàn diện kịch bản:
 * 1. Thông tin đăng nhập sai và không hợp lệ (Invalid Credentials)
 * 2. Token hết hạn (Expired Tokens)
 * 3. Token bị giả mạo chữ ký, cấu trúc dị dạng, đã bị thu hồi hoặc lặp lại refresh (Replay Attack)
 *
 * Jira Task: KAN-65 — Test Invalid Credentials and Expired Tokens
 * Jira Story: KAN-37 — Authentication and User Management
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("KAN-65: Invalid Credentials and Expired Tokens End-to-End Security Tests")
class InvalidCredentialsAndExpiredTokensIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    private User activeUser;
    private User inactiveUser;
    private User nullPasswordUser;

    private static final String VALID_RAW_PASSWORD = "Valid#Password2026";

    @BeforeEach
    void setUp() {
        jwtService.clearRevocations();
        userRepository.deleteAll();

        // 1. Tạo người dùng đang hoạt động bình thường
        activeUser = User.builder()
                .username("captain.alex")
                .fullName("Alex Morgan")
                .email("captain.alex@cruise.com")
                .password(passwordService.hashPassword(VALID_RAW_PASSWORD))
                .role(Role.SYSTEM_ADMIN)
                .cabinNumber("Bridge-01")
                .rfidTag("RFID-CAPTAIN-01")
                .active(true)
                .build();
        activeUser = userRepository.save(activeUser);

        // 2. Tạo người dùng đã bị vô hiệu hóa (disabled / inactive)
        inactiveUser = User.builder()
                .username("banned.sailor")
                .fullName("John Banned")
                .email("banned@cruise.com")
                .password(passwordService.hashPassword(VALID_RAW_PASSWORD))
                .role(Role.PASSENGER)
                .cabinNumber("Deck-04")
                .rfidTag("RFID-BANNED-02")
                .active(false)
                .build();
        inactiveUser = userRepository.save(inactiveUser);

        // 3. Tạo người dùng chưa thiết lập mật khẩu (null password trong database)
        nullPasswordUser = User.builder()
                .username("sso.passenger")
                .fullName("SSO Guest")
                .email("sso.guest@cruise.com")
                .password(null)
                .role(Role.PASSENGER)
                .active(true)
                .build();
        nullPasswordUser = userRepository.save(nullPasswordUser);
    }

    // =========================================================================
    // JSON HELPER METHODS
    // =========================================================================
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String loginJson(String identifier, String password) {
        return String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}", escape(identifier), escape(password));
    }

    private static String verifyPasswordJson(String identifier, String password) {
        return String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}", escape(identifier), escape(password));
    }

    private static String changePasswordJson(String oldPass, String newPass, String confirmPass) {
        return String.format("{\"oldPassword\":\"%s\",\"newPassword\":\"%s\",\"confirmPassword\":\"%s\"}",
                escape(oldPass), escape(newPass), escape(confirmPass));
    }

    private static String refreshTokenJson(String token) {
        return String.format("{\"refreshToken\":\"%s\"}", escape(token));
    }

    private static String verifyTokenJson(String token) {
        return String.format("{\"token\":\"%s\"}", escape(token));
    }

    private static String revokeTokenJson(String token) {
        return String.format("{\"token\":\"%s\"}", escape(token));
    }

    private static String extractJsonField(String json, String fieldName) {
        if (json == null) return null;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // =========================================================================
    // PHẦN 1: TEST THÔNG TIN ĐĂNG NHẬP SAI & KHÔNG HỢP LỆ (INVALID CREDENTIALS)
    // =========================================================================
    @Nested
    @DisplayName("1. Invalid Credentials Tests (POST /api/auth/login & related)")
    class InvalidCredentialsTests {

        @Test
        @DisplayName("1.1. Login thất bại 401 khi nhập sai mật khẩu cho username hợp lệ")
        void testLogin_IncorrectPasswordForUsername_Returns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("captain.alex", "WrongPassword123@")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("1.2. Login thất bại 401 khi nhập sai mật khẩu cho email hợp lệ")
        void testLogin_IncorrectPasswordForEmail_Returns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("captain.alex@cruise.com", "WrongPassword123@")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("1.3. Login thất bại 401 khi username không tồn tại trong hệ thống")
        void testLogin_NonExistentUsername_Returns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("ghost.user", VALID_RAW_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("1.4. Login thất bại 401 khi email không tồn tại trong hệ thống")
        void testLogin_NonExistentEmail_Returns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("unknown@cruise.com", VALID_RAW_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("1.5. Login thất bại 403 khi tài khoản bị vô hiệu hóa (active = false)")
        void testLogin_InactiveAccount_Returns403() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("banned.sailor", VALID_RAW_PASSWORD)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Account is inactive or disabled"));
        }

        @Test
        @DisplayName("1.6. Login thất bại 401 khi tài khoản có mật khẩu null trong DB")
        void testLogin_UserWithNullPassword_Returns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("sso.passenger", VALID_RAW_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("1.7. Login an toàn và trả về 401 khi bị tấn công SQL Injection vào username")
        void testLogin_SqlInjectionPayloadInUsername_SafelyReturns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("' OR '1'='1", "anyPassword123@")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("1.8. Login an toàn và trả về 401 khi username chứa mã độc XSS")
        void testLogin_XssPayloadInUsername_SafelyReturns401() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("<script>alert('hack')</script>", "anyPassword123@")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("1.9. Validation 400 Bad Request khi để trống username (blank/empty)")
        void testLogin_BlankUsername_Returns400() throws Exception {
            String body = "{\"usernameOrEmail\":\"   \",\"password\":\"ValidPass123@\"}";

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("1.10. Validation 400 Bad Request khi để trống password")
        void testLogin_BlankPassword_Returns400() throws Exception {
            String body = "{\"usernameOrEmail\":\"captain.alex\",\"password\":\"\"}";

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("1.11. Validation 400 Bad Request khi gửi request body rỗng '{}'")
        void testLogin_EmptyRequestBody_Returns400() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("1.12. POST /api/auth/verify-password trả về valid=false khi mật khẩu không đúng")
        void testVerifyPassword_IncorrectPassword_ReturnsValidFalse() throws Exception {
            mockMvc.perform(post("/api/auth/verify-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyPasswordJson("captain.alex", "WrongSecret123@")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.message").value("Password verification failed"));
        }

        @Test
        @DisplayName("1.13. POST /api/auth/verify-password trả về valid=false khi username không tồn tại")
        void testVerifyPassword_NonExistentUser_ReturnsValidFalse() throws Exception {
            mockMvc.perform(post("/api/auth/verify-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyPasswordJson("nonexistent.user", VALID_RAW_PASSWORD)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false));
        }

        @Test
        @DisplayName("1.14. POST /api/auth/change-password trả về 400 khi mật khẩu hiện tại sai")
        void testChangePassword_WrongOldPassword_Returns400() throws Exception {
            String validToken = jwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/change-password")
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changePasswordJson("WrongOldPass123@", "NewValidPass999#", "NewValidPass999#")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại không chính xác"));
        }

        @Test
        @DisplayName("1.15. POST /api/auth/change-password trả về 400 khi mật khẩu mới trùng mật khẩu cũ")
        void testChangePassword_NewPasswordSameAsOld_Returns400() throws Exception {
            String validToken = jwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/change-password")
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changePasswordJson(VALID_RAW_PASSWORD, VALID_RAW_PASSWORD, VALID_RAW_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Mật khẩu mới không được trùng với mật khẩu cũ"));
        }

        @Test
        @DisplayName("1.16. POST /api/auth/change-password trả về 400 khi xác nhận mật khẩu không khớp")
        void testChangePassword_ConfirmPasswordMismatch_Returns400() throws Exception {
            String validToken = jwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/change-password")
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changePasswordJson(VALID_RAW_PASSWORD, "NewPassA123@", "DifferentPassB456#")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Mật khẩu xác nhận không trùng khớp"));
        }

        @Test
        @DisplayName("1.17. POST /api/auth/change-password trả về 400 khi mật khẩu mới không đạt chuẩn bảo mật")
        void testChangePassword_WeakNewPassword_Returns400() throws Exception {
            String validToken = jwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/change-password")
                            .header("Authorization", "Bearer " + validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changePasswordJson(VALID_RAW_PASSWORD, "weak", "weak")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("1.18. POST /api/auth/change-password trả về 403 khi tài khoản bị khóa/vô hiệu hóa")
        void testChangePassword_InactiveAccount_Returns403() throws Exception {
            String tokenForInactiveUser = jwtService.generateAccessToken(inactiveUser);

            mockMvc.perform(post("/api/auth/change-password")
                            .header("Authorization", "Bearer " + tokenForInactiveUser)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changePasswordJson(VALID_RAW_PASSWORD, "NewValidPass999#", "NewValidPass999#")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Account is inactive or disabled"));
        }
    }

    // =========================================================================
    // PHẦN 2: TEST TOKEN HẾT HẠN (EXPIRED TOKENS)
    // =========================================================================
    @Nested
    @DisplayName("2. Expired Tokens Tests (Expired Access & Refresh Tokens)")
    class ExpiredTokensTests {

        private JwtService expiredJwtService;

        @BeforeEach
        void initExpiredService() {
            // Service tạo token với thời hạn âm (đã hết hạn ngay khi vừa tạo)
            expiredJwtService = new JwtService(
                    "cruise-secret-key-super-secure-token-2026-auth-jwt-system",
                    -5000L,
                    -10000L,
                    "cruise-auth-service"
            );
        }

        @Test
        @DisplayName("2.1. Xác minh Access Token đã hết hạn trả về valid=false và status=EXPIRED")
        void testVerifyToken_ExpiredAccessToken_ReturnsValidFalseAndExpiredStatus() throws Exception {
            String expiredAccessToken = expiredJwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyTokenJson(expiredAccessToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                    .andExpect(jsonPath("$.data.message").value("Token has expired"))
                    .andExpect(jsonPath("$.data.remainingSeconds").value(0))
                    .andExpect(jsonPath("$.data.username").value("captain.alex"));
        }

        @Test
        @DisplayName("2.2. Xác minh Refresh Token đã hết hạn trả về valid=false và status=EXPIRED")
        void testVerifyToken_ExpiredRefreshToken_ReturnsValidFalseAndExpiredStatus() throws Exception {
            String expiredRefreshToken = expiredJwtService.generateRefreshToken(activeUser);

            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyTokenJson(expiredRefreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("EXPIRED"))
                    .andExpect(jsonPath("$.data.tokenType").value("REFRESH"));
        }

        @Test
        @DisplayName("2.3. Xác minh token hết hạn truyền qua Header Authorization: Bearer <expired-token>")
        void testVerifyToken_ExpiredTokenInAuthHeader_ReturnsExpired() throws Exception {
            String expiredToken = expiredJwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/verify-token")
                            .header("Authorization", "Bearer " + expiredToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("EXPIRED"));
        }

        @Test
        @DisplayName("2.4. POST /api/auth/refresh-token thất bại 401 khi Refresh Token đã hết hạn")
        void testRefreshToken_ExpiredRefreshToken_Returns401() throws Exception {
            String expiredRefreshToken = expiredJwtService.generateRefreshToken(activeUser);

            mockMvc.perform(post("/api/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshTokenJson(expiredRefreshToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid refresh token: Token has expired"));
        }

        @Test
        @DisplayName("2.5. GET /api/auth/me thất bại 401 khi dùng Access Token đã hết hạn")
        void testGetCurrentUser_ExpiredAccessToken_Returns401() throws Exception {
            String expiredToken = expiredJwtService.generateAccessToken(activeUser);

            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid or expired session token"));
        }

        @Test
        @DisplayName("2.6. POST /api/auth/change-password thất bại 401 khi dùng Access Token đã hết hạn")
        void testChangePassword_ExpiredAccessToken_Returns401() throws Exception {
            String expiredToken = expiredJwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/change-password")
                            .header("Authorization", "Bearer " + expiredToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changePasswordJson(VALID_RAW_PASSWORD, "NewPassWord123#", "NewPassWord123#")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid or expired session token"));
        }

        @Test
        @DisplayName("2.7. POST /api/auth/token/pair thất bại 401 khi dùng Access Token đã hết hạn")
        void testIssueTokenPair_ExpiredAccessToken_Returns401() throws Exception {
            String expiredToken = expiredJwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/token/pair")
                            .header("Authorization", "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid or expired session token"));
        }
    }

    // =========================================================================
    // PHẦN 3: TEST TOKEN BỊ GIẢ MẠO, DỊ DẠNG, THU HỒI & REPLAY ATTACK
    // =========================================================================
    @Nested
    @DisplayName("3. Tampered, Malformed, Revoked & Replay Attack Tests")
    class TamperedAndRevokedTokensTests {

        @Test
        @DisplayName("3.1. Xác minh token có chữ ký mật mã bị sửa đổi (tampered signature) trả về INVALID_SIGNATURE")
        void testVerifyToken_TamperedSignature_ReturnsInvalidSignature() throws Exception {
            String validToken = jwtService.generateAccessToken(activeUser);
            String[] parts = validToken.split("\\.");
            String tamperedToken = parts[0] + "." + parts[1] + ".tamperedForgedSignatureABC123";

            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyTokenJson(tamperedToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("INVALID_SIGNATURE"))
                    .andExpect(jsonPath("$.data.message").value("Invalid token cryptographic signature"));
        }

        @Test
        @DisplayName("3.2. Xác minh token bị leo thang đặc quyền (tampered payload) trả về INVALID_SIGNATURE")
        void testVerifyToken_TamperedPayloadPrivilegeEscalation_ReturnsInvalidSignature() throws Exception {
            String validToken = jwtService.generateAccessToken(activeUser);
            String[] parts = validToken.split("\\.");

            // Kẻ tấn công thay đổi payload để sửa role hoặc username nhưng giữ nguyên chữ ký cũ
            String fakePayload = "{\"sub\":\"captain.alex\",\"role\":\"SUPER_HACKER\",\"iss\":\"cruise-auth-service\"}";
            String fakePayloadBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(fakePayload.getBytes());
            String forgedToken = parts[0] + "." + fakePayloadBase64 + "." + parts[2];

            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyTokenJson(forgedToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("INVALID_SIGNATURE"));
        }

        @Test
        @DisplayName("3.3. Xác minh token được ký bởi khóa bí mật khác trả về INVALID_SIGNATURE")
        void testVerifyToken_SignedWithForeignSecretKey_ReturnsInvalidSignature() throws Exception {
            JwtService foreignJwtService = new JwtService("foreign-attacker-secret-key-1234567890", 86400000L);
            String foreignToken = foreignJwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyTokenJson(foreignToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("INVALID_SIGNATURE"));
        }

        @Test
        @DisplayName("3.4. Xác minh chuỗi JWT dị dạng (malformed string) trả về MALFORMED")
        void testVerifyToken_MalformedJwtString_ReturnsMalformed() throws Exception {
            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyTokenJson("not.a.valid.jwt.string.format")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("MALFORMED"));
        }

        @Test
        @DisplayName("3.5. Xác minh token rỗng hoặc không cung cấp token trả về 400 Bad Request")
        void testVerifyToken_MissingToken_Returns400() throws Exception {
            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Token is required in request body or Authorization header"));
        }

        @Test
        @DisplayName("3.6. Sau khi đăng xuất (logout), token bị đưa vào blacklist và trả về REVOKED")
        void testLogout_RevokesToken_SubsequentRequestsReturn401() throws Exception {
            // Bước 1: Login lấy token hợp lệ
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("captain.alex", VALID_RAW_PASSWORD)))
                    .andExpect(status().isOk())
                    .andReturn();

            String token = extractJsonField(loginResult.getResponse().getContentAsString(), "accessToken");
            assertThat(token).isNotEmpty();

            // Kiểm tra token trước khi logout là hợp lệ
            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // Bước 2: Thực hiện đăng xuất
            mockMvc.perform(post("/api/auth/logout")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logout successful"));

            // Bước 3: Xác minh token đã đăng xuất -> trả về REVOKED
            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyTokenJson(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("REVOKED"));

            // Bước 4: Gọi endpoint /api/auth/me với token đã thu hồi -> 401 Unauthorized
            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid or expired session token"));
        }

        @Test
        @DisplayName("3.7. Thu hồi token qua POST /api/auth/revoke-token ngăn chặn các truy cập tiếp theo")
        void testRevokeTokenEndpoint_InvalidatesTokenImmediately() throws Exception {
            String token = jwtService.generateAccessToken(activeUser);

            // Thu hồi token
            mockMvc.perform(post("/api/auth/revoke-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(revokeTokenJson(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Token revoked successfully"));

            // Token bị chuyển thành REVOKED
            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyTokenJson(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("REVOKED"));
        }

        @Test
        @DisplayName("3.8. Phòng chống Replay Attack: Refresh Token chỉ được dùng 1 lần (Token Rotation)")
        void testRefreshTokenRotation_ReplayAttackPrevented_Returns401OnSecondUse() throws Exception {
            // Bước 1: Login lấy refresh token ban đầu
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("captain.alex", VALID_RAW_PASSWORD)))
                    .andExpect(status().isOk())
                    .andReturn();

            String originalRefreshToken = extractJsonField(loginResult.getResponse().getContentAsString(), "refreshToken");
            assertThat(originalRefreshToken).isNotEmpty();

            // Bước 2: Làm mới token lần đầu (thành công)
            MvcResult firstRefreshResult = mockMvc.perform(post("/api/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshTokenJson(originalRefreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andReturn();

            String newRefreshToken = extractJsonField(firstRefreshResult.getResponse().getContentAsString(), "refreshToken");
            assertThat(newRefreshToken).isNotEqualTo(originalRefreshToken);

            // Bước 3: Tấn công phát lại (Replay Attack) - Sử dụng lại Refresh Token cũ
            mockMvc.perform(post("/api/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshTokenJson(originalRefreshToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid refresh token")));
        }

        @Test
        @DisplayName("3.9. Gửi Access Token tới endpoint refresh-token bị từ chối 401 vì sai loại token")
        void testRefreshToken_PassingAccessTokenInsteadOfRefreshToken_Returns401() throws Exception {
            String accessToken = jwtService.generateAccessToken(activeUser);

            mockMvc.perform(post("/api/auth/refresh-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshTokenJson(accessToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Provided token is not a valid refresh token"));
        }

        @Test
        @DisplayName("3.10. Đổi mật khẩu thành công sẽ tự động thu hồi toàn bộ token đã cấp trước đó (User Cutoff Revocation)")
        void testChangePassword_UserLevelRevocation_InvalidatesPreviousTokens() throws Exception {
            String oldAccessToken = jwtService.generateAccessToken(activeUser);

            // Đảm bảo token cũ hoạt động
            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + oldAccessToken))
                    .andExpect(status().isOk());

            // Đợi 15ms để mốc cutoff lớn hơn iat của token cũ
            Thread.sleep(15);

            // Đổi mật khẩu
            mockMvc.perform(post("/api/auth/change-password")
                            .header("Authorization", "Bearer " + oldAccessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changePasswordJson(VALID_RAW_PASSWORD, "BrandNewPass999#", "BrandNewPass999#")))
                    .andExpect(status().isOk());

            // Token cũ lập tức bị coi là REVOKED
            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(verifyTokenJson(oldAccessToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("REVOKED"));

            // Dùng token cũ truy cập /api/auth/me -> 401 Unauthorized
            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer " + oldAccessToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("3.11. Gọi protected endpoint thiếu Authorization Header trả về 401 Unauthorized")
        void testProtectedEndpoints_MissingAuthorizationHeader_Returns401() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));

            mockMvc.perform(post("/api/auth/token/pair"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));

            mockMvc.perform(post("/api/auth/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(changePasswordJson("OldPass123@", "NewPass456#", "NewPass456#")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Missing or invalid Authorization header"));
        }

        @Test
        @DisplayName("3.12. Header Authorization dị dạng (không đúng định dạng Bearer) trả về 401 Unauthorized")
        void testProtectedEndpoints_MalformedAuthorizationHeader_Returns401() throws Exception {
            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Basic dXNlcjpwYXNz"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));

            mockMvc.perform(get("/api/auth/me")
                            .header("Authorization", "Bearer    "))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}

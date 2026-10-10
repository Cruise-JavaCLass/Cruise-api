package com.cruise.auth.controller;

import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import com.cruise.auth.service.PasswordService;
import com.cruise.common.enums.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite kiểm thử tích hợp toàn diện cho API Login và Logout (KAN-64)
 * Bao gồm đầy đủ các kịch bản thành công và thất bại:
 * - Đăng nhập bằng Username / Email hợp lệ
 * - Đăng nhập với nhiều vai trò (Role: PASSENGER, SYSTEM_ADMIN, POS_STAFF)
 * - Đăng nhập thất bại do sai mật khẩu, tài khoản không tồn tại, tài khoản bị vô hiệu hóa
 * - Đăng nhập thất bại do vi phạm validation (bỏ trống username/password, payload rỗng, JSON hỏng)
 * - Đăng xuất bằng Bearer Authorization Header
 * - Đăng xuất bằng Request Body (token)
 * - Đăng xuất khi không truyền token (xử lý graceful)
 * - Vòng đời End-to-End: Login -> Thu nhận Token -> Gọi API -> Logout -> Xác minh Token bị thu hồi (Revoked)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Login and Logout API Comprehensive Tests — KAN-64")
class LoginLogoutApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    private static final String PASSENGER_USERNAME = "huy.tran";
    private static final String PASSENGER_EMAIL = "huy.tran@cruise.com";
    private static final String VALID_PASSWORD = "Password@2026!";

    private static final String ADMIN_USERNAME = "admin.cruise";
    private static final String ADMIN_EMAIL = "admin@cruise.com";
    private static final String ADMIN_PASSWORD = "Admin#Secure2026";

    private static final String POS_USERNAME = "pos.staff";
    private static final String POS_EMAIL = "pos.staff@cruise.com";
    private static final String POS_PASSWORD = "Pos@Staff2026";

    private static final String INACTIVE_USERNAME = "inactive.user";
    private static final String INACTIVE_EMAIL = "inactive@cruise.com";

    @BeforeEach
    void setupTestData() {
        userRepository.deleteAll();

        // 1. Tạo tài khoản Passenger hợp lệ
        User passenger = User.builder()
                .username(PASSENGER_USERNAME)
                .email(PASSENGER_EMAIL)
                .fullName("Trần Gia Huy")
                .password(passwordService.hashPassword(VALID_PASSWORD))
                .role(Role.PASSENGER)
                .cabinNumber("Cabin-808")
                .rfidTag("RFID-88102")
                .active(true)
                .build();
        userRepository.save(passenger);

        // 2. Tạo tài khoản System Admin hợp lệ
        User admin = User.builder()
                .username(ADMIN_USERNAME)
                .email(ADMIN_EMAIL)
                .fullName("System Administrator")
                .password(passwordService.hashPassword(ADMIN_PASSWORD))
                .role(Role.SYSTEM_ADMIN)
                .active(true)
                .build();
        userRepository.save(admin);

        // 3. Tạo tài khoản POS Staff hợp lệ
        User posStaff = User.builder()
                .username(POS_USERNAME)
                .email(POS_EMAIL)
                .fullName("POS Counter Staff")
                .password(passwordService.hashPassword(POS_PASSWORD))
                .role(Role.POS_STAFF)
                .active(true)
                .build();
        userRepository.save(posStaff);

        // 4. Tạo tài khoản bị khóa / vô hiệu hóa (active = false)
        User inactiveUser = User.builder()
                .username(INACTIVE_USERNAME)
                .email(INACTIVE_EMAIL)
                .fullName("Banned User")
                .password(passwordService.hashPassword(VALID_PASSWORD))
                .role(Role.PASSENGER)
                .active(false)
                .build();
        userRepository.save(inactiveUser);
    }

    // =========================================================================
    // 1. CÁC KỊCH BẢN KIỂM THỬ API LOGIN THÀNH CÔNG (SUCCESS SCENARIOS)
    // =========================================================================
    @Nested
    @DisplayName("1. Login API — Success Scenarios")
    class LoginSuccessTests {

        @Test
        @DisplayName("TC-LOG-01: Đăng nhập thành công với Username chính xác và mật khẩu hợp lệ")
        void shouldLoginSuccessfullyWithValidUsername() throws Exception {
            String jsonPayload = createLoginJson(PASSENGER_USERNAME, VALID_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Login successful"))
                    .andExpect(jsonPath("$.data.accessToken").isString())
                    .andExpect(jsonPath("$.data.refreshToken").isString())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").isNumber())
                    .andExpect(jsonPath("$.data.user.username").value(PASSENGER_USERNAME))
                    .andExpect(jsonPath("$.data.user.email").value(PASSENGER_EMAIL))
                    .andExpect(jsonPath("$.data.user.role").value("PASSENGER"))
                    .andExpect(jsonPath("$.data.user.cabinNumber").value("Cabin-808"));
        }

        @Test
        @DisplayName("TC-LOG-02: Đăng nhập thành công với Email chính xác và mật khẩu hợp lệ")
        void shouldLoginSuccessfullyWithValidEmail() throws Exception {
            String jsonPayload = createLoginJson(PASSENGER_EMAIL, VALID_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.user.username").value(PASSENGER_USERNAME));
        }

        @Test
        @DisplayName("TC-LOG-03: Đăng nhập thành công với tài khoản Quản trị viên (SYSTEM_ADMIN)")
        void shouldLoginSuccessfullyWithAdminAccount() throws Exception {
            String jsonPayload = createLoginJson(ADMIN_USERNAME, ADMIN_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.user.role").value("SYSTEM_ADMIN"));
        }

        @Test
        @DisplayName("TC-LOG-04: Đăng nhập thành công với tài khoản Nhân viên POS (POS_STAFF)")
        void shouldLoginSuccessfullyWithPosStaffAccount() throws Exception {
            String jsonPayload = createLoginJson(POS_USERNAME, POS_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.user.role").value("POS_STAFF"));
        }

        @Test
        @DisplayName("TC-LOG-05: Đăng nhập thành công và Access Token trả về có thể xác thực tính hợp lệ")
        void shouldReturnValidAccessTokenUsableForSubsequentRequests() throws Exception {
            String jsonPayload = createLoginJson(PASSENGER_USERNAME, VALID_PASSWORD);

            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.data.accessToken");
            assertThat(accessToken).isNotBlank();

            // Gọi API /api/auth/me với token nhận được
            mockMvc.perform(get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value(PASSENGER_USERNAME))
                    .andExpect(jsonPath("$.data.email").value(PASSENGER_EMAIL));
        }
    }

    // =========================================================================
    // 2. CÁC KỊCH BẢN KIỂM THỬ API LOGIN THẤT BẠI (FAILURE SCENARIOS)
    // =========================================================================
    @Nested
    @DisplayName("2. Login API — Failure Scenarios")
    class LoginFailureTests {

        @Test
        @DisplayName("TC-LOG-FAIL-01: Đăng nhập thất bại khi nhập sai mật khẩu (HTTP 401)")
        void shouldFailLoginWithIncorrectPassword() throws Exception {
            String jsonPayload = createLoginJson(PASSENGER_USERNAME, "WrongPassword123@!");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("TC-LOG-FAIL-02: Đăng nhập thất bại khi Username không tồn tại (HTTP 401)")
        void shouldFailLoginWithNonExistentUsername() throws Exception {
            String jsonPayload = createLoginJson("ghost.user.notfound", VALID_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("TC-LOG-FAIL-03: Đăng nhập thất bại khi Email không tồn tại (HTTP 401)")
        void shouldFailLoginWithNonExistentEmail() throws Exception {
            String jsonPayload = createLoginJson("nonexistent@cruise.com", VALID_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("TC-LOG-FAIL-04: Đăng nhập thất bại khi tài khoản bị khóa/vô hiệu hóa (HTTP 403)")
        void shouldFailLoginWithInactiveAccount() throws Exception {
            String jsonPayload = createLoginJson(INACTIVE_USERNAME, VALID_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Account is inactive or disabled"));
        }

        @Test
        @DisplayName("TC-LOG-FAIL-05: Đăng nhập thất bại khi bỏ trống Username/Email (HTTP 400)")
        void shouldFailLoginWithBlankUsername() throws Exception {
            String jsonPayload = createLoginJson("   ", VALID_PASSWORD);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-LOG-FAIL-06: Đăng nhập thất bại khi bỏ trống Mật khẩu (HTTP 400)")
        void shouldFailLoginWithBlankPassword() throws Exception {
            String jsonPayload = createLoginJson(PASSENGER_USERNAME, "");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-LOG-FAIL-07: Đăng nhập thất bại khi Request Body rỗng {} (HTTP 400)")
        void shouldFailLoginWithEmptyBody() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-LOG-FAIL-08: Đăng nhập thất bại khi gửi JSON sai định dạng (HTTP 400)")
        void shouldFailLoginWithMalformedJson() throws Exception {
            String malformedJson = "{ \"usernameOrEmail\": \"huy.tran\", \"password\": ";

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(malformedJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-LOG-FAIL-09: Gọi sai HTTP Method tới /api/auth/login trả về 405 Method Not Allowed")
        void shouldReturn405WhenUsingGetOnLoginEndpoint() throws Exception {
            mockMvc.perform(get("/api/auth/login"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // =========================================================================
    // 3. CÁC KỊCH BẢN KIỂM THỬ API LOGOUT THÀNH CÔNG (SUCCESS SCENARIOS)
    // =========================================================================
    @Nested
    @DisplayName("3. Logout API — Success Scenarios")
    class LogoutSuccessTests {

        @Test
        @DisplayName("TC-OUT-01: Đăng xuất thành công với Authorization Header chứa Bearer token (HTTP 200)")
        void shouldLogoutSuccessfullyWithAuthorizationHeader() throws Exception {
            String token = obtainAccessToken(PASSENGER_USERNAME, VALID_PASSWORD);

            mockMvc.perform(post("/api/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Logout successful"));
        }

        @Test
        @DisplayName("TC-OUT-02: Đăng xuất thành công khi truyền token trong JSON Request Body (HTTP 200)")
        void shouldLogoutSuccessfullyWithRequestBodyToken() throws Exception {
            String token = obtainAccessToken(PASSENGER_USERNAME, VALID_PASSWORD);
            String jsonPayload = "{\"token\":\"" + token + "\"}";

            mockMvc.perform(post("/api/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Logout successful"));
        }

        @Test
        @DisplayName("TC-OUT-03: Đăng xuất thành công khi truyền cả Header và Body (Header được ưu tiên)")
        void shouldLogoutSuccessfullyWithBothHeaderAndBody() throws Exception {
            String token1 = obtainAccessToken(PASSENGER_USERNAME, VALID_PASSWORD);
            String jsonPayload = "{\"token\":\"other-token\"}";

            mockMvc.perform(post("/api/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("TC-OUT-04: Đăng xuất an toàn khi không gửi token (xử lý graceful không crash, HTTP 200)")
        void shouldHandleLogoutGracefullyWithoutToken() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Logout successful"));
        }

        @Test
        @DisplayName("TC-OUT-05: Đăng xuất an toàn khi gửi Authorization Header rỗng/khoảng trắng (HTTP 200)")
        void shouldHandleLogoutWithBlankAuthorizationHeader() throws Exception {
            mockMvc.perform(post("/api/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "   "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("TC-OUT-06: Đăng xuất an toàn khi gửi raw token (không có tiền tố Bearer)")
        void shouldLogoutSuccessfullyWithRawTokenInHeader() throws Exception {
            String token = obtainAccessToken(PASSENGER_USERNAME, VALID_PASSWORD);

            mockMvc.perform(post("/api/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // =========================================================================
    // 4. KIỂM THỬ VÒNG ĐỜI END-TO-END (LOGIN -> VERIFY -> LOGOUT -> REVOKED)
    // =========================================================================
    @Nested
    @DisplayName("4. End-to-End Lifecycle: Login -> Verify Token -> Logout -> Access Denied")
    class EndToEndLifecycleTests {

        @Test
        @DisplayName("TC-E2E-01: Token bị thu hồi sau Logout và không thể sử dụng để truy cập tài nguyên bảo vệ")
        void shouldRevokeTokenOnLogoutAndRejectSubsequentAccess() throws Exception {
            // Bước 1: Login để nhận Access Token
            String token = obtainAccessToken(PASSENGER_USERNAME, VALID_PASSWORD);

            // Bước 2: Kiểm tra truy cập /api/auth/me thành công trước khi Logout
            mockMvc.perform(get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value(PASSENGER_USERNAME));

            // Bước 3: Thực hiện Logout với token này
            mockMvc.perform(post("/api/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logout successful"));

            // Bước 4: Kiểm tra token qua endpoint /api/auth/verify-token -> trạng thái REVOKED / invalid
            mockMvc.perform(post("/api/auth/verify-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + token + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valid").value(false))
                    .andExpect(jsonPath("$.data.status").value("REVOKED"));

            // Bước 5: Gọi lại /api/auth/me với token đã thu hồi -> Phải bị từ chối HTTP 401 UNAUTHORIZED
            mockMvc.perform(get("/api/auth/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid or expired session token"));
        }

        @Test
        @DisplayName("TC-E2E-02: Đăng xuất với token đã thu hồi vẫn thành công (Idempotent Logout)")
        void shouldHandleMultipleLogoutsIdempotently() throws Exception {
            String token = obtainAccessToken(PASSENGER_USERNAME, VALID_PASSWORD);

            // Logout lần 1
            mockMvc.perform(post("/api/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk());

            // Logout lần 2 với cùng token -> Vẫn trả về 200 OK (idempotent, an toàn)
            mockMvc.perform(post("/api/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("TC-OUT-FAIL-01: Gọi sai HTTP Method tới /api/auth/logout trả về 405 Method Not Allowed")
        void shouldReturn405WhenUsingGetOnLogoutEndpoint() throws Exception {
            mockMvc.perform(get("/api/auth/logout"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    private String createLoginJson(String usernameOrEmail, String password) {
        return String.format("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}", usernameOrEmail, password);
    }

    private String obtainAccessToken(String usernameOrEmail, String password) throws Exception {
        String jsonPayload = createLoginJson(usernameOrEmail, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}

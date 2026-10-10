package com.cruise.auth.service;

import com.cruise.auth.dto.*;
import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit Test Suite kiểm thử chuyên sâu ở tầng Service & Crypto:
 * - Xử lý thông tin đăng nhập sai (Invalid Credentials)
 * - Xử lý token hết hạn (Expired Tokens)
 * - Các kịch bản biên về chữ ký, thời gian, giải mã và thu hồi token
 *
 * Jira Task: KAN-65 — Test Invalid Credentials and Expired Tokens
 * Jira Story: KAN-37 — Authentication and User Management
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KAN-65: Invalid Credentials and Expired Tokens Service & Crypto Unit Tests")
class InvalidCredentialsAndExpiredTokensUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private User inactiveUser;
    private User nullPasswordUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .username("officer.david")
                .fullName("David Nguyen")
                .email("david.nguyen@cruise.com")
                .password("$2a$10$hashedPasswordSampleString1234567890")
                .role(Role.ITINERARY_COORDINATOR)
                .active(true)
                .build();
        sampleUser.setId(10L);

        inactiveUser = User.builder()
                .username("inactive.david")
                .fullName("Inactive David")
                .email("inactive.david@cruise.com")
                .password("$2a$10$hashedPasswordSampleString1234567890")
                .role(Role.ITINERARY_COORDINATOR)
                .active(false)
                .build();
        inactiveUser.setId(11L);

        nullPasswordUser = User.builder()
                .username("nullpass.david")
                .fullName("Null Password David")
                .email("nullpass.david@cruise.com")
                .password(null)
                .role(Role.PASSENGER)
                .active(true)
                .build();
        nullPasswordUser.setId(12L);
    }

    // =========================================================================
    // 1. UNIT TESTS: THÔNG TIN ĐĂNG NHẬP SAI (INVALID CREDENTIALS IN SERVICE)
    // =========================================================================
    @Nested
    @DisplayName("1. Service Level Invalid Credentials Tests")
    class ServiceInvalidCredentialsTests {

        @Test
        @DisplayName("1.1. AuthService.login quăng IllegalArgumentException khi không tìm thấy username hoặc email")
        void shouldThrowWhenUserNotFound() {
            LoginRequest req = new LoginRequest("nonexistent", "SomePass123@");
            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid username or password");
        }

        @Test
        @DisplayName("1.2. AuthService.login quăng IllegalArgumentException khi mật khẩu không trùng khớp")
        void shouldThrowWhenPasswordMismatch() {
            LoginRequest req = new LoginRequest("officer.david", "WrongPassword123@");
            when(userRepository.findByUsername("officer.david")).thenReturn(Optional.of(sampleUser));
            when(passwordService.verifyPassword("WrongPassword123@", sampleUser.getPassword())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid username or password");
        }

        @Test
        @DisplayName("1.3. AuthService.login quăng IllegalStateException khi tài khoản bị khóa (inactive)")
        void shouldThrowWhenAccountInactive() {
            LoginRequest req = new LoginRequest("inactive.david", "ValidPass123@");
            when(userRepository.findByUsername("inactive.david")).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Account is inactive or disabled");

            verify(passwordService, never()).verifyPassword(anyString(), anyString());
        }

        @Test
        @DisplayName("1.4. AuthService.login quăng IllegalArgumentException khi user có password null trong DB")
        void shouldThrowWhenUserHasNullPasswordInDb() {
            LoginRequest req = new LoginRequest("nullpass.david", "ValidPass123@");
            when(userRepository.findByUsername("nullpass.david")).thenReturn(Optional.of(nullPasswordUser));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid username or password");
        }

        @Test
        @DisplayName("1.5. AuthService.verifyPassword trả về false khi mật khẩu sai hoặc user không tồn tại")
        void shouldReturnFalseForInvalidVerifyPassword() {
            // Sai mật khẩu
            VerifyPasswordRequest req1 = new VerifyPasswordRequest("officer.david", "WrongPass123@");
            when(userRepository.findByUsername("officer.david")).thenReturn(Optional.of(sampleUser));
            when(passwordService.verifyPassword("WrongPass123@", sampleUser.getPassword())).thenReturn(false);
            assertThat(authService.verifyPassword(req1)).isFalse();

            // Không tìm thấy user
            VerifyPasswordRequest req2 = new VerifyPasswordRequest("unknown", "AnyPass123@");
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("unknown")).thenReturn(Optional.empty());
            assertThat(authService.verifyPassword(req2)).isFalse();

            // Password null trong DB
            VerifyPasswordRequest req3 = new VerifyPasswordRequest("nullpass.david", "AnyPass123@");
            when(userRepository.findByUsername("nullpass.david")).thenReturn(Optional.of(nullPasswordUser));
            assertThat(authService.verifyPassword(req3)).isFalse();
        }

        @Test
        @DisplayName("1.6. AuthService.changePassword từ chối khi mật khẩu cũ không chính xác")
        void shouldRejectChangePasswordWhenOldPasswordWrong() {
            ChangePasswordRequest req = new ChangePasswordRequest(
                    "WrongOldPass@", "NewPass123@", "NewPass123@"
            );
            when(userRepository.findByUsername("officer.david")).thenReturn(Optional.of(sampleUser));
            when(passwordService.verifyPassword("WrongOldPass@", sampleUser.getPassword())).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword("officer.david", req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Mật khẩu hiện tại không chính xác");
        }

        @Test
        @DisplayName("1.7. AuthService.changePassword từ chối khi mật khẩu mới trùng mật khẩu cũ")
        void shouldRejectChangePasswordWhenNewPasswordSameAsOld() {
            ChangePasswordRequest req = new ChangePasswordRequest(
                    "SamePass123@", "SamePass123@", "SamePass123@"
            );
            when(userRepository.findByUsername("officer.david")).thenReturn(Optional.of(sampleUser));
            when(passwordService.verifyPassword("SamePass123@", sampleUser.getPassword())).thenReturn(true);

            assertThatThrownBy(() -> authService.changePassword("officer.david", req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Mật khẩu mới không được trùng với mật khẩu cũ");
        }

        @Test
        @DisplayName("1.8. AuthService.changePassword từ chối khi xác nhận mật khẩu không khớp")
        void shouldRejectChangePasswordWhenConfirmPasswordMismatch() {
            ChangePasswordRequest req = new ChangePasswordRequest(
                    "OldPass123@", "NewPass123@", "DifferentPass456@"
            );
            when(userRepository.findByUsername("officer.david")).thenReturn(Optional.of(sampleUser));
            when(passwordService.verifyPassword("OldPass123@", sampleUser.getPassword())).thenReturn(true);

            assertThatThrownBy(() -> authService.changePassword("officer.david", req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Mật khẩu xác nhận không trùng khớp");
        }

        @Test
        @DisplayName("1.9. AuthService.changePassword từ chối khi tài khoản bị khóa")
        void shouldRejectChangePasswordWhenUserInactive() {
            ChangePasswordRequest req = new ChangePasswordRequest(
                    "OldPass123@", "NewPass123@", "NewPass123@"
            );
            when(userRepository.findByUsername("inactive.david")).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> authService.changePassword("inactive.david", req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Account is inactive or disabled");
        }
    }

    // =========================================================================
    // 2. UNIT TESTS: TOKEN HẾT HẠN & BẤT HỢP LỆ (EXPIRED TOKENS IN SERVICE)
    // =========================================================================
    @Nested
    @DisplayName("2. Service Level Expired & Invalid Token Handling")
    class ServiceExpiredTokensTests {

        @Test
        @DisplayName("2.1. AuthService.refreshToken từ chối khi refresh token hết hạn")
        void shouldRejectRefreshTokenWhenExpired() {
            RefreshTokenRequest req = new RefreshTokenRequest("expired-token-string");

            VerifyTokenResponse expiredResp = VerifyTokenResponse.builder()
                    .valid(false)
                    .status("EXPIRED")
                    .message("Token has expired")
                    .build();

            when(jwtService.introspectToken("expired-token-string")).thenReturn(expiredResp);

            assertThatThrownBy(() -> authService.refreshToken(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid refresh token")
                    .hasMessageContaining("Token has expired");
        }

        @Test
        @DisplayName("2.2. AuthService.refreshToken từ chối khi refresh token rỗng hoặc null")
        void shouldRejectRefreshTokenWhenBlankOrNull() {
            assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest(null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Refresh token must not be blank");

            assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("   ")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Refresh token must not be blank");
        }

        @Test
        @DisplayName("2.3. AuthService.refreshToken từ chối khi đưa Access Token vào endpoint refresh")
        void shouldRejectRefreshTokenWhenTokenTypeIsAccess() {
            RefreshTokenRequest req = new RefreshTokenRequest("valid-access-token-string");

            VerifyTokenResponse accessResp = VerifyTokenResponse.builder()
                    .valid(true)
                    .status("VALID")
                    .tokenType("ACCESS")
                    .username("officer.david")
                    .build();

            when(jwtService.introspectToken("valid-access-token-string")).thenReturn(accessResp);

            assertThatThrownBy(() -> authService.refreshToken(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Provided token is not a valid refresh token");
        }

        @Test
        @DisplayName("2.4. AuthService.refreshToken từ chối khi tài khoản người dùng đã bị khóa")
        void shouldRejectRefreshTokenWhenUserIsInactive() {
            RefreshTokenRequest req = new RefreshTokenRequest("valid-refresh-token-string");

            VerifyTokenResponse refreshResp = VerifyTokenResponse.builder()
                    .valid(true)
                    .status("VALID")
                    .tokenType("REFRESH")
                    .username("inactive.david")
                    .build();

            when(jwtService.introspectToken("valid-refresh-token-string")).thenReturn(refreshResp);
            when(userRepository.findByUsername("inactive.david")).thenReturn(Optional.of(inactiveUser));

            assertThatThrownBy(() -> authService.refreshToken(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Account is inactive or disabled");
        }

        @Test
        @DisplayName("2.5. AuthService.getCurrentUser trả về Optional.empty khi token hết hạn")
        void shouldReturnEmptyUserWhenTokenIsExpired() {
            VerifyTokenResponse expiredResp = VerifyTokenResponse.builder()
                    .valid(false)
                    .status("EXPIRED")
                    .message("Token has expired")
                    .build();

            when(jwtService.introspectToken("expired-token")).thenReturn(expiredResp);

            Optional<User> result = authService.getCurrentUser("expired-token");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("2.6. AuthService.getCurrentUser trả về Optional.empty khi token không có username")
        void shouldReturnEmptyUserWhenTokenHasNoUsername() {
            VerifyTokenResponse validWithoutUsername = VerifyTokenResponse.builder()
                    .valid(true)
                    .username(null)
                    .build();

            when(jwtService.introspectToken("some-token")).thenReturn(validWithoutUsername);

            Optional<User> result = authService.getCurrentUser("some-token");
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // 3. UNIT TESTS: THUẬT TOÁN MÃ HÓA, THỜI GIAN & PARSING (JWTSERVICE ENGINE)
    // =========================================================================
    @Nested
    @DisplayName("3. JwtService Cryptographic & Expiration Engine Tests")
    class JwtServiceEngineTests {

        private JwtService regularJwtService;
        private JwtService expiredJwtService;

        @BeforeEach
        void initJwtServices() {
            regularJwtService = new JwtService(
                    "custom-secret-key-for-test-unit-2026-very-secure",
                    3600000L,
                    86400000L,
                    "cruise-auth-test"
            );

            // Thời hạn âm -> Token sinh ra đã hết hạn ngay tức thì
            expiredJwtService = new JwtService(
                    "custom-secret-key-for-test-unit-2026-very-secure",
                    -1000L,
                    -5000L,
                    "cruise-auth-test"
            );
        }

        @Test
        @DisplayName("3.1. JwtService tạo token hết hạn và phát hiện chính xác trạng thái EXPIRED")
        void shouldAccuratelyDetectExpiredToken() {
            String expiredToken = expiredJwtService.generateAccessToken(sampleUser);

            assertThat(regularJwtService.validateToken(expiredToken)).isFalse();
            assertThat(regularJwtService.validateAccessToken(expiredToken)).isFalse();

            VerifyTokenResponse introspection = regularJwtService.introspectToken(expiredToken);
            assertThat(introspection.isValid()).isFalse();
            assertThat(introspection.getStatus()).isEqualTo("EXPIRED");
            assertThat(introspection.getMessage()).isEqualTo("Token has expired");
            assertThat(introspection.getRemainingSeconds()).isZero();
            // Các claims định danh vẫn được trích xuất để kiểm tra nguồn gốc của token
            assertThat(introspection.getUsername()).isEqualTo("officer.david");
            assertThat(introspection.getRole()).isEqualTo("ITINERARY_COORDINATOR");
        }

        @Test
        @DisplayName("3.2. JwtService xử lý Refresh Token hết hạn chính xác")
        void shouldAccuratelyDetectExpiredRefreshToken() {
            String expiredRefreshToken = expiredJwtService.generateRefreshToken(sampleUser);

            assertThat(regularJwtService.validateRefreshToken(expiredRefreshToken)).isFalse();

            VerifyTokenResponse introspection = regularJwtService.introspectToken(expiredRefreshToken);
            assertThat(introspection.isValid()).isFalse();
            assertThat(introspection.getStatus()).isEqualTo("EXPIRED");
            assertThat(introspection.getTokenType()).isEqualTo("REFRESH");
        }

        @Test
        @DisplayName("3.3. JwtService từ chối các chuỗi token dị dạng và thiếu")
        void shouldRejectMalformedTokens() {
            VerifyTokenResponse res1 = regularJwtService.introspectToken(null);
            assertThat(res1.isValid()).isFalse();
            assertThat(res1.getStatus()).isEqualTo("MISSING");

            VerifyTokenResponse res2 = regularJwtService.introspectToken("");
            assertThat(res2.isValid()).isFalse();
            assertThat(res2.getStatus()).isEqualTo("MISSING");

            VerifyTokenResponse res3 = regularJwtService.introspectToken("only-one-part");
            assertThat(res3.isValid()).isFalse();
            assertThat(res3.getStatus()).isEqualTo("MALFORMED");

            VerifyTokenResponse res4 = regularJwtService.introspectToken("header.payload");
            assertThat(res4.isValid()).isFalse();
            assertThat(res4.getStatus()).isEqualTo("MALFORMED");

            VerifyTokenResponse res5 = regularJwtService.introspectToken("header.payload.signature.extra");
            assertThat(res5.isValid()).isFalse();
            assertThat(res5.getStatus()).isEqualTo("MALFORMED");
        }

        @Test
        @DisplayName("3.4. JwtService từ chối token khi chữ ký bị chỉnh sửa dù chỉ 1 ký tự")
        void shouldRejectTamperedSignature() {
            String token = regularJwtService.generateAccessToken(sampleUser);
            String[] parts = token.split("\\.");
            String corruptedSignature = parts[2].substring(0, parts[2].length() - 1) + "X";
            String corruptedToken = parts[0] + "." + parts[1] + "." + corruptedSignature;

            VerifyTokenResponse res = regularJwtService.introspectToken(corruptedToken);
            assertThat(res.isValid()).isFalse();
            assertThat(res.getStatus()).isEqualTo("INVALID_SIGNATURE");
        }

        @Test
        @DisplayName("3.5. JwtService đánh dấu REVOKED khi token còn hạn bị đưa vào blacklist và hỗ trợ clearRevocations")
        void shouldHandleInvalidatedTokenAndClearRevocations() {
            String validToken = regularJwtService.generateAccessToken(sampleUser);
            assertThat(regularJwtService.validateToken(validToken)).isTrue();

            // Đưa token vào blacklist
            regularJwtService.invalidateToken(validToken);
            assertThat(regularJwtService.isTokenInvalidated(validToken)).isTrue();

            VerifyTokenResponse resRevoked = regularJwtService.introspectToken(validToken);
            assertThat(resRevoked.isValid()).isFalse();
            assertThat(resRevoked.getStatus()).isEqualTo("REVOKED");

            // Reset cache và kiểm tra token không còn nằm trong blacklist
            regularJwtService.clearRevocations();
            assertThat(regularJwtService.isTokenInvalidated(validToken)).isFalse();
            assertThat(regularJwtService.introspectToken(validToken).isValid()).isTrue();
        }

        @Test
        @DisplayName("3.6. User-level cutoff: Token sinh trước thời điểm thu hồi user bị coi là REVOKED")
        void shouldRevokeTokenGeneratedBeforeUserCutoff() throws InterruptedException {
            String tokenBeforeCutoff = regularJwtService.generateAccessToken(sampleUser);
            assertThat(regularJwtService.validateToken(tokenBeforeCutoff)).isTrue();

            // Đợi 10ms để mốc cutoff lớn hơn iat
            Thread.sleep(10);
            regularJwtService.revokeAllUserTokens("officer.david");

            VerifyTokenResponse res = regularJwtService.introspectToken(tokenBeforeCutoff);
            assertThat(res.isValid()).isFalse();
            assertThat(res.getStatus()).isEqualTo("REVOKED");
            assertThat(res.getMessage()).contains("password update or user session reset");
        }

        @Test
        @DisplayName("3.7. Token trích xuất đầy đủ thông tin thời gian iat, exp và remainingSeconds")
        void shouldExtractTimestampsCorrectly() {
            String token = regularJwtService.generateAccessToken(sampleUser);

            long iat = regularJwtService.extractIat(token);
            long exp = regularJwtService.extractExp(token);

            assertThat(iat).isPositive();
            assertThat(exp).isGreaterThan(iat);

            VerifyTokenResponse res = regularJwtService.introspectToken(token);
            assertThat(res.isValid()).isTrue();
            assertThat(res.getIssuedAt()).isEqualTo(iat);
            assertThat(res.getExpiresAt()).isEqualTo(exp);
            assertThat(res.getRemainingSeconds()).isPositive();
        }
    }
}

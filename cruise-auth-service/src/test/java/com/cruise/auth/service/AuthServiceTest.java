package com.cruise.auth.service;

import com.cruise.auth.dto.*;
import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests with Mockito")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private TokenPair sampleTokenPair;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .username("hoang.nam")
                .fullName("Hoàng Văn Nam")
                .email("hoang.nam@cruise.com")
                .password("$2a$10$hashedPasswordValue")
                .role(Role.ITINERARY_COORDINATOR)
                .active(true)
                .build();
        sampleUser.setId(1L);

        sampleTokenPair = TokenPair.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .refreshExpiresIn(604800L)
                .build();
    }

    // --- 1. LOGIN TESTS ---

    @Test
    @DisplayName("Should login successfully with valid username and password")
    void shouldLoginSuccessfullyWithUsername() {
        LoginRequest request = new LoginRequest("hoang.nam", "ValidPassword123@");

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("ValidPassword123@", sampleUser.getPassword())).thenReturn(true);
        when(jwtService.generateTokenPair(sampleUser)).thenReturn(sampleTokenPair);

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");
        assertThat(response.getUser().getUsername()).isEqualTo("hoang.nam");

        verify(userRepository, times(1)).findByUsername("hoang.nam");
        verify(passwordService, times(1)).verifyPassword("ValidPassword123@", sampleUser.getPassword());
        verify(jwtService, times(1)).generateTokenPair(sampleUser);
    }

    @Test
    @DisplayName("Should login successfully with valid email and password")
    void shouldLoginSuccessfullyWithEmail() {
        LoginRequest request = new LoginRequest("hoang.nam@cruise.com", "ValidPassword123@");

        when(userRepository.findByUsername("hoang.nam@cruise.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("hoang.nam@cruise.com")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("ValidPassword123@", sampleUser.getPassword())).thenReturn(true);
        when(jwtService.generateTokenPair(sampleUser)).thenReturn(sampleTokenPair);

        LoginResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getUser().getEmail()).isEqualTo("hoang.nam@cruise.com");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when username/email not found")
    void shouldThrowExceptionWhenUserNotFound() {
        LoginRequest request = new LoginRequest("unknown.user", "ValidPassword123@");

        when(userRepository.findByUsername("unknown.user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown.user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when account is inactive")
    void shouldThrowExceptionWhenAccountInactive() {
        sampleUser.setActive(false);
        LoginRequest request = new LoginRequest("hoang.nam", "ValidPassword123@");

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account is inactive or disabled");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when password does not match")
    void shouldThrowExceptionWhenPasswordMismatch() {
        LoginRequest request = new LoginRequest("hoang.nam", "WrongPassword123@");

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("WrongPassword123@", sampleUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid username or password");
    }

    // --- 2. REFRESH TOKEN TESTS ---

    @Test
    @DisplayName("Should refresh token successfully with valid refresh token (Token Rotation)")
    void shouldRefreshTokenSuccessfully() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");

        VerifyTokenResponse verifyRes = VerifyTokenResponse.builder()
                .valid(true)
                .status("VALID")
                .tokenType("REFRESH")
                .username("hoang.nam")
                .build();

        when(jwtService.introspectToken("valid-refresh-token")).thenReturn(verifyRes);
        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateTokenPair(sampleUser)).thenReturn(sampleTokenPair);

        LoginResponse response = authService.refreshToken(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");

        verify(jwtService, times(1)).invalidateToken("valid-refresh-token");
        verify(jwtService, times(1)).generateTokenPair(sampleUser);
    }

    @Test
    @DisplayName("Should throw exception when refresh token is invalid or expired")
    void shouldFailRefreshWhenTokenInvalid() {
        RefreshTokenRequest request = new RefreshTokenRequest("expired-token");

        VerifyTokenResponse verifyRes = VerifyTokenResponse.builder()
                .valid(false)
                .status("EXPIRED")
                .message("Token has expired")
                .build();

        when(jwtService.introspectToken("expired-token")).thenReturn(verifyRes);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("Should throw exception when token provided for refresh is an access token")
    void shouldFailRefreshWhenTokenTypeMismatch() {
        RefreshTokenRequest request = new RefreshTokenRequest("an-access-token");

        VerifyTokenResponse verifyRes = VerifyTokenResponse.builder()
                .valid(true)
                .tokenType("ACCESS")
                .username("hoang.nam")
                .build();

        when(jwtService.introspectToken("an-access-token")).thenReturn(verifyRes);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid refresh token");
    }

    // --- 3. CHANGE PASSWORD TESTS ---

    @Test
    @DisplayName("Should change password successfully, save new hash, and revoke existing tokens")
    void shouldChangePasswordSuccessfully() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "CurrentPass123@", "NewSecurePass456#", "NewSecurePass456#"
        );

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("CurrentPass123@", sampleUser.getPassword())).thenReturn(true);
        when(passwordService.hashPassword("NewSecurePass456#")).thenReturn("$2a$10$newHashedPassword");

        authService.changePassword("hoang.nam", request);

        assertThat(sampleUser.getPassword()).isEqualTo("$2a$10$newHashedPassword");
        verify(userRepository, times(1)).save(sampleUser);
        verify(jwtService, times(1)).revokeAllUserTokens("hoang.nam");
    }

    @Test
    @DisplayName("Should reject change password when current password is wrong")
    void shouldFailChangePasswordWhenOldPasswordWrong() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "WrongOldPass123@", "NewSecurePass456#", "NewSecurePass456#"
        );

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("WrongOldPass123@", sampleUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("hoang.nam", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mật khẩu hiện tại không chính xác");
    }

    @Test
    @DisplayName("Should reject change password when new password equals old password")
    void shouldFailChangePasswordWhenNewPassEqualsOldPass() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "SamePass123@", "SamePass123@", "SamePass123@"
        );

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("SamePass123@", sampleUser.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword("hoang.nam", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được trùng");
    }

    @Test
    @DisplayName("Should reject change password when confirm password does not match new password")
    void shouldFailChangePasswordWhenConfirmMismatch() {
        ChangePasswordRequest request = new ChangePasswordRequest(
                "CurrentPass123@", "NewSecurePass456#", "DifferentPass789!"
        );

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("CurrentPass123@", sampleUser.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword("hoang.nam", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không trùng khớp");
    }

    // --- 4. VERIFY PASSWORD TESTS ---

    @Test
    @DisplayName("Should verify password return true when credentials match")
    void shouldVerifyPasswordReturnTrue() {
        VerifyPasswordRequest request = new VerifyPasswordRequest("hoang.nam", "ValidPassword123@");

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("ValidPassword123@", sampleUser.getPassword())).thenReturn(true);

        boolean verified = authService.verifyPassword(request);
        assertThat(verified).isTrue();
    }

    @Test
    @DisplayName("Should verify password return false when user not found")
    void shouldVerifyPasswordReturnFalseWhenUserNotFound() {
        VerifyPasswordRequest request = new VerifyPasswordRequest("unknown.user", "ValidPassword123@");

        when(userRepository.findByUsername("unknown.user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown.user")).thenReturn(Optional.empty());

        boolean verified = authService.verifyPassword(request);
        assertThat(verified).isFalse();
    }

    // --- 5. LOGOUT & CURRENT USER TESTS ---

    @Test
    @DisplayName("Should invalidate token on logout")
    void shouldLogoutSuccessfully() {
        authService.logout("Bearer some-token-string");
        verify(jwtService, times(1)).invalidateToken("Bearer some-token-string");
    }

    @Test
    @DisplayName("Should return user profile when current user token is valid")
    void shouldGetCurrentUserSuccessfully() {
        VerifyTokenResponse verifyRes = VerifyTokenResponse.builder()
                .valid(true)
                .username("hoang.nam")
                .build();

        when(jwtService.introspectToken("valid-token")).thenReturn(verifyRes);
        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));

        Optional<User> userOpt = authService.getCurrentUser("valid-token");
        assertThat(userOpt).isPresent();
        assertThat(userOpt.get().getUsername()).isEqualTo("hoang.nam");
    }

    @Test
    @DisplayName("Should return empty when current user token is invalid")
    void shouldReturnEmptyWhenTokenInvalid() {
        VerifyTokenResponse verifyRes = VerifyTokenResponse.builder()
                .valid(false)
                .build();

        when(jwtService.introspectToken("invalid-token")).thenReturn(verifyRes);

        Optional<User> userOpt = authService.getCurrentUser("invalid-token");
        assertThat(userOpt).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when token is valid but username claim is missing")
    void shouldReturnEmptyWhenTokenHasNoUsername() {
        VerifyTokenResponse verifyRes = VerifyTokenResponse.builder()
                .valid(true)
                .username(null)
                .build();

        when(jwtService.introspectToken("token-no-user")).thenReturn(verifyRes);

        Optional<User> userOpt = authService.getCurrentUser("token-no-user");
        assertThat(userOpt).isEmpty();
    }

    @Test
    @DisplayName("Should handle logout gracefully when token is null or blank")
    void shouldHandleLogoutWithNullOrBlankToken() {
        authService.logout(null);
        authService.logout("");
        authService.logout("   ");
        verify(jwtService, never()).invalidateToken(any());
    }

    // --- 6. TOKEN PAIR & REVOCATION TESTS ---

    @Test
    @DisplayName("Should issue token pair successfully for active user")
    void shouldIssueTokenPairSuccessfully() {
        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateTokenPair(sampleUser)).thenReturn(sampleTokenPair);

        TokenPair result = authService.issueTokenPair("hoang.nam");

        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo("mock-access-token");
        verify(jwtService, times(1)).generateTokenPair(sampleUser);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when issueTokenPair user not found")
    void shouldThrowExceptionWhenIssueTokenPairUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.issueTokenPair("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when issueTokenPair user is inactive")
    void shouldThrowExceptionWhenIssueTokenPairUserInactive() {
        sampleUser.setActive(false);
        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.issueTokenPair("hoang.nam"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account is inactive or disabled");
    }

    @Test
    @DisplayName("Should revoke token successfully")
    void shouldRevokeTokenSuccessfully() {
        RevokeTokenRequest request = RevokeTokenRequest.builder().token("token-to-revoke").build();

        authService.revokeToken(request);
        verify(jwtService, times(1)).invalidateToken("token-to-revoke");
    }

    @Test
    @DisplayName("Should ignore revoke token when request or token is null")
    void shouldIgnoreRevokeTokenWhenNull() {
        authService.revokeToken(null);
        authService.revokeToken(RevokeTokenRequest.builder().token(null).build());

        verify(jwtService, never()).invalidateToken(any());
    }

    @Test
    @DisplayName("Should verify token delegate to jwtService.introspectToken")
    void shouldVerifyTokenDelegate() {
        VerifyTokenResponse verifyRes = VerifyTokenResponse.builder().valid(true).status("VALID").build();
        when(jwtService.introspectToken("check-token")).thenReturn(verifyRes);

        VerifyTokenResponse result = authService.verifyToken("check-token");
        assertThat(result.isValid()).isTrue();
    }

    // --- 7. ADDITIONAL BRANCH COVERAGE TESTS ---

    @Test
    @DisplayName("Should login successfully when password matches plain text (legacy fallback)")
    void shouldLoginWithPlainTextPasswordMatch() {
        sampleUser.setPassword("RawPassword123@");
        LoginRequest request = new LoginRequest("hoang.nam", "RawPassword123@");

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("RawPassword123@", "RawPassword123@")).thenReturn(false);
        when(jwtService.generateTokenPair(sampleUser)).thenReturn(sampleTokenPair);

        LoginResponse response = authService.login(request);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception when login user has null password")
    void shouldFailLoginWhenUserPasswordIsNull() {
        sampleUser.setPassword(null);
        LoginRequest request = new LoginRequest("hoang.nam", "AnyPassword123@");

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should fail refresh token when token string is blank or null")
    void shouldFailRefreshWhenTokenBlankOrNull() {
        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest(null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> authService.refreshToken(new RefreshTokenRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should fail refresh token when user not found in repository")
    void shouldFailRefreshWhenUserNotFound() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        VerifyTokenResponse verifyRes = VerifyTokenResponse.builder()
                .valid(true)
                .tokenType("REFRESH")
                .username("nonexistent")
                .build();

        when(jwtService.introspectToken("valid-refresh-token")).thenReturn(verifyRes);
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should fail refresh token when user is inactive")
    void shouldFailRefreshWhenUserInactive() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        VerifyTokenResponse verifyRes = VerifyTokenResponse.builder()
                .valid(true)
                .tokenType("REFRESH")
                .username("hoang.nam")
                .build();
        sampleUser.setActive(false);

        when(jwtService.introspectToken("valid-refresh-token")).thenReturn(verifyRes);
        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account is inactive");
    }

    @Test
    @DisplayName("Should fail change password when user is not found")
    void shouldFailChangePasswordWhenUserNotFound() {
        ChangePasswordRequest request = new ChangePasswordRequest("Old1@", "New1@", "New1@");
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword("unknown", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("Should fail change password when user is inactive")
    void shouldFailChangePasswordWhenUserInactive() {
        sampleUser.setActive(false);
        ChangePasswordRequest request = new ChangePasswordRequest("Old1@", "New1@", "New1@");
        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> authService.changePassword("hoang.nam", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Account is inactive");
    }

    @Test
    @DisplayName("Should verify password return false when user has null password")
    void shouldVerifyPasswordReturnFalseWhenPasswordNull() {
        sampleUser.setPassword(null);
        VerifyPasswordRequest request = new VerifyPasswordRequest("hoang.nam", "Pass123@");

        when(userRepository.findByUsername("hoang.nam")).thenReturn(Optional.of(sampleUser));

        boolean result = authService.verifyPassword(request);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should verify password return true when looked up by email")
    void shouldVerifyPasswordReturnTrueWithEmail() {
        VerifyPasswordRequest request = new VerifyPasswordRequest("hoang.nam@cruise.com", "ValidPassword123@");

        when(userRepository.findByUsername("hoang.nam@cruise.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("hoang.nam@cruise.com")).thenReturn(Optional.of(sampleUser));
        when(passwordService.verifyPassword("ValidPassword123@", sampleUser.getPassword())).thenReturn(true);

        boolean result = authService.verifyPassword(request);
        assertThat(result).isTrue();
    }
}

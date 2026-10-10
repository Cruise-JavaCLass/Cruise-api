package com.cruise.auth.controller;

import com.cruise.auth.dto.*;
import com.cruise.auth.entity.User;
import com.cruise.auth.service.AuthService;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@DisplayName("AuthController MockMvc Web Layer Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("GET /api/auth/health returns UP status and supported roles")
    void testAuthHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/auth/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.service").value("cruise-auth-service"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.rolesSupported").isArray());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 OK and LoginResponse on valid credentials")
    void testLoginEndpoint_Success() throws Exception {
        User user = User.builder().username("admin").role(Role.SYSTEM_ADMIN).build();
        user.setId(1L);
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("mock-access-jwt")
                .refreshToken("mock-refresh-jwt")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(user)
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        String jsonBody = "{\"usernameOrEmail\":\"admin\",\"password\":\"Admin@1234\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-jwt"))
                .andExpect(jsonPath("$.data.user.username").value("admin"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 UNAUTHORIZED on invalid credentials")
    void testLoginEndpoint_InvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid username or password"));

        String jsonBody = "{\"usernameOrEmail\":\"admin\",\"password\":\"WrongPassword!\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 403 FORBIDDEN when account is inactive")
    void testLoginEndpoint_DisabledAccount() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalStateException("Account is inactive or disabled"));

        String jsonBody = "{\"usernameOrEmail\":\"admin\",\"password\":\"Admin@1234\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Account is inactive or disabled"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 BAD REQUEST when username is blank")
    void testLoginEndpoint_BlankUsername() throws Exception {
        String jsonBody = "{\"usernameOrEmail\":\"   \",\"password\":\"Admin@1234\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 BAD REQUEST when password is blank")
    void testLoginEndpoint_BlankPassword() throws Exception {
        String jsonBody = "{\"usernameOrEmail\":\"admin\",\"password\":\"\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 BAD REQUEST when body is empty")
    void testLoginEndpoint_EmptyBody() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 BAD REQUEST on malformed JSON")
    void testLoginEndpoint_MalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/auth/login returns 405 METHOD NOT ALLOWED")
    void testLoginEndpoint_MethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("POST /api/auth/logout returns 200 OK")
    void testLogoutEndpoint() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer sample-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Logout successful"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 200 OK when user token is valid")
    void testGetCurrentUser_Success() throws Exception {
        User user = User.builder().username("passenger1").role(Role.PASSENGER).build();
        user.setId(2L);
        when(authService.getCurrentUser("Bearer valid-token")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("passenger1"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 UNAUTHORIZED when Authorization header is missing")
    void testGetCurrentUser_MissingHeader() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/refresh-token returns 200 OK with new tokens on valid refresh")
    void testRefreshToken_Success() throws Exception {
        LoginResponse response = LoginResponse.builder()
                .accessToken("new-access-jwt")
                .refreshToken("new-refresh-jwt")
                .tokenType("Bearer")
                .build();

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(response);

        String jsonBody = "{\"refreshToken\":\"valid-refresh-token\"}";

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-jwt"));
    }

    @Test
    @DisplayName("POST /api/auth/verify-token returns 200 OK with introspection details")
    void testVerifyToken_Success() throws Exception {
        VerifyTokenResponse response = VerifyTokenResponse.builder()
                .valid(true)
                .status("VALID")
                .username("admin")
                .role("SYSTEM_ADMIN")
                .build();

        when(authService.verifyToken("test-token")).thenReturn(response);

        String jsonBody = "{\"token\":\"test-token\"}";

        mockMvc.perform(post("/api/auth/verify-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    @DisplayName("POST /api/auth/change-password returns 200 OK when password changed")
    void testChangePassword_Success() throws Exception {
        User user = User.builder().username("admin").build();
        user.setId(1L);
        when(authService.getCurrentUser("Bearer valid-token")).thenReturn(Optional.of(user));
        doNothing().when(authService).changePassword(eq("admin"), any(ChangePasswordRequest.class));

        String jsonBody = "{\"oldPassword\":\"OldPass123@\",\"newPassword\":\"NewPass456#\",\"confirmPassword\":\"NewPass456#\"}";

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/verify-password returns 200 OK with validation map")
    void testVerifyPasswordEndpoint() throws Exception {
        when(authService.verifyPassword(any(VerifyPasswordRequest.class))).thenReturn(true);

        String jsonBody = "{\"usernameOrEmail\":\"admin\",\"password\":\"Admin@1234\"}";

        mockMvc.perform(post("/api/auth/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/token/pair returns 200 OK with new TokenPair for authenticated user")
    void testIssueTokenPairEndpoint() throws Exception {
        User user = User.builder().username("admin").build();
        user.setId(1L);
        TokenPair pair = TokenPair.builder()
                .accessToken("new-access")
                .refreshToken("new-refresh")
                .build();

        when(authService.getCurrentUser("Bearer valid-token")).thenReturn(Optional.of(user));
        when(authService.issueTokenPair("admin")).thenReturn(pair);

        mockMvc.perform(post("/api/auth/token/pair")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));
    }

    @Test
    @DisplayName("POST /api/auth/logout with LogoutRequest body returns 200 OK")
    void testLogoutEndpoint_WithRequestBody() throws Exception {
        doNothing().when(authService).logout(any());

        String jsonBody = "{\"token\":\"raw-token-body\"}";

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/logout without token returns 200 OK")
    void testLogoutEndpoint_WithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/logout with blank Authorization header returns 200 OK")
    void testLogoutEndpoint_WithBlankHeader() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/auth/logout returns 405 METHOD NOT ALLOWED")
    void testLogoutEndpoint_MethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/auth/logout"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 UNAUTHORIZED when token is invalid or expired")
    void testGetCurrentUser_InvalidToken() throws Exception {
        when(authService.getCurrentUser("Bearer expired-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired session token"));
    }

    @Test
    @DisplayName("POST /api/auth/change-password returns 401 when Authorization header is missing")
    void testChangePassword_MissingHeader() throws Exception {
        String jsonBody = "{\"oldPassword\":\"OldPass123@\",\"newPassword\":\"NewPass456#\",\"confirmPassword\":\"NewPass456#\"}";

        mockMvc.perform(post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/change-password returns 401 when token is invalid")
    void testChangePassword_InvalidToken() throws Exception {
        when(authService.getCurrentUser("Bearer invalid-token")).thenReturn(Optional.empty());

        String jsonBody = "{\"oldPassword\":\"OldPass123@\",\"newPassword\":\"NewPass456#\",\"confirmPassword\":\"NewPass456#\"}";

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/change-password returns 400 when validation fails")
    void testChangePassword_BadRequest() throws Exception {
        User user = User.builder().username("admin").build();
        user.setId(1L);
        when(authService.getCurrentUser("Bearer valid-token")).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Mật khẩu hiện tại không chính xác"))
                .when(authService).changePassword(eq("admin"), any(ChangePasswordRequest.class));

        String jsonBody = "{\"oldPassword\":\"OldPass123@\",\"newPassword\":\"NewPass456#\",\"confirmPassword\":\"NewPass456#\"}";

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/change-password returns 403 when account is inactive")
    void testChangePassword_Forbidden() throws Exception {
        User user = User.builder().username("admin").build();
        user.setId(1L);
        when(authService.getCurrentUser("Bearer valid-token")).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new IllegalStateException("Account is inactive or disabled"))
                .when(authService).changePassword(eq("admin"), any(ChangePasswordRequest.class));

        String jsonBody = "{\"oldPassword\":\"OldPass123@\",\"newPassword\":\"NewPass456#\",\"confirmPassword\":\"NewPass456#\"}";

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/refresh-token returns 401 when token is invalid")
    void testRefreshToken_Unauthorized() throws Exception {
        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid refresh token"));

        String jsonBody = "{\"refreshToken\":\"bad-token\"}";

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/refresh-token returns 403 when account is disabled")
    void testRefreshToken_Forbidden() throws Exception {
        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new IllegalStateException("Account is inactive or disabled"));

        String jsonBody = "{\"refreshToken\":\"valid-token\"}";

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/verify-token with Authorization header returns 200 OK")
    void testVerifyToken_WithAuthHeader() throws Exception {
        VerifyTokenResponse response = VerifyTokenResponse.builder()
                .valid(true)
                .status("VALID")
                .username("admin")
                .build();
        when(authService.verifyToken("Bearer test-token")).thenReturn(response);

        mockMvc.perform(post("/api/auth/verify-token")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/verify-token with missing token returns 400 Bad Request")
    void testVerifyToken_MissingToken() throws Exception {
        mockMvc.perform(post("/api/auth/verify-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/verify-token returns 200 with invalid status message")
    void testVerifyToken_InvalidToken() throws Exception {
        VerifyTokenResponse response = VerifyTokenResponse.builder()
                .valid(false)
                .status("EXPIRED")
                .message("Token has expired")
                .build();
        when(authService.verifyToken("expired-token")).thenReturn(response);

        String jsonBody = "{\"token\":\"expired-token\"}";

        mockMvc.perform(post("/api/auth/verify-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/revoke-token with body token returns 200 OK")
    void testRevokeToken_WithBody() throws Exception {
        doNothing().when(authService).revokeToken(any(RevokeTokenRequest.class));

        String jsonBody = "{\"token\":\"token-to-revoke\"}";

        mockMvc.perform(post("/api/auth/revoke-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/revoke-token with header token returns 200 OK")
    void testRevokeToken_WithHeader() throws Exception {
        doNothing().when(authService).revokeToken(any(RevokeTokenRequest.class));

        mockMvc.perform(post("/api/auth/revoke-token")
                        .header("Authorization", "Bearer token-to-revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/revoke-token with empty request returns 200 OK")
    void testRevokeToken_EmptyRequest() throws Exception {
        mockMvc.perform(post("/api/auth/revoke-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/token/pair returns 401 when Authorization header is missing")
    void testIssueTokenPair_MissingHeader() throws Exception {
        mockMvc.perform(post("/api/auth/token/pair"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/token/pair returns 401 when user not found")
    void testIssueTokenPair_UserNotFound() throws Exception {
        when(authService.getCurrentUser("Bearer invalid-token")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/token/pair")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Direct unit tests verify 503 SERVICE_UNAVAILABLE when AuthService is null")
    void testNullAuthServiceDirectly() {
        AuthController controller = new AuthController();
        org.junit.jupiter.api.Assertions.assertNotNull(controller);

        AuthController nullController = new AuthController(null);
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                nullController.login(new LoginRequest("a", "b")).getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                nullController.logout(null, null).getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                nullController.getCurrentUser(null).getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                nullController.changePassword(null, new ChangePasswordRequest("a", "b", "c")).getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                nullController.verifyPassword(new VerifyPasswordRequest("a", "b")).getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                nullController.refreshToken(new RefreshTokenRequest("t")).getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                nullController.verifyToken(null, null).getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                nullController.revokeToken(null, null).getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                nullController.issueTokenPair(null).getStatusCode());
    }
}

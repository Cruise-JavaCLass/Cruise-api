package com.cruise.auth.controller;

import com.cruise.auth.dto.*;
import com.cruise.auth.entity.User;
import com.cruise.auth.service.AuthService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller xử lý các endpoint xác thực và quản lý token:
 * - Đăng nhập, Đăng xuất, Lấy thông tin user hiện tại (Me)
 * - Đổi mật khẩu, Xác minh mật khẩu
 * - Cấp phát token, Làm mới token (Refresh Token), Xác minh token (Introspection), Thu hồi token (Revocation)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuthService authService;

    public AuthController() {
    }

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Endpoint kiểm tra trạng thái hoạt động của Auth Service
     * GET /api/auth/health
     */
    @GetMapping("/health")
    public ApiResponse<java.util.Map<String, Object>> health() {
        return ApiResponse.ok("Cruise Auth Service is running", java.util.Map.of(
                "service", "cruise-auth-service",
                "status", "UP",
                "rolesSupported", java.util.List.of(com.cruise.common.enums.Role.values())
        ));
    }

    /**
     * Endpoint đăng nhập người dùng bằng username/email và mật khẩu
     * Cấp phát cặp Access Token + Refresh Token
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Endpoint đăng xuất và vô hiệu hóa token hiện tại
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) LogoutRequest logoutRequest) {

        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }

        String token = null;
        if (authHeader != null && !authHeader.isBlank()) {
            token = authHeader;
        } else if (logoutRequest != null && logoutRequest.getToken() != null) {
            token = logoutRequest.getToken();
        }

        if (token != null && !token.isBlank()) {
            authService.logout(token);
        }

        return ResponseEntity.ok(ApiResponse.ok("Logout successful", null));
    }

    /**
     * Endpoint lấy thông tin người dùng từ phiên đăng nhập hiện tại
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing or invalid Authorization header"));
        }

        Optional<User> userOpt = authService.getCurrentUser(authHeader);
        return userOpt.map(user -> ResponseEntity.ok(ApiResponse.ok("User profile retrieved successfully", user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or expired session token")));
    }

    /**
     * Endpoint đổi mật khẩu người dùng (yêu cầu Authorization Header)
     * POST /api/auth/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ChangePasswordRequest request) {

        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing or invalid Authorization header"));
        }

        Optional<User> currentUser = authService.getCurrentUser(authHeader);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid or expired session token"));
        }

        try {
            authService.changePassword(currentUser.get().getUsername(), request);
            return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Endpoint xác minh mật khẩu của người dùng (dùng cho các hành động nhạy cảm / thanh toán)
     * POST /api/auth/verify-password
     */
    @PostMapping("/verify-password")
    public ResponseEntity<ApiResponse<java.util.Map<String, Boolean>>> verifyPassword(
            @Valid @RequestBody VerifyPasswordRequest request) {

        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }

        boolean isValid = authService.verifyPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(
                isValid ? "Password verified successfully" : "Password verification failed",
                java.util.Map.of("valid", isValid)
        ));
    }

    /**
     * Endpoint làm mới Access Token bằng Refresh Token (Token Rotation)
     * POST /api/auth/refresh-token, POST /api/auth/refresh
     */
    @PostMapping(value = {"/refresh-token", "/refresh"})
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }

        try {
            LoginResponse response = authService.refreshToken(request);
            return ResponseEntity.ok(ApiResponse.ok("Token refreshed successfully", response));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
        }
    }

    /**
     * Endpoint xác minh và phân tích chi tiết thông tin token (Token Introspection RFC 7662)
     * POST /api/auth/verify-token, POST /api/auth/validate-token
     */
    @PostMapping(value = {"/verify-token", "/validate-token"})
    public ResponseEntity<ApiResponse<VerifyTokenResponse>> verifyToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) VerifyTokenRequest request) {

        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }

        String tokenToVerify = null;
        if (request != null && request.getToken() != null && !request.getToken().isBlank()) {
            tokenToVerify = request.getToken();
        } else if (authHeader != null && !authHeader.isBlank()) {
            tokenToVerify = authHeader;
        }

        if (tokenToVerify == null || tokenToVerify.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Token is required in request body or Authorization header"));
        }

        VerifyTokenResponse response = authService.verifyToken(tokenToVerify);
        return ResponseEntity.ok(ApiResponse.ok(
                response.isValid() ? "Token is valid" : "Token verification failed: " + response.getMessage(),
                response
        ));
    }

    /**
     * Endpoint thu hồi token (đăng xuất thiết bị hoặc admin force-revoke)
     * POST /api/auth/revoke-token, POST /api/auth/token/revoke
     */
    @PostMapping(value = {"/revoke-token", "/token/revoke"})
    public ResponseEntity<ApiResponse<Void>> revokeToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) RevokeTokenRequest request) {

        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }

        String tokenToRevoke = null;
        if (request != null && request.getToken() != null && !request.getToken().isBlank()) {
            tokenToRevoke = request.getToken();
        } else if (authHeader != null && !authHeader.isBlank()) {
            tokenToRevoke = authHeader;
        }

        if (tokenToRevoke != null && !tokenToRevoke.isBlank()) {
            authService.revokeToken(RevokeTokenRequest.builder().token(tokenToRevoke).build());
        }

        return ResponseEntity.ok(ApiResponse.ok("Token revoked successfully", null));
    }

    /**
     * Endpoint cấp phát cặp token mới cho phiên đăng nhập hiện tại
     * POST /api/auth/token/pair
     */
    @PostMapping("/token/pair")
    public ResponseEntity<ApiResponse<TokenPair>> issueTokenPair(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }

        if (authHeader == null || authHeader.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Missing or invalid Authorization header"));
        }

        Optional<User> currentUser = authService.getCurrentUser(authHeader);
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid or expired session token"));
        }

        TokenPair tokenPair = authService.issueTokenPair(currentUser.get().getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Token pair issued successfully", tokenPair));
    }
}

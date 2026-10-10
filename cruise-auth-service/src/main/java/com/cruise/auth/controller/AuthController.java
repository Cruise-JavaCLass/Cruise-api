package com.cruise.auth.controller;

import com.cruise.auth.dto.LoginRequest;
import com.cruise.auth.dto.LoginResponse;
import com.cruise.auth.dto.LogoutRequest;
import com.cruise.auth.entity.User;
import com.cruise.auth.service.AuthService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Controller xử lý các endpoint xác thực: Login, Logout, Me
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
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
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
     * Endpoint đăng xuất và vô hiệu hóa JWT token
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) LogoutRequest logoutRequest) {

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
            @Valid @RequestBody com.cruise.auth.dto.ChangePasswordRequest request) {

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
            @Valid @RequestBody com.cruise.auth.dto.VerifyPasswordRequest request) {

        if (authService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error("Auth service unavailable"));
        }

        boolean isValid = authService.verifyPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(
                isValid ? "Password verified successfully" : "Password verification failed",
                java.util.Map.of("valid", isValid)
        ));
    }
}

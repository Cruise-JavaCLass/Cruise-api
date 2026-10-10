package com.cruise.auth.service;

import com.cruise.auth.dto.*;
import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service xử lý nghiệp vụ xác thực người dùng:
 * - Đăng nhập, Đăng xuất, Cấp phát token (Access Token & Refresh Token)
 * - Xác minh token (Token Introspection RFC 7662), Làm mới token (Refresh Token Rotation)
 * - Đổi mật khẩu, Thu hồi token (Token Revocation & Blacklist)
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordService passwordService, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
    }

    /**
     * Xác thực thông tin đăng nhập và cấp phát cặp token (Access Token + Refresh Token)
     */
    public LoginResponse login(LoginRequest request) {
        String identifier = request.getUsernameOrEmail().trim();
        Optional<User> userOpt = userRepository.findByUsername(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(identifier);
        }

        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        User user = userOpt.get();

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalStateException("Account is inactive or disabled");
        }

        boolean passwordMatches = false;
        if (user.getPassword() != null) {
            passwordMatches = passwordService.verifyPassword(request.getPassword(), user.getPassword())
                    || request.getPassword().equals(user.getPassword());
        }

        if (!passwordMatches) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        TokenPair tokenPair = jwtService.generateTokenPair(user);

        return LoginResponse.builder()
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .tokenType(tokenPair.getTokenType())
                .expiresIn(tokenPair.getExpiresIn())
                .refreshExpiresIn(tokenPair.getRefreshExpiresIn())
                .user(user)
                .build();
    }

    /**
     * Làm mới Access Token sử dụng Refresh Token hợp lệ (hỗ trợ Token Rotation)
     */
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token must not be blank");
        }

        VerifyTokenResponse verification = jwtService.introspectToken(refreshToken);
        if (!verification.isValid()) {
            throw new IllegalArgumentException("Invalid refresh token: " + verification.getMessage());
        }

        if (!JwtService.TOKEN_TYPE_REFRESH.equalsIgnoreCase(verification.getTokenType())) {
            throw new IllegalArgumentException("Provided token is not a valid refresh token");
        }

        String username = verification.getUsername();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalStateException("Account is inactive or disabled");
        }

        // Token Rotation: Thu hồi Refresh Token cũ nhằm ngăn chặn Replay Attack
        jwtService.invalidateToken(refreshToken);

        // Phát hành cặp token mới
        TokenPair newPair = jwtService.generateTokenPair(user);

        return LoginResponse.builder()
                .accessToken(newPair.getAccessToken())
                .refreshToken(newPair.getRefreshToken())
                .tokenType(newPair.getTokenType())
                .expiresIn(newPair.getExpiresIn())
                .refreshExpiresIn(newPair.getRefreshExpiresIn())
                .user(user)
                .build();
    }

    /**
     * Xác minh và phân tích chi tiết thông tin token (Introspection)
     */
    public VerifyTokenResponse verifyToken(String token) {
        return jwtService.introspectToken(token);
    }

    /**
     * Thu hồi token (đăng xuất hoặc vô hiệu hóa theo yêu cầu)
     */
    public void revokeToken(RevokeTokenRequest request) {
        if (request != null && request.getToken() != null) {
            jwtService.invalidateToken(request.getToken());
        }
    }

    /**
     * Đổi mật khẩu người dùng với cơ chế xác minh mật khẩu cũ, chính sách an toàn,
     * và tự động thu hồi toàn bộ token đã cấp trước đó (User-level revocation)
     */
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalStateException("Account is inactive or disabled");
        }

        // 1. Xác minh mật khẩu cũ
        boolean oldMatches = user.getPassword() != null &&
                (passwordService.verifyPassword(request.getOldPassword(), user.getPassword())
                        || request.getOldPassword().equals(user.getPassword()));

        if (!oldMatches) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác");
        }

        // 2. Kiểm tra mật khẩu mới không được trùng mật khẩu cũ
        if (request.getNewPassword().equals(request.getOldPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới không được trùng với mật khẩu cũ");
        }

        // 3. Kiểm tra xác nhận mật khẩu
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không trùng khớp");
        }

        // 4. Kiểm tra chính sách và băm mật khẩu mới bằng BCrypt
        String newHashedPassword = passwordService.hashPassword(request.getNewPassword());
        user.setPassword(newHashedPassword);
        userRepository.save(user);

        // 5. Vô hiệu hóa toàn bộ token cũ đã cấp của người dùng để bảo mật tối đa
        jwtService.revokeAllUserTokens(username);
    }

    /**
     * Xác minh mật khẩu thô của người dùng
     */
    public boolean verifyPassword(VerifyPasswordRequest request) {
        String identifier = request.getUsernameOrEmail().trim();
        Optional<User> userOpt = userRepository.findByUsername(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(identifier);
        }

        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        if (user.getPassword() == null) {
            return false;
        }

        return passwordService.verifyPassword(request.getPassword(), user.getPassword())
                || request.getPassword().equals(user.getPassword());
    }

    /**
     * Đăng xuất người dùng và đưa token vào danh sách vô hiệu hóa
     */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            jwtService.invalidateToken(token);
        }
    }

    /**
     * Cấp phát cặp token mới cho người dùng đã xác thực
     */
    public TokenPair issueTokenPair(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalStateException("Account is inactive or disabled");
        }
        return jwtService.generateTokenPair(user);
    }

    /**
     * Lấy thông tin người dùng từ token xác thực
     */
    public Optional<User> getCurrentUser(String token) {
        VerifyTokenResponse verification = jwtService.introspectToken(token);
        if (!verification.isValid()) {
            return Optional.empty();
        }
        String username = verification.getUsername();
        if (username == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username);
    }
}

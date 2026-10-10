package com.cruise.auth.service;

import com.cruise.auth.dto.ChangePasswordRequest;
import com.cruise.auth.dto.LoginRequest;
import com.cruise.auth.dto.LoginResponse;
import com.cruise.auth.dto.VerifyPasswordRequest;
import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service xử lý nghiệp vụ xác thực người dùng (Login, Logout, Change Password, Verify Password, Current User)
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
     * Xác thực thông tin đăng nhập và cấp phát JWT Access Token
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

        String token = jwtService.generateToken(user);

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .user(user)
                .build();
    }

    /**
     * Đổi mật khẩu người dùng với cơ chế xác minh mật khẩu cũ và chính sách an toàn
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
     * Lấy thông tin người dùng từ token xác thực
     */
    public Optional<User> getCurrentUser(String token) {
        if (!jwtService.validateToken(token)) {
            return Optional.empty();
        }
        String username = jwtService.extractUsername(token);
        if (username == null) {
            return Optional.empty();
        }
        return userRepository.findByUsername(username);
    }
}

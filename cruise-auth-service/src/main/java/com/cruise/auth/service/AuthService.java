package com.cruise.auth.service;

import com.cruise.auth.dto.LoginRequest;
import com.cruise.auth.dto.LoginResponse;
import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service xử lý nghiệp vụ xác thực người dùng (Login, Logout, Current User)
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
            passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword())
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

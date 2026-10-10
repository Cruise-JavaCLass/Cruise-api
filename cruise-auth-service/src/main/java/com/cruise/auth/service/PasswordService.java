package com.cruise.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service chuyên biệt chịu trách nhiệm mã hóa và xác minh mật khẩu an toàn
 */
@Service
public class PasswordService {

    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    public PasswordService(PasswordEncoder passwordEncoder, PasswordValidator passwordValidator) {
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
    }

    /**
     * Kiểm tra chính sách và băm mật khẩu thô bằng thuật toán BCrypt
     */
    public String hashPassword(String rawPassword) {
        passwordValidator.validate(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Băm mật khẩu mà không kiểm tra độ mạnh (dành cho migration hoặc hash trực tiếp)
     */
    public String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu không được để trống");
        }
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Xác minh mật khẩu thô có khớp với chuỗi đã băm hay không
     */
    public boolean verifyPassword(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    /**
     * Kiểm tra xem chuỗi có phải là một hash BCrypt hợp lệ hay không
     */
    public boolean isBCryptHash(String value) {
        if (value == null) {
            return false;
        }
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    /**
     * Lấy PasswordValidator để kiểm tra policy
     */
    public PasswordValidator getValidator() {
        return passwordValidator;
    }
}

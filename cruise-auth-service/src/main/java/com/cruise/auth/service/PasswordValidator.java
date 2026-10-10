package com.cruise.auth.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Component xác thực độ an toàn và chính sách mật khẩu (Password Policy Validator)
 */
@Component
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 100;

    private static final Pattern HAS_UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]");

    private static final Set<String> COMMON_WEAK_PASSWORDS = Set.of(
            "password", "12345678", "password123", "admin123", "qwerty123",
            "letmein123", "welcome123", "iloveyou", "123456789", "cruise123"
    );

    /**
     * Kiểm tra tính hợp lệ của mật khẩu và ném lỗi nếu không đạt yêu cầu
     */
    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu không được để trống");
        }

        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Mật khẩu phải có độ dài tối thiểu " + MIN_LENGTH + " ký tự");
        }

        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Mật khẩu không được vượt quá " + MAX_LENGTH + " ký tự");
        }

        if (COMMON_WEAK_PASSWORDS.contains(password.toLowerCase().trim())) {
            throw new IllegalArgumentException("Mật khẩu quá thông dụng và dễ đoán, vui lòng chọn mật khẩu phức tạp hơn");
        }

        if (!HAS_UPPERCASE.matcher(password).find()) {
            throw new IllegalArgumentException("Mật khẩu phải chứa ít nhất một chữ cái in hoa (A-Z)");
        }

        if (!HAS_LOWERCASE.matcher(password).find()) {
            throw new IllegalArgumentException("Mật khẩu phải chứa ít nhất một chữ cái in thường (a-z)");
        }

        if (!HAS_DIGIT.matcher(password).find()) {
            throw new IllegalArgumentException("Mật khẩu phải chứa ít nhất một chữ số (0-9)");
        }

        if (!HAS_SPECIAL.matcher(password).find()) {
            throw new IllegalArgumentException("Mật khẩu phải chứa ít nhất một ký tự đặc biệt (!@#$%^&*...)");
        }
    }

    /**
     * Kiểm tra nhanh mật khẩu có hợp lệ hay không (trả về boolean)
     */
    public boolean isValid(String password) {
        try {
            validate(password);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Mô tả chính sách mật khẩu
     */
    public String getPolicyDescription() {
        return "Mật khẩu tối thiểu 8 ký tự, gồm ít nhất 1 chữ hoa, 1 chữ thường, 1 số và 1 ký tự đặc biệt.";
    }
}

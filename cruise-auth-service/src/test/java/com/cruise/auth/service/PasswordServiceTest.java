package com.cruise.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordService Unit Tests")
class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(new BCryptPasswordEncoder(), new PasswordValidator());
    }

    @Test
    @DisplayName("Should hash strong password into BCrypt string starting with $2a$ or $2b$")
    void shouldHashPasswordSuccessfully() {
        String rawPassword = "Strong#Password2026";
        String hashed = passwordService.hashPassword(rawPassword);

        assertThat(hashed).isNotNull()
                .isNotEqualTo(rawPassword)
                .startsWith("$2");
        assertThat(passwordService.isBCryptHash(hashed)).isTrue();
    }

    @Test
    @DisplayName("Should verify raw password against hashed password successfully")
    void shouldVerifyPasswordCorrectly() {
        String rawPassword = "Valid@Passcode99";
        String hashed = passwordService.hashPassword(rawPassword);

        boolean matches = passwordService.verifyPassword(rawPassword, hashed);
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should reject incorrect raw password against hashed password")
    void shouldRejectIncorrectPassword() {
        String rawPassword = "Valid@Passcode99";
        String hashed = passwordService.hashPassword(rawPassword);

        boolean matches = passwordService.verifyPassword("WrongPassword123!", hashed);
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should correctly identify whether a string is a BCrypt hash")
    void shouldIdentifyBCryptHashCorrectly() {
        assertThat(passwordService.isBCryptHash("$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqr")).isTrue();
        assertThat(passwordService.isBCryptHash("$2b$12$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqr")).isTrue();
        assertThat(passwordService.isBCryptHash("plainTextPassword")).isFalse();
        assertThat(passwordService.isBCryptHash(null)).isFalse();
        assertThat(passwordService.isBCryptHash("")).isFalse();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when hashing weak password")
    void shouldThrowExceptionWhenHashingWeakPassword() {
        assertThatThrownBy(() -> passwordService.hashPassword("weak"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should encode raw password directly without policy check")
    void shouldEncodeRawPasswordDirectly() {
        String encoded = passwordService.encode("simplePass");
        assertThat(encoded).startsWith("$2");
        assertThat(passwordService.verifyPassword("simplePass", encoded)).isTrue();
    }

    @Test
    @DisplayName("Should throw exception when encoding null or blank password")
    void shouldThrowExceptionWhenEncodingBlankPassword() {
        assertThatThrownBy(() -> passwordService.encode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> passwordService.encode("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should return false when verifying with null raw or hashed password")
    void shouldReturnFalseWhenVerifyingWithNullPasswords() {
        assertThat(passwordService.verifyPassword(null, "$2a$10$xyz")).isFalse();
        assertThat(passwordService.verifyPassword("password", null)).isFalse();
        assertThat(passwordService.verifyPassword(null, null)).isFalse();
    }

    @Test
    @DisplayName("Should retrieve PasswordValidator instance")
    void shouldGetValidatorSuccessfully() {
        assertThat(passwordService.getValidator()).isNotNull();
    }
}

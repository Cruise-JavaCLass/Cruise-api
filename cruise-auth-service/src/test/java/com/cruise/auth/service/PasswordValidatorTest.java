package com.cruise.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordValidator Unit Tests")
class PasswordValidatorTest {

    private PasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Admin@1234",
            "Cruise#2026Secure",
            "P@ssw0rdValid!",
            "Super_Safe_99",
            "A1b2C3d4!"
    })
    @DisplayName("Should accept valid strong passwords adhering to OWASP complexity policy")
    void shouldAcceptValidPasswords(String password) {
        assertThat(validator.isValid(password)).isTrue();
        // validate method should not throw exception
        validator.validate(password);
    }

    @Test
    @DisplayName("Should reject password shorter than 8 characters")
    void shouldRejectTooShortPassword() {
        assertThat(validator.isValid("Short1!")).isFalse();
        assertThatThrownBy(() -> validator.validate("Short1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tối thiểu 8 ký tự");
    }

    @Test
    @DisplayName("Should reject password longer than 100 characters")
    void shouldRejectTooLongPassword() {
        String longPassword = "A1!" + "a".repeat(101);
        assertThat(validator.isValid(longPassword)).isFalse();
        assertThatThrownBy(() -> validator.validate(longPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100 ký tự");
    }

    @Test
    @DisplayName("Should reject password missing uppercase letter")
    void shouldRejectMissingUppercase() {
        assertThat(validator.isValid("alllowercase1@")).isFalse();
        assertThatThrownBy(() -> validator.validate("alllowercase1@"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chữ cái in hoa");
    }

    @Test
    @DisplayName("Should reject password missing lowercase letter")
    void shouldRejectMissingLowercase() {
        assertThat(validator.isValid("ALLUPPERCASE1@")).isFalse();
        assertThatThrownBy(() -> validator.validate("ALLUPPERCASE1@"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chữ cái in thường");
    }

    @Test
    @DisplayName("Should reject password missing digit")
    void shouldRejectMissingDigit() {
        assertThat(validator.isValid("NoDigitsHere@!")).isFalse();
        assertThatThrownBy(() -> validator.validate("NoDigitsHere@!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chữ số");
    }

    @Test
    @DisplayName("Should reject password missing special character")
    void shouldRejectMissingSpecialChar() {
        assertThat(validator.isValid("NoSpecialChar123")).isFalse();
        assertThatThrownBy(() -> validator.validate("NoSpecialChar123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ký tự đặc biệt");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password",
            "12345678",
            "qwerty123",
            "admin123",
            "cruise123",
            "welcome123"
    })
    @DisplayName("Should reject common weak passwords from blacklist")
    void shouldRejectCommonWeakPasswords(String weakPass) {
        assertThat(validator.isValid(weakPass)).isFalse();
        assertThatThrownBy(() -> validator.validate(weakPass))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject null or blank password")
    void shouldRejectNullOrBlankPassword() {
        assertThat(validator.isValid(null)).isFalse();
        assertThat(validator.isValid("")).isFalse();
        assertThat(validator.isValid("   ")).isFalse();

        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được để trống");
    }

    @Test
    @DisplayName("Should return descriptive policy message")
    void shouldReturnPolicyDescription() {
        String description = validator.getPolicyDescription();
        assertThat(description).isNotEmpty()
                .contains("8")
                .contains("chữ hoa")
                .contains("ký tự đặc biệt");
    }
}

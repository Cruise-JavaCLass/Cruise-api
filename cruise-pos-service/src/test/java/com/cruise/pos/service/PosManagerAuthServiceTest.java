package com.cruise.pos.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PosManagerAuthService Unit Tests")
class PosManagerAuthServiceTest {

    private final PosManagerAuthService authService = new PosManagerAuthService();

    @Test
    @DisplayName("verifyRefundApproval approves valid manager credentials")
    void testValidCredentials() {
        assertThat(authService.verifyRefundApproval("MGR-01", "8888")).isTrue();
        assertThat(authService.verifyRefundApproval("mgr-01", "8888")).isTrue();
        assertThat(authService.verifyRefundApproval("MANAGER-DEFAULT", "8888")).isTrue();
        assertThat(authService.verifyRefundApproval("MGR-ADMIN", "9999")).isTrue();
    }

    @Test
    @DisplayName("verifyRefundApproval rejects invalid manager credentials and empty fields")
    void testInvalidCredentials() {
        assertThat(authService.verifyRefundApproval(null, "8888")).isFalse();
        assertThat(authService.verifyRefundApproval("MGR-01", null)).isFalse();
        assertThat(authService.verifyRefundApproval("", "8888")).isFalse();
        assertThat(authService.verifyRefundApproval("MGR-01", "wrong")).isFalse();
        assertThat(authService.verifyRefundApproval("UNKNOWN-USER", "8888")).isFalse();
    }
}

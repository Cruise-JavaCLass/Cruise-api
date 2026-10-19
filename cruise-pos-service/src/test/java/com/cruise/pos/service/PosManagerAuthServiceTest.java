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
        // Wildcard prefix should now be rejected
        assertThat(authService.verifyRefundApproval("MGR-HACKER", "8888")).isFalse();
        assertThat(authService.verifyRefundApproval("MANAGERFOO", "8888")).isFalse();
    }

    @Test
    @DisplayName("verifyRefundApproval blocks repeated brute-force attempts")
    void testBruteForceLockout() {
        String testManager = "MGR-BRUTE";
        authService.setManagers(java.util.Map.of(testManager, "2926a2731f4b312c08982cacf8061eb14bf65c1a87cc5d70e864e079c6220731"));

        for (int i = 0; i < 5; i++) {
            assertThat(authService.verifyRefundApproval(testManager, "wrong-pin-" + i)).isFalse();
        }

        // Even with the right pin, the 6th attempt is locked out
        assertThat(authService.verifyRefundApproval(testManager, "8888")).isFalse();
    }
}

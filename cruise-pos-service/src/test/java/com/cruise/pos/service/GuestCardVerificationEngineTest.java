package com.cruise.pos.service;

import com.cruise.pos.dto.IdentifyPassengerRequest;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.PosCredential;
import com.cruise.pos.enums.CredentialStatus;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.exception.CredentialSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuestCardVerificationEngineTest {

    private GuestCardVerificationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new GuestCardVerificationEngine();
    }

    @Test
    @DisplayName("validateCredentialSecurity throws CredentialSecurityException for REPORTED_LOST")
    void testLostCard() {
        PosCredential cred = PosCredential.builder()
                .status(CredentialStatus.REPORTED_LOST)
                .identifier("1234567890123456")
                .build();
        IdentifyPassengerRequest req = IdentifyPassengerRequest.builder()
                .stationId("STA-1")
                .cashierId("CASH-1")
                .build();

        assertThatThrownBy(() -> engine.validateCredentialSecurity(cred, req))
                .isInstanceOf(CredentialSecurityException.class)
                .hasMessageContaining("REPORTED_LOST");
    }

    @Test
    @DisplayName("validateCredentialSecurity throws CredentialSecurityException for BLOCKED")
    void testBlockedCard() {
        PosCredential cred = PosCredential.builder()
                .status(CredentialStatus.BLOCKED)
                .identifier("1234567890123456")
                .build();
        IdentifyPassengerRequest req = IdentifyPassengerRequest.builder()
                .stationId("STA-1")
                .cashierId("CASH-1")
                .build();

        assertThatThrownBy(() -> engine.validateCredentialSecurity(cred, req))
                .isInstanceOf(CredentialSecurityException.class)
                .hasMessageContaining("BLOCKED");
    }

    @Test
    @DisplayName("evaluateSpendingAllowance correctly evaluates limits and statuses")
    void testSpendingAllowance() {
        PosCredential cred = PosCredential.builder()
                .status(CredentialStatus.ACTIVE)
                .spendingAllowed(true)
                .build();

        CabinFolioAccount activeFolioWithBalance = CabinFolioAccount.builder()
                .creditLimit(BigDecimal.valueOf(1000.00))
                .currentBalance(BigDecimal.valueOf(200.00))
                .status(FolioStatus.ACTIVE)
                .build();

        assertThat(engine.evaluateSpendingAllowance(cred, activeFolioWithBalance)).isTrue();

        CabinFolioAccount lockedFolio = CabinFolioAccount.builder()
                .creditLimit(BigDecimal.valueOf(1000.00))
                .currentBalance(BigDecimal.valueOf(200.00))
                .status(FolioStatus.LOCKED)
                .build();

        assertThat(engine.evaluateSpendingAllowance(cred, lockedFolio)).isFalse();

        cred.setSpendingAllowed(false);
        assertThat(engine.evaluateSpendingAllowance(cred, activeFolioWithBalance)).isFalse();
    }

    @Test
    @DisplayName("isAlcoholRestricted returns true for minor or alcoholRestricted flag")
    void testAlcoholRestriction() {
        PosCredential adult = PosCredential.builder()
                .isMinor(false)
                .alcoholRestricted(false)
                .build();
        assertThat(engine.isAlcoholRestricted(adult)).isFalse();

        PosCredential minor = PosCredential.builder()
                .isMinor(true)
                .alcoholRestricted(false)
                .build();
        assertThat(engine.isAlcoholRestricted(minor)).isTrue();

        PosCredential restrictedAdult = PosCredential.builder()
                .isMinor(false)
                .alcoholRestricted(true)
                .build();
        assertThat(engine.isAlcoholRestricted(restrictedAdult)).isTrue();
    }
}

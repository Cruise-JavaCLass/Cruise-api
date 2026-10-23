package com.cruise.pos.service;

import com.cruise.pos.dto.CabinCreditLimitCheckRequest;
import com.cruise.pos.dto.CabinCreditLimitCheckResponse;
import com.cruise.pos.entity.CabinFolioAccount;
import com.cruise.pos.entity.CabinMember;
import com.cruise.pos.enums.FolioStatus;
import com.cruise.pos.repository.CabinFolioAccountRepository;
import com.cruise.pos.repository.CabinMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CabinCreditLimitEngineTest {

    @Mock
    private CabinFolioAccountRepository folioRepository;

    @Mock
    private CabinMemberRepository memberRepository;

    @Mock
    private CabinBillingSplitService billingSplitService;

    @InjectMocks
    private CabinCreditLimitEngine creditLimitEngine;

    private CabinFolioAccount activeFolio;
    private CabinMember adultMember;
    private CabinMember minorMember;

    @BeforeEach
    void setUp() {
        activeFolio = CabinFolioAccount.builder()
                .cabinNumber("CABIN-7012")
                .primaryGuestName("Nguyễn Văn An")
                .creditLimit(BigDecimal.valueOf(1000.00))
                .currentBalance(BigDecimal.valueOf(200.00))
                .status(FolioStatus.ACTIVE)
                .build();

        adultMember = CabinMember.builder()
                .cabinNumber("CABIN-7012")
                .passengerId(101L)
                .guestName("Nguyễn Văn An")
                .isMinor(false)
                .isAuthorizedSigner(true)
                .individualCreditLimit(BigDecimal.valueOf(400.00))
                .individualBalance(BigDecimal.valueOf(100.00))
                .build();

        minorMember = CabinMember.builder()
                .cabinNumber("CABIN-7012")
                .passengerId(103L)
                .guestName("Nguyễn Bé")
                .isMinor(true)
                .isAuthorizedSigner(false)
                .build();
    }

    @Test
    @DisplayName("Should approve charge when within cabin and member limit")
    void testCheckCreditLimitApproved() {
        when(folioRepository.findByCabinNumber("CABIN-7012")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.findByCabinNumberAndPassengerId("CABIN-7012", 101L)).thenReturn(Optional.of(adultMember));

        CabinCreditLimitCheckRequest req = CabinCreditLimitCheckRequest.builder()
                .amount(BigDecimal.valueOf(150.00))
                .passengerId(101L)
                .build();

        CabinCreditLimitCheckResponse res = creditLimitEngine.checkCreditLimit("CABIN-7012", req);

        assertThat(res.isAllowed()).isTrue();
        assertThat(res.isLocked()).isFalse();
        assertThat(res.isOverLimit()).isFalse();
    }

    @Test
    @DisplayName("Should lock charge when cabin available limit is exceeded")
    void testCheckCreditLimitCabinOverLimit() {
        when(folioRepository.findByCabinNumber("CABIN-7012")).thenReturn(Optional.of(activeFolio));

        // Available is 800 (1000 - 200). Request 900.
        CabinCreditLimitCheckRequest req = CabinCreditLimitCheckRequest.builder()
                .amount(BigDecimal.valueOf(900.00))
                .build();

        CabinCreditLimitCheckResponse res = creditLimitEngine.checkCreditLimit("CABIN-7012", req);

        assertThat(res.isAllowed()).isFalse();
        assertThat(res.isLocked()).isTrue();
        assertThat(res.isOverLimit()).isTrue();
        assertThat(res.getLockReason()).contains("Cabin Over-limit");
    }

    @Test
    @DisplayName("Should lock charge when member is minor or unauthorized signer")
    void testCheckCreditLimitMinorLock() {
        when(folioRepository.findByCabinNumber("CABIN-7012")).thenReturn(Optional.of(activeFolio));
        when(memberRepository.findByCabinNumberAndPassengerId("CABIN-7012", 103L)).thenReturn(Optional.of(minorMember));

        CabinCreditLimitCheckRequest req = CabinCreditLimitCheckRequest.builder()
                .amount(BigDecimal.valueOf(50.00))
                .passengerId(103L)
                .build();

        CabinCreditLimitCheckResponse res = creditLimitEngine.checkCreditLimit("CABIN-7012", req);

        assertThat(res.isAllowed()).isFalse();
        assertThat(res.isLocked()).isTrue();
        assertThat(res.getLockReason()).contains("Minor/Unauthorized Signer");
    }

    @Test
    @DisplayName("Should lock charge when folio is LOCKED or SETTLED")
    void testCheckCreditLimitLockedFolio() {
        activeFolio.setStatus(FolioStatus.LOCKED);
        when(folioRepository.findByCabinNumber("CABIN-7012")).thenReturn(Optional.of(activeFolio));

        CabinCreditLimitCheckRequest req = CabinCreditLimitCheckRequest.builder()
                .amount(BigDecimal.valueOf(20.00))
                .build();

        CabinCreditLimitCheckResponse res = creditLimitEngine.checkCreditLimit("CABIN-7012", req);

        assertThat(res.isAllowed()).isFalse();
        assertThat(res.isLocked()).isTrue();
        assertThat(res.getLockReason()).contains("Folio Locked");
    }
}

package com.cruise.pos.service;

import com.cruise.pos.entity.CabinFolio;
import com.cruise.pos.entity.PreAuthorizationHold;
import com.cruise.pos.enums.CabinFolioStatus;
import com.cruise.pos.enums.PreAuthHoldStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.FolioLimitExceededException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioRepository;
import com.cruise.pos.repository.PreAuthorizationHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FolioManagementService Unit Tests")
class FolioManagementServiceTest {

    @Mock
    private CabinFolioRepository folioRepository;

    @Mock
    private PreAuthorizationHoldRepository holdRepository;

    @InjectMocks
    private FolioManagementService service;

    private CabinFolio folio;

    @BeforeEach
    void setUp() {
        folio = CabinFolio.builder()
                .cabinNumber("A-101")
                .voyageId(1L)
                .creditLimit(new BigDecimal("100.00"))
                .currentBalance(new BigDecimal("40.00"))
                .status(CabinFolioStatus.OPEN)
                .build();
        folio.setId(1L);
    }

    private PreAuthorizationHold activeHold(String amount, Instant expiresAt) {
        PreAuthorizationHold hold = PreAuthorizationHold.builder()
                .folio(folio)
                .passengerId(7L)
                .holdAmount(new BigDecimal(amount))
                .holdReferenceCode("PAH-TEST-0001")
                .status(PreAuthHoldStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build();
        hold.setId(5L);
        return hold;
    }

    private Instant inFuture() {
        return Instant.now().plus(1, ChronoUnit.DAYS);
    }

    // ---------------------------------------------------------------- createPreAuthorization

    @Test
    @DisplayName("Creates an ACTIVE hold with a unique reference code and an expiry date")
    void shouldCreatePreAuthorization() {
        when(folioRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(folio));
        when(holdRepository.save(any(PreAuthorizationHold.class))).thenAnswer(inv -> inv.getArgument(0));

        PreAuthorizationHold hold = service.createPreAuthorization(1L, 7L, new BigDecimal("50"));

        assertThat(hold.getStatus()).isEqualTo(PreAuthHoldStatus.ACTIVE);
        assertThat(hold.getHoldAmount()).isEqualByComparingTo("50.00");
        assertThat(hold.getPassengerId()).isEqualTo(7L);
        assertThat(hold.getFolio()).isSameAs(folio);
        assertThat(hold.getHoldReferenceCode()).startsWith("PAH-");
        assertThat(hold.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Rejects hold amounts that are null, zero, negative or have more than 2 decimals")
    void shouldRejectInvalidHoldAmounts() {
        assertThatThrownBy(() -> service.createPreAuthorization(1L, 7L, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.createPreAuthorization(1L, 7L, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.createPreAuthorization(1L, 7L, new BigDecimal("-5.00")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.createPreAuthorization(1L, 7L, new BigDecimal("10.005")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("2 chữ số thập phân");
        verify(holdRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects a hold when the folio is not OPEN or does not exist")
    void shouldRejectHoldOnClosedOrMissingFolio() {
        folio.setStatus(CabinFolioStatus.SUSPENDED);
        when(folioRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(folio));
        when(folioRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPreAuthorization(1L, 7L, new BigDecimal("50.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("SUSPENDED");
        assertThatThrownBy(() -> service.createPreAuthorization(99L, 7L, new BigDecimal("50.00")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- validateSpendAuthorization

    @Test
    @DisplayName("Allows a charge that exactly reaches the credit limit")
    void shouldAllowChargeUpToCreditLimit() {
        when(folioRepository.findById(1L)).thenReturn(Optional.of(folio));
        when(holdRepository.sumActiveHoldAmount(eq(1L), eq(PreAuthHoldStatus.ACTIVE), any(Instant.class)))
                .thenReturn(null);

        FolioManagementService.SpendAuthorization result =
                service.validateSpendAuthorization(1L, new BigDecimal("60.00"));

        assertThat(result.projectedBalance()).isEqualByComparingTo("100.00");
        assertThat(result.remainingHeadroom()).isEqualByComparingTo("0.00");
        assertThat(result.totalHoldAmount()).isEqualByComparingTo("0.00");
        assertThat(result.overdraftApproved()).isFalse();
    }

    @Test
    @DisplayName("Blocks a charge above the credit limit with error code FOLIO_LIMIT_EXCEEDED")
    void shouldBlockChargeAboveCreditLimit() {
        when(folioRepository.findById(1L)).thenReturn(Optional.of(folio));
        when(holdRepository.sumActiveHoldAmount(eq(1L), eq(PreAuthHoldStatus.ACTIVE), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> service.validateSpendAuthorization(1L, new BigDecimal("60.01")))
                .isInstanceOfSatisfying(FolioLimitExceededException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("FOLIO_LIMIT_EXCEEDED");
                    assertThat(ex.getMessage()).startsWith("FOLIO_LIMIT_EXCEEDED");
                });
    }

    @Test
    @DisplayName("An active hold raises the spending ceiling")
    void shouldAllowChargeCoveredByActiveHold() {
        when(folioRepository.findById(1L)).thenReturn(Optional.of(folio));
        when(holdRepository.sumActiveHoldAmount(eq(1L), eq(PreAuthHoldStatus.ACTIVE), any(Instant.class)))
                .thenReturn(new BigDecimal("50.00"));

        FolioManagementService.SpendAuthorization result =
                service.validateSpendAuthorization(1L, new BigDecimal("110.00"));

        assertThat(result.totalHoldAmount()).isEqualByComparingTo("50.00");
        assertThat(result.remainingHeadroom()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Manager overdraft approval bypasses the limit check")
    void shouldAllowOverdraftWhenApproved() {
        when(folioRepository.findById(1L)).thenReturn(Optional.of(folio));
        when(holdRepository.sumActiveHoldAmount(eq(1L), eq(PreAuthHoldStatus.ACTIVE), any(Instant.class)))
                .thenReturn(null);

        FolioManagementService.SpendAuthorization result =
                service.validateSpendAuthorization(1L, new BigDecimal("500.00"), true);

        assertThat(result.overdraftApproved()).isTrue();
        assertThat(result.remainingHeadroom().signum()).isNegative();
    }

    @Test
    @DisplayName("Rejects spending on a folio that is not OPEN, or that does not exist")
    void shouldRejectSpendOnNonOpenOrMissingFolio() {
        folio.setStatus(CabinFolioStatus.SUSPENDED);
        when(folioRepository.findById(1L)).thenReturn(Optional.of(folio));
        when(folioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateSpendAuthorization(1L, new BigDecimal("1.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("FOLIO_NOT_OPEN");
        assertThatThrownBy(() -> service.validateSpendAuthorization(99L, new BigDecimal("1.00")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.validateSpendAuthorization(1L, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---------------------------------------------------------------- captureHold

    @Test
    @DisplayName("Capturing a hold marks it CAPTURED and reduces the folio balance")
    void shouldCaptureHold() {
        PreAuthorizationHold hold = activeHold("50.00", inFuture());
        when(holdRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(hold));
        when(folioRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(folio));
        when(holdRepository.save(any(PreAuthorizationHold.class))).thenAnswer(inv -> inv.getArgument(0));

        PreAuthorizationHold result = service.captureHold(5L, new BigDecimal("30.00"));

        assertThat(result.getStatus()).isEqualTo(PreAuthHoldStatus.CAPTURED);
        assertThat(result.getCapturedAmount()).isEqualByComparingTo("30.00");
        assertThat(result.getResolvedAt()).isNotNull();
        assertThat(folio.getCurrentBalance()).isEqualByComparingTo("10.00");
        verify(folioRepository).save(folio);
    }

    @Test
    @DisplayName("Cannot capture more than the held amount")
    void shouldRejectCaptureAboveHeldAmount() {
        when(holdRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(activeHold("50.00", inFuture())));

        assertThatThrownBy(() -> service.captureHold(5L, new BigDecimal("50.01")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("vượt");
        verify(folioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cannot capture an expired hold")
    void shouldRejectCaptureOfExpiredHold() {
        PreAuthorizationHold hold = activeHold("50.00", Instant.now().minus(1, ChronoUnit.HOURS));
        when(holdRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> service.captureHold(5L, new BigDecimal("10.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("hết hạn");
    }

    @Test
    @DisplayName("Cannot capture a hold twice")
    void shouldRejectCaptureOfAlreadyResolvedHold() {
        PreAuthorizationHold hold = activeHold("50.00", inFuture());
        hold.setStatus(PreAuthHoldStatus.CAPTURED);
        when(holdRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(hold));
        when(folioRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(folio));

        assertThatThrownBy(() -> service.captureHold(5L, new BigDecimal("10.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("CAPTURED");
        assertThat(folio.getCurrentBalance()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("Cannot capture against a settled folio")
    void shouldRejectCaptureOnSettledFolio() {
        folio.setStatus(CabinFolioStatus.SETTLED);
        when(holdRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(activeHold("50.00", inFuture())));
        when(folioRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(folio));

        assertThatThrownBy(() -> service.captureHold(5L, new BigDecimal("10.00")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("tất toán");
    }

    // ---------------------------------------------------------------- releaseHold

    @Test
    @DisplayName("Releasing an ACTIVE hold marks it RELEASED")
    void shouldReleaseHold() {
        PreAuthorizationHold hold = activeHold("50.00", inFuture());
        when(holdRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(hold));
        when(holdRepository.save(any(PreAuthorizationHold.class))).thenAnswer(inv -> inv.getArgument(0));

        PreAuthorizationHold result = service.releaseHold(5L);

        assertThat(result.getStatus()).isEqualTo(PreAuthHoldStatus.RELEASED);
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Cannot release a hold that is already resolved or missing")
    void shouldRejectReleaseOfResolvedOrMissingHold() {
        PreAuthorizationHold hold = activeHold("50.00", inFuture());
        hold.setStatus(PreAuthHoldStatus.RELEASED);
        when(holdRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(hold));
        when(holdRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.releaseHold(5L)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.releaseHold(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(holdRepository, never()).save(any());
    }
}

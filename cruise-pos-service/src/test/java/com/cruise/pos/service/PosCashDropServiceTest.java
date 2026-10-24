package com.cruise.pos.service;

import com.cruise.pos.dto.CashDropRequest;
import com.cruise.pos.dto.CashDropResponse;
import com.cruise.pos.entity.PosAuditLog;
import com.cruise.pos.entity.PosCashDrop;
import com.cruise.pos.entity.PosSafeLedger;
import com.cruise.pos.entity.PosShift;
import com.cruise.pos.enums.ShiftStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosAuditLogRepository;
import com.cruise.pos.repository.PosCashDropRepository;
import com.cruise.pos.repository.PosSafeLedgerRepository;
import com.cruise.pos.repository.PosShiftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * KAN-614 — Cash drop calculation and safe balance ledger unit tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PosCashDropService & Safe Balance Ledger Tests")
class PosCashDropServiceTest {

    @Mock
    private PosShiftRepository shiftRepository;

    @Mock
    private PosCashDropRepository cashDropRepository;

    @Mock
    private PosSafeLedgerRepository safeLedgerRepository;

    @Mock
    private PosAuditLogRepository auditLogRepository;

    @Mock
    private PosManagerAuthService posManagerAuthService;

    @Spy
    private EscPosReceiptService escPosReceiptService = new EscPosReceiptService();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PosCashDropService cashDropService;

    private PosShift openShift;
    private CashDropRequest validRequest;
    private PosSafeLedger safeLedger;

    @BeforeEach
    void setUp() {
        openShift = PosShift.builder()
                .stationId("ROVING-POS-01")
                .cashierId("CREW-ALICE")
                .status(ShiftStatus.OPEN)
                .openedAt(Instant.now())
                .startingCash(BigDecimal.valueOf(300.00))
                .totalSales(BigDecimal.valueOf(1200.00))
                .totalCashDrops(BigDecimal.ZERO)
                .build();
        openShift.setId(101L);

        validRequest = new CashDropRequest(
                BigDecimal.valueOf(500.00),
                "CREW-ALICE",
                "MGR-01",
                "8888",
                Map.of("100", 3, "50", 4),
                "Drawer threshold reached ($1000 limit)"
        );

        safeLedger = PosSafeLedger.builder()
                .safeId(PosCashDropService.DEFAULT_SAFE_ID)
                .balance(BigDecimal.valueOf(5000.00))
                .build();
    }

    @Nested
    @DisplayName("Successful Cash Drop Scenarios")
    class SuccessfulCashDrop {

        @Test
        @DisplayName("Records cash drop, increases safe balance, and adds to shift total drops")
        void processCashDrop_success() {
            when(shiftRepository.findByIdWithLock(101L)).thenReturn(Optional.of(openShift));
            when(posManagerAuthService.verifyRefundApproval("MGR-01", "8888")).thenReturn(true);
            when(safeLedgerRepository.findByIdWithLock(PosCashDropService.DEFAULT_SAFE_ID))
                    .thenReturn(Optional.of(safeLedger));
            when(safeLedgerRepository.save(any(PosSafeLedger.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cashDropRepository.save(any(PosCashDrop.class))).thenAnswer(inv -> {
                PosCashDrop drop = inv.getArgument(0);
                drop.setId(1L);
                return drop;
            });

            CashDropResponse response = cashDropService.processCashDrop(101L, validRequest);

            assertThat(response).isNotNull();
            assertThat(response.amount()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
            assertThat(response.safeBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(5500.00));
            assertThat(response.dualSignatureVerified()).isTrue();
            assertThat(response.receiptNumber()).startsWith("SAFE-DROP-101-");
            assertThat(response.escPosReceiptSlip()).contains("CRUISE SHIP SAFE TRANSFER");
            assertThat(response.escPosReceiptSlip()).contains("DROPPED AMOUNT : $500.00");

            // Verify shift total cash drops updated
            assertThat(openShift.getTotalCashDrops()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
            verify(shiftRepository).save(openShift);

            // Verify safe ledger updated
            assertThat(safeLedger.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(5500.00));
            verify(safeLedgerRepository).save(safeLedger);

            // Verify audit log created
            ArgumentCaptor<PosAuditLog> captor = ArgumentCaptor.forClass(PosAuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            PosAuditLog log = captor.getValue();
            assertThat(log.getAction()).isEqualTo("MID_SHIFT_CASH_DROP");
            assertThat(log.getPerformedBy()).isEqualTo("CREW-ALICE");
            assertThat(log.getManagerId()).isEqualTo("MGR-01");
        }
    }

    @Nested
    @DisplayName("Validation and Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Throws ResourceNotFoundException when shift does not exist")
        void nonExistentShift_throwsException() {
            when(shiftRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cashDropService.processCashDrop(999L, validRequest))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Shift not found");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when shift is already CLOSED")
        void closedShift_throwsException() {
            openShift.setStatus(ShiftStatus.CLOSED);
            when(shiftRepository.findByIdWithLock(101L)).thenReturn(Optional.of(openShift));

            assertThatThrownBy(() -> cashDropService.processCashDrop(101L, validRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot perform cash drop on a closed shift");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when cashier and witness are the same person")
        void witnessSameAsCashier_throwsException() {
            CashDropRequest samePersonRequest = new CashDropRequest(
                    BigDecimal.valueOf(500.00),
                    "CREW-ALICE",
                    "CREW-ALICE",
                    "8888",
                    null,
                    "Self-witness drop"
            );
            when(shiftRepository.findByIdWithLock(101L)).thenReturn(Optional.of(openShift));

            assertThatThrownBy(() -> cashDropService.processCashDrop(101L, samePersonRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Witness must be a different person than the cashier");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when request cashierId does not match shift cashier")
        void mismatchedCashier_throwsException() {
            CashDropRequest mismatchRequest = new CashDropRequest(
                    BigDecimal.valueOf(500.00),
                    "CREW-BOB",
                    "MGR-01",
                    "8888",
                    null,
                    "Mismatched cashier"
            );
            when(shiftRepository.findByIdWithLock(101L)).thenReturn(Optional.of(openShift));

            assertThatThrownBy(() -> cashDropService.processCashDrop(101L, mismatchRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Request cashier ID does not match active shift cashier");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when currency breakdown total does not match amount")
        void currencyBreakdownMismatch_throwsException() {
            CashDropRequest mismatchBreakdown = new CashDropRequest(
                    BigDecimal.valueOf(500.00),
                    "CREW-ALICE",
                    "MGR-01",
                    "8888",
                    Map.of("100", 2), // $200 != $500
                    "Mismatched breakdown"
            );
            when(shiftRepository.findByIdWithLock(101L)).thenReturn(Optional.of(openShift));
            when(posManagerAuthService.verifyRefundApproval("MGR-01", "8888")).thenReturn(true);

            assertThatThrownBy(() -> cashDropService.processCashDrop(101L, mismatchBreakdown))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Currency breakdown total");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when currency breakdown has negative denomination")
        void currencyBreakdownNegativeDenomination_throwsException() {
            CashDropRequest invalidDenom = new CashDropRequest(
                    BigDecimal.valueOf(500.00),
                    "CREW-ALICE",
                    "MGR-01",
                    "8888",
                    Map.of("-50", 10),
                    "Negative denomination"
            );
            when(shiftRepository.findByIdWithLock(101L)).thenReturn(Optional.of(openShift));
            when(posManagerAuthService.verifyRefundApproval("MGR-01", "8888")).thenReturn(true);

            assertThatThrownBy(() -> cashDropService.processCashDrop(101L, invalidDenom))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when currency breakdown has non-numeric denomination")
        void currencyBreakdownNonNumericDenomination_throwsException() {
            CashDropRequest nonNumeric = new CashDropRequest(
                    BigDecimal.valueOf(500.00),
                    "CREW-ALICE",
                    "MGR-01",
                    "8888",
                    Map.of("ABC", 10),
                    "Non-numeric denomination"
            );
            when(shiftRepository.findByIdWithLock(101L)).thenReturn(Optional.of(openShift));
            when(posManagerAuthService.verifyRefundApproval("MGR-01", "8888")).thenReturn(true);

            assertThatThrownBy(() -> cashDropService.processCashDrop(101L, nonNumeric))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("must be numeric");
        }

        @Test
        @DisplayName("Throws BusinessRuleException when witness PIN is invalid")
        void invalidWitnessPin_throwsException() {
            when(shiftRepository.findByIdWithLock(101L)).thenReturn(Optional.of(openShift));
            when(posManagerAuthService.verifyRefundApproval("MGR-01", "8888")).thenReturn(false);

            assertThatThrownBy(() -> cashDropService.processCashDrop(101L, validRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Witness/Supervisor authentication failed");
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when request is null")
        void nullRequest_throwsException() {
            assertThatThrownBy(() -> cashDropService.processCashDrop(101L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

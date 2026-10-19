package com.cruise.pos.service;

import com.cruise.pos.dto.ShiftSummaryResponse;
import com.cruise.pos.entity.PosShift;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.entity.PosTransactionItem;
import com.cruise.pos.enums.PaymentMethod;
import com.cruise.pos.enums.ShiftStatus;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.repository.PosShiftRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PosShiftService Unit Tests")
class PosShiftServiceTest {

    @Mock
    private PosShiftRepository shiftRepository;

    @Mock
    private PosTransactionRepository transactionRepository;

    @InjectMocks
    private PosShiftService shiftService;

    private PosShift shift;

    @BeforeEach
    void setUp() {
        shift = PosShift.builder()
                .stationId("BAR-DECK-9")
                .cashierId("CASHIER-01")
                .status(ShiftStatus.OPEN)
                .openedAt(Instant.now())
                .startingCash(BigDecimal.valueOf(200.00))
                .totalSales(BigDecimal.valueOf(500.00))
                .totalRefunds(BigDecimal.ZERO)
                .netRevenue(BigDecimal.valueOf(500.00))
                .transactionCount(5)
                .build();
    }

    @Test
    @DisplayName("openShift creates new shift if no active shift exists")
    void testOpenShiftNew() {
        when(shiftRepository.findByStationIdAndStatus("BAR-DECK-9", ShiftStatus.OPEN)).thenReturn(Optional.empty());
        when(shiftRepository.save(any(PosShift.class))).thenAnswer(i -> i.getArgument(0));

        PosShift created = shiftService.openShift("BAR-DECK-9", "CASHIER-01", BigDecimal.valueOf(100.00));

        assertThat(created.getStationId()).isEqualTo("BAR-DECK-9");
        assertThat(created.getStatus()).isEqualTo(ShiftStatus.OPEN);
        verify(shiftRepository).save(any(PosShift.class));
    }

    @Test
    @DisplayName("closeShift settles transactions and closes shift")
    void testCloseShiftSuccess() {
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(any(PosShift.class))).thenAnswer(i -> i.getArgument(0));

        PosTransaction tx = PosTransaction.builder()
                .settled(false)
                .build();
        when(transactionRepository.findByShiftId(1L)).thenReturn(List.of(tx));

        PosShift closed = shiftService.closeShift(1L, "MGR-01", BigDecimal.valueOf(700.00));

        assertThat(closed.getStatus()).isEqualTo(ShiftStatus.CLOSED);
        assertThat(closed.getClosedByManagerId()).isEqualTo("MGR-01");
        assertThat(tx.getSettled()).isTrue();
        verify(transactionRepository).saveAll(any());
    }

    @Test
    @DisplayName("closeShift throws exception if already closed")
    void testCloseShiftAlreadyClosed() {
        shift.setStatus(ShiftStatus.CLOSED);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> shiftService.closeShift(1L, "MGR-01", BigDecimal.valueOf(700.00)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("đã được chốt quyết toán");
    }

    @Test
    @DisplayName("generateShiftSummary calculates totals by payment method and category")
    void testGenerateShiftSummary() {
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));

        PosTransaction tx = PosTransaction.builder()
                .status(TransactionStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CABIN_FOLIO)
                .totalAmount(BigDecimal.valueOf(100.00))
                .items(List.of(
                        PosTransactionItem.builder()
                                .category("BEVERAGE")
                                .totalPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .build();
        when(transactionRepository.findByShiftId(1L)).thenReturn(List.of(tx));

        ShiftSummaryResponse summary = shiftService.generateShiftSummary(1L, "Z_REPORT");

        assertThat(summary.getReportType()).isEqualTo("Z_REPORT");
        assertThat(summary.getRevenueByPaymentMethod().get("CABIN_FOLIO")).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(summary.getRevenueByCategory().get("BEVERAGE")).isEqualByComparingTo(BigDecimal.valueOf(100.00));
    }
}

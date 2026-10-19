package com.cruise.pos.service;

import com.cruise.pos.dto.BatchSyncJournalRequest;
import com.cruise.pos.dto.BatchSyncJournalResponse;
import com.cruise.pos.dto.BatchSyncTransactionItem;
import com.cruise.pos.dto.ReconciliationSummaryMetricsDto;
import com.cruise.pos.entity.DisputeCase;
import com.cruise.pos.entity.GuestFolioLedger;
import com.cruise.pos.entity.PosJournalEntry;
import com.cruise.pos.enums.DiscrepancyStatus;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationSyncServiceTest {

    @Mock
    private PosJournalEntryRepository journalRepository;
    @Mock
    private GuestFolioLedgerRepository folioRepository;
    @Mock
    private DiscrepancyRecordRepository discrepancyRepository;
    @Mock
    private DisputeCaseRepository disputeRepository;
    @Mock
    private ReconciliationAuditLogRepository auditLogRepository;

    @InjectMocks
    private ReconciliationSyncService syncService;

    @Test
    @DisplayName("Đồng bộ mẻ POS ngoại tuyến với kiểm tra Idempotency chống trùng lặp")
    void testSyncBatch_WithIdempotency() {
        BatchSyncTransactionItem item1 = BatchSyncTransactionItem.builder()
                .posTransactionId("TX-SYNC-1")
                .terminalId("TERM-A")
                .serviceArea("BAR")
                .cabinNumber("CABIN-101")
                .amount(new BigDecimal("25.00"))
                .transactionTime(Instant.now())
                .idempotencyKey("IDEM-001")
                .build();

        BatchSyncTransactionItem item2Duplicate = BatchSyncTransactionItem.builder()
                .posTransactionId("TX-SYNC-2")
                .terminalId("TERM-A")
                .serviceArea("BAR")
                .cabinNumber("CABIN-102")
                .amount(new BigDecimal("40.00"))
                .transactionTime(Instant.now())
                .idempotencyKey("IDEM-002")
                .build();

        BatchSyncJournalRequest request = BatchSyncJournalRequest.builder()
                .terminalId("TERM-A")
                .syncOperator("CASHIER_A")
                .transactions(List.of(item1, item2Duplicate))
                .build();

        // item1 does not exist, item2 already exists
        when(journalRepository.existsByPosTransactionId("TX-SYNC-1")).thenReturn(false);
        when(journalRepository.existsByIdempotencyKey("IDEM-001")).thenReturn(false);

        when(journalRepository.existsByPosTransactionId("TX-SYNC-2")).thenReturn(true);

        BatchSyncJournalResponse response = syncService.syncBatch(request);

        assertThat(response.getTotalReceived()).isEqualTo(2);
        assertThat(response.getInsertedCount()).isEqualTo(1);
        assertThat(response.getDuplicateSkippedCount()).isEqualTo(1);
        assertThat(response.getSkippedTransactionIds()).contains("TX-SYNC-2");
        assertThat(response.getTotalAmountSynced()).isEqualByComparingTo("25.00");

        verify(journalRepository, times(1)).save(any(PosJournalEntry.class));
        verify(folioRepository, times(1)).save(any(GuestFolioLedger.class));
    }

    @Test
    @DisplayName("Tính toán chỉ số thống kê đối soát và cảnh báo khẩn cấp khi tỷ lệ sai lệch cao")
    void testGetSummaryMetrics_WithAlert() {
        when(journalRepository.count()).thenReturn(100L);
        when(disputeRepository.countByStatus(DisputeStatus.SUBMITTED)).thenReturn(2L);
        when(disputeRepository.countByStatus(DisputeStatus.UNDER_INVESTIGATION)).thenReturn(3L);
        when(disputeRepository.countByStatus(DisputeStatus.APPROVED_ADJUSTED)).thenReturn(10L);
        when(discrepancyRepository.countByStatus(DiscrepancyStatus.DETECTED)).thenReturn(2L); // 2% > 0.5%
        when(discrepancyRepository.countByStatus(DiscrepancyStatus.INVESTIGATING)).thenReturn(0L);

        GuestFolioLedger ledger = GuestFolioLedger.builder()
                .amount(new BigDecimal("5000.00"))
                .isReversal(false)
                .build();
        when(folioRepository.findAll()).thenReturn(List.of(ledger));

        DisputeCase dispute = DisputeCase.builder()
                .status(DisputeStatus.SUBMITTED)
                .disputeAmount(new BigDecimal("150.00"))
                .build();
        when(disputeRepository.findAll()).thenReturn(List.of(dispute));
        when(discrepancyRepository.findAll()).thenReturn(Collections.emptyList());

        ReconciliationSummaryMetricsDto metrics = syncService.getSummaryMetrics();

        assertThat(metrics.getTotalTransactionsCount()).isEqualTo(100L);
        assertThat(metrics.getOpenDisputesCount()).isEqualTo(5L);
        assertThat(metrics.getResolvedDisputesCount()).isEqualTo(10L);
        assertThat(metrics.getTotalReconciledAmount()).isEqualByComparingTo("5000.00");
        assertThat(metrics.getTotalDisputedAmount()).isEqualByComparingTo("150.00");
        assertThat(metrics.getDiscrepancyRatePercent()).isEqualTo(2.0);
        assertThat(metrics.isCriticalAlertActive()).isTrue();
    }
}

package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.*;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationMatchingServiceTest {

    @Mock
    private ReconciliationBatchRepository batchRepository;
    @Mock
    private DiscrepancyRecordRepository discrepancyRepository;
    @Mock
    private PosJournalEntryRepository journalRepository;
    @Mock
    private GuestFolioLedgerRepository folioRepository;
    @Mock
    private ReconciliationAuditLogRepository auditLogRepository;

    @InjectMocks
    private ReconciliationMatchingService matchingService;

    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();
    }

    @Test
    @DisplayName("Đối soát hoàn hảo khi POS và Folio khớp 100%")
    void testRunReconciliation_AllMatched() {
        RunReconciliationRequest req = RunReconciliationRequest.builder()
                .terminalId("TERM-01")
                .serviceArea("SKY_BAR")
                .startTime(now.minus(1, ChronoUnit.HOURS))
                .endTime(now)
                .triggeredBy("TESTER")
                .build();

        PosJournalEntry journal = PosJournalEntry.builder()
                .posTransactionId("TX-1001")
                .terminalId("TERM-01")
                .cabinNumber("CABIN-101")
                .amount(new BigDecimal("45.00"))
                .serviceArea("SKY_BAR")
                .passengerName("John Doe")
                .transactionTime(now.minus(30, ChronoUnit.MINUTES))
                .build();

        GuestFolioLedger folio = GuestFolioLedger.builder()
                .transactionId("TX-1001")
                .folioId("FOLIO-101")
                .cabinNumber("CABIN-101")
                .amount(new BigDecimal("45.00"))
                .serviceArea("SKY_BAR")
                .passengerName("John Doe")
                .postedAt(now.minus(30, ChronoUnit.MINUTES))
                .isReversal(false)
                .build();

        when(batchRepository.save(any(ReconciliationBatch.class))).thenAnswer(inv -> {
            ReconciliationBatch b = inv.getArgument(0);
            b.setId(1L);
            return b;
        });

        when(journalRepository.findByTransactionTimeBetween(any(), any()))
                .thenReturn(List.of(journal));
        when(folioRepository.findByPostedAtBetween(any(), any()))
                .thenReturn(List.of(folio));

        ReconciliationBatchDto result = matchingService.runReconciliation(req);

        assertThat(result).isNotNull();
        assertThat(result.getMatchedCount()).isEqualTo(1);
        assertThat(result.getDiscrepancyCount()).isZero();
        assertThat(result.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(result.getTotalReconciledAmount()).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("Phát hiện sai lệch UNMATCHED_POS khi có ở POS nhưng thiếu ở Folio")
    void testRunReconciliation_UnmatchedPos() {
        RunReconciliationRequest req = RunReconciliationRequest.builder().build();

        PosJournalEntry journal = PosJournalEntry.builder()
                .posTransactionId("TX-UNMATCHED")
                .cabinNumber("CABIN-202")
                .amount(new BigDecimal("120.00"))
                .serviceArea("SPA")
                .passengerName("Alice")
                .transactionTime(now.minus(20, ChronoUnit.MINUTES))
                .build();

        when(batchRepository.save(any(ReconciliationBatch.class))).thenAnswer(inv -> {
            ReconciliationBatch b = inv.getArgument(0);
            b.setId(2L);
            return b;
        });

        when(journalRepository.findByTransactionTimeBetween(any(), any()))
                .thenReturn(List.of(journal));
        when(folioRepository.findByPostedAtBetween(any(), any()))
                .thenReturn(Collections.emptyList());

        ReconciliationBatchDto result = matchingService.runReconciliation(req);

        assertThat(result.getDiscrepancyCount()).isEqualTo(1);
        assertThat(result.getMatchedCount()).isZero();
        verify(discrepancyRepository, times(1)).save(argThat(d ->
                d.getDiscrepancyType() == DiscrepancyType.UNMATCHED_POS &&
                        d.getTransactionId().equals("TX-UNMATCHED")
        ));
    }

    @Test
    @DisplayName("Phát hiện AMOUNT_MISMATCH khi số tiền giữa POS và Folio bị chênh lệch")
    void testRunReconciliation_AmountMismatch() {
        RunReconciliationRequest req = RunReconciliationRequest.builder().build();

        PosJournalEntry journal = PosJournalEntry.builder()
                .posTransactionId("TX-MISMATCH")
                .cabinNumber("CABIN-303")
                .amount(new BigDecimal("100.00"))
                .serviceArea("RESTAURANT")
                .passengerName("Bob")
                .transactionTime(now.minus(15, ChronoUnit.MINUTES))
                .build();

        GuestFolioLedger folio = GuestFolioLedger.builder()
                .transactionId("TX-MISMATCH")
                .folioId("FOLIO-303")
                .cabinNumber("CABIN-303")
                .amount(new BigDecimal("90.00")) // 10.00 difference
                .serviceArea("RESTAURANT")
                .passengerName("Bob")
                .postedAt(now.minus(15, ChronoUnit.MINUTES))
                .isReversal(false)
                .build();

        when(batchRepository.save(any(ReconciliationBatch.class))).thenAnswer(inv -> {
            ReconciliationBatch b = inv.getArgument(0);
            b.setId(3L);
            return b;
        });

        when(journalRepository.findByTransactionTimeBetween(any(), any())).thenReturn(List.of(journal));
        when(folioRepository.findByPostedAtBetween(any(), any())).thenReturn(List.of(folio));

        ReconciliationBatchDto result = matchingService.runReconciliation(req);

        assertThat(result.getDiscrepancyCount()).isEqualTo(1);
        verify(discrepancyRepository, times(1)).save(argThat(d ->
                d.getDiscrepancyType() == DiscrepancyType.AMOUNT_MISMATCH &&
                        d.getVariance().compareTo(new BigDecimal("10.00")) == 0
        ));
    }

    @Test
    @DisplayName("Phát hiện UNMATCHED_FOLIO khi bút toán Folio không có bản ghi POS tương ứng")
    void testRunReconciliation_UnmatchedFolio() {
        RunReconciliationRequest req = RunReconciliationRequest.builder().build();

        GuestFolioLedger folio = GuestFolioLedger.builder()
                .transactionId("TX-FOLIO-ONLY")
                .folioId("FOLIO-404")
                .cabinNumber("CABIN-404")
                .amount(new BigDecimal("75.00"))
                .serviceArea("CASINO")
                .passengerName("Charlie")
                .postedAt(now.minus(10, ChronoUnit.MINUTES))
                .isReversal(false)
                .build();

        when(batchRepository.save(any(ReconciliationBatch.class))).thenAnswer(inv -> {
            ReconciliationBatch b = inv.getArgument(0);
            b.setId(4L);
            return b;
        });

        when(journalRepository.findByTransactionTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(folioRepository.findByPostedAtBetween(any(), any())).thenReturn(List.of(folio));

        ReconciliationBatchDto result = matchingService.runReconciliation(req);

        assertThat(result.getDiscrepancyCount()).isEqualTo(1);
        verify(discrepancyRepository, times(1)).save(argThat(d ->
                d.getDiscrepancyType() == DiscrepancyType.UNMATCHED_FOLIO &&
                        d.getTransactionId().equals("TX-FOLIO-ONLY")
        ));
    }

    @Test
    @DisplayName("Cập nhật xử lý sai lệch thành công")
    void testResolveDiscrepancy_Success() {
        DiscrepancyRecord record = DiscrepancyRecord.builder()
                .discrepancyType(DiscrepancyType.AMOUNT_MISMATCH)
                .status(DiscrepancyStatus.DETECTED)
                .build();
        record.setId(10L);

        when(discrepancyRepository.findById(10L)).thenReturn(Optional.of(record));
        when(discrepancyRepository.save(any(DiscrepancyRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveDiscrepancyRequest req = ResolveDiscrepancyRequest.builder()
                .status(DiscrepancyStatus.RESOLVED)
                .resolutionNotes("Đã đối chiếu hóa đơn ký tay, chấp thuận bút toán điều chỉnh")
                .resolvedBy("PURSER_01")
                .build();

        DiscrepancyRecordDto result = matchingService.resolveDiscrepancy(10L, req);

        assertThat(result.getStatus()).isEqualTo(DiscrepancyStatus.RESOLVED);
        assertThat(result.getResolvedBy()).isEqualTo("PURSER_01");
    }

    @Test
    @DisplayName("Báo lỗi khi xử lý sai lệch không tồn tại")
    void testResolveDiscrepancy_NotFound() {
        when(discrepancyRepository.findById(999L)).thenReturn(Optional.empty());

        ResolveDiscrepancyRequest req = ResolveDiscrepancyRequest.builder()
                .status(DiscrepancyStatus.RESOLVED)
                .resolutionNotes("notes")
                .build();

        assertThatThrownBy(() -> matchingService.resolveDiscrepancy(999L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Không tạo trùng bản ghi sai lệch nếu giao dịch đã có sai lệch từ mẻ đối soát trước")
    void testRunReconciliation_SkipExistingDiscrepancyInOverlappingWindow() {
        RunReconciliationRequest req = RunReconciliationRequest.builder().build();

        PosJournalEntry journal = PosJournalEntry.builder()
                .posTransactionId("TX-ALREADY-FLAGGED")
                .cabinNumber("CABIN-101")
                .amount(new BigDecimal("50.00"))
                .serviceArea("BAR")
                .transactionTime(now.minus(10, ChronoUnit.MINUTES))
                .build();

        when(batchRepository.save(any(ReconciliationBatch.class))).thenAnswer(inv -> {
            ReconciliationBatch b = inv.getArgument(0);
            b.setId(3L);
            return b;
        });

        when(journalRepository.findByTransactionTimeBetween(any(), any()))
                .thenReturn(List.of(journal));
        when(folioRepository.findByPostedAtBetween(any(), any()))
                .thenReturn(Collections.emptyList());
        when(discrepancyRepository.existsByTransactionId("TX-ALREADY-FLAGGED")).thenReturn(true);

        ReconciliationBatchDto result = matchingService.runReconciliation(req);

        // Discrepancy is skipped because it was already flagged in a previous batch
        assertThat(result.getDiscrepancyCount()).isZero();
        verify(discrepancyRepository, never()).save(any());
    }
}

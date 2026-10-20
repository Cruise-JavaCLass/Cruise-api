package com.cruise.pos.service;

import com.cruise.pos.dto.BatchSyncJournalRequest;
import com.cruise.pos.dto.BatchSyncJournalResponse;
import com.cruise.pos.dto.BatchSyncTransactionItem;
import com.cruise.pos.dto.ReconciliationSummaryMetricsDto;
import com.cruise.pos.entity.GuestFolioLedger;
import com.cruise.pos.entity.PosJournalEntry;
import com.cruise.pos.entity.ReconciliationAuditLog;
import com.cruise.pos.enums.DiscrepancyStatus;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationSyncService {

    private final PosJournalEntryRepository journalRepository;
    private final GuestFolioLedgerRepository folioRepository;
    private final DiscrepancyRecordRepository discrepancyRepository;
    private final DisputeCaseRepository disputeRepository;
    private final ReconciliationAuditLogRepository auditLogRepository;
    private final PosJournalItemWriter itemWriter;

    public BatchSyncJournalResponse syncBatch(BatchSyncJournalRequest request) {
        log.info("Receiving batch POS journal sync from terminal: {}, items count: {}",
                request.getTerminalId(), request.getTransactions().size());

        int inserted = 0;
        int duplicates = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<String> skippedIds = new ArrayList<>();

        for (BatchSyncTransactionItem item : request.getTransactions()) {
            // Idempotency check 1: posTransactionId
            if (journalRepository.existsByPosTransactionId(item.getPosTransactionId())) {
                duplicates++;
                skippedIds.add(item.getPosTransactionId());
                continue;
            }

            // Idempotency check 2: idempotencyKey
            if (item.getIdempotencyKey() != null && !item.getIdempotencyKey().isBlank()) {
                if (journalRepository.existsByIdempotencyKey(item.getIdempotencyKey())) {
                    duplicates++;
                    skippedIds.add(item.getPosTransactionId());
                    continue;
                }
            }

            PosJournalEntry entry = PosJournalEntry.builder()
                    .posTransactionId(item.getPosTransactionId())
                    .terminalId(item.getTerminalId())
                    .serviceArea(item.getServiceArea())
                    .cabinNumber(item.getCabinNumber())
                    .passengerName(item.getPassengerName())
                    .amount(item.getAmount())
                    .currency(item.getCurrency() != null ? item.getCurrency() : "USD")
                    .transactionTime(item.getTransactionTime())
                    .idempotencyKey(item.getIdempotencyKey())
                    .itemsSummary(item.getItemsSummary())
                    .isSynced(true)
                    .syncedAt(Instant.now())
                    .build();

            GuestFolioLedger ledger = null;
            if (!folioRepository.existsByTransactionId(item.getPosTransactionId())) {
                ledger = GuestFolioLedger.builder()
                        .folioId("FOLIO-" + item.getCabinNumber())
                        .cabinNumber(item.getCabinNumber())
                        .passengerName(item.getPassengerName())
                        .transactionId(item.getPosTransactionId())
                        .description("Chi tiêu POS: " + item.getServiceArea())
                        .serviceArea(item.getServiceArea())
                        .amount(item.getAmount())
                        .isReversal(false)
                        .postedAt(item.getTransactionTime())
                        .build();
            }

            try {
                itemWriter.writeItem(entry, ledger);
                inserted++;
                totalAmount = totalAmount.add(item.getAmount());
            } catch (DataIntegrityViolationException dive) {
                if (isDuplicateConstraintViolation(dive)) {
                    log.warn("Duplicate constraint encountered for tx {}: {}", item.getPosTransactionId(), dive.getMessage());
                    duplicates++;
                    skippedIds.add(item.getPosTransactionId());
                } else {
                    log.error("Non-duplicate data integrity violation for tx {}: {}", item.getPosTransactionId(), dive.getMessage(), dive);
                    throw dive;
                }
            }
        }

        auditLogRepository.save(ReconciliationAuditLog.builder()
                .action("BATCH_SYNC_JOURNAL")
                .targetType("TERMINAL")
                .targetId(request.getTerminalId() != null ? request.getTerminalId() : "UNKNOWN")
                .actorId(request.getSyncOperator() != null ? request.getSyncOperator() : "POS_DAEMON")
                .details("Synced " + inserted + " records, skipped " + duplicates + " duplicates, total amount: " + totalAmount)
                .build());

        return BatchSyncJournalResponse.builder()
                .totalReceived(request.getTransactions().size())
                .insertedCount(inserted)
                .duplicateSkippedCount(duplicates)
                .totalAmountSynced(totalAmount)
                .skippedTransactionIds(skippedIds)
                .syncStatus("COMPLETED")
                .build();
    }

    @Transactional(readOnly = true)
    public ReconciliationSummaryMetricsDto getSummaryMetrics() {
        long totalJournals = journalRepository.count();
        long openDisputes = disputeRepository.countByStatus(DisputeStatus.SUBMITTED)
                + disputeRepository.countByStatus(DisputeStatus.UNDER_INVESTIGATION);
        long resolvedDisputes = disputeRepository.countByStatus(DisputeStatus.APPROVED_ADJUSTED);
        long pendingDiscrepancies = discrepancyRepository.countByStatus(DiscrepancyStatus.DETECTED)
                + discrepancyRepository.countByStatus(DiscrepancyStatus.INVESTIGATING);

        List<GuestFolioLedger> allFolios = folioRepository.findAll();
        BigDecimal totalReconciled = BigDecimal.ZERO;
        for (GuestFolioLedger f : allFolios) {
            if (!Boolean.TRUE.equals(f.getIsReversal())) {
                totalReconciled = totalReconciled.add(f.getAmount());
            }
        }

        BigDecimal totalDisputed = disputeRepository.findAll().stream()
                .filter(d -> d.getStatus() == DisputeStatus.SUBMITTED || d.getStatus() == DisputeStatus.UNDER_INVESTIGATION)
                .map(d -> d.getDisputeAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVariance = discrepancyRepository.findAll().stream()
                .filter(d -> d.getStatus() == DiscrepancyStatus.DETECTED)
                .map(d -> d.getVariance() != null ? d.getVariance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double rate = 0.0;
        if (totalJournals > 0) {
            rate = ((double) pendingDiscrepancies / (double) totalJournals) * 100.0;
            rate = Math.round(rate * 100.0) / 100.0;
        }

        boolean criticalAlert = rate > 0.5 || pendingDiscrepancies > 10 || totalVariance.compareTo(new BigDecimal("500.00")) > 0;
        String alertMsg = criticalAlert ?
                "CẢNH BÁO KHẨN CẤP: Tỷ lệ sai lệch hoặc số tiền chênh lệch vượt ngưỡng an toàn trước giờ cập bến!" :
                "Hệ thống ổn định. Tỷ lệ sai lệch trong ngưỡng kiểm soát.";

        return ReconciliationSummaryMetricsDto.builder()
                .totalReconciledAmount(totalReconciled)
                .totalDisputedAmount(totalDisputed)
                .totalVarianceAmount(totalVariance)
                .totalTransactionsCount(totalJournals)
                .openDisputesCount(openDisputes)
                .resolvedDisputesCount(resolvedDisputes)
                .pendingDiscrepanciesCount(pendingDiscrepancies)
                .discrepancyRatePercent(rate)
                .criticalAlertActive(criticalAlert)
                .alertMessage(alertMsg)
                .generatedAt(Instant.now())
                .build();
    }

    private boolean isDuplicateConstraintViolation(DataIntegrityViolationException dive) {
        Throwable current = dive;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String constraintName = cve.getConstraintName();
                if (constraintName != null) {
                    String lower = constraintName.toLowerCase(Locale.ROOT);
                    if (lower.contains("pos_transaction_id") || lower.contains("idempotency_key")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }

        Throwable rootCause = dive.getMostSpecificCause();
        String message = rootCause != null && rootCause.getMessage() != null
                ? rootCause.getMessage().toLowerCase(Locale.ROOT)
                : "";
        String diveMessage = dive.getMessage() != null ? dive.getMessage().toLowerCase(Locale.ROOT) : "";
        String combined = message + " " + diveMessage;

        boolean mentionsKey = combined.contains("pos_transaction_id") || combined.contains("idempotency_key");
        boolean mentionsDuplicate = combined.contains("unique") || combined.contains("duplicate");

        return mentionsKey && mentionsDuplicate;
    }
}

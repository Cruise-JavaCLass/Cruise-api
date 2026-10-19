package com.cruise.pos.service;

import com.cruise.pos.dto.DiscrepancyRecordDto;
import com.cruise.pos.dto.ReconciliationBatchDto;
import com.cruise.pos.dto.ResolveDiscrepancyRequest;
import com.cruise.pos.dto.RunReconciliationRequest;
import com.cruise.pos.entity.*;
import com.cruise.pos.enums.BatchStatus;
import com.cruise.pos.enums.DiscrepancyStatus;
import com.cruise.pos.enums.DiscrepancyType;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationMatchingService {

    private final ReconciliationBatchRepository batchRepository;
    private final DiscrepancyRecordRepository discrepancyRepository;
    private final PosJournalEntryRepository journalRepository;
    private final GuestFolioLedgerRepository folioRepository;
    private final ReconciliationAuditLogRepository auditLogRepository;

    // Concurrency Lock: only one active reconciliation batch allowed at a time
    private static final Object RECONCILIATION_LOCK = new Object();

    @Transactional
    public ReconciliationBatchDto runReconciliation(RunReconciliationRequest request) {
        synchronized (RECONCILIATION_LOCK) {
            log.info("Starting automated transaction reconciliation run: {}", request);

            Instant start = request.getStartTime() != null ? request.getStartTime() : Instant.now().minus(24, ChronoUnit.HOURS);
            Instant end = request.getEndTime() != null ? request.getEndTime() : Instant.now();

            String batchNumber = "RECON-" + System.currentTimeMillis();
            ReconciliationBatch batch = ReconciliationBatch.builder()
                    .batchNumber(batchNumber)
                    .terminalId(request.getTerminalId())
                    .serviceArea(request.getServiceArea())
                    .startedAt(Instant.now())
                    .status(BatchStatus.IN_PROGRESS)
                    .triggeredBy(request.getTriggeredBy() != null ? request.getTriggeredBy() : "SYSTEM_SCHEDULER")
                    .notes(request.getNotes())
                    .build();

            batch = batchRepository.save(batch);

            List<PosJournalEntry> journals = journalRepository.findByTransactionTimeBetween(start, end);
            List<GuestFolioLedger> folios = folioRepository.findByPostedAtBetween(start, end);

            Map<String, GuestFolioLedger> folioMap = new HashMap<>();
            for (GuestFolioLedger folio : folios) {
                folioMap.put(folio.getTransactionId(), folio);
            }

            Set<String> processedTransactions = new HashSet<>();
            int matched = 0;
            int discrepancies = 0;
            BigDecimal totalReconciled = BigDecimal.ZERO;
            BigDecimal totalVariance = BigDecimal.ZERO;

            // Track duplicate suspects by cabin + amount + minute
            Map<String, List<PosJournalEntry>> potentialDuplicates = new HashMap<>();

            for (PosJournalEntry journal : journals) {
                processedTransactions.add(journal.getPosTransactionId());
                String dupKey = journal.getCabinNumber() + "_" + journal.getAmount().stripTrailingZeros().toPlainString();
                potentialDuplicates.computeIfAbsent(dupKey, k -> new ArrayList<>()).add(journal);

                GuestFolioLedger matchedFolio = folioMap.get(journal.getPosTransactionId());
                if (matchedFolio == null) {
                    // UNMATCHED_POS
                    discrepancies++;
                    totalVariance = totalVariance.add(journal.getAmount());
                    createDiscrepancy(batch.getId(), journal.getPosTransactionId(), null,
                            journal.getCabinNumber(), journal.getPassengerName(), journal.getServiceArea(),
                            DiscrepancyType.UNMATCHED_POS, journal.getAmount(), BigDecimal.ZERO,
                            journal.getAmount(), journal.getTransactionTime());
                } else {
                    // Check Amount Mismatch
                    BigDecimal diff = journal.getAmount().subtract(matchedFolio.getAmount()).abs();
                    if (diff.compareTo(new BigDecimal("0.001")) > 0) {
                        discrepancies++;
                        totalVariance = totalVariance.add(diff);
                        createDiscrepancy(batch.getId(), journal.getPosTransactionId(), matchedFolio.getFolioId(),
                                journal.getCabinNumber(), journal.getPassengerName(), journal.getServiceArea(),
                                DiscrepancyType.AMOUNT_MISMATCH, journal.getAmount(), matchedFolio.getAmount(),
                                diff, journal.getTransactionTime());
                    } else {
                        matched++;
                        totalReconciled = totalReconciled.add(journal.getAmount());
                    }
                }
            }

            // Check UNMATCHED_FOLIO
            for (GuestFolioLedger folio : folios) {
                if (!processedTransactions.contains(folio.getTransactionId()) && !Boolean.TRUE.equals(folio.getIsReversal())) {
                    discrepancies++;
                    totalVariance = totalVariance.add(folio.getAmount());
                    createDiscrepancy(batch.getId(), folio.getTransactionId(), folio.getFolioId(),
                            folio.getCabinNumber(), folio.getPassengerName(), folio.getServiceArea(),
                            DiscrepancyType.UNMATCHED_FOLIO, BigDecimal.ZERO, folio.getAmount(),
                            folio.getAmount(), folio.getPostedAt());
                }
            }

            // Check DUPLICATE_SUSPECT in POS entries
            for (Map.Entry<String, List<PosJournalEntry>> entry : potentialDuplicates.entrySet()) {
                List<PosJournalEntry> list = entry.getValue();
                if (list.size() > 1) {
                    for (int i = 0; i < list.size(); i++) {
                        for (int j = i + 1; j < list.size(); j++) {
                            long seconds = Math.abs(ChronoUnit.SECONDS.between(list.get(i).getTransactionTime(), list.get(j).getTransactionTime()));
                            if (seconds <= 60) {
                                discrepancies++;
                                createDiscrepancy(batch.getId(), list.get(j).getPosTransactionId(), null,
                                        list.get(j).getCabinNumber(), list.get(j).getPassengerName(), list.get(j).getServiceArea(),
                                        DiscrepancyType.DUPLICATE_SUSPECT, list.get(j).getAmount(), BigDecimal.ZERO,
                                        list.get(j).getAmount(), list.get(j).getTransactionTime());
                            }
                        }
                    }
                }
            }

            batch.setCompletedAt(Instant.now());
            batch.setTotalTransactions(journals.size());
            batch.setMatchedCount(matched);
            batch.setDiscrepancyCount(discrepancies);
            batch.setTotalReconciledAmount(totalReconciled);
            batch.setTotalVarianceAmount(totalVariance);
            batch.setStatus(BatchStatus.COMPLETED);

            batch = batchRepository.save(batch);

            auditLogRepository.save(ReconciliationAuditLog.builder()
                    .action("RUN_RECONCILIATION")
                    .targetType("BATCH")
                    .targetId(batch.getBatchNumber())
                    .actorId(batch.getTriggeredBy())
                    .details("Reconciliation completed. Matched: " + matched + ", Discrepancies: " + discrepancies)
                    .build());

            return toBatchDto(batch);
        }
    }

    private void createDiscrepancy(Long batchId, String txId, String folioId, String cabin,
                                   String guest, String area, DiscrepancyType type,
                                   BigDecimal posAmount, BigDecimal folioAmount,
                                   BigDecimal variance, Instant txTime) {
        DiscrepancyRecord record = DiscrepancyRecord.builder()
                .batchId(batchId)
                .transactionId(txId)
                .folioId(folioId)
                .cabinNumber(cabin)
                .passengerName(guest)
                .serviceArea(area)
                .discrepancyType(type)
                .posAmount(posAmount)
                .folioAmount(folioAmount)
                .variance(variance)
                .status(DiscrepancyStatus.DETECTED)
                .transactionTimestamp(txTime)
                .build();
        discrepancyRepository.save(record);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationBatchDto> getBatches(Pageable pageable) {
        return batchRepository.findAll(pageable).map(this::toBatchDto);
    }

    @Transactional(readOnly = true)
    public ReconciliationBatchDto getBatchById(Long id) {
        ReconciliationBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẻ đối soát với ID: " + id));
        return toBatchDto(batch);
    }

    @Transactional(readOnly = true)
    public Page<DiscrepancyRecordDto> getDiscrepancies(Long batchId, DiscrepancyStatus status, Pageable pageable) {
        if (batchId != null) {
            return discrepancyRepository.findByBatchId(batchId, pageable).map(this::toDiscrepancyDto);
        }
        if (status != null) {
            return discrepancyRepository.findByStatus(status, pageable).map(this::toDiscrepancyDto);
        }
        return discrepancyRepository.findAll(pageable).map(this::toDiscrepancyDto);
    }

    @Transactional
    public DiscrepancyRecordDto resolveDiscrepancy(Long id, ResolveDiscrepancyRequest request) {
        DiscrepancyRecord record = discrepancyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bản ghi sai lệch với ID: " + id));

        if (record.getStatus() == DiscrepancyStatus.RESOLVED) {
            throw new BusinessRuleException("Bản ghi sai lệch này đã được xử lý hoàn tất trước đó");
        }

        record.setStatus(request.getStatus());
        record.setResolutionNotes(request.getResolutionNotes());
        record.setResolvedBy(request.getResolvedBy() != null ? request.getResolvedBy() : "STAFF");
        record.setResolvedAt(Instant.now());

        record = discrepancyRepository.save(record);

        auditLogRepository.save(ReconciliationAuditLog.builder()
                .action("RESOLVE_DISCREPANCY")
                .targetType("DISCREPANCY")
                .targetId(String.valueOf(record.getId()))
                .actorId(record.getResolvedBy())
                .details("Resolved discrepancy: " + record.getDiscrepancyType() + " with status " + record.getStatus() + ". Note: " + record.getResolutionNotes())
                .build());

        return toDiscrepancyDto(record);
    }

    public ReconciliationBatchDto toBatchDto(ReconciliationBatch b) {
        return ReconciliationBatchDto.builder()
                .id(b.getId())
                .batchNumber(b.getBatchNumber())
                .terminalId(b.getTerminalId())
                .serviceArea(b.getServiceArea())
                .startedAt(b.getStartedAt())
                .completedAt(b.getCompletedAt())
                .totalTransactions(b.getTotalTransactions())
                .matchedCount(b.getMatchedCount())
                .discrepancyCount(b.getDiscrepancyCount())
                .totalReconciledAmount(b.getTotalReconciledAmount())
                .totalVarianceAmount(b.getTotalVarianceAmount())
                .status(b.getStatus())
                .triggeredBy(b.getTriggeredBy())
                .notes(b.getNotes())
                .build();
    }

    public DiscrepancyRecordDto toDiscrepancyDto(DiscrepancyRecord d) {
        return DiscrepancyRecordDto.builder()
                .id(d.getId())
                .batchId(d.getBatchId())
                .transactionId(d.getTransactionId())
                .folioId(d.getFolioId())
                .cabinNumber(d.getCabinNumber())
                .passengerName(d.getPassengerName())
                .serviceArea(d.getServiceArea())
                .discrepancyType(d.getDiscrepancyType())
                .posAmount(d.getPosAmount())
                .folioAmount(d.getFolioAmount())
                .variance(d.getVariance())
                .status(d.getStatus())
                .resolutionNotes(d.getResolutionNotes())
                .resolvedBy(d.getResolvedBy())
                .resolvedAt(d.getResolvedAt())
                .transactionTimestamp(d.getTransactionTimestamp())
                .build();
    }
}

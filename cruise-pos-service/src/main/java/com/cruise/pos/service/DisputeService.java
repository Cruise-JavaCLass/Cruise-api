package com.cruise.pos.service;

import com.cruise.pos.dto.CreateDisputeRequest;
import com.cruise.pos.dto.DisputeCaseDto;
import com.cruise.pos.dto.DisputeHistoryDto;
import com.cruise.pos.dto.ResolveDisputeRequest;
import com.cruise.pos.entity.DisputeCase;
import com.cruise.pos.entity.DisputeHistory;
import com.cruise.pos.entity.GuestFolioLedger;
import com.cruise.pos.entity.ReconciliationAuditLog;
import com.cruise.pos.enums.DisputeStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.DisputeCaseRepository;
import com.cruise.pos.repository.DisputeHistoryRepository;
import com.cruise.pos.repository.GuestFolioLedgerRepository;
import com.cruise.pos.repository.ReconciliationAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private final DisputeCaseRepository disputeRepository;
    private final DisputeHistoryRepository historyRepository;
    private final GuestFolioLedgerRepository folioRepository;
    private final ReconciliationAuditLogRepository auditLogRepository;

    @Transactional
    public DisputeCaseDto createDispute(CreateDisputeRequest request) {
        log.info("Creating dispute case for transaction: {}", request.getTransactionId());

        // Require the transaction to exist in GuestFolioLedger and acquire pessimistic lock to prevent race conditions
        GuestFolioLedger originalFolio = folioRepository.findByTransactionIdForUpdate(request.getTransactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch gốc " + request.getTransactionId() + " trong sao kê phòng"));

        if (Boolean.TRUE.equals(originalFolio.getIsReversal())) {
            throw new BusinessRuleException("Không thể khiếu nại bút toán hoàn tiền");
        }

        if (!java.util.Objects.equals(originalFolio.getFolioId(), request.getFolioId())
                || !java.util.Objects.equals(originalFolio.getCabinNumber(), request.getCabinNumber())) {
            throw new BusinessRuleException("Mã Folio hoặc số cabin không khớp với giao dịch gốc");
        }

        // Check if an active dispute already exists for this transaction
        boolean activeDisputeExists = disputeRepository.existsByTransactionIdAndStatusIn(
                request.getTransactionId(),
                List.of(DisputeStatus.SUBMITTED, DisputeStatus.UNDER_INVESTIGATION, DisputeStatus.APPROVED_ADJUSTED)
        );
        if (activeDisputeExists) {
            throw new BusinessRuleException("Giao dịch " + request.getTransactionId() + " đang có khiếu nại đang xử lý hoặc đã được chấp thuận trước đó");
        }

        if (request.getDisputeAmount().compareTo(originalFolio.getAmount()) > 0) {
            throw new BusinessRuleException("Số tiền khiếu nại (" + request.getDisputeAmount() +
                    ") không được vượt quá số tiền giao dịch gốc (" + originalFolio.getAmount() + ")");
        }

        String disputeNumber = "DISP-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DisputeCase dispute = DisputeCase.builder()
                .disputeNumber(disputeNumber)
                .transactionId(request.getTransactionId())
                .folioId(request.getFolioId())
                .cabinNumber(request.getCabinNumber())
                .passengerId(request.getPassengerId())
                .passengerName(request.getPassengerName())
                .reason(request.getReason())
                .disputeAmount(request.getDisputeAmount())
                .passengerNote(request.getPassengerNote())
                .evidenceUrl(request.getEvidenceUrl())
                .status(DisputeStatus.SUBMITTED)
                .build();

        dispute = disputeRepository.save(dispute);

        recordHistory(dispute.getId(), null, DisputeStatus.SUBMITTED,
                request.getPassengerName() != null ? request.getPassengerName() : "PASSENGER",
                "Khiếu nại được khởi tạo với lý do: " + request.getReason());

        auditLogRepository.save(ReconciliationAuditLog.builder()
                .action("CREATE_DISPUTE")
                .targetType("DISPUTE")
                .targetId(dispute.getDisputeNumber())
                .actorId(request.getCabinNumber())
                .details("Created dispute " + dispute.getDisputeNumber() + " for tx " + request.getTransactionId())
                .build());

        return toDto(dispute, true);
    }

    @Transactional
    public DisputeCaseDto updateStatus(Long id, DisputeStatus newStatus, String actorId, String note) {
        DisputeCase dispute = disputeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + id));

        DisputeStatus oldStatus = dispute.getStatus();

        // Validate FSM transitions
        if (oldStatus == DisputeStatus.APPROVED_ADJUSTED || oldStatus == DisputeStatus.REJECTED) {
            throw new BusinessRuleException("Không thể thay đổi trạng thái của hồ sơ khiếu nại đã đóng (" + oldStatus + ")");
        }

        // Resolving to APPROVED_ADJUSTED or REJECTED must go through resolveDispute
        if (newStatus == DisputeStatus.APPROVED_ADJUSTED || newStatus == DisputeStatus.REJECTED) {
            throw new BusinessRuleException("Quyết định phê duyệt hoặc từ chối khiếu nại bắt buộc phải thực hiện qua chức năng Thẩm định / Giải quyết khiếu nại (resolveDispute)");
        }

        if (newStatus != DisputeStatus.UNDER_INVESTIGATION) {
            throw new BusinessRuleException("Trạng thái trung gian hợp lệ duy nhất là Đang điều tra (UNDER_INVESTIGATION)");
        }

        dispute.setStatus(newStatus);
        dispute.setReviewerId(actorId);
        dispute.setReviewerNote(note);
        dispute = disputeRepository.save(dispute);

        recordHistory(dispute.getId(), oldStatus, newStatus, actorId, note);

        auditLogRepository.save(ReconciliationAuditLog.builder()
                .action("UPDATE_DISPUTE_STATUS")
                .targetType("DISPUTE")
                .targetId(dispute.getDisputeNumber())
                .actorId(actorId)
                .details("Changed status from " + oldStatus + " to " + newStatus)
                .build());

        return toDto(dispute, true);
    }

    @Transactional
    public DisputeCaseDto resolveDispute(Long id, ResolveDisputeRequest request) {
        DisputeCase dispute = disputeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + id));

        DisputeStatus oldStatus = dispute.getStatus();
        if (oldStatus == DisputeStatus.APPROVED_ADJUSTED || oldStatus == DisputeStatus.REJECTED) {
            throw new BusinessRuleException("Hồ sơ khiếu nại này đã được đóng với trạng thái " + oldStatus);
        }

        DisputeStatus decision = request.getDecisionStatus();
        if (decision != DisputeStatus.APPROVED_ADJUSTED && decision != DisputeStatus.REJECTED) {
            throw new BusinessRuleException("Quyết định giải quyết chỉ có thể là APPROVED_ADJUSTED hoặc REJECTED");
        }

        String reviewer = request.getReviewerId() != null ? request.getReviewerId() : "PURSER_OFFICER";

        if (decision == DisputeStatus.APPROVED_ADJUSTED) {
            BigDecimal refund = request.getApprovedRefundAmount() != null ?
                    request.getApprovedRefundAmount() : dispute.getDisputeAmount();

            if (refund.compareTo(BigDecimal.ZERO) <= 0 || refund.compareTo(dispute.getDisputeAmount()) > 0) {
                throw new BusinessRuleException("Số tiền hoàn trả hợp lệ phải > 0 và <= số tiền khiếu nại ban đầu (" + dispute.getDisputeAmount() + ")");
            }

            // Anti-Double-Refund check
            if (dispute.getAdjustmentTransactionId() != null) {
                throw new BusinessRuleException("Khiếu nại này đã phát sinh bút toán hoàn tiền trước đó: " + dispute.getAdjustmentTransactionId());
            }

            String adjTxId = "ADJ-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            GuestFolioLedger adjustment = GuestFolioLedger.builder()
                    .folioId(dispute.getFolioId())
                    .cabinNumber(dispute.getCabinNumber())
                    .passengerName(dispute.getPassengerName())
                    .transactionId(adjTxId)
                    .description("Hoàn tiền khiếu nại chi phí #" + dispute.getDisputeNumber())
                    .serviceArea("PURSER_DESK")
                    .amount(refund.negate()) // Negative credit to folio
                    .isReversal(true)
                    .linkedDisputeId(dispute.getId())
                    .postedAt(Instant.now())
                    .build();

            folioRepository.save(adjustment);

            dispute.setAdjustmentTransactionId(adjTxId);
            dispute.setRefundAmount(refund);
            dispute.setStatus(DisputeStatus.APPROVED_ADJUSTED);
            dispute.setReviewerId(reviewer);
            dispute.setReviewerNote(request.getReviewerNote());
            dispute.setResolvedAt(Instant.now());

            recordHistory(dispute.getId(), oldStatus, DisputeStatus.APPROVED_ADJUSTED, reviewer,
                    "Phê duyệt hoàn tiền " + refund + " USD. Mã bút toán: " + adjTxId + ". Ghi chú: " + request.getReviewerNote());
        } else {
            dispute.setStatus(DisputeStatus.REJECTED);
            dispute.setReviewerId(reviewer);
            dispute.setReviewerNote(request.getReviewerNote());
            dispute.setResolvedAt(Instant.now());

            recordHistory(dispute.getId(), oldStatus, DisputeStatus.REJECTED, reviewer,
                    "Từ chối khiếu nại. Lý do: " + request.getReviewerNote());
        }

        dispute = disputeRepository.save(dispute);

        auditLogRepository.save(ReconciliationAuditLog.builder()
                .action("RESOLVE_DISPUTE")
                .targetType("DISPUTE")
                .targetId(dispute.getDisputeNumber())
                .actorId(reviewer)
                .details("Resolved as " + decision + ", adjustment tx: " + dispute.getAdjustmentTransactionId())
                .build());

        return toDto(dispute, true);
    }

    @Transactional(readOnly = true)
    public Page<DisputeCaseDto> getDisputes(DisputeStatus status, String cabinNumber, Pageable pageable) {
        if (status != null && cabinNumber != null && !cabinNumber.isBlank()) {
            return disputeRepository.findByStatusAndCabinNumber(status, cabinNumber, pageable).map(d -> toDto(d, false));
        }
        if (status != null) {
            return disputeRepository.findByStatus(status, pageable).map(d -> toDto(d, false));
        }
        if (cabinNumber != null && !cabinNumber.isBlank()) {
            return disputeRepository.findByCabinNumber(cabinNumber, pageable).map(d -> toDto(d, false));
        }
        return disputeRepository.findAll(pageable).map(d -> toDto(d, false));
    }

    @Transactional(readOnly = true)
    public DisputeCaseDto getDisputeById(Long id) {
        DisputeCase dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + id));
        return toDto(dispute, true);
    }

    private void recordHistory(Long disputeId, DisputeStatus oldStatus, DisputeStatus newStatus, String actor, String notes) {
        historyRepository.save(DisputeHistory.builder()
                .disputeId(disputeId)
                .previousStatus(oldStatus)
                .newStatus(newStatus)
                .actionBy(actor)
                .notes(notes)
                .timestamp(Instant.now())
                .build());
    }

    public DisputeCaseDto toDto(DisputeCase d, boolean includeHistories) {
        List<DisputeHistoryDto> histories = null;
        if (includeHistories) {
            histories = historyRepository.findByDisputeIdOrderByTimestampDesc(d.getId()).stream()
                    .map(h -> DisputeHistoryDto.builder()
                            .id(h.getId())
                            .disputeId(h.getDisputeId())
                            .previousStatus(h.getPreviousStatus())
                            .newStatus(h.getNewStatus())
                            .actionBy(h.getActionBy())
                            .notes(h.getNotes())
                            .timestamp(h.getTimestamp())
                            .build())
                    .collect(Collectors.toList());
        }

        return DisputeCaseDto.builder()
                .id(d.getId())
                .disputeNumber(d.getDisputeNumber())
                .transactionId(d.getTransactionId())
                .folioId(d.getFolioId())
                .cabinNumber(d.getCabinNumber())
                .passengerId(d.getPassengerId())
                .passengerName(d.getPassengerName())
                .reason(d.getReason())
                .disputeAmount(d.getDisputeAmount())
                .refundAmount(d.getRefundAmount())
                .passengerNote(d.getPassengerNote())
                .evidenceUrl(d.getEvidenceUrl())
                .status(d.getStatus())
                .reviewerId(d.getReviewerId())
                .reviewerNote(d.getReviewerNote())
                .adjustmentTransactionId(d.getAdjustmentTransactionId())
                .createdAt(d.getCreatedAt())
                .resolvedAt(d.getResolvedAt())
                .histories(histories)
                .build();
    }
}

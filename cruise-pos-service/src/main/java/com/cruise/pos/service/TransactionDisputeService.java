package com.cruise.pos.service;

import com.cruise.pos.entity.CabinFolio;
import com.cruise.pos.entity.FolioDispute;
import com.cruise.pos.entity.FolioDisputeAuditEntry;
import com.cruise.pos.entity.PosTransaction;
import com.cruise.pos.enums.CabinFolioStatus;
import com.cruise.pos.enums.FolioDisputeStatus;
import com.cruise.pos.enums.TransactionStatus;
import com.cruise.pos.enums.TransactionType;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.CabinFolioRepository;
import com.cruise.pos.repository.FolioDisputeAuditEntryRepository;
import com.cruise.pos.repository.FolioDisputeRepository;
import com.cruise.pos.repository.PosTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Quy trình khiếu nại giao dịch POS và hoàn tiền minh bạch.
 *
 * <p>State machine: PENDING -> IN_REVIEW -> (APPROVED | REJECTED).
 * Mỗi bước đều ghi một dòng nhật ký kiểm toán bất biến.
 * Khi duyệt: sinh giao dịch REFUND_ADJUSTMENT và ghi giảm công nợ của folio cabin.
 *
 * <p>Việc chỉ cho nhân viên FINANCE_FRONT_DESK gọi {@link #startReview} và {@link #reviewDispute}
 * thuộc tầng API (RBAC); service chỉ ghi nhận mã nhân viên thực hiện.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TransactionDisputeService {

    private static final List<FolioDisputeStatus> ACTIVE_STATUSES =
            List.of(FolioDisputeStatus.PENDING, FolioDisputeStatus.IN_REVIEW);
    private static final List<FolioDisputeStatus> APPROVED_STATUSES = List.of(FolioDisputeStatus.APPROVED);
    private static final int MAX_REASON_LENGTH = 1000;
    private static final int MAX_NOTE_LENGTH = 512;

    private final FolioDisputeRepository disputeRepository;
    private final FolioDisputeAuditEntryRepository auditRepository;
    private final PosTransactionRepository transactionRepository;
    private final CabinFolioRepository folioRepository;

    /** Khách khiếu nại toàn bộ số tiền còn có thể hoàn của giao dịch. */
    public FolioDispute raiseDispute(Long passengerId, Long transactionId, String reason) {
        return raiseDispute(passengerId, transactionId, reason, null);
    }

    /**
     * Khách gửi yêu cầu khiếu nại một giao dịch bị tính nhầm.
     *
     * @param disputedAmount số tiền khiếu nại; null nghĩa là toàn bộ số tiền còn có thể hoàn
     */
    public FolioDispute raiseDispute(Long passengerId, Long transactionId, String reason, BigDecimal disputedAmount) {
        if (passengerId == null || transactionId == null) {
            throw new BusinessRuleException("Mã hành khách và mã giao dịch không được để trống");
        }
        String cleanReason = requireText("Lý do khiếu nại", reason, MAX_REASON_LENGTH);

        PosTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch ID: " + transactionId));

        boolean isCharge = transaction.getType() == null || transaction.getType() == TransactionType.CHARGE;
        boolean isDisputable = transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.PARTIALLY_REFUNDED;
        if (!isCharge || !isDisputable) {
            throw new BusinessRuleException("Chỉ có thể khiếu nại giao dịch thanh toán đã hoàn tất");
        }
        if (transaction.getPassengerId() != null && !transaction.getPassengerId().equals(passengerId)) {
            throw new BusinessRuleException("Giao dịch này không thuộc về hành khách đã gửi khiếu nại");
        }
        if (disputeRepository.existsByTransaction_IdAndStatusIn(transactionId, ACTIVE_STATUSES)) {
            throw new BusinessRuleException("Giao dịch này đang có khiếu nại chưa được xử lý xong");
        }
        if (transaction.getCabinNumber() == null || transaction.getCabinNumber().isBlank()) {
            throw new BusinessRuleException("Giao dịch không gắn với cabin nên không thể khiếu nại qua folio");
        }
        CabinFolio folio = folioRepository
                .findFirstByCabinNumberAndStatusInOrderByIdDesc(transaction.getCabinNumber(),
                        List.of(CabinFolioStatus.OPEN, CabinFolioStatus.SUSPENDED))
                .orElseThrow(() -> new BusinessRuleException(
                        "Không tìm thấy folio đang mở của cabin " + transaction.getCabinNumber()));

        BigDecimal refundable = refundableAmount(transaction);
        if (refundable.signum() <= 0) {
            throw new BusinessRuleException("Giao dịch không còn số tiền nào có thể hoàn");
        }
        BigDecimal amount = disputedAmount == null
                ? refundable
                : BillingMoney.requirePositive("Số tiền khiếu nại", disputedAmount);
        if (amount.compareTo(refundable) > 0) {
            throw new BusinessRuleException(String.format(
                    "Số tiền khiếu nại %s vượt số tiền còn có thể hoàn %s", amount, refundable));
        }

        FolioDispute dispute = disputeRepository.save(FolioDispute.builder()
                .folio(folio)
                .transaction(transaction)
                .passengerId(passengerId)
                .disputeReason(cleanReason)
                .disputedAmount(amount)
                .status(FolioDisputeStatus.PENDING)
                .build());
        recordAudit(dispute, "RAISED", null, FolioDisputeStatus.PENDING,
                String.valueOf(passengerId), cleanReason, amount);
        log.info("Hành khách {} gửi khiếu nại giao dịch {} số tiền {}", passengerId, transactionId, amount);
        return dispute;
    }

    /** Nhân viên tài chính nhận xử lý khiếu nại: PENDING -> IN_REVIEW. */
    public FolioDispute startReview(Long disputeId, String reviewerStaffId) {
        String staffId = requireText("Mã nhân viên", reviewerStaffId, 64);
        FolioDispute dispute = lockDispute(disputeId);

        FolioDisputeStatus from = dispute.getStatus();
        dispute.startReview(staffId);
        FolioDispute saved = disputeRepository.save(dispute);
        recordAudit(saved, "REVIEW_STARTED", from, FolioDisputeStatus.IN_REVIEW, staffId, null, saved.getDisputedAmount());
        return saved;
    }

    /**
     * Duyệt hoặc bác bỏ khiếu nại đang IN_REVIEW.
     * Khi duyệt: tạo giao dịch REFUND_ADJUSTMENT và ghi giảm số dư nợ trên CabinFolio.
     */
    public FolioDispute reviewDispute(Long disputeId, boolean approved, String reviewerNotes, String reviewerStaffId) {
        String staffId = requireText("Mã nhân viên", reviewerStaffId, 64);
        String notes = approved
                ? optionalText(reviewerNotes, MAX_NOTE_LENGTH)
                : requireText("Ghi chú bác bỏ", reviewerNotes, MAX_NOTE_LENGTH);

        FolioDispute dispute = lockDispute(disputeId);
        if (dispute.getStatus() != FolioDisputeStatus.IN_REVIEW) {
            throw new BusinessRuleException("Khiếu nại phải ở trạng thái IN_REVIEW mới có thể duyệt hoặc bác bỏ (hiện tại: "
                    + dispute.getStatus() + ")");
        }

        Instant now = Instant.now();
        FolioDispute saved;
        if (approved) {
            Long refundTransactionId = applyRefund(dispute, staffId, notes);
            dispute.approve(staffId, notes, refundTransactionId, now);
            saved = disputeRepository.save(dispute);
            recordAudit(saved, "APPROVED", FolioDisputeStatus.IN_REVIEW, FolioDisputeStatus.APPROVED,
                    staffId, notes, saved.getDisputedAmount());
        } else {
            dispute.reject(staffId, notes, now);
            saved = disputeRepository.save(dispute);
            recordAudit(saved, "REJECTED", FolioDisputeStatus.IN_REVIEW, FolioDisputeStatus.REJECTED,
                    staffId, notes, saved.getDisputedAmount());
        }
        log.info("Khiếu nại {} đã được {} bởi {}", disputeId, approved ? "duyệt" : "bác bỏ", staffId);
        return saved;
    }

    // ---------------------------------------------------------------------

    private Long applyRefund(FolioDispute dispute, String staffId, String notes) {
        PosTransaction original = dispute.getTransaction();
        BigDecimal amount = dispute.getDisputedAmount();

        if (amount.compareTo(refundableAmount(original)) > 0) {
            throw new BusinessRuleException("Số tiền khiếu nại vượt số tiền còn có thể hoàn của giao dịch gốc");
        }
        CabinFolio folio = folioRepository.findByIdForUpdate(dispute.getFolio().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy folio của khiếu nại"));
        if (folio.getStatus() == CabinFolioStatus.SETTLED) {
            throw new BusinessRuleException("Folio " + folio.getCabinNumber() + " đã tất toán, không thể ghi giảm công nợ");
        }

        PosTransaction refund = transactionRepository.save(PosTransaction.builder()
                .transactionUuid("RFA-" + UUID.randomUUID())
                .stationId(original.getStationId())
                .cashierId(staffId)
                .passengerId(dispute.getPassengerId())
                .cabinNumber(original.getCabinNumber())
                .guestName(original.getGuestName())
                .paymentMethod(original.getPaymentMethod())
                .type(TransactionType.REFUND_ADJUSTMENT)
                .status(TransactionStatus.COMPLETED)
                .subtotal(amount)
                .totalAmount(amount)
                .currency(original.getCurrency())
                .originalTransactionId(original.getId())
                .notes(truncate("Hoàn tiền khiếu nại #" + dispute.getId()
                        + (notes == null ? "" : ": " + notes), MAX_NOTE_LENGTH))
                .build());

        folio.credit(amount);
        folioRepository.save(folio);
        return refund.getId();
    }

    /** Số tiền còn có thể hoàn = còn lại theo giao dịch gốc, trừ các khoản khiếu nại đã duyệt. */
    private BigDecimal refundableAmount(PosTransaction transaction) {
        BigDecimal alreadyApproved = BillingMoney.nullSafe(
                disputeRepository.sumDisputedAmount(transaction.getId(), APPROVED_STATUSES));
        return transaction.getRemainingRefundableAmount().subtract(alreadyApproved);
    }

    private FolioDispute lockDispute(Long disputeId) {
        if (disputeId == null) {
            throw new BusinessRuleException("Mã khiếu nại không được để trống");
        }
        return disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại ID: " + disputeId));
    }

    private void recordAudit(FolioDispute dispute, String action, FolioDisputeStatus from,
                             FolioDisputeStatus to, String actorId, String notes, BigDecimal amount) {
        auditRepository.save(FolioDisputeAuditEntry.builder()
                .dispute(dispute)
                .action(action)
                .fromStatus(from)
                .toStatus(to)
                .actorId(actorId)
                .notes(truncate(notes, MAX_REASON_LENGTH))
                .amount(amount)
                .build());
    }

    private static String requireText(String label, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(label + " không được để trống");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessRuleException(label + " không được vượt quá " + maxLength + " ký tự");
        }
        return trimmed;
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText("Ghi chú", value, maxLength);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.FolioDisputeStatus;
import com.cruise.pos.exception.BusinessRuleException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Khiếu nại giao dịch POS. State machine: PENDING -> IN_REVIEW -> (APPROVED | REJECTED).
 */
@Entity
@Table(name = "billing_folio_disputes", indexes = {
        @Index(name = "idx_billing_dispute_tx_status", columnList = "transaction_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"folio", "transaction"})
public class FolioDispute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folio_id", nullable = false)
    private CabinFolio folio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private PosTransaction transaction;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "dispute_reason", nullable = false, length = 1000)
    private String disputeReason;

    @Column(name = "disputed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal disputedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private FolioDisputeStatus status = FolioDisputeStatus.PENDING;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "reviewer_staff_id", length = 64)
    private String reviewerStaffId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "refund_transaction_id")
    private Long refundTransactionId;

    /** PENDING -> IN_REVIEW */
    public void startReview(String staffId) {
        requireStatus(FolioDisputeStatus.PENDING, "bắt đầu xem xét");
        this.status = FolioDisputeStatus.IN_REVIEW;
        this.reviewerStaffId = staffId;
    }

    /** IN_REVIEW -> APPROVED */
    public void approve(String staffId, String notes, Long refundTransactionId, Instant now) {
        requireStatus(FolioDisputeStatus.IN_REVIEW, "duyệt");
        this.status = FolioDisputeStatus.APPROVED;
        this.reviewerStaffId = staffId;
        this.resolutionNotes = notes;
        this.refundTransactionId = refundTransactionId;
        this.reviewedAt = now;
    }

    /** IN_REVIEW -> REJECTED */
    public void reject(String staffId, String notes, Instant now) {
        requireStatus(FolioDisputeStatus.IN_REVIEW, "bác bỏ");
        this.status = FolioDisputeStatus.REJECTED;
        this.reviewerStaffId = staffId;
        this.resolutionNotes = notes;
        this.reviewedAt = now;
    }

    private void requireStatus(FolioDisputeStatus expected, String action) {
        if (status != expected) {
            throw new BusinessRuleException(String.format(
                    "Không thể %s khiếu nại ở trạng thái %s (yêu cầu trạng thái %s)", action, status, expected));
        }
    }
}

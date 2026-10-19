package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.DisputeReason;
import com.cruise.pos.enums.DisputeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pos_dispute_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeCase extends BaseEntity {

    @Column(name = "dispute_number", nullable = false, unique = true, length = 64)
    private String disputeNumber;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Column(name = "folio_id", nullable = false, length = 64)
    private String folioId;

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Column(name = "passenger_id", length = 64)
    private String passengerId;

    @Column(name = "passenger_name", length = 128)
    private String passengerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 64)
    private DisputeReason reason;

    @Column(name = "dispute_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal disputeAmount;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "passenger_note", length = 1000)
    private String passengerNote;

    @Column(name = "evidence_url", length = 512)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.SUBMITTED;

    @Column(name = "reviewer_id", length = 64)
    private String reviewerId;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "adjustment_transaction_id", length = 64)
    private String adjustmentTransactionId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}

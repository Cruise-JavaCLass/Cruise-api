package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.DiscrepancyStatus;
import com.cruise.pos.enums.DiscrepancyType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_discrepancies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscrepancyRecord extends BaseEntity {

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "folio_id", length = 64)
    private String folioId;

    @Column(name = "cabin_number", length = 32)
    private String cabinNumber;

    @Column(name = "passenger_name", length = 128)
    private String passengerName;

    @Column(name = "service_area", length = 64)
    private String serviceArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false, length = 32)
    private DiscrepancyType discrepancyType;

    @Column(name = "pos_amount", precision = 12, scale = 2)
    private BigDecimal posAmount;

    @Column(name = "folio_amount", precision = 12, scale = 2)
    private BigDecimal folioAmount;

    @Column(name = "variance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal variance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private DiscrepancyStatus status = DiscrepancyStatus.DETECTED;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "transaction_timestamp")
    private Instant transactionTimestamp;
}

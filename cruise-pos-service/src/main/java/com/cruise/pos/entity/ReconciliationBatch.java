package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reconciliation_batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationBatch extends BaseEntity {

    @Column(name = "batch_number", nullable = false, unique = true, length = 64)
    private String batchNumber;

    @Column(name = "terminal_id", length = 64)
    private String terminalId;

    @Column(name = "service_area", length = 64)
    private String serviceArea;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_transactions", nullable = false)
    @Builder.Default
    private Integer totalTransactions = 0;

    @Column(name = "matched_count", nullable = false)
    @Builder.Default
    private Integer matchedCount = 0;

    @Column(name = "discrepancy_count", nullable = false)
    @Builder.Default
    private Integer discrepancyCount = 0;

    @Column(name = "total_reconciled_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalReconciledAmount = BigDecimal.ZERO;

    @Column(name = "total_variance_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalVarianceAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private BatchStatus status = BatchStatus.IN_PROGRESS;

    @Column(name = "triggered_by", length = 64)
    private String triggeredBy;

    @Column(name = "notes", length = 512)
    private String notes;
}

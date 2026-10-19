package com.cruise.pos.dto;

import com.cruise.pos.enums.BatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationBatchDto {
    private Long id;
    private String batchNumber;
    private String terminalId;
    private String serviceArea;
    private Instant startedAt;
    private Instant completedAt;
    private Integer totalTransactions;
    private Integer matchedCount;
    private Integer discrepancyCount;
    private BigDecimal totalReconciledAmount;
    private BigDecimal totalVarianceAmount;
    private BatchStatus status;
    private String triggeredBy;
    private String notes;
}

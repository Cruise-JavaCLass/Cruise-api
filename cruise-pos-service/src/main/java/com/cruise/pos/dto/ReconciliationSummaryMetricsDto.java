package com.cruise.pos.dto;

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
public class ReconciliationSummaryMetricsDto {
    private BigDecimal totalReconciledAmount;
    private BigDecimal totalDisputedAmount;
    private BigDecimal totalVarianceAmount;
    private long totalTransactionsCount;
    private long openDisputesCount;
    private long resolvedDisputesCount;
    private long pendingDiscrepanciesCount;
    private double discrepancyRatePercent;
    private boolean criticalAlertActive;
    private String alertMessage;
    private Instant generatedAt;
}

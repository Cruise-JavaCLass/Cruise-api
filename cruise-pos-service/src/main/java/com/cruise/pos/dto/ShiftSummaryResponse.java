package com.cruise.pos.dto;

import com.cruise.pos.enums.ShiftStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftSummaryResponse {
    private Long shiftId;
    private String stationId;
    private String cashierId;
    private ShiftStatus status;
    private Instant openedAt;
    private Instant closedAt;
    private String reportType; // "X_REPORT" or "Z_REPORT"
    private BigDecimal startingCash;
    private BigDecimal endingCash;
    private BigDecimal totalSales;
    private BigDecimal totalRefunds;
    private BigDecimal netRevenue;
    private Integer transactionCount;
    private Map<String, BigDecimal> revenueByPaymentMethod;
    private Map<String, BigDecimal> revenueByCategory;
}

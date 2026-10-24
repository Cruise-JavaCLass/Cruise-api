package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalReportResponse {

    private BigDecimal totalGrossRevenue;
    private BigDecimal totalNetRevenue;
    private BigDecimal totalDiscountAmount;
    private BigDecimal totalTaxAmount;
    private Long totalTransactionCount;
    private BigDecimal averageTicketSize;

    private List<DimensionBreakdownDto> breakdown;
    private Map<String, BigDecimal> revenueByPaymentMethod;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionBreakdownDto {
        private String key;
        private String label;
        private Long transactionCount;
        private BigDecimal grossRevenue;
        private BigDecimal netRevenue;
        private Double revenuePercentage;
    }
}

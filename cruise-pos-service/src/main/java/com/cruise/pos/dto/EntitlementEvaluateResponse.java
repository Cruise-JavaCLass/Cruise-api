package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitlementEvaluateResponse {

    private Long passengerId;
    private String cabinNumber;
    private BigDecimal originalTotal;
    private BigDecimal packageDiscountTotal;
    private BigDecimal netPayableTotal;
    private List<ItemEntitlementResult> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemEntitlementResult {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal grossAmount;
        private BigDecimal coveredAmount;
        private BigDecimal outOfPocketAmount;
        private Boolean fullyCovered;
        private String appliedPackageName;
        private Long appliedEntitlementId;
        private Integer quotaDeducted;
    }
}

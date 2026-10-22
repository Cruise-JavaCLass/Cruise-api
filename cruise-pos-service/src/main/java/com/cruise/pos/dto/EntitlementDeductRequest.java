package com.cruise.pos.dto;

import jakarta.validation.constraints.NotNull;
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
public class EntitlementDeductRequest {

    @NotNull(message = "Passenger ID must not be null")
    private Long passengerId;

    private String transactionId;

    @NotNull(message = "Deduction items must not be null")
    private List<DeductItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeductItem {
        @NotNull(message = "Entitlement ID must not be null")
        private Long entitlementId;
        private String productName;
        @NotNull(message = "Units to deduct must not be null")
        private Integer units;
        private BigDecimal amountCovered;
    }
}

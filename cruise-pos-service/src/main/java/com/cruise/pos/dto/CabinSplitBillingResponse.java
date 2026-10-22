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
public class CabinSplitBillingResponse {
    private String cabinNumber;
    private BigDecimal totalAmount;
    private Integer splitCount;
    private List<SplitShareResult> splits;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitShareResult {
        private Long passengerId;
        private String guestName;
        private BigDecimal amount;
        private Long transactionId;
        private String transactionUuid;
        private boolean signatureArchived;
        private BigDecimal remainingMemberLimit;
    }
}

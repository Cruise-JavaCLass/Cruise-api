package com.cruise.pos.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitlementRollbackRequest {

    @NotNull(message = "Passenger ID must not be null")
    private Long passengerId;

    private String transactionId;

    @NotNull(message = "Rollback items must not be null")
    private List<RollbackItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollbackItem {
        @NotNull(message = "Entitlement ID must not be null")
        private Long entitlementId;
        @NotNull(message = "Units to rollback must not be null")
        private Integer units;
        private String reason;
    }
}

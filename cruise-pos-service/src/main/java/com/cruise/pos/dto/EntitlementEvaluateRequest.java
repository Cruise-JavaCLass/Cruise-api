package com.cruise.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class EntitlementEvaluateRequest {

    @NotNull(message = "Passenger ID must not be null")
    private Long passengerId;

    private String cabinNumber;

    @NotNull(message = "Items list must not be null")
    @Valid
    private List<EvaluateItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluateItem {
        private String productId;
        @NotBlank(message = "Product name must not be blank")
        private String productName;
        private String category;
        @NotNull(message = "Quantity must not be null")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;
        @NotNull(message = "Unit price must not be null")
        @DecimalMin("0.00")
        private BigDecimal unitPrice;
    }
}

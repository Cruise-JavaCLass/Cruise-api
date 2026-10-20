package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemCalculationResult {
    private Long productId;
    private String sku;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal modifierUnitTotal;
    private BigDecimal grossLineTotal;
    private BigDecimal discountAmount;
    private BigDecimal netLineTotal;
    private Boolean happyHourApplied;
    @Builder.Default
    private List<String> appliedModifierNames = new ArrayList<>();
}

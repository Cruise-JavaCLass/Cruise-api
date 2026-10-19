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
public class CartPriceCalculationResponse {

    private BigDecimal subtotal;
    private BigDecimal happyHourDiscount;
    private BigDecimal taxableSubtotal;
    private BigDecimal maritimeTax;
    private BigDecimal taxRatePercent;
    private BigDecimal serviceCharge;
    private BigDecimal serviceChargePercent;
    private BigDecimal grandTotal;

    private Boolean isDutyFree;
    private Boolean isHappyHourActive;
    private String happyHourDescription;

    @Builder.Default
    private List<CartItemCalculationResult> items = new ArrayList<>();
}

package com.cruise.activity.engine;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calculation Engine cho quyết toán nhà cung cấp tour (KAN-332, AC1)
 * Formula:
 * BillablePax = MAX(ActualPax, MinGuaranteePax)
 * BaseAmount = BillablePax * AgreedUnitRate
 * TotalAmount = BaseAmount + AdjustedAmount (ExtraServices - Penalties)
 */
@Component
public class SettlementCalculationEngine {

    public record CalculationResult(
            int billablePax,
            BigDecimal baseAmount,
            BigDecimal adjustedAmount,
            BigDecimal totalAmount
    ) {}

    public CalculationResult calculate(
            int actualPax,
            int minGuaranteePax,
            BigDecimal agreedUnitRate,
            BigDecimal totalAdjustments
    ) {
        int safeActualPax = Math.max(0, actualPax);
        int safeMinPax = Math.max(0, minGuaranteePax);
        BigDecimal safeRate = Objects.requireNonNullElse(agreedUnitRate, BigDecimal.ZERO);
        if (safeRate.compareTo(BigDecimal.ZERO) < 0) {
            safeRate = BigDecimal.ZERO;
        }
        BigDecimal safeAdjustments = Objects.requireNonNullElse(totalAdjustments, BigDecimal.ZERO);

        int billablePax = Math.max(safeActualPax, safeMinPax);
        BigDecimal baseAmount = safeRate.multiply(BigDecimal.valueOf(billablePax))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = baseAmount.add(safeAdjustments).setScale(2, RoundingMode.HALF_UP);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return new CalculationResult(billablePax, baseAmount, safeAdjustments.setScale(2, RoundingMode.HALF_UP), totalAmount);
    }
}

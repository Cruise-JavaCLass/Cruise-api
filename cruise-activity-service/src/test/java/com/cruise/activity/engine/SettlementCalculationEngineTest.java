package com.cruise.activity.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementCalculationEngineTest {

    private SettlementCalculationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SettlementCalculationEngine();
    }

    @Test
    @DisplayName("ActualPax > MinGuaranteePax: BillablePax phải bằng ActualPax")
    void testCalculate_whenActualExceedsMinGuarantee() {
        SettlementCalculationEngine.CalculationResult result = engine.calculate(
                45, // actual
                30, // min guarantee
                BigDecimal.valueOf(100.0), // rate
                BigDecimal.valueOf(250.0) // adjustments
        );

        assertThat(result.billablePax()).isEqualTo(45);
        assertThat(result.baseAmount()).isEqualByComparingTo("4500.00");
        assertThat(result.adjustedAmount()).isEqualByComparingTo("250.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("4750.00");
    }

    @Test
    @DisplayName("ActualPax < MinGuaranteePax: BillablePax phải bằng MinGuaranteePax")
    void testCalculate_whenActualLessThanMinGuarantee() {
        SettlementCalculationEngine.CalculationResult result = engine.calculate(
                20, // actual
                40, // min guarantee
                BigDecimal.valueOf(80.0), // rate
                BigDecimal.valueOf(-150.0) // penalties
        );

        assertThat(result.billablePax()).isEqualTo(40);
        assertThat(result.baseAmount()).isEqualByComparingTo("3200.00");
        assertThat(result.adjustedAmount()).isEqualByComparingTo("-150.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("3050.00");
    }

    @Test
    @DisplayName("Penalties lớn hơn BaseAmount: TotalAmount không được âm (sàn 0)")
    void testCalculate_whenPenaltiesExceedBaseAmount() {
        SettlementCalculationEngine.CalculationResult result = engine.calculate(
                5,
                5,
                BigDecimal.valueOf(50.0),
                BigDecimal.valueOf(-1000.0)
        );

        assertThat(result.billablePax()).isEqualTo(5);
        assertThat(result.baseAmount()).isEqualByComparingTo("250.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Xử lý giá trị null hoặc âm an toàn")
    void testCalculate_withNullAndNegativeInputs() {
        SettlementCalculationEngine.CalculationResult result = engine.calculate(
                -5,
                -10,
                null,
                null
        );

        assertThat(result.billablePax()).isEqualTo(0);
        assertThat(result.baseAmount()).isEqualByComparingTo("0.00");
        assertThat(result.adjustedAmount()).isEqualByComparingTo("0.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
    }
}

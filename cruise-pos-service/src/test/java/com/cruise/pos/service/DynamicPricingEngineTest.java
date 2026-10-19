package com.cruise.pos.service;

import com.cruise.pos.dto.HappyHourRuleDto;
import com.cruise.pos.entity.PosHappyHourRule;
import com.cruise.pos.entity.PosProduct;
import com.cruise.pos.repository.PosHappyHourRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamicPricingEngine Unit Tests")
class DynamicPricingEngineTest {

    @Mock
    private PosHappyHourRuleRepository happyHourRuleRepository;

    @InjectMocks
    private DynamicPricingEngine pricingEngine;

    @Test
    @DisplayName("isRuleActive returns true when current time is inside Happy Hour window")
    void testIsRuleActiveWithinWindow() {
        PosHappyHourRule rule = PosHappyHourRule.builder()
                .startTime("17:00")
                .endTime("19:00")
                .discountPercent(new BigDecimal("20.00"))
                .daysOfWeek("ALL")
                .isActive(true)
                .build();

        boolean activeAt1730 = pricingEngine.isRuleActive(rule, LocalTime.of(17, 30), DayOfWeek.FRIDAY);
        boolean activeAt1900 = pricingEngine.isRuleActive(rule, LocalTime.of(19, 0), DayOfWeek.FRIDAY);
        boolean inactiveAt1659 = pricingEngine.isRuleActive(rule, LocalTime.of(16, 59), DayOfWeek.FRIDAY);
        boolean inactiveAt1901 = pricingEngine.isRuleActive(rule, LocalTime.of(19, 1), DayOfWeek.FRIDAY);

        assertThat(activeAt1730).isTrue();
        assertThat(activeAt1900).isTrue();
        assertThat(inactiveAt1659).isFalse();
        assertThat(inactiveAt1901).isFalse();
    }

    @Test
    @DisplayName("isRuleActive handles overnight window spanning past midnight (e.g. 22:00 to 02:00)")
    void testIsRuleActiveOvernight() {
        PosHappyHourRule rule = PosHappyHourRule.builder()
                .startTime("22:00")
                .endTime("02:00")
                .discountPercent(new BigDecimal("25.00"))
                .daysOfWeek("ALL")
                .isActive(true)
                .build();

        boolean activeAt2330 = pricingEngine.isRuleActive(rule, LocalTime.of(23, 30), DayOfWeek.SATURDAY);
        boolean activeAt0130 = pricingEngine.isRuleActive(rule, LocalTime.of(1, 30), DayOfWeek.SUNDAY);
        boolean inactiveAt1500 = pricingEngine.isRuleActive(rule, LocalTime.of(15, 0), DayOfWeek.SATURDAY);

        assertThat(activeAt2330).isTrue();
        assertThat(activeAt0130).isTrue();
        assertThat(inactiveAt1500).isFalse();
    }

    @Test
    @DisplayName("calculateItemDiscount applies percentage discount on eligible products")
    void testCalculateItemDiscountEligible() {
        PosProduct product = PosProduct.builder()
                .sku("BEV-COCKTAIL-1")
                .happyHourEligible(true)
                .build();

        PosHappyHourRule rule = PosHappyHourRule.builder()
                .discountPercent(new BigDecimal("20.00"))
                .build();

        BigDecimal grossPrice = new BigDecimal("15.00");
        BigDecimal discount = pricingEngine.calculateItemDiscount(product, grossPrice, rule);

        // 15.00 * 20% = 3.00
        assertThat(discount).isEqualByComparingTo(new BigDecimal("3.00"));
    }

    @Test
    @DisplayName("calculateItemDiscount returns ZERO when product is not eligible for Happy Hour")
    void testCalculateItemDiscountNonEligible() {
        PosProduct product = PosProduct.builder()
                .sku("FOOD-STEAK-01")
                .happyHourEligible(false)
                .build();

        PosHappyHourRule rule = PosHappyHourRule.builder()
                .discountPercent(new BigDecimal("20.00"))
                .build();

        BigDecimal discount = pricingEngine.calculateItemDiscount(product, new BigDecimal("45.00"), rule);
        assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

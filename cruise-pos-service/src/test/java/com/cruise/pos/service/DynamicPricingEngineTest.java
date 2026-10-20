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

    /**
     * Verifies that Happy Hour includes its end boundary and excludes times outside the window.
     */
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

    /**
     * Verifies that an overnight Happy Hour spans midnight and excludes afternoon hours.
     */
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

    /**
     * Verifies that an eligible product receives the rule percentage discount.
     */
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

    /**
     * Verifies that an ineligible product receives no Happy Hour discount.
     */
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

    @Test
    @DisplayName("isRuleActive matches only exact time when start equals end")
    void testIsRuleActiveEqualStartEndMatchesExactTimeOnly() {
        PosHappyHourRule rule = PosHappyHourRule.builder()
                .startTime("17:00")
                .endTime("17:00")
                .discountPercent(new BigDecimal("15.00"))
                .daysOfWeek("ALL")
                .isActive(true)
                .build();

        boolean activeAt1700 = pricingEngine.isRuleActive(rule, LocalTime.of(17, 0), DayOfWeek.MONDAY);
        boolean activeAt1701 = pricingEngine.isRuleActive(rule, LocalTime.of(17, 1), DayOfWeek.MONDAY);
        boolean activeAt1659 = pricingEngine.isRuleActive(rule, LocalTime.of(16, 59), DayOfWeek.MONDAY);
        boolean activeAt1200 = pricingEngine.isRuleActive(rule, LocalTime.of(12, 0), DayOfWeek.MONDAY);

        assertThat(activeAt1700).isTrue();
        assertThat(activeAt1701).isFalse();
        assertThat(activeAt1659).isFalse();
        assertThat(activeAt1200).isFalse();
    }

    @Test
    @DisplayName("calculateItemDiscount returns zero when rule is null even if product has discount percent")
    void testCalculateItemDiscountNullRuleReturnsZero() {
        PosProduct product = PosProduct.builder()
                .sku("BEV-WINE-01")
                .happyHourEligible(true)
                .happyHourDiscountPercent(new BigDecimal("25.00"))
                .build();

        BigDecimal discount = pricingEngine.calculateItemDiscount(product, new BigDecimal("20.00"), null);
        assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("calculateItemDiscount uses rule discount percent over product level percent")
    void testCalculateItemDiscountRulePrecedence() {
        PosProduct product = PosProduct.builder()
                .sku("BEV-WINE-01")
                .happyHourEligible(true)
                .happyHourDiscountPercent(new BigDecimal("10.00"))
                .build();

        PosHappyHourRule rule = PosHappyHourRule.builder()
                .discountPercent(new BigDecimal("20.00"))
                .build();

        BigDecimal discount = pricingEngine.calculateItemDiscount(product, new BigDecimal("50.00"), rule);
        // 50.00 * 20% = 10.00
        assertThat(discount).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("calculateItemDiscount falls back to product discount percent when rule discount percent is null or non-positive")
    void testCalculateItemDiscountFallbackToProduct() {
        PosProduct product = PosProduct.builder()
                .sku("BEV-BEER-01")
                .happyHourEligible(true)
                .happyHourDiscountPercent(new BigDecimal("15.00"))
                .build();

        PosHappyHourRule ruleWithNullPercent = PosHappyHourRule.builder()
                .discountPercent(null)
                .build();
        BigDecimal discount = pricingEngine.calculateItemDiscount(product, new BigDecimal("40.00"), ruleWithNullPercent);
        // 40.00 * 15% = 6.00
        assertThat(discount).isEqualByComparingTo(new BigDecimal("6.00"));

        PosProduct productNoDiscount = PosProduct.builder()
                .sku("BEV-BEER-02")
                .happyHourEligible(true)
                .happyHourDiscountPercent(BigDecimal.ZERO)
                .build();
        BigDecimal zeroDiscount = pricingEngine.calculateItemDiscount(productNoDiscount, new BigDecimal("40.00"), ruleWithNullPercent);
        assertThat(zeroDiscount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("isRuleActive checks day of week, active flag and invalid time format")
    void testIsRuleActiveEdgeCases() {
        PosHappyHourRule inactiveRule = PosHappyHourRule.builder()
                .isActive(false)
                .startTime("17:00")
                .endTime("19:00")
                .build();
        assertThat(pricingEngine.isRuleActive(inactiveRule, LocalTime.of(18, 0), DayOfWeek.MONDAY)).isFalse();

        PosHappyHourRule specificDayRule = PosHappyHourRule.builder()
                .isActive(true)
                .daysOfWeek("MONDAY,WEDNESDAY,FRIDAY")
                .startTime("17:00")
                .endTime("19:00")
                .build();
        assertThat(pricingEngine.isRuleActive(specificDayRule, LocalTime.of(18, 0), DayOfWeek.MONDAY)).isTrue();
        assertThat(pricingEngine.isRuleActive(specificDayRule, LocalTime.of(18, 0), DayOfWeek.TUESDAY)).isFalse();

        PosHappyHourRule invalidTimeRule = PosHappyHourRule.builder()
                .isActive(true)
                .daysOfWeek("ALL")
                .startTime("NOT_A_TIME")
                .endTime("19:00")
                .build();
        assertThat(pricingEngine.isRuleActive(invalidTimeRule, LocalTime.of(18, 0), DayOfWeek.MONDAY)).isFalse();
    }

    @Test
    @DisplayName("findActiveRule and getRulesByOutlet delegate to repository and evaluate correctly")
    void testFindActiveRuleAndGetRulesByOutlet() {
        PosHappyHourRule rule1 = PosHappyHourRule.builder()
                .outletId(1L)
                .name("Evening HH")
                .startTime("17:00")
                .endTime("19:00")
                .discountPercent(new BigDecimal("20.00"))
                .daysOfWeek("ALL")
                .isActive(true)
                .build();
        rule1.setId(10L);

        when(happyHourRuleRepository.findActiveRulesByOutlet(1L)).thenReturn(List.of(rule1));

        List<HappyHourRuleDto> dtos = pricingEngine.getRulesByOutlet(1L);
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).getName()).isEqualTo("Evening HH");

        Optional<PosHappyHourRule> activeRule = pricingEngine.findActiveRule(1L, LocalTime.of(18, 0), DayOfWeek.FRIDAY);
        assertThat(activeRule).isPresent();
        assertThat(activeRule.get().getId()).isEqualTo(10L);

        Optional<PosHappyHourRule> outsideRule = pricingEngine.findActiveRule(1L, LocalTime.of(12, 0), DayOfWeek.FRIDAY);
        assertThat(outsideRule).isEmpty();
    }

    @Test
    @DisplayName("createRule, updateRule and deleteRule perform CRUD correctly and throw on missing rule")
    void testRuleCrudLifecycle() {
        HappyHourRuleDto createDto = HappyHourRuleDto.builder()
                .outletId(1L)
                .name("Poolside HH")
                .startTime("14:00")
                .endTime("16:00")
                .discountPercent(new BigDecimal("25.00"))
                .description("Afternoon discount")
                .build();

        PosHappyHourRule savedEntity = PosHappyHourRule.builder()
                .outletId(1L)
                .name("Poolside HH")
                .startTime("14:00")
                .endTime("16:00")
                .discountPercent(new BigDecimal("25.00"))
                .daysOfWeek("ALL")
                .isActive(true)
                .description("Afternoon discount")
                .build();
        savedEntity.setId(100L);

        when(happyHourRuleRepository.save(org.mockito.ArgumentMatchers.any(PosHappyHourRule.class))).thenReturn(savedEntity);

        HappyHourRuleDto created = pricingEngine.createRule(createDto);
        assertThat(created.getId()).isEqualTo(100L);
        assertThat(created.getDaysOfWeek()).isEqualTo("ALL");

        when(happyHourRuleRepository.findById(100L)).thenReturn(Optional.of(savedEntity));
        HappyHourRuleDto updateDto = HappyHourRuleDto.builder()
                .name("Updated Poolside HH")
                .discountPercent(new BigDecimal("30.00"))
                .build();

        HappyHourRuleDto updated = pricingEngine.updateRule(100L, updateDto);
        assertThat(updated.getName()).isEqualTo("Updated Poolside HH");

        when(happyHourRuleRepository.findById(999L)).thenReturn(Optional.empty());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pricingEngine.updateRule(999L, updateDto))
                .isInstanceOf(com.cruise.pos.exception.ResourceNotFoundException.class);

        when(happyHourRuleRepository.existsById(100L)).thenReturn(true);
        pricingEngine.deleteRule(100L);
        org.mockito.Mockito.verify(happyHourRuleRepository).deleteById(100L);

        when(happyHourRuleRepository.existsById(999L)).thenReturn(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pricingEngine.deleteRule(999L))
                .isInstanceOf(com.cruise.pos.exception.ResourceNotFoundException.class);
    }
}

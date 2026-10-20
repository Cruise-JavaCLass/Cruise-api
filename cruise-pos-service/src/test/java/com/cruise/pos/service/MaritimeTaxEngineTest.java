package com.cruise.pos.service;

import com.cruise.pos.dto.CartItemCalculationRequest;
import com.cruise.pos.dto.CartPriceCalculationRequest;
import com.cruise.pos.dto.CartPriceCalculationResponse;
import com.cruise.pos.entity.PosHappyHourRule;
import com.cruise.pos.entity.PosProduct;
import com.cruise.pos.entity.PosProductModifier;
import com.cruise.pos.enums.TaxCategory;
import com.cruise.pos.enums.VoyageLocationStatus;
import com.cruise.pos.repository.PosProductModifierRepository;
import com.cruise.pos.repository.PosProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaritimeTaxEngine Unit Tests")
class MaritimeTaxEngineTest {

    @Mock
    private PosProductRepository productRepository;

    @Mock
    private PosProductModifierRepository modifierRepository;

    @Mock
    private DynamicPricingEngine pricingEngine;

    @InjectMocks
    private MaritimeTaxEngine taxEngine;

    /**
     * Verifies that standard and reduced VAT categories are duty-free in international waters.
     */
    @Test
    @DisplayName("resolveTaxRate returns 0% when ship is in INTERNATIONAL_WATERS (Duty-Free)")
    void testTaxRateInternationalWatersDutyFree() {
        BigDecimal rateStandard = taxEngine.resolveTaxRate(TaxCategory.STANDARD_VAT, true);
        BigDecimal rateReduced = taxEngine.resolveTaxRate(TaxCategory.REDUCED_VAT, true);

        assertThat(rateStandard).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(rateReduced).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Verifies standard, reduced, and zero-rated VAT percentages for sales in port.
     */
    @Test
    @DisplayName("resolveTaxRate returns standard 10% VAT and reduced 5% VAT when IN_PORT")
    void testTaxRateInPort() {
        BigDecimal rateStandard = taxEngine.resolveTaxRate(TaxCategory.STANDARD_VAT, false);
        BigDecimal rateReduced = taxEngine.resolveTaxRate(TaxCategory.REDUCED_VAT, false);
        BigDecimal rateZero = taxEngine.resolveTaxRate(TaxCategory.ZERO_RATED, false);

        assertThat(rateStandard).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(rateReduced).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(rateZero).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Verifies in-port cart subtotal, VAT, 18% gratuity, and grand total.
     */
    @Test
    @DisplayName("calculateCartPricing computes subtotal, 18% gratuity, VAT and grand total in port")
    void testCalculateCartPricingInPort() {
        PosProduct product = PosProduct.builder()
                .sku("BEV-MOJITO")
                .name("Mojito")
                .basePrice(new BigDecimal("10.00"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .happyHourEligible(false)
                .build();
        product.setId(1L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(pricingEngine.findActiveRule(any(), any(), any())).thenReturn(Optional.empty());
        when(pricingEngine.calculateItemDiscount(any(), any(), any())).thenReturn(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN));

        CartPriceCalculationRequest request = CartPriceCalculationRequest.builder()
                .outletId(1L)
                .voyageLocation(VoyageLocationStatus.IN_PORT)
                .items(List.of(
                        CartItemCalculationRequest.builder()
                                .productId(1L)
                                .quantity(2)
                                .build()
                ))
                .build();

        CartPriceCalculationResponse response = taxEngine.calculateCartPricing(request);

        // Subtotal = 10.00 * 2 = 20.00
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(response.getHappyHourDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTaxableSubtotal()).isEqualByComparingTo(new BigDecimal("20.00"));

        // VAT 10% on 20.00 = 2.00
        assertThat(response.getMaritimeTax()).isEqualByComparingTo(new BigDecimal("2.00"));

        // Service charge 18% on 20.00 = 3.60
        assertThat(response.getServiceCharge()).isEqualByComparingTo(new BigDecimal("3.60"));

        // Grand Total = 20.00 + 2.00 + 3.60 = 25.60
        assertThat(response.getGrandTotal()).isEqualByComparingTo(new BigDecimal("25.60"));
        assertThat(response.getIsDutyFree()).isFalse();
    }

    /**
     * Verifies modifier pricing and gratuity with zero VAT in international waters.
     */
    @Test
    @DisplayName("calculateCartPricing with modifiers and Duty-Free in INTERNATIONAL_WATERS")
    void testCalculateCartPricingDutyFreeWithModifiers() {
        PosProduct product = PosProduct.builder()
                .sku("FOOD-BURGER")
                .name("Angus Burger")
                .basePrice(new BigDecimal("15.00"))
                .taxCategory(TaxCategory.STANDARD_VAT)
                .happyHourEligible(false)
                .build();
        product.setId(2L);

        PosProductModifier bacon = PosProductModifier.builder()
                .productId(2L)
                .name("Crispy Bacon")
                .priceDelta(new BigDecimal("2.50"))
                .build();
        bacon.setId(10L);

        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(modifierRepository.findAllById(List.of(10L))).thenReturn(List.of(bacon));
        when(pricingEngine.findActiveRule(any(), any(), any())).thenReturn(Optional.empty());
        when(pricingEngine.calculateItemDiscount(any(), any(), any())).thenReturn(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN));

        CartPriceCalculationRequest request = CartPriceCalculationRequest.builder()
                .outletId(1L)
                .voyageLocation(VoyageLocationStatus.INTERNATIONAL_WATERS)
                .items(List.of(
                        CartItemCalculationRequest.builder()
                                .productId(2L)
                                .quantity(1)
                                .selectedModifierIds(List.of(10L))
                                .build()
                ))
                .build();

        CartPriceCalculationResponse response = taxEngine.calculateCartPricing(request);

        // Subtotal = 15.00 + 2.50 = 17.50
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("17.50"));

        // Duty free -> tax = 0.00
        assertThat(response.getMaritimeTax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getIsDutyFree()).isTrue();

        // Service charge 18% of 17.50 = 3.15 (HALF_EVEN)
        assertThat(response.getServiceCharge()).isEqualByComparingTo(new BigDecimal("3.15"));

        // Grand total = 17.50 + 3.15 = 20.65
        assertThat(response.getGrandTotal()).isEqualByComparingTo(new BigDecimal("20.65"));
    }
}

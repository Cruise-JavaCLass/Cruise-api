package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import com.cruise.pos.entity.PosHappyHourRule;
import com.cruise.pos.entity.PosProduct;
import com.cruise.pos.entity.PosProductModifier;
import com.cruise.pos.enums.ProductStatus;
import com.cruise.pos.enums.TaxCategory;
import com.cruise.pos.enums.VoyageLocationStatus;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.exception.ResourceNotFoundException;
import com.cruise.pos.repository.PosProductModifierRepository;
import com.cruise.pos.repository.PosProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaritimeTaxEngine {

    private final PosProductRepository productRepository;
    private final PosProductModifierRepository modifierRepository;
    private final DynamicPricingEngine pricingEngine;

    // Maritime Standard Rates
    public static final BigDecimal MARITIME_SERVICE_CHARGE_RATE = new BigDecimal("18.00"); // 18% auto gratuity
    public static final BigDecimal STANDARD_PORT_VAT_RATE = new BigDecimal("10.00"); // 10% standard port VAT
    public static final BigDecimal REDUCED_PORT_VAT_RATE = new BigDecimal("5.00"); // 5% reduced port VAT
    public static final BigDecimal DUTY_FREE_RATE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Calculates cart totals with product modifiers, discounts, location-based VAT, and 18% gratuity.
     *
     * Taxes and gratuity use discounted amounts and HALF_EVEN rounding to two decimals.
     * A valid simulated time overrides the current time; rule matching uses the current day.
     */
    @Transactional(readOnly = true)
    public CartPriceCalculationResponse calculateCartPricing(CartPriceCalculationRequest request) {
        LocalTime evalTime = LocalTime.now();
        DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();

        if (request.getSimulatedTime() != null && !request.getSimulatedTime().isBlank()) {
            try {
                evalTime = LocalTime.parse(request.getSimulatedTime(), TIME_FORMATTER);
            } catch (Exception e) {
                log.warn("Could not parse simulated time '{}', using current time.", request.getSimulatedTime());
            }
        }

        Optional<PosHappyHourRule> activeRuleOpt = pricingEngine.findActiveRule(request.getOutletId(), evalTime, dayOfWeek);
        PosHappyHourRule activeRule = activeRuleOpt.orElse(null);
        boolean isHappyHourActive = activeRule != null;
        String happyHourDesc = isHappyHourActive
                ? activeRule.getName() + " (" + activeRule.getDiscountPercent() + "% off)"
                : null;

        boolean isDutyFree = request.getVoyageLocation() == VoyageLocationStatus.INTERNATIONAL_WATERS;

        BigDecimal cartSubtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal cartDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal cartMaritimeTax = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        List<CartItemCalculationResult> itemResults = new ArrayList<>();

        for (CartItemCalculationRequest itemReq : request.getItems()) {
            PosProduct product;
            if (itemReq.getProductId() != null) {
                product = productRepository.findById(itemReq.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm ID: " + itemReq.getProductId()));
            } else if (itemReq.getSku() != null) {
                product = productRepository.findBySku(itemReq.getSku())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm SKU: " + itemReq.getSku()));
            } else {
                throw new ResourceNotFoundException("Item thiếu productId hoặc sku");
            }

            if (product.getStatus() != null && product.getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessRuleException("Sản phẩm không hoạt động: " + product.getName());
            }
            if (Boolean.FALSE.equals(product.getIsAvailable()) || Boolean.TRUE.equals(product.getIs86ed())) {
                throw new BusinessRuleException("Sản phẩm hiện không khả dụng: " + product.getName());
            }
            if (request.getOutletId() != null && product.getOutletId() != null && !product.getOutletId().equals(request.getOutletId())) {
                throw new BusinessRuleException("Sản phẩm " + product.getName() + " không thuộc điểm bán ID: " + request.getOutletId());
            }

            int qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : 1;

            BigDecimal basePrice = product.getBasePrice().setScale(2, RoundingMode.HALF_EVEN);

            // Modifiers calculation
            BigDecimal modifierUnitPrice = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
            List<String> modNames = new ArrayList<>();

            if (itemReq.getSelectedModifierIds() != null && !itemReq.getSelectedModifierIds().isEmpty()) {
                List<PosProductModifier> modifiers = modifierRepository.findAllById(itemReq.getSelectedModifierIds());
                Map<Long, PosProductModifier> modMap = modifiers.stream()
                        .collect(Collectors.toMap(PosProductModifier::getId, m -> m));

                Map<Long, Long> counts = itemReq.getSelectedModifierIds().stream()
                        .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

                for (Map.Entry<Long, Long> entry : counts.entrySet()) {
                    Long modId = entry.getKey();
                    long count = entry.getValue();
                    PosProductModifier mod = modMap.get(modId);
                    if (mod == null) {
                        throw new ResourceNotFoundException("Không tìm thấy modifier ID: " + modId);
                    }
                    if (!product.getId().equals(mod.getProductId())) {
                        throw new BusinessRuleException("Modifier " + mod.getName() + " không thuộc sản phẩm ID: " + product.getId());
                    }
                    if (mod.getMaxSelection() != null && count > mod.getMaxSelection()) {
                        throw new BusinessRuleException("Modifier " + mod.getName() + " vượt quá số lượng tối đa cho phép (" + mod.getMaxSelection() + ")");
                    }
                }

                for (Long modId : itemReq.getSelectedModifierIds()) {
                    PosProductModifier mod = modMap.get(modId);
                    if (mod != null) {
                        modifierUnitPrice = modifierUnitPrice.add(mod.getPriceDelta());
                        modNames.add(mod.getName());
                    }
                }
            }

            BigDecimal unitGross = basePrice.add(modifierUnitPrice).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal grossLine = unitGross.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_EVEN);

            // Discount calculation
            BigDecimal discountUnit = pricingEngine.calculateItemDiscount(product, unitGross, activeRule);
            BigDecimal lineDiscount = discountUnit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal netLine = grossLine.subtract(lineDiscount).setScale(2, RoundingMode.HALF_EVEN);

            cartSubtotal = cartSubtotal.add(grossLine);
            cartDiscount = cartDiscount.add(lineDiscount);

            // Tax calculation for item
            BigDecimal taxRate = resolveTaxRate(product.getTaxCategory(), isDutyFree);
            if (taxRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal itemTax = netLine.multiply(taxRate)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
                cartMaritimeTax = cartMaritimeTax.add(itemTax);
            }

            itemResults.add(CartItemCalculationResult.builder()
                    .productId(product.getId())
                    .sku(product.getSku())
                    .productName(product.getName())
                    .quantity(qty)
                    .unitPrice(basePrice)
                    .modifierUnitTotal(modifierUnitPrice)
                    .grossLineTotal(grossLine)
                    .discountAmount(lineDiscount)
                    .netLineTotal(netLine)
                    .happyHourApplied(discountUnit.compareTo(BigDecimal.ZERO) > 0)
                    .appliedModifierNames(modNames)
                    .build());
        }

        BigDecimal taxableSubtotal = cartSubtotal.subtract(cartDiscount).setScale(2, RoundingMode.HALF_EVEN);

        // Service charge (18% Maritime auto-gratuity calculated on net taxable subtotal)
        BigDecimal serviceCharge = taxableSubtotal.multiply(MARITIME_SERVICE_CHARGE_RATE)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);

        BigDecimal grandTotal = taxableSubtotal.add(cartMaritimeTax).add(serviceCharge)
                .setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal effectiveTaxRate = isDutyFree ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN) : STANDARD_PORT_VAT_RATE;

        return CartPriceCalculationResponse.builder()
                .subtotal(cartSubtotal)
                .happyHourDiscount(cartDiscount)
                .taxableSubtotal(taxableSubtotal)
                .maritimeTax(cartMaritimeTax)
                .taxRatePercent(effectiveTaxRate)
                .serviceCharge(serviceCharge)
                .serviceChargePercent(MARITIME_SERVICE_CHARGE_RATE)
                .grandTotal(grandTotal)
                .isDutyFree(isDutyFree)
                .isHappyHourActive(isHappyHourActive)
                .happyHourDescription(happyHourDesc)
                .items(itemResults)
                .build();
    }

    /**
     * Returns zero for duty-free sales; otherwise returns the category VAT rate, defaulting null to 10%.
     */
    public BigDecimal resolveTaxRate(TaxCategory category, boolean isDutyFree) {
        if (isDutyFree) {
            return DUTY_FREE_RATE;
        }
        if (category == null) {
            return STANDARD_PORT_VAT_RATE;
        }
        return switch (category) {
            case STANDARD_VAT -> STANDARD_PORT_VAT_RATE;
            case REDUCED_VAT -> REDUCED_PORT_VAT_RATE;
            case ZERO_RATED, EXEMPT -> DUTY_FREE_RATE;
        };
    }
}

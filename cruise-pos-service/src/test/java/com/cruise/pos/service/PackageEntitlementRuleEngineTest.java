package com.cruise.pos.service;

import com.cruise.pos.dto.EntitlementEvaluateRequest;
import com.cruise.pos.dto.EntitlementEvaluateResponse;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.enums.EntitlementStatus;
import com.cruise.pos.enums.PackageType;
import com.cruise.pos.repository.GuestPackageEntitlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageEntitlementRuleEngineTest {

    @Mock
    private GuestPackageEntitlementRepository entitlementRepository;

    @InjectMocks
    private PackageEntitlementRuleEngine ruleEngine;

    private GuestPackageEntitlement beveragePkg;

    @BeforeEach
    void setUp() {
        beveragePkg = GuestPackageEntitlement.builder()
                .passengerId(101L)
                .cabinNumber("CABIN-101")
                .packageName("Deluxe Beverage Package")
                .packageType(PackageType.BEVERAGE_DELUXE)
                .eligibleCategory("BEVERAGE")
                .totalQuota(10)
                .usedQuota(2)
                .maxItemPriceCap(BigDecimal.valueOf(15.00))
                .priorityLevel(5)
                .status(EntitlementStatus.ACTIVE)
                .build();
        beveragePkg.setId(10L);
    }

    @Test
    @DisplayName("evaluateCartEntitlements covers eligible items up to cap and quota")
    void testEvaluateCartSuccess() {
        when(entitlementRepository.findActiveEntitlementsOrderByPriority(101L))
                .thenReturn(List.of(beveragePkg));

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .cabinNumber("CABIN-101")
                .items(List.of(
                        EntitlementEvaluateRequest.EvaluateItem.builder()
                                .productId("COCKTAIL-1")
                                .productName("Mojito")
                                .category("BEVERAGE")
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(14.00))
                                .build(),
                        EntitlementEvaluateRequest.EvaluateItem.builder()
                                .productId("SNACK-1")
                                .productName("Burger")
                                .category("FOOD")
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(20.00))
                                .build()
                ))
                .build();

        EntitlementEvaluateResponse response = ruleEngine.evaluateCartEntitlements(request);

        assertThat(response).isNotNull();
        assertThat(response.getOriginalTotal()).isEqualByComparingTo(BigDecimal.valueOf(48.00));
        assertThat(response.getPackageDiscountTotal()).isEqualByComparingTo(BigDecimal.valueOf(28.00));
        assertThat(response.getNetPayableTotal()).isEqualByComparingTo(BigDecimal.valueOf(20.00));

        EntitlementEvaluateResponse.ItemEntitlementResult mojitoResult = response.getItems().get(0);
        assertThat(mojitoResult.getFullyCovered()).isTrue();
        assertThat(mojitoResult.getCoveredAmount()).isEqualByComparingTo(BigDecimal.valueOf(28.00));
        assertThat(mojitoResult.getQuotaDeducted()).isEqualTo(2);

        EntitlementEvaluateResponse.ItemEntitlementResult burgerResult = response.getItems().get(1);
        assertThat(burgerResult.getFullyCovered()).isFalse();
        assertThat(burgerResult.getCoveredAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("evaluateCartEntitlements handles null or empty items gracefully")
    void testEvaluateCart_NullOrEmptyItems() {
        when(entitlementRepository.findActiveEntitlementsOrderByPriority(101L))
                .thenReturn(List.of(beveragePkg));

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .items(null)
                .build();

        EntitlementEvaluateResponse response = ruleEngine.evaluateCartEntitlements(request);

        assertThat(response).isNotNull();
        assertThat(response.getOriginalTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPackageDiscountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getNetPayableTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    @DisplayName("evaluateCartEntitlements handles null repository result")
    void testEvaluateCart_NullEntitlements() {
        when(entitlementRepository.findActiveEntitlementsOrderByPriority(101L))
                .thenReturn(null);

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .items(List.of(EntitlementEvaluateRequest.EvaluateItem.builder()
                        .productId("P1")
                        .productName("Beer")
                        .category("BEVERAGE")
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(8.00))
                        .build()))
                .build();

        EntitlementEvaluateResponse response = ruleEngine.evaluateCartEntitlements(request);

        assertThat(response).isNotNull();
        assertThat(response.getOriginalTotal()).isEqualByComparingTo(BigDecimal.valueOf(8.00));
        assertThat(response.getPackageDiscountTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getNetPayableTotal()).isEqualByComparingTo(BigDecimal.valueOf(8.00));
        assertThat(response.getItems().get(0).getFullyCovered()).isFalse();
    }

    @Test
    @DisplayName("evaluateCartEntitlements applies maxItemPriceCap partially when unit price exceeds cap")
    void testEvaluateCart_PriceCapApplied() {
        when(entitlementRepository.findActiveEntitlementsOrderByPriority(101L))
                .thenReturn(List.of(beveragePkg));

        // Beverage price is 25, cap is 15 -> covered 15, out-of-pocket 10 per unit
        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .items(List.of(EntitlementEvaluateRequest.EvaluateItem.builder()
                        .productId("WINE-1")
                        .productName("Vintage Red Wine")
                        .category("BEVERAGE")
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(25.00))
                        .build()))
                .build();

        EntitlementEvaluateResponse response = ruleEngine.evaluateCartEntitlements(request);

        assertThat(response).isNotNull();
        assertThat(response.getOriginalTotal()).isEqualByComparingTo(BigDecimal.valueOf(25.00));
        assertThat(response.getPackageDiscountTotal()).isEqualByComparingTo(BigDecimal.valueOf(15.00));
        assertThat(response.getNetPayableTotal()).isEqualByComparingTo(BigDecimal.valueOf(10.00));

        EntitlementEvaluateResponse.ItemEntitlementResult result = response.getItems().get(0);
        assertThat(result.getFullyCovered()).isFalse();
        assertThat(result.getCoveredAmount()).isEqualByComparingTo(BigDecimal.valueOf(15.00));
        assertThat(result.getOutOfPocketAmount()).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        assertThat(result.getQuotaDeducted()).isEqualTo(1);
    }

    @Test
    @DisplayName("evaluateCartEntitlements splits coverage across multiple packages when single package quota is insufficient")
    void testEvaluateCart_MultiPackageSplitCoverage() {
        GuestPackageEntitlement pkg1 = GuestPackageEntitlement.builder()
                .passengerId(101L)
                .packageName("Priority Small Pkg")
                .eligibleCategory("BEVERAGE")
                .totalQuota(1)
                .usedQuota(0)
                .priorityLevel(10)
                .status(EntitlementStatus.ACTIVE)
                .build();
        pkg1.setId(1L);

        GuestPackageEntitlement pkg2 = GuestPackageEntitlement.builder()
                .passengerId(101L)
                .packageName("Secondary Large Pkg")
                .eligibleCategory("BEVERAGE")
                .totalQuota(10)
                .usedQuota(0)
                .priorityLevel(5)
                .status(EntitlementStatus.ACTIVE)
                .build();
        pkg2.setId(2L);

        when(entitlementRepository.findActiveEntitlementsOrderByPriority(101L))
                .thenReturn(List.of(pkg2, pkg1)); // returned unsorted

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .items(List.of(EntitlementEvaluateRequest.EvaluateItem.builder()
                        .productId("COCKTAIL-1")
                        .productName("Martini")
                        .category("BEVERAGE")
                        .quantity(3)
                        .unitPrice(BigDecimal.valueOf(12.00))
                        .build()))
                .build();

        EntitlementEvaluateResponse response = ruleEngine.evaluateCartEntitlements(request);

        assertThat(response).isNotNull();
        assertThat(response.getOriginalTotal()).isEqualByComparingTo(BigDecimal.valueOf(36.00));
        assertThat(response.getPackageDiscountTotal()).isEqualByComparingTo(BigDecimal.valueOf(36.00));
        assertThat(response.getNetPayableTotal()).isEqualByComparingTo(BigDecimal.ZERO);

        EntitlementEvaluateResponse.ItemEntitlementResult itemResult = response.getItems().get(0);
        assertThat(itemResult.getFullyCovered()).isTrue();
        assertThat(itemResult.getQuotaDeducted()).isEqualTo(3);
        assertThat(itemResult.getAppliedCoverages()).hasSize(2);

        // pkg1 covered 1 unit, pkg2 covered 2 units
        assertThat(itemResult.getAppliedCoverages().get(0).getEntitlementId()).isEqualTo(1L);
        assertThat(itemResult.getAppliedCoverages().get(0).getUnitsCovered()).isEqualTo(1);
        assertThat(itemResult.getAppliedCoverages().get(1).getEntitlementId()).isEqualTo(2L);
        assertThat(itemResult.getAppliedCoverages().get(1).getUnitsCovered()).isEqualTo(2);
    }

    @Test
    @DisplayName("evaluateCartEntitlements correctly sorts packages by priority desc, validTo asc, and id asc")
    void testEvaluateCart_SortingPackages() {
        Instant t1 = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant t2 = Instant.now().plus(2, ChronoUnit.DAYS);

        GuestPackageEntitlement p1 = GuestPackageEntitlement.builder()
                .packageName("P1 - Priority 10 Later")
                .eligibleCategory("ALL")
                .totalQuota(1)
                .priorityLevel(10)
                .validTo(t2)
                .status(EntitlementStatus.ACTIVE)
                .build();
        p1.setId(101L);

        GuestPackageEntitlement p2 = GuestPackageEntitlement.builder()
                .packageName("P2 - Priority 10 Earlier")
                .eligibleCategory("ALL")
                .totalQuota(1)
                .priorityLevel(10)
                .validTo(t1)
                .status(EntitlementStatus.ACTIVE)
                .build();
        p2.setId(102L);

        GuestPackageEntitlement p3 = GuestPackageEntitlement.builder()
                .packageName("P3 - Priority 10 No Expiry")
                .eligibleCategory("ALL")
                .totalQuota(1)
                .priorityLevel(10)
                .validTo(null)
                .status(EntitlementStatus.ACTIVE)
                .build();
        p3.setId(103L);

        when(entitlementRepository.findActiveEntitlementsOrderByPriority(101L))
                .thenReturn(List.of(p3, p1, p2));

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .items(List.of(EntitlementEvaluateRequest.EvaluateItem.builder()
                        .productId("ITEM-1")
                        .productName("Item")
                        .category("ALL")
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(10.00))
                        .build()))
                .build();

        EntitlementEvaluateResponse response = ruleEngine.evaluateCartEntitlements(request);

        assertThat(response).isNotNull();
        // The first applied package should be P2 (earliest validTo)
        assertThat(response.getItems().get(0).getAppliedPackageName()).isEqualTo("P2 - Priority 10 Earlier");
    }
}

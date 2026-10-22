package com.cruise.pos.service;

import com.cruise.pos.dto.EntitlementDeductRequest;
import com.cruise.pos.dto.EntitlementEvaluateRequest;
import com.cruise.pos.dto.EntitlementEvaluateResponse;
import com.cruise.pos.dto.EntitlementRollbackRequest;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.entity.PackageEntitlementAuditLedger;
import com.cruise.pos.enums.EntitlementStatus;
import com.cruise.pos.enums.PackageType;
import com.cruise.pos.exception.BusinessRuleException;
import com.cruise.pos.repository.GuestPackageEntitlementRepository;
import com.cruise.pos.repository.PackageEntitlementAuditLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
}

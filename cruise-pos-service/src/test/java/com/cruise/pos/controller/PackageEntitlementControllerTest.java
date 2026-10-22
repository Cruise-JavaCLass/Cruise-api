package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.EntitlementDeductRequest;
import com.cruise.pos.dto.EntitlementEvaluateRequest;
import com.cruise.pos.dto.EntitlementEvaluateResponse;
import com.cruise.pos.dto.EntitlementRollbackRequest;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.repository.GuestPackageEntitlementRepository;
import com.cruise.pos.service.PackageEntitlementLedgerService;
import com.cruise.pos.service.PackageEntitlementRuleEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageEntitlementControllerTest {

    @Mock
    private PackageEntitlementRuleEngine entitlementRuleEngine;

    @Mock
    private GuestPackageEntitlementRepository entitlementRepository;

    @Mock
    private PackageEntitlementLedgerService ledgerService;

    @InjectMocks
    private PackageEntitlementController controller;

    @Test
    @DisplayName("evaluateCart returns evaluated pricing breakdown")
    void testEvaluateCart() {
        EntitlementEvaluateResponse mockResponse = EntitlementEvaluateResponse.builder()
                .passengerId(101L)
                .originalTotal(BigDecimal.valueOf(100.00))
                .packageDiscountTotal(BigDecimal.valueOf(40.00))
                .netPayableTotal(BigDecimal.valueOf(60.00))
                .items(Collections.emptyList())
                .build();

        when(entitlementRuleEngine.evaluateCartEntitlements(any(EntitlementEvaluateRequest.class)))
                .thenReturn(mockResponse);

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .items(Collections.emptyList())
                .build();

        ApiResponse<EntitlementEvaluateResponse> response = controller.evaluateCart(request);

        assertThat(response).isNotNull();
        assertThat(response.getData().getNetPayableTotal()).isEqualByComparingTo(BigDecimal.valueOf(60.00));
        verify(entitlementRuleEngine).evaluateCartEntitlements(request);
    }

    @Test
    @DisplayName("deductQuota calls ledger service")
    void testDeductQuota() {
        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .items(Collections.emptyList())
                .build();

        ApiResponse<Void> response = controller.deductQuota(request);
        assertThat(response).isNotNull();
        verify(ledgerService).deductEntitlementQuota(request);
    }

    @Test
    @DisplayName("rollbackQuota calls ledger service")
    void testRollbackQuota() {
        EntitlementRollbackRequest request = EntitlementRollbackRequest.builder()
                .passengerId(101L)
                .items(Collections.emptyList())
                .build();

        ApiResponse<Void> response = controller.rollbackQuota(request);
        assertThat(response).isNotNull();
        verify(ledgerService).rollbackEntitlementQuota(request);
    }
}

package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.EntitlementDeductRequest;
import com.cruise.pos.dto.EntitlementEvaluateRequest;
import com.cruise.pos.dto.EntitlementEvaluateResponse;
import com.cruise.pos.dto.EntitlementRollbackRequest;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.repository.GuestPackageEntitlementRepository;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.PackageEntitlementLedgerService;
import com.cruise.pos.service.PackageEntitlementRuleEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setSecurityPrincipal(String username, String role, String cabin) {
        AuthPrincipal principal = new AuthPrincipal(username, role, cabin);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("evaluateCart returns evaluated pricing breakdown without auth")
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
    @DisplayName("evaluateCart allows access when authenticated as staff")
    void testEvaluateCart_StaffAccess() {
        setSecurityPrincipal("staff_01", "CASHIER", null);

        EntitlementEvaluateResponse mockResponse = EntitlementEvaluateResponse.builder()
                .passengerId(101L)
                .netPayableTotal(BigDecimal.ZERO)
                .build();
        when(entitlementRuleEngine.evaluateCartEntitlements(any(EntitlementEvaluateRequest.class)))
                .thenReturn(mockResponse);

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .build();

        ApiResponse<EntitlementEvaluateResponse> response = controller.evaluateCart(request);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("evaluateCart allows access when passenger matches authenticated identity")
    void testEvaluateCart_PassengerSelfAccess() {
        setSecurityPrincipal("101", "ROLE_PASSENGER", "CABIN-101");

        EntitlementEvaluateResponse mockResponse = EntitlementEvaluateResponse.builder()
                .passengerId(101L)
                .netPayableTotal(BigDecimal.ZERO)
                .build();
        when(entitlementRuleEngine.evaluateCartEntitlements(any(EntitlementEvaluateRequest.class)))
                .thenReturn(mockResponse);

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .build();

        ApiResponse<EntitlementEvaluateResponse> response = controller.evaluateCart(request);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("evaluateCart denies access when passenger does not match authenticated identity")
    void testEvaluateCart_PassengerMismatchAccessDenied() {
        setSecurityPrincipal("102", "ROLE_PASSENGER", "CABIN-102");

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .build();

        assertThatThrownBy(() -> controller.evaluateCart(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Hành khách không có quyền truy cập gói đặc quyền của hành khách khác");
    }

    @Test
    @DisplayName("evaluateCart denies access when principal username is null")
    void testEvaluateCart_NullUsernameAccessDenied() {
        setSecurityPrincipal(null, "ROLE_PASSENGER", null);

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .build();

        assertThatThrownBy(() -> controller.evaluateCart(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Người dùng chưa được xác định danh tính hợp lệ");
    }

    @Test
    @DisplayName("evaluateCart denies access when principal username is not numeric for passenger")
    void testEvaluateCart_InvalidUsernameFormatAccessDenied() {
        setSecurityPrincipal("invalid_passenger_id", "ROLE_PASSENGER", null);

        EntitlementEvaluateRequest request = EntitlementEvaluateRequest.builder()
                .passengerId(101L)
                .build();

        assertThatThrownBy(() -> controller.evaluateCart(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Định dạng định danh không hợp lệ");
    }

    @Test
    @DisplayName("deductQuota calls ledger service when unauthenticated (demo/test mode)")
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
    @DisplayName("deductQuota allows staff cashier mutation access")
    void testDeductQuota_StaffAllowed() {
        setSecurityPrincipal("cashier_01", "ROLE_CASHIER", null);

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .items(Collections.emptyList())
                .build();

        ApiResponse<Void> response = controller.deductQuota(request);
        assertThat(response).isNotNull();
        verify(ledgerService).deductEntitlementQuota(request);
    }

    @Test
    @DisplayName("deductQuota denies access when non-staff role calls deduction")
    void testDeductQuota_NonStaffDenied() {
        setSecurityPrincipal("101", "ROLE_PASSENGER", "CABIN-101");

        EntitlementDeductRequest request = EntitlementDeductRequest.builder()
                .passengerId(101L)
                .items(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> controller.deductQuota(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ nhân viên POS mới có quyền thực hiện trừ hoặc hoàn quota gói đặc quyền");
    }

    @Test
    @DisplayName("rollbackQuota calls ledger service when unauthenticated")
    void testRollbackQuota() {
        EntitlementRollbackRequest request = EntitlementRollbackRequest.builder()
                .passengerId(101L)
                .items(Collections.emptyList())
                .build();

        ApiResponse<Void> response = controller.rollbackQuota(request);
        assertThat(response).isNotNull();
        verify(ledgerService).rollbackEntitlementQuota(request);
    }

    @Test
    @DisplayName("rollbackQuota allows staff manager mutation access")
    void testRollbackQuota_StaffAllowed() {
        setSecurityPrincipal("mgr_01", "ROLE_MANAGER", null);

        EntitlementRollbackRequest request = EntitlementRollbackRequest.builder()
                .passengerId(101L)
                .items(Collections.emptyList())
                .build();

        ApiResponse<Void> response = controller.rollbackQuota(request);
        assertThat(response).isNotNull();
        verify(ledgerService).rollbackEntitlementQuota(request);
    }

    @Test
    @DisplayName("rollbackQuota denies access when passenger attempts rollback")
    void testRollbackQuota_PassengerDenied() {
        setSecurityPrincipal("101", "ROLE_PASSENGER", "CABIN-101");

        EntitlementRollbackRequest request = EntitlementRollbackRequest.builder()
                .passengerId(101L)
                .items(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> controller.rollbackQuota(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ nhân viên POS mới có quyền thực hiện trừ hoặc hoàn quota gói đặc quyền");
    }

    @Test
    @DisplayName("getPassengerEntitlements returns active entitlements for passenger")
    void testGetPassengerEntitlements_Success() {
        GuestPackageEntitlement mockEntitlement = GuestPackageEntitlement.builder()
                .passengerId(101L)
                .packageName("Beverage Classic")
                .totalQuota(5)
                .usedQuota(1)
                .build();

        when(ledgerService.resolvePackageCollision(101L)).thenReturn(List.of(mockEntitlement));

        ApiResponse<List<GuestPackageEntitlement>> response = controller.getPassengerEntitlements(101L);

        assertThat(response).isNotNull();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getPackageName()).isEqualTo("Beverage Classic");
        verify(ledgerService).resolvePackageCollision(101L);
    }

    @Test
    @DisplayName("getPassengerEntitlements denies access when passenger requests other's entitlements")
    void testGetPassengerEntitlements_DeniedForOtherPassenger() {
        setSecurityPrincipal("102", "ROLE_PASSENGER", "CABIN-102");

        assertThatThrownBy(() -> controller.getPassengerEntitlements(101L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Hành khách không có quyền truy cập gói đặc quyền của hành khách khác");
    }
}

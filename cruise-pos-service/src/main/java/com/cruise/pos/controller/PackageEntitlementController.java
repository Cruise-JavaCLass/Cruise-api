package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.EntitlementEvaluateRequest;
import com.cruise.pos.dto.EntitlementEvaluateResponse;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.repository.GuestPackageEntitlementRepository;
import com.cruise.pos.security.AuthPrincipal;
import com.cruise.pos.service.PackageEntitlementRuleEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/pos/entitlements")
@RequiredArgsConstructor
@Slf4j
public class PackageEntitlementController {

    private static final Set<String> ALLOWED_STAFF_ROLES = Set.of(
            "ROLE_CASHIER", "ROLE_STAFF", "ROLE_ADMIN", "ROLE_MANAGER", "ROLE_FINANCE_FRONT_DESK"
    );

    private final PackageEntitlementRuleEngine entitlementRuleEngine;
    private final GuestPackageEntitlementRepository entitlementRepository;
    private final com.cruise.pos.service.PackageEntitlementLedgerService ledgerService;

    @PostMapping("/evaluate")
    public ApiResponse<EntitlementEvaluateResponse> evaluateCart(@Valid @RequestBody EntitlementEvaluateRequest request) {
        verifyReadAccess(request.getPassengerId());
        log.info("Evaluating entitlements for passengerId: {}, items count: {}", request.getPassengerId(), request.getItems() != null ? request.getItems().size() : 0);
        EntitlementEvaluateResponse response = entitlementRuleEngine.evaluateCartEntitlements(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/deduct")
    public ApiResponse<Void> deductQuota(@Valid @RequestBody com.cruise.pos.dto.EntitlementDeductRequest request) {
        verifyStaffMutationAccess();
        log.info("Deducting package entitlements for passengerId: {}", request.getPassengerId());
        ledgerService.deductEntitlementQuota(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rollback")
    public ApiResponse<Void> rollbackQuota(@Valid @RequestBody com.cruise.pos.dto.EntitlementRollbackRequest request) {
        verifyStaffMutationAccess();
        log.info("Rolling back package entitlements for passengerId: {}", request.getPassengerId());
        ledgerService.rollbackEntitlementQuota(request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/passengers/{passengerId}")
    public ApiResponse<List<GuestPackageEntitlement>> getPassengerEntitlements(@PathVariable("passengerId") Long passengerId) {
        verifyReadAccess(passengerId);
        log.info("Fetching active entitlements for passengerId: {}", passengerId);
        List<GuestPackageEntitlement> list = ledgerService.resolvePackageCollision(passengerId);
        return ApiResponse.ok(list);
    }

    private void verifyStaffMutationAccess() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return; // When security is disabled/unauthenticated in test/demo mode
        }
        if (auth.getPrincipal() instanceof AuthPrincipal principal) {
            String role = principal.role();
            String normalizedRole = role != null && !role.startsWith("ROLE_") ? "ROLE_" + role : role;
            if (normalizedRole != null && ALLOWED_STAFF_ROLES.contains(normalizedRole)) {
                return;
            }
            throw new AccessDeniedException("Chỉ nhân viên POS mới có quyền thực hiện trừ hoặc hoàn quota gói đặc quyền");
        }
    }

    private void verifyReadAccess(Long passengerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return;
        }
        String role = principal.role();
        String normalizedRole = role != null && !role.startsWith("ROLE_") ? "ROLE_" + role : role;
        if (normalizedRole != null && ALLOWED_STAFF_ROLES.contains(normalizedRole)) {
            return;
        }
        if (principal.username() == null) {
            throw new AccessDeniedException("Người dùng chưa được xác định danh tính hợp lệ");
        }
        try {
            long authPassengerId = Long.parseLong(principal.username());
            if (passengerId != null && authPassengerId != passengerId) {
                throw new AccessDeniedException("Hành khách không có quyền truy cập gói đặc quyền của hành khách khác");
            }
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Định dạng định danh không hợp lệ");
        }
    }
}

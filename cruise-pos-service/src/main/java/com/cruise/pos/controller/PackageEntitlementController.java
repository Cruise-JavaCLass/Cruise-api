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

@RestController
@RequestMapping("/api/pos/entitlements")
@RequiredArgsConstructor
@Slf4j
public class PackageEntitlementController {

    private final PackageEntitlementRuleEngine entitlementRuleEngine;
    private final GuestPackageEntitlementRepository entitlementRepository;
    private final com.cruise.pos.service.PackageEntitlementLedgerService ledgerService;

    @PostMapping("/evaluate")
    public ApiResponse<EntitlementEvaluateResponse> evaluateCart(@Valid @RequestBody EntitlementEvaluateRequest request) {
        verifyPosAccess(request.getPassengerId());
        log.info("Evaluating entitlements for passengerId: {}, items count: {}", request.getPassengerId(), request.getItems() != null ? request.getItems().size() : 0);
        EntitlementEvaluateResponse response = entitlementRuleEngine.evaluateCartEntitlements(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/deduct")
    public ApiResponse<Void> deductQuota(@Valid @RequestBody com.cruise.pos.dto.EntitlementDeductRequest request) {
        verifyPosAccess(request.getPassengerId());
        log.info("Deducting package entitlements for passengerId: {}", request.getPassengerId());
        ledgerService.deductEntitlementQuota(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rollback")
    public ApiResponse<Void> rollbackQuota(@Valid @RequestBody com.cruise.pos.dto.EntitlementRollbackRequest request) {
        verifyPosAccess(request.getPassengerId());
        log.info("Rolling back package entitlements for passengerId: {}", request.getPassengerId());
        ledgerService.rollbackEntitlementQuota(request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/passengers/{passengerId}")
    public ApiResponse<List<GuestPackageEntitlement>> getPassengerEntitlements(@PathVariable("passengerId") Long passengerId) {
        verifyPosAccess(passengerId);
        log.info("Fetching active entitlements for passengerId: {}", passengerId);
        List<GuestPackageEntitlement> list = ledgerService.resolvePackageCollision(passengerId);
        return ApiResponse.ok(list);
    }

    private void verifyPosAccess(Long passengerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            return;
        }
        String role = principal.role();
        // Allow POS staff / cashiers / managers / admins to manage or passenger themselves
        if (role != null && (role.contains("STAFF") || role.contains("ADMIN") || role.contains("MANAGER") || role.contains("CASHIER"))) {
            return;
        }
        if (principal.username() == null) {
            throw new AccessDeniedException("Người dùng chưa được xác định danh tính hợp lệ");
        }
        try {
            Long authPassengerId = Long.parseLong(principal.username());
            if (passengerId != null && !authPassengerId.equals(passengerId)) {
                throw new AccessDeniedException("Hành khách không có quyền truy cập gói đặc quyền của hành khách khác");
            }
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Định dạng định danh không hợp lệ");
        }
    }
}

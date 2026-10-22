package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.EntitlementEvaluateRequest;
import com.cruise.pos.dto.EntitlementEvaluateResponse;
import com.cruise.pos.entity.GuestPackageEntitlement;
import com.cruise.pos.repository.GuestPackageEntitlementRepository;
import com.cruise.pos.service.PackageEntitlementRuleEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        log.info("Evaluating entitlements for passengerId: {}, items count: {}", request.getPassengerId(), request.getItems() != null ? request.getItems().size() : 0);
        EntitlementEvaluateResponse response = entitlementRuleEngine.evaluateCartEntitlements(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/deduct")
    public ApiResponse<Void> deductQuota(@Valid @RequestBody com.cruise.pos.dto.EntitlementDeductRequest request) {
        log.info("Deducting package entitlements for passengerId: {}", request.getPassengerId());
        ledgerService.deductEntitlementQuota(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rollback")
    public ApiResponse<Void> rollbackQuota(@Valid @RequestBody com.cruise.pos.dto.EntitlementRollbackRequest request) {
        log.info("Rolling back package entitlements for passengerId: {}", request.getPassengerId());
        ledgerService.rollbackEntitlementQuota(request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/passengers/{passengerId}")
    public ApiResponse<List<GuestPackageEntitlement>> getPassengerEntitlements(@PathVariable("passengerId") Long passengerId) {
        log.info("Fetching active entitlements for passengerId: {}", passengerId);
        List<GuestPackageEntitlement> list = ledgerService.resolvePackageCollision(passengerId);
        return ApiResponse.ok(list);
    }
}

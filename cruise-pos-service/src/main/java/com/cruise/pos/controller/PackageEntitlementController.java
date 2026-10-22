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

    @PostMapping("/evaluate")
    public ApiResponse<EntitlementEvaluateResponse> evaluateCart(@Valid @RequestBody EntitlementEvaluateRequest request) {
        log.info("Evaluating entitlements for passengerId: {}, items count: {}", request.getPassengerId(), request.getItems() != null ? request.getItems().size() : 0);
        EntitlementEvaluateResponse response = entitlementRuleEngine.evaluateCartEntitlements(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/passengers/{passengerId}")
    public ApiResponse<List<GuestPackageEntitlement>> getPassengerEntitlements(@PathVariable("passengerId") Long passengerId) {
        log.info("Fetching active entitlements for passengerId: {}", passengerId);
        List<GuestPackageEntitlement> list = entitlementRepository.findActiveEntitlementsOrderByPriority(passengerId);
        return ApiResponse.ok(list);
    }
}

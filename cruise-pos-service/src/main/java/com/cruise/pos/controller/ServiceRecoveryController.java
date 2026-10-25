package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.*;
import com.cruise.pos.enums.ServiceRecoveryCaseStatus;
import com.cruise.pos.enums.ServiceRecoverySeverity;
import com.cruise.pos.service.ServiceRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/service-recovery")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ServiceRecoveryController {

    private final ServiceRecoveryService serviceRecoveryService;

    @PostMapping("/cases")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceRecoveryCaseDto>> createCase(
            @Valid @RequestBody CreateServiceRecoveryCaseRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "GUEST_RELATIONS_OFFICER";
        ServiceRecoveryCaseDto dto = serviceRecoveryService.createCase(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Service recovery case created", dto));
    }

    @GetMapping("/cases")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ServiceRecoveryCaseDto>>> searchCases(
            @RequestParam(required = false) ServiceRecoveryCaseStatus status,
            @RequestParam(required = false) ServiceRecoverySeverity severity,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ServiceRecoveryCaseDto> page = serviceRecoveryService.searchCases(status, severity, search, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    @GetMapping("/cases/{caseId}")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceRecoveryCaseDto>> getCase(@PathVariable Long caseId) {
        ServiceRecoveryCaseDto dto = serviceRecoveryService.getCase(caseId);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping("/cases/{caseId}/escalate")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceRecoveryCaseDto>> escalateCase(
            @PathVariable Long caseId,
            @Valid @RequestBody EscalateCaseRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "DUTY_MANAGER";
        ServiceRecoveryCaseDto dto = serviceRecoveryService.escalateCase(caseId, request, actor);
        return ResponseEntity.ok(ApiResponse.ok("Case escalated to Director level", dto));
    }

    @PostMapping("/cases/{caseId}/resolve")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceRecoveryCaseDto>> resolveCase(
            @PathVariable Long caseId,
            @Valid @RequestBody ResolveCaseRequest request,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "GUEST_RELATIONS_DIRECTOR";
        ServiceRecoveryCaseDto dto = serviceRecoveryService.resolveCase(caseId, request, actor);
        return ResponseEntity.ok(ApiResponse.ok("Case marked as resolved", dto));
    }

    @PostMapping("/cases/{caseId}/vouchers")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<ServiceRecoveryObcVoucherDto>> issueVoucher(
            @PathVariable Long caseId,
            @Valid @RequestBody IssueObcVoucherRequest request,
            Principal principal) {
        String authorizedBy = principal != null ? principal.getName() : "GUEST_RELATIONS_DIRECTOR";
        ServiceRecoveryObcVoucherDto voucher = serviceRecoveryService.issueObcVoucher(caseId, request, authorizedBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("OBC compensation voucher issued", voucher));
    }

    @GetMapping("/cases/{caseId}/vouchers")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<ServiceRecoveryObcVoucherDto>>> getVouchersForCase(@PathVariable Long caseId) {
        List<ServiceRecoveryObcVoucherDto> list = serviceRecoveryService.getVouchersForCase(caseId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/vouchers/{voucherCode}/claim")
    public ResponseEntity<ApiResponse<ClaimObcVoucherResponse>> claimVoucher(
            @PathVariable String voucherCode,
            Principal principal) {
        String actor = principal != null ? principal.getName() : "PASSENGER";
        ClaimObcVoucherResponse response = serviceRecoveryService.claimObcVoucher(voucherCode, actor);
        return ResponseEntity.ok(ApiResponse.ok("Voucher claimed and credited to cabin folio", response));
    }

    @GetMapping("/passenger/{passengerId}/vouchers")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN', 'SYSTEM_ADMIN') or #passengerId.toString() == authentication.name")
    public ResponseEntity<ApiResponse<List<ServiceRecoveryObcVoucherDto>>> getPassengerVouchers(@PathVariable Long passengerId) {
        List<ServiceRecoveryObcVoucherDto> list = serviceRecoveryService.getPassengerVouchers(passengerId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}

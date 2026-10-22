package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.enums.SettlementStatus;
import com.cruise.activity.service.ProviderReconciliationService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Controller cho đối soát công nợ & quyết toán nhà cung cấp tour (KAN-332, KAN-333, KAN-334, KAN-335)
 */
@RestController
@RequestMapping("/api/v1/tour-providers")
@RequiredArgsConstructor
public class TourSettlementController {

    private final ProviderReconciliationService reconciliationService;

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SettlementDetailDto> initializeOrRecalculate(
            @Valid @RequestBody InitializeSettlementRequest request
    ) {
        return ApiResponse.ok("Khởi tạo / đối soát quyết toán thành công",
                reconciliationService.initializeOrRecalculateSettlement(request));
    }

    @GetMapping("/settlements/{id}")
    public ApiResponse<SettlementDetailDto> getSettlementById(@PathVariable Long id) {
        return ApiResponse.ok("Lấy chi tiết quyết toán thành công",
                reconciliationService.getSettlementById(id));
    }

    @GetMapping("/settlements")
    public ApiResponse<Page<SettlementDetailDto>> searchSettlements(
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) Long voyageId,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok("Tìm kiếm danh sách quyết toán thành công",
                reconciliationService.searchSettlements(providerId, voyageId, status, pageable));
    }

    @PostMapping("/settlements/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SettlementDetailDto> addAdjustment(
            @PathVariable Long id,
            @Valid @RequestBody CreateSettlementAdjustmentRequest request
    ) {
        return ApiResponse.ok("Ghi nhận điều chỉnh / tranh chấp thành công",
                reconciliationService.addAdjustment(id, request));
    }

    @PatchMapping("/settlements/{id}/status")
    public ApiResponse<SettlementDetailDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSettlementStatusRequest request
    ) {
        return ApiResponse.ok("Cập nhật trạng thái quyết toán thành công",
                reconciliationService.updateStatus(id, request));
    }

    @GetMapping("/{id}/settlement-summary")
    public ApiResponse<ProviderSettlementSummaryResponse> getSettlementSummary(
            @PathVariable("id") String providerId,
            @RequestParam(required = false) Long voyageId
    ) {
        return ApiResponse.ok("Xuất biên bản đối soát và tổng kết chi phí thành công",
                reconciliationService.getSettlementSummary(providerId, voyageId));
    }
}

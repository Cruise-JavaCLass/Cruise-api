package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.DiscrepancyRecordDto;
import com.cruise.pos.dto.ReconciliationBatchDto;
import com.cruise.pos.dto.ResolveDiscrepancyRequest;
import com.cruise.pos.dto.RunReconciliationRequest;
import com.cruise.pos.enums.DiscrepancyStatus;
import com.cruise.pos.service.ReconciliationMatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationMatchingService reconciliationService;

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<ReconciliationBatchDto>> runReconciliation(
            @RequestBody(required = false) RunReconciliationRequest request) {
        if (request == null) {
            request = RunReconciliationRequest.builder().build();
        }
        ReconciliationBatchDto batch = reconciliationService.runReconciliation(request);
        return ResponseEntity.ok(ApiResponse.ok("Khởi chạy đối soát giao dịch thành công", batch));
    }

    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<Page<ReconciliationBatchDto>>> getBatches(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<ReconciliationBatchDto> batches = reconciliationService.getBatches(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.ok(batches));
    }

    @GetMapping("/batches/{id}")
    public ResponseEntity<ApiResponse<ReconciliationBatchDto>> getBatchById(@PathVariable Long id) {
        ReconciliationBatchDto batch = reconciliationService.getBatchById(id);
        return ResponseEntity.ok(ApiResponse.ok(batch));
    }

    @GetMapping("/discrepancies")
    public ResponseEntity<ApiResponse<Page<DiscrepancyRecordDto>>> getDiscrepancies(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) DiscrepancyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<DiscrepancyRecordDto> discrepancies = reconciliationService.getDiscrepancies(
                batchId, status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.ok(discrepancies));
    }

    @PatchMapping("/discrepancies/{id}/resolve")
    public ResponseEntity<ApiResponse<DiscrepancyRecordDto>> resolveDiscrepancy(
            @PathVariable Long id,
            @Valid @RequestBody ResolveDiscrepancyRequest request) {
        DiscrepancyRecordDto resolved = reconciliationService.resolveDiscrepancy(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật xử lý sai lệch thành công", resolved));
    }
}

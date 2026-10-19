package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.BatchSyncJournalRequest;
import com.cruise.pos.dto.BatchSyncJournalResponse;
import com.cruise.pos.dto.ReconciliationSummaryMetricsDto;
import com.cruise.pos.service.ReconciliationSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos/reconciliation")
@RequiredArgsConstructor
public class ReconciliationSyncController {

    private final ReconciliationSyncService syncService;

    @PostMapping("/sync-batch")
    public ResponseEntity<ApiResponse<BatchSyncJournalResponse>> syncBatch(
            @Valid @RequestBody BatchSyncJournalRequest request) {
        BatchSyncJournalResponse response = syncService.syncBatch(request);
        return ResponseEntity.ok(ApiResponse.ok("Đồng bộ mẻ giao dịch POS thành công", response));
    }

    @GetMapping("/metrics/summary")
    public ResponseEntity<ApiResponse<ReconciliationSummaryMetricsDto>> getSummaryMetrics() {
        ReconciliationSummaryMetricsDto metrics = syncService.getSummaryMetrics();
        return ResponseEntity.ok(ApiResponse.ok(metrics));
    }
}

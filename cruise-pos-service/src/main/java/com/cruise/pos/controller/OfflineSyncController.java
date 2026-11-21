package com.cruise.pos.controller;
import com.cruise.pos.dto.OfflineSyncDtos.*;
import com.cruise.pos.entity.SyncConflictLog;
import com.cruise.pos.service.OfflineSyncProcessingService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController @RequestMapping("/api/v1/pos/sync") @RequiredArgsConstructor
public class OfflineSyncController {
    private final OfflineSyncProcessingService service;
    @PostMapping("/batch")
    public ApiResponse<BatchResult> batch(@RequestHeader("X-Terminal-Id") String terminalId,
            @RequestHeader("X-Batch-Id") String batchId,@Valid @RequestBody BatchRequest request) {
        return ApiResponse.ok("Offline batch reconciled",service.processBatch(terminalId,batchId,request.transactions()));
    }
    @GetMapping("/batch/{batchId}/status")
    public ApiResponse<BatchResult> status(@PathVariable Long batchId,@RequestHeader("X-Terminal-Id") String terminalId) {
        return ApiResponse.ok(service.getStatus(batchId,terminalId));
    }
    @GetMapping("/conflicts")
    public ApiResponse<Page<SyncConflictLog>> conflicts(@RequestParam(defaultValue="PENDING") String status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.getConflicts(status,page,size));
    }
    @PostMapping("/conflicts/{id}/resolve")
    public ApiResponse<RecordResult> resolve(@PathVariable Long id,@Valid @RequestBody ResolveRequest request,Principal actor) {
        return ApiResponse.ok("Conflict decision recorded",service.resolve(id,request,actor.getName()));
    }
    @PostMapping("/heartbeat")
    public ApiResponse<HeartbeatResult> heartbeat(@RequestHeader("X-Terminal-Id") String terminalId,@Valid @RequestBody HeartbeatRequest request) {
        return ApiResponse.ok(service.heartbeat(terminalId,request));
    }
}

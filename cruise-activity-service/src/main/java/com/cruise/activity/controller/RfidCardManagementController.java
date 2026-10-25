package com.cruise.activity.controller;

import com.cruise.activity.dto.*;
import com.cruise.activity.service.RfidCardManagementService;
import com.cruise.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/operations/rfid-cards")
@RequiredArgsConstructor
public class RfidCardManagementController {

    private final RfidCardManagementService rfidService;

    @PostMapping("/batch-ingest")
    public ResponseEntity<ApiResponse<BulkRfidIngestionResponse>> ingestBatch(
            @Valid @RequestBody BulkRfidIngestionRequest request) {
        BulkRfidIngestionResponse response = rfidService.ingestBlankCardBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("RFID blank batch ingested successfully", response));
    }

    @PostMapping("/blacklist")
    public ResponseEntity<ApiResponse<BlacklistRfidCardResponse>> blacklistCard(
            @Valid @RequestBody BlacklistRfidCardRequest request) {
        BlacklistRfidCardResponse response = rfidService.blacklistCard(request);
        return ResponseEntity.ok(ApiResponse.ok("RFID card quarantined and blacklisted successfully", response));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<RfidInventoryDashboardDto>> getDashboard() {
        RfidInventoryDashboardDto metrics = rfidService.getDashboardMetrics();
        return ResponseEntity.ok(ApiResponse.ok("RFID inventory dashboard metrics retrieved", metrics));
    }

    @GetMapping("/blacklist-uids")
    public ResponseEntity<ApiResponse<List<String>>> getActiveBlacklist() {
        List<String> uids = rfidService.getActiveBlacklistedUids();
        return ResponseEntity.ok(ApiResponse.ok("Active RFID blacklist UIDs retrieved", uids));
    }
}

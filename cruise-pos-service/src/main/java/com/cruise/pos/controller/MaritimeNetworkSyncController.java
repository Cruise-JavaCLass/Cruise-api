package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.AdaptiveChunkSyncRequest;
import com.cruise.pos.dto.AdaptiveChunkSyncResponse;
import com.cruise.pos.dto.NetworkHeartbeatResponse;
import com.cruise.pos.service.MaritimeNetworkSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos/network")
@RequiredArgsConstructor
@Slf4j
public class MaritimeNetworkSyncController {

    private final MaritimeNetworkSyncService networkSyncService;

    @GetMapping("/heartbeat")
    public ApiResponse<NetworkHeartbeatResponse> pingHeartbeat(
            @RequestParam(value = "clientTimestamp", required = false) Long clientTimestamp,
            @RequestParam(value = "reportedLatencyMs", required = false) Long reportedLatencyMs
    ) {
        NetworkHeartbeatResponse response = networkSyncService.pingHeartbeat(clientTimestamp, reportedLatencyMs);
        return ApiResponse.ok(response);
    }

    @PostMapping("/sync/adaptive-chunk")
    public ApiResponse<AdaptiveChunkSyncResponse> ingestAdaptiveChunk(@Valid @RequestBody AdaptiveChunkSyncRequest request) {
        log.info("Received adaptive sync chunk {}/{} for device: {}, size: {}",
                request.getChunkIndex(), request.getTotalChunks(), request.getDeviceId(),
                request.getTransactions() != null ? request.getTransactions().size() : 0);
        AdaptiveChunkSyncResponse response = networkSyncService.ingestAdaptiveChunk(request);
        return ApiResponse.ok(response);
    }
}

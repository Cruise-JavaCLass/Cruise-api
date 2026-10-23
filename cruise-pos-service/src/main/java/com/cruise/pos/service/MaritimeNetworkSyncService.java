package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaritimeNetworkSyncService {

    private static final int MAX_TRACKED_DEVICES = 10_000;
    private static final long COOLDOWN_MS = 400;

    private final PosTransactionService transactionService;

    // Rate limiting tracking: deviceId -> lastSyncTimestampMs (bounded LRU map)
    private final Map<String, Long> deviceRateLimitMap = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > MAX_TRACKED_DEVICES;
                }
            }
    );

    public NetworkHeartbeatResponse pingHeartbeat(Long clientTimestamp, Long clientReportedLatencyMs) {
        long start = System.currentTimeMillis();
        Instant now = Instant.now();

        long latency = clientReportedLatencyMs != null ? clientReportedLatencyMs : 0L;
        String status;
        int recommendedBatchSize;
        int recommendedIntervalSec;

        if (latency <= 150) {
            status = "EXCELLENT";
            recommendedBatchSize = 50;
            recommendedIntervalSec = 5;
        } else if (latency <= 500) {
            status = "FAIR";
            recommendedBatchSize = 25;
            recommendedIntervalSec = 10;
        } else if (latency <= 1500) {
            status = "POOR";
            recommendedBatchSize = 10;
            recommendedIntervalSec = 20;
        } else {
            status = "DEGRADED";
            recommendedBatchSize = 5;
            recommendedIntervalSec = 30;
        }

        long processingMs = System.currentTimeMillis() - start;

        return NetworkHeartbeatResponse.builder()
                .clientEchoTimestamp(clientTimestamp)
                .serverTimestamp(now)
                .serverProcessingTimeMs(processingMs)
                .satelliteLinkStatus(status)
                .recommendedBatchSize(recommendedBatchSize)
                .recommendedSyncIntervalSec(recommendedIntervalSec)
                .build();
    }

    public AdaptiveChunkSyncResponse ingestAdaptiveChunk(AdaptiveChunkSyncRequest request) {
        String deviceId = request.getDeviceId();
        long nowMs = System.currentTimeMillis();
        long latency = request.getReportedLatencyMs() != null ? request.getReportedLatencyMs() : 0L;

        // Atomic rate-limiting check and timestamp update using compute
        boolean[] limited = {false};
        deviceRateLimitMap.compute(deviceId, (k, last) -> {
            if (latency > 1000 && last != null && (nowMs - last < COOLDOWN_MS)) {
                limited[0] = true;
                return last;
            }
            return nowMs;
        });

        if (limited[0]) {
            log.warn("Rate limiting active on device {} due to high latency ({}ms)", deviceId, latency);
            return AdaptiveChunkSyncResponse.builder()
                    .deviceId(deviceId)
                    .chunkIndex(request.getChunkIndex())
                    .totalChunks(request.getTotalChunks())
                    .processedCount(0)
                    .duplicateCount(0)
                    .failedCount(0)
                    .rateLimited(true)
                    .nextRecommendedChunkSize(Math.max(1, (request.getChunkSize() != null ? request.getChunkSize() : 10) / 2))
                    .nextBackoffMs(1000)
                    .build();
        }

        // Forward to transactionService.processBatchSync
        BatchSyncRequest batchReq = BatchSyncRequest.builder()
                .deviceId(request.getDeviceId())
                .clientSyncTimestamp(request.getClientSyncTimestamp())
                .transactions(request.getTransactions())
                .build();

        BatchSyncResponse batchResp = transactionService.processBatchSync(batchReq);

        int nextSize = computeNextChunkSize(latency, batchResp.getFailedCount());
        int backoffMs = latency > 1500 ? 1500 : (latency > 500 ? 500 : 0);

        return AdaptiveChunkSyncResponse.builder()
                .deviceId(deviceId)
                .chunkIndex(request.getChunkIndex())
                .totalChunks(request.getTotalChunks())
                .processedCount(batchResp.getSuccessfullyProcessed())
                .duplicateCount(batchResp.getDuplicateCount())
                .failedCount(batchResp.getFailedCount())
                .processedUuids(batchResp.getProcessedUuids())
                .duplicateUuids(batchResp.getDuplicateUuids())
                .failedReasons(batchResp.getFailedReasons())
                .rateLimited(false)
                .nextRecommendedChunkSize(nextSize)
                .nextBackoffMs(backoffMs)
                .build();
    }

    private int computeNextChunkSize(long latency, int failedCount) {
        if (failedCount > 0 || latency > 1500) {
            return 5;
        } else if (latency > 500) {
            return 15;
        } else {
            return 30;
        }
    }
}

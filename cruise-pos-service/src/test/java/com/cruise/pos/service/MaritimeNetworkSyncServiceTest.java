package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaritimeNetworkSyncServiceTest {

    @Mock
    private PosTransactionService transactionService;

    @InjectMocks
    private MaritimeNetworkSyncService networkSyncService;

    @Test
    @DisplayName("pingHeartbeat classifies network quality based on latency")
    void testPingHeartbeatClassification() {
        NetworkHeartbeatResponse excellent = networkSyncService.pingHeartbeat(1000L, 80L);
        assertThat(excellent.getSatelliteLinkStatus()).isEqualTo("EXCELLENT");
        assertThat(excellent.getRecommendedBatchSize()).isEqualTo(50);
        assertThat(excellent.getRecommendedSyncIntervalSec()).isEqualTo(5);

        NetworkHeartbeatResponse fair = networkSyncService.pingHeartbeat(1000L, 350L);
        assertThat(fair.getSatelliteLinkStatus()).isEqualTo("FAIR");
        assertThat(fair.getRecommendedBatchSize()).isEqualTo(25);
        assertThat(fair.getRecommendedSyncIntervalSec()).isEqualTo(10);

        NetworkHeartbeatResponse poor = networkSyncService.pingHeartbeat(1000L, 800L);
        assertThat(poor.getSatelliteLinkStatus()).isEqualTo("POOR");
        assertThat(poor.getRecommendedBatchSize()).isEqualTo(10);
        assertThat(poor.getRecommendedSyncIntervalSec()).isEqualTo(20);

        NetworkHeartbeatResponse degraded = networkSyncService.pingHeartbeat(1000L, 2000L);
        assertThat(degraded.getSatelliteLinkStatus()).isEqualTo("DEGRADED");
        assertThat(degraded.getRecommendedBatchSize()).isEqualTo(5);
        assertThat(degraded.getRecommendedSyncIntervalSec()).isEqualTo(30);
    }

    @Test
    @DisplayName("pingHeartbeat handles null latency and null clientTimestamp")
    void testPingHeartbeatNullInputs() {
        NetworkHeartbeatResponse response = networkSyncService.pingHeartbeat(null, null);
        assertThat(response).isNotNull();
        assertThat(response.getClientEchoTimestamp()).isNull();
        assertThat(response.getSatelliteLinkStatus()).isEqualTo("EXCELLENT");
        assertThat(response.getRecommendedBatchSize()).isEqualTo(50);
        assertThat(response.getServerTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("ingestAdaptiveChunk processes batch and returns recommended chunk size for low latency")
    void testIngestAdaptiveChunk() {
        BatchSyncResponse mockBatchResp = BatchSyncResponse.builder()
                .deviceId("POS-DEVICE-01")
                .successfullyProcessed(5)
                .duplicateCount(0)
                .failedCount(0)
                .processedUuids(List.of("UUID-1", "UUID-2"))
                .build();

        when(transactionService.processBatchSync(any(BatchSyncRequest.class))).thenReturn(mockBatchResp);

        AdaptiveChunkSyncRequest request = AdaptiveChunkSyncRequest.builder()
                .deviceId("POS-DEVICE-01")
                .chunkIndex(1)
                .totalChunks(2)
                .chunkSize(5)
                .reportedLatencyMs(120L)
                .transactions(Collections.emptyList())
                .build();

        AdaptiveChunkSyncResponse response = networkSyncService.ingestAdaptiveChunk(request);

        assertThat(response).isNotNull();
        assertThat(response.getProcessedCount()).isEqualTo(5);
        assertThat(response.getRateLimited()).isFalse();
        assertThat(response.getNextRecommendedChunkSize()).isEqualTo(30);
        assertThat(response.getNextBackoffMs()).isEqualTo(0);

        verify(transactionService).processBatchSync(any(BatchSyncRequest.class));
    }

    @Test
    @DisplayName("ingestAdaptiveChunk throttles device when latency > 1000ms and requests arrive too quickly")
    void testIngestAdaptiveChunkRateLimiting() {
        BatchSyncResponse mockBatchResp = BatchSyncResponse.builder()
                .deviceId("DEV-RATE-1")
                .successfullyProcessed(3)
                .failedCount(0)
                .build();
        when(transactionService.processBatchSync(any(BatchSyncRequest.class))).thenReturn(mockBatchResp);

        AdaptiveChunkSyncRequest req1 = AdaptiveChunkSyncRequest.builder()
                .deviceId("DEV-RATE-1")
                .chunkIndex(1)
                .totalChunks(3)
                .chunkSize(20)
                .reportedLatencyMs(1200L)
                .transactions(Collections.emptyList())
                .build();

        AdaptiveChunkSyncResponse resp1 = networkSyncService.ingestAdaptiveChunk(req1);
        assertThat(resp1.getRateLimited()).isFalse();

        // Immediate subsequent request on high latency link triggers rate limiting
        AdaptiveChunkSyncRequest req2 = AdaptiveChunkSyncRequest.builder()
                .deviceId("DEV-RATE-1")
                .chunkIndex(2)
                .totalChunks(3)
                .chunkSize(20)
                .reportedLatencyMs(1200L)
                .transactions(Collections.emptyList())
                .build();

        AdaptiveChunkSyncResponse resp2 = networkSyncService.ingestAdaptiveChunk(req2);
        assertThat(resp2.getRateLimited()).isTrue();
        assertThat(resp2.getProcessedCount()).isEqualTo(0);
        assertThat(resp2.getNextRecommendedChunkSize()).isEqualTo(10);
        assertThat(resp2.getNextBackoffMs()).isEqualTo(1000);

        // processBatchSync was called only once for req1
        verify(transactionService, times(1)).processBatchSync(any(BatchSyncRequest.class));
    }

    @Test
    @DisplayName("ingestAdaptiveChunk handles null chunkSize during rate limiting fallback")
    void testIngestAdaptiveChunkRateLimitedWithNullChunkSize() {
        BatchSyncResponse mockBatchResp = BatchSyncResponse.builder()
                .deviceId("DEV-RATE-NULL")
                .successfullyProcessed(1)
                .failedCount(0)
                .build();
        when(transactionService.processBatchSync(any(BatchSyncRequest.class))).thenReturn(mockBatchResp);

        AdaptiveChunkSyncRequest req1 = AdaptiveChunkSyncRequest.builder()
                .deviceId("DEV-RATE-NULL")
                .reportedLatencyMs(1500L)
                .transactions(Collections.emptyList())
                .build();
        networkSyncService.ingestAdaptiveChunk(req1);

        AdaptiveChunkSyncRequest req2 = AdaptiveChunkSyncRequest.builder()
                .deviceId("DEV-RATE-NULL")
                .chunkSize(null)
                .reportedLatencyMs(1500L)
                .transactions(Collections.emptyList())
                .build();

        AdaptiveChunkSyncResponse resp2 = networkSyncService.ingestAdaptiveChunk(req2);
        assertThat(resp2.getRateLimited()).isTrue();
        assertThat(resp2.getNextRecommendedChunkSize()).isEqualTo(5); // 10 / 2 = 5 default
    }

    @Test
    @DisplayName("ingestAdaptiveChunk recommends chunk size 5 and backoff 1500ms when latency > 1500ms")
    void testIngestAdaptiveChunkHighLatencyDegraded() {
        BatchSyncResponse mockBatchResp = BatchSyncResponse.builder()
                .deviceId("DEV-DEGRADED")
                .successfullyProcessed(2)
                .failedCount(0)
                .build();
        when(transactionService.processBatchSync(any(BatchSyncRequest.class))).thenReturn(mockBatchResp);

        AdaptiveChunkSyncRequest req = AdaptiveChunkSyncRequest.builder()
                .deviceId("DEV-DEGRADED")
                .chunkSize(10)
                .reportedLatencyMs(1800L)
                .transactions(Collections.emptyList())
                .build();

        AdaptiveChunkSyncResponse resp = networkSyncService.ingestAdaptiveChunk(req);

        assertThat(resp.getRateLimited()).isFalse();
        assertThat(resp.getNextRecommendedChunkSize()).isEqualTo(5);
        assertThat(resp.getNextBackoffMs()).isEqualTo(1500);
    }

    @Test
    @DisplayName("ingestAdaptiveChunk recommends chunk size 15 and backoff 500ms when latency is between 500ms and 1500ms")
    void testIngestAdaptiveChunkMediumLatencyFair() {
        BatchSyncResponse mockBatchResp = BatchSyncResponse.builder()
                .deviceId("DEV-FAIR")
                .successfullyProcessed(5)
                .failedCount(0)
                .build();
        when(transactionService.processBatchSync(any(BatchSyncRequest.class))).thenReturn(mockBatchResp);

        AdaptiveChunkSyncRequest req = AdaptiveChunkSyncRequest.builder()
                .deviceId("DEV-FAIR")
                .chunkSize(20)
                .reportedLatencyMs(750L)
                .transactions(Collections.emptyList())
                .build();

        AdaptiveChunkSyncResponse resp = networkSyncService.ingestAdaptiveChunk(req);

        assertThat(resp.getRateLimited()).isFalse();
        assertThat(resp.getNextRecommendedChunkSize()).isEqualTo(15);
        assertThat(resp.getNextBackoffMs()).isEqualTo(500);
    }

    @Test
    @DisplayName("ingestAdaptiveChunk reduces chunk size to 5 when batch has failed transactions")
    void testIngestAdaptiveChunkWithFailuresReducesChunk() {
        BatchSyncResponse mockBatchResp = BatchSyncResponse.builder()
                .deviceId("DEV-FAIL")
                .successfullyProcessed(3)
                .failedCount(2) // failures detected
                .build();
        when(transactionService.processBatchSync(any(BatchSyncRequest.class))).thenReturn(mockBatchResp);

        AdaptiveChunkSyncRequest req = AdaptiveChunkSyncRequest.builder()
                .deviceId("DEV-FAIL")
                .chunkSize(30)
                .reportedLatencyMs(100L) // low latency, but failedCount > 0
                .transactions(Collections.emptyList())
                .build();

        AdaptiveChunkSyncResponse resp = networkSyncService.ingestAdaptiveChunk(req);

        assertThat(resp.getNextRecommendedChunkSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("deviceRateLimitMap evicts eldest entry when capacity exceeds MAX_TRACKED_DEVICES")
    @SuppressWarnings("unchecked")
    void testDeviceRateLimitMapEviction() {
        Map<String, Long> rateLimitMap = (Map<String, Long>) ReflectionTestUtils.getField(networkSyncService, "deviceRateLimitMap");
        assertThat(rateLimitMap).isNotNull();

        for (int i = 0; i <= 10_005; i++) {
            rateLimitMap.put("DEV-" + i, (long) i);
        }

        assertThat(rateLimitMap.size()).isLessThanOrEqualTo(10_000);
    }
}

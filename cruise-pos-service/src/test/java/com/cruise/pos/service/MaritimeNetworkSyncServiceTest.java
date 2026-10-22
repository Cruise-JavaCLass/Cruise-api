package com.cruise.pos.service;

import com.cruise.pos.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        NetworkHeartbeatResponse fair = networkSyncService.pingHeartbeat(1000L, 350L);
        assertThat(fair.getSatelliteLinkStatus()).isEqualTo("FAIR");
        assertThat(fair.getRecommendedBatchSize()).isEqualTo(25);

        NetworkHeartbeatResponse poor = networkSyncService.pingHeartbeat(1000L, 800L);
        assertThat(poor.getSatelliteLinkStatus()).isEqualTo("POOR");
        assertThat(poor.getRecommendedBatchSize()).isEqualTo(10);

        NetworkHeartbeatResponse degraded = networkSyncService.pingHeartbeat(1000L, 2000L);
        assertThat(degraded.getSatelliteLinkStatus()).isEqualTo("DEGRADED");
        assertThat(degraded.getRecommendedBatchSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("ingestAdaptiveChunk processes batch and returns recommended chunk size")
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

        verify(transactionService).processBatchSync(any(BatchSyncRequest.class));
    }
}

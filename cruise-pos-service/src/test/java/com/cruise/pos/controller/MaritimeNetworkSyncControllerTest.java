package com.cruise.pos.controller;

import com.cruise.common.dto.ApiResponse;
import com.cruise.pos.dto.AdaptiveChunkSyncRequest;
import com.cruise.pos.dto.AdaptiveChunkSyncResponse;
import com.cruise.pos.dto.NetworkHeartbeatResponse;
import com.cruise.pos.service.MaritimeNetworkSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaritimeNetworkSyncControllerTest {

    @Mock
    private MaritimeNetworkSyncService networkSyncService;

    @InjectMocks
    private MaritimeNetworkSyncController controller;

    @Test
    @DisplayName("pingHeartbeat returns 200 OK with server heartbeat data")
    void testPingHeartbeat() {
        NetworkHeartbeatResponse mockResp = NetworkHeartbeatResponse.builder()
                .clientEchoTimestamp(123456L)
                .serverTimestamp(Instant.now())
                .serverProcessingTimeMs(2L)
                .satelliteLinkStatus("EXCELLENT")
                .recommendedBatchSize(50)
                .recommendedSyncIntervalSec(5)
                .build();

        when(networkSyncService.pingHeartbeat(eq(123456L), eq(50L))).thenReturn(mockResp);

        ApiResponse<NetworkHeartbeatResponse> response = controller.pingHeartbeat(123456L, 50L);

        assertThat(response).isNotNull();
        assertThat(response.getData().getSatelliteLinkStatus()).isEqualTo("EXCELLENT");
        verify(networkSyncService).pingHeartbeat(123456L, 50L);
    }

    @Test
    @DisplayName("ingestAdaptiveChunk returns processed sync response")
    void testIngestAdaptiveChunk() {
        AdaptiveChunkSyncResponse mockResp = AdaptiveChunkSyncResponse.builder()
                .deviceId("DEV-01")
                .processedCount(10)
                .rateLimited(false)
                .build();

        when(networkSyncService.ingestAdaptiveChunk(any(AdaptiveChunkSyncRequest.class))).thenReturn(mockResp);

        AdaptiveChunkSyncRequest request = AdaptiveChunkSyncRequest.builder()
                .deviceId("DEV-01")
                .chunkIndex(1)
                .totalChunks(1)
                .chunkSize(10)
                .transactions(Collections.emptyList())
                .build();

        ApiResponse<AdaptiveChunkSyncResponse> response = controller.ingestAdaptiveChunk(request);

        assertThat(response).isNotNull();
        assertThat(response.getData().getProcessedCount()).isEqualTo(10);
        verify(networkSyncService).ingestAdaptiveChunk(request);
    }
}

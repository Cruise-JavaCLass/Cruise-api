package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkHeartbeatResponse {

    private Long clientEchoTimestamp;
    private Instant serverTimestamp;
    private Long serverProcessingTimeMs;
    private String satelliteLinkStatus; // EXCELLENT, FAIR, POOR, DEGRADED
    private Integer recommendedBatchSize;
    private Integer recommendedSyncIntervalSec;
}

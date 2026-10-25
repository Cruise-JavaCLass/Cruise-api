package com.cruise.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveChunkSyncResponse {

    private String deviceId;
    private Integer chunkIndex;
    private Integer totalChunks;
    private Integer processedCount;
    private Integer duplicateCount;
    private Integer failedCount;
    private List<String> processedUuids;
    private List<String> duplicateUuids;
    private List<String> failedReasons;
    private Integer nextRecommendedChunkSize;
    private Integer nextBackoffMs;
    private Boolean rateLimited;
}

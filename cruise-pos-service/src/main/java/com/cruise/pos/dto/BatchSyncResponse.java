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
public class BatchSyncResponse {
    private String deviceId;
    private Integer totalReceived;
    private Integer successfullyProcessed;
    private Integer duplicateCount;
    private Integer failedCount;
    private List<String> processedUuids;
    private List<String> failedReasons;
}

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
public class RunReconciliationRequest {
    private String terminalId;
    private String serviceArea;
    private Instant startTime;
    private Instant endTime;
    private String triggeredBy;
    private String notes;
}

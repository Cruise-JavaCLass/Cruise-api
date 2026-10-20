package com.cruise.pos.dto;

import com.cruise.pos.enums.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeHistoryDto {
    private Long id;
    private Long disputeId;
    private DisputeStatus previousStatus;
    private DisputeStatus newStatus;
    private String actionBy;
    private String notes;
    private Instant timestamp;
}

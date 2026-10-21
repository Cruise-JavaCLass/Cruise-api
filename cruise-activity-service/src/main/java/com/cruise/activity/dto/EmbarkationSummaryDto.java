package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbarkationSummaryDto {

    private Long tourId;
    private long expectedTotal;
    private long boardedCount;
    private long remainingCount;
    private double boardingProgressPercent;
    private Map<String, Long> categoryBreakdown;
    private long vipTotal;
    private long vipBoarded;
}

package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Thống kê điểm CSAT của hoạt động (KAN-364)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCsatSummaryDto {
    private Long activityId;
    private double averageRating;
    private long totalReviews;
    private long escalatedCount;
    private Map<Integer, Long> ratingDistribution;
}

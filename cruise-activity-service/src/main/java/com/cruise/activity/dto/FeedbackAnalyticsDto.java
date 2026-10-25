package com.cruise.activity.dto;

import com.cruise.activity.enums.FeedbackCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackAnalyticsDto {

    private Long totalReviews;
    private Double averageRating;
    private Long lowRatingAlertCount;
    private Long escalatedCount;
    private Long resolvedCount;
    private Map<Integer, Long> ratingDistribution; // 1 to 5
    private Map<FeedbackCategory, Double> categoryAverages;
}

package com.cruise.activity.dto;

import java.util.List;

public record ProviderScorecardDto(
        String providerName,
        Long totalExcursions,
        Long totalReviews,
        Double averageRating,
        Double averageGuideRating,
        Double averageBusRating,
        Double averageValueRating,
        Long flaggedCount,
        String performanceTier // EXCELLENT, GOOD, NEEDS_IMPROVEMENT, HIGH_RISK
) {
}

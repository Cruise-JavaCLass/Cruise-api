package com.cruise.activity.dto;

public record ProviderScorecardDto(
        String providerName,
        Long totalExcursions,
        Long totalReviews,
        Double averageRating,
        Double averageGuideRating,
        Double averageBusRating,
        Double averageValueRating,
        Long flaggedCount,
        String performanceTier // EXCELLENT, GOOD, NEEDS_IMPROVEMENT, HIGH_RISK, UNRATED
) {
}

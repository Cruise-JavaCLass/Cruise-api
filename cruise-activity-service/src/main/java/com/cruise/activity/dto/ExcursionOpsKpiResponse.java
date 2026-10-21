package com.cruise.activity.dto;

import java.util.List;

public record ExcursionOpsKpiResponse(
        Long totalExcursions,
        Long totalReviews,
        Double averageOverallRating,
        Double averageGuideRating,
        Double averageBusRating,
        Double averageValueRating,
        Long totalFlaggedReviews,
        List<ProviderScorecardDto> providerScorecards,
        List<GuideRatingDto> guideRatings
) {
    public record GuideRatingDto(
            Long guideId,
            String guideName,
            String guideCode,
            String role,
            Double averageRating,
            Long totalAssignedExcursions
    ) {}
}

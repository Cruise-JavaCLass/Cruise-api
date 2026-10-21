package com.cruise.activity.dto;

import java.time.LocalDateTime;

public record ExcursionReviewResponse(
        Long id,
        Long excursionId,
        Long passengerId,
        Integer ratingOverall,
        Integer ratingGuide,
        Integer ratingBus,
        Integer ratingValue,
        String comments,
        Boolean isFlagged,
        String flagReason,
        LocalDateTime createdAt
) {
}

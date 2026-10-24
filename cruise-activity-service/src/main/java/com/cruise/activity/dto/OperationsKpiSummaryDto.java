package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsKpiSummaryDto {
    private long totalPassengers;
    private long checkedInPassengers;
    private double embarkationRate;

    private long totalActivitiesScheduled;
    private long totalActivitiesActive;
    private long totalActivityRegistrations;

    private long totalShoreExcursions;
    private long departingExcursionsToday;
    private long totalExcursionBookings;

    private double averageActivityRating;
    private long totalFeedbackSubmitted;

    private Instant generatedAt;
    private Map<String, Long> activitiesByCategory;
}

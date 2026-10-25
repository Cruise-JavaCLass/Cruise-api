package com.cruise.pos.dto;

import com.cruise.pos.enums.VoyageFinancialStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record VoyageFinancialClosureDto(
        Long id,
        String voyageNumber,
        String shipId,
        String itineraryName,
        Instant voyageStartDate,
        Instant voyageEndDate,
        Integer voyageDurationDays,
        Integer totalPassengerCapacity,
        Integer actualPassengerAttendance,
        Integer passengerCruiseDays,
        BigDecimal budgetedGrossRevenue,
        BigDecimal actualGrossRevenue,
        BigDecimal actualNetRevenue,
        BigDecimal totalDiscountsAndObc,
        BigDecimal revenueBeverageBars,
        BigDecimal revenueSpecialtyDining,
        BigDecimal revenueShoreExcursions,
        BigDecimal revenueSpaWellness,
        BigDecimal revenueRetailBoutiques,
        BigDecimal occupancyPercentage,
        BigDecimal revenuePerPassengerCruiseDay,
        BigDecimal budgetVariancePercentage,
        BigDecimal profitabilityIndex,
        VoyageFinancialStatus status,
        String closedBy,
        Instant closedAt,
        String closureAuditNotes,
        Instant createdAt
) {}

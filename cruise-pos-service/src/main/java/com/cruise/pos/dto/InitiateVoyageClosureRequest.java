package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record InitiateVoyageClosureRequest(
        @NotBlank(message = "voyageNumber is required")
        @Size(max = 64, message = "voyageNumber cannot exceed 64 characters")
        String voyageNumber,

        @NotBlank(message = "shipId is required")
        @Size(max = 64, message = "shipId cannot exceed 64 characters")
        String shipId,

        @NotBlank(message = "itineraryName is required")
        @Size(max = 128, message = "itineraryName cannot exceed 128 characters")
        String itineraryName,

        @NotNull(message = "voyageStartDate is required")
        Instant voyageStartDate,

        @NotNull(message = "voyageEndDate is required")
        Instant voyageEndDate,

        @NotNull(message = "totalPassengerCapacity is required")
        @Min(value = 1, message = "totalPassengerCapacity must be at least 1")
        Integer totalPassengerCapacity,

        @NotNull(message = "actualPassengerAttendance is required")
        @Min(value = 0, message = "actualPassengerAttendance cannot be negative")
        Integer actualPassengerAttendance,

        @NotNull(message = "budgetedGrossRevenue is required")
        @DecimalMin(value = "0.00")
        BigDecimal budgetedGrossRevenue,

        @DecimalMin(value = "0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal revenueBeverageBars,

        @DecimalMin(value = "0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal revenueSpecialtyDining,

        @DecimalMin(value = "0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal revenueShoreExcursions,

        @DecimalMin(value = "0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal revenueSpaWellness,

        @DecimalMin(value = "0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal revenueRetailBoutiques,

        @DecimalMin(value = "0.00")
        @Digits(integer = 10, fraction = 2)
        BigDecimal totalDiscountsAndObc,

        @Size(max = 1000, message = "closureAuditNotes cannot exceed 1000 characters")
        String closureAuditNotes
) {}

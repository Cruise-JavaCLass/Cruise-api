package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.VoyageFinancialStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "voyage_financial_closures", indexes = {
        @Index(name = "idx_closure_voyage_number", columnList = "voyage_number", unique = true),
        @Index(name = "idx_closure_ship_id", columnList = "ship_id"),
        @Index(name = "idx_closure_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoyageFinancialClosure extends BaseEntity {

    @Column(name = "voyage_number", nullable = false, unique = true, length = 64)
    private String voyageNumber; // e.g. "VOY-2026-MED-07"

    @Column(name = "ship_id", nullable = false, length = 64)
    private String shipId; // e.g. "SHIP-CELEBRATION"

    @Column(name = "itinerary_name", nullable = false, length = 128)
    private String itineraryName;

    @Column(name = "voyage_start_date", nullable = false)
    private Instant voyageStartDate;

    @Column(name = "voyage_end_date", nullable = false)
    private Instant voyageEndDate;

    @Column(name = "voyage_duration_days", nullable = false)
    private Integer voyageDurationDays;

    @Column(name = "total_passenger_capacity", nullable = false)
    private Integer totalPassengerCapacity;

    @Column(name = "actual_passenger_attendance", nullable = false)
    private Integer actualPassengerAttendance;

    @Column(name = "passenger_cruise_days", nullable = false)
    private Integer passengerCruiseDays; // actualPassengers * durationDays (APCD)

    // Financial Metrics
    @Column(name = "budgeted_gross_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal budgetedGrossRevenue;

    @Column(name = "actual_gross_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal actualGrossRevenue;

    @Column(name = "actual_net_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal actualNetRevenue;

    @Column(name = "total_discounts_and_obc", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDiscountsAndObc;

    // Department Revenue Breakdown
    @Column(name = "revenue_beverage_bars", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueBeverageBars;

    @Column(name = "revenue_specialty_dining", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueSpecialtyDining;

    @Column(name = "revenue_shore_excursions", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueShoreExcursions;

    @Column(name = "revenue_spa_wellness", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueSpaWellness;

    @Column(name = "revenue_retail_boutiques", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueRetailBoutiques;

    // Derived Executive Indices
    @Column(name = "occupancy_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal occupancyPercentage; // (actual / capacity) * 100

    @Column(name = "revenue_per_passenger_cruise_day", nullable = false, precision = 10, scale = 2)
    private BigDecimal revenuePerPassengerCruiseDay; // RevPCD = actualGrossRevenue / APCD

    @Column(name = "budget_variance_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal budgetVariancePercentage; // ((actual - budget) / budget) * 100

    @Column(name = "profitability_index", nullable = false, precision = 6, scale = 2)
    private BigDecimal profitabilityIndex; // performance ratio

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private VoyageFinancialStatus status = VoyageFinancialStatus.ACTIVE;

    @Column(name = "closed_by", length = 64)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_audit_notes", length = 1000)
    private String closureAuditNotes;
}

package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.VoyageOperationalPhase;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "voyage_lifecycle_states", indexes = {
        @Index(name = "idx_voyage_state_voyage_number", columnList = "voyage_number", unique = true),
        @Index(name = "idx_voyage_state_ship_id", columnList = "ship_id"),
        @Index(name = "idx_voyage_state_phase", columnList = "current_phase")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoyageLifecycleState extends BaseEntity {

    @Column(name = "voyage_number", nullable = false, unique = true, length = 64)
    private String voyageNumber; // e.g. "VOY-2026-MED-05"

    @Column(name = "ship_id", nullable = false, length = 64)
    private String shipId; // e.g. "SHIP-CELEBRATION"

    @Column(name = "itinerary_name", nullable = false, length = 128)
    private String itineraryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", nullable = false, length = 32)
    @Builder.Default
    private VoyageOperationalPhase currentPhase = VoyageOperationalPhase.PLANNING;

    @Column(name = "current_port_name", length = 128)
    private String currentPortName;

    @Column(name = "current_timezone", nullable = false, length = 64)
    @Builder.Default
    private String currentTimezone = "UTC"; // e.g. "Europe/Athens", "America/New_York"

    @Column(name = "vessel_clock_offset_minutes", nullable = false)
    @Builder.Default
    private Integer vesselClockOffsetMinutes = 0;

    @Column(name = "clock_sync_enabled", nullable = false)
    @Builder.Default
    private Boolean clockSyncEnabled = true;

    @Column(name = "phase_transitioned_at", nullable = false)
    private Instant phaseTransitionedAt;

    @Column(name = "transitioned_by", nullable = false, length = 64)
    private String transitionedBy;

    @Column(name = "transition_reason", length = 255)
    private String transitionReason;

    @Column(name = "tax_exemption_active", nullable = false)
    @Builder.Default
    private Boolean taxExemptionActive = false; // At Sea = tax-free on international waters

    @Column(name = "casino_open", nullable = false)
    @Builder.Default
    private Boolean casinoOpen = false; // Closed in port, open at sea
}

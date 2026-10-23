package com.cruise.activity.entity;

import com.cruise.activity.enums.MusterStatus;
import com.cruise.activity.enums.SolasEmergencyType;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "solas_muster_incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolasMusterIncident extends BaseEntity {

    @Column(name = "incident_code", nullable = false, unique = true, length = 64)
    private String incidentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "emergency_type", nullable = false, length = 50)
    private SolasEmergencyType emergencyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private MusterStatus status = MusterStatus.ACTIVE_MUSTER;

    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy; // e.g. "CAPTAIN", "CHIEF_OFFICER", "SAFETY_OFFICER"

    @Column(name = "announcement_text", nullable = false, length = 1000)
    private String announcementText;

    @Column(name = "acoustic_signal_code", length = 50)
    @Builder.Default
    private String acousticSignalCode = "7_SHORT_1_LONG";

    @Column(name = "total_souls_on_board", nullable = false)
    private Integer totalSoulsOnBoard;

    @Column(name = "mustered_souls_count", nullable = false)
    @Builder.Default
    private Integer musteredSoulsCount = 0;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "all_clear_at")
    private Instant allClearAt;
}

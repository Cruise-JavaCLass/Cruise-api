package com.cruise.activity.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "solas_muster_checkins", indexes = {
        @Index(name = "idx_muster_incident_pass", columnList = "incident_id, passenger_id", unique = true),
        @Index(name = "idx_muster_station", columnList = "muster_station_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolasMusterCheckin extends BaseEntity {

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "muster_station_code", nullable = false, length = 32)
    private String musterStationCode; // e.g. "STATION-A", "STATION-B"

    @Column(name = "lifejacket_verified", nullable = false)
    @Builder.Default
    private Boolean lifejacketVerified = true;

    @Column(name = "checked_in_by", length = 100)
    private String checkedInBy; // crew member ID or RFID scanner gate

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;
}

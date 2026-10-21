package com.cruise.activity.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Nhật ký Kiểm toán Check-in Lên tàu tại Cổng Terminal (Immutable Embarkation Check-in Audit Trail - AC6 / KAN-382)
 */
@Entity
@Table(name = "embarkation_check_in_logs", indexes = {
        @Index(name = "idx_embark_log_passenger_id", columnList = "passenger_id"),
        @Index(name = "idx_embark_log_tour_id", columnList = "tour_id"),
        @Index(name = "idx_embark_log_time", columnList = "boarded_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmbarkationCheckInLog extends BaseEntity {

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "staff_id", length = 100)
    private String staffId;

    @Column(name = "terminal_device_id", length = 100)
    private String terminalDeviceId;

    @Column(name = "rfid_tag", length = 50)
    private String rfidTag;

    @Column(name = "gate_lane", length = 50)
    private String gateLane;

    @Column(name = "boarded_at", nullable = false)
    private Instant boardedAt;

    @Column(name = "verification_method", length = 50)
    private String verificationMethod;
}

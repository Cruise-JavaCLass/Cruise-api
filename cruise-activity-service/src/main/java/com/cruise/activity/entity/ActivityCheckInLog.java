package com.cruise.activity.entity;

import com.cruise.activity.enums.ActivityCheckInMethod;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Nhật ký Kiểm toán Điểm danh Sự kiện Bất biến (Immutable Activity Check-in Audit Trail - KAN-219)
 */
@Entity
@Table(name = "activity_check_in_logs", indexes = {
        @Index(name = "idx_act_log_activity_id", columnList = "activity_id"),
        @Index(name = "idx_act_log_passenger_id", columnList = "passenger_id"),
        @Index(name = "idx_act_log_time", columnList = "check_in_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityCheckInLog extends BaseEntity {

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "passenger_name", length = 150)
    private String passengerName;

    @Column(name = "cabin_number", length = 50)
    private String cabinNumber;

    @Column(name = "staff_id", length = 100)
    private String staffId;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "check_in_time", nullable = false)
    private Instant checkInTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    private ActivityCheckInMethod method;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}

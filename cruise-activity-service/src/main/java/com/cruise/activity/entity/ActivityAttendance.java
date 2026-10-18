package com.cruise.activity.entity;

import com.cruise.activity.enums.ActivityAttendanceStatus;
import com.cruise.activity.enums.ActivityCheckInMethod;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thực thể Điểm danh và Tham gia Hoạt động trên tàu (KAN-219)
 */
@Entity
@Table(name = "activity_attendances", indexes = {
        @Index(name = "idx_act_att_activity_id", columnList = "activity_id"),
        @Index(name = "idx_act_att_passenger_id", columnList = "passenger_id"),
        @Index(name = "idx_act_att_cabin_number", columnList = "cabin_number"),
        @Index(name = "idx_act_att_rfid_tag", columnList = "rfid_tag"),
        @Index(name = "idx_act_att_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_activity_passenger", columnNames = {"activity_id", "passenger_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityAttendance extends BaseEntity {

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "passenger_name", nullable = false, length = 150)
    private String passengerName;

    @Column(name = "cabin_number", nullable = false, length = 50)
    private String cabinNumber;

    @Column(name = "rfid_tag", length = 100)
    private String rfidTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ActivityAttendanceStatus status = ActivityAttendanceStatus.REGISTERED;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_in_by_staff_id", length = 100)
    private String checkedInByStaffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_method", length = 30)
    private ActivityCheckInMethod checkInMethod;

    @Column(name = "is_walk_in", nullable = false)
    @Builder.Default
    private Boolean isWalkIn = false;

    @Column(name = "charged_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal chargedAmount = BigDecimal.ZERO;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "note", length = 500)
    private String note;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}

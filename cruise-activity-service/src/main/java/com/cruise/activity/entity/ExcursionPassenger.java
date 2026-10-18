package com.cruise.activity.entity;

import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Thực thể Hành khách đăng ký tham gia Tour tham quan trên bờ (Shore Excursion)
 * Quản lý thông tin phân nhóm, xe đưa đón và trạng thái chuyến đi (KAN-176)
 */
@Entity
@Table(name = "excursion_passengers", indexes = {
        @Index(name = "idx_exc_passenger_excursion_id", columnList = "excursion_id"),
        @Index(name = "idx_exc_passenger_ticket_code", columnList = "ticket_code"),
        @Index(name = "idx_exc_passenger_cabin", columnList = "cabin_number"),
        @Index(name = "idx_exc_passenger_group", columnList = "group_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionPassenger extends BaseEntity {

    @Column(name = "excursion_id", nullable = false)
    private Long excursionId;

    @Column(name = "passenger_name", nullable = false, length = 150)
    private String passengerName;

    @Column(name = "cabin_number", nullable = false, length = 50)
    private String cabinNumber;

    @Column(name = "ticket_code", nullable = false, unique = true, length = 100)
    private String ticketCode;

    @Column(name = "passenger_id")
    private Long passengerId;

    @Column(name = "rfid_tag", length = 100)
    private String rfidTag;

    @Column(name = "bus_number", length = 100)
    private String busNumber;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(name = "emergency_phone", length = 50)
    private String emergencyPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ExcursionPassengerStatus status = ExcursionPassengerStatus.REGISTERED;

    @Column(name = "checked_in_at")
    private java.time.Instant checkedInAt;

    @Column(name = "returned_safe_at")
    private java.time.Instant returnedSafeAt;

    @Column(name = "special_requirements", length = 500)
    private String specialRequirements;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}

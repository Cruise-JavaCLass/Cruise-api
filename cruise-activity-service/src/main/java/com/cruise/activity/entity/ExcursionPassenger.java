package com.cruise.activity.entity;

import com.cruise.activity.enums.ExcursionBookingStatus;
import com.cruise.activity.enums.ExcursionPassengerStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thực thể Hành khách đăng ký tham gia Tour tham quan trên bờ (Shore Excursion)
 * Quản lý thông tin phân nhóm, xe đưa đón, trạng thái chuyến đi, hàng đợi chờ (Waitlist) và hoàn tiền (KAN-176, KAN-324, KAN-374)
 */
@Entity
@Table(name = "excursion_passengers", indexes = {
        @Index(name = "idx_exc_passenger_excursion_id", columnList = "excursion_id"),
        @Index(name = "idx_exc_passenger_ticket_code", columnList = "ticket_code"),
        @Index(name = "idx_exc_passenger_cabin", columnList = "cabin_number"),
        @Index(name = "idx_exc_passenger_group", columnList = "group_name"),
        @Index(name = "idx_exc_passenger_booking_status", columnList = "booking_status"),
        @Index(name = "idx_exc_passenger_waitlist", columnList = "excursion_id, booking_status, waitlist_position")
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
    @Column(name = "booking_status", nullable = false, length = 30)
    @Builder.Default
    private ExcursionBookingStatus bookingStatus = ExcursionBookingStatus.CONFIRMED;

    @Column(name = "waitlist_position")
    private Integer waitlistPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ExcursionPassengerStatus status = ExcursionPassengerStatus.REGISTERED;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "returned_safe_at")
    private Instant returnedSafeAt;

    @Column(name = "special_requirements", length = 500)
    private String specialRequirements;

    @Column(name = "charged_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal chargedAmount = BigDecimal.ZERO;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "penalty_fee", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal penaltyFee = BigDecimal.ZERO;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "is_included")
    @Builder.Default
    private Boolean isIncluded = false;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "medical_waiver")
    @Builder.Default
    private Boolean medicalWaiver = false;

    @Column(name = "medical_waiver_reason", length = 500)
    private String medicalWaiverReason;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}

package com.cruise.activity.entity;

import com.cruise.activity.enums.RegistrationStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thực thể Đăng ký hoạt động giải trí (Activity Registration - KAN-215 / KAN-216)
 */
@Entity
@Table(name = "activity_registrations", indexes = {
        @Index(name = "idx_act_reg_activity", columnList = "activity_id"),
        @Index(name = "idx_act_reg_passenger", columnList = "passenger_id"),
        @Index(name = "idx_act_reg_status", columnList = "status"),
        @Index(name = "idx_act_reg_act_status", columnList = "activity_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRegistration extends BaseEntity {

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "passenger_name", length = 150)
    private String passengerName;

    @Column(name = "cabin_number", nullable = false, length = 50)
    private String cabinNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.CONFIRMED;

    @Column(name = "charged_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal chargedAmount = BigDecimal.ZERO;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}

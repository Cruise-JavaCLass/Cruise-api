package com.cruise.activity.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thực thể Nhật ký kiểm toán bất biến cho đăng ký hoạt động (AC6 - Immutable Audit Logging)
 */
@Entity
@Table(name = "activity_registration_audit_logs", indexes = {
        @Index(name = "idx_act_reg_audit_reg", columnList = "registration_id"),
        @Index(name = "idx_act_reg_audit_act", columnList = "activity_id"),
        @Index(name = "idx_act_reg_audit_passenger", columnList = "passenger_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRegistrationAuditLog extends BaseEntity {

    @Column(name = "registration_id")
    private Long registrationId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "charged_amount", precision = 12, scale = 2)
    private BigDecimal chargedAmount;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "audit_time", nullable = false)
    private Instant auditTime;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "staff_id", length = 50)
    private String staffId;
}

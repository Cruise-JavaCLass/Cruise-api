package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.ServiceRecoveryCaseStatus;
import com.cruise.pos.enums.ServiceRecoveryRootCause;
import com.cruise.pos.enums.ServiceRecoverySeverity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "service_recovery_cases", indexes = {
        @Index(name = "idx_recovery_case_number", columnList = "case_number", unique = true),
        @Index(name = "idx_recovery_cabin", columnList = "cabin_number"),
        @Index(name = "idx_recovery_passenger", columnList = "passenger_id"),
        @Index(name = "idx_recovery_status", columnList = "status"),
        @Index(name = "idx_recovery_severity", columnList = "severity"),
        @Index(name = "idx_recovery_sla", columnList = "sla_due_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRecoveryCase extends BaseEntity {

    @Column(name = "case_number", nullable = false, unique = true, length = 64)
    private String caseNumber; // e.g. "SRC-20261022-001"

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "passenger_name", nullable = false, length = 128)
    private String passengerName;

    @Column(name = "cabin_number", nullable = false, length = 32)
    private String cabinNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "root_cause", nullable = false, length = 64)
    private ServiceRecoveryRootCause rootCause;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 64)
    private ServiceRecoverySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 64)
    @Builder.Default
    private ServiceRecoveryCaseStatus status = ServiceRecoveryCaseStatus.OPEN;

    @Column(name = "complaint_summary", nullable = false, length = 255)
    private String complaintSummary;

    @Column(name = "complaint_details", columnDefinition = "TEXT")
    private String complaintDetails;

    @Column(name = "assigned_handler", length = 64)
    private String assignedHandler;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "escalation_reason", length = 255)
    private String escalationReason;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}

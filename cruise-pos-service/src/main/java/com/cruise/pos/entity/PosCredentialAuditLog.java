package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.CredentialType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "pos_credential_audit_logs", indexes = {
        @Index(name = "idx_cred_audit_station", columnList = "station_id"),
        @Index(name = "idx_cred_audit_cashier", columnList = "cashier_id"),
        @Index(name = "idx_cred_audit_ts", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosCredentialAuditLog extends BaseEntity {

    @Column(name = "station_id", length = 64)
    private String stationId;

    @Column(name = "cashier_id", length = 64)
    private String cashierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", length = 32)
    private CredentialType credentialType;

    @Column(name = "masked_identifier", nullable = false, length = 128)
    private String maskedIdentifier;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "result", nullable = false, length = 64)
    private String result;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;
}

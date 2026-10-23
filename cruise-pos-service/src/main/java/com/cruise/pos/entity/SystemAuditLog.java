package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "system_audit_logs", indexes = {
        @Index(name = "idx_sys_audit_category", columnList = "category"),
        @Index(name = "idx_sys_audit_action", columnList = "action"),
        @Index(name = "idx_sys_audit_actor", columnList = "actor"),
        @Index(name = "idx_sys_audit_target", columnList = "target_resource, target_id"),
        @Index(name = "idx_sys_audit_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemAuditLog extends BaseEntity {

    @Column(name = "category", nullable = false, length = 64)
    private String category; // e.g. "DEVICE", "AUTH", "SECURITY", "CATALOG", "SETTLEMENT"

    @Column(name = "action", nullable = false, length = 64)
    private String action; // e.g. "DEVICE_PROVISIONED", "TOKEN_ROTATED", "REMOTE_WIPE"

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "target_resource", length = 64)
    private String targetResource;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "payload_before", columnDefinition = "TEXT")
    private String payloadBefore;

    @Column(name = "payload_after", columnDefinition = "TEXT")
    private String payloadAfter;

    @Column(name = "immutable_hash", length = 128)
    private String immutableHash;

    @Override
    @PrePersist
    public void onCreate() {
        Instant preset = getCreatedAt();
        super.onCreate();
        if (preset != null) {
            setCreatedAt(preset);
        }
    }
}

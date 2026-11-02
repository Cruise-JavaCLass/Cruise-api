package com.cruise.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only identity mutations; never contains passwords or bearer tokens. */
@Entity
@Table(name = "identity_audit_events", indexes = @Index(name = "idx_identity_audit_occurred", columnList = "occurred_at"))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class IdentityAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(nullable = false, updatable = false, length = 30)
    private String action;
    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;
    @Column(name = "target_username", nullable = false, updatable = false, length = 50)
    private String targetUsername;
    @Column(name = "target_role", nullable = false, updatable = false, length = 30)
    private String targetRole;

    public IdentityAuditEvent(String action, Long actorUserId, String targetUsername, String targetRole) {
        this.occurredAt = Instant.now();
        this.action = action;
        this.actorUserId = actorUserId;
        this.targetUsername = targetUsername;
        this.targetRole = targetRole;
    }
}

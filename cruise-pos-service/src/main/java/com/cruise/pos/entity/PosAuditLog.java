package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "pos_audit_logs", indexes = {
        @Index(name = "idx_audit_transaction", columnList = "transaction_id"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosAuditLog extends BaseEntity {

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "action", nullable = false, length = 64)
    private String action; // e.g. "CHARGE", "VOID", "REFUND", "OVERRIDE"

    @Column(name = "performed_by", nullable = false, length = 64)
    private String performedBy;

    @Column(name = "manager_id", length = 64)
    private String managerId;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "details", length = 1024)
    private String details;
}

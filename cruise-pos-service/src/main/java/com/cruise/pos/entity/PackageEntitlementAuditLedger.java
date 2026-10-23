package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pos_package_entitlement_audit_ledgers", indexes = {
        @Index(name = "idx_pkg_audit_entitlement", columnList = "entitlement_id"),
        @Index(name = "idx_pkg_audit_passenger", columnList = "passenger_id"),
        @Index(name = "idx_pkg_audit_tx", columnList = "transaction_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageEntitlementAuditLedger extends BaseEntity {

    @Column(name = "entitlement_id", nullable = false)
    private Long entitlementId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "operation_type", nullable = false, length = 32)
    private String operationType; // DEDUCTION, ROLLBACK, COLLISION_RESOLVED

    @Column(name = "quota_changed", nullable = false)
    private Integer quotaChanged;

    @Column(name = "amount_covered", precision = 10, scale = 2)
    private BigDecimal amountCovered;

    @Column(name = "product_name", length = 150)
    private String productName;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "notes", length = 255)
    private String notes;
}

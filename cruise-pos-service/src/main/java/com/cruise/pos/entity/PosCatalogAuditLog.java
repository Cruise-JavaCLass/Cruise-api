package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "pos_catalog_audit_logs", indexes = {
        @Index(name = "idx_catalog_audit_sku", columnList = "sku"),
        @Index(name = "idx_catalog_audit_action", columnList = "action"),
        @Index(name = "idx_catalog_audit_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosCatalogAuditLog extends BaseEntity {

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sku", length = 64)
    private String sku;

    @Column(name = "action", nullable = false, length = 64)
    private String action; // e.g. "PRICE_UPDATE", "86ED", "RESTORE_86ED", "BULK_IMPORT"

    @Column(name = "old_value", length = 256)
    private String oldValue;

    @Column(name = "new_value", length = 256)
    private String newValue;

    @Column(name = "performed_by", length = 64)
    private String performedBy;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "details", length = 1024)
    private String details;
}

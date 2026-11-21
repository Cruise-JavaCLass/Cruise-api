package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.FolioDisputeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * Nhật ký kiểm toán bất biến của khiếu nại: chỉ thêm mới, không bao giờ cập nhật hay xóa.
 */
@Entity
@Immutable
@Table(name = "billing_folio_dispute_audits", indexes = {
        @Index(name = "idx_billing_dispute_audit_dispute", columnList = "dispute_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString(exclude = "dispute")
public class FolioDisputeAuditEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispute_id", nullable = false, updatable = false)
    private FolioDispute dispute;

    @Column(name = "action", nullable = false, updatable = false, length = 32)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 32)
    private FolioDisputeStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 32)
    private FolioDisputeStatus toStatus;

    @Column(name = "actor_id", nullable = false, updatable = false, length = 64)
    private String actorId;

    @Column(name = "notes", updatable = false, length = 1000)
    private String notes;

    @Column(name = "amount", updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}

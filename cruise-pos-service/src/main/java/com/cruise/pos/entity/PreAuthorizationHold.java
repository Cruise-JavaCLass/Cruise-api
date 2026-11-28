package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.PreAuthHoldStatus;
import com.cruise.pos.exception.BusinessRuleException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Khoản tạm giữ hạn mức thẻ khi khách lên tàu (Check-in).
 * Khoản tạm giữ đang hiệu lực làm tăng mức chi tiêu tối đa của folio.
 */
@Entity
@Table(name = "billing_pre_auth_holds", indexes = {
        @Index(name = "idx_billing_hold_folio_status", columnList = "folio_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "folio")
public class PreAuthorizationHold extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folio_id", nullable = false)
    private CabinFolio folio;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "hold_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal holdAmount;

    @Column(name = "captured_amount", precision = 12, scale = 2)
    private BigDecimal capturedAmount;

    @Column(name = "hold_reference_code", nullable = false, unique = true, length = 64)
    private String holdReferenceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private PreAuthHoldStatus status = PreAuthHoldStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    /** Quyết toán khoản tạm giữ (ACTIVE -> CAPTURED) */
    public void capture(BigDecimal finalAmount, Instant now) {
        requireActive("quyết toán");
        this.capturedAmount = finalAmount;
        this.status = PreAuthHoldStatus.CAPTURED;
        this.resolvedAt = now;
    }

    /** Hủy khoản tạm giữ (ACTIVE -> RELEASED) */
    public void release(Instant now) {
        requireActive("hủy");
        this.status = PreAuthHoldStatus.RELEASED;
        this.resolvedAt = now;
    }

    private void requireActive(String action) {
        if (status != PreAuthHoldStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Không thể " + action + " khoản tạm giữ " + holdReferenceCode + " ở trạng thái " + status);
        }
    }
}

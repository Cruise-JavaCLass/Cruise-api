package com.cruise.activity.entity;

import com.cruise.activity.enums.AdjustmentType;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Thực thể chi tiết điều chỉnh / tranh chấp chi phí quyết toán nhà cung cấp (KAN-332, KAN-334)
 */
@Entity
@Table(name = "provider_settlement_adjustments", indexes = {
        @Index(name = "idx_psa_settlement_id", columnList = "settlement_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderSettlementAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private ProviderSettlement settlement;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 50)
    private AdjustmentType adjustmentType;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount; // Dương nếu là ExtraService, âm hoặc giảm trừ nếu là Penalty

    @Column(name = "invoice_reference", length = 150)
    private String invoiceReference;

    @Column(name = "explanation", length = 1000, nullable = false)
    private String explanation;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;
}

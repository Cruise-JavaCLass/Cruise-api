package com.cruise.activity.entity;

import com.cruise.activity.enums.SettlementStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Thực thể Quyết toán chi phí nhà cung cấp tour (Tour Provider Settlement) (KAN-332, KAN-333)
 */
@Entity
@Table(name = "provider_settlements", indexes = {
        @Index(name = "idx_ps_provider_id", columnList = "provider_id"),
        @Index(name = "idx_ps_excursion_id", columnList = "excursion_id"),
        @Index(name = "idx_ps_voyage_id", columnList = "voyage_id"),
        @Index(name = "idx_ps_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_ps_excursion_provider", columnNames = {"excursion_id", "provider_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderSettlement extends BaseEntity {

    @Column(name = "provider_id", nullable = false, length = 150)
    private String providerId;

    @Column(name = "provider_name", nullable = false, length = 200)
    private String providerName;

    @Column(name = "excursion_id", nullable = false)
    private Long excursionId;

    @Column(name = "excursion_title", nullable = false, length = 200)
    private String excursionTitle;

    @Column(name = "voyage_id", nullable = false)
    private Long voyageId;

    @Column(name = "port_id")
    private Long portId;

    @Column(name = "port_name", length = 150)
    private String portName;

    @Column(name = "agreed_unit_rate", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal agreedUnitRate = BigDecimal.ZERO;

    @Column(name = "min_guarantee_pax", nullable = false)
    @Builder.Default
    private Integer minGuaranteePax = 0;

    @Column(name = "registered_pax", nullable = false)
    @Builder.Default
    private Integer registeredPax = 0;

    @Column(name = "actual_pax", nullable = false)
    @Builder.Default
    private Integer actualPax = 0;

    @Column(name = "no_show_pax", nullable = false)
    @Builder.Default
    private Integer noShowPax = 0;

    @Column(name = "billable_pax", nullable = false)
    @Builder.Default
    private Integer billablePax = 0;

    @Column(name = "base_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal baseAmount = BigDecimal.ZERO;

    @Column(name = "adjusted_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal adjustedAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "currency", length = 10, nullable = false)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.DRAFT;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProviderSettlementAdjustment> adjustments = new ArrayList<>();

    public void addAdjustment(ProviderSettlementAdjustment adjustment) {
        adjustments.add(adjustment);
        adjustment.setSettlement(this);
    }
}

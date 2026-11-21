package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.FolioSplitType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Quy tắc chia phí chung của folio cho từng khách (ở cùng phòng hoặc đi theo đoàn).
 * splitValue: phần trăm (PERCENTAGE) hoặc số tiền (FIXED_AMOUNT); bỏ trống với EQUAL_SPLIT.
 */
@Entity
@Table(name = "billing_folio_split_rules",
        uniqueConstraints = @UniqueConstraint(name = "uk_billing_split_folio_passenger",
                columnNames = {"folio_id", "passenger_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "folio")
public class FolioSplitRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folio_id", nullable = false)
    private CabinFolio folio;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 32)
    private FolioSplitType splitType;

    @Column(name = "split_value", precision = 12, scale = 2)
    private BigDecimal splitValue;
}

package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pos_product_modifiers", indexes = {
        @Index(name = "idx_modifier_product_id", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosProductModifier extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Builder.Default
    @Column(name = "price_delta", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = false;

    @Builder.Default
    @Column(name = "min_selection", nullable = false)
    private Integer minSelection = 0;

    @Builder.Default
    @Column(name = "max_selection", nullable = false)
    private Integer maxSelection = 1;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";
}

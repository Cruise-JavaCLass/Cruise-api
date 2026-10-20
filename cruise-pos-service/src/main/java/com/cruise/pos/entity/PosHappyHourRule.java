package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pos_happy_hour_rules", indexes = {
        @Index(name = "idx_hh_outlet_id", columnList = "outlet_id"),
        @Index(name = "idx_hh_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosHappyHourRule extends BaseEntity {

    @Column(name = "outlet_id")
    private Long outletId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "start_time", nullable = false, length = 8)
    private String startTime; // e.g. "17:00"

    @Column(name = "end_time", nullable = false, length = 8)
    private String endTime; // e.g. "19:00"

    @Builder.Default
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent = new BigDecimal("20.00");

    @Builder.Default
    @Column(name = "days_of_week", length = 64)
    private String daysOfWeek = "ALL";

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "description", length = 256)
    private String description;
}

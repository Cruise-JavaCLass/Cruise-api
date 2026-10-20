package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import com.cruise.pos.enums.OutletType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pos_outlets", indexes = {
        @Index(name = "idx_pos_outlet_code", columnList = "code", unique = true),
        @Index(name = "idx_pos_outlet_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosOutlet extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "outlet_type", nullable = false, length = 32)
    private OutletType outletType;

    @Column(name = "location", length = 128)
    private String location;

    @Column(name = "open_time", length = 16)
    private String openTime;

    @Column(name = "close_time", length = 16)
    private String closeTime;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "description", length = 512)
    private String description;
}

package com.cruise.pos.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pos_categories", indexes = {
        @Index(name = "idx_pos_category_code", columnList = "code"),
        @Index(name = "idx_pos_category_outlet", columnList = "outlet_id"),
        @Index(name = "idx_pos_category_parent", columnList = "parent_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosCategory extends BaseEntity {

    @Column(name = "outlet_id")
    private Long outletId;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "icon", length = 64)
    private String icon;

    @Column(name = "color", length = 32)
    private String color;

    @Column(name = "description", length = 256)
    private String description;
}

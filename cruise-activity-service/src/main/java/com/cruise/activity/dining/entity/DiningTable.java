package com.cruise.activity.dining.entity;

import com.cruise.activity.dining.enums.DiningTableType;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "dining_tables",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_dining_table_restaurant_number",
            columnNames = {"restaurant_id", "table_number"}
        )
    },
    indexes = {
        @Index(name = "idx_dining_table_restaurant", columnList = "restaurant_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiningTable extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "table_number", nullable = false, length = 20)
    private String tableNumber;

    @Column(nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "table_type", nullable = false, length = 30)
    private DiningTableType tableType;
}

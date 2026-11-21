package com.cruise.activity.dining.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dining_restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    // Deck number, ví dụ: Deck 5
    @Column(nullable = false, length = 50)
    private String location;

    @Column(name = "cuisine_type", length = 100)
    private String cuisineType;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Column(name = "opening_hours", length = 500)
    private String openingHours;

    @OneToMany(mappedBy = "restaurant", fetch = FetchType.LAZY)
    @Builder.Default
    private List<DiningTable> tables = new ArrayList<>();
}

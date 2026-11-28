package com.cruise.activity.dining.entity;

import com.cruise.activity.dining.enums.AllergenType;
import com.cruise.activity.dining.enums.SeverityLevel;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "guest_dietary_profiles",
    indexes = {
        @Index(name = "idx_dietary_profile_passenger", columnList = "passenger_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestDietaryProfile extends BaseEntity {

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "allergen_type", nullable = false, length = 30)
    private AllergenType allergenType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity_level", nullable = false, length = 20)
    private SeverityLevel severityLevel;

    @Column(name = "special_notes", length = 1000)
    private String specialNotes;
}

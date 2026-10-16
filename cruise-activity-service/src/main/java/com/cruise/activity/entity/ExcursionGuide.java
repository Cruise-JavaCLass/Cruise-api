package com.cruise.activity.entity;

import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.enums.GuideStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Thực thể Hướng dẫn viên tham gia điều phối tour bờ (KAN-177)
 */
@Entity
@Table(name = "excursion_guides", indexes = {
        @Index(name = "idx_exc_guide_excursion_id", columnList = "excursion_id"),
        @Index(name = "idx_exc_guide_code", columnList = "guide_code"),
        @Index(name = "idx_exc_guide_license", columnList = "license_number"),
        @Index(name = "idx_exc_guide_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionGuide extends BaseEntity {

    @Column(name = "excursion_id", nullable = false)
    private Long excursionId;

    @Column(name = "guide_code", length = 50)
    private String guideCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    @Builder.Default
    private GuideRole role = GuideRole.LEAD;

    @Column(name = "languages", length = 200)
    private String languages;

    @Column(name = "license_number", length = 100)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private GuideStatus status = GuideStatus.ACTIVE;

    @Column(name = "rating")
    @Builder.Default
    private Double rating = 5.0;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}

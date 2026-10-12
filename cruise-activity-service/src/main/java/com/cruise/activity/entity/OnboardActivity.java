package com.cruise.activity.entity;

import com.cruise.activity.enums.ActivityStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Thực thể Hoạt động & Dịch vụ trên du thuyền (Onboard Activity)
 */
@Entity
@Table(name = "activities", indexes = {
        @Index(name = "idx_activity_tour_id", columnList = "tour_id"),
        @Index(name = "idx_activity_date", columnList = "activity_date"),
        @Index(name = "idx_activity_location", columnList = "location"),
        @Index(name = "idx_activity_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardActivity extends BaseEntity {

    @Column(name = "tour_id", nullable = false)
    private Long tourId;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "location", nullable = false, length = 150)
    private String location;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @Column(name = "current_participants", nullable = false)
    @Builder.Default
    private Integer currentParticipants = 0;

    @Column(name = "price", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "is_included", nullable = false)
    @Builder.Default
    private Boolean isIncluded = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ActivityStatus status = ActivityStatus.SCHEDULED;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}

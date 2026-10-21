package com.cruise.activity.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Thực thể Phản hồi & Đánh giá trải nghiệm sau hoạt động (KAN-364)
 */
@Entity
@Table(name = "activity_feedbacks", indexes = {
        @Index(name = "idx_af_activity_id", columnList = "activity_id"),
        @Index(name = "idx_af_passenger_id", columnList = "passenger_id"),
        @Index(name = "idx_af_rating", columnList = "rating"),
        @Index(name = "idx_af_escalated", columnList = "escalated")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_af_activity_passenger", columnNames = {"activity_id", "passenger_id"}),
        @UniqueConstraint(name = "uk_af_idempotency_key", columnNames = {"idempotency_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityFeedback extends BaseEntity {

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "rating", nullable = false)
    private Integer rating; // 1 - 5 stars

    @Column(name = "organization_rating")
    private Integer organizationRating; // 1 - 5

    @Column(name = "guide_rating")
    private Integer guideRating; // 1 - 5

    @Column(name = "value_rating")
    private Integer valueRating; // 1 - 5

    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "escalated", nullable = false)
    @Builder.Default
    private Boolean escalated = false;

    @Column(name = "escalation_reason", length = 300)
    private String escalationReason;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
}

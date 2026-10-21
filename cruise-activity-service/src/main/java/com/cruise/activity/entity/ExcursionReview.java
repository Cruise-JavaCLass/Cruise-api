package com.cruise.activity.entity;

import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Thực thể Đánh giá chất lượng Tour trên bờ (Shore Excursion Review) (KAN-341)
 * Thu thập phản hồi từ hành khách sau khi tham gia tour
 */
@Entity
@Table(name = "excursion_reviews", indexes = {
        @Index(name = "idx_er_excursion_id", columnList = "excursion_id"),
        @Index(name = "idx_er_passenger_id", columnList = "passenger_id"),
        @Index(name = "idx_er_rating_overall", columnList = "rating_overall"),
        @Index(name = "idx_er_is_flagged", columnList = "is_flagged")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_er_excursion_passenger", columnNames = {"excursion_id", "passenger_id"}),
        @UniqueConstraint(name = "uk_er_idempotency_key", columnNames = {"idempotency_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcursionReview extends BaseEntity {

    @Column(name = "excursion_id", nullable = false)
    private Long excursionId;

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "rating_overall", nullable = false)
    private Integer ratingOverall; // 1 - 5 stars

    @Column(name = "rating_guide")
    private Integer ratingGuide; // 1 - 5 stars

    @Column(name = "rating_bus")
    private Integer ratingBus; // 1 - 5 stars

    @Column(name = "rating_value")
    private Integer ratingValue; // 1 - 5 stars: Value for Money

    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "is_flagged", nullable = false)
    @Builder.Default
    private Boolean isFlagged = false;

    @Column(name = "flag_reason", length = 300)
    private String flagReason;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;
}

package com.cruise.activity.entity;

import com.cruise.activity.enums.FeedbackCategory;
import com.cruise.activity.enums.FeedbackStatus;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "passenger_service_reviews", indexes = {
        @Index(name = "idx_review_passenger", columnList = "passenger_id"),
        @Index(name = "idx_review_cabin", columnList = "cabin_number"),
        @Index(name = "idx_review_category", columnList = "category"),
        @Index(name = "idx_review_status", columnList = "status"),
        @Index(name = "idx_review_rating", columnList = "rating")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerServiceReview extends BaseEntity {

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "passenger_name", nullable = false, length = 150)
    private String passengerName;

    @Column(name = "cabin_number", nullable = false, length = 50)
    private String cabinNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private FeedbackCategory category;

    @Column(name = "rating", nullable = false)
    private Integer rating; // 1 to 5 stars

    @Column(name = "sentiment", length = 32)
    private String sentiment; // POSITIVE, NEUTRAL, NEGATIVE

    @Column(name = "comments", length = 1000)
    private String comments;

    @Column(name = "service_outlet", length = 100)
    private String serviceOutlet;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private FeedbackStatus status = FeedbackStatus.NEW;

    @Column(name = "is_low_rating_alert", nullable = false)
    @Builder.Default
    private Boolean isLowRatingAlert = false;

    @Column(name = "escalation_reason", length = 255)
    private String escalationReason;

    @Column(name = "admin_notes", length = 1000)
    private String adminNotes;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}

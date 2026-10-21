package com.cruise.activity.entity;

import com.cruise.activity.enums.NotificationCategory;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Thực thể Thông báo trong ứng dụng cho Hành khách (KAN-363)
 */
@Entity
@Table(name = "passenger_notifications", indexes = {
        @Index(name = "idx_pnotif_passenger_id", columnList = "passenger_id"),
        @Index(name = "idx_pnotif_category", columnList = "category"),
        @Index(name = "idx_pnotif_is_read", columnList = "is_read"),
        @Index(name = "idx_pnotif_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PassengerNotification extends BaseEntity {

    @Column(name = "passenger_id", nullable = false)
    private Long passengerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private NotificationCategory category;

    @Column(name = "action_url", length = 300)
    private String actionUrl;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "metadata", length = 500)
    private String metadata;
}

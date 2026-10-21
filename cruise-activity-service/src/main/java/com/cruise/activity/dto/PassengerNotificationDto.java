package com.cruise.activity.dto;

import com.cruise.activity.enums.NotificationCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO hiển thị thông báo hành khách (KAN-363)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerNotificationDto {
    private Long id;
    private Long passengerId;
    private String title;
    private String message;
    private NotificationCategory category;
    private String actionUrl;
    private Boolean isRead;
    private Instant readAt;
    private Instant createdAt;
    private String metadata;
}

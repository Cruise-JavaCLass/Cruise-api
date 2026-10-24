package com.cruise.activity.dto;

import com.cruise.activity.enums.NotificationCategory;
import com.cruise.activity.enums.NotificationScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalNotificationEventDto {
    private String broadcastId;
    private String title;
    private String message;
    private NotificationCategory category;
    private NotificationScope scope;
    private Integer targetDeck;
    private String targetZone;
    private String actionUrl;
    private Boolean playAlertSound;
    private Boolean requireAcknowledgment;
    private Instant broadcastAt;
    private int recipientCount;
    private Map<String, Object> metadata;
}

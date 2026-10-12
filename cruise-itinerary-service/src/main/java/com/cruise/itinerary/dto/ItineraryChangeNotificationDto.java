package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.ChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO thông báo gửi tới hành khách hoặc thuyền viên khi lịch trình thay đổi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryChangeNotificationDto {

    private Long tourId;
    private String tourCode;
    private String tourName;
    private Long dailyItineraryId;
    private Integer dayNumber;
    private ChangeType changeType;
    private String subject;
    private String message;
    private String urgency; // "NORMAL", "URGENT", "CRITICAL"
    private Instant createdAt;
}

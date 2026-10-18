package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Phản hồi Thẻ vé điện tử kèm mã QR động (KAN-222)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityPassResponse {

    private String token;
    private Instant expiresAt;
    private Long activityId;
    private String activityTitle;
    private String category;
    private String venue;
    private String location;
    private String deck;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String rfidTag;
}

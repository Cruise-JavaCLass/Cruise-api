package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Phản hồi kết quả điểm danh hoạt động (KAN-222)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityCheckInResponse {

    private Long attendanceId;
    private Long activityId;
    private String activityTitle;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String status;
    private Instant checkInTime;
    private String staffId;
    private String staffName;
    private String method;
    private String message;
}

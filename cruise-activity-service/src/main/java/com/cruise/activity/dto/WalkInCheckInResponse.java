package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Phản hồi đăng ký và check-in khách vãng lai (KAN-320)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalkInCheckInResponse {

    private Long attendanceId;
    private Long activityId;
    private String activityTitle;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private BigDecimal chargedAmount;
    private String transactionId;
    private String status;
    private Instant checkInTime;
    private Boolean isWalkIn;
    private String message;
}

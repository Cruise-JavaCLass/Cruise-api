package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bản ghi hành khách trong danh sách điểm danh (Roster) của hoạt động (KAN-321)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRosterPassengerResponse {

    private Long attendanceId;
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String rfidTag;
    private String status;
    private Instant checkedInAt;
    private String checkedInByStaffId;
    private String checkInMethod;
    private Boolean isWalkIn;
    private BigDecimal chargedAmount;
    private String transactionId;
}

package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Thống kê điểm danh sự kiện theo thời gian thực (KAN-321)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityAttendanceSummaryResponse {

    private Long activityId;
    private String activityTitle;
    private Integer maxCapacity;
    private Long totalRegistered;
    private Long checkedInCount;
    private Integer remainingCapacity;
    private Double attendanceRate;
    private Long walkInCount;
    private Long pendingCount;
    private BigDecimal totalPrice;
    private Boolean isCapacityFull;
}

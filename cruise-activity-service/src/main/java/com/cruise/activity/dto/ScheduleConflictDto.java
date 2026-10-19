package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * DTO mô tả thông tin chi tiết một xung đột thời gian giữa hai sự kiện (KAN-350)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleConflictDto {

    private Long firstEventId;
    private String firstEventTitle;
    private String firstEventType;
    private LocalTime firstStartTime;
    private LocalTime firstEndTime;

    private Long secondEventId;
    private String secondEventTitle;
    private String secondEventType;
    private LocalTime secondStartTime;
    private LocalTime secondEndTime;

    private Long overlapMinutes;
    private String description;
}

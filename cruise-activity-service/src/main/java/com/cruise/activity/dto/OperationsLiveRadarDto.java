package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationsLiveRadarDto {
    private Instant timestamp;
    private List<ActivityAttendanceRadarItem> activityRadar;
    private List<ExcursionDepartureRadarItem> excursionRadar;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityAttendanceRadarItem {
        private Long activityId;
        private String title;
        private String location;
        private String scheduledTime;
        private int capacity;
        private int registeredCount;
        private int checkedInCount;
        private double occupancyRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExcursionDepartureRadarItem {
        private Long excursionId;
        private String title;
        private String portName;
        private String departureTime;
        private String status;
        private int maxParticipants;
        private int bookedCount;
        private int presentCount;
        private boolean isDepartingSoon;
    }
}

package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionPassengerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response thống kê điểm danh và cảnh báo hành khách chưa về tàu (KAN-317)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcursionAttendanceSummaryResponse {

    private Long excursionId;
    private String excursionTitle;
    private String returnToShipTime;
    private int totalRegistered;
    private int totalCheckedIn;
    private int totalOnTour;
    private int totalReturnedSafe;
    private int totalPendingDeparture;
    private int totalMissingReturn;
    private List<BusAttendanceStat> busStats;
    private List<GroupAttendanceStat> groupStats;
    private List<MissingPassengerItem> missingPassengers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusAttendanceStat {
        private String busNumber;
        private int total;
        private int checkedIn;
        private int returnedSafe;
        private int missing;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupAttendanceStat {
        private String groupName;
        private int total;
        private int checkedIn;
        private int returnedSafe;
        private int missing;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingPassengerItem {
        private Long excursionPassengerId;
        private Long passengerId;
        private String passengerName;
        private String cabinNumber;
        private String ticketCode;
        private String rfidTag;
        private String busNumber;
        private String groupName;
        private String emergencyPhone;
        private ExcursionPassengerStatus currentStatus;
    }
}

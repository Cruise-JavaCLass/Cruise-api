package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO tổng hợp thông tin booking và tóm tắt chuyến đi của hành khách (KAN-197)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerBookingSummaryResponse {

    private Long passengerId;
    private Long tourId;
    private String tourName;
    private String tourCode;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String cabinNumber;
    private String cabinCategory;
    private String rfidTag;
    private String qrCode;
    private String tourPackage;
    private String bleZone;
    private com.cruise.activity.enums.RegistrationStatus registrationStatus;
    private com.cruise.activity.enums.CheckInStatus checkInStatus;
    private String boardedAt;

    // Booking financials & excursion summary
    private Integer totalRegisteredExcursions;
    private BigDecimal totalExcursionSpent;
    private List<ExcursionSummaryItem> registeredExcursions;

    /**
     * Booking ticket, group and transport logistics, schedule, and charge for a matched excursion.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExcursionSummaryItem {
        private Long excursionId;
        private String excursionTitle;
        private String ticketCode;
        private String groupName;
        private String busNumber;
        private String status;
        private String gatheringTime;
        private String returnToShipTime;
        private String portName;
        private BigDecimal price;
    }
}

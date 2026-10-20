package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO đại diện cho một sự kiện (hoạt động hoặc tour trên bờ) trong lịch trình cá nhân của hành khách (KAN-349 / KAN-350)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalScheduleItemDto {

    private Long eventId;
    private String itemType; // "ACTIVITY" hoặc "EXCURSION"
    private String title;
    private String category;
    private Integer dayNumber;
    private LocalDate activityDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalTime assemblyTime; // Giờ tập trung (cho excursion)
    private LocalTime departureTime; // Giờ xuất phát (cho excursion)
    private String assemblyWarning; // Cảnh báo tập trung trước giờ khởi hành
    private String location; // Địa điểm hoặc Tên cảng
    private String deck; // Boong tàu (cho activity) hoặc meeting point
    private String status; // REGISTERED, CHECKED_IN, COMPLETED, CANCELLED
    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private String ticketCode;
    private BigDecimal price;
    private Boolean isIncluded;

    // Các trường phục vụ phát hiện xung đột lịch (KAN-350)
    @Builder.Default
    private Boolean hasConflict = false;

    private String conflictReason;

    @Builder.Default
    private List<Long> conflictWithIds = new ArrayList<>();
}

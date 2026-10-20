package com.cruise.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Phản hồi tổng hợp lịch trình cá nhân / cabin theo ngày của hành khách (KAN-349 / KAN-350)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalScheduleResponse {

    private Long passengerId;
    private String passengerName;
    private String cabinNumber;
    private Integer dayNumber;
    private String scope; // "my" hoặc "cabin"

    @Builder.Default
    private Integer totalItems = 0;

    @Builder.Default
    private Integer totalConflicts = 0;

    @Builder.Default
    private List<PersonalScheduleItemDto> items = new ArrayList<>();

    @Builder.Default
    private List<ScheduleConflictDto> conflicts = new ArrayList<>();
}

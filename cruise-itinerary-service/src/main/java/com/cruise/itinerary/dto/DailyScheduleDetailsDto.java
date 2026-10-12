package com.cruise.itinerary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalTime;

/**
 * Lớp chứa các thuộc tính hoạt động cập cảng và thời tiết dùng chung
 * để ngăn trùng lặp mã nguồn theo tiêu chuẩn SonarCloud CPD.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class DailyScheduleDetailsDto {

    private String title;

    private String description;

    private Long portId;

    private String portName;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime arrivalTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime departureTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime allAboardTime;

    private Boolean isSeaDay;

    private String weatherForecast;
}

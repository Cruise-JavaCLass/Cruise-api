package com.cruise.itinerary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Lớp cơ sở chứa các thuộc tính chung của Lịch trình theo ngày (Daily Itinerary)
 * nhằm tránh trùng lặp mã nguồn theo tiêu chuẩn SonarCloud CPD.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDailyItineraryDto {

    @NotNull(message = "Số thứ tự ngày không được để trống")
    private Integer dayNumber;

    @NotNull(message = "Ngày lịch trình không được để trống")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate itineraryDate;

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

    private Boolean isAffected;

    private String changeReason;

    private com.cruise.itinerary.enums.ChangeType changeType;
}

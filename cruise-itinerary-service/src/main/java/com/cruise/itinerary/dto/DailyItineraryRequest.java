package com.cruise.itinerary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyItineraryRequest {

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

    @Builder.Default
    private Boolean isSeaDay = false;

    private String weatherForecast;
}

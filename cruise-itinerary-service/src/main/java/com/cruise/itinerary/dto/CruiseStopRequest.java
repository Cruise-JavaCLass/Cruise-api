package com.cruise.itinerary.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CruiseStopRequest {

    @NotNull(message = "ID cảng không được để trống")
    private Long portId;

    @NotNull(message = "Thứ tự điểm dừng không được để trống")
    @Positive(message = "Thứ tự điểm dừng phải là số dương")
    private Integer stopOrder;

    @NotNull(message = "Thời gian cập cảng không được để trống")
    private LocalDateTime arrivalTime;

    @NotNull(message = "Thời gian rời cảng không được để trống")
    private LocalDateTime departureTime;

    private LocalDateTime allAboardTime;

    private String description;

    private Boolean active = true;
}

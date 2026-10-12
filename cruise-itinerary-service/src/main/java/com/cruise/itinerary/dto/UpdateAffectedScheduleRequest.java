package com.cruise.itinerary.dto;

import com.cruise.itinerary.enums.ChangeType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * DTO yêu cầu cập nhật lịch trình bị ảnh hưởng do thời tiết hoặc tình huống khẩn cấp (KAN-125)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAffectedScheduleRequest {

    @NotNull(message = "Loại thay đổi không được để trống")
    private ChangeType changeType;

    @NotBlank(message = "Lý do thay đổi lịch trình không được để trống")
    private String reason;

    private String changedBy;

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

    private String description;
}

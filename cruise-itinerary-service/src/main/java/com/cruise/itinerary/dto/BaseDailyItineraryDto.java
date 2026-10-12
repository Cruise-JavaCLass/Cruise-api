package com.cruise.itinerary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * Lớp cơ sở chứa các thuộc tính định danh và ngày của Lịch trình theo ngày (Daily Itinerary)
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseDailyItineraryDto extends DailyScheduleDetailsDto {

    @NotNull(message = "Số thứ tự ngày không được để trống")
    private Integer dayNumber;

    @NotNull(message = "Ngày lịch trình không được để trống")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate itineraryDate;

    private Boolean isAffected;

    private String changeReason;

    private com.cruise.itinerary.enums.ChangeType changeType;
}

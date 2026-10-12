package com.cruise.activity.dto;

import com.cruise.activity.enums.ActivityStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request payload cập nhật toàn diện thông tin hoạt động (KAN-134)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateActivityRequest {

    private Integer dayNumber;

    @NotBlank(message = "Tên hoạt động không được để trống")
    private String title;

    private String description;

    private String category;

    @NotBlank(message = "Địa điểm tổ chức không được để trống")
    private String location;

    @NotNull(message = "Ngày diễn ra không được để trống")
    private LocalDate activityDate;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private LocalTime startTime;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    private LocalTime endTime;

    @PositiveOrZero(message = "Sức chứa tối đa không được âm")
    private Integer maxCapacity;

    @PositiveOrZero(message = "Giá dịch vụ không được âm")
    private BigDecimal price;

    private Boolean isIncluded;

    private ActivityStatus status;
}

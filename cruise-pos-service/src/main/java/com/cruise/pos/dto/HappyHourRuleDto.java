package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HappyHourRuleDto {
    private Long id;
    private Long outletId;

    @NotBlank(message = "Tên quy tắc không được để trống")
    private String name;

    @NotBlank(message = "Thời gian bắt đầu không được để trống (HH:mm)")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Thời gian bắt đầu phải có định dạng HH:mm")
    private String startTime;

    @NotBlank(message = "Thời gian kết thúc không được để trống (HH:mm)")
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Thời gian kết thúc phải có định dạng HH:mm")
    private String endTime;

    @NotNull(message = "Tỷ lệ chiết khấu không được để trống")
    @DecimalMin(value = "0.00", message = "Tỷ lệ chiết khấu phải >= 0")
    @DecimalMax(value = "100.00", message = "Tỷ lệ chiết khấu phải <= 100")
    private BigDecimal discountPercent;

    private String daysOfWeek;
    private Boolean isActive;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}

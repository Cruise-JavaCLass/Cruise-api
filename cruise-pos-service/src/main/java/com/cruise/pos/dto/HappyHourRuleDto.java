package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String startTime;

    @NotBlank(message = "Thời gian kết thúc không được để trống (HH:mm)")
    private String endTime;

    @NotNull(message = "Tỷ lệ chiết khấu không được để trống")
    @DecimalMin(value = "0.00", message = "Tỷ lệ chiết khấu phải >= 0")
    private BigDecimal discountPercent;

    private String daysOfWeek;
    private Boolean isActive;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}

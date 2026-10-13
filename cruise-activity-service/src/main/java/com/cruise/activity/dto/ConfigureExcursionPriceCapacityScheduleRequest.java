package com.cruise.activity.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request DTO chuyên biệt cấu hình Giá, Sức chứa và Lịch trình cho Tour bờ (KAN-168)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigureExcursionPriceCapacityScheduleRequest {

    /**
     * Ngày diễn ra tour bờ (tùy chọn cập nhật ngày)
     */
    private LocalDate excursionDate;

    /**
     * Giờ tập trung hành khách
     */
    @NotNull(message = "Giờ tập trung không được để trống")
    private LocalTime gatheringTime;

    /**
     * Giờ xuất phát tour bờ
     */
    @NotNull(message = "Giờ xuất phát không được để trống")
    private LocalTime departureTime;

    /**
     * Giờ hành khách quay trở lại tàu
     */
    @NotNull(message = "Giờ quay lại tàu không được để trống")
    private LocalTime returnToShipTime;

    /**
     * Sức chứa tối đa (Max Capacity)
     */
    @NotNull(message = "Sức chứa tối đa không được để trống")
    @Min(value = 1, message = "Sức chứa tối đa phải lớn hơn 0")
    private Integer maxCapacity;

    /**
     * Giá vé người lớn
     */
    @NotNull(message = "Giá vé không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá vé không được âm")
    private BigDecimal price;

    /**
     * Giá vé trẻ em (tùy chọn)
     */
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá vé trẻ em không được âm")
    private BigDecimal childPrice;

    /**
     * Gói tour bao gồm hay trả phí ngoài
     */
    private Boolean isIncluded;
}

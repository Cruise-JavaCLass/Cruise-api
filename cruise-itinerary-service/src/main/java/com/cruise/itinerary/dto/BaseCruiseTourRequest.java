package com.cruise.itinerary.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lớp cơ sở chứa các trường thông tin chung của yêu cầu tour du thuyền (tránh trùng lặp mã theo Sonar Quality Gate)
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseCruiseTourRequest {

    @NotBlank(message = "Tên tour không được để trống")
    @Size(min = 5, max = 150, message = "Tên tour phải từ 5 đến 150 ký tự")
    private String name;

    @NotBlank(message = "Mã tour không được để trống")
    @Size(min = 3, max = 50, message = "Mã tour phải từ 3 đến 50 ký tự")
    private String code;

    @Size(max = 2000, message = "Mô tả tour tối đa 2000 ký tự")
    private String description;

    private Long shipId;

    @NotNull(message = "Số ngày tour không được để trống")
    @Min(value = 1, message = "Số ngày tour phải từ 1 ngày trở lên")
    @Max(value = 60, message = "Số ngày tour tối đa là 60 ngày")
    private Integer durationDays;

    @NotNull(message = "Số đêm tour không được để trống")
    @Min(value = 0, message = "Số đêm tour phải từ 0 trở lên")
    @Max(value = 60, message = "Số đêm tour tối đa là 60 đêm")
    private Integer durationNights;

    @NotBlank(message = "Cảng khởi hành không được để trống")
    @Size(max = 150, message = "Cảng khởi hành tối đa 150 ký tự")
    private String departurePort;

    @NotBlank(message = "Cảng đích không được để trống")
    @Size(max = 150, message = "Cảng đích tối đa 150 ký tự")
    private String destinationPort;

    @NotNull(message = "Ngày bắt đầu tour không được để trống")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc tour không được để trống")
    private LocalDate endDate;

    @NotNull(message = "Sức chứa tối đa không được để trống")
    @Min(value = 1, message = "Sức chứa tối đa phải từ 1 hành khách trở lên")
    private Integer maxCapacity;

    @NotNull(message = "Giá tour cơ bản không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá tour cơ bản không được âm")
    private BigDecimal basePrice;

    @Builder.Default
    private String currency = "VND";

    @Size(max = 2000, message = "Tóm tắt lộ trình tối đa 2000 ký tự")
    private String itineraryOverview;

    @Size(max = 500, message = "URL hình ảnh tối đa 500 ký tự")
    private String imageUrl;
}

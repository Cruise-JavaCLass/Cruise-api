package com.cruise.activity.dto;

import com.cruise.activity.enums.ExcursionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Base DTO chứa các thuộc tính chung cho việc tạo và cập nhật Tour bờ (KAN-168)
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseShoreExcursionRequest {

    @NotNull(message = "Mã điểm dừng (Port ID) không được để trống")
    private Long portId;

    @NotNull(message = "Ngày thứ mấy trong tour (dayNumber) không được để trống")
    @Min(value = 1, message = "Số ngày tour phải từ 1 trở lên")
    private Integer dayNumber;

    @NotBlank(message = "Tên tour bờ không được để trống")
    private String title;

    private String description;

    @NotBlank(message = "Tên cảng ghé không được để trống")
    private String portName;

    @NotBlank(message = "Điểm tập trung không được để trống")
    private String meetingPoint;

    private String localProvider;

    private String providerContact;

    @NotNull(message = "Ngày tổ chức tour bờ không được để trống")
    private LocalDate excursionDate;

    @NotNull(message = "Giờ tập trung không được để trống")
    private LocalTime gatheringTime;

    @NotNull(message = "Giờ xuất phát không được để trống")
    private LocalTime departureTime;

    @NotNull(message = "Giờ quay lại tàu không được để trống")
    private LocalTime returnToShipTime;

    @NotNull(message = "Sức chứa tối đa không được để trống")
    @Min(value = 1, message = "Sức chứa tối đa phải lớn hơn 0")
    private Integer maxCapacity;

    @NotNull(message = "Giá vé không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá vé không được âm")
    private BigDecimal price;

    @DecimalMin(value = "0.0", inclusive = true, message = "Giá vé trẻ em không được âm")
    private BigDecimal childPrice;

    private String currency;

    private Boolean isIncluded;

    private ExcursionStatus status;
}

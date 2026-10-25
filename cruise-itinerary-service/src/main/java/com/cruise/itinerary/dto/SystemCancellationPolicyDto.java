package com.cruise.itinerary.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemCancellationPolicyDto {
    private Long id;

    @Size(max = 150, message = "Tên chính sách tối đa 150 ký tự")
    @NotBlank(message = "Tên chính sách không được để trống")
    private String policyName;

    @Size(max = 50, message = "Loại dịch vụ tối đa 50 ký tự")
    @NotBlank(message = "Loại dịch vụ không được để trống")
    private String serviceType;

    @NotNull(message = "Thời gian hủy trước giờ khởi hành là bắt buộc")
    @Min(value = 0, message = "Số giờ phải lớn hơn hoặc bằng 0")
    private Integer hoursBeforeCutoff;

    @NotNull(message = "Tỷ lệ hoàn tiền là bắt buộc")
    @Min(value = 0, message = "Tỷ lệ hoàn tiền tối thiểu 0%")
    @Max(value = 100, message = "Tỷ lệ hoàn tiền tối đa 100%")
    private Integer refundPercentage;

    @DecimalMin(value = "0.0", message = "Phí hủy không được âm")
    @Digits(integer = 8, fraction = 2, message = "Phí hủy không hợp lệ (tối đa 8 chữ số nguyên và 2 chữ số thập phân)")
    private BigDecimal cancellationFee;

    private Boolean isActive;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;
}

package com.cruise.activity.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO yêu cầu khởi tạo hoặc tính toán lại quyết toán nhà cung cấp (KAN-332, KAN-333)
 */
public record InitializeSettlementRequest(
        @NotNull(message = "Mã tour trên bờ (excursionId) không được để trống")
        Long excursionId,

        @Size(max = 150, message = "Mã nhà cung cấp tối đa 150 ký tự")
        String providerId,

        @DecimalMin(value = "0.0", message = "Đơn giá thỏa thuận không được âm")
        BigDecimal agreedUnitRate,

        @Min(value = 0, message = "Số lượng khách cam kết tối thiểu không được âm")
        Integer minGuaranteePax,

        @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
        String notes
) {}

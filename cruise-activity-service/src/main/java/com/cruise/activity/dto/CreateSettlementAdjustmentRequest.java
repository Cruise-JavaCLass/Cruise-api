package com.cruise.activity.dto;

import com.cruise.activity.enums.AdjustmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO yêu cầu ghi nhận điều chỉnh / tranh chấp chi phí quyết toán (KAN-332, KAN-334)
 */
public record CreateSettlementAdjustmentRequest(
        @NotNull(message = "Loại điều chỉnh không được để trống")
        AdjustmentType adjustmentType,

        @NotNull(message = "Số tiền điều chỉnh không được để trống")
        BigDecimal amount,

        @Size(max = 150, message = "Mã hóa đơn chứng từ tối đa 150 ký tự")
        String invoiceReference,

        @NotBlank(message = "Lý do giải trình không được để trống")
        @Size(max = 1000, message = "Lý do giải trình tối đa 1000 ký tự")
        String explanation,

        @Size(max = 100, message = "Người lập tối đa 100 ký tự")
        String recordedBy
) {}

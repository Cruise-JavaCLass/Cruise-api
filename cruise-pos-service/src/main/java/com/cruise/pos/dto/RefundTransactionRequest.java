package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundTransactionRequest {

    @NotNull(message = "Số tiền hoàn (refundAmount) không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền hoàn tối thiểu là 0.01")
    private BigDecimal refundAmount;

    @NotBlank(message = "Lý do hoàn tiền (refundReason) không được để trống")
    private String refundReason;

    @NotBlank(message = "Mã người thực hiện (performedBy) không được để trống")
    private String performedBy;

    @NotBlank(message = "Mã PIN người quản lý (managerPin) bắt buộc đối với hoàn tiền")
    private String managerPin;

    private String managerId;
}

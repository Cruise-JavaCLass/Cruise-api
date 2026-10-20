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
public class PostExpenseRequest {

    @NotBlank(message = "Mã giao dịch không được để trống")
    private String transactionId;

    @NotBlank(message = "Mô tả chi phí không được để trống")
    private String description;

    @NotBlank(message = "Khu vực dịch vụ không được để trống")
    private String serviceArea;

    @NotNull(message = "Số tiền không được để trống")
    @DecimalMin(value = "0.01", message = "Số tiền phải lớn hơn 0")
    private BigDecimal amount;

    @Builder.Default
    private Boolean isReversal = false;
}

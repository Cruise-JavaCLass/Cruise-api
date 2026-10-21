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
public class UpdateCreditLimitRequest {

    @NotNull(message = "Hạn mức tín dụng mới không được để trống")
    @DecimalMin(value = "0.00", message = "Hạn mức tín dụng phải >= 0")
    private BigDecimal creditLimit;

    @NotBlank(message = "Lý do điều chỉnh hạn mức không được để trống")
    private String reason;
}

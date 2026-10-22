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
public class GenerateVietQrRequest {

    @NotBlank(message = "Cabin number must not be blank")
    private String cabinNumber;

    private Long passengerId;

    @NotNull(message = "Amount must not be null")
    @DecimalMin(value = "1000.00", message = "Minimum settlement amount is 1,000 VND")
    private BigDecimal amount;

    private String description;

    private String idempotencyKey;
}

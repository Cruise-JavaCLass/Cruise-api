package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
public class CabinSplitShareRequest {
    @NotNull(message = "passengerId is required")
    private Long passengerId;

    private String guestName;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "amount must have at most 2 decimal places")
    private BigDecimal amount;

    @Size(max = 2_000_000, message = "signatureBase64 exceeds maximum allowed size")
    private String signatureBase64;
}

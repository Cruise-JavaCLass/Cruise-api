package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

public record CashDropRequest(
        @NotNull(message = "Amount cannot be null")
        @DecimalMin(value = "0.01", message = "Cash drop amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,

        @NotBlank(message = "Cashier ID cannot be blank")
        @Size(max = 64, message = "Cashier ID cannot exceed 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Cashier ID contains invalid characters")
        String cashierId,

        @NotBlank(message = "Witness/Supervisor ID cannot be blank")
        @Size(max = 64, message = "Witness ID cannot exceed 64 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Witness ID contains invalid characters")
        String witnessId,

        @NotBlank(message = "Witness PIN cannot be blank")
        @Size(max = 64, message = "Witness PIN cannot exceed 64 characters")
        String witnessPin,

        @Size(max = 50, message = "Currency breakdown cannot exceed 50 entries")
        Map<@Pattern(regexp = "^[A-Za-z0-9._ -]+$", message = "Denomination key contains invalid characters") String, @Min(value = 0, message = "Denomination count cannot be negative") Integer> currencyBreakdown,

        @Size(max = 512, message = "Notes cannot exceed 512 characters")
        String notes
) {
}

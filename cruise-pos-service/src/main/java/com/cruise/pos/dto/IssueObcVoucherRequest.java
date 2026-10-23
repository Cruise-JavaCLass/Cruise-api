package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record IssueObcVoucherRequest(
        @NotNull(message = "creditAmount is required")
        @DecimalMin(value = "5.00", message = "Minimum compensation OBC is $5.00")
        @DecimalMax(value = "50000.00", message = "Maximum compensation OBC is $50,000.00")
        @Digits(integer = 8, fraction = 2, message = "creditAmount format is invalid")
        BigDecimal creditAmount,

        @NotBlank(message = "apologyTitle is required")
        @Size(max = 128)
        String apologyTitle,

        @NotBlank(message = "apologyLetterBody is required")
        @Size(max = 2000, message = "apologyLetterBody must not exceed 2000 characters")
        String apologyLetterBody,

        Integer validityHours
) {}

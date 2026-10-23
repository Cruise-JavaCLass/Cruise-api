package com.cruise.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankWebhookPayload {

    @NotBlank(message = "Payment reference must not be blank")
    private String paymentReference;

    @NotBlank(message = "Transaction ID must not be blank")
    private String transactionId;

    @NotNull(message = "Transfer amount must not be null")
    @DecimalMin("1000.00")
    private BigDecimal amount;

    @NotBlank(message = "Payment status must not be blank")
    @Pattern(regexp = "^(?i)(SUCCESS|FAILED)$", message = "Status must be either SUCCESS or FAILED")
    private String status;

    private String bankCode;

    private String description;
}
